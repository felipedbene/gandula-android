package dev.debene.gandula

import dev.debene.gandula.domain.Attributes
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tactics
import dev.debene.gandula.domain.Team
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width
import dev.debene.gandula.engine.MatchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Determinism + statistical-sanity tests for the ported engine — the Kotlin
 * analogue of upstream `core/tests/determinism.rs` and `stronger_wins.rs`.
 * Pure JVM (no Android), so they run fast under `testDebugUnitTest`.
 */
class MatchEngineTest {

    private fun testTeam(name: String, teamId: Int, base: Int): Team {
        val roster = (1..11).map { i ->
            Player(
                id = teamId * 100 + i,
                name = "J$teamId$i",
                age = 25,
                position = when (i) {
                    1 -> Position.GK
                    in 2..5 -> Position.DEF
                    in 6..8 -> Position.MID
                    else -> Position.FWD
                },
                attributes = Attributes(
                    pace = base, technique = base, passing = base,
                    defending = base, finishing = base, stamina = 90,
                ),
            )
        }
        return Team(
            id = teamId,
            name = name,
            roster = roster,
            formation = Formation.F442,
            tactics = Tactics(Mentality.Balanced, Tempo.Normal, Pressing.Medium, Width.Normal),
            startingXi = (1..11).map { teamId * 100 + it },
            bench = emptyList(),
        )
    }

    @Test
    fun `same seed yields identical match`() {
        val home = testTeam("Home", 1, 70)
        val away = testTeam("Away", 2, 60)
        val m1 = MatchEngine.simulate(home, away, 42)
        val m2 = MatchEngine.simulate(home, away, 42)
        assertEquals("same seed must yield identical match", m1, m2)
    }

    @Test
    fun `different seeds diverge`() {
        val home = testTeam("Home", 1, 70)
        val away = testTeam("Away", 2, 60)
        val m1 = MatchEngine.simulate(home, away, 1)
        val m2 = MatchEngine.simulate(home, away, 2)
        assertNotEquals("different seeds should diverge", m1, m2)
    }

    @Test
    fun `stronger team wins more often over many seeds`() {
        val strong = testTeam("Strong", 1, 85)
        val weak = testTeam("Weak", 2, 55)
        var strongWins = 0
        var weakWins = 0
        for (seed in 0L until 200L) {
            val m = MatchEngine.simulate(strong, weak, seed)
            if (m.result.homeGoals > m.result.awayGoals) strongWins++
            else if (m.result.awayGoals > m.result.homeGoals) weakWins++
        }
        assertTrue("strong ($strongWins) should clearly beat weak ($weakWins)", strongWins > weakWins * 2)
    }

    @Test
    fun `match always closes with a full-time event at minute 90 or later`() {
        val home = testTeam("Home", 1, 70)
        val away = testTeam("Away", 2, 70)
        val m = MatchEngine.simulate(home, away, 7)
        val last = m.events.last()
        assertTrue("last event is full-time", last.kind == dev.debene.gandula.domain.MatchEventKind.FullTime)
        assertTrue("full-time at 90'+", last.minute >= 90)
    }
}
