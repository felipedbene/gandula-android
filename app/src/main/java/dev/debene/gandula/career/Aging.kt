package dev.debene.gandula.career

import dev.debene.gandula.domain.Attributes
import dev.debene.gandula.domain.Player
import kotlin.math.max
import kotlin.math.min

/**
 * Player aging — port of `web/src/util/aging.ts`. Deterministic, no RNG; applied
 * once per season. The match engine ignores age, so the effect comes from
 * drifting the six attributes along an age curve (young develop, prime plateaus,
 * veterans decline).
 */
object Aging {
    const val MAX_AGE = 50 // engine validates age 15..50
    const val GROWTH_CAP = 90
    const val DECLINE_FLOOR = 25

    fun ageDelta(age: Int): Int = when {
        age < 23 -> 1
        age <= 30 -> 0
        age <= 33 -> -1
        age <= 36 -> -2
        else -> -3
    }

    private fun applyDelta(value: Int, delta: Int): Int = when {
        delta > 0 -> if (value >= GROWTH_CAP) value else min(GROWTH_CAP, value + delta)
        delta < 0 -> if (value <= DECLINE_FLOOR) value else max(DECLINE_FLOOR, value + delta)
        else -> value
    }

    fun agePlayer(p: Player): Player {
        val age = min(MAX_AGE, p.age + 1)
        val d = ageDelta(age)
        val a = p.attributes
        return p.copy(
            age = age,
            attributes = Attributes(
                pace = applyDelta(a.pace, d),
                technique = applyDelta(a.technique, d),
                passing = applyDelta(a.passing, d),
                defending = applyDelta(a.defending, d),
                finishing = applyDelta(a.finishing, d),
                stamina = applyDelta(a.stamina, d),
            ),
        )
    }

    fun ageRoster(roster: List<Player>): List<Player> = roster.map(::agePlayer)
}
