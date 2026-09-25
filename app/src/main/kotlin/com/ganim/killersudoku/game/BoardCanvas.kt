package com.ganim.killersudoku.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.ui.theme.BoardColors
import com.ganim.killersudoku.ui.theme.LocalBoardColors

/**
 * The 9x9 board: highlights, grid, dashed cage outlines inset inside the cells with the
 * sum in each cage's top-left cell (build plan 6 - the conventional rendering), digits
 * and 3x3 pencil marks.
 */
@Composable
fun BoardCanvas(ui: GameUi, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    val measurer = rememberTextMeasurer()
    val outlines = remember(ui.puzzle) { CageOutlines(ui.puzzle) }
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val cell = size.width / 9f
                    val r = (p.y / cell).toInt().coerceIn(0, 8)
                    val c = (p.x / cell).toInt().coerceIn(0, 8)
                    onSelect(Geometry.cell(r, c))
                }
            },
    ) {
        val s = size.width / 9f
        drawRect(colors.background)
        drawHighlights(ui, s, colors)
        drawGrid(s, colors)
        drawCages(ui.puzzle, outlines, s, colors, measurer)
        drawDigits(ui, s, colors, measurer)
    }
}

private fun DrawScope.cellOrigin(cell: Int, s: Float) = Offset(Geometry.col(cell) * s, Geometry.row(cell) * s)

private fun DrawScope.drawHighlights(ui: GameUi, s: Float, colors: BoardColors) {
    val sel = ui.selected
    val selCage = ui.puzzle.cageOf[sel]
    val selDigit = ui.values[sel]
    for (c in 0 until Geometry.CELLS) {
        val color = when {
            ui.hint?.cell == c -> colors.hint
            c == sel -> colors.selected
            selDigit != 0 && ui.values[c] == selDigit -> colors.sameDigit
            ui.puzzle.cageOf[c] == selCage -> colors.cageHighlight
            Geometry.sharesHouse(c, sel) -> colors.related
            else -> null
        } ?: continue
        drawRect(color, cellOrigin(c, s), Size(s, s))
        if (ui.conflicts[c]) drawRect(colors.errorFill, cellOrigin(c, s), Size(s, s))
    }
    for (c in 0 until Geometry.CELLS) {
        if (ui.conflicts[c]) drawRect(colors.errorFill, cellOrigin(c, s), Size(s, s))
    }
}

private fun DrawScope.drawGrid(s: Float, colors: BoardColors) {
    val thin = 1f
    val thick = 2.5f * density / 1.5f
    for (i in 0..9) {
        val w = if (i % 3 == 0) thick else thin
        val col = if (i % 3 == 0) colors.gridThick else colors.gridThin
        drawLine(col, Offset(i * s, 0f), Offset(i * s, size.height), w)
        drawLine(col, Offset(0f, i * s), Offset(size.width, i * s), w)
    }
}

/**
 * Inset dashed outlines. Each border segment runs between inset corners; where the cage
 * continues around a corner the segment is extended so neighbouring segments meet - to the
 * cell edge at a straight join, and past it by the inset at a concave corner.
 */
private class CageOutlines(puzzle: KillerPuzzle) {
    /** Each segment as fractions of a cell: x1, y1, x2, y2 in cell units, inset applied at draw time. */
    val segments: List<FloatArray>

    init {
        val k = puzzle.cageOf
        fun same(cell: Int, dr: Int, dc: Int): Boolean {
            val r = Geometry.row(cell) + dr
            val c = Geometry.col(cell) + dc
            return r in 0..8 && c in 0..8 && k[Geometry.cell(r, c)] == k[cell]
        }
        // Each entry: (x1, y1, x2, y2, ex1, ey1, ex2, ey2): base endpoints in inset units.
        val out = ArrayList<FloatArray>()
        for (cell in 0 until Geometry.CELLS) {
            val r = Geometry.row(cell).toFloat()
            val c = Geometry.col(cell).toFloat()
            // For each side: direction to neighbour (dr, dc) and the two perpendicular directions.
            // Represented with endpoint offsets in "inset" units: -1 means extend past edge, 0 edge, 1 inset.
            fun end(perpDr: Int, perpDc: Int, sideDr: Int, sideDc: Int): Int = when {
                !same(cell, perpDr, perpDc) -> 1
                same(cell, perpDr + sideDr, perpDc + sideDc) -> -1
                else -> 0
            }
            if (!same(cell, -1, 0)) out.add(floatArrayOf(c, r, c + 1, r, end(0, -1, -1, 0).toFloat(), 1f, -end(0, 1, -1, 0).toFloat(), 1f))
            if (!same(cell, 1, 0)) out.add(floatArrayOf(c, r + 1, c + 1, r + 1, end(0, -1, 1, 0).toFloat(), -1f, -end(0, 1, 1, 0).toFloat(), -1f))
            if (!same(cell, 0, -1)) out.add(floatArrayOf(c, r, c, r + 1, 1f, end(-1, 0, 0, -1).toFloat(), 1f, -end(1, 0, 0, -1).toFloat()))
            if (!same(cell, 0, 1)) out.add(floatArrayOf(c + 1, r, c + 1, r + 1, -1f, end(-1, 0, 0, 1).toFloat(), -1f, -end(1, 0, 0, 1).toFloat()))
        }
        segments = out
    }
}

private fun DrawScope.drawCages(puzzle: KillerPuzzle, outlines: CageOutlines, s: Float, colors: BoardColors, measurer: TextMeasurer) {
    val inset = s * 0.09f
    val dash = PathEffect.dashPathEffect(floatArrayOf(s * 0.07f, s * 0.05f))
    val stroke = 1.2f * density
    for (seg in outlines.segments) {
        drawLine(
            colors.cage,
            Offset(seg[0] * s + seg[4] * inset, seg[1] * s + seg[5] * inset),
            Offset(seg[2] * s + seg[6] * inset, seg[3] * s + seg[7] * inset),
            stroke,
            pathEffect = dash,
        )
    }
    val style = TextStyle(fontSize = (s * 0.21f / density / fontScale).sp, fontWeight = FontWeight.SemiBold, color = colors.cageSum)
    for (cage in puzzle.cages) {
        val o = cellOrigin(cage.anchor, s)
        val layout = measurer.measure(cage.sum.toString(), style)
        // Paint behind the label so the dashed line doesn't run through it.
        drawRect(colors.background, Offset(o.x + inset * 0.5f, o.y + inset * 0.5f),
            Size(layout.size.width + inset * 0.6f, layout.size.height.toFloat()))
        drawText(layout, topLeft = Offset(o.x + inset * 0.7f, o.y + inset * 0.4f))
    }
}

private fun DrawScope.drawDigits(ui: GameUi, s: Float, colors: BoardColors, measurer: TextMeasurer) {
    val big = TextStyle(fontSize = (s * 0.55f / density / fontScale).sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = (s * 0.2f / density / fontScale).sp, color = colors.note)
    for (c in 0 until Geometry.CELLS) {
        val o = cellOrigin(c, s)
        val v = ui.values[c]
        if (v != 0) {
            val wrong = v != ui.puzzle.solution[c]
            val layout = measurer.measure(v.toString(), big.copy(color = if (wrong || ui.conflicts[c]) colors.error else colors.entered))
            drawText(layout, topLeft = Offset(o.x + (s - layout.size.width) / 2, o.y + (s - layout.size.height) / 2 + s * 0.04f))
        } else if (ui.notes[c] != 0) {
            val sub = s * 0.8f / 3
            for (d in Digits.toList(ui.notes[c])) {
                val layout = measurer.measure(d.toString(), small)
                val cx = o.x + s * 0.1f + ((d - 1) % 3) * sub + sub / 2
                val cy = o.y + s * 0.14f + ((d - 1) / 3) * sub * 0.95f + sub / 2
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2, cy - layout.size.height / 2))
            }
        }
    }
}
