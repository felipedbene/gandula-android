package dev.debene.gandula

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import dev.debene.gandula.ui.MatchScreen
import dev.debene.gandula.ui.MatchViewModel
import dev.debene.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real [MatchScreen] under Robolectric with a match already played
 * from the bundled `teams.json`, and captures a screenshot. Headless proof that
 * the Compose UI + asset loading + engine compose into a working screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GandulaMatchScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun match_screen_screenshot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MatchViewModel(app)
        vm.play() // home=0, away=1, seed=1998 from defaults

        composeTestRule.setContent {
            MyApplicationTheme { MatchScreen(vm = vm) }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/gandula_match.png")
    }
}
