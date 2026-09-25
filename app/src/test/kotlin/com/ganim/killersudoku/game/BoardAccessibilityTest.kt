package com.ganim.killersudoku.game

import com.ganim.killersudoku.engine.generator.KillerGenerator
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.Digits
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** What TalkBack says for a cell: position, the cage clue, then the contents. */
class BoardAccessibilityTest {

    private val puzzle = generateSequence(Random(5).let { rng ->
        { KillerGenerator().attempt(rng, KillerGenerator.shapeFor(Difficulty.EASY))?.puzzle }
    }).first()

    private fun ui(values: IntArray = IntArray(81), notes: IntArray = IntArray(81)): GameUi {
        val s = GameSession(puzzle, values, notes)
        return GameUi(
            puzzle, "t", s.values, s.notes, s.conflicts(), 0, false, false, 0, IntArray(10), false, 0, null, 0, false, null,
            solved = false, outOfLives = false,
        )
    }

    private fun cageText(c: Int): String {
        val cage = puzzle.cages[puzzle.cageOf[c]]
        return if (cage.size == 1) "single cell cage of ${cage.sum}" else "cage of ${cage.sum}, ${cage.size} cells"
    }

    @Test
    fun `an empty cell names its position and its cage clue`() {
        describeCell(ui(), 22) shouldBe "Row 3, column 5, ${cageText(22)}, empty"
    }

    @Test
    fun `pencil marks are read out`() {
        val notes = IntArray(81).also { it[0] = Digits.bit(1) or Digits.bit(4) or Digits.bit(9) }
        describeCell(ui(notes = notes), 0) shouldBe "Row 1, column 1, ${cageText(0)}, empty, notes 1 4 9"
    }

    @Test
    fun `a correct digit is just the digit, a wrong one says so`() {
        val right = IntArray(81).also { it[40] = puzzle.solution[40] }
        describeCell(ui(values = right), 40) shouldBe "Row 5, column 5, ${cageText(40)}, ${puzzle.solution[40]}"

        val wrongDigit = if (puzzle.solution[40] == 9) 1 else puzzle.solution[40] + 1
        val wrong = IntArray(81).also { it[40] = wrongDigit }
        describeCell(ui(values = wrong), 40) shouldStartWith "Row 5, column 5"
        describeCell(ui(values = wrong), 40).endsWith("$wrongDigit, wrong") shouldBe true
    }
}
