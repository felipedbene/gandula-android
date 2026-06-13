package dev.debene.gandula.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

/** Top-level app shell: a branded header + three tabs. */
@Composable
fun GandulaApp(modifier: Modifier = Modifier, initialTab: Int = 0) {
    var tab by remember { mutableIntStateOf(initialTab) }
    val titles = listOf("Partida", "Carreira", "Elenco")
    Column(modifier.fillMaxSize()) {
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
        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            titles.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) },
                )
            }
        }
        when (tab) {
            0 -> MatchScreen(modifier = Modifier.fillMaxSize())
            1 -> CareerScreen(modifier = Modifier.fillMaxSize())
            else -> MarketScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
