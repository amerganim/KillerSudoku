package com.ganim.killersudoku.engine.generator

import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.solver.CandidateGrid
import com.ganim.killersudoku.engine.solver.PuzzleIndex
import com.ganim.killersudoku.engine.solver.SolveResult
import com.ganim.killersudoku.engine.solver.Solver
import kotlin.random.Random

/** An accepted puzzle plus the solver's rating signals. */
class Rated(val puzzle: KillerPuzzle, val maxTier: Int, val depth: Int)

/**
 * generate -> solve -> accept only Unique -> rate (build plan 5).
 * Soundness of every technique means a logical Unique is a proof of uniqueness; the
 * backtracking verifier in the tests checks that claim independently.
 */
class KillerGenerator(private val solver: Solver = Solver()) {

    /** One attempt. Null when the partition or the solve is rejected. */
    fun attempt(rng: Random, shape: CageShape, id: Int = 0): Rated? {
        val solution = SolutionGenerator.generate(rng)
        var layout = CagePartitioner.partition(solution, shape, rng) ?: return null
        repeat(MAX_REPAIRS) {
            val draft = KillerPuzzle.fromLayout(id, solution, layout, Difficulty.EASY)
            val index = PuzzleIndex(draft)
            val grid = CandidateGrid()
            when (val result = solver.solve(index, grid)) {
                is SolveResult.Unique -> {
                    check(result.solution.contentEquals(solution)) { "solver disagreed with the generating solution" }
                    val difficulty = Difficulty.forTier(maxOf(1, result.maxTier))
                    return Rated(KillerPuzzle(id, draft.cages, solution, difficulty), result.maxTier, result.depth)
                }
                SolveResult.Contradiction -> error("sums derived from a valid solution cannot contradict")
                SolveResult.Ambiguous -> {
                    val open = (0 until Geometry.CELLS).filter { !grid.isSolved(it) }
                    layout = CageRepair.repair(layout, solution, open, shape, rng) ?: return null
                }
            }
        }
        return null
    }

    /** Keeps attempting until a puzzle of [difficulty] comes out. */
    fun generate(rng: Random, difficulty: Difficulty, id: Int = 0, maxAttempts: Int = 10_000): Rated? {
        repeat(maxAttempts) {
            val rated = attempt(rng, shapeFor(difficulty), id) ?: return@repeat
            if (rated.puzzle.difficulty == difficulty) return rated
        }
        return null
    }

    companion object {
        /** Reshapes allowed before the attempt is abandoned. */
        const val MAX_REPAIRS = 40

        /** Provisional; calibrated by the pack tool's distribution report. */
        fun shapeFor(difficulty: Difficulty): CageShape = when (difficulty) {
            Difficulty.EASY -> CageShape(intArrayOf(0, 45, 35, 15, 5, 0), maxSingletons = 2)
            Difficulty.MEDIUM -> CageShape(intArrayOf(0, 30, 35, 25, 10, 0), maxSingletons = 1)
            Difficulty.HARD -> CageShape(intArrayOf(0, 20, 35, 30, 15, 0), maxSingletons = 1)
            Difficulty.EXPERT -> CageShape(intArrayOf(0, 15, 30, 35, 15, 5), maxSingletons = 0)
        }
    }
}
