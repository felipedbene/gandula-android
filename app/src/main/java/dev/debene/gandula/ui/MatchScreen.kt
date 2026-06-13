package dev.debene.gandula.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.debene.ui.theme.GandulaMono
import dev.debene.gandula.domain.Match
import dev.debene.gandula.domain.MatchEvent
import dev.debene.gandula.domain.MatchEventKind
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
            ScoreHeader(match, vm.teams)
            HorizontalDivider()
            MatchFeed(match, Modifier.fillMaxWidth())
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

@Composable
private fun ScoreHeader(match: Match, teams: List<Team>) {
    val home = teams.firstOrNull { it.id == match.home }?.name ?: "Mandante"
    val away = teams.firstOrNull { it.id == match.away }?.name ?: "Visitante"
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SideCrest(home, match.home, Modifier.weight(1f))
                Text(
                    "${match.result.homeGoals} : ${match.result.awayGoals}",
                    fontFamily = GandulaMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                SideCrest(away, match.away, Modifier.weight(1f))
            }
            Text(
                "semente ${match.seed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SideCrest(name: String, id: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ClubCrest(name, id, size = 40.dp)
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MatchFeed(match: Match, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(match.events) { event -> FeedLine(event) }
    }
}

@Composable
private fun FeedLine(event: MatchEvent) {
    val isGoal = event.kind is MatchEventKind.Goal
    val isWhistle = event.kind is MatchEventKind.HalfTime || event.kind is MatchEventKind.FullTime
    val color = when {
        isGoal -> MaterialTheme.colorScheme.primary
        event.kind is MatchEventKind.RedCard -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = event.text,
        color = color,
        fontFamily = GandulaMono,
        fontSize = 13.sp,
        fontWeight = if (isGoal || isWhistle) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    )
}
