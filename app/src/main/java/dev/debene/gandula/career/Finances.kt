package dev.debene.gandula.career

import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Team
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * The career economy — port of the core of `web/src/util/finances.ts`. Per-round
 * cash accrual (home gate + TV + sponsorship + match bonus − wages), the
 * end-of-season placement prize + promotion/relegation bonus, fanbase/stadium/
 * marketing levers, the cash-runway projection, and the firing lose-condition.
 *
 * Deferred from upstream (depend on systems not yet ported): Copa prize money,
 * negotiable TV/sponsorship deals + scandals, and per-season opponent evolution
 * (opponent strength here reads the static registry — matches season 1 upstream).
 *
 * All numbers are illustrative / tunable, copied verbatim from upstream so the
 * balance is the same.
 */
object Finances {
    // ─── Gate: stadium, fanbase, demand ──────────────────────────────────────
    const val TICKET_PRICE = 1.5
    const val DEMAND_FANBASE_COEF = 1.0
    const val STADIUM_EXPANSION_STEP = 5_000
    const val STADIUM_MAX_CAPACITY = 80_000

    fun startingCapacity(tier: Int) = when (tier) { 1 -> 45_000; 2 -> 25_000; else -> 12_000 }
    fun startingFanbase(tier: Int) = when (tier) { 1 -> 40_000; 2 -> 22_000; else -> 10_000 }
    fun demandTierMult(tier: Int) = when (tier) { 1 -> 1.0; 2 -> 0.8; else -> 0.65 }
    private fun opponentDraw(strength: Int): Double = 0.45 + strength * 0.01
    fun expansionCost(currentCapacity: Int): Long = 1_500_000L + currentCapacity * 80L

    // ─── Fanbase drift (per season, at boundary) ─────────────────────────────
    fun fanbaseTarget(tier: Int) = when (tier) { 1 -> 70_000; 2 -> 30_000; else -> 12_000 }
    const val FANBASE_PLACEMENT_SWING = 15_000
    const val FANBASE_PLACEMENT_PIVOT = 10
    const val FANBASE_MAX_STEP = 4_000

    // ─── Marketing campaigns ─────────────────────────────────────────────────
    const val CAMPAIGN_FANBASE = 6_000
    const val MARKETING_MOMENTUM_PER_CAMPAIGN = 6_000
    const val MARKETING_MOMENTUM_DECAY = 0.5
    const val MARKETING_MOMENTUM_MAX = 40_000
    fun marketingCost(currentMomentum: Int): Long = 800_000L + currentMomentum * 120L
    fun nextMarketingMomentum(currentMomentum: Int): Int {
        val decayed = (currentMomentum * MARKETING_MOMENTUM_DECAY).roundToLong().toInt()
        return if (decayed <= 1) 0 else decayed
    }

    // ─── Form (bounded attendance multiplier) ────────────────────────────────
    const val FORM_WINDOW = 5
    const val FORM_STEP = 0.05
    const val FORM_MIN = 0.9
    const val FORM_MAX = 1.2

    // ─── Wages, prizes, floors ───────────────────────────────────────────────
    const val SALARY_PER_PLAYER_STRENGTH = 500L
    const val PROMOTION_BONUS = 500_000L
    const val RELEGATION_PENALTY = 200_000L
    const val MANAGER_FIRING_FLOOR = 0L

    fun tvDeal(tier: Int): Long = when (tier) { 1 -> 3_000_000L; 2 -> 900_000L; else -> 300_000L }
    const val WIN_BONUS = 40_000L
    const val DRAW_BONUS = 12_000L

    const val PLACEMENT_PRIZE_BASE = 1_500_000L
    const val PLACEMENT_CUTOFF = 12
    fun placementTierMult(tier: Int) = when (tier) { 1 -> 1.0; 2 -> 0.4; else -> 0.15 }

    fun sponsorshipBase(tier: Int): Long = when (tier) { 1 -> 800_000L; 2 -> 300_000L; else -> 100_000L }
    const val SPONSORSHIP_FANBASE_COEF = 2.5
    const val SPONSORSHIP_PLACEMENT_BONUS = 600_000
    const val SPONSORSHIP_PLACEMENT_PIVOT = 10

    fun isManagerFired(balance: Long): Boolean = balance < MANAGER_FIRING_FLOOR

    // ─── Copa do Brasil prize money ──────────────────────────────────────────
    /** Prize for reaching a cup round (regardless of that tie's result). */
    fun cupPrizeForRound(round: CupRoundName): Long = when (round) {
        CupRoundName.PRELIM -> 0L
        CupRoundName.R32 -> 60_000L
        CupRoundName.R16 -> 120_000L
        CupRoundName.QF -> 250_000L
        CupRoundName.SF -> 500_000L
        CupRoundName.FINAL -> 900_000L
    }
    const val CUP_CHAMPION_BONUS = 1_200_000L

    /** Total cup money the user banked: the prize for every round they reached
     *  (had a real tie in) plus the champion bonus if they won. */
    fun cupPrizeTotal(bracket: Copa.Bracket, controlledTeamId: Int): Long {
        var prize = 0L
        bracket.rounds.forEachIndexed { i, round ->
            if (Copa.userTieInRound(bracket, i, controlledTeamId) != null) prize += cupPrizeForRound(round.name)
        }
        if (bracket.championId == controlledTeamId) prize += CUP_CHAMPION_BONUS
        return prize
    }

    // ─── Per-player / per-team helpers ───────────────────────────────────────
    private fun avgAttributes(p: Player): Int {
        val a = p.attributes
        return ((a.pace + a.technique + a.passing + a.defending + a.finishing + a.stamina) / 6.0).roundToLong().toInt()
    }

    private fun opponentStrength(registry: Map<Int, Team>, oppId: Int): Int =
        registry[oppId]?.let { Divisions.avgStrength(it) } ?: 0

    fun matchDemand(fanbase: Int, tier: Int, oppStrength: Int): Double =
        fanbase * DEMAND_FANBASE_COEF * demandTierMult(tier) * opponentDraw(oppStrength)

    private fun homeGateRevenue(fanbase: Int, capacity: Int, tier: Int, oppStrength: Int, form: Double): Long {
        val attendance = min(matchDemand(fanbase, tier, oppStrength), capacity.toDouble())
        return (attendance * TICKET_PRICE * form).roundToLong()
    }

    // ─── Form multiplier ─────────────────────────────────────────────────────
    fun formMultiplier(career: Career, beforeRoundIdx: Int): Double {
        val div = CareerEngine.userDivision(career.season, career.controlledTeamId)
        val rec = div.record
        val results = rec.fixtures.mapIndexed { i, f -> f to rec.matches[i] }
            .filter { it.first.round < beforeRoundIdx }
            .sortedBy { it.first.round }
            .mapNotNull { (_, m) ->
                val isHome = m.home == career.controlledTeamId
                val isAway = m.away == career.controlledTeamId
                if (!isHome && !isAway) return@mapNotNull null
                val gf = if (isHome) m.result.homeGoals else m.result.awayGoals
                val ga = if (isHome) m.result.awayGoals else m.result.homeGoals
                if (gf > ga) 1 else if (gf < ga) -1 else 0
            }
        val window = results.takeLast(FORM_WINDOW)
        val raw = 1.0 + window.sumOf { it * FORM_STEP }
        return raw.coerceIn(FORM_MIN, FORM_MAX)
    }

    // ─── Fair-rounded per-round slices ───────────────────────────────────────
    private fun slicedSegment(total: Long, roundIdx: Int, from: Int, to: Int): Long {
        val n = to - from
        if (n <= 0) return 0
        val i = roundIdx - from
        return (total.toDouble() * (i + 1) / n).roundToLong() - (total.toDouble() * i / n).roundToLong()
    }

    private fun totalRounds(career: Career): Int = CareerEngine.totalRounds(career.season)

    /** The tier-derived TV floor, ignoring any signed deal. */
    fun tvFloor(career: Career): Long =
        tvDeal(CareerEngine.userDivision(career.season, career.controlledTeamId).tier)

    /** TV + sponsorship offer slates for next season, anchored on the current
     *  floors. (tv offers, sponsorship offers). */
    fun dealOffers(career: Career): Pair<List<DealOffer>, List<DealOffer>> {
        val tier = CareerEngine.userDivision(career.season, career.controlledTeamId).tier
        return Deals_.generateOffers(career.seed, career.season.year + 1, tier, tvFloor(career), sponsorshipFloor(career))
    }

    /** Income for `roundIdx` of `total`, honouring a signed deal (its amount
     *  overrides the floor) and a mid-season scandal (income reverts to the floor
     *  from the drop round on, each segment fair-rounded to sum cleanly). */
    private fun dealAwareSlice(
        career: Career, roundIdx: Int, total: Int, deal: Deal?, floor: Long, slot: String,
    ): Long {
        if (deal == null) return slicedSegment(floor, roundIdx, 0, total)
        val drop = Deals_.scandalDropRound(career.seed, career.season.year, slot, total)
        return when {
            drop == null -> slicedSegment(deal.seasonAmount, roundIdx, 0, total)
            roundIdx < drop -> slicedSegment(deal.seasonAmount, roundIdx, 0, total)
            else -> slicedSegment(floor, roundIdx, drop, total)
        }
    }

    fun tvIncomeForRound(career: Career, roundIdx: Int): Long {
        val total = totalRounds(career)
        if (total <= 0) return 0
        return dealAwareSlice(career, roundIdx, total, career.activeDeals?.tv, tvFloor(career), "tv")
    }

    fun sponsorshipFloor(career: Career): Long {
        val tier = CareerEngine.userDivision(career.season, career.controlledTeamId).tier
        val last = career.history.lastOrNull()
        val placementTerm = if (last == null) 0.0 else
            SPONSORSHIP_PLACEMENT_BONUS * ((SPONSORSHIP_PLACEMENT_PIVOT - last.userPosition).toDouble() / SPONSORSHIP_PLACEMENT_PIVOT)
        return max(0L, (sponsorshipBase(tier) + career.fanbase * SPONSORSHIP_FANBASE_COEF + placementTerm).roundToLong())
    }

    fun sponsorshipForRound(career: Career, roundIdx: Int): Long {
        val total = totalRounds(career)
        if (total <= 0) return 0
        return dealAwareSlice(career, roundIdx, total, career.activeDeals?.sponsorship, sponsorshipFloor(career), "sponsorship")
    }

    fun matchBonusForRound(career: Career, roundIdx: Int): Long {
        val div = CareerEngine.userDivision(career.season, career.controlledTeamId)
        val rec = div.record
        rec.fixtures.forEachIndexed { i, f ->
            if (f.round != roundIdx) return@forEachIndexed
            val m = rec.matches[i]
            val isHome = m.home == career.controlledTeamId
            val isAway = m.away == career.controlledTeamId
            if (!isHome && !isAway) return@forEachIndexed
            val gf = if (isHome) m.result.homeGoals else m.result.awayGoals
            val ga = if (isHome) m.result.awayGoals else m.result.homeGoals
            return if (gf > ga) WIN_BONUS else if (gf == ga) DRAW_BONUS else 0L
        }
        return 0
    }

    fun seasonSalary(career: Career, registry: Map<Int, Team>): Long {
        if (career.controlledTeamId !in registry) return 0
        // Effective squad (transfer overlay if any) — bought players cost wages.
        return Roster.workingRoster(career, registry).sumOf { avgAttributes(it) * SALARY_PER_PLAYER_STRENGTH }
    }

    fun salarySliceForRound(career: Career, registry: Map<Int, Team>, roundIdx: Int): Long {
        val total = totalRounds(career)
        if (total <= 0) return 0
        return slicedSegment(seasonSalary(career, registry), roundIdx, 0, total)
    }

    fun homeTicketForRound(career: Career, registry: Map<Int, Team>, roundIdx: Int): Long {
        val div = CareerEngine.userDivision(career.season, career.controlledTeamId)
        val rec = div.record
        rec.fixtures.forEachIndexed { i, f ->
            if (f.round != roundIdx) return@forEachIndexed
            val m = rec.matches[i]
            if (m.home == career.controlledTeamId) {
                return homeGateRevenue(
                    career.fanbase, career.stadiumCapacity, div.tier,
                    opponentStrength(registry, m.away), formMultiplier(career, roundIdx),
                )
            }
            if (m.away == career.controlledTeamId) return 0 // away game
        }
        return 0 // bye
    }

    /** Net cash for revealing [roundIdx]: gate + TV + sponsorship + bonus − wages. */
    fun roundCashDelta(career: Career, registry: Map<Int, Team>, roundIdx: Int): Long =
        homeTicketForRound(career, registry, roundIdx) +
            tvIncomeForRound(career, roundIdx) +
            sponsorshipForRound(career, roundIdx) +
            matchBonusForRound(career, roundIdx) -
            salarySliceForRound(career, registry, roundIdx)

    // ─── Season-boundary money ───────────────────────────────────────────────
    fun placementPrizeFor(career: Career): Long {
        val div = CareerEngine.userDivision(career.season, career.controlledTeamId)
        val pos = div.record.standings.indexOfFirst { it.teamId == career.controlledTeamId } + 1
        if (pos <= 0) return 0
        val base = (PLACEMENT_PRIZE_BASE * max(0.0, (PLACEMENT_CUTOFF - pos + 1).toDouble() / PLACEMENT_CUTOFF)).roundToLong()
        return (base * placementTierMult(div.tier)).roundToLong()
    }

    fun prBonus(outcome: UserOutcome): Long = when (outcome) {
        UserOutcome.PROMOTED -> PROMOTION_BONUS
        UserOutcome.RELEGATED -> -RELEGATION_PENALTY
        UserOutcome.STAYED -> 0L
    }

    fun nextFanbase(currentFanbase: Int, tier: Int, position: Int, marketingMomentum: Int): Int {
        val placementAdj = FANBASE_PLACEMENT_SWING * ((FANBASE_PLACEMENT_PIVOT - position).toDouble() / FANBASE_PLACEMENT_PIVOT)
        val target = fanbaseTarget(tier) + placementAdj + marketingMomentum
        val delta = (target - currentFanbase).coerceIn(-FANBASE_MAX_STEP.toDouble(), FANBASE_MAX_STEP.toDouble())
        return max(0L, (currentFanbase + delta).roundToLong()).toInt()
    }

    // ─── Reporting (history, ledger, runway) ─────────────────────────────────
    data class SeasonFinances(
        val ticketRevenue: Long,
        val tvRevenue: Long,
        val sponsorship: Long,
        val matchBonuses: Long,
        val salaries: Long,
        val placementPrize: Long,
        val cupPrize: Long,
        val prBonus: Long,
        val net: Long,
    )

    fun computeSeasonFinances(career: Career, registry: Map<Int, Team>, outcome: UserOutcome): SeasonFinances {
        val total = totalRounds(career)
        var ticket = 0L; var tv = 0L; var sponsorship = 0L; var bonus = 0L; var wages = 0L
        for (r in 0 until total) {
            ticket += homeTicketForRound(career, registry, r)
            tv += tvIncomeForRound(career, r)
            sponsorship += sponsorshipForRound(career, r)
            bonus += matchBonusForRound(career, r)
            wages += salarySliceForRound(career, registry, r)
        }
        val placement = placementPrizeFor(career)
        val cup = cupPrizeTotal(career.season.copa, career.controlledTeamId)
        val pr = prBonus(outcome)
        val net = ticket + tv + sponsorship + bonus - wages + placement + cup + pr
        return SeasonFinances(ticket, tv, sponsorship, bonus, wages, placement, cup, pr, net)
    }

    data class SeasonLedger(
        val rounds: Int,
        val ticket: Long,
        val tv: Long,
        val sponsorship: Long,
        val bonus: Long,
        val wages: Long,
        val net: Long,
    )

    /** Cash already accrued over the rounds played so far ([0, currentRoundIdx)). */
    fun seasonToDateLedger(career: Career, registry: Map<Int, Team>): SeasonLedger {
        val played = min(career.season.currentRoundIdx, totalRounds(career))
        var ticket = 0L; var tv = 0L; var sponsorship = 0L; var bonus = 0L; var wages = 0L
        for (r in 0 until played) {
            ticket += homeTicketForRound(career, registry, r)
            tv += tvIncomeForRound(career, r)
            sponsorship += sponsorshipForRound(career, r)
            bonus += matchBonusForRound(career, r)
            wages += salarySliceForRound(career, registry, r)
        }
        return SeasonLedger(played, ticket, tv, sponsorship, bonus, wages, ticket + tv + sponsorship + bonus - wages)
    }

    data class RunwayProjection(
        val remainingRounds: Int,
        val projectedNet: Long,
        val projectedEndBalance: Long,
        val remainingWages: Long,
        val atRisk: Boolean,
    )

    /** Conservative end-of-season balance projection over the unplayed rounds. */
    fun projectSeasonRunway(career: Career, registry: Map<Int, Team>): RunwayProjection {
        val total = totalRounds(career)
        val from = min(career.season.currentRoundIdx, total)
        var projectedNet = 0L; var remainingWages = 0L
        for (r in from until total) {
            projectedNet += roundCashDelta(career, registry, r)
            remainingWages += salarySliceForRound(career, registry, r)
        }
        val end = career.money + projectedNet
        return RunwayProjection(max(0, total - from), projectedNet, end, remainingWages, end < 0)
    }

    data class HomeDemand(val demand: Double, val capacity: Int, val oppId: Int, val roundIdx: Int)

    /** The user's next home game as projected demand vs. capacity, or null. */
    fun nextHomeDemand(career: Career, registry: Map<Int, Team>): HomeDemand? {
        val div = CareerEngine.userDivision(career.season, career.controlledTeamId)
        val rec = div.record
        val total = totalRounds(career)
        for (round in career.season.currentRoundIdx until total) {
            rec.fixtures.forEachIndexed { i, f ->
                if (f.round != round) return@forEachIndexed
                val m = rec.matches[i]
                if (m.home == career.controlledTeamId) {
                    return HomeDemand(
                        matchDemand(career.fanbase, div.tier, opponentStrength(registry, m.away)),
                        career.stadiumCapacity, m.away, round,
                    )
                }
            }
        }
        return null
    }
}
