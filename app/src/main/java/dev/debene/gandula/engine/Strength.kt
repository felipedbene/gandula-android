package dev.debene.gandula.engine

import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width

/**
 * Team strength composition and tactical modifiers. Port of upstream
 * `core/src/engine/strength.rs`. All magic numbers stay here so the engine can
 * be tuned from one place — see ARCHITECTURE.md for the formulas.
 */
object Strength {
    // Per-player attribute blending into a "raw" stat.
    const val ATTACK_W_FINISHING = 0.5
    const val ATTACK_W_TECHNIQUE = 0.3
    const val ATTACK_W_PACE = 0.2

    const val MID_W_PASSING = 0.5
    const val MID_W_TECHNIQUE = 0.3
    const val MID_W_STAMINA = 0.2

    const val DEF_W_DEFENDING = 0.5
    const val DEF_W_PACE = 0.2
    const val DEF_W_STAMINA = 0.3

    fun posWeightAttack(p: Position): Double = when (p) {
        Position.GK -> 0.0
        Position.DEF -> 0.1
        Position.MID -> 0.3
        Position.FWD -> 0.6
    }

    fun posWeightMid(p: Position): Double = when (p) {
        Position.GK -> 0.0
        Position.DEF -> 0.2
        Position.MID -> 0.6
        Position.FWD -> 0.2
    }

    fun posWeightDefense(p: Position): Double = when (p) {
        Position.GK -> 0.1
        Position.DEF -> 0.6
        Position.MID -> 0.3
        Position.FWD -> 0.0
    }

    // Stamina -> effectiveness. Fresh (99) = 100%, depleted (0) = 70%, linear.
    const val STAMINA_MIN_EFF = 0.7
    const val STAMINA_RANGE_EFF = 0.3

    fun staminaEffectiveness(stamina: Double): Double =
        STAMINA_MIN_EFF + STAMINA_RANGE_EFF * (stamina.coerceIn(0.0, 99.0) / 99.0)

    // Formation modifier: (attack, midfield, defense) deltas.
    fun formationMod(f: Formation): Triple<Double, Double, Double> = when (f) {
        Formation.F442 -> Triple(0.0, 0.0, 0.0)
        Formation.F433 -> Triple(5.0, -2.0, -5.0)
        Formation.F352 -> Triple(-2.0, 5.0, -3.0)
        Formation.F4231 -> Triple(3.0, 3.0, -3.0)
    }

    // Mentality: (attack, defense) deltas.
    fun mentalityMod(m: Mentality): Pair<Double, Double> = when (m) {
        Mentality.VeryDefensive -> Pair(-10.0, 10.0)
        Mentality.Defensive -> Pair(-5.0, 5.0)
        Mentality.Balanced -> Pair(0.0, 0.0)
        Mentality.Attacking -> Pair(5.0, -5.0)
        Mentality.VeryAttacking -> Pair(10.0, -10.0)
    }

    fun tempoEventFactor(t: Tempo): Double = when (t) {
        Tempo.Slow -> 0.85
        Tempo.Normal -> 1.0
        Tempo.Fast -> 1.15
    }

    fun tempoStaminaFactor(t: Tempo): Double = when (t) {
        Tempo.Slow -> 0.85
        Tempo.Normal -> 1.0
        Tempo.Fast -> 1.25
    }

    fun pressingDisrupt(p: Pressing): Double = when (p) {
        Pressing.Low -> 0.0
        Pressing.Medium -> 3.0
        Pressing.High -> 6.0
    }

    fun pressingStaminaFactor(p: Pressing): Double = when (p) {
        Pressing.Low -> 0.85
        Pressing.Medium -> 1.0
        Pressing.High -> 1.25
    }

    fun pressingFoulFactor(p: Pressing): Double = when (p) {
        Pressing.Low -> 0.8
        Pressing.Medium -> 1.0
        Pressing.High -> 1.3
    }

    fun widthShotFactor(w: Width): Double = when (w) {
        Width.Narrow -> 0.97
        Width.Normal -> 1.0
        Width.Wide -> 1.03
    }

    // Per-minute drive probabilities — single source of truth shared by the tick.
    const val BASE_EVENT_RATE = 0.18
    const val POSSESSION_MID_SCALE = 0.005
    const val POSSESSION_MIN = 0.10
    const val POSSESSION_MAX = 0.90

    const val SHOT_BASE_WITHIN_EVENT = 0.70
    const val SHOT_ATTACK_DEFENSE_SCALE = 1.0 / 200.0
    const val SHOT_PROB_MIN = 0.20
    const val SHOT_PROB_MAX = 0.95

    fun possessionHome(home: TeamStrength, away: TeamStrength): Double =
        (0.5 + POSSESSION_MID_SCALE * (home.midfield - away.midfield))
            .coerceIn(POSSESSION_MIN, POSSESSION_MAX)

    fun eventProb(tempo: Tempo): Double = BASE_EVENT_RATE * tempoEventFactor(tempo)

    fun shotProb(attacker: TeamStrength, defender: TeamStrength): Double =
        (SHOT_BASE_WITHIN_EVENT *
            (1.0 + (attacker.attack - defender.defense) * SHOT_ATTACK_DEFENSE_SCALE))
            .coerceIn(SHOT_PROB_MIN, SHOT_PROB_MAX)

    /** Per-player (attack, mid, defense) raw scores from base attributes. */
    fun rawPlayerStats(
        finishing: Int,
        technique: Int,
        pace: Int,
        passing: Int,
        defending: Int,
        stamina: Int,
    ): Triple<Double, Double, Double> {
        val attack = ATTACK_W_FINISHING * finishing +
            ATTACK_W_TECHNIQUE * technique +
            ATTACK_W_PACE * pace
        val mid = MID_W_PASSING * passing +
            MID_W_TECHNIQUE * technique +
            MID_W_STAMINA * stamina
        val def = DEF_W_DEFENDING * defending +
            DEF_W_PACE * pace +
            DEF_W_STAMINA * stamina
        return Triple(attack, mid, def)
    }

    /** A single on-field player's stamina-scaled contribution. */
    data class EffectivePlayer(
        val position: Position,
        val attack: Double,
        val mid: Double,
        val def: Double,
    )

    /** Compose team strength from stamina-scaled per-player stats + tactics. */
    fun compose(
        effective: List<EffectivePlayer>,
        formation: Formation,
        mentality: Mentality,
        pressingDisruptOnOpponent: Double,
    ): TeamStrength {
        var aNum = 0.0; var aDen = 0.0
        var mNum = 0.0; var mDen = 0.0
        var dNum = 0.0; var dDen = 0.0

        for (p in effective) {
            val wa = posWeightAttack(p.position)
            val wm = posWeightMid(p.position)
            val wd = posWeightDefense(p.position)
            aNum += wa * p.attack; aDen += wa
            mNum += wm * p.mid; mDen += wm
            dNum += wd * p.def; dDen += wd
        }

        val (fa, fm, fd) = formationMod(formation)
        val (ma, md) = mentalityMod(mentality)

        val attack = (aNum / maxOf(aDen, 1e-6)) + fa + ma
        val midfield = (mNum / maxOf(mDen, 1e-6)) + fm - pressingDisruptOnOpponent
        val defense = (dNum / maxOf(dDen, 1e-6)) + fd + md

        return TeamStrength(attack, midfield, defense)
    }
}

data class TeamStrength(val attack: Double, val midfield: Double, val defense: Double)
