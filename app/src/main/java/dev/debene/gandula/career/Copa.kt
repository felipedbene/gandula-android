package dev.debene.gandula.career

import dev.debene.gandula.domain.Match
import dev.debene.gandula.domain.Team
import dev.debene.gandula.engine.MatchEngine
import dev.debene.gandula.engine.SeasonEngine

/** The six cup rounds, in order (ordinal = bracket depth). */
enum class CupRoundName(val display: String) {
    PRELIM("Preliminar"),
    R32("Fase de 32"),
    R16("Oitavas"),
    QF("Quartas"),
    SF("Semifinal"),
    FINAL("Final"),
}

/**
 * Copa do Brasil — a season-long knockout over all 60 clubs. Port of
 * `web/src/util/copa.ts`. Pure layering over [MatchEngine] (the engine knows
 * nothing of cups/byes/shootouts): a strength-seeded bracket (no PRNG), two-leg
 * ties decided on aggregate → away goals → a seeded shootout, every tie's seed
 * derived from the cup seed. Fully deterministic in (teams, seasonSeed), so it
 * rebuilds identically on re-simulation — and the whole cup is pre-played at
 * season build, then revealed as the user advances league rounds.
 */
object Copa {
    const val COPA_BYE = -1
    private const val CUP_SEED_NS = 0xc09aL
    private const val PRELIM_BYES = 4
    private const val LEG1_NS = 0x1e6001L
    private const val LEG2_NS = 0x1e6002L
    private const val SHOOTOUT_NS = 0x5e0c0aL

    /** Which league round (0-based) each cup round is revealed on. */
    val ROUND_AT_LEAGUE_ROUND = intArrayOf(3, 8, 14, 20, 27, 34)

    data class Shootout(val homeGoals: Int, val awayGoals: Int, val winnerId: Int)

    /** A resolved tie. `aggHome`/`aggAway` are aggregate goals for the leg-1 home
     *  (homeId) / away (awayId) sides. A bye carries `bye=true`, winner = homeId. */
    data class Tie(
        val homeId: Int,
        val awayId: Int,
        val bye: Boolean,
        val winnerId: Int?,
        val aggHome: Int,
        val aggAway: Int,
        val shootout: Shootout?,
    )

    data class Round(val name: CupRoundName, val ties: List<Tie>)

    data class Bracket(
        val rounds: List<Round>,
        val championId: Int?,
        val userEliminatedAtRoundIdx: Int?,
    )

    private fun cupSeed(seasonSeed: Long): Long = seasonSeed xor CUP_SEED_NS

    private fun mulberryFrom(seed: Long): Mulberry32 {
        val lo = (seed and 0xFFFFFFFFL).toInt()
        val hi = ((seed ushr 32) and 0xFFFFFFFFL).toInt()
        return Mulberry32(lo xor hi)
    }

    /** Simulate the whole cup for a season over the (already evolved) `teams`. */
    fun simulate(teams: List<Team>, seasonSeed: Long, controlledTeamId: Int): Bracket {
        val byId = teams.associateBy { it.id }
        val tiers = Divisions.divideIntoDivisions(teams)
        val byeClubs = tiers[0].take(PRELIM_BYES)
        val prelimClubs = tiers[0].drop(PRELIM_BYES) + tiers[1] + tiers[2]

        val prelimTies = ArrayList<Tie>()
        for (i in 0 until prelimClubs.size / 2) {
            val home = prelimClubs[i]
            val away = prelimClubs[prelimClubs.size - 1 - i]
            prelimTies.add(Tie(home.id, away.id, bye = false, winnerId = null, aggHome = 0, aggAway = 0, shootout = null))
        }
        val byeTies = byeClubs.map { Tie(it.id, COPA_BYE, bye = true, winnerId = it.id, aggHome = 0, aggAway = 0, shootout = null) }

        val rounds = mutableListOf(Round(CupRoundName.PRELIM, prelimTies + byeTies))
        val cs = cupSeed(seasonSeed)
        var championId: Int? = null
        var userElim: Int? = null
        var roundIdx = 0

        while (true) {
            val round = rounds[roundIdx]
            val base = tieSeedBase(rounds, roundIdx)
            var counter = 0
            val played = round.ties.map { tie ->
                if (tie.bye || tie.winnerId != null) {
                    tie
                } else {
                    val tieSeed = SeasonEngine.matchSeed(cs, base + counter)
                    counter++
                    resolveTie(tie, tieSeed, byId)
                }
            }
            rounds[roundIdx] = round.copy(ties = played)

            if (userElim == null) {
                val ut = played.firstOrNull { !it.bye && (it.homeId == controlledTeamId || it.awayId == controlledTeamId) }
                if (ut != null && ut.winnerId != controlledTeamId) userElim = roundIdx
            }

            val winners = played.mapNotNull { it.winnerId }
            val nextName = CupRoundName.entries.getOrNull(round.name.ordinal + 1)
            if (nextName == null) {
                championId = winners.firstOrNull()
                break
            }
            val nextTies = ArrayList<Tie>()
            var i = 0
            while (i + 1 < winners.size) {
                nextTies.add(Tie(winners[i], winners[i + 1], bye = false, winnerId = null, aggHome = 0, aggAway = 0, shootout = null))
                i += 2
            }
            rounds.add(Round(nextName, nextTies))
            roundIdx++
        }

        return Bracket(rounds, championId, userElim)
    }

    private fun tieSeedBase(rounds: List<Round>, roundIdx: Int): Int {
        var base = 0
        for (r in 0 until roundIdx) base += rounds[r].ties.count { !it.bye }
        return base
    }

    private fun resolveTie(tie: Tie, tieSeed: Long, byId: Map<Int, Team>): Tie {
        val home = byId.getValue(tie.homeId)
        val away = byId.getValue(tie.awayId)
        val leg1 = MatchEngine.simulate(home, away, tieSeed xor LEG1_NS)
        val leg2 = MatchEngine.simulate(away, home, tieSeed xor LEG2_NS)

        val aggHome = leg1.result.homeGoals + leg2.result.awayGoals
        val aggAway = leg1.result.awayGoals + leg2.result.homeGoals

        if (aggHome != aggAway) {
            val winner = if (aggHome > aggAway) tie.homeId else tie.awayId
            return tie.copy(winnerId = winner, aggHome = aggHome, aggAway = aggAway)
        }
        // Away-goals: homeId scored away in leg 2; awayId scored away in leg 1.
        val homeAway = leg2.result.awayGoals
        val awayAway = leg1.result.awayGoals
        if (homeAway != awayAway) {
            val winner = if (homeAway > awayAway) tie.homeId else tie.awayId
            return tie.copy(winnerId = winner, aggHome = aggHome, aggAway = aggAway)
        }
        // Shootout at leg-2 venue: leg2 home = awayId, leg2 away = homeId.
        val shoot = seededShootout(leg2, tieSeed, leg2HomeId = tie.awayId, leg2AwayId = tie.homeId)
        return tie.copy(winnerId = shoot.winnerId, aggHome = aggHome, aggAway = aggAway, shootout = shoot)
    }

    private fun seededShootout(leg2: Match, tieSeed: Long, leg2HomeId: Int, leg2AwayId: Int): Shootout {
        val rng = mulberryFrom(tieSeed xor leg2.seed xor SHOOTOUT_NS)
        var home = 0
        var away = 0
        repeat(5) {
            if (rng.next() < 0.75) home++
            if (rng.next() < 0.75) away++
        }
        while (home == away) {
            if (rng.next() < 0.75) home++
            if (rng.next() < 0.75) away++
        }
        return Shootout(home, away, if (home > away) leg2HomeId else leg2AwayId)
    }

    // ─── Reveal + result helpers ─────────────────────────────────────────────
    /** Cup rounds whose mapped league round is strictly before `leagueRound`. */
    fun revealedRounds(leagueRound: Int): Int = ROUND_AT_LEAGUE_ROUND.count { it < leagueRound }

    /** The user's tie in a round, or null (not in it / had a bye). */
    fun userTieInRound(bracket: Bracket, roundIdx: Int, controlledTeamId: Int): Tie? =
        bracket.rounds.getOrNull(roundIdx)?.ties?.firstOrNull {
            !it.bye && (it.homeId == controlledTeamId || it.awayId == controlledTeamId)
        }

    /** Cup outcome for history: "champion", the round-name display they went out
     *  at, or null if undecided. */
    fun cupResultFor(bracket: Bracket, controlledTeamId: Int): String? = when {
        bracket.championId == controlledTeamId -> "champion"
        bracket.userEliminatedAtRoundIdx != null ->
            bracket.rounds.getOrNull(bracket.userEliminatedAtRoundIdx)?.name?.display
        else -> null
    }
}
