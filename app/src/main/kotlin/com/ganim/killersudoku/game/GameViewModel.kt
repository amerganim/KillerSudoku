package com.ganim.killersudoku.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ganim.killersudoku.data.ProgressRepository
import com.ganim.killersudoku.data.SavedBoard
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.monetize.HintBank
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Immutable snapshot the UI draws from. */
class GameUi(
    val puzzle: KillerPuzzle,
    val title: String,
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
    val hintsUsed: Int,
    /** The wallet is empty and a hint is waiting: offer a rewarded ad (or the pack). */
    val hintNeedsTopUp: Boolean,
    val message: String?,
    val solved: Boolean,
    val outOfLives: Boolean,
)

/**
 * One puzzle being played.
 *
 * Built per puzzle by [Factory] with its saved board already loaded, so the board is
 * never briefly empty before the save lands. Everything the player does is written
 * through [ProgressRepository] straight away. The debounced autosave Nonogram started
 * with lost the last move when the process was killed.
 */
class GameViewModel(
    private val puzzleId: String,
    puzzle: KillerPuzzle,
    private val title: String,
    restored: SavedBoard?,
    private val dailyDate: LocalDate?,
    private val progress: ProgressRepository,
    /** Null means hints are free (previews). */
    private val monetization: HintBank? = null,
    /** Where the solver runs for a hint; injectable so tests stay on one thread. */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val session = GameSession(
        puzzle,
        values = restored?.values ?: IntArray(81),
        notes = restored?.notes ?: IntArray(81),
        mistakes = restored?.mistakes ?: 0,
        undo = restored?.undo.orEmpty(),
    )
    private val hints = HintProvider(puzzle)

    private var elapsedMs = restored?.elapsedMs ?: 0L
    private var selected = 40
    private var pencil = false
    private var autoNotes = false
    private var hint: Hint? = null
    private var hintsUsed = 0
    /** A hint already found but not yet paid for. */
    private var pendingHint: Hint? = null
    private var message: String? = null
    private var timer: Job? = null
    private var recordedCompletion = session.isSolved

    var ui: GameUi by mutableStateOf(snapshot())
        private set

    /**
     * The clock runs only while the screen is in front of the player. Before Nonogram
     * wired this up, answering one message mid-puzzle added a minute to your time.
     */
    fun onResume() {
        if (timer?.isActive == true) return
        timer = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive) {
                delay(1000)
                val now = System.nanoTime()
                if (!session.isSolved && !session.isOutOfLives) {
                    elapsedMs += (now - last) / 1_000_000
                    publish()
                }
                last = now
            }
        }
    }

    fun onPause() {
        timer?.cancel()
        timer = null
        save()
    }

    fun setAutoNotes(enabled: Boolean) {
        if (autoNotes == enabled) return
        autoNotes = enabled
        publish()
    }

    fun select(cell: Int) {
        selected = cell
        publish()
    }

    fun digit(d: Int) {
        if (session.isSolved || session.isOutOfLives) return
        hint = null
        message = null
        if (pencil && !autoNotes) {
            session.toggleNote(selected, d)
        } else if (session.place(selected, d) == GameSession.Entry.WRONG) {
            message = if (session.isOutOfLives) null else "$d doesn't go there"
        }
        afterMove()
    }

    fun erase() {
        session.erase(selected)
        afterMove()
    }

    fun undo() {
        session.undo()
        afterMove()
    }

    fun togglePencil() {
        pencil = !pencil
        publish()
    }

    /**
     * Finds the next certain step, then pays for it.
     *
     * Order matters, as in Nonogram: the board is checked *before* the wallet, so a player
     * is never charged a hint - or shown an ad - when there is nothing to reveal or a
     * wrong digit has to go first.
     */
    fun requestHint() {
        if (session.isSolved || session.isOutOfLives) return
        val wrong = hints.wrongCell(session.values)
        if (wrong != null) {
            selected = wrong
            message = "This digit is wrong. Clear it first."
            publish()
            return
        }
        viewModelScope.launch {
            val found = withContext(compute) { hints.next(session.values.copyOf()) } ?: return@launch
            val paid = monetization?.spendHint() ?: true
            if (paid) show(found) else {
                pendingHint = found
                publish()
            }
        }
    }

    /** A rewarded ad was watched (or had no fill, which grants anyway): earn one, spend it. */
    fun grantRewardedHint() {
        val found = pendingHint ?: return
        viewModelScope.launch {
            monetization?.grantHintFromAd()
            monetization?.spendHint()
            show(found)
        }
    }

    /** A hint pack was bought while the offer was open: the pending hint can now be paid for. */
    fun retryPendingHint() {
        val found = pendingHint ?: return
        viewModelScope.launch {
            if (monetization?.spendHint() != false) show(found)
        }
    }

    fun cancelTopUp() {
        pendingHint = null
        publish()
    }

    private fun show(found: Hint) {
        pendingHint = null
        hint = found
        selected = found.cell
        hintsUsed++
        publish()
    }

    /** Watching an ad restores one life, so an out-of-lives player can carry on. */
    fun restoreLife() {
        session.restoreLife()
        message = null
        publish()
        save()
    }

    /** Places the digit the current hint proved. */
    fun applyHint() {
        val h = hint ?: return
        selected = h.cell
        hint = null
        session.place(h.cell, h.digit)
        afterMove()
    }

    fun dismissHint() {
        hint = null
        publish()
    }

    /** Out of lives: start the same puzzle again from an empty board. */
    fun restart() {
        session.values.fill(0)
        session.notes.fill(0)
        session.resetMistakes()
        elapsedMs = 0
        hint = null
        message = null
        afterMove()
    }

    private fun afterMove() {
        publish()
        save()
        if (session.isSolved && !recordedCompletion) {
            recordedCompletion = true
            // Finishing a daily moves the streak; a ladder or free-play puzzle must not.
            val date = dailyDate ?: return
            viewModelScope.launch { progress.completeDaily(date, puzzleId) }
        }
    }

    private fun save() {
        val board = SavedBoard(
            values = session.values.copyOf(),
            notes = session.notes.copyOf(),
            mistakes = session.mistakes,
            elapsedMs = elapsedMs,
            undo = session.undoHistory,
        )
        val completed = session.isSolved
        // NonCancellable: the save issued from onPause must land even as the screen
        // (and its view model scope) is torn down.
        viewModelScope.launch(NonCancellable) { progress.save(puzzleId, board, completed) }
    }

    private fun publish() {
        ui = snapshot()
    }

    private fun snapshot() = GameUi(
        puzzle = session.puzzle,
        title = title,
        values = session.values.copyOf(),
        notes = if (autoNotes) session.autoCandidates() else session.notes.copyOf(),
        conflicts = session.conflicts(),
        selected = selected,
        pencil = pencil,
        autoNotes = autoNotes,
        mistakes = session.mistakes,
        remaining = IntArray(10) { if (it == 0) 0 else session.remaining(it) },
        canUndo = session.canUndo,
        elapsedSeconds = elapsedMs / 1000,
        hint = hint,
        hintsUsed = hintsUsed,
        hintNeedsTopUp = pendingHint != null,
        message = message,
        solved = session.isSolved,
        outOfLives = session.isOutOfLives,
    )

    class Factory(
        private val puzzleId: String,
        private val puzzle: KillerPuzzle,
        private val title: String,
        private val restored: SavedBoard?,
        private val dailyDate: LocalDate?,
        private val progress: ProgressRepository,
        private val monetization: HintBank?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameViewModel(puzzleId, puzzle, title, restored, dailyDate, progress, monetization) as T
    }
}
