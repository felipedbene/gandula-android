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
import dev.debene.ui.theme.GandulaMono
import dev.debene.gandula.career.Finances
import dev.debene.gandula.career.SeasonHistory
import dev.debene.gandula.career.UserOutcome
import dev.debene.gandula.engine.TeamStats
import androidx.compose.ui.graphics.Brush
import dev.debene.ui.theme.GradientStart
import dev.debene.ui.theme.GradientEnd
import dev.debene.ui.theme.NeonCyan
import dev.debene.ui.theme.GlassBody
import dev.debene.ui.theme.GlassBorder
import androidx.compose.foundation.border
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
    /** A match is being broadcast live or the half-time card is up — hide the
     *  "play round" / pre-match controls until it resolves. */
    val live: Boolean,
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
            live = vm.broadcast != null || vm.halftimePrompt != null,
        ),
        nameOf = vm::teamName,
        onPlayRound = vm::playNextRound,
        onNextSeason = vm::advanceToNextSeason,
        onRestart = vm::restart,
        onExpand = vm::expandStadium,
        onCampaign = vm::runMarketingCampaign,
        modifier = modifier,
        preSeason = { PreSeasonSection(vm) },
        preMatch = {
            if (!vm.seasonComplete && !career.fired) PreMatchSection(vm)
        },
        broadcast = {
            vm.broadcast?.let { b ->
                MatchBroadcast(
                    homeName = b.homeName, homeId = b.homeId,
                    awayName = b.awayName, awayId = b.awayId,
                    events = b.events,
                    startMinute = b.startMinute,
                    baselineHome = b.baselineHome,
                    baselineAway = b.baselineAway,
                    onDone = vm::onBroadcastDone,
                )
            }
        },
        halftime = { vm.halftimePrompt?.let { HalftimeCard(it, vm::confirmHalftime) } },
    )
}

/** Header built from the shared career view-model (reused by every career sub-screen). */
@Composable
private fun CareerHeaderFrom(vm: CareerViewModel) {
    val c = vm.career ?: return
    val div = vm.userDivision ?: return
    CareerHeader(vm.teamName(c.controlledTeamId), div.name, c.season.year, c.money, c.fanbase, c.stadiumCapacity, c.season.currentRoundIdx, vm.totalRounds)
}

@Composable
private fun CareerLoadingOr(vm: CareerViewModel, content: @Composable () -> Unit) {
    if (vm.loading || vm.career == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        content()
    }
}

/** "Tabela" destination: Copa + the league standings. */
@Composable
fun CareerTableScreen(modifier: Modifier = Modifier, vm: CareerViewModel = viewModel()) {
    CareerLoadingOr(vm) {
        val career = vm.career ?: return@CareerLoadingOr
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CareerHeaderFrom(vm)
            CopaCard(vm.copaStatus, vm.copaChampionName)
            HorizontalDivider()
            StandingsTable(vm.userStandings, career.controlledTeamId, vm::teamName, Modifier.fillMaxWidth())
            career.history.lastOrNull()?.let { LastSeasonLine(it) }
        }
    }
}

/** "Finanças" destination: ledger, runway, stadium/marketing levers. */
@Composable
fun CareerFinanceScreen(modifier: Modifier = Modifier, vm: CareerViewModel = viewModel()) {
    CareerLoadingOr(vm) {
        val career = vm.career ?: return@CareerLoadingOr
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CareerHeaderFrom(vm)
            if (!career.fired) {
                FinancesCard(vm.ledger, vm.runway, vm.expansionCost, vm.marketingCost, vm.canExpand, vm.canCampaign, vm::expandStadium, vm::runMarketingCampaign)
            }
            career.history.lastOrNull()?.let { LastSeasonLine(it) }
        }
    }
}

@Composable
private fun PreMatchSection(vm: CareerViewModel) {
    val t = vm.upcomingTactics ?: return
    val round = vm.career?.season?.currentRoundIdx ?: return
    Card(colors = CardDefaults.cardColors(containerColor = GlassBody), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tática — rodada ${round + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TacticRow("Formação", t.formation.ptLabel(), vm::cyclePreFormation)
            TacticRow("Mentalidade", t.tactics.mentality.ptLabel(), vm::cyclePreMentality)
            TacticRow("Ritmo", t.tactics.tempo.ptLabel(), vm::cyclePreTempo)
            TacticRow("Pressão", t.tactics.pressing.ptLabel(), vm::cyclePrePressing)
            TacticRow("Largura", t.tactics.width.ptLabel(), vm::cyclePreWidth)
            LineupExpander(vm.lineupSquad, vm.upcomingXi, t.formation, vm::setPreMatchXi)
        }
    }
}

/** Collapsible starting-XI editor — shared by the pre-match and pre-season cards. */
@Composable
private fun LineupExpander(
    squad: List<dev.debene.gandula.domain.Player>,
    xi: List<Int>,
    formation: dev.debene.gandula.domain.Formation,
    onChange: (List<Int>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable { open = !open }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Escalação (titulares)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(if (open) "▴" else "▾", style = MaterialTheme.typography.labelMedium)
    }
    if (open) PitchLineupEditor(squad, xi, formation, onChange)
}

@Composable
internal fun HalftimeCard(prompt: CareerViewModel.HalftimePrompt, onConfirm: (dev.debene.gandula.career.SeasonTactics?) -> Unit) {
    var formation by remember(prompt) { mutableStateOf(prompt.base.formation) }
    var mentality by remember(prompt) { mutableStateOf(prompt.base.tactics.mentality) }
    var tempo by remember(prompt) { mutableStateOf(prompt.base.tactics.tempo) }
    var pressing by remember(prompt) { mutableStateOf(prompt.base.tactics.pressing) }
    var width by remember(prompt) { mutableStateOf(prompt.base.tactics.width) }
    var xi by remember(prompt) { mutableStateOf(prompt.xi) }
    val subCount = prompt.xi.count { it !in xi.toSet() }

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
            TacticRow("Formação", formation.ptLabel()) { formation = cycleEnum(formation) }
            TacticRow("Mentalidade", mentality.ptLabel()) { mentality = cycleEnum(mentality) }
            TacticRow("Ritmo", tempo.ptLabel()) { tempo = cycleEnum(tempo) }
            TacticRow("Pressão", pressing.ptLabel()) { pressing = cycleEnum(pressing) }
            TacticRow("Largura", width.ptLabel()) { width = cycleEnum(width) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                "Substituições" + if (subCount > 0) " — $subCount/3" else " (até 3)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            PitchLineupEditor(prompt.squad, xi, formation, onChange = { if (prompt.xi.count { id -> id !in it.toSet() } <= 3) xi = it })
            Button(
                onClick = {
                    onConfirm(
                        dev.debene.gandula.career.SeasonTactics(
                            formation,
                            dev.debene.gandula.domain.Tactics(mentality, tempo, pressing, width),
                            xi = if (xi != prompt.xi) xi else null,
                        ),
                    )
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
    Card(colors = CardDefaults.cardColors(containerColor = GlassBody), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pré-temporada — tática", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TacticRow("Formação", t.formation.ptLabel(), vm::cycleFormation)
            TacticRow("Mentalidade", t.tactics.mentality.ptLabel(), vm::cycleMentality)
            TacticRow("Ritmo", t.tactics.tempo.ptLabel(), vm::cycleTempo)
            TacticRow("Pressão", t.tactics.pressing.ptLabel(), vm::cyclePressing)
            TacticRow("Largura", t.tactics.width.ptLabel(), vm::cycleWidth)
            LineupExpander(vm.lineupSquad, vm.seasonXi, t.formation, vm::setSeasonXi)
        }
    }
    DemandsCard(vm)
    DealsCard(vm)
}

@Composable
private fun TacticRow(label: String, value: String, onCycle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = GandulaMono)
            OutlinedButton(onClick = onCycle, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                Text("▸", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DemandsCard(vm: CareerViewModel) {
    val demands = vm.pendingDemands
    if (demands.isEmpty()) return
    val decisions = vm.demandDecisions
    Card(colors = CardDefaults.cardColors(containerColor = GlassBody), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vestiário — pedidos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Decida antes da próxima temporada. Sem decisão = recusa.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            demands.forEach { d ->
                val poach = d.kind == "poach"
                val pct = ((d.targetMult - d.currentMult) * 100).toInt()
                val decision = decisions[d.playerId]
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${d.position.name} ${d.playerName} (${d.overall})" + if (d.mercenary) " · mercenário" else " · leal",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (poach) "Proposta de fora: ${formatMoney(d.fee)} — segurar (+$pct% salário) ou vender?"
                        else "Quer +$pct% de salário." + if (d.mercenary) " Recusar → vai embora (${formatMoney(d.fee)})." else " Recusar → desmotiva.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val acceptLabel = if (poach) "Segurar" else "Aceitar"
                        val refuseLabel = if (poach || d.mercenary) "Vender" else "Recusar"
                        DecisionButton(acceptLabel, selected = decision == true, Modifier.weight(1f)) { vm.decideDemand(d.playerId, true) }
                        DecisionButton(refuseLabel, selected = decision == false, Modifier.weight(1f)) { vm.decideDemand(d.playerId, false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DealsCard(vm: CareerViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = GlassBody), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))) {
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
    preMatch: @Composable () -> Unit = {},
    broadcast: @Composable () -> Unit = {},
    halftime: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CareerHeader(ui.club, ui.division, ui.year, ui.money, ui.fanbase, ui.stadiumCapacity, ui.round, ui.total)

        broadcast()
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
            ui.live -> Unit // the broadcast / half-time card above owns the screen
            else -> {
                preMatch()
                Button(onClick = onPlayRound, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.round == 0) "Começar temporada" else "Jogar rodada ${ui.round + 1}")
                }
            }
        }

        if (ui.seasonComplete && !ui.fired) preSeason()

        // Finanças, Copa and the league table live in their own bottom-nav
        // destinations now — this screen stays focused on the next match.
        OutlinedButton(onClick = onRestart) { Text("Nova carreira") }
    }
}

@Composable
private fun CareerHeader(club: String, division: String, year: Int, money: Long, fanbase: Int, capacity: Int, round: Int, total: Int) {
    val gradient = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(gradient).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(club, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color.White)
                Text("$division · $year · Rodada $round/$total", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
                val animatedMoney by androidx.compose.animation.core.animateIntAsState(
                    targetValue = money.toInt(),
                    animationSpec = androidx.compose.animation.core.tween(700),
                    label = "caixa",
                )
                Text(
                    "Caixa: ${formatMoney(animatedMoney.toLong())}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text("Torcida ${formatPlain(fanbase)} · Estádio ${formatPlain(capacity)} lug.", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
            }
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
private fun FinancesCard(
    ledger: Finances.SeasonLedger?,
    runway: Finances.RunwayProjection?,
    expansionCost: Long,
    marketingCost: Long,
    canExpand: Boolean,
    canCampaign: Boolean,
    onExpand: () -> Unit,
    onCampaign: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = GlassBody), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Finanças", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ledger?.let { l ->
                LedgerLine("Bilheteria", l.ticket)
                LedgerLine("TV", l.tv)
                LedgerLine("Patrocínio", l.sponsorship)
                LedgerLine("Bônus", l.bonus)
                LedgerLine("Salários", -l.wages)
                LedgerLine("Saldo da temporada", l.net, bold = true)
            }
            runway?.let { r ->
                val color = if (r.atRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    "Projeção fim de temporada: ${formatMoney(r.projectedEndBalance)}" + if (r.atRisk) " ⚠ risco de caixa" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExpand, enabled = canExpand, modifier = Modifier.weight(1f)) {
                    Text("Estádio +5k\n${formatMoney(expansionCost)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onCampaign, enabled = canCampaign, modifier = Modifier.weight(1f)) {
                    Text("Marketing\n${formatMoney(marketingCost)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
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
            fontFamily = GandulaMono,
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
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp)) {
            Cell("#", 0.10f, bold = true)
            Cell("Time", 0.50f, bold = true)
            Cell("J", 0.11f, bold = true, end = true)
            Cell("SG", 0.14f, bold = true, end = true)
            Cell("P", 0.13f, bold = true, end = true)
        }
        standings.forEachIndexed { i, s ->
            val isUser = s.teamId == controlledTeamId
            val bg = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                i < 3 -> MaterialTheme.colorScheme.surfaceVariant
                i >= standings.size - 3 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val rowModifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .then(if (isUser) Modifier.border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
                .padding(vertical = 10.dp, horizontal = 12.dp)

            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Cell("${i + 1}", 0.10f, bold = isUser)
                Row(Modifier.weight(0.50f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClubCrest(nameOf(s.teamId), s.teamId, size = 20.dp)
                    Text(
                        nameOf(s.teamId),
                        fontSize = 13.sp,
                        fontWeight = if (isUser) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        color = if (isUser) NeonCyan else MaterialTheme.colorScheme.onSurface
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
        fontFamily = GandulaMono,
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
