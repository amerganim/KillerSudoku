package com.ganim.killersudoku.engine

import com.ganim.killersudoku.engine.generator.CagePartitioner
import com.ganim.killersudoku.engine.generator.KillerGenerator
import com.ganim.killersudoku.engine.generator.SolutionGenerator
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.solver.CandidateGrid
import com.ganim.killersudoku.engine.solver.PuzzleIndex
import com.ganim.killersudoku.engine.solver.Solver
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Calibration aid: logical outcome vs true solution count. Run with -Pexplore. */
@Tag("explore")
class ExploreTest {
    @Test fun explore() {
        val solver = Solver()
        for (d in Difficulty.entries) {
            val stats = HashMap<String, Int>()
            repeat(10) { a ->
                val rng = Random(d.ordinal * 100_000L + a)
                val sol = SolutionGenerator.generate(rng)
                val layout = CagePartitioner.partition(sol, KillerGenerator.shapeFor(d), rng) ?: return@repeat
                val p = KillerPuzzle.fromLayout(0, sol, layout, Difficulty.EASY)
                val index = PuzzleIndex(p)
                val grid = CandidateGrid()
                val r = solver.solve(index, grid)
                val unsolved = (0 until 81).count { !grid.isSolved(it) }
                val t0 = System.nanoTime()
                val count = BacktrackingVerifier.countSolutions(p, 2, nodeBudget = 3_000_000)
                println("  $d #$a ${r::class.simpleName} open=$unsolved truth=$count ${(System.nanoTime() - t0) / 1_000_000}ms")
                val key = "${r::class.simpleName} truth=$count"
                stats.merge(key, 1, Int::plus)
                if (count == 1 && r !is com.ganim.killersudoku.engine.solver.SolveResult.Unique) {
                    println("  $d #$a unique but stalled with $unsolved open cells, ${p.cages.size} cages")
                }
            }
            println("$d: $stats")
        }
    }
}
