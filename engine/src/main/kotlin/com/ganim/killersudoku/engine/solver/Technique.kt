package com.ganim.killersudoku.engine.solver

/**
 * One deduction rule. [apply] makes at most one "unit" of progress (one cell, house or
 * cage) and describes it, so the same code drives the solver and player-facing hints.
 */
interface Technique {
    val name: String

    /** Difficulty weight: 1 EASY .. 4 EXPERT (build plan 4). */
    val tier: Int

    /** Applies once, mutating [grid]. Returns null when nothing changed. */
    fun apply(index: PuzzleIndex, grid: CandidateGrid): Step?
}

/** A single productive application of a technique. [cells] are the cells it changed. */
class Step(val technique: Technique, val description: String, val cells: IntArray) {
    override fun toString(): String = "${technique.name}: $description"
}
