package com.ganim.killersudoku.engine.generator

import kotlin.random.Random

/**
 * A random complete sudoku grid: a canonical pattern put through structure-preserving
 * transforms (build plan 5 step 1). Much faster than backtracking from empty.
 */
object SolutionGenerator {

    fun generate(rng: Random): IntArray {
        val digits = (1..9).shuffled(rng)
        val rows = permutedLines(rng)
        val cols = permutedLines(rng)
        val transpose = rng.nextBoolean()
        return IntArray(81) { i ->
            var r = rows[i / 9]
            var c = cols[i % 9]
            if (transpose) r = c.also { c = r }
            digits[(r * 3 + r / 3 + c) % 9]
        }
    }

    /** Shuffles bands (stacks) and the lines within each. */
    private fun permutedLines(rng: Random): IntArray {
        val bands = (0..2).shuffled(rng)
        return bands.flatMap { b -> (0..2).shuffled(rng).map { b * 3 + it } }.toIntArray()
    }
}
