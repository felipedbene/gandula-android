package dev.debene.gandula.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.debene.gandula.career.Career
import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Copa
import dev.debene.gandula.career.Deal
import dev.debene.gandula.career.DealOffer
import dev.debene.gandula.career.Deals
import dev.debene.gandula.career.Division
import dev.debene.gandula.career.Finances
import dev.debene.gandula.career.Roster
import dev.debene.gandula.career.SeasonTactics
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.career.TransferRecord
import dev.debene.gandula.data.CareerStore
import dev.debene.gandula.data.TeamRepository
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Team
import dev.debene.gandula.engine.TeamStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Drives career mode: loads (or starts) the single-slot save, reveals rounds,
 * rolls seasons. Season simulation (~1140 matches) runs off the main thread.
 */
class CareerViewModel(private val app: Application) : AndroidViewModel(app) {

    private val teams: List<Team> = runCatching { TeamRepository.loadTeams(app) }.getOrDefault(emptyList())
    private val registry: Map<Int, Team> = teams.associateBy { it.id }

    var loading by mutableStateOf(true)
        private set
    var career by mutableStateOf<Career?>(null)
        private set

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) {
                CareerStore.load(app, registry) ?: CareerEngine.newCareer(teams, Random.nextLong())
            }
            career = loaded
            loading = false
            if (!CareerStore.hasSave(app)) persist(loaded)
        }
    }

    fun teamName(id: Int): String = registry[id]?.name ?: "Time $id"

    val userDivision: Division? get() = career?.let { CareerEngine.userDivision(it.season, it.controlledTeamId) }

    val userStandings: List<TeamStats>
        get() = career?.let { c ->
            userDivision?.let { CareerEngine.standingsUpTo(c.season, it) }
        } ?: emptyList()

    val totalRounds: Int get() = career?.let { CareerEngine.totalRounds(it.season) } ?: 0
    val seasonComplete: Boolean get() = career?.let { CareerEngine.seasonComplete(it) } ?: false

    val ledger: Finances.SeasonLedger?
        get() = career?.let { Finances.seasonToDateLedger(it, registry) }
    val runway: Finances.RunwayProjection?
        get() = career?.let { Finances.projectSeasonRunway(it, registry) }
    val expansionCost: Long get() = career?.let { Finances.expansionCost(it.stadiumCapacity) } ?: 0
    val marketingCost: Long get() = career?.let { Finances.marketingCost(it.marketingMomentum) } ?: 0
    val canExpand: Boolean
        get() = career?.let { it.stadiumCapacity < Finances.STADIUM_MAX_CAPACITY && it.money >= Finances.expansionCost(it.stadiumCapacity) } ?: false
    val canCampaign: Boolean
        get() = career?.let { it.marketingMomentum < Finances.MARKETING_MOMENTUM_MAX && it.money >= Finances.marketingCost(it.marketingMomentum) } ?: false

    // ─── Copa do Brasil (revealed by league round) ──────────────────────────
    val copaStatus: String
        get() {
            val c = career ?: return ""
            val copa = c.season.copa
            val id = c.controlledTeamId
            val revealed = Copa.revealedRounds(c.season.currentRoundIdx)
            val elim = copa.userEliminatedAtRoundIdx
            return when {
                copa.championId == id && revealed >= copa.rounds.size -> "Campeão da Copa do Brasil! 🏆"
                elim != null && elim < revealed -> "Eliminado na ${copa.rounds[elim].name.display}"
                revealed == 0 -> "Chaveamento definido"
                else -> {
                    val last = (revealed - 1 downTo 0).firstOrNull { Copa.userTieInRound(copa, it, id) != null }
                    if (last != null) "Classificado — ${copa.rounds[last].name.display}" else "Fora da Copa"
                }
            }
        }

    val copaChampionName: String?
        get() {
            val c = career ?: return null
            val copa = c.season.copa
            if (Copa.revealedRounds(c.season.currentRoundIdx) < copa.rounds.size) return null
            return copa.championId?.let { teamName(it) }
        }

    // ─── Round play with a live half-time decision ──────────────────────────
    /** Interval state for the user's current-round match — shown so the user can
     *  steer the second half before the round resolves. */
    data class HalftimePrompt(
        val round: Int,
        val homeName: String,
        val awayName: String,
        val userIsHome: Boolean,
        val userGoals: Int,
        val oppGoals: Int,
        val base: SeasonTactics,
        val squad: List<Player>,
        val xi: List<Int>,
    )

    var halftimePrompt by mutableStateOf<HalftimePrompt?>(null)
        private set
    private var pendingRound = 0
    private var pendingMatch: dev.debene.gandula.domain.Match? = null

    /** A slice of the user's current-round match being broadcast minute-by-minute
     *  (first half, then — after the interval — second half). */
    data class Broadcast(
        val round: Int,
        val homeId: Int,
        val awayId: Int,
        val homeName: String,
        val awayName: String,
        val startMinute: Int,
        val baselineHome: Int,
        val baselineAway: Int,
        val events: List<dev.debene.gandula.domain.MatchEvent>,
        val secondHalf: Boolean,
    )

    var broadcast by mutableStateOf<Broadcast?>(null)
        private set

    private fun firstHalfGoals(match: dev.debene.gandula.domain.Match): Pair<Int, Int> {
        val (firstHalf, _) = splitAtHalfTime(match.events)
        val h = firstHalf.count { it.kind is dev.debene.gandula.domain.MatchEventKind.Goal && it.side == dev.debene.gandula.domain.Side.Home }
        val a = firstHalf.count { it.kind is dev.debene.gandula.domain.MatchEventKind.Goal && it.side == dev.debene.gandula.domain.Side.Away }
        return h to a
    }

    /** Called by the UI when a broadcast slice finishes (played or skipped):
     *  after the first half → show the half-time card; after the second → advance. */
    fun onBroadcastDone() {
        val b = broadcast ?: return
        broadcast = null
        if (!b.secondHalf) {
            val match = pendingMatch ?: return
            val c = career ?: return
            val (hg, ag) = firstHalfGoals(match)
            val userIsHome = match.home == c.controlledTeamId
            halftimePrompt = HalftimePrompt(
                round = b.round,
                homeName = b.homeName,
                awayName = b.awayName,
                userIsHome = userIsHome,
                userGoals = if (userIsHome) hg else ag,
                oppGoals = if (userIsHome) ag else hg,
                base = matchTacticsDraft ?: baseTactics(c),
                squad = Roster.workingRoster(c, registry),
                xi = matchXi(c, b.round),
            )
        } else {
            val c = career ?: return
            viewModelScope.launch {
                val updated = withContext(Dispatchers.Default) { CareerEngine.revealNextRound(c, registry) }
                matchTacticsDraft = null
                career = updated
                persist(updated)
            }
        }
    }

    // ─── Pre-kickoff tactics for the upcoming round ─────────────────────────
    /** Editable formation/tactics for the *next* round's match. Null = use the
     *  season default; reset after each round resolves. */
    var matchTacticsDraft by mutableStateOf<SeasonTactics?>(null)
        private set
    val upcomingTactics: SeasonTactics? get() = career?.let { matchTacticsDraft ?: baseTactics(it) }

    fun cyclePreFormation() {
        val c = career ?: return
        val base = matchTacticsDraft ?: baseTactics(c)
        val next = cycle(base.formation)
        matchTacticsDraft = base.copy(formation = next, xi = buildXiForFormation(c, next, upcomingXi))
    }
    fun cyclePreMentality() = setPre { it.copy(tactics = it.tactics.copy(mentality = cycle(it.tactics.mentality))) }
    fun cyclePreTempo() = setPre { it.copy(tactics = it.tactics.copy(tempo = cycle(it.tactics.tempo))) }
    fun cyclePrePressing() = setPre { it.copy(tactics = it.tactics.copy(pressing = cycle(it.tactics.pressing))) }
    fun cyclePreWidth() = setPre { it.copy(tactics = it.tactics.copy(width = cycle(it.tactics.width))) }

    private fun setPre(op: (SeasonTactics) -> SeasonTactics) {
        val c = career ?: return
        matchTacticsDraft = op(matchTacticsDraft ?: baseTactics(c))
    }

    // ─── Lineup (starting XI) — squad + the eleven for pre-match / pre-season ──
    val lineupSquad: List<Player>
        get() = career?.let { Roster.workingRoster(it, registry) } ?: emptyList()

    /** Eleven for the upcoming round (pre-match draft, else current default) —
     *  always normalized to the chosen formation (1 GK + valid composition). */
    val upcomingXi: List<Int>
        get() = career?.let { c ->
            val t = matchTacticsDraft ?: baseTactics(c)
            Roster.lineupFor(Roster.workingRoster(c, registry), t.formation, t.xi ?: emptyList())
        } ?: emptyList()

    /** Eleven for the season default (pre-season editor), normalized to its formation. */
    val seasonXi: List<Int>
        get() = career?.let { c ->
            Roster.lineupFor(Roster.workingRoster(c, registry), baseTactics(c).formation, c.userTactics?.xi ?: emptyList())
        } ?: emptyList()

    fun setPreMatchXi(xi: List<Int>) = setPre { it.copy(xi = xi) }
    fun setSeasonXi(xi: List<Int>) = setTac { it.copy(xi = xi) }

    /** Best XI for [f], preferring [currentXi] — delegates to the shared
     *  formation-aware lineup builder. */
    private fun buildXiForFormation(c: Career, f: Formation, currentXi: List<Int>): List<Int> =
        Roster.lineupFor(Roster.workingRoster(c, registry), f, currentXi)

    /** The user's on-pitch eleven for [round] (roster + pre-match tactics applied)
     *  — the starting point for half-time substitutions. */
    private fun matchXi(c: Career, round: Int): List<Int> {
        val pre = c.matchTactics[round] ?: c.userTactics
        return Roster.effectiveTeam(registry.getValue(c.controlledTeamId), Roster.workingRoster(c, registry), pre).startingXi
    }

    fun playNextRound() {
        val c0 = career ?: return
        if (CareerEngine.seasonComplete(c0) || c0.fired || halftimePrompt != null || broadcast != null) return
        val round = c0.season.currentRoundIdx
        viewModelScope.launch {
            // Lock in any pre-kickoff tactic change for this round (re-simulates).
            val base = baseTactics(c0)
            val draft = matchTacticsDraft
            val c = if (draft != null && draft != base) {
                val withTac = withContext(Dispatchers.Default) { CareerEngine.setMatchTactics(c0, registry, round, draft) }
                career = withTac; persist(withTac); withTac
            } else c0
            val match = withContext(Dispatchers.Default) { CareerEngine.userMatch(c, round) }
            if (match == null) { // no user fixture (bye) — just advance
                val updated = withContext(Dispatchers.Default) { CareerEngine.revealNextRound(c, registry) }
                matchTacticsDraft = null
                career = updated; persist(updated)
                return@launch
            }
            // Broadcast the first half live; the half-time card follows when it ends.
            pendingRound = round
            pendingMatch = match
            val (firstHalf, _) = splitAtHalfTime(match.events)
            broadcast = Broadcast(
                round = round,
                homeId = match.home, awayId = match.away,
                homeName = teamName(match.home), awayName = teamName(match.away),
                startMinute = 0, baselineHome = 0, baselineAway = 0,
                events = firstHalf, secondHalf = false,
            )
        }
    }

    /** Resolve the interval: re-simulate the user's second half if they changed
     *  tactics (else the default result stands), then broadcast it live. The round
     *  advances when that second-half broadcast finishes ([onBroadcastDone]). */
    fun confirmHalftime(override: SeasonTactics?) {
        val c = career ?: return
        val round = pendingRound
        val base = upcomingTactics ?: baseTactics(c)
        halftimePrompt = null
        viewModelScope.launch {
            var updated = c
            if (override != null && override != base) {
                updated = withContext(Dispatchers.Default) { CareerEngine.applyHalftime(c, registry, round, override) }
                career = updated; persist(updated)
            }
            val match = withContext(Dispatchers.Default) { CareerEngine.userMatch(updated, round) } ?: return@launch
            pendingMatch = match
            val (_, secondHalf) = splitAtHalfTime(match.events)
            val (hg, ag) = firstHalfGoals(match)
            broadcast = Broadcast(
                round = round,
                homeId = match.home, awayId = match.away,
                homeName = teamName(match.home), awayName = teamName(match.away),
                startMinute = 45, baselineHome = hg, baselineAway = ag,
                events = secondHalf, secondHalf = true,
            )
        }
    }

    fun expandStadium() = mutate { CareerEngine.expandStadium(it) }
    fun runMarketingCampaign() = mutate { CareerEngine.runMarketingCampaign(it) }

    // ─── Transfer market ────────────────────────────────────────────────────
    /** The squad market is open all season (except once fired): a buy/sell takes
     *  effect from the current round and re-simulates the rounds still to play. */
    val transfersOpen: Boolean get() = career?.fired == false

    val squad: List<Player>
        get() = career?.let { Roster.workingRoster(it, registry).sortedBy { p -> p.position.ordinal } } ?: emptyList()

    val freeAgents: List<Player>
        get() = career?.let { TransferMarket.availableAgents(it, registry) } ?: emptyList()

    fun buyPrice(p: Player): Long = TransferMarket.playerPrice(p, TransferMarket.Kind.BUY)
    fun sellPrice(p: Player): Long = TransferMarket.playerPrice(p, TransferMarket.Kind.SELL)
    fun scout(p: Player): TransferMarket.ScoutReport =
        TransferMarket.scoutReport(p, career?.let { Roster.workingRoster(it, registry) } ?: emptyList())

    fun canBuy(p: Player): Boolean =
        transfersOpen && career?.let { TransferMarket.canBuy(it, registry, buyPrice(p)).ok } == true
    fun canSell(p: Player): Boolean =
        transfersOpen && career?.let { TransferMarket.canSell(it, registry, p.id).ok } == true

    fun buy(p: Player) { if (transfersOpen) mutateRebuild { TransferMarket.buy(it, registry, p) } }
    fun sell(p: Player) { if (transfersOpen) mutateRebuild { TransferMarket.sell(it, registry, p) } }

    val sessionTransfers: List<TransferRecord> get() = career?.transfers ?: emptyList()
    val lastSeasonTransfers: List<TransferRecord> get() = career?.history?.lastOrNull()?.transfers ?: emptyList()

    // ─── Pre-season tactics (gated to season end, applied next season) ──────
    /** Between-seasons gate for the deals slate + pre-season tactics (these still
     *  apply to next season's sim; the squad market, by contrast, is open now). */
    val marketOpen: Boolean get() = seasonComplete && career?.fired == false

    private fun baseTactics(c: Career): SeasonTactics =
        c.userTactics ?: registry.getValue(c.controlledTeamId).let { SeasonTactics(it.formation, it.tactics) }

    val currentTactics: SeasonTactics? get() = career?.let { baseTactics(it) }

    /** Cycling the formation rebuilds the XI to match its composition, so shape
     *  and lineup stay consistent (no 4-3-3 squad crammed into a 4-5-2). */
    fun cycleFormation() {
        val c = career ?: return
        if (!marketOpen) return
        val base = baseTactics(c)
        val next = cycle(base.formation)
        mutate { CareerEngine.setTactics(it, base.copy(formation = next, xi = buildXiForFormation(c, next, seasonXi))) }
    }
    fun cycleMentality() = setTac { it.copy(tactics = it.tactics.copy(mentality = cycle(it.tactics.mentality))) }
    fun cycleTempo() = setTac { it.copy(tactics = it.tactics.copy(tempo = cycle(it.tactics.tempo))) }
    fun cyclePressing() = setTac { it.copy(tactics = it.tactics.copy(pressing = cycle(it.tactics.pressing))) }
    fun cycleWidth() = setTac { it.copy(tactics = it.tactics.copy(width = cycle(it.tactics.width))) }

    private fun setTac(op: (SeasonTactics) -> SeasonTactics) {
        val c = career ?: return
        if (!marketOpen) return
        mutate { CareerEngine.setTactics(it, op(baseTactics(c))) }
    }

    // ─── Pre-season deals ───────────────────────────────────────────────────
    val tvOffers: List<DealOffer> get() = career?.let { Finances.dealOffers(it).first } ?: emptyList()
    val sponsorOffers: List<DealOffer> get() = career?.let { Finances.dealOffers(it).second } ?: emptyList()
    val activeDeals: Deals? get() = career?.activeDeals
    fun signDeal(d: Deal) { if (marketOpen) mutate { CareerEngine.signDeal(it, d) } }

    // ─── Dressing room (raise demands / poaching, resolved at the boundary) ──
    val pendingDemands: List<dev.debene.gandula.career.Contracts.Demand> get() = career?.pendingDemands ?: emptyList()
    val demandDecisions: Map<Int, Boolean> get() = career?.demandDecisions ?: emptyMap()
    fun decideDemand(playerId: Int, accept: Boolean) {
        if (marketOpen) mutate { CareerEngine.decideDemand(it, playerId, accept) }
    }

    private fun mutate(op: (Career) -> Career) {
        val c = career ?: return
        val updated = op(c)
        career = updated
        persist(updated)
    }

    /** Apply a mutation that changes the squad/overlays, then re-simulate the
     *  season off the main thread (only the user's remaining matches actually
     *  change; already-revealed rounds and money are untouched). */
    private fun mutateRebuild(op: (Career) -> Career) {
        val c = career ?: return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.Default) { CareerEngine.rebuildSeason(op(c), registry) }
            career = updated
            persist(updated)
        }
    }

    fun advanceToNextSeason() {
        val c = career ?: return
        if (!CareerEngine.seasonComplete(c)) return
        viewModelScope.launch {
            loading = true
            val next = withContext(Dispatchers.Default) { CareerEngine.advanceSeason(c, registry) }
            career = next
            loading = false
            persist(next)
        }
    }

    fun restart() {
        viewModelScope.launch {
            loading = true
            val fresh = withContext(Dispatchers.Default) {
                CareerStore.clear(app)
                CareerEngine.newCareer(teams, Random.nextLong())
            }
            career = fresh
            loading = false
            persist(fresh)
        }
    }

    private fun persist(c: Career) {
        viewModelScope.launch(Dispatchers.Default) { CareerStore.save(app, c) }
    }
}

/** Next value of an enum, wrapping around — for the tactics cyclers. */
private inline fun <reified E : Enum<E>> cycle(e: E): E {
    val values = enumValues<E>()
    return values[(e.ordinal + 1) % values.size]
}
