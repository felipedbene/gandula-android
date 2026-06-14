package dev.debene.gandula

import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.SeasonTactics
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tactics
import dev.debene.gandula.domain.Team
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width
import dev.debene.gandula.engine.MatchEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Half-split engine + live half-time tactics tests. */
class HalftimeTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `first half plus second half is byte-identical to the one-shot match`() {
        val teams = loadTeams()
        val home = teams[0]
        val away = teams[1]
        val oneShot = MatchEngine.simulate(home, away, 42)
        val half = MatchEngine.simulateFirstHalf(home, away, 42)
        val composed = MatchEngine.simulateSecondHalf(half, home, away)
        assertEquals("split must equal the one-shot", oneShot, composed)
    }

    @Test
    fun `a second-half tactics change keeps the first half but is deterministic`() {
        val teams = loadTeams()
        val home = teams[0]
        val away = teams[1]
        val edited = home.copy(tactics = home.tactics.copy(mentality = Mentality.VeryAttacking, tempo = Tempo.Fast))

        val steered = MatchEngine.simulateSecondHalf(MatchEngine.simulateFirstHalf(home, away, 42), edited, away)
        val steered2 = MatchEngine.simulateSecondHalf(MatchEngine.simulateFirstHalf(home, away, 42), edited, away)
        assertEquals("same change → same steered match", steered, steered2)

        val unchanged = MatchEngine.simulate(home, away, 42)
        // The first half (events up to and including the interval) is untouched.
        assertEquals(
            "first half is identical regardless of the second-half change",
            unchanged.events.filter { it.minute <= 45 },
            steered.events.filter { it.minute <= 45 },
        )
    }

    @Test
    fun `a half-time substitution changes the second half but not simulate()`() {
        val teams = loadTeams()
        val home = teams[0]
        val away = teams[1]

        // Sub the first bench player on for the last starter, at the interval.
        val off = home.startingXi.last()
        val on = home.bench.first()
        val subs = listOf(off to on)

        val withSub = MatchEngine.simulateSecondHalf(MatchEngine.simulateFirstHalf(home, away, 42), home, away, homeSubs = subs)
        val withSub2 = MatchEngine.simulateSecondHalf(MatchEngine.simulateFirstHalf(home, away, 42), home, away, homeSubs = subs)
        assertEquals("same sub → same match (deterministic)", withSub, withSub2)

        // The substitution is narrated in the second half…
        assertTrue(
            "the sub shows up as an event",
            withSub.events.any { e -> (e.kind as? dev.debene.gandula.domain.MatchEventKind.Substitution)?.let { it.off == off && it.on == on } == true },
        )

        // …and the no-sub path is byte-identical to the one-shot simulate().
        val noSub = MatchEngine.simulateSecondHalf(MatchEngine.simulateFirstHalf(home, away, 42), home, away)
        assertEquals("no subs → identical to simulate()", MatchEngine.simulate(home, away, 42), noSub)
    }

    @Test
    fun `live half-time patch reproduces exactly on re-simulation (load path)`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        val override = SeasonTactics(Formation.F4231, Tactics(Mentality.VeryAttacking, Tempo.Fast, Pressing.High, Width.Wide))

        // Live: confirm a half-time change in round 0 (the user plays every round).
        val patched = CareerEngine.applyHalftime(career, registry, round = 0, override = override)
        assertEquals(override, patched.halftimeTactics[0])

        // Load path: rebuild the season from the persisted override map.
        val tierIds = career.season.divisions.map { it.teamIds }
        val rebuilt = CareerEngine.buildSeason(
            career.seed, career.season.year, tierIds, registry,
            career.controlledTeamId, career.userRoster, career.userTactics,
            halftimeTactics = mapOf(0 to override),
        )

        val patchedDiv = CareerEngine.userDivision(patched.season, career.controlledTeamId)
        val rebuiltDiv = CareerEngine.userDivision(rebuilt, career.controlledTeamId)
        assertEquals("patched matches reproduce on load", patchedDiv.record.matches, rebuiltDiv.record.matches)
        assertEquals("patched standings reproduce on load", patchedDiv.record.standings, rebuiltDiv.record.standings)
    }
}
