package dev.debene.gandula.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.domain.Player
import dev.debene.ui.theme.GandulaMono
import dev.debene.ui.theme.NeonCyan

/**
 * Pick the starting eleven by tap-to-swap: tap a starter to select it, then tap a
 * reserve to swap them in. Always keeps exactly 11 starters, so the result is
 * always fieldable. [onChange] fires with the new XI (player ids) after each swap.
 */
@Composable
fun LineupEditor(
    squad: List<Player>,
    xi: List<Int>,
    onChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val byId = squad.associateBy { it.id }
    val starters = xi.mapNotNull { byId[it] }.sortedWith(compareBy({ it.position.ordinal }, { -TransferMarket.playerOverall(it) }))
    val xiSet = xi.toSet()
    val reserves = squad.filter { it.id !in xiSet }.sortedWith(compareBy({ it.position.ordinal }, { -TransferMarket.playerOverall(it) }))

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            if (selected == null) "Toque num titular, depois num reserva para trocar"
            else "Agora toque no reserva que entra",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Titulares (${starters.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        starters.forEach { p ->
            LineupRow(p, isSelected = selected == p.id, isStarter = true) {
                selected = if (selected == p.id) null else p.id
            }
        }
        Text("Reservas (${reserves.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        reserves.forEach { p ->
            LineupRow(p, isSelected = false, isStarter = false, dimmed = selected == null) {
                val off = selected ?: return@LineupRow
                onChange(xi.map { if (it == off) p.id else it })
                selected = null
            }
        }
    }
}

@Composable
private fun LineupRow(
    player: Player,
    isSelected: Boolean,
    isStarter: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        isSelected -> NeonCyan.copy(alpha = 0.25f)
        isStarter -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .then(if (isSelected) Modifier.border(1.dp, NeonCyan, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            player.position.name,
            fontFamily = GandulaMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dimmed) 0.4f else 1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Text(
            player.name,
            fontSize = 13.sp,
            fontWeight = if (isStarter) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.5f else 1f),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${TransferMarket.playerOverall(player)}",
            fontFamily = GandulaMono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.5f else 1f),
        )
    }
}
