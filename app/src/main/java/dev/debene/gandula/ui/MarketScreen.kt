package dev.debene.gandula.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.domain.Player

data class SquadRow(val player: Player, val sellPrice: Long, val canSell: Boolean)
data class AgentRow(val player: Player, val buyPrice: Long, val canBuy: Boolean, val scoutDelta: Int)

@Composable
fun MarketScreen(modifier: Modifier = Modifier, vm: CareerViewModel = viewModel()) {
    val career = vm.career
    if (vm.loading || career == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    MarketScreenContent(
        modifier = modifier,
        money = career.money,
        marketOpen = vm.marketOpen,
        squad = vm.squad.map { SquadRow(it, vm.sellPrice(it), vm.canSell(it)) },
        agents = vm.freeAgents.map { AgentRow(it, vm.buyPrice(it), vm.canBuy(it), vm.scout(it).delta) },
        transfers = vm.sessionTransfers,
        onBuy = vm::buy,
        onSell = vm::sell,
    )
}

@Composable
fun MarketScreenContent(
    money: Long,
    marketOpen: Boolean,
    squad: List<SquadRow>,
    agents: List<AgentRow>,
    onBuy: (Player) -> Unit,
    onSell: (Player) -> Unit,
    modifier: Modifier = Modifier,
    transfers: List<dev.debene.gandula.career.TransferRecord> = emptyList(),
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Mercado de transferências", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Caixa: ${formatMoneyM(money)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (marketOpen) "Mercado aberto — reforços jogam na próxima temporada."
                    else "O mercado abre no fim da temporada.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (marketOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (transfers.isNotEmpty()) {
            SectionHeader("Negócios desta temporada (${transfers.size})")
            transfers.forEach { t ->
                val arrow = if (t.kind == "buy") "↓ Comprou" else "↑ Vendeu"
                Text(
                    "$arrow ${t.position.name} ${t.playerName} — ${formatMoneyM(t.price)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (t.kind == "buy") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        SectionHeader("Elenco (${squad.size})")
        squad.forEach { row ->
            PlayerLine(
                player = row.player,
                trailing = "${formatMoneyM(row.sellPrice)}",
                actionLabel = "Vender",
                enabled = row.canSell,
                onClick = { onSell(row.player) },
            )
        }

        SectionHeader("Agentes livres (${agents.size})")
        agents.forEach { row ->
            PlayerLine(
                player = row.player,
                trailing = (if (row.scoutDelta >= 0) "+${row.scoutDelta}" else "${row.scoutDelta}") + " · ${formatMoneyM(row.buyPrice)}",
                actionLabel = "Comprar",
                enabled = row.canBuy,
                onClick = { onBuy(row.player) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PlayerLine(player: Player, trailing: String, actionLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(player.position.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(36.dp))
        Text("${TransferMarket.playerOverall(player)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(28.dp))
        Text(player.name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(trailing, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(110.dp))
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(actionLabel, fontSize = 12.sp) }
    }
}

private fun formatMoneyM(n: Long): String {
    val sign = if (n < 0) "-" else ""
    val digits = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$sign$ $digits"
}
