package dev.debene.gandula

import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Divisions
import dev.debene.gandula.career.Promotion
import dev.debene.gandula.career.UserOutcome
import dev.debene.gandula.domain.Team
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Career structural-core tests against the real 60-team world (pure JVM). */
class CareerEngineTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json"))
            .first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `world divides into three tiers of twenty`() {
        val teams = loadTeams()
        assertEquals("teams.json should hold the full world", Divisions.WORLD_SIZE, teams.size)
        val tiers = Divisions.divideIntoDivisions(teams)
        assertEquals(3, tiers.size)
        tiers.forEach { assertEquals(20, it.size) }
        // Strongest tier's average strength should exceed the weakest tier's.
        val avg = { ts: List<Team> -> ts.sumOf { Divisions.avgStrength(it) } / ts.size.toDouble() }
        assertTrue("Série A stronger than Série C", avg(tiers[0]) > avg(tiers[2]))
    }

    @Test
    fun `new career starts the user in Série C and is deterministic per seed`() {
        val teams = loadTeams()
        val c1 = CareerEngine.newCareer(teams, seed = 1998, random = Random(0))
        val c2 = CareerEngine.newCareer(teams, seed = 1998, random = Random(0))
        assertEquals("same seed+random → same career", c1.season, c2.season)
        assertEquals("user starts in Série C", 3, CareerEngine.userDivision(c1.season, c1.controlledTeamId).tier)
        assertEquals(CareerEngine.STARTING_MONEY, c1.money)
        assertEquals(2026, c1.season.year)
        assertEquals(38, CareerEngine.totalRounds(c1.season))
    }

    @Test
    fun `promotion-relegation conserves all 60 teams and keeps tiers at 20`() {
        val teams = loadTeams()
        val career = CareerEngine.newCareer(teams, seed = 7, random = Random(1))
        val s = career.season
        val pr = Promotion.compute(
            s.divisions[0].record.standings,
            s.divisions[1].record.standings,
            s.divisions[2].record.standings,
            career.controlledTeamId,
        )
        val next = CareerEngine.applyPromotionRelegation(
            s.divisions[0].record.standings,
            s.divisions[1].record.standings,
            s.divisions[2].record.standings,
            pr,
        )
        next.forEach { assertEquals(20, it.size) }
        val allIds = next.flatten()
        assertEquals("no duplicates across tiers", 60, allIds.toSet().size)
        assertEquals("same 60 teams as before", teams.map { it.id }.toSet(), allIds.toSet())

        // Top 3 of Série B must appear in next Série A; bottom 3 of A must not.
        val promotedBtoA = s.divisions[1].record.standings.take(3).map { it.teamId }
        assertTrue("B promotees land in A", next[0].containsAll(promotedBtoA))
        val relegatedAtoB = s.divisions[0].record.standings.takeLast(3).map { it.teamId }
        assertTrue("A relegated leave A", next[0].none { it in relegatedAtoB })
    }

    @Test
    fun `advancing a season moves the user by their outcome and rolls the year`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 42, random = Random(3))
        val prevTier = CareerEngine.userDivision(career.season, career.controlledTeamId).tier

        val next = CareerEngine.advanceSeason(career, registry)
        assertEquals("year advances", career.season.year + 1, next.season.year)
        assertEquals("one season recorded", 1, next.history.size)

        val nextTier = CareerEngine.userDivision(next.season, next.controlledTeamId).tier
        val expectedTier = when (next.history[0].userOutcome) {
            UserOutcome.PROMOTED -> prevTier - 1
            UserOutcome.RELEGATED -> prevTier + 1
            UserOutcome.STAYED -> prevTier
        }
        assertEquals("user ends in the tier their outcome implies", expectedTier, nextTier)
    }
}
