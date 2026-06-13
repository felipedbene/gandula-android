package dev.debene.gandula.career

import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Team

/**
 * Opponent regeneration — port of `web/src/util/regen.ts`. Opponents reset to the
 * registry each season, so their evolution is replayed from the registry base
 * every time a season is composed (deterministic, no per-team persistence): each
 * season age → retire the old → bring in youth → rebuild a valid XI/bench.
 */
object Regen {
    private const val MAX_BENCH = 7
    const val RETIREMENT_AGE = 36

    /** Evolve a registry team forward `seasons` years. Deterministic in
     *  (team, seasons, careerSeed); result always satisfies the engine. */
    fun evolveTeam(team: Team, seasons: Int, careerSeed: Long): Team {
        var t = team
        for (offset in 1..seasons) t = evolveOneSeason(t, careerSeed, offset)
        return t
    }

    /** One season of squad churn on a roster: age all, retire ≥ RETIREMENT_AGE,
     *  replace each retiree with one same-position youth (size held constant).
     *  Shared by opponents and the user's squad. */
    fun evolveRoster(roster: List<Player>, careerSeed: Long, teamId: Int, yearOffset: Int): List<Player> {
        val rng = rngFor(careerSeed, teamId, yearOffset)
        val aged = Aging.ageRoster(roster)
        val retired = aged.filter { it.age >= RETIREMENT_AGE }
        val retiredIds = retired.map { it.id }.toSet()
        val survivors = aged.filter { it.id !in retiredIds }
        val youth = retired.mapIndexed { slot, r ->
            TransferMarket.buildYouth(rng, TransferMarket.regenId(teamId, yearOffset, slot), r.position)
        }
        return survivors + youth
    }

    private fun rngFor(careerSeed: Long, teamId: Int, yearOffset: Int): Mulberry32 {
        val s = (careerSeed xor teamId.toLong() xor (yearOffset.toLong() * 0x9e37L) xor 0x4ee7L) and 0xFFFFFFFFL
        return Mulberry32(s.toInt())
    }

    private fun evolveOneSeason(team: Team, careerSeed: Long, yearOffset: Int): Team {
        val roster = evolveRoster(team.roster, careerSeed, team.id, yearOffset)
        // Recompute aging bookkeeping (matches survivors in `roster` exactly).
        val aged = Aging.ageRoster(team.roster)
        val agedById = aged.associateBy { it.id }
        val retiredIds = aged.filter { it.age >= RETIREMENT_AGE }.map { it.id }.toSet()

        val startingXi = backfillXI(team.startingXi, retiredIds, roster, agedById)
        val bench = backfillBench(team.bench, retiredIds, roster, startingXi)
        return team.copy(roster = roster, startingXi = startingXi, bench = bench)
    }

    /** Replace each retired XI slot with the best available roster player,
     *  preferring the retiree's position. Survivors keep their slots. */
    private fun backfillXI(
        oldXi: List<Int>,
        retiredIds: Set<Int>,
        roster: List<Player>,
        agedById: Map<Int, Player>,
    ): List<Int> {
        val rosterIds = roster.map { it.id }.toSet()
        val result = oldXi.filter { it !in retiredIds && it in rosterIds }.toMutableList()
        val used = result.toMutableSet()
        val byOverall = roster.sortedWith(
            compareByDescending<Player> { TransferMarket.playerOverall(it) }.thenBy { it.id },
        )

        fun take(pred: (Player) -> Boolean): Boolean {
            val pick = byOverall.firstOrNull { it.id !in used && pred(it) } ?: return false
            result.add(pick.id); used.add(pick.id); return true
        }

        val lostPositions = oldXi.filter { it in retiredIds }.mapNotNull { agedById[it]?.position }
        for (pos in lostPositions) {
            if (!take { it.position == pos }) take { true }
        }
        while (result.size < 11 && take { true }) { /* fill */ }
        return result
    }

    /** Drop retirees from the bench, then top back up from the best remaining
     *  non-XI players, keeping the original depth (≤ MAX_BENCH). */
    private fun backfillBench(
        oldBench: List<Int>,
        retiredIds: Set<Int>,
        roster: List<Player>,
        xi: List<Int>,
    ): List<Int> {
        val xiSet = xi.toSet()
        val rosterIds = roster.map { it.id }.toSet()
        val bench = oldBench.filter { it !in retiredIds && it in rosterIds && it !in xiSet }.toMutableList()
        val target = minOf(MAX_BENCH, oldBench.size)
        if (bench.size >= target) return bench

        val used = (xiSet + bench).toMutableSet()
        val fill = roster.filter { it.id !in used }
            .sortedWith(compareByDescending<Player> { TransferMarket.playerOverall(it) }.thenBy { it.id })
            .take(target - bench.size)
            .map { it.id }
        return bench + fill
    }
}
