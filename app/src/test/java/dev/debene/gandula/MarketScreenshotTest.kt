package dev.debene.gandula

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Roster
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.data.TeamRepository
import dev.debene.gandula.ui.AgentRow
import dev.debene.gandula.ui.MarketScreenContent
import dev.debene.gandula.ui.SquadRow
import dev.debene.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** Renders the transfer market (season complete → open) from a real career. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class MarketScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun market_screen_screenshot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val teams = TeamRepository.loadTeams(app)
        val registry = teams.associateBy { it.id }
        var career = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        repeat(CareerEngine.totalRounds(career.season)) { career = CareerEngine.revealNextRound(career, registry) }
        // Sign two reinforcements so the transfer-history section shows.
        repeat(2) {
            val pick = TransferMarket.availableAgents(career, registry).maxByOrNull { TransferMarket.playerOverall(it) }!!
            career = TransferMarket.buy(career, registry, pick)
        }

        val squad = Roster.workingRoster(career, registry).sortedBy { it.position.ordinal }.map {
            SquadRow(it, TransferMarket.playerPrice(it, TransferMarket.Kind.SELL), TransferMarket.canSell(career, registry, it.id).ok)
        }
        val finalCareer = career
        val agents = TransferMarket.availableAgents(career, registry).map {
            AgentRow(
                it,
                TransferMarket.playerPrice(it, TransferMarket.Kind.BUY),
                TransferMarket.canBuy(finalCareer, registry, TransferMarket.playerPrice(it, TransferMarket.Kind.BUY)).ok,
                TransferMarket.scoutReport(it, Roster.workingRoster(finalCareer, registry)).delta,
            )
        }

        composeTestRule.setContent {
            MyApplicationTheme {
                MarketScreenContent(
                    money = career.money,
                    marketOpen = true,
                    squad = squad,
                    agents = agents,
                    transfers = career.transfers,
                    onBuy = {},
                    onSell = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/gandula_market.png")
    }
}
