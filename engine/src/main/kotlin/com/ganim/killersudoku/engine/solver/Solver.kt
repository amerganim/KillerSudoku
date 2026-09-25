package com.ganim.killersudoku.engine.solver

import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle

sealed interface SolveResult {
    /** Solved by logic alone. [maxTier] is the hardest technique genuinely required. */
    data class Unique(val depth: Int, val maxTier: Int, val solution: IntArray) : SolveResult

    /** Stalled with cells unresolved. */
    data object Ambiguous : SolveResult

    /** Some cell ran out of candidates. */
    data object Contradiction : SolveResult
}

/**
 * Solve-to-fixpoint with a restart from the cheapest technique after every change
 * (puzzle-engine-core.md 4), so `maxTier` means "hardest technique actually needed".
 * No guessing and no backtracking - that lives in test sources only.
 */
class Solver(private val techniques: List<Technique> = Techniques.standard) {

    fun solve(puzzle: KillerPuzzle, maxTier: Int = 4): SolveResult =
        solve(PuzzleIndex(puzzle), CandidateGrid(), maxTier)

    fun solve(index: PuzzleIndex, grid: CandidateGrid, maxTier: Int = 4): SolveResult {
        var depth = 0
        var hardest = 0
        while (true) {
            if (grid.hasContradiction) return SolveResult.Contradiction
            if (grid.isComplete) {
                val values = grid.values()
                return if (isValidSolution(index, values)) SolveResult.Unique(depth, hardest, values)
                else SolveResult.Contradiction
            }
            val step = nextStep(index, grid, maxTier) ?: return SolveResult.Ambiguous
            hardest = maxOf(hardest, step.technique.tier)
            depth++
        }
    }

    /** Applies the cheapest productive technique once. Null when stalled. */
    fun nextStep(index: PuzzleIndex, grid: CandidateGrid, maxTier: Int = 4): Step? {
        for (t in techniques) {
            if (t.tier > maxTier) continue
            t.apply(index, grid)?.let { return it }
        }
        return null
    }

    companion object {
        fun isValidSolution(index: PuzzleIndex, values: IntArray): Boolean {
            if (values.size != Geometry.CELLS || values.any { it !in 1..9 }) return false
            for (house in Geometry.houses) {
                if (house.fold(0) { a, c -> a or (1 shl values[c]) } != 0x3FE) return false
            }
            for (cage in index.cages) {
                val mask = cage.cells.fold(0) { a, c -> a or (1 shl values[c]) }
                if (Integer.bitCount(mask) != cage.size || cage.cells.sumOf { values[it] } != cage.sum) return false
            }
            return true
        }
    }
}
