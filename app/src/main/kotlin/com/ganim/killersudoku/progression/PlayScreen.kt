package com.ganim.killersudoku.progression

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.killersudoku.daily.difficultyTint
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.ui.components.Chip
import com.ganim.killersudoku.ui.components.GameIcon
import com.ganim.killersudoku.ui.components.Glyph
import com.ganim.killersudoku.ui.components.Meter
import com.ganim.killersudoku.ui.components.Panel
import com.ganim.killersudoku.ui.components.PrimaryButton
import com.ganim.killersudoku.ui.theme.LocalBoardColors
import com.ganim.killersudoku.ui.theme.LocalReduceMotion
import com.ganim.killersudoku.ui.theme.Motion

/**
 * The home screen: a ladder of numbered levels.
 *
 * This replaced the daily puzzle as the app's landing screen after the first real tester
 * could not find where the game started. The diagnosis was not that the daily screen was
 * badly built - it was that it opened on a concept. "Today's puzzle" assumes you already
 * play; "Level 1" assumes nothing.
 *
 * So the screen answers one question before any other: **what do I tap?** The continue
 * card is the answer, it is the first thing on the page, and it is the only bevelled
 * button here. Everything below it is for the player who wants to choose instead.
 */
@Composable
fun PlayScreen(
    viewModel: PlayViewModel,
    onPlay: (puzzleId: String) -> Unit,
    onHowToPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalBoardColors.current

    // A cleared stage folds to one line. Twelve warm-ups you have already solved are not
    // worth two screens of scrolling, and free play below the ladder is otherwise buried
    // behind them.
    //
    // Held here rather than in the view model: which sections are open is view state,
    // it means nothing to the rest of the app, and it should not survive being killed
    // in the background. A Set of names saves and restores across rotation for free.
    var opened by rememberSaveable { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        modifier.fillMaxSize().background(colors.boardBackground),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    "Killer Sudoku",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.clueText,
                )
                Text(
                    if (state.solved == 0) {
                        "Every cage adds up. No guessing, ever."
                    } else {
                        "${state.solved} of ${state.total} levels solved"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
        }

        // Before anything has been solved, the walkthrough is the more useful first tap
        // of the two - so it sits above the ladder rather than in Settings where the
        // tester never looked.
        if (state.solved == 0) {
            item { HowToPlayCard(onHowToPlay) }
        }

        state.next?.let { next ->
            item { ContinueCard(next, state.solved == 0) { onPlay(next.level.id) } }
        }

        state.continueId?.let { id ->
            item { ResumeCard { onPlay(id) } }
        }

        if (state.loaded && state.next == null) {
            item { AllClearCard() }
        }

        items(state.stages, key = { it.stage.name }) { stage ->
            StageSection(
                stage = stage,
                // A stage you are still working through is always open; only a finished
                // one folds, and only until you ask for it back.
                expanded = !stage.cleared || stage.stage.name in opened,
                onToggle = {
                    opened = if (stage.stage.name in opened) {
                        opened - stage.stage.name
                    } else {
                        opened + stage.stage.name
                    }
                },
                onPlay = onPlay,
            )
        }

        item {
            FreePlaySection(
                free = state.freePlay,
                onDifficulty = viewModel::setFreeDifficulty,
                onPlayOne = { viewModel.pickFreePuzzle()?.let(onPlay) },
            )
        }
    }
}

/**
 * The other five thousand.
 *
 * Five thousand puzzles is a *supply*, not a catalogue (Nonogram learned this the hard
 * way and retired its archive tab). So the unit is a difficulty and the action is "play
 * one". There is no browse grid: every killer is 9x9, so a thumbnail tells you nothing.
 */
@Composable
private fun FreePlaySection(
    free: FreePlayUi,
    onDifficulty: (Difficulty) -> Unit,
    onPlayOne: () -> Unit,
) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Text(
            "Free play",
            style = MaterialTheme.typography.titleMedium,
            color = colors.clueText,
        )
        Text(
            "Five thousand more, every one checked for a single solution.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textMuted,
        )

        Box(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Difficulty.entries.forEach { difficulty ->
                Pick(
                    label = difficulty.name.lowercase(),
                    selected = difficulty == free.difficulty,
                    tint = difficultyTint(difficulty),
                    modifier = Modifier.weight(1f),
                ) { onDifficulty(difficulty) }
            }
        }

        Box(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (free.allSolved) {
                    "All ${free.total} solved"
                } else {
                    "${free.solved} of ${free.total} solved"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (free.allSolved) colors.success else colors.textMuted,
                modifier = Modifier.weight(1f),
            )
        }

        Box(Modifier.height(10.dp))

        PrimaryButton(
            text = "Play a random one",
            onClick = onPlayOne,
            modifier = Modifier.fillMaxWidth(),
            glyph = Glyph.SPARK,
        )
    }
}

@Composable
private fun Pick(
    label: String,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            // Selected wears the accent, like every other "this one" in the app. The
            // difficulty colour marks the label instead: as a fill it put ink on sky at
            // under 3:1 in the light theme.
            .background(if (selected) colors.accentFill else Color.Transparent)
            .border(1.dp, if (selected) colors.accentDeep else colors.stroke, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onAccentFill else tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun HowToPlayCard(onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = colors.info, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.SPARK, colors.info, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "New to killer sudoku?",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.clueText,
                )
                Text(
                    "The rules in one minute, and the one trick that unlocks them.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            GameIcon(Glyph.ARROW, colors.info, size = 20.dp)
        }
    }
}

/**
 * The one tap this screen exists to offer.
 *
 * The wording changes on the very first run - "Start here" rather than "Continue" -
 * because a player with nothing solved is not continuing anything, and being told they
 * are is the small kind of wrong that makes an app feel like it is not talking to you.
 */
@Composable
private fun ContinueCard(next: LevelUi, firstRun: Boolean, onPlay: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(next.level.stage.title.uppercase(), colors.accent)
            Chip(next.level.difficulty.name.lowercase(), difficultyTint(next.level.difficulty))
            if (next.started) Chip("in progress", colors.success)
        }

        Box(Modifier.height(12.dp))

        Text(
            "Level ${next.level.number}",
            style = MaterialTheme.typography.displaySmall,
            color = colors.clueText,
        )

        Box(Modifier.height(4.dp))

        Text(
            when {
                next.started -> "Pick up where you left off."
                firstRun -> "Fill the grid so every cage adds up to its number."
                else -> next.level.stage.blurb
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )

        Box(Modifier.height(16.dp))

        PrimaryButton(
            text = when {
                next.started -> "Resume"
                firstRun -> "Start here"
                else -> "Play"
            },
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A free-play or daily puzzle left half done. */
@Composable
private fun ResumeCard(onResume: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), onClick = onResume) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.CLOCK, colors.accent, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text("Unfinished puzzle", style = MaterialTheme.typography.titleMedium, color = colors.clueText)
                Text(
                    "Pick up the one you left.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            GameIcon(Glyph.ARROW, colors.accent, size = 20.dp)
        }
    }
}

@Composable
private fun AllClearCard() {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = colors.success) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(Glyph.TROPHY, colors.success, size = 24.dp)
            Column {
                Text(
                    "Ladder cleared",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.clueText,
                )
                Text(
                    "Every level solved. Free play has 5,000 more.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun StageSection(
    stage: StageUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: (String) -> Unit,
) {
    val colors = LocalBoardColors.current
    val reduceMotion = LocalReduceMotion.current
    val turn by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = tween(Motion.duration(Motion.QUICK, reduceMotion)),
        label = "stageChevron",
    )

    Panel(Modifier.fillMaxWidth()) {
        Row(
            // Only a cleared stage has anything to toggle, so only that one takes the
            // tap - a header that looks pressable and does nothing is worse than one
            // that plainly is not.
            Modifier
                .fillMaxWidth()
                .then(if (stage.cleared) Modifier.clickable(onClick = onToggle) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        stage.stage.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.clueText,
                    )
                    if (stage.cleared) {
                        GameIcon(Glyph.CHECK, colors.success, size = 17.dp)
                    }
                }
                Text(
                    // Folded, the blurb is the wrong thing to say: what this stage is
                    // like no longer matters once it is behind you.
                    if (stage.cleared && !expanded) "Cleared" else stage.stage.blurb,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (stage.cleared && !expanded) colors.success else colors.textMuted,
                )
            }
            Text(
                "${stage.completedCount}/${stage.total}",
                style = MaterialTheme.typography.titleMedium,
                color = if (stage.cleared) colors.success else colors.textMuted,
            )
            if (stage.cleared) {
                Box(Modifier.width(6.dp))
                GameIcon(
                    glyph = Glyph.CHEVRON_DOWN,
                    tint = colors.textMuted,
                    modifier = Modifier.rotate(turn),
                    size = 18.dp,
                    contentDescription = if (expanded) "Hide levels" else "Show levels",
                )
            }
        }

        if (!expanded) return@Panel

        Box(Modifier.height(10.dp))
        Meter(stage.fraction, if (stage.cleared) colors.success else colors.accent)
        Box(Modifier.height(12.dp))

        // Laid out by hand in rows of five rather than with a nested lazy grid, which
        // cannot be measured inside a LazyColumn item without a fixed height.
        stage.levels.chunked(LEVELS_PER_ROW).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { level ->
                    LevelTile(level, Modifier.weight(1f)) { onPlay(level.level.id) }
                }
                // Keeps the last row's tiles the same size as every other row's.
                repeat(LEVELS_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Half a turn, so the chevron points up when its section is open. */
private const val HALF_TURN = 180f

/**
 * One level.
 *
 * Three states worth distinguishing and no more: solved, the one to play next, and not
 * yet. A started-but-unfinished level is the next one by definition, so it does not need
 * a fourth look.
 */
@Composable
private fun LevelTile(level: LevelUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        level.completed -> colors.success
        level.isNext -> colors.accentFill
        else -> Color.Transparent
    }
    val content = when {
        level.completed -> colors.onAccent
        level.isNext -> colors.onAccentFill
        else -> colors.textMuted
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = when {
                    level.completed -> colors.success
                    level.isNext -> colors.accentDeep
                    else -> colors.stroke
                },
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (level.completed) {
            GameIcon(
                Glyph.CHECK,
                content,
                size = 18.dp,
                contentDescription = "Level ${level.level.number}, solved",
            )
        } else {
            Text(
                level.level.numberInStage.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val LEVELS_PER_ROW = 5
