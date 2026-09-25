package com.ganim.killersudoku.data

import android.content.res.AssetManager
import com.ganim.killersudoku.daily.DailySchedule
import com.ganim.killersudoku.daily.DailySelector
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.pack.PuzzlePack
import com.ganim.killersudoku.progression.LevelLadder
import java.time.LocalDate
import kotlin.random.Random

/** One pack entry, without decoding its cages. */
data class PuzzleEntry(
    val index: Int,
    val id: String,
    val difficulty: Difficulty,
    val cageCount: Int,
)

/**
 * The bundled pack (generated offline by `:engine:generatePuzzlePack`), plus the three
 * ways into it: the level ladder, the daily puzzle, and free play.
 *
 * Takes the pack bytes through a loader so it runs on the JVM in tests.
 */
class PuzzleRepository(private val load: () -> ByteArray) {

    constructor(assets: AssetManager) : this({ assets.open(ASSET).use { it.readBytes() } })

    private val pack: PuzzlePack.Pack by lazy { PuzzlePack.decode(load()) }

    /** Every puzzle's header, in pack order. Built once; about 5,000 small objects. */
    val entries: List<PuzzleEntry> by lazy {
        List(pack.size) { i -> PuzzleEntry(i, pack.idOf(i), pack.difficultyOf(i), pack.cageCount(i)) }
    }

    private val byId: Map<String, PuzzleEntry> by lazy { entries.associateBy { it.id } }

    val ladder: List<LevelLadder.StageLevels> by lazy { LevelLadder.build(entries) }

    /**
     * Daily pools: each difficulty, minus the ladder's levels.
     *
     * The ladder takes puzzles from the front of each pool. Without this, Monday's daily
     * could be a level the player finished last week - which reads as the app repeating
     * itself.
     */
    private val dailyPools: Map<Difficulty, List<PuzzleEntry>> by lazy {
        val onLadder = LevelLadder.allLevels(ladder).map { it.id }.toSet()
        entries.filter { it.id !in onLadder }.groupBy { it.difficulty }
    }

    val count: Int get() = entries.size

    fun count(difficulty: Difficulty): Int = pack.count(difficulty)

    fun entry(id: String): PuzzleEntry? = byId[id]

    fun puzzleById(id: String): KillerPuzzle? = byId[id]?.let { pack[it.index] }

    /** Today's (or any date's) puzzle. A pure function of the date - nothing is stored or fetched. */
    fun dailyIdFor(date: LocalDate): String? {
        val pool = dailyPools[DailySchedule.specFor(date).difficulty].orEmpty()
        if (pool.isEmpty()) return null
        return pool[DailySelector.indexInPool(date, pool.size)].id
    }

    /** Free play: a random puzzle of [difficulty], preferring ones not yet solved. */
    fun randomId(difficulty: Difficulty, solved: Set<String>, rng: Random = Random.Default): String? {
        val pool = entries.filter { it.difficulty == difficulty }
        val fresh = pool.filter { it.id !in solved }
        return (fresh.ifEmpty { pool }).randomOrNull(rng)?.id
    }

    companion object {
        const val ASSET = "puzzles.bin"
    }
}
