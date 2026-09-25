package com.ganim.killersudoku.game

import com.ganim.killersudoku.engine.generator.KillerGenerator
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GameSessionTest {
    companion object {
        private val puzzles: List<KillerPuzzle> by lazy {
            val generator = KillerGenerator()
            val rng = Random(3)
            generateSequence { generator.attempt(rng, KillerGenerator.shapeFor(Difficulty.HARD))?.puzzle }
                .take(6).toList()
        }
    }

    private val puzzle get() = puzzles.first()
    private fun wrongDigit(cell: Int) = if (puzzle.solution[cell] == 9) 1 else puzzle.solution[cell] + 1

    @Test fun `a wrong digit costs a life and stays visible`() {
        val s = GameSession(puzzle)
        s.place(0, wrongDigit(0)) shouldBe GameSession.Entry.WRONG
        s.mistakes shouldBe 1
        s.values[0] shouldBe wrongDigit(0)
        s.place(1, wrongDigit(1))
        s.place(2, wrongDigit(2))
        s.isOutOfLives shouldBe true
    }

    @Test fun `a correct digit clears that pencil mark from its peers`() {
        val s = GameSession(puzzle)
        val d = puzzle.solution[0]
        s.toggleNote(1, d)
        s.toggleNote(80, d)
        s.place(0, d) shouldBe GameSession.Entry.CORRECT
        s.notes[1] and Digits.bit(d) shouldBe 0
        s.notes[80] and Digits.bit(d) shouldBe Digits.bit(d)
    }

    @Test fun `undo restores digits and notes but not lives`() {
        val s = GameSession(puzzle)
        s.toggleNote(5, 3)
        s.place(0, wrongDigit(0))
        s.undo() shouldBe true
        s.values[0] shouldBe 0
        s.mistakes shouldBe 1
        s.undo() shouldBe true
        s.notes[5] shouldBe 0
        s.undo() shouldBe false
    }

    @Test fun `undo keeps at least 100 steps`() {
        val s = GameSession(puzzle)
        repeat(150) { s.toggleNote(it % 81, 1 + it % 9) }
        var n = 0
        while (s.undo()) n++
        (n >= 100) shouldBe true
    }

    @Test fun `correct digits are locked`() {
        val s = GameSession(puzzle)
        s.place(0, puzzle.solution[0])
        s.erase(0)
        s.values[0] shouldBe puzzle.solution[0]
    }

    @Test fun `duplicate digits in a house are conflicts`() {
        val s = GameSession(puzzle)
        val a = 0
        val b = Geometry.houses[0].first { puzzle.cageOf[it] != puzzle.cageOf[a] && it != a }
        s.place(a, 5)
        s.place(b, 5)
        s.conflicts()[a] shouldBe true
        s.conflicts()[b] shouldBe true
    }

    @Test fun `following hints alone solves every puzzle with correct digits`() {
        for (p in puzzles) {
            val s = GameSession(p)
            val hints = HintProvider(p)
            while (!s.isSolved) {
                val hint = hints.next(s.values).shouldNotBeNull()
                hint.digit shouldBe p.solution[hint.cell]
                s.values[hint.cell] shouldBe 0
                s.place(hint.cell, hint.digit) shouldBe GameSession.Entry.CORRECT
            }
            s.mistakes shouldBe 0
        }
    }

    @Test fun `hint points out a wrong digit first`() {
        val s = GameSession(puzzle)
        s.place(7, wrongDigit(7))
        HintProvider(puzzle).wrongCell(s.values) shouldBe 7
    }
}
