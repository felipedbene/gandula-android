package dev.debene.gandula.career

import dev.debene.gandula.career.Divisions.tierName
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Team
import dev.debene.gandula.engine.MatchEngine
import dev.debene.gandula.engine.SeasonEngine
import dev.debene.gandula.engine.SeasonRecord
import dev.debene.gandula.engine.TeamStats
import kotlin.random.Random

// ─── In-memory career model (port of web persistence.ts shapes) ─────────────

/** One tier within a season. `teamIds` is the schedule order; `record` is the
 *  fully pre-simulated season for this division. */
data class Division(
    val tier: Int,
    val name: String,
    val teamIds: List<Int>,
    val record: SeasonRecord,
)

/** A season in progress. `currentRoundIdx` is the reveal cursor (how many rounds
 *  the user has played); all three tiers run the same 38 rounds in lockstep. */
data class Season(
    val year: Int,
    val seed: Long,
    val divisions: List<Division>, // index 0 = Série A
    val currentRoundIdx: Int,
    /** Pre-simulated Copa do Brasil for this season (revealed by league round). */
    val copa: Copa.Bracket,
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class SeasonHistory(
    val year: Int,
    val userTier: Int,
    val userDivisionName: String,
    val userPosition: Int,
    val userPoints: Int,
    val championId: Int,
    val championName: String,
    val userOutcome: UserOutcome,
    val moneyDelta: Long,
    val moneyAfter: Long,
    /** Copa do Brasil champion id this season, and the user's cup result
     *  ("champion" or the round they went out at). Null when undecided. */
    val copaChampionId: Int? = null,
    val copaUserResult: String? = null,
    /** Transfers the user made during this season. */
    val transfers: List<TransferRecord> = emptyList(),
)

/** Top-level career save (in memory). One per user. `stadiumCapacity`/`fanbase`/
 *  `marketingMomentum` are the cross-season manager economy state; `fired` is the
 *  lose-condition flag (set when the balance goes negative). */
data class Career(
    val seed: Long,
    val controlledTeamId: Int,
    val money: Long,
    val stadiumCapacity: Int,
    val fanbase: Int,
    val marketingMomentum: Int,
    val season: Season,
    val history: List<SeasonHistory>,
    val fired: Boolean = false,
    /** Transfer-market squad overlay. Empty = use the registry default. */
    val userRoster: List<Player> = emptyList(),
    /** Season tactical overlay (formation + tactics). Null = registry default. */
    val userTactics: SeasonTactics? = null,
    /** Signed TV/sponsorship deals that override the income floors. */
    val activeDeals: Deals? = null,
    /** Transfers made this season (reset at the boundary into history). */
    val transfers: List<TransferRecord> = emptyList(),
    /** Half-time tactical changes the user confirmed, keyed by league round. The
     *  user's match in that round is re-simulated with these second-half tactics;
     *  persisted so a reload reproduces the steered result. */
    val halftimeTactics: Map<Int, SeasonTactics> = emptyMap(),
)

/**
 * Career orchestration — port of the structural core of `web/src/util/career.ts`.
 * Deferred from upstream (later slices): full economy/finances, Copa, transfers,
 * aging/regen, and rival coaches. Opponents reset to the registry each season.
 */
object CareerEngine {
    const val FIRST_YEAR = 2026
    const val STARTING_MONEY = 2_000_000L
    const val STARTER_TIER = 3 // new careers begin in Série C

    fun seasonSeed(careerSeed: Long, year: Int): Long = careerSeed xor year.toLong()
    private fun divSeed(seasonSeed: Long, tier: Int): Long = seasonSeed xor tier.toLong()

    fun totalRounds(season: Season): Int {
        val fixtures = season.divisions.firstOrNull()?.record?.fixtures ?: return 0
        return if (fixtures.isEmpty()) 0 else fixtures.maxOf { it.round } + 1
    }

    fun seasonComplete(career: Career): Boolean =
        career.season.currentRoundIdx >= totalRounds(career.season)

    fun userDivision(season: Season, controlledTeamId: Int): Division =
        season.divisions.first { controlledTeamId in it.teamIds }

    /** Reveal one more round, accruing that round's cash (gate + TV + sponsorship
     *  + bonus − wages). No-op once the season is complete. Sets `fired` if the
     *  balance goes negative. */
    fun revealNextRound(career: Career, registry: Map<Int, Team>): Career {
        if (seasonComplete(career)) return career
        val round = career.season.currentRoundIdx
        val money = career.money + Finances.roundCashDelta(career, registry, round)
        val updated = career.copy(
            money = money,
            season = career.season.copy(currentRoundIdx = round + 1),
        )
        return if (Finances.isManagerFired(money)) updated.copy(fired = true) else updated
    }

    /** Pay to add seats (capped). No-op if unaffordable or already maxed. */
    fun expandStadium(career: Career): Career {
        val cost = Finances.expansionCost(career.stadiumCapacity)
        if (career.stadiumCapacity >= Finances.STADIUM_MAX_CAPACITY || career.money < cost) return career
        return career.copy(
            money = career.money - cost,
            stadiumCapacity = minOf(career.stadiumCapacity + Finances.STADIUM_EXPANSION_STEP, Finances.STADIUM_MAX_CAPACITY),
        )
    }

    /** Pay for a marketing campaign: grows fanbase now + adds decaying momentum. */
    fun runMarketingCampaign(career: Career): Career {
        val cost = Finances.marketingCost(career.marketingMomentum)
        if (career.marketingMomentum >= Finances.MARKETING_MOMENTUM_MAX || career.money < cost) return career
        return career.copy(
            money = career.money - cost,
            fanbase = career.fanbase + Finances.CAMPAIGN_FANBASE,
            marketingMomentum = minOf(career.marketingMomentum + Finances.MARKETING_MOMENTUM_PER_CAMPAIGN, Finances.MARKETING_MOMENTUM_MAX),
        )
    }

    /** Standings for [division] up to the season's reveal cursor. */
    fun standingsUpTo(season: Season, division: Division): List<TeamStats> =
        SeasonEngine.computeStandings(
            division.teamIds, division.record.fixtures, division.record.matches, season.currentRoundIdx,
        )

    // ─── New career ──────────────────────────────────────────────────────────
    fun newCareer(teams: List<Team>, seed: Long, random: Random = Random.Default): Career {
        val tiers = Divisions.divideIntoDivisions(teams)
        val tierIds = tiers.map { tier -> tier.map { it.id } }
        val starter = Divisions.pickRandomStarter(tiers[2], random)
        val registry = teams.associateBy { it.id }
        val season = buildSeason(seed, FIRST_YEAR, tierIds, registry, starter.id, emptyList())
        return Career(
            seed = seed,
            controlledTeamId = starter.id,
            money = STARTING_MONEY,
            stadiumCapacity = Finances.startingCapacity(STARTER_TIER),
            fanbase = Finances.startingFanbase(STARTER_TIER),
            marketingMomentum = 0,
            season = season,
            history = emptyList(),
        )
    }

    /** Build (simulate) a full season from per-tier ordered team-id lists. The
     *  controlled team is substituted with its effective (transfer-overlaid)
     *  squad so bought players actually take the pitch. */
    /** The team that takes the pitch for `id` in this (careerSeed, year) season:
     *  the user's effective squad (transfer + tactics overlay), or an opponent
     *  replayed forward `elapsed` years (age + youth) and coached for its tier.
     *  Deterministic from the registry — the basis of the re-sim-on-load invariant. */
    private fun resolveTeam(
        careerSeed: Long, year: Int, registry: Map<Int, Team>,
        controlledTeamId: Int, userRoster: List<Player>, userTactics: SeasonTactics?,
        id: Int, tier: Int,
    ): Team {
        if (id == controlledTeamId) return Roster.effectiveTeam(registry.getValue(controlledTeamId), userRoster, userTactics)
        val base = registry.getValue(id)
        val elapsed = year - FIRST_YEAR
        if (elapsed <= 0) return base
        val evolved = Regen.evolveTeam(base, elapsed, careerSeed)
        return RivalCoach.applyRivalCoach(evolved, tier, year, careerSeed, elapsed)
    }

    fun buildSeason(
        careerSeed: Long,
        year: Int,
        tierIds: List<List<Int>>,
        registry: Map<Int, Team>,
        controlledTeamId: Int,
        userRoster: List<Player>,
        userTactics: SeasonTactics? = null,
        halftimeTactics: Map<Int, SeasonTactics> = emptyMap(),
    ): Season {
        val sSeed = seasonSeed(careerSeed, year)
        fun resolve(id: Int, tier: Int): Team =
            resolveTeam(careerSeed, year, registry, controlledTeamId, userRoster, userTactics, id, tier)

        val allResolved = ArrayList<Team>(Divisions.WORLD_SIZE)
        var divisions = tierIds.mapIndexed { i, ids ->
            val tier = i + 1
            val teams = ids.map { resolve(it, tier) }
            allResolved.addAll(teams)
            val record = SeasonEngine.simulateSeason(teams, divSeed(sSeed, tier), tierName(tier))
            Division(tier, tierName(tier), ids, record)
        }
        // Re-simulate the user's second halves where a half-time change was made.
        if (halftimeTactics.isNotEmpty()) {
            val userTierIdx = tierIds.indexOfFirst { controlledTeamId in it }
            if (userTierIdx >= 0) {
                divisions = divisions.toMutableList().also {
                    it[userTierIdx] = patchHalftime(
                        it[userTierIdx], sSeed, ::resolve, controlledTeamId, userRoster, userTactics, registry, careerSeed, year, halftimeTactics,
                    )
                }
            }
        }
        // The Copa fields the same evolved/coached sides the league does.
        val copa = Copa.simulate(allResolved, sSeed, controlledTeamId)
        return Season(year, sSeed, divisions, currentRoundIdx = 0, copa = copa)
    }

    /** Replace the user's matches in `division` for any round with a half-time
     *  override: re-run the first half (identical), then the second half with the
     *  user's edited tactics, and recompute the standings. */
    private fun patchHalftime(
        division: Division,
        sSeed: Long,
        resolve: (Int, Int) -> Team,
        controlledTeamId: Int,
        userRoster: List<Player>,
        userTactics: SeasonTactics?,
        registry: Map<Int, Team>,
        careerSeed: Long,
        year: Int,
        halftimeTactics: Map<Int, SeasonTactics>,
    ): Division {
        val rec = division.record
        val dSeed = divSeed(sSeed, division.tier)
        val matches = rec.matches.toMutableList()
        rec.fixtures.forEachIndexed { i, f ->
            val override = halftimeTactics[f.round] ?: return@forEachIndexed
            val homeId = division.teamIds[f.homeIdx]
            val awayId = division.teamIds[f.awayIdx]
            if (homeId != controlledTeamId && awayId != controlledTeamId) return@forEachIndexed
            val homeTeam = resolve(homeId, division.tier)
            val awayTeam = resolve(awayId, division.tier)
            val half = MatchEngine.simulateFirstHalf(homeTeam, awayTeam, SeasonEngine.matchSeed(dSeed, i))
            val editedHome = if (homeId == controlledTeamId) applyTactics(homeTeam, override) else homeTeam
            val editedAway = if (awayId == controlledTeamId) applyTactics(awayTeam, override) else awayTeam
            matches[i] = MatchEngine.simulateSecondHalf(half, editedHome, editedAway)
        }
        val standings = SeasonEngine.computeStandings(division.teamIds, rec.fixtures, matches, Int.MAX_VALUE)
        return division.copy(record = rec.copy(matches = matches, standings = standings))
    }

    private fun applyTactics(team: Team, t: SeasonTactics): Team =
        team.copy(formation = t.formation, tactics = t.tactics)

    // ─── Advance to next season (apply P/R, record history, money) ───────────
    fun advanceSeason(career: Career, registry: Map<Int, Team>): Career {
        val s = career.season
        val standA = s.divisions[0].record.standings
        val standB = s.divisions[1].record.standings
        val standC = s.divisions[2].record.standings
        val pr = Promotion.compute(standA, standB, standC, career.controlledTeamId)
        val outcome = Promotion.userOutcome(pr)

        val userDiv = userDivision(s, career.controlledTeamId)
        val userPos = userDiv.record.standings.indexOfFirst { it.teamId == career.controlledTeamId } + 1
        val userStats = userDiv.record.standings[userPos - 1]
        val champion = userDiv.record.standings[0]

        // Per-round cash already accrued into money during the season; the
        // boundary pieces (placement prize + P/R bonus + Copa prize) are new money.
        val finances = Finances.computeSeasonFinances(career, registry, outcome)
        val moneyAfter = career.money + finances.prBonus + finances.placementPrize + finances.cupPrize
        val history = SeasonHistory(
            year = s.year,
            userTier = userDiv.tier,
            userDivisionName = userDiv.name,
            userPosition = userPos,
            userPoints = userStats.points,
            championId = champion.teamId,
            championName = registry[champion.teamId]?.name ?: "Time ${champion.teamId}",
            userOutcome = outcome,
            moneyDelta = finances.net,
            moneyAfter = moneyAfter,
            copaChampionId = s.copa.championId,
            copaUserResult = Copa.cupResultFor(s.copa, career.controlledTeamId),
            transfers = career.transfers,
        )

        // Fanbase drifts toward the tier it'll play in next, swung by placement;
        // marketing momentum decays.
        val nextTier = when (outcome) {
            UserOutcome.PROMOTED -> userDiv.tier - 1
            UserOutcome.RELEGATED -> userDiv.tier + 1
            UserOutcome.STAYED -> userDiv.tier
        }
        val nextFanbase = Finances.nextFanbase(career.fanbase, nextTier, userPos, career.marketingMomentum)
        val nextMomentum = Finances.nextMarketingMomentum(career.marketingMomentum)

        // Age the user's squad one season too (retire + youth), symmetric with
        // the opponents — so a transfer-free career still ages and renews. The
        // result is persisted as the new userRoster and simulated against.
        val nextYearOffset = (s.year + 1) - FIRST_YEAR
        val agedUserRoster = Regen.evolveRoster(
            Roster.workingRoster(career, registry), career.seed, career.controlledTeamId, nextYearOffset,
        )

        // Carry signed deals forward, dropping any that expire / fail their
        // clause / lapse on relegation (TV).
        val nextYear = s.year + 1
        val nextDeals = career.activeDeals?.let {
            Deals(
                tv = Deals_.keepDeal(it.tv, "tv", outcome, userPos, nextYear),
                sponsorship = Deals_.keepDeal(it.sponsorship, "sponsorship", outcome, userPos, nextYear),
            )
        }

        val nextTierIds = applyPromotionRelegation(standA, standB, standC, pr)
        val nextSeason = buildSeason(
            career.seed, nextYear, nextTierIds, registry, career.controlledTeamId, agedUserRoster, career.userTactics,
        )
        return Career(
            seed = career.seed,
            controlledTeamId = career.controlledTeamId,
            money = moneyAfter,
            stadiumCapacity = career.stadiumCapacity,
            fanbase = nextFanbase,
            marketingMomentum = nextMomentum,
            season = nextSeason,
            history = career.history + history,
            fired = Finances.isManagerFired(moneyAfter),
            userRoster = agedUserRoster,
            userTactics = career.userTactics,
            activeDeals = nextDeals,
            transfers = emptyList(), // fresh season's market is empty
        )
    }

    // ─── Half-time (live, per-match second-half tactics) ─────────────────────
    /** The user's first half for [round] (deterministic — identical to the
     *  pre-simulated match's first half), plus whether the user is home. The
     *  caller shows the interval score, then calls [applyHalftime] if changed. */
    fun userFirstHalf(career: Career, registry: Map<Int, Team>, round: Int): Pair<MatchEngine.HalfTimeState, Boolean>? {
        val s = career.season
        val div = userDivision(s, career.controlledTeamId)
        val dSeed = divSeed(s.seed, div.tier)
        div.record.fixtures.forEachIndexed { i, f ->
            if (f.round != round) return@forEachIndexed
            val homeId = div.teamIds[f.homeIdx]
            val awayId = div.teamIds[f.awayIdx]
            if (homeId != career.controlledTeamId && awayId != career.controlledTeamId) return@forEachIndexed
            val homeTeam = resolveTeam(career.seed, s.year, registry, career.controlledTeamId, career.userRoster, career.userTactics, homeId, div.tier)
            val awayTeam = resolveTeam(career.seed, s.year, registry, career.controlledTeamId, career.userRoster, career.userTactics, awayId, div.tier)
            val half = MatchEngine.simulateFirstHalf(homeTeam, awayTeam, SeasonEngine.matchSeed(dSeed, i))
            return half to (homeId == career.controlledTeamId)
        }
        return null
    }

    /** Record a half-time tactics change for [round] and re-simulate the user's
     *  second half, patching the record + standings. Persisted so a reload
     *  reproduces the steered match. */
    fun applyHalftime(career: Career, registry: Map<Int, Team>, round: Int, override: SeasonTactics): Career {
        val s = career.season
        val userTierIdx = s.divisions.indexOfFirst { career.controlledTeamId in it.teamIds }
        if (userTierIdx < 0) return career
        val resolve = { id: Int, tier: Int ->
            resolveTeam(career.seed, s.year, registry, career.controlledTeamId, career.userRoster, career.userTactics, id, tier)
        }
        val patched = patchHalftime(
            s.divisions[userTierIdx], s.seed, resolve, career.controlledTeamId,
            career.userRoster, career.userTactics, registry, career.seed, s.year, mapOf(round to override),
        )
        val divisions = s.divisions.toMutableList().also { it[userTierIdx] = patched }
        return career.copy(
            season = s.copy(divisions = divisions),
            halftimeTactics = career.halftimeTactics + (round to override),
        )
    }

    // ─── Pre-season actions (set tactics / sign deals; gated to season end) ───
    fun setTactics(career: Career, tactics: SeasonTactics): Career = career.copy(userTactics = tactics)

    fun signDeal(career: Career, deal: Deal): Career {
        val deals = career.activeDeals ?: Deals()
        val next = if (deal.kind == "tv") deals.copy(tv = deal) else deals.copy(sponsorship = deal)
        return career.copy(activeDeals = next)
    }

    /** Recompose the three tiers across both boundaries: survivors (in finishing
     *  order) then incomers. Each tier stays at 20. */
    fun applyPromotionRelegation(
        standA: List<TeamStats>,
        standB: List<TeamStats>,
        standC: List<TeamStats>,
        pr: Promotion.PRResult,
    ): List<List<Int>> {
        fun ids(list: List<TeamStats>) = list.map { it.teamId }
        val leavingA = ids(pr.relegatedAtoB).toSet()
        val tierA = standA.filter { it.teamId !in leavingA }.map { it.teamId } + ids(pr.promotedBtoA)

        val leavingB = (ids(pr.promotedBtoA) + ids(pr.relegatedBtoC)).toSet()
        val tierB = standB.filter { it.teamId !in leavingB }.map { it.teamId } +
            ids(pr.relegatedAtoB) + ids(pr.promotedCtoB)

        val leavingC = ids(pr.promotedCtoB).toSet()
        val tierC = standC.filter { it.teamId !in leavingC }.map { it.teamId } + ids(pr.relegatedBtoC)

        return listOf(tierA, tierB, tierC)
    }
}
