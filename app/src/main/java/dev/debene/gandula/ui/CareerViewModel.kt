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
import dev.debene.gandula.domain.Player
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
    )

    var halftimePrompt by mutableStateOf<HalftimePrompt?>(null)
        private set
    private var pendingRound = 0

    fun playNextRound() {
        val c = career ?: return
        if (CareerEngine.seasonComplete(c) || c.fired || halftimePrompt != null) return
        val round = c.season.currentRoundIdx
        viewModelScope.launch {
            val fh = withContext(Dispatchers.Default) { CareerEngine.userFirstHalf(c, registry, round) }
            if (fh == null) { // no user fixture (bye) — just advance
                val updated = withContext(Dispatchers.Default) { CareerEngine.revealNextRound(c, registry) }
                career = updated; persist(updated)
                return@launch
            }
            val (half, userIsHome) = fh
            pendingRound = round
            halftimePrompt = HalftimePrompt(
                round = round,
                homeName = half.homeName,
                awayName = half.awayName,
                userIsHome = userIsHome,
                userGoals = if (userIsHome) half.homeGoals else half.awayGoals,
                oppGoals = if (userIsHome) half.awayGoals else half.homeGoals,
                base = baseTactics(c),
            )
        }
    }

    /** Resolve the round: re-simulate the user's second half if they changed
     *  tactics (else the default result stands), then advance + accrue cash. */
    fun confirmHalftime(override: SeasonTactics?) {
        val c = career ?: return
        val round = pendingRound
        val base = baseTactics(c)
        halftimePrompt = null
        viewModelScope.launch {
            var updated = c
            if (override != null && override != base) {
                updated = withContext(Dispatchers.Default) { CareerEngine.applyHalftime(c, registry, round, override) }
            }
            updated = withContext(Dispatchers.Default) { CareerEngine.revealNextRound(updated, registry) }
            career = updated
            persist(updated)
        }
    }

    fun expandStadium() = mutate { CareerEngine.expandStadium(it) }
    fun runMarketingCampaign() = mutate { CareerEngine.runMarketingCampaign(it) }

    // ─── Transfer market ────────────────────────────────────────────────────
    /** The market opens between seasons so squad changes apply cleanly to the
     *  next season's simulation (the current one is already played). */
    val marketOpen: Boolean get() = seasonComplete && career?.fired == false

    val squad: List<Player>
        get() = career?.let { Roster.workingRoster(it, registry).sortedBy { p -> p.position.ordinal } } ?: emptyList()

    val freeAgents: List<Player>
        get() = career?.let { TransferMarket.availableAgents(it, registry) } ?: emptyList()

    fun buyPrice(p: Player): Long = TransferMarket.playerPrice(p, TransferMarket.Kind.BUY)
    fun sellPrice(p: Player): Long = TransferMarket.playerPrice(p, TransferMarket.Kind.SELL)
    fun scout(p: Player): TransferMarket.ScoutReport =
        TransferMarket.scoutReport(p, career?.let { Roster.workingRoster(it, registry) } ?: emptyList())

    fun canBuy(p: Player): Boolean =
        marketOpen && career?.let { TransferMarket.canBuy(it, registry, buyPrice(p)).ok } == true
    fun canSell(p: Player): Boolean =
        marketOpen && career?.let { TransferMarket.canSell(it, registry, p.id).ok } == true

    fun buy(p: Player) { if (marketOpen) mutate { TransferMarket.buy(it, registry, p) } }
    fun sell(p: Player) { if (marketOpen) mutate { TransferMarket.sell(it, registry, p) } }

    val sessionTransfers: List<TransferRecord> get() = career?.transfers ?: emptyList()
    val lastSeasonTransfers: List<TransferRecord> get() = career?.history?.lastOrNull()?.transfers ?: emptyList()

    // ─── Pre-season tactics (gated to season end, applied next season) ──────
    private fun baseTactics(c: Career): SeasonTactics =
        c.userTactics ?: registry.getValue(c.controlledTeamId).let { SeasonTactics(it.formation, it.tactics) }

    val currentTactics: SeasonTactics? get() = career?.let { baseTactics(it) }

    fun cycleFormation() = setTac { it.copy(formation = cycle(it.formation)) }
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

    private fun mutate(op: (Career) -> Career) {
        val c = career ?: return
        val updated = op(c)
        career = updated
        persist(updated)
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
