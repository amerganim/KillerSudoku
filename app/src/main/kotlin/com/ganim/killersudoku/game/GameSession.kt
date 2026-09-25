package com.ganim.killersudoku.game

import com.ganim.killersudoku.engine.combos.DigitCombos
import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle

/**
 * The player's board: pen digits, pencil marks, lives and undo. No Android types, so it
 * is unit-tested on the JVM.
 */
class GameSession(
    val puzzle: KillerPuzzle,
    val values: IntArray = IntArray(Geometry.CELLS),
    val notes: IntArray = IntArray(Geometry.CELLS),
    mistakes: Int = 0,
) {
    var mistakes: Int = mistakes
        private set

    private val undoStack = ArrayDeque<Snapshot>()

    private class Snapshot(val values: IntArray, val notes: IntArray)

    enum class Entry { CORRECT, WRONG, IGNORED }

    val isSolved: Boolean get() = values.contentEquals(puzzle.solution)

    val isOutOfLives: Boolean get() = mistakes >= MAX_MISTAKES

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Pen entry. A digit that differs from the solution costs a life but stays visible. */
    fun place(cell: Int, digit: Int): Entry {
        if (values[cell] == digit || isLocked(cell)) return Entry.IGNORED
        pushUndo()
        values[cell] = digit
        notes[cell] = 0
        if (digit != puzzle.solution[cell]) {
            mistakes++
            return Entry.WRONG
        }
        // Tidy pencil marks the new digit rules out.
        val bit = Digits.bit(digit)
        for (p in peersOf(cell)) notes[p] = notes[p] and bit.inv()
        return Entry.CORRECT
    }

    fun toggleNote(cell: Int, digit: Int) {
        if (values[cell] != 0) return
        pushUndo()
        notes[cell] = notes[cell] xor Digits.bit(digit)
    }

    fun erase(cell: Int) {
        if (isLocked(cell) || (values[cell] == 0 && notes[cell] == 0)) return
        pushUndo()
        values[cell] = 0
        notes[cell] = 0
    }

    /** Correct digits are final: erasing them only invites a second mistake. */
    fun isLocked(cell: Int): Boolean = values[cell] != 0 && values[cell] == puzzle.solution[cell]

    fun undo(): Boolean {
        val s = undoStack.removeLastOrNull() ?: return false
        s.values.copyInto(values)
        s.notes.copyInto(notes)
        return true
    }

    /** Cells whose digit repeats within a row, column, box or cage. */
    fun conflicts(): BooleanArray {
        val out = BooleanArray(Geometry.CELLS)
        fun mark(group: IntArray) {
            for (i in group.indices) for (j in i + 1 until group.size) {
                val a = group[i]
                val b = group[j]
                if (values[a] != 0 && values[a] == values[b]) { out[a] = true; out[b] = true }
            }
        }
        Geometry.houses.forEach(::mark)
        puzzle.cages.forEach { cage ->
            mark(cage.cells)
            // A full cage with the wrong total is also a conflict.
            if (cage.cells.all { values[it] != 0 } && cage.cells.sumOf { values[it] } != cage.sum) {
                cage.cells.forEach { out[it] = true }
            }
        }
        return out
    }

    /** Candidates for auto-notes: the cage's possible digits minus digits already placed nearby. */
    fun autoCandidates(): IntArray = IntArray(Geometry.CELLS) { c ->
        if (values[c] != 0) return@IntArray 0
        val cage = puzzle.cages[puzzle.cageOf[c]]
        var mask = DigitCombos.possibleDigits(cage.size, cage.sum)
        for (p in peersOf(c)) if (values[p] != 0) mask = mask and Digits.bit(values[p]).inv()
        mask
    }

    fun remaining(digit: Int): Int = 9 - values.indices.count { values[it] == digit && values[it] == puzzle.solution[it] }

    private fun peersOf(cell: Int): List<Int> =
        Geometry.peers[cell].asList() + puzzle.cages[puzzle.cageOf[cell]].cells.filter { it != cell }

    private fun pushUndo() {
        undoStack.addLast(Snapshot(values.copyOf(), notes.copyOf()))
        if (undoStack.size > UNDO_DEPTH) undoStack.removeFirst()
    }

    companion object {
        const val MAX_MISTAKES = 3

        /** Build plan 6: at least 100 steps. */
        const val UNDO_DEPTH = 200
    }
}
