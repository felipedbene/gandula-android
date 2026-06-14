package dev.debene.gandula

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Finances
import dev.debene.gandula.data.TeamRepository
import dev.debene.gandula.ui.CareerScreenContent
import dev.debene.gandula.ui.CareerUi
import dev.debene.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Renders the career standings UI mid-season from a real, simulated career —
 * headless proof that divisions/standings/reveal compose into a working screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class CareerScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun career_screen_screenshot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val teams = TeamRepository.loadTeams(app)
        val registry = teams.associateBy { it.id }
        var career = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        repeat(20) { career = CareerEngine.revealNextRound(career, registry) }

        val div = CareerEngine.userDivision(career.season, career.controlledTeamId)
        val standings = CareerEngine.standingsUpTo(career.season, div)
        val nameOf: (Int) -> String = { id -> teams.firstOrNull { it.id == id }?.name ?: "Time $id" }

        composeTestRule.setContent {
            MyApplicationTheme {
                CareerScreenContent(
                    ui = CareerUi(
                        club = nameOf(career.controlledTeamId),
                        division = div.name,
                        year = career.season.year,
                        money = career.money,
                        fanbase = career.fanbase,
                        stadiumCapacity = career.stadiumCapacity,
                        round = career.season.currentRoundIdx,
                        total = CareerEngine.totalRounds(career.season),
                        seasonComplete = false,
                        fired = career.fired,
                        standings = standings,
                        controlledTeamId = career.controlledTeamId,
                        lastSeason = null,
                        ledger = Finances.seasonToDateLedger(career, registry),
                        runway = Finances.projectSeasonRunway(career, registry),
                        expansionCost = Finances.expansionCost(career.stadiumCapacity),
                        marketingCost = Finances.marketingCost(career.marketingMomentum),
                        canExpand = true,
                        canCampaign = true,
                        copaStatus = "Eliminado na Fase de 32",
                        copaChampion = null,
                        live = false,
                    ),
                    nameOf = nameOf,
                    onPlayRound = {},
                    onNextSeason = {},
                    onRestart = {},
                    onExpand = {},
                    onCampaign = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/gandula_career.png")
    }
}
