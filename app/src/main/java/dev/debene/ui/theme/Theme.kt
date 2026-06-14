package dev.debene.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Gandula is always dark with the electric-blue brand — no dynamic color, no
// light variant, so the look is consistent on every device.
private val GandulaColors =
  darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = BlueContainer,
    onPrimaryContainer = OnBlueContainer,
    secondary = ElectricBlueDim,
    onSecondary = Color.White,
    secondaryContainer = Surface2,
    onSecondaryContainer = TextHigh,
    tertiary = GoldTertiary,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = GoldTertiary,
    background = Ink,
    onBackground = TextHigh,
    surface = Surface1,
    onSurface = TextHigh,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    outline = OutlineDim,
    outlineVariant = OutlineDim,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerContainer,
    onErrorContainer = DangerRed,
  )

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = GandulaColors, typography = Typography, content = content)
}
