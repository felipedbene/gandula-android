package dev.debene.gandula

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.debene.gandula.career.Contracts
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contracts: deterministic demands + boundary resolution by temperament. */
class ContractsTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `demands are deterministic and capped`() {
        val squad = loadTeams().first().roster
        val a = Contracts.endOfSeasonDemands(2026, 2027, squad, emptyMap())
        val b = Contracts.endOfSeasonDemands(2026, 2027, squad, emptyMap())
        assertEquals("same seed/year/squad → same demands", a, b)
        assertTrue("capped at MAX_DEMANDS", a.size <= Contracts.MAX_DEMANDS)
    }

    @Test
    fun `accepting a raise bumps the contract multiplier and keeps the player`() {
        val squad = loadTeams().first().roster
        val d = Contracts.endOfSeasonDemands(2026, 2027, squad, emptyMap()).firstOrNull() ?: return
        val res = Contracts.resolve(squad, emptyMap(), listOf(d), mapOf(d.playerId to true))
        assertEquals("multiplier set to target", d.targetMult, res.multipliers[d.playerId])
        assertTrue("player stays", res.roster.any { it.id == d.playerId })
        assertEquals("no fee on a signed raise", 0L, res.feesEarned)
    }

    @Test
    fun `a refused mercenary leaves with a fee while a refused loyal sulks but stays`() {
        val pool = loadTeams().flatMap { it.roster }
        // Synthesize one of each temperament from the league pool.
        val merc = pool.first { Contracts.isMercenary(2026, it.id) }
        val loyal = pool.first { !Contracts.isMercenary(2026, it.id) }
        val squad = listOf(merc, loyal)
        val dMerc = Contracts.Demand(merc.id, merc.name, merc.position, TransferMarket.playerOverall(merc), "raise", 1.0, 1.3, 1_000_000, true)
        val dLoyal = Contracts.Demand(loyal.id, loyal.name, loyal.position, TransferMarket.playerOverall(loyal), "raise", 1.0, 1.3, 0, false)

        val res = Contracts.resolve(squad, emptyMap(), listOf(dMerc, dLoyal), mapOf(dMerc.playerId to false, dLoyal.playerId to false))

        assertFalse("mercenary left", res.roster.any { it.id == merc.id })
        assertEquals("banked the mercenary's fee", dMerc.fee, res.feesEarned)
        assertTrue("loyal stayed", res.roster.any { it.id == loyal.id })
        val sulked = res.roster.first { it.id == loyal.id }
        assertTrue("loyal sulked (weaker)", TransferMarket.playerOverall(sulked) < TransferMarket.playerOverall(loyal))
    }
}
