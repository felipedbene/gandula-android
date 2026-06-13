package dev.debene.gandula.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.debene.gandula.career.Finances
import dev.debene.gandula.career.SeasonHistory
import dev.debene.gandula.career.UserOutcome
import dev.debene.gandula.engine.TeamStats

/** Flat, immutable view-model snapshot for the (stateless) career screen. */
data class CareerUi(
    val club: String,
    val division: String,
    val year: Int,
    val money: Long,
    val fanbase: Int,
    val stadiumCapacity: Int,
    val round: Int,
    val total: Int,
    val seasonComplete: Boolean,
    val fired: Boolean,
    val standings: List<TeamStats>,
    val controlledTeamId: Int,
    val lastSeason: SeasonHistory?,
    val ledger: Finances.SeasonLedger?,
    val runway: Finances.RunwayProjection?,
    val expansionCost: Long,
    val marketingCost: Long,
    val canExpand: Boolean,
    val canCampaign: Boolean,
    val copaStatus: String,
    val copaChampion: String?,
)

@Composable
fun CareerScreen(modifier: Modifier = Modifier, vm: CareerViewModel = viewModel()) {
    val career = vm.career
    if (vm.loading || career == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Simulando temporada…")
            }
        }
        return
    }
    val div = vm.userDivision ?: return
    CareerScreenContent(
        ui = CareerUi(
            club = vm.teamName(career.controlledTeamId),
            division = div.name,
            year = career.season.year,
            money = career.money,
            fanbase = career.fanbase,
            stadiumCapacity = career.stadiumCapacity,
            round = career.season.currentRoundIdx,
            total = vm.totalRounds,
            seasonComplete = vm.seasonComplete,
            fired = career.fired,
            standings = vm.userStandings,
            controlledTeamId = career.controlledTeamId,
            lastSeason = career.history.lastOrNull(),
            ledger = vm.ledger,
            runway = vm.runway,
            expansionCost = vm.expansionCost,
            marketingCost = vm.marketingCost,
            canExpand = vm.canExpand,
            canCampaign = vm.canCampaign,
            copaStatus = vm.copaStatus,
            copaChampion = vm.copaChampionName,
        ),
        nameOf = vm::teamName,
        onPlayRound = vm::playNextRound,
        onNextSeason = vm::advanceToNextSeason,
        onRestart = vm::restart,
        onExpand = vm::expandStadium,
        onCampaign = vm::runMarketingCampaign,
        modifier = modifier,
        preSeason = { PreSeasonSection(vm) },
        halftime = { vm.halftimePrompt?.let { HalftimeCard(it, vm::confirmHalftime) } },
    )
}

@Composable
internal fun HalftimeCard(prompt: CareerViewModel.HalftimePrompt, onConfirm: (dev.debene.gandula.career.SeasonTactics?) -> Unit) {
    var formation by remember(prompt) { mutableStateOf(prompt.base.formation) }
    var mentality by remember(prompt) { mutableStateOf(prompt.base.tactics.mentality) }
    var tempo by remember(prompt) { mutableStateOf(prompt.base.tactics.tempo) }
    var pressing by remember(prompt) { mutableStateOf(prompt.base.tactics.pressing) }
    var width by remember(prompt) { mutableStateOf(prompt.base.tactics.width) }

    val homeScore = if (prompt.userIsHome) prompt.userGoals else prompt.oppGoals
    val awayScore = if (prompt.userIsHome) prompt.oppGoals else prompt.userGoals
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Intervalo — rodada ${prompt.round + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${prompt.homeName}  $homeScore x $awayScore  ${prompt.awayName}",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Ajuste a tática para o 2º tempo:", style = MaterialTheme.typography.labelMedium)
            TacticRow("Formação", formation.name) { formation = cycleEnum(formation) }
            TacticRow("Mentalidade", mentality.name) { mentality = cycleEnum(mentality) }
            TacticRow("Ritmo", tempo.name) { tempo = cycleEnum(tempo) }
            TacticRow("Pressão", pressing.name) { pressing = cycleEnum(pressing) }
            TacticRow("Largura", width.name) { width = cycleEnum(width) }
            Button(
                onClick = {
                    onConfirm(dev.debene.gandula.career.SeasonTactics(formation, dev.debene.gandula.domain.Tactics(mentality, tempo, pressing, width)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Seguir para o 2º tempo") }
        }
    }
}

private inline fun <reified E : Enum<E>> cycleEnum(e: E): E {
    val v = enumValues<E>()
    return v[(e.ordinal + 1) % v.size]
}

@Composable
private fun PreSeasonSection(vm: CareerViewModel) {
    val t = vm.currentTactics ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pré-temporada — tática", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TacticRow("Formação", t.formation.name, vm::cycleFormation)
            TacticRow("Mentalidade", t.tactics.mentality.name, vm::cycleMentality)
            TacticRow("Ritmo", t.tactics.tempo.name, vm::cycleTempo)
            TacticRow("Pressão", t.tactics.pressing.name, vm::cyclePressing)
            TacticRow("Largura", t.tactics.width.name, vm::cycleWidth)
        }
    }
    DealsCard(vm)
}

@Composable
private fun TacticRow(label: String, value: String, onCycle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            OutlinedButton(onClick = onCycle, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                Text("▸", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DealsCard(vm: CareerViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Contratos (valem na próxima temporada)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            vm.activeDeals?.tv?.let { Text("TV assinado: ${formatMoney(it.seasonAmount)}", style = MaterialTheme.typography.labelMedium) }
            vm.activeDeals?.sponsorship?.let { Text("Patrocínio assinado: ${formatMoney(it.seasonAmount)}", style = MaterialTheme.typography.labelMedium) }
            DealOfferRow("TV", vm.tvOffers, vm::signDeal)
            DealOfferRow("Patrocínio", vm.sponsorOffers, vm::signDeal)
        }
    }
}

@Composable
private fun DealOfferRow(label: String, offers: List<dev.debene.gandula.career.DealOffer>, onSign: (dev.debene.gandula.career.Deal) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        offers.forEach { offer ->
            OutlinedButton(
                onClick = { onSign(offer.deal) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    "${offer.label}${if (offer.deal.maxPosition != null) "*" else ""}\n${formatMoney(offer.deal.seasonAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Stateless career screen — renders in tests/previews from a [CareerUi]. */
@Composable
fun CareerScreenContent(
    ui: CareerUi,
    nameOf: (Int) -> String,
    onPlayRound: () -> Unit,
    onNextSeason: () -> Unit,
    onRestart: () -> Unit,
    onExpand: () -> Unit,
    onCampaign: () -> Unit,
    modifier: Modifier = Modifier,
    preSeason: @Composable () -> Unit = {},
    halftime: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CareerHeader(ui.club, ui.division, ui.year, ui.money, ui.fanbase, ui.stadiumCapacity, ui.round, ui.total)

        halftime()

        when {
            ui.fired -> FiredCard(onRestart)
            ui.seasonComplete -> SeasonEndCard(
                standings = ui.standings,
                controlledTeamId = ui.controlledTeamId,
                divisionName = ui.division,
                championName = nameOf(ui.standings.firstOrNull()?.teamId ?: -1),
                onNext = onNextSeason,
            )
            else -> Button(onClick = onPlayRound, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.round == 0) "Começar temporada" else "Jogar rodada ${ui.round + 1}")
            }
        }

        if (ui.seasonComplete && !ui.fired) preSeason()

        CopaCard(ui.copaStatus, ui.copaChampion)

        if (!ui.fired) {
            FinancesCard(ui, onExpand, onCampaign)
        }
        ui.lastSeason?.let { LastSeasonLine(it) }

        HorizontalDivider()
        StandingsTable(ui.standings, ui.controlledTeamId, nameOf, Modifier.fillMaxWidth())

        OutlinedButton(onClick = onRestart) { Text("Nova carreira") }
    }
}

@Composable
private fun CareerHeader(club: String, division: String, year: Int, money: Long, fanbase: Int, capacity: Int, round: Int, total: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(club, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$division · $year · Rodada $round/$total", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Caixa: ${formatMoney(money)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("Torcida ${formatPlain(fanbase)} · Estádio ${formatPlain(capacity)} lug.", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CopaCard(status: String, champion: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Copa do Brasil", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(status, style = MaterialTheme.typography.bodyMedium)
            champion?.let { Text("Campeão: $it", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun FinancesCard(ui: CareerUi, onExpand: () -> Unit, onCampaign: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Finanças", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ui.ledger?.let { l ->
                LedgerLine("Bilheteria", l.ticket)
                LedgerLine("TV", l.tv)
                LedgerLine("Patrocínio", l.sponsorship)
                LedgerLine("Bônus", l.bonus)
                LedgerLine("Salários", -l.wages)
                LedgerLine("Saldo da temporada", l.net, bold = true)
            }
            ui.runway?.let { r ->
                val color = if (r.atRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    "Projeção fim de temporada: ${formatMoney(r.projectedEndBalance)}" + if (r.atRisk) " ⚠ risco de caixa" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExpand, enabled = ui.canExpand, modifier = Modifier.weight(1f)) {
                    Text("Estádio +5k\n${formatMoney(ui.expansionCost)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onCampaign, enabled = ui.canCampaign, modifier = Modifier.weight(1f)) {
                    Text("Marketing\n${formatMoney(ui.marketingCost)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun LedgerLine(label: String, value: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            formatMoney(value),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FiredCard(onRestart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Você foi demitido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("O caixa ficou negativo e a diretoria perdeu a paciência.")
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Recomeçar carreira") }
        }
    }
}

@Composable
private fun SeasonEndCard(
    standings: List<TeamStats>,
    controlledTeamId: Int,
    divisionName: String,
    championName: String,
    onNext: () -> Unit,
) {
    val pos = standings.indexOfFirst { it.teamId == controlledTeamId } + 1
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fim da temporada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Você terminou em ${pos}º no $divisionName.")
            Text("Campeão: $championName")
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Próxima temporada") }
        }
    }
}

@Composable
private fun LastSeasonLine(h: SeasonHistory) {
    val verb = when (h.userOutcome) {
        UserOutcome.PROMOTED -> "subiu para a divisão de cima"
        UserOutcome.RELEGATED -> "foi rebaixado"
        UserOutcome.STAYED -> "permaneceu"
    }
    val cup = h.copaUserResult?.let { if (it == "champion") " · Copa: campeão" else " · Copa: $it" } ?: ""
    val transfers = if (h.transfers.isNotEmpty()) " · ${h.transfers.count { it.kind == "buy" }}↓/${h.transfers.count { it.kind == "sell" }}↑" else ""
    Text(
        "${h.year}: ${h.userPosition}º no ${h.userDivisionName} — $verb (${formatMoney(h.moneyAfter)})$cup$transfers.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StandingsTable(
    standings: List<TeamStats>,
    controlledTeamId: Int,
    nameOf: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    // Plain rows (not a nested LazyColumn) so the whole career screen scrolls as one.
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)) {
            Cell("#", 0.10f, bold = true)
            Cell("Time", 0.50f, bold = true)
            Cell("J", 0.11f, bold = true, end = true)
            Cell("SG", 0.14f, bold = true, end = true)
            Cell("P", 0.13f, bold = true, end = true)
        }
        standings.forEachIndexed { i, s ->
            val isUser = s.teamId == controlledTeamId
            val bg = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                i < 3 -> MaterialTheme.colorScheme.surfaceVariant
                i >= standings.size - 3 -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .padding(vertical = 7.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell("${i + 1}", 0.10f, bold = isUser)
                Row(Modifier.weight(0.50f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClubCrest(nameOf(s.teamId), s.teamId, size = 18.dp)
                    Text(
                        nameOf(s.teamId),
                        fontSize = 12.sp,
                        fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                Cell("${s.played}", 0.11f, end = true)
                Cell(formatSigned(s.goalDifference), 0.14f, end = true)
                Cell("${s.points}", 0.13f, bold = true, end = true)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    end: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        maxLines = 1,
    )
}

private fun formatSigned(n: Int): String = if (n > 0) "+$n" else "$n"

private fun formatPlain(n: Int): String = n.toString().reversed().chunked(3).joinToString(".").reversed()

private fun formatMoney(n: Long): String {
    val sign = if (n < 0) "-" else ""
    val digits = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$sign$ $digits"
}
