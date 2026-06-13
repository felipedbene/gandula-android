package dev.debene.gandula.career

import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tactics
import dev.debene.gandula.domain.Team
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width

/**
 * Distilled rival coach — port of `web/src/util/rival-coach.ts`. AI clubs adopt a
 * per-tier tactic and spend a stateless transfer budget on squad upgrades each
 * season, so the league genuinely strengthens rather than only aging. Behaviour
 * was distilled from an RL policy ("climb then consolidate"); the constants are
 * transcribed from that artifact. Pure + deterministic in (tier, seed, teamId,
 * yearOffset) so the registry-replay determinism holds.
 */
object RivalCoach {
    private data class RivalTier(val formation: Formation, val tactics: Tactics, val budgetBase: Long)

    private fun policy(tier: Int): RivalTier = when (tier) {
        1 -> RivalTier(Formation.F442, Tactics(Mentality.Defensive, Tempo.Normal, Pressing.Low, Width.Normal), 4_000_000L)
        2 -> RivalTier(Formation.F4231, Tactics(Mentality.VeryAttacking, Tempo.Fast, Pressing.High, Width.Wide), 6_000_000L)
        else -> RivalTier(Formation.F352, Tactics(Mentality.Defensive, Tempo.Fast, Pressing.High, Width.Narrow), 3_000_000L)
    }

    private const val RIVAL_CASH_FLOOR = 500_000L
    private const val MIN_UPGRADE_DELTA = 2

    private fun rngFor(careerSeed: Long, teamId: Int, yearOffset: Int): Mulberry32 {
        // Distinct namespace from Regen.rngFor (different XOR constants).
        val s = (careerSeed xor teamId.toLong() xor (yearOffset.toLong() * 0x7f4aL) xor 0xc0a7L) and 0xFFFFFFFFL
        return Mulberry32(s.toInt())
    }

    /** Stateless per-season budget: tier base × per-club jitter in [0.7, 1.3].
     *  Depends only on (tier, seed, teamId, yearOffset) so re-sim reconstructs it. */
    fun rivalBudget(tier: Int, careerSeed: Long, teamId: Int, yearOffset: Int): Long {
        val base = policy(tier).budgetBase
        val jitter = 0.7 + rngFor(careerSeed, teamId, yearOffset).next() * 0.6
        return Math.round(base * jitter)
    }

    private fun weakestPosition(roster: List<Player>): Pair<Position, Int> {
        var chosen = Position.GK
        var chosenWorst = Int.MAX_VALUE
        for (pos in Position.entries) {
            val same = roster.filter { it.position == pos }
            val worst = if (same.isEmpty()) 0 else same.minOf { TransferMarket.playerOverall(it) }
            if (worst < chosenWorst) { chosenWorst = worst; chosen = pos }
        }
        return chosen to (if (chosenWorst == Int.MAX_VALUE) 0 else chosenWorst)
    }

    /** Greedy buy: best affordable agent that upgrades the weakest position,
     *  until budget (down to the floor) or roster cap is hit. */
    fun rivalTransfers(roster: List<Player>, budget: Long, year: Int, careerSeed: Long): List<Player> {
        val pool = TransferMarket.generateFreeAgents(careerSeed, year)
        val taken = roster.map { it.id }.toMutableSet()
        val result = roster.toMutableList()
        var cash = budget

        while (result.size < TransferMarket.MAX_ROSTER && cash > RIVAL_CASH_FLOOR) {
            val (position, worst) = weakestPosition(result)
            val pick = pool.asSequence()
                .filter { it.id !in taken && it.position == position }
                .filter { TransferMarket.playerOverall(it) >= worst + MIN_UPGRADE_DELTA }
                .filter { TransferMarket.playerPrice(it, TransferMarket.Kind.BUY) <= cash - RIVAL_CASH_FLOOR }
                .sortedWith(
                    compareByDescending<Player> { TransferMarket.playerOverall(it) }
                        .thenBy { TransferMarket.playerPrice(it, TransferMarket.Kind.BUY) }
                        .thenBy { it.id },
                )
                .firstOrNull() ?: break
            cash -= TransferMarket.playerPrice(pick, TransferMarket.Kind.BUY)
            taken.add(pick.id)
            result.add(pick)
        }
        return result
    }

    /** After buys, swap bought players into the XI where a strict upgrade, and
     *  top up the bench — keeping XI = 11 distinct rostered ids, bench ≤ 7. */
    private fun reconcileLineup(team: Team, newRoster: List<Player>): Team {
        val byId = newRoster.associateBy { it.id }
        val rosterIds = byId.keys
        val xi = team.startingXi.filter { it in rosterIds }.toMutableList()
        val inXi = xi.toMutableSet()

        val benchPool = newRoster.filter { it.id !in inXi }
            .sortedWith(compareByDescending<Player> { TransferMarket.playerOverall(it) }.thenBy { it.id })
        for (cand in benchPool) {
            var weakestSlot = -1
            var weakestOverall = Int.MAX_VALUE
            xi.forEachIndexed { slot, id ->
                val p = byId[id] ?: return@forEachIndexed
                if (p.position != cand.position) return@forEachIndexed
                val ov = TransferMarket.playerOverall(p)
                if (ov < weakestOverall) { weakestOverall = ov; weakestSlot = slot }
            }
            if (weakestSlot >= 0 && TransferMarket.playerOverall(cand) > weakestOverall) {
                inXi.remove(xi[weakestSlot]); xi[weakestSlot] = cand.id; inXi.add(cand.id)
            }
        }

        val priorDepth = if (team.bench.isEmpty()) 7 else team.bench.size
        val bench = newRoster.filter { it.id !in inXi }
            .sortedWith(compareByDescending<Player> { TransferMarket.playerOverall(it) }.thenBy { it.id })
            .take(minOf(7, priorDepth))
            .map { it.id }
        return team.copy(roster = newRoster, startingXi = xi, bench = bench)
    }

    /** Apply the coach to an already aged/regen'd opponent for the upcoming
     *  season. Season 0 (yearOffset 0) is the uncoached registry baseline. */
    fun applyRivalCoach(team: Team, tier: Int, year: Int, careerSeed: Long, yearOffset: Int): Team {
        if (yearOffset == 0) return team
        val budget = rivalBudget(tier, careerSeed, team.id, yearOffset)
        val bought = rivalTransfers(team.roster, budget, year, careerSeed)
        val p = policy(tier)
        val coached = reconcileLineup(team, bought)
        return coached.copy(formation = p.formation, tactics = p.tactics)
    }
}
