package dev.debene.gandula.career

import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Team

/** Outfield line counts (DEF, MID, FWD) — the formation's required composition. */
fun Formation.lineCounts(): Triple<Int, Int, Int> = when (this) {
    Formation.F442 -> Triple(4, 4, 2)
    Formation.F433 -> Triple(4, 3, 3)
    Formation.F352 -> Triple(3, 5, 2)
    Formation.F4231 -> Triple(4, 5, 1)
}

/**
 * Resolves the user's effective team from the registry default plus the
 * `Career.userRoster` overlay (port of `web/src/util/roster.ts`). An empty
 * overlay falls back to the registry; a non-empty one wins, with the XI and
 * bench reconciled so a sold/retired player can't dangle in either.
 */
object Roster {

    /** The squad the season *started* with — `Career.userRoster` once populated,
     *  else the registry default. Mid-season transfers are layered on top of this
     *  via [rosterAtRound]; the season boundary folds the final squad back into a
     *  fresh `userRoster`. */
    fun seasonStartRoster(career: Career, registry: Map<Int, Team>): List<Player> =
        if (career.userRoster.isEmpty()) registry.getValue(career.controlledTeamId).roster else career.userRoster

    /** The squad as of [throughRound]: the season-start roster with every transfer
     *  whose `round` is in `0..throughRound` applied in order. Pure function of the
     *  persisted (seasonStart, transfers) — the basis of the mid-season re-sim. */
    fun rosterAtRound(seasonStart: List<Player>, transfers: List<TransferRecord>, throughRound: Int): List<Player> {
        if (transfers.isEmpty()) return seasonStart
        var r = seasonStart
        for (t in transfers.filter { it.round in 0..throughRound && it.player != null }.sortedBy { it.round }) {
            val p = t.player!!
            r = when (t.kind) {
                "buy" -> if (r.any { it.id == p.id }) r else r + p
                "sell" -> r.filter { it.id != p.id }
                else -> r
            }
        }
        return r
    }

    /** The squad the working career currently presents: the season-start roster
     *  with this season's transfers applied through the current reveal cursor. */
    fun workingRoster(career: Career, registry: Map<Int, Team>): List<Player> =
        rosterAtRound(seasonStartRoster(career, registry), career.transfers, career.season.currentRoundIdx)

    /** Effective Team for the controlled side: base with the roster overlay
     *  swapped in, the season tactics applied, and the starting XI **normalized to
     *  the formation** (exactly 1 GK + the right DEF/MID/FWD counts) so the side is
     *  always valid — never two keepers or a striker at centre-back. */
    fun effectiveTeam(base: Team, userRoster: List<Player>, userTactics: SeasonTactics? = null): Team {
        var team = base
        if (userRoster.isNotEmpty()) team = team.copy(roster = userRoster)
        if (userTactics != null) team = team.copy(formation = userTactics.formation, tactics = userTactics.tactics)

        val formation = userTactics?.formation ?: team.formation
        val prefer = userTactics?.xi?.takeIf { it.size == 11 } ?: team.startingXi
        val xi = lineupFor(team.roster, formation, prefer)
        val xiSet = xi.toSet()
        val rosterIds = team.roster.map { it.id }.toSet()
        return team.copy(
            startingXi = xi,
            bench = (team.bench.filter { it in rosterIds && it !in xiSet }),
        )
    }

    /** A valid starting XI for [formation]: 1 GK + its DEF/MID/FWD counts, drawn
     *  from [roster], preferring the players in [prefer] (then best by overall),
     *  any shortfall filled by the best remaining. If [prefer] is *already* a valid
     *  XI for the formation it's returned unchanged (so a good lineup — and its
     *  order — is never reshuffled). */
    fun lineupFor(roster: List<Player>, formation: Formation, prefer: List<Int>): List<Int> {
        val (defC, midC, fwdC) = formation.lineCounts()
        val byId = roster.associateBy { it.id }
        if (prefer.size == 11 && prefer.all { it in byId }) {
            val ps = prefer.mapNotNull { byId[it]?.position }
            if (ps.count { it == Position.GK } == 1 &&
                ps.count { it == Position.DEF } == defC &&
                ps.count { it == Position.MID } == midC &&
                ps.count { it == Position.FWD } == fwdC
            ) {
                return prefer
            }
        }
        val preferSet = prefer.toSet()
        val used = HashSet<Int>()
        fun pick(pos: Position, n: Int): List<Int> {
            val chosen = roster.filter { it.position == pos && it.id !in used }
                .sortedWith(compareByDescending<Player> { it.id in preferSet }.thenByDescending { TransferMarket.playerOverall(it) })
                .take(n).map { it.id }
            used.addAll(chosen)
            return chosen
        }
        var xi = pick(Position.GK, 1) + pick(Position.DEF, defC) + pick(Position.MID, midC) + pick(Position.FWD, fwdC)
        if (xi.size < 11) {
            xi = xi + roster.filter { it.id !in used }
                .sortedByDescending { TransferMarket.playerOverall(it) }
                .take(11 - xi.size).map { it.id }
        }
        return xi.take(11)
    }

    fun userTeam(career: Career, registry: Map<Int, Team>): Team =
        effectiveTeam(registry.getValue(career.controlledTeamId), workingRoster(career, registry), career.userTactics)

    private fun attrSum(p: Player): Int {
        val a = p.attributes
        return a.pace + a.technique + a.passing + a.defending + a.finishing + a.stamina
    }

    /** Base XI pruned to rostered ids, backfilled (best attrs, lower id breaks
     *  ties) until a fieldable 11. No-op when every starter is still rostered. */
    private fun reconcileXI(baseXi: List<Int>, roster: List<Player>): List<Int> {
        val rosterIds = roster.map { it.id }.toSet()
        val xi = baseXi.filter { it in rosterIds }.toMutableList()
        if (xi.size >= 11) return xi
        val used = xi.toMutableSet()
        val candidates = roster.filter { it.id !in used }
            .sortedWith(compareByDescending<Player> { attrSum(it) }.thenBy { it.id })
        for (p in candidates) {
            if (xi.size >= 11) break
            xi.add(p.id)
        }
        return xi
    }
}
