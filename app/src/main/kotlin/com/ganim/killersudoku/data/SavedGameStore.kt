package com.ganim.killersudoku.data

import android.content.Context
import androidx.core.content.edit

/** The in-progress game, so it survives process death (build plan Phase 2). */
class SavedGame(
    val puzzleIndex: Int,
    val values: IntArray,
    val notes: IntArray,
    val mistakes: Int,
    val elapsedSeconds: Long,
)

class SavedGameStore(context: Context) {
    private val prefs = context.getSharedPreferences("saved_game", Context.MODE_PRIVATE)

    fun load(): SavedGame? {
        val index = prefs.getInt(KEY_INDEX, -1)
        if (index < 0) return null
        return runCatching {
            SavedGame(
                puzzleIndex = index,
                values = decode(prefs.getString(KEY_VALUES, null)!!),
                notes = decode(prefs.getString(KEY_NOTES, null)!!),
                mistakes = prefs.getInt(KEY_MISTAKES, 0),
                elapsedSeconds = prefs.getLong(KEY_ELAPSED, 0),
            )
        }.getOrNull()
    }

    fun save(game: SavedGame) = prefs.edit {
        putInt(KEY_INDEX, game.puzzleIndex)
        putString(KEY_VALUES, game.values.joinToString(","))
        putString(KEY_NOTES, game.notes.joinToString(","))
        putInt(KEY_MISTAKES, game.mistakes)
        putLong(KEY_ELAPSED, game.elapsedSeconds)
    }

    fun clear() = prefs.edit { clear() }

    private fun decode(s: String): IntArray = s.split(',').map { it.toInt() }.toIntArray().also { require(it.size == 81) }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_VALUES = "values"
        const val KEY_NOTES = "notes"
        const val KEY_MISTAKES = "mistakes"
        const val KEY_ELAPSED = "elapsed"
    }
}
