package com.ganim.killersudoku.progression

import com.ganim.killersudoku.data.PuzzleEntry
import com.ganim.killersudoku.data.PuzzleRepository
import com.ganim.killersudoku.engine.model.Difficulty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

/**
 * The ladder is the first thing a new player sees, so this checks what would confuse one:
 * a ramp that goes backwards, a level number that skips, a "next" pointing at something
 * already solved. Ported from Nonogram; difficulty replaces grid size as the ramp.
 */
class LevelLadderTest {

    @Test
    fun `stages come out in the intended order`() {
        LevelLadder.build(fakePack()).map { it.stage } shouldBe LevelLadder.Stage.entries.toList()
    }

    @Test
    @DisplayName("the ramp never goes backwards: difficulty never drops down the ladder")
    fun `difficulty never drops`() {
        LevelLadder.allLevels(LevelLadder.build(fakePack())).zipWithNext { a, b ->
            assertTrue(b.difficulty >= a.difficulty) {
                "level ${b.number} is ${b.difficulty}, easier than level ${a.number} at ${a.difficulty}"
            }
        }
    }

    @Test
    fun `the warm-up is the gentlest end of the easy pool`() {
        val pack = fakePack()
        val warm = LevelLadder.build(pack).first().levels.map { it.entry }
        val easyRest = pack.filter { it.difficulty == Difficulty.EASY && it !in warm }
        assertTrue(warm.minOf { it.cageCount } >= easyRest.maxOf { it.cageCount }) {
            "an easy puzzle with more (smaller) cages was left out of the warm-up"
        }
    }

    @Test
    fun `level numbers run 1 to n with no gaps`() {
        val stages = LevelLadder.build(fakePack())
        LevelLadder.allLevels(stages).map { it.number } shouldBe (1..LevelLadder.allLevels(stages).size).toList()
        stages.forEach { stage -> stage.levels.map { it.numberInStage } shouldBe (1..stage.levels.size).toList() }
    }

    @Test
    fun `no puzzle appears twice`() {
        val ids = LevelLadder.allLevels(LevelLadder.build(fakePack())).map { it.id }
        assertEquals(ids.size, ids.toSet().size, "the same puzzle is two different levels")
    }

    @Test
    @DisplayName("more than forty levels come before the first medium one")
    fun `the easy end is long`() {
        val firstMedium = LevelLadder.allLevels(LevelLadder.build(fakePack())).first { it.stage == LevelLadder.Stage.MEDIUM }
        assertTrue(firstMedium.number > 40) { "only ${firstMedium.number - 1} levels before Medium" }
    }

    @Test
    fun `next is the first unsolved level, even when later ones are done`() {
        val stages = LevelLadder.build(fakePack())
        val levels = LevelLadder.allLevels(stages)
        LevelLadder.next(stages, emptySet())?.number shouldBe 1
        LevelLadder.next(stages, levels.filter { it.number != 3 }.map { it.id }.toSet())?.number shouldBe 3
        LevelLadder.next(stages, levels.map { it.id }.toSet()) shouldBe null
    }

    @Test
    @DisplayName("a pool too small to fill its quota shortens the stage instead of failing")
    fun `a thin pack degrades gracefully`() {
        val stages = LevelLadder.build(List(3) { entry(it, Difficulty.EASY, 30) })
        stages.first().levels.size shouldBe 3
        assertTrue(stages.drop(1).all { it.levels.isEmpty() })
        LevelLadder.next(stages, emptySet())?.number shouldBe 1
    }

    @Test
    fun `the ladder is stable across rebuilds`() {
        LevelLadder.allLevels(LevelLadder.build(fakePack())).map { it.id } shouldBe
            LevelLadder.allLevels(LevelLadder.build(fakePack())).map { it.id }
    }

    // --- against the real bundled pack -----------------------------------------------

    private val realPack = File("src/main/assets/${PuzzleRepository.ASSET}")
    private val repo by lazy { PuzzleRepository { realPack.readBytes() } }

    @Test
    fun `the real pack fills every stage`() {
        val stages = repo.ladder
        stages.map { it.levels.size } shouldBe listOf(LevelLadder.WARM_UP_COUNT, 30, 30, 24, 20)
    }

    @Test
    @DisplayName("a daily is never a ladder level, so it never repeats one the player finished")
    fun `dailies avoid the ladder`() {
        val ladderIds = LevelLadder.allLevels(repo.ladder).map { it.id }.toSet()
        val start = LocalDate.of(2026, 1, 1)
        repeat(730) { day ->
            val id = repo.dailyIdFor(start.plusDays(day.toLong()))!!
            assertTrue(id !in ladderIds) { "the daily on ${start.plusDays(day.toLong())} is a ladder level" }
        }
    }

    @Test
    fun `a daily matches its weekday's difficulty`() {
        val start = LocalDate.of(2026, 3, 2) // a Monday
        repeat(14) { day ->
            val date = start.plusDays(day.toLong())
            val entry = repo.entry(repo.dailyIdFor(date)!!)!!
            entry.difficulty shouldBe com.ganim.killersudoku.daily.DailySchedule.specFor(date).difficulty
        }
    }

    @Test
    fun `puzzle ids are unique across the whole pack`() {
        repo.entries.map { it.id }.toSet().size shouldBe repo.count
    }

    // --- helpers ---------------------------------------------------------------------

    private fun entry(index: Int, difficulty: Difficulty, cages: Int) = PuzzleEntry(index, "p$index", difficulty, cages)

    /** Shaped like the shipped pack, at a tenth of the size. */
    private fun fakePack(): List<PuzzleEntry> {
        var index = 0
        fun run(count: Int, difficulty: Difficulty) = List(count) { entry(index++, difficulty, 24 + (index * 7) % 10) }
        return run(150, Difficulty.EASY) + run(150, Difficulty.MEDIUM) + run(125, Difficulty.HARD) + run(75, Difficulty.EXPERT)
    }
}
