package com.ganim.killersudoku.data

import android.content.Context
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.pack.PuzzlePack
import kotlin.random.Random

/** The bundled pack (generated offline by :engine:generatePuzzlePack). */
class PuzzleRepository(context: Context) {
    private val pack: PuzzlePack.Pack by lazy {
        PuzzlePack.decode(context.assets.open(ASSET).use { it.readBytes() })
    }

    fun count(difficulty: Difficulty): Int = pack.count(difficulty)

    fun puzzle(index: Int): KillerPuzzle = pack[index]

    fun randomIndex(difficulty: Difficulty, rng: Random = Random.Default): Int =
        pack.firstIndex(difficulty) + rng.nextInt(pack.count(difficulty))

    private companion object {
        const val ASSET = "puzzles.bin"
    }
}
