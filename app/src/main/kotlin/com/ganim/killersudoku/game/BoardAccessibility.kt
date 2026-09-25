package com.ganim.killersudoku.game

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.Geometry

/**
 * Makes the board readable by TalkBack, as Nonogram's board does.
 *
 * The board is one canvas, so to a screen reader it is a blank rectangle. Here each cell
 * gets a real semantics node that announces where it is, what cage it belongs to and what
 * it holds; activating it selects the cell, and the number pad then fills it.
 *
 * Built **only while touch exploration is on**. Eighty-one nodes over the board would
 * cost every other player recompositions for nothing; with TalkBack off this composes
 * nothing at all.
 */
@Composable
fun BoardAccessibilityOverlay(ui: GameUi, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (!rememberTouchExplorationEnabled()) return
    BoxWithConstraints(modifier) {
        val cell = maxWidth / 9
        for (c in 0 until Geometry.CELLS) {
            Box(
                Modifier
                    .offset(x = cell * Geometry.col(c), y = cell * Geometry.row(c))
                    .size(cell)
                    .semantics {
                        contentDescription = describeCell(ui, c)
                        selected = c == ui.selected
                        onClick(label = "Select") {
                            onSelect(c)
                            true
                        }
                    },
            )
        }
    }
}

/**
 * Position, then cage, then contents - the order a sighted player reads a cell in.
 *
 * The cage is announced as its sum and size ("cage of 17, 2 cells"), because that is the
 * whole clue; a bare "row 3, column 5, empty" would leave a screen-reader player with
 * nothing to reason from.
 */
internal fun describeCell(ui: GameUi, c: Int): String {
    val cage = ui.puzzle.cages[ui.puzzle.cageOf[c]]
    val where = "Row ${Geometry.row(c) + 1}, column ${Geometry.col(c) + 1}"
    val cageText = if (cage.size == 1) "single cell cage of ${cage.sum}" else "cage of ${cage.sum}, ${cage.size} cells"
    val v = ui.values[c]
    val contents = when {
        v != 0 && v != ui.puzzle.solution[c] -> "$v, wrong"
        v != 0 && ui.conflicts[c] -> "$v, clashes"
        v != 0 -> "$v"
        ui.notes[c] != 0 -> "empty, notes ${Digits.toList(ui.notes[c]).joinToString(" ")}"
        else -> "empty"
    }
    return "$where, $cageText, $contents"
}

/** Tracked live, so turning TalkBack on does not need an app restart. */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(context.touchExplorationOn()) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

private fun Context.touchExplorationOn(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
