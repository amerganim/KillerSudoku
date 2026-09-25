package com.ganim.killersudoku.engine.combos

import com.ganim.killersudoku.engine.model.Digits

/**
 * Every non-empty subset of 1..9 (exactly 511), indexed by size and sum.
 * Shared with Kakuro later (puzzle-engine-core.md 6). Masks use bits 1..9.
 */
object DigitCombos {
    private const val MAX_SUM = 45

    /** [size][sum] -> masks. */
    private val table: Array<Array<IntArray>>

    val subsetCount: Int

    init {
        val lists = Array(10) { Array(MAX_SUM + 1) { mutableListOf<Int>() } }
        var n = 0
        for (subset in 1 until (1 shl 9)) {
            val mask = subset shl 1
            lists[Digits.count(mask)][Digits.sum(mask)].add(mask)
            n++
        }
        table = Array(10) { size -> Array(MAX_SUM + 1) { sum -> lists[size][sum].toIntArray() } }
        subsetCount = n
    }

    /** All masks of [size] distinct digits summing to [sum]; empty when out of range. */
    fun masksFor(size: Int, sum: Int): IntArray =
        if (size !in 1..9 || sum !in 0..MAX_SUM) IntArray(0) else table[size][sum]

    /** Union of all valid combinations - every digit that could appear. */
    fun possibleDigits(size: Int, sum: Int): Int = masksFor(size, sum).fold(0) { acc, m -> acc or m }

    /** Intersection - digits present in every valid combination. 0 when none exist. */
    fun requiredDigits(size: Int, sum: Int): Int {
        val masks = masksFor(size, sum)
        return if (masks.isEmpty()) 0 else masks.fold(Digits.ALL) { acc, m -> acc and m }
    }
}
