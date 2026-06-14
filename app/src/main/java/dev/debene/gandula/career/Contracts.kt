package dev.debene.gandula.career

import com.squareup.moshi.JsonClass
import dev.debene.gandula.domain.Attributes
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Contracts, wage demands, and player departures — the "dressing room" economy.
 *
 * Each player has a deterministic **temperament** derived from (careerSeed, id):
 * a *loyal* player accepts a refusal but sulks (a one-off attribute drop next
 * season); a *mercenary* walks to a bigger club if refused. Top players may also
 * be **poached** (an outside bid you either match with a raise or cash in).
 *
 * Demands are generated the moment a season completes (a pure function of seed +
 * year + squad + current contracts) and resolved at the boundary, so the season
 * already played is never altered. A signed raise bumps the player's wage
 * multiplier (`Finances` bills `overall × rate × multiplier`).
 */
object Contracts {

    const val MAX_DEMANDS = 3
    const val MERCENARY_BELOW = 35   // loyalty < 35 ⇒ mercenary
    const val MORALE_DROP = 4        // attribute points a snubbed loyal player loses

    /** Stable 0–99 loyalty for a player in this career. */
    fun loyalty(careerSeed: Long, playerId: Int): Int {
        val rng = Mulberry32((careerSeed xor (playerId.toLong() * 2_654_435_761L)).toInt())
        return (rng.next() * 100).toInt()
    }

    fun isMercenary(careerSeed: Long, playerId: Int): Boolean = loyalty(careerSeed, playerId) < MERCENARY_BELOW

    @JsonClass(generateAdapter = true)
    data class Demand(
        val playerId: Int,
        val playerName: String,
        val position: Position,
        val overall: Int,
        val kind: String,        // "raise" | "poach"
        val currentMult: Double,
        val targetMult: Double,
        val fee: Long,           // banked if the player leaves
        val mercenary: Boolean,
    )

    private fun overall(p: Player): Int = TransferMarket.playerOverall(p)

    /** The end-of-season demands for [squad], deterministic in (seed, year). The
     *  better the player, the likelier they push; capped at [MAX_DEMANDS]. */
    fun endOfSeasonDemands(careerSeed: Long, year: Int, squad: List<Player>, mults: Map<Int, Double>): List<Demand> {
        val rng = Mulberry32((careerSeed xor (year.toLong() * 0x9E3779B9L) xor 0xC0FFEEL).toInt())
        val ranked = squad.sortedWith(compareByDescending<Player> { overall(it) }.thenBy { it.id })
        val out = ArrayList<Demand>()
        for (p in ranked) {
            if (out.size >= MAX_DEMANDS) break
            val ov = overall(p)
            val trigger = when {
                ov >= 80 -> 0.55
                ov >= 73 -> 0.33
                ov >= 66 -> 0.15
                else -> 0.04
            }
            if (rng.next() >= trigger) continue
            val merc = isMercenary(careerSeed, p.id)
            val cur = mults[p.id] ?: 1.0
            val bump = 0.15 + rng.next() * 0.30 // +15%..+45%
            val target = ((cur + bump) * 100).roundToInt() / 100.0
            val poached = merc && ov >= 74 && rng.next() < 0.5
            val fee = if (poached) TransferMarket.playerPrice(p, TransferMarket.Kind.BUY)
            else TransferMarket.playerPrice(p, TransferMarket.Kind.SELL)
            out.add(Demand(p.id, p.name, p.position, ov, if (poached) "poach" else "raise", cur, target, fee, merc))
        }
        return out
    }

    /** Outcome of resolving the off-season demands against the user's decisions
     *  (playerId → accepted). Applied at the boundary to the roster that carries
     *  to next season. */
    data class Resolution(
        val roster: List<Player>,
        val feesEarned: Long,
        val multipliers: Map<Int, Double>,
        val departed: List<Demand>,
        val sulked: List<Int>,
    )

    fun resolve(
        roster: List<Player>,
        multipliers: Map<Int, Double>,
        demands: List<Demand>,
        decisions: Map<Int, Boolean>,
    ): Resolution {
        var r = roster
        var fees = 0L
        val mults = multipliers.toMutableMap()
        val departed = ArrayList<Demand>()
        val sulked = ArrayList<Int>()
        for (d in demands) {
            val accepted = decisions[d.playerId] == true
            when {
                accepted -> mults[d.playerId] = d.targetMult // raise / match the bid → stays
                d.kind == "poach" || d.mercenary -> {        // sold / walks out → leaves with a fee
                    r = r.filter { it.id != d.playerId }
                    mults.remove(d.playerId)
                    fees += d.fee
                    departed.add(d)
                }
                else -> {                                    // loyal snub → sulks (stays, weaker)
                    r = r.map { if (it.id == d.playerId) sulk(it) else it }
                    sulked.add(d.playerId)
                }
            }
        }
        // Drop stale contracts for anyone no longer on the squad.
        val ids = r.map { it.id }.toSet()
        return Resolution(r, fees, mults.filterKeys { it in ids }, departed, sulked)
    }

    private fun sulk(p: Player): Player {
        val a = p.attributes
        fun d(v: Int) = max(1, v - MORALE_DROP)
        return p.copy(
            attributes = Attributes(d(a.pace), d(a.technique), d(a.passing), d(a.defending), d(a.finishing), d(a.stamina)),
        )
    }

    /** Per-player wage with the contract multiplier applied. */
    fun playerWage(p: Player, rate: Long, multiplier: Double): Long =
        (overall(p) * rate * multiplier).roundToLong()
}
