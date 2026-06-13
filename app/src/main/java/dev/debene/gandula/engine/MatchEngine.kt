package dev.debene.gandula.engine

import dev.debene.gandula.domain.Match
import dev.debene.gandula.domain.MatchEvent
import dev.debene.gandula.domain.MatchEventKind
import dev.debene.gandula.domain.MatchResult
import dev.debene.gandula.domain.NearMissKind
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Side
import dev.debene.gandula.domain.Team
import dev.debene.gandula.rng.MatchRng

/**
 * The deterministic match engine. One-shot port of upstream `core/src/engine`
 * (tick.rs + manager.rs + mod.rs), folded into a single file.
 *
 * `simulate(home, away, seed)` is a pure function: identical inputs produce an
 * identical [Match] (event log included). This port runs the match in one pass
 * (90 minutes + 0–4 injury), omitting the web build's half-time snapshot/resume
 * machinery, which only exists for mid-match tactics editing.
 */
object MatchEngine {

    // ─── Tunables (tick.rs) ──────────────────────────────────────────────────
    private const val BASE_STAMINA_DRAIN = 0.30
    private const val FOUL_BASE_WITHIN_EVENT = 0.15

    private const val ON_TARGET_BASE = 0.35
    private const val ON_TARGET_TECHNIQUE_SCALE = 1.0 / 200.0
    private const val ON_TARGET_MIN = 0.10
    private const val ON_TARGET_MAX = 0.85

    private const val GOAL_BASE = 0.32
    private const val GOAL_FINISHING_GK_SCALE = 1.0 / 200.0
    private const val GOAL_MIN = 0.05
    private const val GOAL_MAX = 0.70

    private const val ASSIST_PROB = 0.60

    private const val CARD_NONE = 0.70
    private const val CARD_YELLOW = 0.25
    private const val CARD_RED = 0.05

    private const val PENALTY_FOUL_RATE = 0.04

    private const val PENALTY_CONVERSION_BASE = 0.75
    private const val PENALTY_CONVERSION_SCALE = 0.005
    private const val PENALTY_CONVERSION_MIN = 0.50
    private const val PENALTY_CONVERSION_MAX = 0.95

    private const val NEAR_MISS_PROMOTION_RATE = 0.50

    // ─── Manager tunables (manager.rs) ───────────────────────────────────────
    private const val MAX_SUBS_PER_MATCH = 3
    private const val STAMINA_SUB_THRESHOLD = 40.0
    private const val STAMINA_FRESH_THRESHOLD = 70.0
    private const val STAMINA_RULE_MIN_MINUTE = 55
    private const val GAME_STATE_RULE_MIN_MINUTE = 70

    internal class PendingPenalty(val side: Side, val taker: Int)

    private data class ManagerConfig(
        val maxSubsPerMatch: Int = MAX_SUBS_PER_MATCH,
        val staminaSubThreshold: Double = STAMINA_SUB_THRESHOLD,
        val staminaFreshThreshold: Double = STAMINA_FRESH_THRESHOLD,
        val staminaRuleMinMinute: Int = STAMINA_RULE_MIN_MINUTE,
        val gameStateRuleMinMinute: Int = GAME_STATE_RULE_MIN_MINUTE,
    )

    private val BALANCED = ManagerConfig()
    private val CAUTIOUS = ManagerConfig(
        staminaSubThreshold = 45.0,
        staminaRuleMinMinute = 50,
        gameStateRuleMinMinute = 65,
    )
    private val BOLD = ManagerConfig(
        staminaSubThreshold = 35.0,
        staminaFreshThreshold = 65.0,
        staminaRuleMinMinute = 60,
        gameStateRuleMinMinute = 75,
    )

    /** Deterministic per-club manager style, keyed off team id (manager.rs). */
    private fun managerConfigFor(teamId: Int): ManagerConfig = when (teamId % 3) {
        0 -> BALANCED
        1 -> CAUTIOUS
        else -> BOLD
    }

    // ─── Mutable per-match state ─────────────────────────────────────────────
    // `home`/`away` are var so the second half can swap in tactic-edited teams
    // (same players/XI, only formation+tactics differ) — the half-time change.
    internal class MatchState(var home: Team, var away: Team) {
        val homeCurrentXi: IntArray = home.startingXi.toIntArray()
        val awayCurrentXi: IntArray = away.startingXi.toIntArray()
        val homeStamina = DoubleArray(11) { home.lookup(home.startingXi[it])?.attributes?.stamina?.toDouble() ?: 0.0 }
        val awayStamina = DoubleArray(11) { away.lookup(away.startingXi[it])?.attributes?.stamina?.toDouble() ?: 0.0 }
        val homeOnField = BooleanArray(11) { true }
        val awayOnField = BooleanArray(11) { true }
        val homeBenchUsed = BooleanArray(home.bench.size)
        val awayBenchUsed = BooleanArray(away.bench.size)
        var homeSubsUsed = 0
        var awaySubsUsed = 0
        var homeGoals = 0
        var awayGoals = 0
        val events = mutableListOf<MatchEvent>()
        var pendingPenalty: PendingPenalty? = null

        fun teamFor(side: Side): Team = if (side == Side.Home) home else away

        fun ctxFor(side: Side, minute: Int): Narration.Context {
            val (own, theirs) = if (side == Side.Home) homeGoals to awayGoals else awayGoals to homeGoals
            return Narration.Context(minute, own - theirs)
        }

        fun toMatch(seed: Long): Match = Match(
            home = home.id,
            away = away.id,
            seed = seed,
            result = MatchResult(homeGoals, awayGoals),
            events = events.toList(),
        )
    }

    // ─── Public entrypoint ───────────────────────────────────────────────────
    /** Live mid-match state captured at the interval, so the second half can be
     *  run separately (and, for the user, with edited tactics). Holds the live
     *  [MatchState] and a snapshot of the RNG, so the second half resumes the
     *  exact stream rather than re-seeding. */
    class HalfTimeState internal constructor(
        internal val state: MatchState,
        internal val rng: MatchRng,
        val seed: Long,
    ) {
        val homeGoals: Int get() = state.homeGoals
        val awayGoals: Int get() = state.awayGoals
        val homeName: String get() = state.home.name
        val awayName: String get() = state.away.name
    }

    /** Run minutes 1..45 (closing any 45' penalty) and stop at the interval. */
    fun simulateFirstHalf(home: Team, away: Team, seed: Long): HalfTimeState {
        validate(home)
        validate(away)
        val rng = MatchRng(seed)
        val state = MatchState(home, away)
        for (minute in 1..45) {
            tick(state, rng, minute)
            runManagers(state, rng, minute)
        }
        forceResolvePendingPenalty(state, rng, 45)
        state.events.add(
            MatchEvent(
                minute = 45, side = null, kind = MatchEventKind.HalfTime,
                text = Narration.halfTime(rng, state.home.name, state.homeGoals, state.away.name, state.awayGoals),
            ),
        )
        return HalfTimeState(state, rng, seed)
    }

    /** Resume from the interval and run 46..90 + injury time. `home`/`away` supply
     *  tactics/formation (re-read every tick), so passing tactic-edited teams here
     *  is how a half-time change takes effect. Pass the same teams for no change. */
    fun simulateSecondHalf(half: HalfTimeState, home: Team, away: Team): Match {
        val state = half.state
        state.home = home
        state.away = away
        val rng = half.rng
        for (minute in 46..90) {
            tick(state, rng, minute)
            runManagers(state, rng, minute)
        }
        val injury = rng.rangeU32(0, 5)
        for (i in 1..injury) {
            tick(state, rng, 90 + i)
            runManagers(state, rng, 90 + i)
        }
        val finalMinute = 90 + injury
        state.events.add(
            MatchEvent(
                minute = finalMinute, side = null, kind = MatchEventKind.FullTime,
                text = Narration.fullTime(rng, finalMinute, state.home.name, state.homeGoals, state.away.name, state.awayGoals),
            ),
        )
        return state.toMatch(half.seed)
    }

    /** Simulate a match end-to-end. Pure function of (home, away, seed) — a thin
     *  composition of the two halves with unchanged tactics, byte-identical to the
     *  former one-shot loop. */
    fun simulate(home: Team, away: Team, seed: Long): Match =
        simulateSecondHalf(simulateFirstHalf(home, away, seed), home, away)

    private fun validate(team: Team) {
        require(team.startingXi.size == 11) { "${team.name}: starting XI must have 11 players" }
        for (id in team.startingXi) {
            requireNotNull(team.lookup(id)) { "${team.name}: XI references unknown player $id" }
        }
    }

    // ─── Per-tick drive ──────────────────────────────────────────────────────
    private fun tick(state: MatchState, rng: MatchRng, minute: Int) {
        drainStamina(state)

        state.pendingPenalty?.let { pen ->
            state.pendingPenalty = null
            resolvePenalty(state, rng, minute, pen)
            return
        }

        val homeStr = currentStrength(state, Side.Home)
        val awayStr = currentStrength(state, Side.Away)

        val pHome = Strength.possessionHome(homeStr, awayStr)
        val attackerSide = if (rng.chance(pHome)) Side.Home else Side.Away

        val attackerTeam = state.teamFor(attackerSide)
        if (!rng.chance(Strength.eventProb(attackerTeam.tactics.tempo))) return

        val attStr = if (attackerSide == Side.Home) homeStr else awayStr
        val defStr = if (attackerSide == Side.Home) awayStr else homeStr
        val defenderTeam = state.teamFor(attackerSide.flip())

        val shotP = Strength.shotProb(attStr, defStr)
        val foulP = FOUL_BASE_WITHIN_EVENT * Strength.pressingFoulFactor(defenderTeam.tactics.pressing)

        val r = rng.unit()
        if (r < shotP) {
            resolveShot(state, rng, minute, attackerSide)
        } else if (r < shotP + foulP) {
            resolveFoul(state, rng, minute, attackerSide)
        }
    }

    private fun forceResolvePendingPenalty(state: MatchState, rng: MatchRng, minute: Int) {
        state.pendingPenalty?.let { pen ->
            state.pendingPenalty = null
            resolvePenalty(state, rng, minute, pen)
        }
    }

    // ─── Stamina ─────────────────────────────────────────────────────────────
    private fun drainStamina(state: MatchState) {
        val homeDrain = BASE_STAMINA_DRAIN *
            Strength.tempoStaminaFactor(state.home.tactics.tempo) *
            Strength.pressingStaminaFactor(state.home.tactics.pressing)
        val awayDrain = BASE_STAMINA_DRAIN *
            Strength.tempoStaminaFactor(state.away.tactics.tempo) *
            Strength.pressingStaminaFactor(state.away.tactics.pressing)
        for (i in 0 until 11) {
            if (state.homeOnField[i]) state.homeStamina[i] = maxOf(state.homeStamina[i] - homeDrain, 0.0)
            if (state.awayOnField[i]) state.awayStamina[i] = maxOf(state.awayStamina[i] - awayDrain, 0.0)
        }
    }

    // ─── Strength snapshot using current XI + stamina ────────────────────────
    private fun currentStrength(state: MatchState, side: Side): TeamStrength {
        val team: Team
        val currentXi: IntArray
        val stamina: DoubleArray
        val onField: BooleanArray
        val oppPressing: Pressing
        if (side == Side.Home) {
            team = state.home; currentXi = state.homeCurrentXi; stamina = state.homeStamina
            onField = state.homeOnField; oppPressing = state.away.tactics.pressing
        } else {
            team = state.away; currentXi = state.awayCurrentXi; stamina = state.awayStamina
            onField = state.awayOnField; oppPressing = state.home.tactics.pressing
        }

        val effective = ArrayList<Strength.EffectivePlayer>(11)
        for (i in 0 until 11) {
            if (!onField[i]) continue
            val player = team.lookup(currentXi[i]) ?: continue
            val eff = Strength.staminaEffectiveness(stamina[i])
            val (a, m, d) = Strength.rawPlayerStats(
                player.attributes.finishing, player.attributes.technique, player.attributes.pace,
                player.attributes.passing, player.attributes.defending, player.attributes.stamina,
            )
            effective.add(Strength.EffectivePlayer(player.position, a * eff, m * eff, d * eff))
        }
        return Strength.compose(
            effective, team.formation, team.tactics.mentality, Strength.pressingDisrupt(oppPressing),
        )
    }

    // ─── Picking players ─────────────────────────────────────────────────────
    private fun pickIndexByPosition(
        team: Team,
        currentXi: IntArray,
        onField: BooleanArray,
        weights: DoubleArray, // [GK, DEF, MID, FWD]
        rng: MatchRng,
        exclude: Int?,
    ): Int? {
        val ws = DoubleArray(11)
        for (i in 0 until 11) {
            if (!onField[i]) continue
            if (currentXi[i] == exclude) continue
            val player = team.lookup(currentXi[i]) ?: continue
            ws[i] = when (player.position) {
                Position.GK -> weights[0]
                Position.DEF -> weights[1]
                Position.MID -> weights[2]
                Position.FWD -> weights[3]
            }
        }
        if (ws.sum() <= 0.0) return null
        return rng.weightedPick(ws)
    }

    private fun goalkeeper(team: Team, currentXi: IntArray, onField: BooleanArray): Player? {
        for (i in 0 until 11) {
            if (!onField[i]) continue
            val p = team.lookup(currentXi[i]) ?: continue
            if (p.position == Position.GK) return p
        }
        return null
    }

    private fun pickPenaltyTaker(state: MatchState, side: Side): Int? {
        val team: Team; val currentXi: IntArray; val onField: BooleanArray
        if (side == Side.Home) {
            team = state.home; currentXi = state.homeCurrentXi; onField = state.homeOnField
        } else {
            team = state.away; currentXi = state.awayCurrentXi; onField = state.awayOnField
        }
        var bestId: Int? = null
        var bestFinishing = -1
        for (i in 0 until 11) {
            if (!onField[i]) continue
            val p = team.lookup(currentXi[i]) ?: continue
            if (p.position == Position.GK) continue
            if (p.attributes.finishing > bestFinishing) {
                bestFinishing = p.attributes.finishing
                bestId = currentXi[i]
            }
        }
        return bestId
    }

    // ─── Penalty resolution ──────────────────────────────────────────────────
    private fun resolvePenalty(state: MatchState, rng: MatchRng, minute: Int, pen: PendingPenalty) {
        val attTeam: Team; val defTeam: Team; val defXi: IntArray; val defOnField: BooleanArray
        if (pen.side == Side.Home) {
            attTeam = state.home; defTeam = state.away; defXi = state.awayCurrentXi; defOnField = state.awayOnField
        } else {
            attTeam = state.away; defTeam = state.home; defXi = state.homeCurrentXi; defOnField = state.homeOnField
        }
        val taker = attTeam.lookup(pen.taker) ?: return
        val gk = goalkeeper(defTeam, defXi, defOnField)
        val keeperDef = gk?.attributes?.defending?.toDouble() ?: 50.0
        val keeperName = gk?.name ?: "o goleiro"

        val convP = (PENALTY_CONVERSION_BASE + (taker.attributes.finishing - keeperDef) * PENALTY_CONVERSION_SCALE)
            .coerceIn(PENALTY_CONVERSION_MIN, PENALTY_CONVERSION_MAX)

        if (rng.chance(convP)) {
            if (pen.side == Side.Home) state.homeGoals++ else state.awayGoals++
            val ctx = state.ctxFor(pen.side, minute)
            state.events.add(
                MatchEvent(
                    minute, pen.side, MatchEventKind.Goal(pen.taker, null),
                    Narration.penaltyScored(ctx, rng, minute, attTeam.name, taker.name),
                ),
            )
        } else {
            val ctx = state.ctxFor(pen.side, minute)
            state.events.add(
                MatchEvent(
                    minute, pen.side, MatchEventKind.PenaltyMissed(pen.taker),
                    Narration.penaltyMissed(rng, minute, taker.name, keeperName),
                ),
            )
        }
    }

    // ─── Shot resolution ─────────────────────────────────────────────────────
    private fun resolveShot(state: MatchState, rng: MatchRng, minute: Int, side: Side) {
        val attTeam: Team; val defTeam: Team
        val attXi: IntArray; val attOnField: BooleanArray; val defXi: IntArray; val defOnField: BooleanArray
        if (side == Side.Home) {
            attTeam = state.home; defTeam = state.away
            attXi = state.homeCurrentXi; attOnField = state.homeOnField
            defXi = state.awayCurrentXi; defOnField = state.awayOnField
        } else {
            attTeam = state.away; defTeam = state.home
            attXi = state.awayCurrentXi; attOnField = state.awayOnField
            defXi = state.homeCurrentXi; defOnField = state.homeOnField
        }

        val shooterIdx = pickIndexByPosition(
            attTeam, attXi, attOnField, doubleArrayOf(0.05, 1.0, 3.0, 5.0), rng, null,
        ) ?: return
        val shooterId = attXi[shooterIdx]
        val shooter = attTeam.lookup(shooterId) ?: return

        val widthF = Strength.widthShotFactor(attTeam.tactics.width)
        val onTargetP = ((ON_TARGET_BASE + (shooter.attributes.technique - 50.0) * ON_TARGET_TECHNIQUE_SCALE) * widthF)
            .coerceIn(ON_TARGET_MIN, ON_TARGET_MAX)
        val onTarget = rng.chance(onTargetP)

        if (!onTarget) {
            val ctx = state.ctxFor(side, minute)
            if (rng.chance(NEAR_MISS_PROMOTION_RATE)) {
                val kind = when (rng.rangeU32(0, 3)) {
                    0 -> NearMissKind.Post
                    1 -> NearMissKind.Crossbar
                    else -> NearMissKind.JustWide
                }
                state.events.add(
                    MatchEvent(
                        minute, side, MatchEventKind.NearMiss(shooterId, kind),
                        Narration.nearMiss(rng, minute, shooter.name, kind),
                    ),
                )
            } else {
                state.events.add(
                    MatchEvent(
                        minute, side, MatchEventKind.Shot(shooterId, false),
                        Narration.shotWide(rng, minute, shooter.name),
                    ),
                )
            }
            return
        }

        val gk = goalkeeper(defTeam, defXi, defOnField)
        val gkDef = gk?.attributes?.defending?.toDouble() ?: 50.0
        val gkName = gk?.name ?: "o goleiro"
        val goalP = (GOAL_BASE + (shooter.attributes.finishing - gkDef) * GOAL_FINISHING_GK_SCALE)
            .coerceIn(GOAL_MIN, GOAL_MAX)

        if (rng.chance(goalP)) {
            val assist = if (rng.chance(ASSIST_PROB)) {
                pickIndexByPosition(
                    attTeam, attXi, attOnField, doubleArrayOf(0.0, 0.5, 3.0, 2.0), rng, shooterId,
                )?.let { attXi[it] }
            } else {
                null
            }
            if (side == Side.Home) state.homeGoals++ else state.awayGoals++
            val ctx = state.ctxFor(side, minute)
            val assistName = assist?.let { attTeam.lookup(it)?.name }
            state.events.add(
                MatchEvent(
                    minute, side, MatchEventKind.Goal(shooterId, assist),
                    Narration.goal(ctx, rng, minute, attTeam.name, shooter.name, assistName),
                ),
            )
        } else {
            val ctx = state.ctxFor(side, minute)
            state.events.add(
                MatchEvent(
                    minute, side, MatchEventKind.Shot(shooterId, true),
                    Narration.shotSaved(rng, minute, shooter.name, gkName),
                ),
            )
        }
    }

    // ─── Foul resolution ─────────────────────────────────────────────────────
    private fun resolveFoul(state: MatchState, rng: MatchRng, minute: Int, attackerSide: Side) {
        val attTeam: Team; val defTeam: Team
        val attXi: IntArray; val attOnField: BooleanArray; val defXi: IntArray; val defOnField: BooleanArray
        if (attackerSide == Side.Home) {
            attTeam = state.home; defTeam = state.away
            attXi = state.homeCurrentXi; attOnField = state.homeOnField
            defXi = state.awayCurrentXi; defOnField = state.awayOnField
        } else {
            attTeam = state.away; defTeam = state.home
            attXi = state.awayCurrentXi; attOnField = state.awayOnField
            defXi = state.homeCurrentXi; defOnField = state.homeOnField
        }

        val offenderIdx = pickIndexByPosition(
            defTeam, defXi, defOnField, doubleArrayOf(0.1, 3.0, 2.0, 1.0), rng, null,
        )
        val victimIdx = pickIndexByPosition(
            attTeam, attXi, attOnField, doubleArrayOf(0.1, 1.0, 2.0, 3.0), rng, null,
        )
        if (offenderIdx == null || victimIdx == null) return
        val offenderId = defXi[offenderIdx]
        val victimId = attXi[victimIdx]
        val offender = defTeam.lookup(offenderId) ?: return
        val victim = attTeam.lookup(victimId) ?: return

        val defenderSide = attackerSide.flip()
        state.events.add(
            MatchEvent(
                minute, defenderSide, MatchEventKind.Foul(offenderId, victimId),
                Narration.foul(rng, minute, offender.name, victim.name),
            ),
        )

        // A small fraction of fouls are "inside the box" → penalty (replaces the card roll).
        if (rng.chance(PENALTY_FOUL_RATE)) {
            val takerId = pickPenaltyTaker(state, attackerSide)
            if (takerId != null) {
                val takerName = state.teamFor(attackerSide).lookup(takerId)?.name ?: ""
                val penCtx = state.ctxFor(attackerSide, minute)
                state.events.add(
                    MatchEvent(
                        minute, attackerSide, MatchEventKind.PenaltyAwarded(takerId),
                        Narration.penaltyAwarded(penCtx, rng, minute, takerName),
                    ),
                )
                state.pendingPenalty = PendingPenalty(attackerSide, takerId)
                return
            }
            // No eligible taker: fall through to regular card logic.
        }

        val r = rng.unit()
        val pressingBias = if (defTeam.tactics.pressing == Pressing.High) 0.05 else 0.0
        if (r < CARD_NONE - pressingBias) {
            return
        } else if (r < CARD_NONE - pressingBias + CARD_YELLOW) {
            state.events.add(
                MatchEvent(
                    minute, defenderSide, MatchEventKind.YellowCard(offenderId),
                    Narration.yellow(rng, minute, offender.name),
                ),
            )
        } else if (r < CARD_NONE - pressingBias + CARD_YELLOW + CARD_RED + pressingBias) {
            state.events.add(
                MatchEvent(
                    minute, defenderSide, MatchEventKind.RedCard(offenderId),
                    Narration.red(rng, minute, offender.name),
                ),
            )
            if (defenderSide == Side.Home) state.homeOnField[offenderIdx] = false else state.awayOnField[offenderIdx] = false
        }
    }

    // ─── Manager (substitutions), run after each minute ──────────────────────
    private fun runManagers(state: MatchState, rng: MatchRng, minute: Int) {
        for (side in arrayOf(Side.Home, Side.Away)) {
            val team = state.teamFor(side)
            val cfg = managerConfigFor(team.id)
            val offAndOn = decideSub(state, side, cfg, minute) ?: continue
            applySubstitution(state, rng, side, offAndOn.first, offAndOn.second, minute)
        }
    }

    /** Returns (offSlot, onBenchIdx) or null. Pure heuristic; first rule wins. */
    private fun decideSub(state: MatchState, side: Side, cfg: ManagerConfig, minute: Int): Pair<Int, Int>? {
        val team: Team; val currentXi: IntArray; val onField: BooleanArray; val stamina: DoubleArray
        val benchUsed: BooleanArray; val subsUsed: Int; val ourGoals: Int; val theirGoals: Int
        if (side == Side.Home) {
            team = state.home; currentXi = state.homeCurrentXi; onField = state.homeOnField
            stamina = state.homeStamina; benchUsed = state.homeBenchUsed; subsUsed = state.homeSubsUsed
            ourGoals = state.homeGoals; theirGoals = state.awayGoals
        } else {
            team = state.away; currentXi = state.awayCurrentXi; onField = state.awayOnField
            stamina = state.awayStamina; benchUsed = state.awayBenchUsed; subsUsed = state.awaySubsUsed
            ourGoals = state.awayGoals; theirGoals = state.homeGoals
        }

        if (subsUsed >= cfg.maxSubsPerMatch) return null

        // Rule 1: GK red-carded — bring on a bench GK (sacrifice a FWD, else MID).
        gkEmergency(team, currentXi, onField, benchUsed)?.let { return it }

        // Rule 2: stamina swap.
        if (minute >= cfg.staminaRuleMinMinute) {
            staminaSwap(team, currentXi, onField, stamina, benchUsed, cfg)?.let { return it }
        }

        // Rule 3: game-state reactions.
        if (minute >= cfg.gameStateRuleMinMinute) {
            val diff = ourGoals - theirGoals
            if (diff < 0) {
                chaseWithFreshFwd(team, currentXi, onField, stamina, benchUsed)?.let { return it }
            } else if (diff > 0) {
                lockInWithDef(team, currentXi, onField, benchUsed)?.let { return it }
            }
        }
        return null
    }

    private fun gkEmergency(
        team: Team, currentXi: IntArray, onField: BooleanArray, benchUsed: BooleanArray,
    ): Pair<Int, Int>? {
        val gkSlot = (0 until 11).firstOrNull { team.lookup(currentXi[it])?.position == Position.GK } ?: return null
        if (onField[gkSlot]) return null
        val benchIdx = findBench(team, benchUsed, Position.GK, 0.0) ?: return null
        val offSlot = findOnField(team, currentXi, onField, Position.FWD)
            ?: findOnField(team, currentXi, onField, Position.MID) ?: return null
        return offSlot to benchIdx
    }

    private fun staminaSwap(
        team: Team, currentXi: IntArray, onField: BooleanArray, stamina: DoubleArray,
        benchUsed: BooleanArray, cfg: ManagerConfig,
    ): Pair<Int, Int>? {
        for (slot in 0 until 11) {
            if (!onField[slot]) continue
            if (stamina[slot] >= cfg.staminaSubThreshold) continue
            val player = team.lookup(currentXi[slot]) ?: continue
            if (player.position == Position.GK) continue
            val benchIdx = findBench(team, benchUsed, player.position, cfg.staminaFreshThreshold)
            if (benchIdx != null) return slot to benchIdx
        }
        return null
    }

    private fun chaseWithFreshFwd(
        team: Team, currentXi: IntArray, onField: BooleanArray, stamina: DoubleArray, benchUsed: BooleanArray,
    ): Pair<Int, Int>? {
        val offSlot = (0 until 11)
            .filter { onField[it] && team.lookup(currentXi[it])?.position == Position.FWD }
            .minByOrNull { stamina[it] } ?: return null
        val benchIdx = findBench(team, benchUsed, Position.FWD, 0.0) ?: return null
        return offSlot to benchIdx
    }

    private fun lockInWithDef(
        team: Team, currentXi: IntArray, onField: BooleanArray, benchUsed: BooleanArray,
    ): Pair<Int, Int>? {
        val offSlot = findOnField(team, currentXi, onField, Position.FWD) ?: return null
        val benchIdx = findBench(team, benchUsed, Position.DEF, 0.0) ?: return null
        return offSlot to benchIdx
    }

    private fun findBench(team: Team, benchUsed: BooleanArray, pos: Position, minStamina: Double): Int? {
        for (i in team.bench.indices) {
            if (benchUsed[i]) continue
            val p = team.lookup(team.bench[i]) ?: continue
            if (p.position != pos) continue
            if (p.attributes.stamina < minStamina) continue
            return i
        }
        return null
    }

    private fun findOnField(team: Team, currentXi: IntArray, onField: BooleanArray, pos: Position): Int? =
        (0 until 11).firstOrNull { onField[it] && team.lookup(currentXi[it])?.position == pos }

    private fun applySubstitution(
        state: MatchState, rng: MatchRng, side: Side, offSlot: Int, onBenchIdx: Int, minute: Int,
    ) {
        val team = state.teamFor(side)
        val currentXi = if (side == Side.Home) state.homeCurrentXi else state.awayCurrentXi
        val onField = if (side == Side.Home) state.homeOnField else state.awayOnField
        val stamina = if (side == Side.Home) state.homeStamina else state.awayStamina
        val benchUsed = if (side == Side.Home) state.homeBenchUsed else state.awayBenchUsed

        val offId = currentXi[offSlot]
        val onId = team.bench[onBenchIdx]
        val onStamina = team.lookup(onId)?.attributes?.stamina?.toDouble() ?: return
        val offName = team.lookup(offId)?.name ?: return
        val onName = team.lookup(onId)?.name ?: return

        currentXi[offSlot] = onId
        onField[offSlot] = true
        stamina[offSlot] = onStamina
        benchUsed[onBenchIdx] = true
        if (side == Side.Home) state.homeSubsUsed++ else state.awaySubsUsed++

        state.events.add(
            MatchEvent(
                minute, side, MatchEventKind.Substitution(offId, onId),
                Narration.substitution(rng, minute, team.name, offName, onName),
            ),
        )
    }
}
