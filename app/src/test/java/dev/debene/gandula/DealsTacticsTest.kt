package dev.debene.gandula

import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Deal
import dev.debene.gandula.career.Deals
import dev.debene.gandula.career.Deals_
import dev.debene.gandula.career.Finances
import dev.debene.gandula.career.Roster
import dev.debene.gandula.career.SeasonTactics
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.career.UserOutcome
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tactics
import dev.debene.gandula.domain.Team
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Tests for transfer history, season tactics, and negotiable deals + scandals. */
class DealsTacticsTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `transfers are recorded, carried into history, and reset next season`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        var c = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        val agent = TransferMarket.availableAgents(c, registry).first()

        c = TransferMarket.buy(c, registry, agent)
        assertEquals(1, c.transfers.size)
        assertEquals("buy", c.transfers[0].kind)
        assertEquals(agent.name, c.transfers[0].playerName)

        repeat(CareerEngine.totalRounds(c.season)) { c = CareerEngine.revealNextRound(c, registry) }
        val next = CareerEngine.advanceSeason(c, registry)
        assertEquals("transfer banked into last season's history", 1, next.history.last().transfers.size)
        assertTrue("new season's market starts empty", next.transfers.isEmpty())
    }

    @Test
    fun `season tactics override the user team and carry to next season`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 7, random = Random(1))
        val chosen = SeasonTactics(Formation.F4231, Tactics(Mentality.VeryAttacking, Tempo.Fast, Pressing.High, Width.Wide))
        val withTac = CareerEngine.setTactics(career, chosen)

        val team = Roster.userTeam(withTac, registry)
        assertEquals(Formation.F4231, team.formation)
        assertEquals(Mentality.VeryAttacking, team.tactics.mentality)

        var c = withTac
        repeat(CareerEngine.totalRounds(c.season)) { c = CareerEngine.revealNextRound(c, registry) }
        val next = CareerEngine.advanceSeason(c, registry)
        assertEquals("tactics carry forward", chosen, next.userTactics)
        assertEquals(Formation.F4231, Roster.userTeam(next, registry).formation)
    }

    @Test
    fun `a signed deal overrides the income floor and its slices sum to the deal`() {
        val teams = loadTeams()
        val career = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        val amount = 5_000_000L
        val signed = CareerEngine.signDeal(
            career,
            Deal(id = "tv-2027-0", kind = "tv", seasonAmount = amount, startYear = career.season.year, termYears = 2),
        )
        val total = CareerEngine.totalRounds(signed.season)
        val sum = (0 until total).sumOf { Finances.tvIncomeForRound(signed, it) }
        val drop = Deals_.scandalDropRound(signed.seed, signed.season.year, "tv", total)
        if (drop == null) {
            assertEquals("no scandal → slices sum to the full deal", amount, sum)
        } else {
            // Scandal drop → pre-drop deal pro-rata + floor tail; strictly between.
            assertTrue("scandal-segmented income is between floor and deal", sum in Finances.tvFloor(signed)..amount)
        }
    }

    @Test
    fun `keepDeal drops on term expiry, relegation, and a failed clause`() {
        val deal = Deal(id = "tv-2027-0", kind = "tv", seasonAmount = 4_000_000L, startYear = 2027, termYears = 2)
        // Active in 2027/2028, expires going into 2029.
        assertEquals(deal, Deals_.keepDeal(deal, "tv", UserOutcome.STAYED, 5, nextYear = 2028))
        assertNull("term elapsed", Deals_.keepDeal(deal, "tv", UserOutcome.STAYED, 5, nextYear = 2029))
        assertNull("TV drops on relegation", Deals_.keepDeal(deal, "tv", UserOutcome.RELEGATED, 5, nextYear = 2028))

        val clauseDeal = deal.copy(maxPosition = 6)
        assertNull("clause failed (finished 9th)", Deals_.keepDeal(clauseDeal, "tv", UserOutcome.STAYED, 9, nextYear = 2028))
        assertEquals("clause met (finished 4th)", clauseDeal, Deals_.keepDeal(clauseDeal, "tv", UserOutcome.STAYED, 4, nextYear = 2028))
    }

    @Test
    fun `deal offers are deterministic and the aggressive one carries a clause`() {
        val (tvA, spA) = Deals_.generateOffers(1998, 2027, tier = 3, tvFloor = 300_000, sponsorshipFloor = 125_000)
        val (tvB, _) = Deals_.generateOffers(1998, 2027, tier = 3, tvFloor = 300_000, sponsorshipFloor = 125_000)
        assertEquals("same inputs → same slate", tvA, tvB)
        assertEquals(3, tvA.size)
        assertEquals(3, spA.size)
        val aggressive = tvA.first { it.label == "Agressiva" }
        assertTrue("aggressive pays the most", aggressive.deal.seasonAmount >= tvA.maxOf { it.deal.seasonAmount })
        assertEquals("aggressive has a Série C clause (12th)", 12, aggressive.deal.maxPosition)
        assertFalse("solid offer has no clause", tvA.first { it.label == "Sólida" }.deal.maxPosition != null)
    }
}
