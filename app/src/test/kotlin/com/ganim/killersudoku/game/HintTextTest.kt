package com.ganim.killersudoku.game

import com.ganim.killersudoku.data.PuzzleRepository
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.progression.LevelLadder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The hint is the listing's headline feature, so its wording is held to a standard, over
 * every hint of real ladder puzzles solved start to finish.
 *
 * A first draft said "the 2-cell cage of 12 can only use 39" and "can only use 3456789":
 * true, and unreadable. These checks are what stop that coming back.
 */
class HintTextTest {

    private val repo = PuzzleRepository { File("src/main/assets/${PuzzleRepository.ASSET}").readBytes() }

    @Test
    @DisplayName("every hint names its cell, states its digit, and reads as a sentence")
    fun `hint wording`() {
        var checked = 0
        val levels = LevelLadder.allLevels(repo.ladder).filter { it.number % 8 == 1 }
        for (level in levels) {
            val p = repo.puzzleById(level.id)!!
            val s = GameSession(p)
            val hints = HintProvider(p)
            while (!s.isSolved) {
                val h = hints.next(s.values)!!
                val text = h.explanation
                val where = "level ${level.number}: \"$text\""
                assertTrue(Geometry.cellName(h.cell) in text) { "does not say which cell - $where" }
                assertTrue(Regex("""\b${h.digit}\b""").containsMatchIn(text)) { "does not say the digit - $where" }
                assertTrue(!Regex("""\d{3,}""").containsMatchIn(text)) { "digits run together - $where" }
                assertTrue(text.endsWith(".") && (text.first().isUpperCase() || text.first().isDigit())) { "not a sentence - $where" }
                s.place(h.cell, h.digit)
                checked++
            }
        }
        assertTrue(checked > 1000) { "only $checked hints checked" }
    }
}
