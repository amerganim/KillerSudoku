package com.ganim.killersudoku.engine.model

enum class Difficulty(val maxTier: Int) {
    EASY(1),
    MEDIUM(2),
    HARD(3),
    EXPERT(4);

    companion object {
        /**
         * Rating is the hardest technique tier the solver genuinely needed (build plan 4).
         * Thresholds are provisional until calibrated against real play in Phase 1.
         */
        fun forTier(tier: Int): Difficulty = entries.first { tier <= it.maxTier }
    }
}

/** A cage: distinct digits in [cells] summing to [sum]. */
class Cage(val cells: IntArray, val sum: Int) {
    val size: Int get() = cells.size

    /** Top-left cell - where the sum label is drawn. */
    val anchor: Int get() = cells.minOrNull() ?: error("empty cage")

    override fun equals(other: Any?): Boolean =
        other is Cage && sum == other.sum && cells.contentEquals(other.cells)

    override fun hashCode(): Int = 31 * cells.contentHashCode() + sum

    override fun toString(): String = "Cage(${cells.joinToString(",") { Geometry.cellName(it) }}=$sum)"
}

/**
 * Zero givens: the cages carry all the information (build plan 2).
 * [solution] is stored, not derived, for fast mistake checking.
 */
class KillerPuzzle(
    val id: Int,
    val cages: List<Cage>,
    val solution: IntArray,
    val difficulty: Difficulty,
) {
    /** Cell -> index into [cages]. */
    val cageOf: IntArray = IntArray(Geometry.CELLS) { -1 }.also { map ->
        cages.forEachIndexed { i, cage -> cage.cells.forEach { map[it] = i } }
    }

    init {
        require(solution.size == Geometry.CELLS) { "solution must have 81 digits" }
        require(cageOf.none { it < 0 }) { "cages must cover every cell" }
        require(cages.sumOf { it.size } == Geometry.CELLS) { "cages must not overlap" }
    }

    override fun equals(other: Any?): Boolean =
        other is KillerPuzzle && id == other.id && difficulty == other.difficulty &&
            cages == other.cages && solution.contentEquals(other.solution)

    override fun hashCode(): Int = 31 * cages.hashCode() + solution.contentHashCode()

    companion object {
        /** Builds a puzzle from a solution and a cell->cage assignment, deriving sums. */
        fun fromLayout(id: Int, solution: IntArray, cageOf: IntArray, difficulty: Difficulty): KillerPuzzle {
            val count = cageOf.max() + 1
            val cages = (0 until count).map { k ->
                val cells = (0 until Geometry.CELLS).filter { cageOf[it] == k }.toIntArray()
                Cage(cells, cells.sumOf { solution[it] })
            }
            return KillerPuzzle(id, cages, solution, difficulty)
        }
    }
}
