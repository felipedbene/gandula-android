package dev.debene.gandula.domain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Enums (constant names match the JSON strings exactly so Moshi maps them
// with zero extra configuration: "F433", "Attacking", "GK", ...) ────────────────

enum class Position { GK, DEF, MID, FWD }

enum class Formation { F442, F433, F352, F4231 }

enum class Mentality { VeryDefensive, Defensive, Balanced, Attacking, VeryAttacking }

enum class Tempo { Slow, Normal, Fast }

enum class Pressing { Low, Medium, High }

enum class Width { Narrow, Normal, Wide }

@JsonClass(generateAdapter = true)
data class Attributes(
    val pace: Int,
    val technique: Int,
    val passing: Int,
    val defending: Int,
    val finishing: Int,
    val stamina: Int,
)

@JsonClass(generateAdapter = true)
data class Tactics(
    val mentality: Mentality,
    val tempo: Tempo,
    val pressing: Pressing,
    val width: Width,
)

@JsonClass(generateAdapter = true)
data class Player(
    val id: Int,
    val name: String,
    val age: Int,
    val position: Position,
    val attributes: Attributes,
)

@JsonClass(generateAdapter = true)
data class Team(
    val id: Int,
    val name: String,
    val roster: List<Player>,
    val formation: Formation,
    val tactics: Tactics,
    @param:Json(name = "starting_xi") val startingXi: List<Int>,
    val bench: List<Int> = emptyList(),
) {
    fun lookup(id: Int): Player? = roster.firstOrNull { it.id == id }
}

// ─── Match output ───────────────────────────────────────────────────────────

enum class Side {
    Home,
    Away;

    fun flip(): Side = if (this == Home) Away else Home
}

enum class NearMissKind { Post, Crossbar, JustWide }

/** Data classes throughout so structural equality powers the determinism test. */
sealed interface MatchEventKind {
    data class Shot(val shooter: Int, val onTarget: Boolean) : MatchEventKind
    data class Goal(val scorer: Int, val assist: Int?) : MatchEventKind
    data class Foul(val offender: Int, val victim: Int) : MatchEventKind
    data class YellowCard(val player: Int) : MatchEventKind
    data class RedCard(val player: Int) : MatchEventKind
    data class Substitution(val off: Int, val on: Int) : MatchEventKind
    data class PenaltyAwarded(val taker: Int) : MatchEventKind
    data class PenaltyMissed(val taker: Int) : MatchEventKind
    data class NearMiss(val shooter: Int, val kind: NearMissKind) : MatchEventKind
    data object HalfTime : MatchEventKind
    data object FullTime : MatchEventKind
}

data class MatchEvent(
    val minute: Int,
    val side: Side?,
    val kind: MatchEventKind,
    val text: String,
)

data class MatchResult(val homeGoals: Int, val awayGoals: Int)

data class Match(
    val home: Int,
    val away: Int,
    val seed: Long,
    val result: MatchResult,
    val events: List<MatchEvent>,
)
