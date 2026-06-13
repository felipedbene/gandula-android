package dev.debene.gandula.career

/**
 * Mulberry32 — 32-bit deterministic PRNG, ported from `web/src/util/prng.ts`.
 * Shared by the transfer market, regen, and rival-coach evolution so all the
 * season-to-season randomness stays reproducible. Int arithmetic wraps to 32
 * bits exactly like JS `| 0` / `Math.imul`.
 */
internal class Mulberry32(seed: Int) {
    private var state = seed
    fun next(): Double {
        state += 0x6d2b79f5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}
