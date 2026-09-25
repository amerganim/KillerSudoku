package com.ganim.killersudoku.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.ui.components.Capsule
import com.ganim.killersudoku.ui.components.GameIcon
import com.ganim.killersudoku.ui.components.GhostButton
import com.ganim.killersudoku.ui.components.Glyph
import com.ganim.killersudoku.ui.components.Panel
import com.ganim.killersudoku.ui.components.PrimaryButton
import com.ganim.killersudoku.ui.components.ScoreRow
import com.ganim.killersudoku.ui.theme.LocalBoardColors

/**
 * The play screen: board, then tools, then a persistent number pad (build plan 6 - never
 * a popup keypad over the grid). The bottom bar is hidden here; the board needs the room.
 */
@Composable
fun GameScreen(
    vm: GameViewModel,
    haptics: Boolean,
    onToggleAutoNotes: () -> Unit,
    onExit: () -> Unit,
    onNext: (() -> Unit)?,
) {
    val ui = vm.ui
    val colors = LocalBoardColors.current
    val feedback = LocalHapticFeedback.current
    fun tick() {
        if (haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(Modifier.fillMaxSize().background(colors.boardBackground)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(ui, onExit)
            BoardCanvas(
                ui,
                onSelect = { tick(); vm.select(it) },
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            )
            StatusLine(ui, vm)
            Spacer(Modifier.weight(1f))
            Tools(ui, vm, onToggleAutoNotes)
            Spacer(Modifier.height(10.dp))
            NumberPad(ui) { tick(); vm.digit(it) }
            Spacer(Modifier.height(10.dp))
        }

        if (ui.solved) {
            ResultsCard(ui, onExit, onNext)
        } else if (ui.outOfLives) {
            OutOfLivesCard(onRestart = vm::restart, onExit = onExit)
        }
    }
}

@Composable
private fun TopBar(ui: GameUi, onExit: () -> Unit) {
    val colors = LocalBoardColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            GameIcon(Glyph.BACK, colors.clueText, size = 20.dp, contentDescription = "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(ui.title, style = MaterialTheme.typography.titleMedium, color = colors.clueText, maxLines = 1)
            Text(
                ui.puzzle.difficulty.name.lowercase(),
                style = MaterialTheme.typography.labelMedium,
                color = difficultyColor(ui.puzzle.difficulty),
            )
        }
        Capsule(Glyph.CLOCK, formatTime(ui.elapsedSeconds), colors.textMuted)
        Lives(ui.mistakes)
    }
}

@Composable
private fun Lives(mistakes: Int) {
    val colors = LocalBoardColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(GameSession.MAX_MISTAKES) { i ->
            val left = i < GameSession.MAX_MISTAKES - mistakes
            GameIcon(
                if (left) Glyph.HEART_SOLID else Glyph.HEART,
                if (left) colors.cellMistake else colors.stroke,
                size = 18.dp,
            )
        }
    }
}

/** Fixed height for hints and feedback, so the pad never jumps. */
@Composable
private fun StatusLine(ui: GameUi, vm: GameViewModel) {
    val colors = LocalBoardColors.current
    Box(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(top = 8.dp)) {
        val hint = ui.hint
        when {
            hint != null -> Panel(Modifier.fillMaxWidth(), tint = colors.info) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameIcon(Glyph.BULB, colors.info, size = 18.dp)
                    Text(
                        "${hint.technique} · ${Geometry.cellName(hint.cell)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.clueText,
                    )
                }
                Text(hint.explanation, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    GhostButton("Close", onClick = vm::dismissHint)
                    GhostButton("Fill in ${hint.digit}", onClick = vm::applyHint, glyph = Glyph.CHECK, tint = colors.info)
                }
            }
            ui.message != null -> Text(
                ui.message,
                Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge,
                color = colors.cellMistake,
            )
        }
    }
}

@Composable
private fun Tools(ui: GameUi, vm: GameViewModel, onToggleAutoNotes: () -> Unit) {
    Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tool(Glyph.UNDO, "Undo", Modifier.weight(1f), enabled = ui.canUndo, onClick = vm::undo)
        Tool(Glyph.ERASER, "Erase", Modifier.weight(1f), onClick = vm::erase)
        Tool(Glyph.PENCIL, "Notes", Modifier.weight(1f), active = ui.pencil && !ui.autoNotes, enabled = !ui.autoNotes, onClick = vm::togglePencil)
        Tool(Glyph.WAND, "Auto", Modifier.weight(1f), active = ui.autoNotes, onClick = onToggleAutoNotes)
        Tool(Glyph.BULB, "Hint", Modifier.weight(1f), onClick = vm::requestHint)
    }
}

/** A tool key. Active wears the accent, like every other "this one is on". */
@Composable
private fun Tool(
    glyph: Glyph,
    label: String,
    modifier: Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(16.dp)
    val tint = if (active) colors.onAccentFill else colors.clueText
    Column(
        modifier
            .clip(shape)
            .background(if (active) colors.accentFill else colors.surface)
            .border(1.dp, if (active) colors.accentDeep else colors.stroke, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        GameIcon(glyph, tint, size = 20.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

/**
 * Nine keys, each showing how many of that digit are left to place. A digit that is
 * finished fades out, which doubles as a progress read-out. In notes mode the digits
 * shrink, so the mode is visible on the keys you are about to press.
 */
@Composable
private fun NumberPad(ui: GameUi, onDigit: (Int) -> Unit) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (d in 1..9) {
            val done = ui.remaining[d] == 0
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.stroke, shape)
                    .clickable(enabled = !done, role = Role.Button) { onDigit(d) }
                    .alpha(if (done) DONE_ALPHA else 1f)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.toString(),
                    style = if (ui.pencil && !ui.autoNotes) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    color = if (ui.pencil && !ui.autoNotes) colors.textMuted else colors.clueText,
                    textAlign = TextAlign.Center,
                )
                Text(ui.remaining[d].toString(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun ResultsCard(ui: GameUi, onExit: () -> Unit, onNext: (() -> Unit)?) {
    val colors = LocalBoardColors.current
    Scrim {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameIcon(Glyph.TROPHY, colors.success, size = 26.dp)
                Text("Solved", style = MaterialTheme.typography.displaySmall, color = colors.clueText)
            }
            Spacer(Modifier.height(4.dp))
            Text(ui.title, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Spacer(Modifier.height(14.dp))
            ScoreRow(
                listOf(
                    Triple("Time", formatTime(ui.elapsedSeconds), colors.clueText),
                    Triple("Mistakes", ui.mistakes.toString(), if (ui.mistakes == 0) colors.success else colors.cellMistake),
                    Triple("Hints", ui.hintsUsed.toString(), colors.info),
                ),
            )
            Spacer(Modifier.height(16.dp))
            if (onNext != null) {
                PrimaryButton("Next level", onClick = onNext, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                GhostButton("Back", onClick = onExit, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Done", onClick = onExit, modifier = Modifier.fillMaxWidth(), glyph = Glyph.CHECK)
            }
        }
    }
}

@Composable
private fun OutOfLivesCard(onRestart: () -> Unit, onExit: () -> Unit) {
    val colors = LocalBoardColors.current
    Scrim {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameIcon(Glyph.HEART, colors.cellMistake, size = 26.dp)
                Text("Out of lives", style = MaterialTheme.typography.headlineSmall, color = colors.clueText)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Three mistakes. Every puzzle here can be solved without guessing - a hint will " +
                    "show you the next step that is certain.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Try again", onClick = onRestart, modifier = Modifier.fillMaxWidth(), glyph = Glyph.UNDO)
            Spacer(Modifier.height(8.dp))
            GhostButton("Back", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Dims the board behind a card and swallows taps, so the finished board cannot be edited. */
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    val colors = LocalBoardColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.boardBackground.copy(alpha = SCRIM_ALPHA))
            .clickable(enabled = true, onClick = {})
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.widthIn(max = 460.dp)) { content() }
    }
}

@Composable
fun difficultyColor(difficulty: Difficulty) = com.ganim.killersudoku.daily.difficultyTint(difficulty)

fun formatTime(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

private const val DISABLED_ALPHA = 0.4f
private const val DONE_ALPHA = 0.25f
private const val SCRIM_ALPHA = 0.82f
