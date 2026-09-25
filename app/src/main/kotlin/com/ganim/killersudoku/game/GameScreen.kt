package com.ganim.killersudoku.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganim.killersudoku.engine.model.Geometry

@Composable
fun GameScreen(vm: GameViewModel, onHome: () -> Unit, onNewGame: () -> Unit) {
    val ui = vm.ui ?: return
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(ui, onHome)
        BoardCanvas(ui, vm::select, Modifier.widthIn(max = 560.dp).fillMaxWidth())
        StatusLine(ui, vm)
        Spacer(Modifier.weight(1f))
        Tools(ui, vm)
        Spacer(Modifier.height(10.dp))
        NumberPad(ui, vm::digit)
        Spacer(Modifier.height(12.dp))
    }

    if (ui.solved) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Solved!") },
            text = { Text("${ui.puzzle.difficulty.label()} in ${formatTime(ui.elapsedSeconds)} with ${ui.mistakes} mistake${if (ui.mistakes == 1) "" else "s"}.") },
            confirmButton = { TextButton(onClick = onNewGame) { Text("New puzzle") } },
            dismissButton = { TextButton(onClick = onHome) { Text("Home") } },
        )
    } else if (ui.outOfLives) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Out of lives") },
            text = { Text("Three mistakes. Try this puzzle again from the start?") },
            confirmButton = { TextButton(onClick = vm::restart) { Text("Restart") } },
            dismissButton = { TextButton(onClick = onHome) { Text("Home") } },
        )
    }
}

@Composable
private fun TopBar(ui: GameUi, onHome: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹ Home",
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onHome).padding(8.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(ui.puzzle.difficulty.label(), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(formatTime(ui.elapsedSeconds), Modifier.padding(end = 12.dp))
        Text("♥".repeat(GameSession.MAX_MISTAKES - ui.mistakes) + "♡".repeat(ui.mistakes), color = MaterialTheme.colorScheme.error)
    }
}

/** Fixed-height line for hints and feedback, so the pad never jumps. */
@Composable
private fun StatusLine(ui: GameUi, vm: GameViewModel) {
    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(top = 8.dp)) {
        val hint = ui.hint
        when {
            hint != null -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("${hint.technique} · ${Geometry.cellName(hint.cell)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(hint.explanation, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = vm::dismissHint) { Text("Close") }
                        TextButton(onClick = vm::applyHint) { Text("Fill in ${hint.digit}") }
                    }
                }
            }
            ui.message != null -> Text(ui.message, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Tools(ui: GameUi, vm: GameViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Tool("Undo", enabled = ui.canUndo, onClick = vm::undo)
        Tool("Erase", onClick = vm::erase)
        Tool(if (ui.pencil) "Notes ON" else "Notes", active = ui.pencil, enabled = !ui.autoNotes, onClick = vm::togglePencil)
        Tool(if (ui.autoNotes) "Auto ON" else "Auto", active = ui.autoNotes, onClick = vm::toggleAutoNotes)
        Tool("Hint", onClick = vm::requestHint)
    }
}

@Composable
private fun Tool(label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) colors.primary else colors.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = if (active) colors.onPrimary else colors.onSurfaceVariant,
        fontSize = 14.sp,
    )
}

/** Persistent pad under the board - never a popup over the grid (build plan 6). */
@Composable
private fun NumberPad(ui: GameUi, onDigit: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in 1..9) {
            val done = ui.remaining[d] == 0
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
                    .clickable(enabled = !done) { onDigit(d) }
                    .alpha(if (done) 0.25f else 1f)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.toString(),
                    fontSize = if (ui.pencil) 18.sp else 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (ui.pencil) colors.onSurfaceVariant else colors.primary,
                    textAlign = TextAlign.Center,
                )
                Text(ui.remaining[d].toString(), fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

fun formatTime(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun com.ganim.killersudoku.engine.model.Difficulty.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
