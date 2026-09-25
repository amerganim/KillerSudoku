package com.ganim.killersudoku.game

import com.ganim.killersudoku.daily.GameClock
import com.ganim.killersudoku.data.FakeDailyRecordDao
import com.ganim.killersudoku.data.FakePuzzleProgressDao
import com.ganim.killersudoku.data.FakeUserStatsDao
import com.ganim.killersudoku.data.ProgressRepository
import com.ganim.killersudoku.engine.generator.KillerGenerator
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.monetize.HintBank
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * The money rules around a hint and a life, and which completions move the streak.
 * These are the places where a bug either costs a player something they paid for or
 * breaks the "never charged for nothing" promise, so they are pinned here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 3, 4)
    private val dailyDao = FakeDailyRecordDao()
    private val progress = ProgressRepository(FakePuzzleProgressDao(), dailyDao, FakeUserStatsDao(), GameClock { today })

    /** A wallet that counts what was taken from it. */
    private class FakeBank(var hints: Int) : HintBank {
        var spent = 0
        var granted = 0
        override suspend fun spendHint(): Boolean = (hints > 0).also { if (it) { hints--; spent++ } }
        override suspend fun grantHintFromAd() { hints++; granted++ }
    }

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun model(bank: HintBank?, date: LocalDate? = null) =
        GameViewModel("p1", puzzle, "Test", restored = null, dailyDate = date, progress = progress, monetization = bank, compute = dispatcher)

    private fun wrongDigit(cell: Int) = if (puzzle.solution[cell] == 9) 1 else puzzle.solution[cell] + 1

    private fun TestScope.solveAll(vm: GameViewModel) {
        for (c in 0 until 81) { vm.select(c); vm.digit(puzzle.solution[c]) }
        advanceUntilIdle()
    }

    @Test
    fun `a hint with hints in the wallet costs exactly one`() = runTest(dispatcher) {
        val bank = FakeBank(3)
        val vm = model(bank)
        vm.requestHint(); advanceUntilIdle()
        vm.ui.hint.shouldNotBeNull()
        bank.spent shouldBe 1
    }

    @Test
    @DisplayName("an empty wallet shows no hint and charges nothing, then offers a top-up")
    fun `empty wallet offers top-up`() = runTest(dispatcher) {
        val bank = FakeBank(0)
        val vm = model(bank)
        vm.requestHint(); advanceUntilIdle()
        vm.ui.hint.shouldBeNull()
        vm.ui.hintNeedsTopUp shouldBe true
        bank.spent shouldBe 0

        vm.grantRewardedHint(); advanceUntilIdle()
        vm.ui.hint.shouldNotBeNull()
        vm.ui.hintNeedsTopUp shouldBe false
        bank.granted shouldBe 1
        bank.spent shouldBe 1
        bank.hints shouldBe 0
    }

    @Test
    @DisplayName("one rewarded video is one hint, even when the wallet refresh triggers a retry")
    fun `reward and retry do not double count`() = runTest(dispatcher) {
        // On the A15 the grant bumped the wallet, the screen's retry saw a waiting hint and
        // paid for it as well, and the results card counted two hints for one video.
        val bank = FakeBank(0)
        val vm = model(bank)
        vm.requestHint(); advanceUntilIdle()
        vm.grantRewardedHint()
        vm.retryPendingHint() // what the screen does when the wallet count goes up
        advanceUntilIdle()
        vm.ui.hint.shouldNotBeNull()
        vm.ui.hintsUsed shouldBe 1
        bank.granted shouldBe 1
        bank.spent shouldBe 1
    }

    @Test
    fun `declining the top-up leaves the wallet untouched`() = runTest(dispatcher) {
        val bank = FakeBank(0)
        val vm = model(bank)
        vm.requestHint(); advanceUntilIdle()
        vm.cancelTopUp()
        vm.ui.hintNeedsTopUp shouldBe false
        bank.spent shouldBe 0
        bank.granted shouldBe 0
    }

    @Test
    @DisplayName("a wrong digit on the board is pointed out for free, before any charge")
    fun `wrong digit is free`() = runTest(dispatcher) {
        val bank = FakeBank(3)
        val vm = model(bank)
        vm.select(10); vm.digit(wrongDigit(10))
        vm.requestHint(); advanceUntilIdle()
        vm.ui.hint.shouldBeNull()
        vm.ui.selected shouldBe 10
        bank.spent shouldBe 0
    }

    @Test
    fun `a solved puzzle is never charged for a hint`() = runTest(dispatcher) {
        val bank = FakeBank(3)
        val vm = model(bank)
        solveAll(vm)
        vm.requestHint(); advanceUntilIdle()
        bank.spent shouldBe 0
    }

    @Test
    fun `a pack bought during the offer pays for the waiting hint`() = runTest(dispatcher) {
        val bank = FakeBank(0)
        val vm = model(bank)
        vm.requestHint(); advanceUntilIdle()
        bank.hints = 25 // the purchase landed
        vm.retryPendingHint(); advanceUntilIdle()
        vm.ui.hint.shouldNotBeNull()
        bank.hints shouldBe 24
    }

    @Test
    fun `watching for a life brings an out-of-lives player back`() = runTest(dispatcher) {
        val vm = model(FakeBank(3))
        listOf(1, 2, 3).forEach { c -> vm.select(c); vm.digit(wrongDigit(c)) }
        vm.ui.outOfLives shouldBe true
        vm.restoreLife()
        vm.ui.outOfLives shouldBe false
        vm.ui.mistakes shouldBe 2
    }

    @Test
    fun `finishing a daily moves the streak`() = runTest(dispatcher) {
        val vm = model(FakeBank(3), date = today)
        solveAll(vm)
        progress.dailyRecord(today)!!.completed shouldBe true
        progress.loadStats().currentStreak shouldBe 1
    }

    @Test
    fun `finishing a ladder or free-play puzzle does not`() = runTest(dispatcher) {
        val vm = model(FakeBank(3), date = null)
        solveAll(vm)
        progress.loadStats().currentStreak shouldBe 0
        progress.loadInProgress("p1").shouldBeNull() // saved as completed, snapshot dropped
    }

    @Test
    fun `every move is saved, so a killed process loses nothing`() = runTest(dispatcher) {
        val vm = model(FakeBank(3))
        vm.select(0); vm.digit(puzzle.solution[0])
        vm.select(1); vm.togglePencil(); vm.digit(4)
        advanceUntilIdle()
        val saved = progress.loadInProgress("p1").shouldNotBeNull()
        saved.values[0] shouldBe puzzle.solution[0]
        saved.notes[1] shouldBe (1 shl 4)
        saved.undo.size shouldBe 2
    }

    companion object {
        private val puzzle: KillerPuzzle by lazy {
            val rng = Random(11)
            val generator = KillerGenerator()
            generateSequence { generator.attempt(rng, KillerGenerator.shapeFor(Difficulty.EASY))?.puzzle }.first()
        }
    }
}
