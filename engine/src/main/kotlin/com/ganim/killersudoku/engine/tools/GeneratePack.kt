package com.ganim.killersudoku.engine.tools

import com.ganim.killersudoku.engine.generator.PackBuilder
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.pack.PuzzlePack
import java.io.File

/**
 * Offline pack generator. Deterministic for a given seed and count.
 *
 *   --count=5000 --seed=1 --threads=8 --out=app/src/main/assets/puzzles.bin
 */
fun main(args: Array<String>) {
    val opts = args.associate { it.removePrefix("--").substringBefore('=') to it.substringAfter('=') }
    val count = opts["count"]?.toInt() ?: 5000
    val seed = opts["seed"]?.toLong() ?: 1L
    val threads = opts["threads"]?.toInt() ?: Runtime.getRuntime().availableProcessors()
    val out = File(opts["out"] ?: "puzzles.bin")

    // Build plan 5: 1,500 easy, 1,500 medium, 1,250 hard, 750 expert (of 5,000).
    val share = mapOf(Difficulty.EASY to 0.30, Difficulty.MEDIUM to 0.30, Difficulty.HARD to 0.25)
    val quota = share.mapValues { (_, s) -> (count * s).toInt() }.toMutableMap()
    quota[Difficulty.EXPERT] = count - quota.values.sum()

    val started = System.nanoTime()
    val result = PackBuilder(seed, threads).build(quota) { n, sizes ->
        println("attempt $n: " + Difficulty.entries.joinToString { "$it ${sizes[it]}/${quota[it]}" })
    }
    val chosen = Difficulty.entries.flatMap { result.puzzles.getValue(it) }
    val bytes = PuzzlePack.encode(chosen.map { it.puzzle })
    out.parentFile?.mkdirs()
    out.writeBytes(bytes)

    val seconds = (System.nanoTime() - started) / 1e9
    println("Wrote ${chosen.size} puzzles, ${bytes.size / 1024} KB, to $out in ${"%.1f".format(seconds)}s")
    println("Attempts ${result.attempts}, abandoned ${result.failed}")
    for (d in Difficulty.entries) {
        val list = result.puzzles.getValue(d)
        val cages = list.map { it.puzzle.cages.size }.average()
        val depth = list.map { it.depth }.average()
        println("  $d: ${list.size} puzzles, avg cages ${"%.1f".format(cages)}, avg depth ${"%.0f".format(depth)}")
    }
}
