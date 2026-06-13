package dev.debene.gandula

import dev.debene.gandula.career.Aging
import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Divisions
import dev.debene.gandula.career.Regen
import dev.debene.gandula.career.RivalCoach
import dev.debene.gandula.career.Roster
import dev.debene.gandula.domain.Attributes
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Team
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Opponent-evolution tests (aging + regen + rival coach) against the real world. */
class EvolutionTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    private fun player(id: Int, age: Int, attr: Int) =
        Player(id, "P$id", age, Position.MID, Attributes(attr, attr, attr, attr, attr, attr))

    @Test
    fun `aging develops the young and declines veterans within bounds`() {
        val young = Aging.agePlayer(player(1, age = 19, attr = 60))
        assertEquals(20, young.age)
        assertEquals("young player grows", 61, young.attributes.pace)

        val vet = Aging.agePlayer(player(2, age = 38, attr = 60))
        assertEquals(39, vet.age)
        assertEquals("veteran declines by 3", 57, vet.attributes.pace)

        val floored = Aging.agePlayer(player(3, age = 40, attr = 26))
        assertEquals("decline floors at 25", 25, floored.attributes.pace)
    }

    @Test
    fun `evolveTeam stays engine-valid and is deterministic`() {
        val teams = loadTeams()
        val team = teams.first()
        val a = Regen.evolveTeam(team, seasons = 6, careerSeed = 1998)
        val b = Regen.evolveTeam(team, seasons = 6, careerSeed = 1998)
        assertEquals("same inputs → identical evolution", a, b)

        assertEquals("XI is 11", 11, a.startingXi.size)
        assertEquals("XI is distinct", 11, a.startingXi.toSet().size)
        assertTrue("XI all rostered", a.startingXi.all { id -> a.roster.any { it.id == id } })
        assertTrue("bench all rostered & off the XI", a.bench.all { id -> a.roster.any { it.id == id } && id !in a.startingXi })
        assertTrue("nobody is over the retirement age still on the pitch unaged", a.roster.all { it.age <= Aging.MAX_AGE })
    }

    @Test
    fun `rival coach strengthens an opponent squad`() {
        val teams = loadTeams()
        // A Série C-strength club coached at tier 2 (6M budget) buys upgrades.
        val base = Divisions.divideIntoDivisions(teams)[2].first()
        val evolved = Regen.evolveTeam(base, seasons = 1, careerSeed = 7)
        val coached = RivalCoach.applyRivalCoach(evolved, tier = 2, year = 2027, careerSeed = 7, yearOffset = 1)
        assertTrue("coach bought reinforcements", coached.roster.size > base.roster.size)
        assertTrue("coached XI is stronger", Divisions.avgStrength(coached) >= Divisions.avgStrength(evolved))
    }

    @Test
    fun `the league as a whole strengthens over several seasons`() {
        val teams = loadTeams()
        val baseMean = teams.map { Divisions.avgStrength(it) }.average()
        val evolvedMean = teams.map {
            val ev = Regen.evolveTeam(it, seasons = 5, careerSeed = 1998)
            Divisions.avgStrength(RivalCoach.applyRivalCoach(ev, tier = 2, year = 2031, careerSeed = 1998, yearOffset = 5))
        }.average()
        println("League avg strength — registry: %.1f → after 5 coached seasons: %.1f".format(baseMean, evolvedMean))
        assertTrue("coached league ($evolvedMean) stronger than registry ($baseMean)", evolvedMean > baseMean)
    }

    @Test
    fun `a future season is reproducible (evolution is deterministic on re-sim)`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val tierIds = Divisions.divideIntoDivisions(teams).map { tier -> tier.map { it.id } }
        val s1 = CareerEngine.buildSeason(1998, 2031, tierIds, registry, tierIds[2].first(), emptyList())
        val s2 = CareerEngine.buildSeason(1998, 2031, tierIds, registry, tierIds[2].first(), emptyList())
        assertEquals(
            "same future year → identical evolved season",
            s1.divisions.map { it.record.standings },
            s2.divisions.map { it.record.standings },
        )
    }

    @Test
    fun `advancing a season ages the user's surviving players`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 42, random = Random(3))
        val before = Roster.workingRoster(career, registry)
        // A mid-career player who won't retire next season.
        val sample = before.first { it.age in 20..28 }
        repeat(CareerEngine.totalRounds(career.season)) { /* season pre-simulated; just advance */ }

        val next = CareerEngine.advanceSeason(career, registry)
        assertTrue("user roster is now materialized", next.userRoster.isNotEmpty())
        val aged = next.userRoster.firstOrNull { it.id == sample.id }
        assertTrue("surviving player aged by one season", aged != null && aged.age == sample.age + 1)
    }
}
