package com.ganim.killersudoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganim.killersudoku.ui.components.GhostButton
import com.ganim.killersudoku.ui.components.Glyph
import com.ganim.killersudoku.ui.components.Panel
import com.ganim.killersudoku.ui.components.PrimaryButton
import com.ganim.killersudoku.ui.theme.DisplayFamily
import com.ganim.killersudoku.ui.theme.LocalBoardColors

/**
 * The rules, and the three ideas that make killer sudoku solvable without guessing.
 *
 * Opened once on first launch (marked seen on the way in, as in Nonogram) and kept at the
 * top of Settings. Short cards rather than a wall of text: the one thing it must land is
 * that **the numbers are enough**. A player who thinks killer sudoku needs guessing will
 * play one badly and quit.
 */
@Composable
fun HowToPlayScreen(onDone: () -> Unit) {
    val colors = LocalBoardColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.boardBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("How to play", style = MaterialTheme.typography.headlineSmall, color = colors.clueText, modifier = Modifier.weight(1f))
            GhostButton("Close", onClick = onDone, glyph = Glyph.CROSS)
        }

        Lesson(
            title = "Sudoku, but the numbers are sums",
            body = "Fill every row, column and 3×3 box with 1 to 9, once each. There are no " +
                "starting digits. Instead, dashed cages each show a total - the digits inside must " +
                "add up to it, and no digit repeats within a cage.",
        ) { CageExample(listOf("", "", ""), sum = 15) }

        Lesson(
            title = "Some cages have only one answer",
            body = "Two cells adding to 3 can only be 1 and 2. Two cells adding to 17 can only be " +
                "8 and 9. Small and extreme sums are where every puzzle starts.",
        ) { CageExample(listOf("8", "9"), sum = 17) }

        Lesson(
            title = "Every row, column and box adds to 45",
            body = "1 + 2 + … + 9 = 45. Add up the cages that sit wholly inside a row: whatever " +
                "is left over belongs to the cells that stick out. This is the rule of 45, and it " +
                "is the move that cracks almost every killer.",
        ) { CageExample(listOf("", "", "", "?"), sum = 45) }

        Lesson(
            title = "When you are stuck",
            body = "Pencil in the digits a cell could still be - or turn on Auto notes. A hint " +
                "never just gives you an answer: it names the technique and shows why the next " +
                "digit is certain. Three wrong digits ends the puzzle.",
        ) { CageExample(listOf("1 2", "", "7"), sum = 10) }

        PrimaryButton("Start playing", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Lesson(title: String, body: String, picture: @Composable () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.clueText)
        Column(Modifier.padding(vertical = 10.dp)) { picture() }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }
}

/** A row of cells with one dashed cage around them and its sum in the corner - the board's own look. */
@Composable
private fun CageExample(cells: List<String>, sum: Int) {
    val colors = LocalBoardColors.current
    val measurer = rememberTextMeasurer()
    val cellDp = 44.dp
    Canvas(Modifier.width(cellDp * cells.size).height(cellDp)) {
        val s = size.height
        val inset = s * 0.09f
        cells.indices.forEach { i ->
            drawRect(colors.cell, Offset(i * s, 0f), Size(s, s))
            drawRect(colors.gridLine, Offset(i * s, 0f), Size(s, s), style = Stroke(1f))
        }
        drawRect(
            colors.cage,
            Offset(inset, inset),
            Size(s * cells.size - 2 * inset, s - 2 * inset),
            style = Stroke(1.2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.07f, s * 0.05f))),
        )
        val sumLayout = measurer.measure(sum.toString(), TextStyle(fontSize = 10.sp, color = colors.clueText, fontFamily = DisplayFamily))
        drawRect(colors.cell, Offset(inset * 0.5f, inset * 0.5f), Size(sumLayout.size.width + inset * 0.6f, sumLayout.size.height.toFloat()))
        drawText(sumLayout, topLeft = Offset(inset * 0.7f, inset * 0.4f))
        cells.forEachIndexed { i, label ->
            if (label.isEmpty()) return@forEachIndexed
            val small = label.length > 1
            val layout = measurer.measure(
                label,
                TextStyle(
                    fontSize = if (small) 11.sp else 22.sp,
                    color = if (label == "?") colors.accent else if (small) colors.textMuted else colors.clueText,
                    fontFamily = DisplayFamily,
                ),
            )
            drawText(layout, topLeft = Offset(i * s + (s - layout.size.width) / 2, (s - layout.size.height) / 2 + s * 0.05f))
        }
    }
}
