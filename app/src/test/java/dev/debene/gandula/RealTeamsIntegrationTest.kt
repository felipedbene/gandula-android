package dev.debene.gandula

import dev.debene.gandula.domain.Team
import dev.debene.gandula.engine.MatchEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * End-to-end check of the production data path: parse the bundled
 * `assets/teams.json` with the same Moshi setup [TeamRepository] uses, then run a
 * real match through the engine. Prints the minute-by-minute feed so the whole
 * pipeline (JSON → domain → simulation → PT-BR narration) is observable.
 */
class RealTeamsIntegrationTest {

    private fun loadTeams(): List<Team> {
        val candidates = listOf(
            File("src/main/assets/teams.json"),
            File("app/src/main/assets/teams.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("teams.json not found (looked in: ${candidates.joinToString { it.path }})")
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return moshi.adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `real teams parse and simulate deterministically`() {
        val teams = loadTeams()
        assertTrue("expected several teams in teams.json", teams.size >= 2)

        val home = teams[0]
        val away = teams[1]
        val m1 = MatchEngine.simulate(home, away, 1998)
        val m2 = MatchEngine.simulate(home, away, 1998)
        assertEquals("same seed must be deterministic on real data", m1, m2)

        println("=== ${home.name} ${m1.result.homeGoals} x ${m1.result.awayGoals} ${away.name} (semente 1998) ===")
        m1.events.forEach { println(it.text) }
    }
}
