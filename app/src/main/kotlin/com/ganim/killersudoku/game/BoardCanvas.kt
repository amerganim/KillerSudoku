package com.ganim.killersudoku.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.ganim.killersudoku.ui.theme.DisplayFamily
import com.ganim.killersudoku.ui.theme.LocalBoardColors
import com.ganim.killersudoku.ui.theme.over

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
    Box(modifier.aspectRatio(1f)) {
        Canvas(
            Modifier
                .fillMaxSize()
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
            drawRect(colors.cell)
            drawHighlights(ui, s, colors)
            drawGrid(s, colors)
            drawCages(ui, outlines, s, colors, measurer)
            drawSelection(ui.selected, s, colors)
            drawDigits(ui, s, colors, measurer)
        }
        // Composes nothing unless a screen reader is exploring by touch.
        BoardAccessibilityOverlay(ui, onSelect, Modifier.matchParentSize())
    }
}

private fun DrawScope.cellOrigin(cell: Int, s: Float) = Offset(Geometry.col(cell) * s, Geometry.row(cell) * s)

/** The wash a cell wears, if any. One function, so the cage-sum label can match it exactly. */
private fun washFor(ui: GameUi, c: Int, colors: BoardColors): Color? {
    val sel = ui.selected
    val selDigit = ui.values[sel]
    return when {
        ui.hint?.cell == c -> colors.hintWash
        c == sel -> colors.selected
        selDigit != 0 && ui.values[c] == selDigit -> colors.sameDigit
        ui.puzzle.cageOf[c] == ui.puzzle.cageOf[sel] -> colors.cageHighlight
        Geometry.sharesHouse(c, sel) -> colors.highlight
        else -> null
    }
}

/** What the cell actually looks like under its digit: the cell, its wash, and a clash on top. */
private fun groundFor(ui: GameUi, c: Int, colors: BoardColors): Color {
    var ground = colors.cell
    washFor(ui, c, colors)?.let { ground = it.over(ground) }
    if (ui.conflicts[c]) ground = colors.mistakeWash.over(ground)
    return ground
}

private fun DrawScope.drawHighlights(ui: GameUi, s: Float, colors: BoardColors) {
    for (c in 0 until Geometry.CELLS) {
        val wash = washFor(ui, c, colors) ?: continue
        drawRect(wash, cellOrigin(c, s), Size(s, s))
    }
    for (c in 0 until Geometry.CELLS) {
        if (ui.conflicts[c]) drawRect(colors.mistakeWash, cellOrigin(c, s), Size(s, s))
    }
}

/**
 * The selection is a ring in the accent, not a heavy fill: a wash strong enough to find
 * at a glance took a wrong digit on it below 3:1 in the dark theme.
 */
private fun DrawScope.drawSelection(cell: Int, s: Float, colors: BoardColors) {
    val w = 2.5f * density
    val o = cellOrigin(cell, s)
    drawRoundRect(
        colors.accent,
        topLeft = Offset(o.x + w / 2, o.y + w / 2),
        size = Size(s - w, s - w),
        cornerRadius = CornerRadius(s * 0.12f),
        style = Stroke(w),
    )
}

private fun DrawScope.drawGrid(s: Float, colors: BoardColors) {
    val thin = 1f
    val thick = 2.5f * density / 1.5f
    for (i in 0..9) {
        val w = if (i % 3 == 0) thick else thin
        val col = if (i % 3 == 0) colors.gridLineMajor else colors.gridLine
        drawLine(col, Offset(i * s, 0f), Offset(i * s, size.height), w)
        drawLine(col, Offset(0f, i * s), Offset(size.width, i * s), w)
    }
}

private fun DrawScope.drawCages(ui: GameUi, outlines: CageOutlines, s: Float, colors: BoardColors, measurer: TextMeasurer) {
    val inset = s * 0.09f
    val dash = PathEffect.dashPathEffect(floatArrayOf(s * 0.07f, s * 0.05f))
    val stroke = 1.2f * density
    for (seg in outlines.segments) {
        val p = outlines.points(seg, s, inset)
        drawLine(colors.cage, Offset(p[0], p[1]), Offset(p[2], p[3]), stroke, pathEffect = dash)
    }
    val style = TextStyle(fontSize = (s * 0.21f / density / fontScale).sp, fontWeight = FontWeight.SemiBold, color = colors.clueText, fontFamily = DisplayFamily)
    for (cage in ui.puzzle.cages) {
        val o = cellOrigin(cage.anchor, s)
        val layout = measurer.measure(cage.sum.toString(), style)
        // Paint behind the label so the dashed line doesn't run through it.
        drawRect(groundFor(ui, cage.anchor, colors), Offset(o.x + inset * 0.5f, o.y + inset * 0.5f),
            Size(layout.size.width + inset * 0.6f, layout.size.height.toFloat()))
        drawText(layout, topLeft = Offset(o.x + inset * 0.7f, o.y + inset * 0.4f))
    }
}

private fun DrawScope.drawDigits(ui: GameUi, s: Float, colors: BoardColors, measurer: TextMeasurer) {
    val big = TextStyle(fontSize = (s * 0.55f / density / fontScale).sp, fontWeight = FontWeight.Medium, fontFamily = DisplayFamily)
    val small = TextStyle(fontSize = (s * 0.2f / density / fontScale).sp, color = colors.textMuted)
    for (c in 0 until Geometry.CELLS) {
        val o = cellOrigin(c, s)
        val v = ui.values[c]
        if (v != 0) {
            val wrong = v != ui.puzzle.solution[c]
            val layout = measurer.measure(v.toString(), big.copy(color = if (wrong || ui.conflicts[c]) colors.cellMistake else colors.clueText))
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
