package dev.debene.gandula.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.debene.gandula.domain.Team

@Composable
fun MatchScreen(modifier: Modifier = Modifier, vm: MatchViewModel = viewModel()) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Gandula",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (vm.teams.isEmpty()) {
            Text("Não foi possível carregar os times (assets/teams.json).")
            return@Column
        }

        TeamPicker("Mandante", vm.teams, vm.homeIndex, vm::selectHome)
        TeamPicker("Visitante", vm.teams, vm.awayIndex, vm::selectAway)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.seedText,
                onValueChange = vm::setSeed,
                label = { Text("Semente") },
                singleLine = true,
                modifier = Modifier.width(140.dp),
            )
            Button(onClick = vm::play) { Text("Jogar") }
        }

        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        vm.match?.let { match ->
            val home = vm.teams.firstOrNull { it.id == match.home }?.name ?: "Mandante"
            val away = vm.teams.firstOrNull { it.id == match.away }?.name ?: "Visitante"
            HorizontalDivider()
            // The friendly plays straight through (the half-time whistle just shows
            // as a feed line); a fresh seed/teams resets the broadcast via its key.
            MatchBroadcast(
                homeName = home, homeId = match.home,
                awayName = away, awayId = match.away,
                events = match.events,
                startMinute = 0,
                baselineHome = 0,
                baselineAway = 0,
                onDone = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "SEMENTE ${match.seed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TeamPicker(label: String, teams: List<Team>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(teams.getOrNull(selected)?.name ?: "—", modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                teams.forEachIndexed { i, team ->
                    DropdownMenuItem(
                        text = { Text(team.name) },
                        onClick = { onSelect(i); expanded = false },
                    )
                }
            }
        }
    }
}
