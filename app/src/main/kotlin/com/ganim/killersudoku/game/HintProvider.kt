package com.ganim.killersudoku.game

import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.solver.CandidateGrid
import com.ganim.killersudoku.engine.solver.HiddenSingle
import com.ganim.killersudoku.engine.solver.NakedSingle
import com.ganim.killersudoku.engine.solver.PuzzleIndex
import com.ganim.killersudoku.engine.solver.Solver

/** One revealed cell and the reasoning that finds it. */
class Hint(val cell: Int, val digit: Int, val technique: String, val explanation: String)

/**
 * Runs the real solver from the player's current board and stops at the first empty cell
 * it can prove, naming the technique that proved it (build plan 6). Only correct digits
 * are trusted; wrong ones are reported first.
 */
class HintProvider(private val puzzle: KillerPuzzle) {
    private val index = PuzzleIndex(puzzle)
    private val solver = Solver()

    fun wrongCell(values: IntArray): Int? =
        values.indices.firstOrNull { values[it] != 0 && values[it] != puzzle.solution[it] }

    fun next(values: IntArray): Hint? {
        val grid = CandidateGrid()
        for (c in 0 until Geometry.CELLS) {
            if (values[c] != 0 && values[c] == puzzle.solution[c]) grid.masks[c] = Digits.bit(values[c])
        }
        val open = (0 until Geometry.CELLS).filter { values[it] == 0 }
        while (true) {
            val step = solver.nextStep(index, grid) ?: return null
            if (grid.hasContradiction) return null
            val cell = open.firstOrNull { grid.isSolved(it) } ?: continue
            val digit = grid.value(cell)
            val where = Geometry.cellName(cell)
            val explanation = when {
                step.technique === NakedSingle -> "$digit is the only digit left that fits $where."
                // The step placed this very cell and already said the digit (a hidden single,
                // a single-cell cage, a one-cell rule-of-45 remainder): "that leaves only 7
                // for R3C3" would just repeat it. A multi-cell sum can never end in the lone
                // digit, because two or more digits add up to more than any one of them.
                step.cells.size == 1 && step.cells[0] == cell &&
                    (step.technique === HiddenSingle || step.description.endsWith(" $digit")) -> "${step.description}."
                else -> "${step.description}. That leaves only $digit for $where."
            }
            return Hint(cell, digit, step.technique.name, explanation)
        }
    }
}
