package com.ganim.killersudoku.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ganim.killersudoku.data.ProgressRepository
import com.ganim.killersudoku.data.PuzzleRepository
import com.ganim.killersudoku.engine.model.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One level as the screen needs it: the rung, plus what the player has done with it. */
data class LevelUi(
    val level: LevelLadder.Level,
    val completed: Boolean,
    val started: Boolean,
    /** The level the home screen is steering towards. At most one is true. */
    val isNext: Boolean,
)

data class StageUi(
    val stage: LevelLadder.Stage,
    val levels: List<LevelUi>,
    val completedCount: Int,
) {
    val total: Int get() = levels.size
    val fraction: Float get() = if (total == 0) 0f else completedCount.toFloat() / total
    val cleared: Boolean get() = total > 0 && completedCount == total
}

/**
 * Free play: a difficulty, and "play one". The 5,000 puzzles are a supply, not a
 * catalogue (Nonogram's lesson), and killer sudoku has one size, so the bucket is just
 * the difficulty.
 */
data class FreePlayUi(
    val difficulty: Difficulty = Difficulty.EASY,
    val solved: Int = 0,
    val total: Int = 0,
) {
    val allSolved: Boolean get() = total > 0 && solved >= total
}

data class PlayUiState(
    val stages: List<StageUi> = emptyList(),
    val next: LevelUi? = null,
    val solved: Int = 0,
    val total: Int = 0,
    val loaded: Boolean = false,
    val freePlay: FreePlayUi = FreePlayUi(),
    /** An unfinished puzzle outside the ladder, offered as "Continue". */
    val continueId: String? = null,
)

/**
 * Drives the Play screen. The ladder is a pure function of the pack, built once off the
 * main thread; only the completed/started id sets move.
 */
class PlayViewModel(
    private val puzzles: PuzzleRepository,
    private val progress: ProgressRepository,
) : ViewModel() {

    private var stages: List<LevelLadder.StageLevels> = emptyList()
    private val difficulty = MutableStateFlow(Difficulty.EASY)
    private var solvedIds: Set<String> = emptySet()

    private val _state = MutableStateFlow(PlayUiState())
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    fun setFreeDifficulty(value: Difficulty) {
        difficulty.value = value
    }

    /**
     * An unsolved puzzle of the chosen difficulty, at random - free play is "another one",
     * not a second ladder. Falls back to any puzzle once all are solved, so the button
     * never does nothing.
     */
    fun pickFreePuzzle(): String? = puzzles.randomId(difficulty.value, solvedIds)

    init {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { stages = puzzles.ladder }
            val ladderIds = LevelLadder.allLevels(stages).map { it.id }.toSet()

            combine(
                progress.observeCompletedIds(),
                progress.observeInProgressIds(),
                difficulty,
            ) { completed, started, chosen ->
                solvedIds = completed
                val nextLevel = LevelLadder.next(stages, completed)
                val projected = stages.map { stage ->
                    val levels = stage.levels.map { level ->
                        LevelUi(
                            level = level,
                            completed = level.id in completed,
                            started = level.id in started,
                            isNext = level.id == nextLevel?.id,
                        )
                    }
                    StageUi(stage.stage, levels, levels.count { it.completed })
                }
                val pool = puzzles.entries.filter { it.difficulty == chosen }
                PlayUiState(
                    stages = projected,
                    next = projected.firstNotNullOfOrNull { s -> s.levels.firstOrNull { it.isNext } },
                    solved = projected.sumOf { it.completedCount },
                    total = projected.sumOf { it.total },
                    loaded = true,
                    freePlay = FreePlayUi(chosen, pool.count { it.id in completed }, pool.size),
                    // A ladder level in progress is already surfaced by "next"; this is for
                    // a free-play or daily puzzle left half done.
                    continueId = started.firstOrNull { it !in ladderIds && it !in completed },
                )
            }.collect { _state.value = it }
        }
    }

    class Factory(
        private val puzzles: PuzzleRepository,
        private val progress: ProgressRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlayViewModel(puzzles, progress) as T
    }
}
