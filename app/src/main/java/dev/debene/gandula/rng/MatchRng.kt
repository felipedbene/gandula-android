package dev.debene.gandula.rng

/**
 * Single source of randomness for one match. Port of the upstream Rust
 * `MatchRng`, which wrapped `ChaCha8Rng`. Determinism contract is "same seed +
 * same inputs -> identical match", NOT byte-for-byte parity with the Rust/WASM
 * build — so this uses a self-contained xoshiro256** generator (seeded through
 * SplitMix64) rather than reproducing ChaCha8. Nothing else in the engine reads
 * entropy: this is the only source.
 *
 * The public API (`unit`, `rangeU32`, `chance`, `weightedPick`) mirrors the Rust
 * one exactly so the tick loop ports line-for-line.
 */
class MatchRng(seed: Long) {
    private var s0: Long
    private var s1: Long
    private var s2: Long
    private var s3: Long

    init {
        // SplitMix64 expands the u64 seed into the four 64-bit lanes xoshiro needs.
        var z = seed
        fun nextSeed(): Long {
            z += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
            var x = z
            x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return x xor (x ushr 31)
        }
        s0 = nextSeed()
        s1 = nextSeed()
        s2 = nextSeed()
        s3 = nextSeed()
    }

    private fun rotl(x: Long, k: Int): Long = (x shl k) or (x ushr (64 - k))

    private fun nextLong(): Long {
        val result = rotl(s1 * 5, 7) * 9
        val t = s1 shl 17
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = rotl(s3, 45)
        return result
    }

    /** Uniform f64 in [0, 1) — top 53 bits as a mantissa, matching the Rust port. */
    fun unit(): Double {
        val bits = nextLong() ushr 11
        return bits.toDouble() * (1.0 / 9007199254740992.0) // 1 / 2^53
    }

    /** Uniform integer in [low, high). */
    fun rangeU32(low: Int, high: Int): Int {
        require(high > low)
        val span = (high - low).toULong()
        return low + (nextLong().toULong() % span).toInt()
    }

    fun chance(p: Double): Boolean = unit() < p

    /**
     * Pick an index in [0, weights.size) proportional to weights. Weights must be
     * non-negative with at least one > 0.
     */
    fun weightedPick(weights: DoubleArray): Int {
        val total = weights.sum()
        var r = unit() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0.0) return i
        }
        return weights.size - 1
    }
}
