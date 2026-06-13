package dev.debene.gandula.career

import dev.debene.gandula.domain.Team
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The three-tier "Brasileirão Imaginário" — Série A / B / C, 20 teams each.
 * Port of upstream `web/src/util/divisions.ts`.
 */
object Divisions {
    const val TIER_A_SIZE = 20
    const val TIER_B_SIZE = 20
    const val TIER_C_SIZE = 20
    const val WORLD_SIZE = TIER_A_SIZE + TIER_B_SIZE + TIER_C_SIZE

    fun tierName(tier: Int): String = when (tier) {
        1 -> "Série A"
        2 -> "Série B"
        else -> "Série C"
    }

    /** Mean of the six attributes per starter, mean across the XI, rounded. */
    fun avgStrength(team: Team): Int {
        val starters = team.startingXi.mapNotNull { id -> team.roster.firstOrNull { it.id == id } }
        if (starters.isEmpty()) return 0
        val sum = starters.sumOf { p ->
            val a = p.attributes
            (a.pace + a.technique + a.passing + a.defending + a.finishing + a.stamina) / 6.0
        }
        return (sum / starters.size).roundToInt()
    }

    /**
     * Split [WORLD_SIZE] teams into `[tierA, tierB, tierC]` by strength (strongest
     * 20 on top). Deterministic; tiebreak lower id first.
     */
    fun divideIntoDivisions(teams: List<Team>): List<List<Team>> {
        require(teams.size == WORLD_SIZE) {
            "divideIntoDivisions expects $WORLD_SIZE teams, got ${teams.size}"
        }
        val sorted = teams.sortedWith(
            compareByDescending<Team> { avgStrength(it) }.thenBy { it.id },
        )
        return listOf(
            sorted.subList(0, TIER_A_SIZE).toList(),
            sorted.subList(TIER_A_SIZE, TIER_A_SIZE + TIER_B_SIZE).toList(),
            sorted.subList(TIER_A_SIZE + TIER_B_SIZE, WORLD_SIZE).toList(),
        )
    }

    /**
     * Pick the club a new career manages: any Série C team, chosen at random
     * (mirrors upstream `pickRandomStarter`). The season sim stays fully
     * seed-deterministic; only which club you control varies, and it's persisted.
     */
    fun pickRandomStarter(bottomTier: List<Team>, random: Random = Random.Default): Team {
        require(bottomTier.isNotEmpty()) { "pickRandomStarter: empty bottom tier" }
        return bottomTier[random.nextInt(bottomTier.size)]
    }
}
