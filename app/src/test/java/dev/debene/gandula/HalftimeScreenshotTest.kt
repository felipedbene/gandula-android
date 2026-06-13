package dev.debene.gandula

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import dev.debene.gandula.career.SeasonTactics
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tactics
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width
import dev.debene.gandula.ui.CareerViewModel
import dev.debene.gandula.ui.HalftimeCard
import dev.debene.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the live half-time decision card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class HalftimeScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun halftime_card_screenshot() {
        val prompt = CareerViewModel.HalftimePrompt(
            round = 14,
            homeName = "Esporte Fortalezense United",
            awayName = "Tubarão EC",
            userIsHome = true,
            userGoals = 0,
            oppGoals = 1,
            base = SeasonTactics(Formation.F433, Tactics(Mentality.Balanced, Tempo.Normal, Pressing.Medium, Width.Normal)),
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                HalftimeCard(prompt, onConfirm = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/gandula_halftime.png")
    }
}
