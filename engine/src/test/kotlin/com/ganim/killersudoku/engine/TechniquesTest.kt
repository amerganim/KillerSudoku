package com.ganim.killersudoku.engine

import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.solver.CageCombination
import com.ganim.killersudoku.engine.solver.CageUnitElimination
import com.ganim.killersudoku.engine.solver.CandidateGrid
import com.ganim.killersudoku.engine.solver.HiddenSingle
import com.ganim.killersudoku.engine.solver.HiddenSubset
import com.ganim.killersudoku.engine.solver.IntersectionRemoval
import com.ganim.killersudoku.engine.solver.NakedSingle
import com.ganim.killersudoku.engine.solver.NakedSubset
import com.ganim.killersudoku.engine.solver.PuzzleIndex
import com.ganim.killersudoku.engine.solver.RuleOf45
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Hand-constructed positions, one per technique. */
class TechniquesTest {
    private fun digits(s: String) = s.fold(0) { a, c -> a or Digits.bit(c - '0') }

    /** Row 1 = 123456789, a valid canonical grid. */
    private val solution = IntArray(81) { i -> val r = i / 9; val c = i % 9; (r * 3 + r / 3 + c) % 9 + 1 }

    /** Horizontal pairs (0-1, 2-3, 4-5, 6-7) in every row, column 9 as singletons; [cages] override. */
    private fun index(vararg cages: IntArray): PuzzleIndex {
        val group = IntArray(81) { i -> val r = i / 9; val c = i % 9; if (c == 8) 1000 + r else r * 4 + c / 2 }
        cages.forEachIndexed { k, cells -> cells.forEach { group[it] = 2000 + k } }
        val ids = group.distinct()
        val cageOf = IntArray(81) { ids.indexOf(group[it]) }
        return PuzzleIndex(KillerPuzzle.fromLayout(0, solution, cageOf, Difficulty.EASY))
    }

    private val plain = index()

    @Test fun `naked single clears peers`() {
        val grid = CandidateGrid().apply { masks[40] = Digits.bit(5) }
        NakedSingle.apply(plain, grid).shouldNotBeNull()
        (listOf(36, 44, 4, 76, 30, 50).all { grid.masks[it] and Digits.bit(5) == 0 }) shouldBe true
        grid.masks[0] shouldBe Digits.ALL
    }

    @Test fun `hidden single in a row`() {
        val grid = CandidateGrid()
        (0..8).filter { it != 3 }.forEach { grid.eliminate(it, Digits.bit(5)) }
        HiddenSingle.apply(plain, grid).shouldNotBeNull()
        grid.masks[3] shouldBe Digits.bit(5)
    }

    @Test fun `cage combination restricts a 17 pair to 8 and 9`() {
        // Cells 7 and 8 of row 1 hold 8 and 9 in the canonical grid.
        val index = index(intArrayOf(7, 8))
        val grid = CandidateGrid()
        while (CageCombination.apply(index, grid) != null) {}
        grid.masks[7] shouldBe digits("89")
        grid.masks[8] shouldBe digits("89")
    }

    @Test fun `cage wholly inside a row eliminates its digits from the row`() {
        val index = index(intArrayOf(7, 8))
        val grid = CandidateGrid()
        while (CageCombination.apply(index, grid) != null) {}
        CageUnitElimination.apply(index, grid).shouldNotBeNull()
        // One digit per step; the second application removes the other.
        while (CageUnitElimination.apply(index, grid) != null) {}
        (0..6).all { grid.masks[it] and digits("89") == 0 } shouldBe true
    }

    @Test fun `rule of 45 fixes the single innie of a row`() {
        // Row 2's last cell (17) joins cell 8 of row 1, so row 1's innie is cell 8.
        val index = index(intArrayOf(8, 17))
        val grid = CandidateGrid()
        var step = RuleOf45(1).apply(index, grid)
        while (step != null && !grid.isSolved(8)) step = RuleOf45(1).apply(index, grid)
        grid.masks[8] shouldBe Digits.bit(solution[8])
    }

    @Test fun `naked pair`() {
        val grid = CandidateGrid().apply { masks[0] = digits("12"); masks[1] = digits("12") }
        NakedSubset.apply(plain, grid).shouldNotBeNull()
        (2..8).all { grid.masks[it] and digits("12") == 0 } shouldBe true
    }

    @Test fun `hidden pair`() {
        val grid = CandidateGrid()
        (2..8).forEach { grid.eliminate(it, digits("12")) }
        HiddenSubset.apply(plain, grid).shouldNotBeNull()
        grid.masks[0] shouldBe digits("12")
        grid.masks[1] shouldBe digits("12")
    }

    @Test fun `pointing pair removes a digit from the rest of the row`() {
        val grid = CandidateGrid()
        listOf(9, 10, 11, 18, 19, 20).forEach { grid.eliminate(it, Digits.bit(7)) }
        IntersectionRemoval.apply(plain, grid).shouldNotBeNull()
        (3..8).all { grid.masks[it] and Digits.bit(7) == 0 } shouldBe true
        grid.masks[0] shouldBe Digits.ALL
    }
}
