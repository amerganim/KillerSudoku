package com.ganim.killersudoku.engine

import com.ganim.killersudoku.engine.combos.DigitCombos
import com.ganim.killersudoku.engine.model.Digits
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DigitCombosTest {
    private fun digits(s: String) = s.fold(0) { a, c -> a or Digits.bit(c - '0') }

    @Test fun `enumerates exactly 511 subsets`() {
        DigitCombos.subsetCount shouldBe 511
        (1..9).sumOf { size -> (0..45).sumOf { DigitCombos.masksFor(size, it).size } } shouldBe 511
    }

    @Test fun `unique combinations`() {
        DigitCombos.masksFor(2, 17).toList() shouldBe listOf(digits("89"))
        DigitCombos.masksFor(2, 3).toList() shouldBe listOf(digits("12"))
        DigitCombos.masksFor(3, 24).toList() shouldBe listOf(digits("789"))
        DigitCombos.masksFor(9, 45).toList() shouldBe listOf(Digits.ALL)
    }

    @Test fun `possible and required digits`() {
        DigitCombos.possibleDigits(2, 10) shouldBe digits("12346789")
        DigitCombos.requiredDigits(4, 10) shouldBe digits("1234")
        DigitCombos.requiredDigits(3, 7) shouldBe digits("124")
        DigitCombos.requiredDigits(2, 10) shouldBe 0
        DigitCombos.possibleDigits(1, 5) shouldBe digits("5")
    }

    @Test fun `out of range is empty, not an exception`() {
        DigitCombos.masksFor(2, 2).size shouldBe 0
        DigitCombos.masksFor(2, 18).size shouldBe 0
        DigitCombos.masksFor(0, 0).size shouldBe 0
        DigitCombos.masksFor(10, 45).size shouldBe 0
        DigitCombos.possibleDigits(3, 99) shouldBe 0
        DigitCombos.requiredDigits(3, 5) shouldBe 0
    }

    @Test fun `digits are spoken as a player would say them`() {
        Digits.spoken(digits("7"), "and") shouldBe "7"
        Digits.spoken(digits("39"), "and") shouldBe "3 and 9"
        Digits.spoken(digits("13456"), "or") shouldBe "1, 3, 4, 5 or 6"
    }
}