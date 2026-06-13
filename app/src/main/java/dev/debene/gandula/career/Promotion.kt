package dev.debene.gandula.career

import dev.debene.gandula.engine.TeamStats

/**
 * Promotion / relegation across the two boundaries of the three-tier pyramid.
 * Port of upstream `web/src/util/promotion.ts`. 3 up / 3 down at each boundary
 * keeps every tier at 20 (the middle tier nets zero).
 *
 * Tiebreaks come for free from the engine's standings sort — each division's
 * `standings` is already in finishing order, so slicing is the whole algorithm.
 */
object Promotion {
    const val PROMOTION_SLOTS = 3
    const val RELEGATION_SLOTS = 3

    data class PRResult(
        val promotedBtoA: List<TeamStats>,
        val relegatedAtoB: List<TeamStats>,
        val promotedCtoB: List<TeamStats>,
        val relegatedBtoC: List<TeamStats>,
        val userPromoted: Boolean,
        val userRelegated: Boolean,
    )

    /** `divisionStandings` is indexed by tier-1 (index 0 = Série A standings). */
    fun compute(
        tierAStandings: List<TeamStats>,
        tierBStandings: List<TeamStats>,
        tierCStandings: List<TeamStats>,
        controlledTeamId: Int,
    ): PRResult {
        val promotedBtoA = tierBStandings.take(PROMOTION_SLOTS)
        val relegatedAtoB = tierAStandings.takeLast(RELEGATION_SLOTS)
        val promotedCtoB = tierCStandings.take(PROMOTION_SLOTS)
        val relegatedBtoC = tierBStandings.takeLast(RELEGATION_SLOTS)

        fun isUser(s: TeamStats) = s.teamId == controlledTeamId
        return PRResult(
            promotedBtoA = promotedBtoA,
            relegatedAtoB = relegatedAtoB,
            promotedCtoB = promotedCtoB,
            relegatedBtoC = relegatedBtoC,
            userPromoted = promotedBtoA.any(::isUser) || promotedCtoB.any(::isUser),
            userRelegated = relegatedAtoB.any(::isUser) || relegatedBtoC.any(::isUser),
        )
    }

    fun userOutcome(pr: PRResult): UserOutcome = when {
        pr.userPromoted -> UserOutcome.PROMOTED
        pr.userRelegated -> UserOutcome.RELEGATED
        else -> UserOutcome.STAYED
    }
}

enum class UserOutcome { PROMOTED, RELEGATED, STAYED }
