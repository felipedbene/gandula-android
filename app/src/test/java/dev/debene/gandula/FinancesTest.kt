package dev.debene.gandula

import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Finances
import dev.debene.gandula.career.Promotion
import dev.debene.gandula.domain.Team
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/** Economy invariants against the real world (pure JVM). */
class FinancesTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `per-round slices sum exactly to the season totals`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        val total = CareerEngine.totalRounds(career.season)
        val tier = CareerEngine.userDivision(career.season, career.controlledTeamId).tier

        val tvSum = (0 until total).sumOf { Finances.tvIncomeForRound(career, it) }
        assertEquals("TV slices sum to the tier deal", Finances.tvDeal(tier), tvSum)

        val sponsorSum = (0 until total).sumOf { Finances.sponsorshipForRound(career, it) }
        assertEquals("sponsorship slices sum to the floor", Finances.sponsorshipFloor(career), sponsorSum)

        val wageSum = (0 until total).sumOf { Finances.salarySliceForRound(career, registry, it) }
        assertEquals("wage slices sum to the season salary", Finances.seasonSalary(career, registry), wageSum)
    }

    @Test
    fun `playing a full season moves cash by exactly the season ledger net`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        var career = CareerEngine.newCareer(teams, seed = 7, random = Random(1))
        val before = career.money
        val total = CareerEngine.totalRounds(career.season)
        repeat(total) { career = CareerEngine.revealNextRound(career, registry) }

        val ledger = Finances.seasonToDateLedger(career, registry)
        assertEquals("accrued cash == ledger net", ledger.net, career.money - before)
        assertEquals("ledger counts every round", total, ledger.rounds)
    }

    @Test
    fun `season boundary adds exactly placement prize plus PR bonus`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        var career = CareerEngine.newCareer(teams, seed = 42, random = Random(3))
        repeat(CareerEngine.totalRounds(career.season)) { career = CareerEngine.revealNextRound(career, registry) }

        val s = career.season
        val pr = Promotion.compute(
            s.divisions[0].record.standings, s.divisions[1].record.standings, s.divisions[2].record.standings,
            career.controlledTeamId,
        )
        val outcome = Promotion.userOutcome(pr)
        val finances = Finances.computeSeasonFinances(career, registry, outcome)

        val endOfSeasonMoney = career.money
        val next = CareerEngine.advanceSeason(career, registry)
        assertEquals(
            "boundary only adds placement + PR (per-round cash already banked)",
            endOfSeasonMoney + finances.prBonus + finances.placementPrize,
            next.money,
        )
    }

    @Test
    fun `fanbase drift is bounded by the per-season step`() {
        for (pos in intArrayOf(1, 5, 10, 18)) {
            val before = 10_000
            val after = Finances.nextFanbase(before, tier = 3, position = pos, marketingMomentum = 0)
            assertTrue("step bounded", abs(after - before) <= Finances.FANBASE_MAX_STEP)
            assertTrue("non-negative", after >= 0)
        }
    }
}
