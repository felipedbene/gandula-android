package dev.debene.gandula

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import dev.debene.gandula.ui.GandulaSplash
import dev.debene.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the launch hero / splash. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class SplashScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun splash_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme { GandulaSplash(onStart = {}) }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/gandula_splash.png")
    }
}
