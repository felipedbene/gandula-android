package dev.debene.gandula

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.debene.gandula.domain.Team
import dev.debene.gandula.ui.PitchLineupEditor
import dev.debene.ui.theme.GlowBackground
import dev.debene.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PitchScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun pitch_lineup_screenshot() {
        val team = loadTeams().first()
        composeTestRule.setContent {
            MyApplicationTheme {
                GlowBackground {
                    PitchLineupEditor(
                        squad = team.roster,
                        xi = team.startingXi,
                        formation = team.formation,
                        onChange = {},
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/gandula_pitch.png")
    }
}
