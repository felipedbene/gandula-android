package dev.debene.gandula.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.debene.ui.theme.GlassBorder
import dev.debene.ui.theme.GlowBackground
import dev.debene.ui.theme.IndigoLight
import dev.debene.ui.theme.SlateNavBg
import dev.debene.ui.theme.TextSlate400

/** App root: the launch hero, then the tabbed app once the user enters. */
@Composable
fun GandulaRoot(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    if (started) {
        GandulaApp(modifier, initialTab = 1) // land on Carreira
    } else {
        GandulaSplash(modifier, onStart = { started = true })
    }
}

/** Top-level app shell: a branded header, the screen, and a frosted bottom nav. */
@Composable
fun GandulaApp(modifier: Modifier = Modifier, initialTab: Int = 0) {
    var tab by remember { mutableIntStateOf(initialTab) }
    val items = listOf(
        Triple("Partida", Icons.Filled.SportsSoccer, 0),
        Triple("Jogo", Icons.Filled.Stadium, 1),
        Triple("Tabela", Icons.Filled.Leaderboard, 2),
        Triple("Finanças", Icons.Filled.Payments, 3),
        Triple("Elenco", Icons.Filled.Groups, 4),
    )
    GlowBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            // Brand bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BallCrest(size = 26.dp)
                Wordmark()
                Text(
                    "· Elifoot Homage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    0 -> MatchScreen(modifier = Modifier.fillMaxSize())
                    1 -> CareerScreen(modifier = Modifier.fillMaxSize())
                    2 -> CareerTableScreen(modifier = Modifier.fillMaxSize())
                    3 -> CareerFinanceScreen(modifier = Modifier.fillMaxSize())
                    else -> MarketScreen(modifier = Modifier.fillMaxSize())
                }
            }
            GlassBottomNav(selected = tab, items = items, onSelect = { tab = it })
        }
    }
}

@Composable
private fun GlassBottomNav(
    selected: Int,
    items: List<Triple<String, ImageVector, Int>>,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(SlateNavBg)) {
        HorizontalDivider(thickness = 1.dp, color = GlassBorder)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (title, icon, index) ->
                val active = selected == index
                val tint = if (active) IndigoLight else TextSlate400
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSelect(index) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Glowing active indicator pill above the icon.
                    Box(
                        Modifier.size(width = 22.dp, height = 3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (active) IndigoLight else Color.Transparent),
                    )
                    Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
                    Text(
                        title,
                        color = tint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
