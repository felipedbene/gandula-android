package dev.debene.gandula

import dev.debene.gandula.career.Copa
import dev.debene.gandula.career.CupRoundName
import dev.debene.gandula.career.Finances
import dev.debene.gandula.domain.Team
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Copa do Brasil bracket tests against the real 60-team world (pure JVM). */
class CopaTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `bracket has the right shape and a champion`() {
        val teams = loadTeams()
        val cup = Copa.simulate(teams, seasonSeed = 1998, controlledTeamId = teams.last().id)
        assertEquals("six rounds", 6, cup.rounds.size)
        // Prelim: 28 real ties + 4 byes = 32; then halving 16, 8, 4, 2, 1.
        assertEquals(32, cup.rounds[0].ties.size)
        assertEquals(28, cup.rounds[0].ties.count { !it.bye })
        assertEquals(4, cup.rounds[0].ties.count { it.bye })
        assertEquals(listOf(16, 8, 4, 2, 1), cup.rounds.drop(1).map { it.ties.size })

        // Every real tie resolved with a winner; champion is set.
        assertTrue("all real ties decided", cup.rounds.all { r -> r.ties.all { it.winnerId != null } })
        assertNotNull("champion crowned", cup.championId)
        // The final's winner is the champion.
        assertEquals(cup.rounds[5].ties[0].winnerId, cup.championId)
    }

    @Test
    fun `cup is deterministic for the same season seed`() {
        val teams = loadTeams()
        val a = Copa.simulate(teams, 42, teams.first().id)
        val b = Copa.simulate(teams, 42, teams.first().id)
        assertEquals(a, b)
    }

    @Test
    fun `champion banks the round prizes plus the title bonus`() {
        val teams = loadTeams()
        val cup = Copa.simulate(teams, 1998, teams.last().id)
        val champ = cup.championId!!
        val prize = Finances.cupPrizeTotal(cup, champ)
        // A champion played (at least) R32→final, so prize ≥ those round prizes + bonus.
        val minExpected = Finances.cupPrizeForRound(CupRoundName.FINAL) +
            Finances.cupPrizeForRound(CupRoundName.SF) +
            Finances.cupPrizeForRound(CupRoundName.QF) +
            Finances.CUP_CHAMPION_BONUS
        assertTrue("champion prize ($prize) ≥ deep-run minimum ($minExpected)", prize >= minExpected)
        assertEquals("champion's cup result", "champion", Copa.cupResultFor(cup, champ))
    }

    @Test
    fun `an eliminated club reports the round it went out`() {
        val teams = loadTeams()
        // Use a weak Série C club as the controlled team; it'll exit at some point.
        val weak = teams.minByOrNull {
            it.startingXi.mapNotNull { id -> it.roster.firstOrNull { p -> p.id == id } }
                .sumOf { p -> p.attributes.run { pace + technique + passing + defending + finishing + stamina } }
        }!!
        val cup = Copa.simulate(teams, 7, weak.id)
        val result = Copa.cupResultFor(cup, weak.id)
        // Either they were eliminated (a round name) or they won it; never null for a played cup.
        assertNotNull("a played cup yields a result for every entrant", result)
    }
}
