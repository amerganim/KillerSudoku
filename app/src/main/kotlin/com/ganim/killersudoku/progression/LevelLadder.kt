package com.ganim.killersudoku.progression

import com.ganim.killersudoku.data.PuzzleEntry
import com.ganim.killersudoku.engine.model.Difficulty

/**
 * The ordered path through the game, lifted from Nonogram.
 *
 * Nonogram's first tester could not tell where the game started or what "daily" meant.
 * Both were the home screen opening on a concept instead of an action. "Level 1" needs
 * no explanation, so the app opens on a ladder.
 *
 * ## Guided, not gated
 *
 * Nothing is locked. A beginner stuck on level 9 can go elsewhere, and someone who
 * already plays killer sudoku is not made to grind through the warm-up. The [next] level
 * is one tap away; the rest of the ladder is visible below it.
 *
 * ## How the stages are filled
 *
 * Every killer here is 9x9, so the ramp is difficulty alone (Nonogram's ramp was grid
 * size and band together). The warm-up is the gentlest end of the easy pool: the easy
 * puzzles with the most cages. More cages means smaller cages, and a 2-cell cage of 3 or
 * 17 has only one combination, so those are the puzzles where a newcomer learns how
 * cage sums work before being asked to use the rule of 45 in anger.
 *
 * Pack order is deterministic, so level 7 is the same puzzle on every device.
 */
object LevelLadder {

    enum class Stage(val title: String, val blurb: String) {
        WARM_UP("Warm up", "Lots of small cages. Learn how the sums fit together."),
        EASY("Easy", "Fewer, larger cages. The rule of 45 starts to matter."),
        MEDIUM("Medium", "Cages that must share a digit with a row or box."),
        HARD("Hard", "Pairs, triples and pointing. Pencil marks earn their keep."),
        EXPERT("Expert", "Adding up whole rows at once. These take a while."),
    }

    data class Level(
        /** 1-based, across the whole ladder, so the home screen can say "Level 43". */
        val number: Int,
        /** 1-based within its stage, which is what the tile shows. */
        val numberInStage: Int,
        val stage: Stage,
        val entry: PuzzleEntry,
    ) {
        val id: String get() = entry.id
        val difficulty: Difficulty get() = entry.difficulty
    }

    data class StageLevels(val stage: Stage, val levels: List<Level>)

    const val WARM_UP_COUNT = 12

    private val quotas = listOf(
        Stage.EASY to 30,
        Stage.MEDIUM to 30,
        Stage.HARD to 24,
        Stage.EXPERT to 20,
    )

    /**
     * Builds the ladder from the pack index. A pool that cannot fill its quota
     * contributes what it has: a regenerated pack with a different mix shortens the
     * ladder rather than crashing the home screen.
     */
    fun build(entries: List<PuzzleEntry>): List<StageLevels> {
        val byDifficulty = entries.groupBy { it.difficulty }
        val easy = byDifficulty[Difficulty.EASY].orEmpty()
        // Stable sort: ties keep pack order, so the pick is deterministic.
        val warmUp = easy.sortedByDescending { it.cageCount }.take(WARM_UP_COUNT)
        val warmIds = warmUp.map { it.id }.toSet()

        val picks = listOf(Stage.WARM_UP to warmUp) + quotas.map { (stage, quota) ->
            val pool = when (stage) {
                Stage.EASY -> easy.filter { it.id !in warmIds }
                Stage.MEDIUM -> byDifficulty[Difficulty.MEDIUM].orEmpty()
                Stage.HARD -> byDifficulty[Difficulty.HARD].orEmpty()
                Stage.EXPERT -> byDifficulty[Difficulty.EXPERT].orEmpty()
                Stage.WARM_UP -> emptyList()
            }
            stage to pool.take(quota)
        }

        var running = 0
        return picks.map { (stage, list) ->
            StageLevels(stage, list.mapIndexed { i, entry -> Level(++running, i + 1, stage, entry) })
        }
    }

    /**
     * The first unsolved level. Not the last solved plus one: someone who jumped ahead to
     * Expert is pointed back at the gap they left, not marched off the end. Null only
     * when every level is solved.
     */
    fun next(stages: List<StageLevels>, completedIds: Set<String>): Level? =
        stages.asSequence().flatMap { it.levels.asSequence() }.firstOrNull { it.id !in completedIds }

    fun allLevels(stages: List<StageLevels>): List<Level> = stages.flatMap { it.levels }
}
