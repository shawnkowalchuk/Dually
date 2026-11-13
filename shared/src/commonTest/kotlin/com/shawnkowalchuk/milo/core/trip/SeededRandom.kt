package com.shawnkowalchuk.milo.core.trip

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The random numbers of the randomized tests: `java.util.Random`'s own algorithm, written out so
 * that it runs on iOS too (the trip rules' tests moved to the shared module on 2026-11-12,
 * ADR-014). Java's generator is specified to the bit (a 48-bit linear congruential generator,
 * Knuth, TAOCP 3.2.1; the polar method for [nextGaussian]), so each seed gives exactly the
 * numbers it gave when these tests were written against `java.util.Random`, on every platform.
 * Kotlin's own `Random` would give other numbers, and the tests' clouds were tuned to these.
 */
class SeededRandom(seed: Long) {
    private var state = (seed xor MULTIPLIER) and MASK
    private var nextNextGaussian = 0.0
    private var haveNextNextGaussian = false

    private fun next(bits: Int): Int {
        state = (state * MULTIPLIER + ADDEND) and MASK
        return (state ushr (48 - bits)).toInt()
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        if (bound and -bound == bound) return ((bound.toLong() * next(31).toLong()) shr 31).toInt()
        var bits: Int
        var value: Int
        do {
            bits = next(31)
            value = bits % bound
        } while (bits - value + (bound - 1) < 0)
        return value
    }

    fun nextDouble(): Double = (((next(26).toLong()) shl 27) + next(27)) * DOUBLE_UNIT

    fun nextGaussian(): Double {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false
            return nextNextGaussian
        }
        var v1: Double
        var v2: Double
        var s: Double
        do {
            v1 = 2 * nextDouble() - 1
            v2 = 2 * nextDouble() - 1
            s = v1 * v1 + v2 * v2
        } while (s >= 1 || s == 0.0)
        val multiplier = sqrt(-2 * ln(s) / s)
        nextNextGaussian = v2 * multiplier
        haveNextNextGaussian = true
        return v1 * multiplier
    }

    private companion object {
        const val MULTIPLIER = 0x5DEECE66DL
        const val ADDEND = 0xBL
        const val MASK = (1L shl 48) - 1
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
    }
}
