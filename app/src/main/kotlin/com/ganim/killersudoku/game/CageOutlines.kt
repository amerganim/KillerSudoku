package com.ganim.killersudoku.game

import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle

/**
 * Geometry for the dashed cage outlines, inset inside the cells (build plan 6 - the
 * conventional rendering). Pure Kotlin, so it is tested against every cage in the pack
 * rather than eyeballed on a few.
 *
 * Each border segment runs along one side of one cell. Its ends sit at the inset corner
 * where the cage turns; where the cage continues past the corner the end is pushed out -
 * to the cell edge at a straight join, and past the edge by one inset at a concave corner
 * - so that it meets the neighbouring segment exactly.
 */
class CageOutlines(puzzle: KillerPuzzle) {

    /**
     * One segment: `(x1, y1, x2, y2, ix1, iy1, ix2, iy2)`. The first four are cell-grid
     * coordinates, the last four are multiples of the inset added to them. A point is
     * `x * cellSize + ix * inset`.
     */
    val segments: List<FloatArray>

    init {
        val k = puzzle.cageOf
        fun same(cell: Int, dr: Int, dc: Int): Boolean {
            val r = Geometry.row(cell) + dr
            val c = Geometry.col(cell) + dc
            return r in 0..8 && c in 0..8 && k[Geometry.cell(r, c)] == k[cell]
        }
        val out = ArrayList<FloatArray>()
        for (cell in 0 until Geometry.CELLS) {
            val r = Geometry.row(cell).toFloat()
            val c = Geometry.col(cell).toFloat()
            // How far one end of a side goes, looking along the side towards (perpDr, perpDc):
            //  1 - the cage turns here: stop at the inset corner
            //  0 - the cage carries straight on: run to the cell edge
            // -1 - concave corner (the diagonal cell is in the cage too): run past the edge by an inset
            // (sideDr, sideDc) points across the side, out of the cell.
            fun end(perpDr: Int, perpDc: Int, sideDr: Int, sideDc: Int): Int = when {
                !same(cell, perpDr, perpDc) -> 1
                same(cell, perpDr + sideDr, perpDc + sideDc) -> -1
                else -> 0
            }
            if (!same(cell, -1, 0)) out.add(floatArrayOf(c, r, c + 1, r, end(0, -1, -1, 0).f, 1f, -end(0, 1, -1, 0).f, 1f))
            if (!same(cell, 1, 0)) out.add(floatArrayOf(c, r + 1, c + 1, r + 1, end(0, -1, 1, 0).f, -1f, -end(0, 1, 1, 0).f, -1f))
            if (!same(cell, 0, -1)) out.add(floatArrayOf(c, r, c, r + 1, 1f, end(-1, 0, 0, -1).f, 1f, -end(1, 0, 0, -1).f))
            if (!same(cell, 0, 1)) out.add(floatArrayOf(c + 1, r, c + 1, r + 1, -1f, end(-1, 0, 0, 1).f, -1f, -end(1, 0, 0, 1).f))
        }
        segments = out
    }

    private val Int.f: Float get() = toFloat()

    /** Resolves a segment to concrete coordinates for a cell size and inset. */
    fun points(seg: FloatArray, cell: Float, inset: Float): FloatArray = floatArrayOf(
        seg[0] * cell + seg[4] * inset,
        seg[1] * cell + seg[5] * inset,
        seg[2] * cell + seg[6] * inset,
        seg[3] * cell + seg[7] * inset,
    )
}
