package dev.debene.gandula.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Top-level app shell: two tabs — exhibition match and career mode. */
@Composable
fun GandulaApp(modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Partida", "Carreira", "Elenco")
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
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
