package com.ganim.killersudoku.game

import com.ganim.killersudoku.data.PuzzleRepository
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.pack.PuzzlePack
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.roundToLong

/**
 * Build plan Phase 2: "Cage borders and sums render correctly for every cage shape in the
 * pack (test against the full pack, not a sample - snaking cages will find your rendering
 * bugs)". So this runs over all 5,000 bundled puzzles.
 */
class CageOutlinesTest {

    private val pack by lazy { PuzzlePack.decode(File("src/main/assets/${PuzzleRepository.ASSET}").readBytes()) }

    private fun eachPuzzle(block: (KillerPuzzle) -> Unit) {
        for (i in 0 until pack.size) block(pack[i])
    }

    @Test
    @DisplayName("every cell side between two cages gets exactly one segment, and no other side gets any")
    fun `borders match the layout`() = eachPuzzle { p ->
        val boundarySides = (0 until Geometry.CELLS).sumOf { c ->
            val r = Geometry.row(c)
            val col = Geometry.col(c)
            listOf(r - 1 to col, r + 1 to col, r to col - 1, r to col + 1).count { (nr, nc) ->
                nr !in 0..8 || nc !in 0..8 || p.cageOf[Geometry.cell(nr, nc)] != p.cageOf[c]
            }
        }
        CageOutlines(p).segments.size shouldBe boundarySides
    }

    @Test
    @DisplayName("every cage outline closes: each segment end meets exactly one other end")
    fun `outlines are closed loops`() {
        var corners = 0
        eachPuzzle { p ->
            val outlines = CageOutlines(p)
            // Arbitrary but realistic proportions: the canvas uses an inset of 9% of a cell.
            val ends = HashMap<Pair<Long, Long>, Int>()
            for (seg in outlines.segments) {
                val q = outlines.points(seg, CELL, INSET)
                assertTrue(length(q) > 0.2f * CELL) { "a degenerate segment in puzzle ${p.id}" }
                for (k in listOf(0, 2)) ends.merge(key(q[k], q[k + 1]), 1, Int::plus)
            }
            val loose = ends.filterValues { it != 2 }
            assertTrue(loose.isEmpty()) {
                "puzzle ${p.id}: ${loose.size} outline ends meet the wrong number of others " +
                    "(a gap or an overshoot at a corner), e.g. ${loose.entries.first()}"
            }
            corners += ends.size
        }
        // A sanity floor so an empty pack or an empty outline cannot pass vacuously.
        assertTrue(corners > 5_000 * 50)
    }

    @Test
    @DisplayName("the sum label always sits in a convex top-left corner, clear of any outline running through")
    fun `sum labels have a free corner`() = eachPuzzle { p ->
        for (cage in p.cages) {
            val a = cage.anchor
            val r = Geometry.row(a)
            val c = Geometry.col(a)
            val above = r > 0 && p.cageOf[a - 9] == p.cageOf[a]
            val left = c > 0 && p.cageOf[a - 1] == p.cageOf[a]
            assertTrue(!above && !left) { "puzzle ${p.id}: cage label at ${Geometry.cellName(a)} has cage cells above or left" }
        }
    }

    @Test
    fun `the pack exercises the hard shapes`() {
        // Concave corners (L, T and snake shapes) are where the extension logic matters.
        // If the pack stopped containing them, the loop test above would prove much less.
        var concave = 0
        eachPuzzle { p ->
            for (seg in CageOutlines(p).segments) {
                val horizontal = seg[1] == seg[3]
                // Along-the-side multipliers: a start pushed back (-1) or an end pushed on (+1)
                // past the cell edge is a concave join.
                val start = if (horizontal) seg[4] else seg[5]
                val end = if (horizontal) seg[6] else seg[7]
                if (start == -1f) concave++
                if (end == 1f) concave++
            }
        }
        assertTrue(concave > 10_000) { "only $concave concave joins across the pack" }
    }

    private fun length(q: FloatArray): Float = kotlin.math.hypot(q[2] - q[0], q[3] - q[1])

    private fun key(x: Float, y: Float) = (x * 1000).roundToLong() to (y * 1000).roundToLong()

    private companion object {
        const val CELL = 1f
        const val INSET = 0.09f
    }
}
