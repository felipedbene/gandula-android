package dev.debene.gandula.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debene.gandula.data.CareerStore
import dev.debene.ui.theme.ElectricBlue
import dev.debene.ui.theme.GandulaMono

/**
 * Launch hero — the GANDULA wordmark, a glowing ball crest, and a single
 * primary action ("NOVA CARREIRA", or "CONTINUAR" when a save exists). Mirrors
 * gandula.debene.dev's start screen.
 */
@Composable
fun GandulaSplash(modifier: Modifier = Modifier, onStart: () -> Unit) {
    val context = LocalContext.current
    val hasSave = remember { runCatching { CareerStore.hasSave(context) }.getOrDefault(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "GANDULA",
                color = ElectricBlue,
                fontWeight = FontWeight.Black,
                fontSize = 48.sp,
                letterSpacing = 10.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "Elifoot Retro Homage Manager",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )

            Spacer(Modifier.height(36.dp))

            // Glowing crest tile.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(240.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(ElectricBlue.copy(alpha = 0.22f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    BallCrest(size = 72.dp)
                }
            }

            Spacer(Modifier.height(36.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
            ) {
                Text(
                    if (hasSave) "▶  CONTINUAR" else "▶  NOVA CARREIRA",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "v1.0 • Off-line • 100% Salvo no Aparelho",
                fontFamily = GandulaMono,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
