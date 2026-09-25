package com.ganim.killersudoku.engine.solver

import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.Geometry

/** Candidate masks for all 81 cells. Mutated in place by techniques. */
class CandidateGrid(val masks: IntArray = IntArray(Geometry.CELLS) { Digits.ALL }) {

    fun copy(): CandidateGrid = CandidateGrid(masks.copyOf())

    fun isSolved(cell: Int): Boolean = Digits.isSingle(masks[cell])

    fun value(cell: Int): Int = if (isSolved(cell)) Digits.single(masks[cell]) else 0

    val isComplete: Boolean get() = masks.all { Digits.isSingle(it) }

    val hasContradiction: Boolean get() = masks.any { it == 0 }

    /** Removes [bits] from [cell]. Returns true if anything changed. */
    fun eliminate(cell: Int, bits: Int): Boolean {
        val old = masks[cell]
        val new = old and bits.inv()
        if (new == old) return false
        masks[cell] = new
        return true
    }

    /** Keeps only [allowed] in [cell]. Returns true if anything changed. */
    fun restrict(cell: Int, allowed: Int): Boolean = eliminate(cell, allowed.inv() and Digits.ALL)

    fun values(): IntArray = IntArray(Geometry.CELLS) { value(it) }
}
