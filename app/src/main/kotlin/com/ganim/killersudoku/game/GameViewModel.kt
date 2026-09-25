package com.ganim.killersudoku.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ganim.killersudoku.data.PuzzleRepository
import com.ganim.killersudoku.data.SavedGame
import com.ganim.killersudoku.data.SavedGameStore
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.KillerPuzzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Immutable snapshot the UI draws from. */
class GameUi(
    val puzzle: KillerPuzzle,
    val values: IntArray,
    val notes: IntArray,
    val conflicts: BooleanArray,
    val selected: Int,
    val pencil: Boolean,
    val autoNotes: Boolean,
    val mistakes: Int,
    val remaining: IntArray,
    val canUndo: Boolean,
    val elapsedSeconds: Long,
    val hint: Hint?,
    val message: String?,
    val solved: Boolean,
    val outOfLives: Boolean,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PuzzleRepository(app)
    private val store = SavedGameStore(app)

    private var session: GameSession? = null
    private var puzzleIndex = -1
    private var hints: HintProvider? = null
    private var selected = 40
    private var pencil = false
    private var autoNotes = false
    private var elapsed = 0L
    private var hint: Hint? = null
    private var message: String? = null
    private var timer: Job? = null

    var ui: GameUi? by mutableStateOf(null)
        private set

    val hasSavedGame: Boolean get() = store.load() != null

    fun newGame(difficulty: Difficulty) {
        val index = repository.randomIndex(difficulty)
        start(index, GameSession(repository.puzzle(index)), 0)
    }

    fun resume(): Boolean {
        val saved = store.load() ?: return false
        val puzzle = runCatching { repository.puzzle(saved.puzzleIndex) }.getOrNull() ?: return false
        start(saved.puzzleIndex, GameSession(puzzle, saved.values, saved.notes, saved.mistakes), saved.elapsedSeconds)
        return true
    }

    private fun start(index: Int, s: GameSession, elapsedSeconds: Long) {
        session = s
        puzzleIndex = index
        hints = HintProvider(s.puzzle)
        selected = 40
        pencil = false
        elapsed = elapsedSeconds
        hint = null
        message = null
        publish()
        timer?.cancel()
        timer = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val cur = session ?: continue
                if (!cur.isSolved && !cur.isOutOfLives) {
                    elapsed++
                    publish(save = elapsed % 5 == 0L)
                }
            }
        }
    }

    fun select(cell: Int) {
        selected = cell
        publish(save = false)
    }

    fun digit(d: Int) {
        val s = session ?: return
        if (s.isSolved || s.isOutOfLives) return
        hint = null
        message = null
        if (pencil && !autoNotes) {
            s.toggleNote(selected, d)
        } else if (s.place(selected, d) == GameSession.Entry.WRONG) {
            message = if (s.isOutOfLives) null else "$d doesn't go there"
        }
        if (s.isSolved) store.clear()
        publish(save = !s.isSolved)
    }

    fun erase() {
        session?.erase(selected)
        publish()
    }

    fun undo() {
        session?.undo()
        publish()
    }

    fun togglePencil() {
        pencil = !pencil
        publish(save = false)
    }

    fun toggleAutoNotes() {
        autoNotes = !autoNotes
        publish(save = false)
    }

    fun requestHint() {
        val s = session ?: return
        val provider = hints ?: return
        viewModelScope.launch {
            val wrong = provider.wrongCell(s.values)
            if (wrong != null) {
                selected = wrong
                message = "This digit is wrong - clear it first."
                publish(save = false)
                return@launch
            }
            val found = withContext(Dispatchers.Default) { provider.next(s.values.copyOf()) }
            hint = found
            if (found != null) selected = found.cell
            publish(save = false)
        }
    }

    /** Places the digit the current hint proved. */
    fun applyHint() {
        val h = hint ?: return
        selected = h.cell
        hint = null
        session?.place(h.cell, h.digit)
        publish()
    }

    fun dismissHint() {
        hint = null
        publish(save = false)
    }

    fun restart() {
        val s = session ?: return
        start(puzzleIndex, GameSession(s.puzzle), 0)
    }

    private fun publish(save: Boolean = true) {
        val s = session ?: return
        if (save && !s.isSolved) {
            store.save(SavedGame(puzzleIndex, s.values.copyOf(), s.notes.copyOf(), s.mistakes, elapsed))
        }
        ui = GameUi(
            puzzle = s.puzzle,
            values = s.values.copyOf(),
            notes = if (autoNotes) s.autoCandidates() else s.notes.copyOf(),
            conflicts = s.conflicts(),
            selected = selected,
            pencil = pencil,
            autoNotes = autoNotes,
            mistakes = s.mistakes,
            remaining = IntArray(10) { if (it == 0) 0 else s.remaining(it) },
            canUndo = s.canUndo,
            elapsedSeconds = elapsed,
            hint = hint,
            message = message,
            solved = s.isSolved,
            outOfLives = s.isOutOfLives,
        )
    }
}
