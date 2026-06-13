package dev.debene.gandula

import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Finances
import dev.debene.gandula.career.Roster
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Team
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Transfer-market + roster-overlay tests against the real world (pure JVM). */
class TransferMarketTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `free agent pool is deterministic with the expected composition`() {
        val a = TransferMarket.generateFreeAgents(careerSeed = 1998, year = 2026)
        val b = TransferMarket.generateFreeAgents(careerSeed = 1998, year = 2026)
        assertEquals("same seed+year → same pool", a, b)
        assertEquals(TransferMarket.POOL_SIZE, a.size)
        assertEquals(2, a.count { it.position == Position.GK })
        assertEquals(4, a.count { it.position == Position.DEF })
        assertEquals(4, a.count { it.position == Position.MID })
        assertEquals(2, a.count { it.position == Position.FWD })
        assertEquals("unique ids", a.size, a.map { it.id }.toSet().size)

        val next = TransferMarket.generateFreeAgents(careerSeed = 1998, year = 2027)
        assertTrue("next year's ids don't collide", a.map { it.id }.toSet().intersect(next.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `sell price is the resale haircut of the buy price`() {
        val pool = TransferMarket.generateFreeAgents(1998, 2026)
        pool.forEach { p ->
            val buy = TransferMarket.playerPrice(p, TransferMarket.Kind.BUY)
            val sell = TransferMarket.playerPrice(p, TransferMarket.Kind.SELL)
            assertTrue("sell ($sell) < buy ($buy)", sell < buy)
        }
    }

    @Test
    fun `buying then selling restores cash and roster`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        val agent = TransferMarket.availableAgents(career, registry).first()

        val rosterBefore = Roster.workingRoster(career, registry).size
        val moneyBefore = career.money
        val buyPrice = TransferMarket.playerPrice(agent, TransferMarket.Kind.BUY)

        val bought = TransferMarket.buy(career, registry, agent)
        assertEquals("cash debited", moneyBefore - buyPrice, bought.money)
        assertEquals("roster grew", rosterBefore + 1, Roster.workingRoster(bought, registry).size)
        assertTrue("player on the squad", Roster.userTeam(bought, registry).roster.any { it.id == agent.id })

        val sold = TransferMarket.sell(bought, registry, agent)
        assertEquals("cash credited back the haircut", bought.money + TransferMarket.playerPrice(agent, TransferMarket.Kind.SELL), sold.money)
        assertEquals("roster restored", rosterBefore, Roster.workingRoster(sold, registry).size)
    }

    @Test
    fun `bought players raise the wage bill and join the squad that plays`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 7, random = Random(1))
        val wagesBefore = Finances.seasonSalary(career, registry)

        val agent = TransferMarket.availableAgents(career, registry).first()
        val bought = TransferMarket.buy(career, registry, agent)
        assertTrue("wage bill rose", Finances.seasonSalary(bought, registry) > wagesBefore)

        // The overlay flows into the team buildSeason simulates against.
        val nextSeasonUserTeam = Roster.userTeam(bought, registry)
        assertTrue("reinforcement is rostered for next season", nextSeasonUserTeam.roster.any { it.id == agent.id })
    }

    @Test
    fun `guards block selling a starter and over-spending`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 3, random = Random(2))
        val starterId = Roster.userTeam(career, registry).startingXi.first()
        assertFalse("can't sell an XI starter", TransferMarket.canSell(career, registry, starterId).ok)

        val pricey = career.copy(money = 0)
        val agent = TransferMarket.availableAgents(career, registry).first()
        assertFalse("can't buy with no cash", TransferMarket.canBuy(pricey, registry, TransferMarket.playerPrice(agent, TransferMarket.Kind.BUY)).ok)
    }
}
