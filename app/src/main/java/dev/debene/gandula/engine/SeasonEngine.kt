package dev.debene.gandula.engine

import dev.debene.gandula.domain.Match
import dev.debene.gandula.domain.Team

/** Zero-based round; indices into the league's team list. */
data class Fixture(val round: Int, val homeIdx: Int, val awayIdx: Int)

data class TeamStats(
    val teamId: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
) {
    val points: Int get() = won * 3 + drawn
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

data class SeasonRecord(
    val leagueName: String,
    val fixtures: List<Fixture>,
    val matches: List<Match>,
    val standings: List<TeamStats>,
)

/**
 * Season layer on top of [MatchEngine]. Port of upstream `core/src/season/mod.rs`:
 * a double round-robin via the circle method, each fixture simulated with its own
 * deterministic seed, then standings assembled (Pts desc, GD desc, GF desc,
 * team_id asc). Identical (teams, seed) → identical [SeasonRecord].
 */
object SeasonEngine {

    fun simulateSeason(teams: List<Team>, seed: Long, name: String): SeasonRecord {
        val fixtures = generateFixtures(teams.size)
        val matches = fixtures.mapIndexed { idx, f ->
            MatchEngine.simulate(teams[f.homeIdx], teams[f.awayIdx], matchSeed(seed, idx))
        }
        val standings = computeStandings(teams.map { it.id }, fixtures, matches, Int.MAX_VALUE)
        return SeasonRecord(name, fixtures, matches, standings)
    }

    /** Circle-method double round-robin. Odd counts get a virtual BYE that rests
     *  one team per round (all gandula tiers are 20, so no byes in practice). */
    fun generateFixtures(nTeams: Int): List<Fixture> {
        if (nTeams < 2) return emptyList()
        val effective = if (nTeams % 2 == 0) nTeams else nTeams + 1
        val bye = nTeams // first invalid index
        val roundsPerHalf = effective - 1
        val positions = IntArray(effective) { it }
        val fixtures = mutableListOf<Fixture>()

        for (round in 0 until roundsPerHalf) {
            for (i in 0 until effective / 2) {
                val a = positions[i]
                val b = positions[effective - 1 - i]
                val home: Int
                val away: Int
                if ((i + round) % 2 == 0) {
                    home = a; away = b
                } else {
                    home = b; away = a
                }
                if (home == bye || away == bye) continue
                fixtures.add(Fixture(round, home, away))
            }
            // Circle rotation: position 0 fixed, slide last → position 1.
            if (effective >= 3) {
                val last = positions[effective - 1]
                for (i in effective - 1 downTo 2) positions[i] = positions[i - 1]
                positions[1] = last
            }
        }

        // Second half: flip home/away, offset round numbers.
        val firstHalfLen = fixtures.size
        for (i in 0 until firstHalfLen) {
            val f = fixtures[i]
            fixtures.add(Fixture(f.round + roundsPerHalf, f.awayIdx, f.homeIdx))
        }
        return fixtures
    }

    /** Deterministic, fixture-unique seed. Long arithmetic wraps (two's
     *  complement) exactly like Rust's wrapping_mul/add. */
    fun matchSeed(seasonSeed: Long, fixtureIdx: Int): Long {
        var s = seasonSeed * 0x9E3779B97F4A7C15uL.toLong()
        s += fixtureIdx.toLong() * 0xD1B54A32D192ED03uL.toLong()
        return s * 0xC6BC279692B5C323uL.toLong()
    }

    /**
     * Standings over the fixtures whose round is `< upToRoundExclusive`. Pass
     * `Int.MAX_VALUE` for the full table. `teamIds` seeds every row so the table
     * never gains/loses rows as rounds are revealed. Sort mirrors the Rust impl.
     */
    fun computeStandings(
        teamIds: List<Int>,
        fixtures: List<Fixture>,
        matches: List<Match>,
        upToRoundExclusive: Int,
    ): List<TeamStats> {
        // Mutable accumulators keyed by team id.
        val played = HashMap<Int, IntArray>() // [played, won, drawn, lost, gf, ga]
        for (id in teamIds) played[id] = IntArray(6)

        fixtures.forEachIndexed { i, f ->
            if (f.round >= upToRoundExclusive) return@forEachIndexed
            val m = matches.getOrNull(i) ?: return@forEachIndexed
            val home = played[m.home] ?: return@forEachIndexed
            val away = played[m.away] ?: return@forEachIndexed
            val hg = m.result.homeGoals
            val ag = m.result.awayGoals
            home[0]++; away[0]++
            home[4] += hg; home[5] += ag
            away[4] += ag; away[5] += hg
            when {
                hg > ag -> { home[1]++; away[3]++ }
                hg < ag -> { away[1]++; home[3]++ }
                else -> { home[2]++; away[2]++ }
            }
        }

        return teamIds.map { id ->
            val s = played[id]!!
            TeamStats(id, s[0], s[1], s[2], s[3], s[4], s[5])
        }.sortedWith(
            compareByDescending<TeamStats> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.teamId },
        )
    }
}
