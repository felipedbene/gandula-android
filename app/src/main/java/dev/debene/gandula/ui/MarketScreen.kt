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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import dev.debene.ui.theme.GradientStart
import dev.debene.ui.theme.GradientEnd
import dev.debene.ui.theme.NeonCyan
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.debene.ui.theme.GandulaMono
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
        marketOpen = vm.transfersOpen,
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
        val gradient = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(Modifier.fillMaxWidth().background(gradient).padding(16.dp)) {
                Column {
                    Text("Mercado de transferências", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Caixa: ${formatMoneyM(money)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NeonCyan)
                    Text(
                        if (marketOpen) "Mercado aberto — reforços entram a partir da próxima rodada."
                        else "Mercado fechado.",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (marketOpen) Color.White else Color.White.copy(alpha = 0.7f),
                    )
                }
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
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PlayerLine(player: Player, trailing: String, actionLabel: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = dev.debene.ui.theme.GlassBody),
        modifier = Modifier.fillMaxWidth().border(1.dp, dev.debene.ui.theme.GlassBorder, RoundedCornerShape(14.dp))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${TransferMarket.playerOverall(player)}", fontFamily = GandulaMono, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(player.position.name, fontFamily = GandulaMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(player.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Text(trailing, fontFamily = GandulaMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp)) {
                Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatMoneyM(n: Long): String {
    val sign = if (n < 0) "-" else ""
    val digits = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$sign$ $digits"
}
