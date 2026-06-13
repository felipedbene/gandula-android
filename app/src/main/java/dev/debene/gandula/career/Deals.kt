package dev.debene.gandula.career

import com.squareup.moshi.JsonClass
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Tactics
import kotlin.math.max
import kotlin.math.roundToLong

/** A single transfer-market transaction recorded for the history view. */
@JsonClass(generateAdapter = true)
data class TransferRecord(
    val kind: String, // "buy" | "sell"
    val playerName: String,
    val position: Position,
    val price: Long,
)

/** The user's season tactical overlay (applied to their effective team). */
@JsonClass(generateAdapter = true)
data class SeasonTactics(val formation: Formation, val tactics: Tactics)

/** A negotiable TV/sponsorship contract. When present in [Deals], its
 *  `seasonAmount` overrides the tier-derived income floor for that stream. */
@JsonClass(generateAdapter = true)
data class Deal(
    val id: String,
    val kind: String, // "tv" | "sponsorship"
    val seasonAmount: Long,
    val startYear: Int,
    val termYears: Int,
    /** Performance clause: drops at the boundary if the club finishes worse. */
    val maxPosition: Int? = null,
)

@JsonClass(generateAdapter = true)
data class Deals(val tv: Deal? = null, val sponsorship: Deal? = null)

/** A signable offer = a [Deal] plus a display label. */
data class DealOffer(val deal: Deal, val label: String)

/**
 * Negotiable deals + scandals — port of the v12 deal logic in
 * `web/src/util/finances.ts`. Offers are generated deterministically per
 * (careerSeed, year) so the slate is stable; a signed deal overrides the income
 * floor; a rare scandal can terminate it mid-season; and `keepDeal` decides
 * carry-forward at the season boundary (term expiry / relegation / failed clause).
 */
object Deals_ {
    private const val TV_OFFER_SALT = 0xdea1L
    private const val SPONSOR_OFFER_SALT = 0x5907L
    private const val SCANDAL_SALT = 0x5ca1L
    const val SCANDAL_SEASON_CHANCE = 0.05

    private data class Shape(val label: String, val mult: Double, val clause: Boolean)
    private val OFFER_SHAPES = listOf(
        Shape("Sólida", 1.0, false),
        Shape("Agressiva", 1.3, true),
        Shape("Conservadora", 0.85, false),
    )

    fun clauseMaxPosition(tier: Int): Int = when (tier) { 1 -> 6; 2 -> 10; else -> 12 }

    private fun offersFor(kind: String, floor: Long, tier: Int, seed: Long, year: Int, salt: Long): List<DealOffer> {
        val folded = seed xor year.toLong() xor salt
        val rng = Mulberry32((folded and 0xFFFFFFFFL).toInt())
        return OFFER_SHAPES.mapIndexed { i, shape ->
            val jitter = 0.92 + rng.next() * 0.16
            val termYears = 1 + (rng.next() * 3).toInt() // 1..3
            DealOffer(
                Deal(
                    id = "$kind-$year-$i",
                    kind = kind,
                    seasonAmount = max(0L, (floor * shape.mult * jitter).roundToLong()),
                    startYear = year,
                    termYears = termYears,
                    maxPosition = if (shape.clause) clauseMaxPosition(tier) else null,
                ),
                shape.label,
            )
        }
    }

    /** Offer slates for `year`, anchored on the floors the year's tier/fanbase
     *  would otherwise yield (passed in by the caller). */
    fun generateOffers(
        careerSeed: Long,
        year: Int,
        tier: Int,
        tvFloor: Long,
        sponsorshipFloor: Long,
    ): Pair<List<DealOffer>, List<DealOffer>> =
        offersFor("tv", tvFloor, tier, careerSeed, year, TV_OFFER_SALT) to
            offersFor("sponsorship", sponsorshipFloor, tier, careerSeed, year, SPONSOR_OFFER_SALT)

    /** Whether a deal survives into `nextYear` given the finish. Returns the deal
     *  to carry, or null if it drops (→ derived floor). */
    fun keepDeal(deal: Deal?, slot: String, outcome: UserOutcome, userPosition: Int, nextYear: Int): Deal? {
        if (deal == null) return null
        if (nextYear >= deal.startYear + deal.termYears) return null // term elapsed
        if (slot == "tv" && outcome == UserOutcome.RELEGATED) return null
        if (deal.maxPosition != null && userPosition > deal.maxPosition) return null
        return deal
    }

    /** The round a scandal terminates the deal in `slot` this season, or null.
     *  Pure + deterministic in (careerSeed, year, slot): one roll for IF, one for
     *  WHICH round. */
    fun scandalDropRound(careerSeed: Long, year: Int, slot: String, totalRounds: Int): Int? {
        if (totalRounds <= 0) return null
        val slotSalt = if (slot == "tv") 0L else 0x11L
        val folded = careerSeed xor year.toLong() xor SCANDAL_SALT xor slotSalt
        val rng = Mulberry32((folded and 0xFFFFFFFFL).toInt())
        if (rng.next() >= SCANDAL_SEASON_CHANCE) return null
        return (rng.next() * totalRounds).toInt()
    }
}
