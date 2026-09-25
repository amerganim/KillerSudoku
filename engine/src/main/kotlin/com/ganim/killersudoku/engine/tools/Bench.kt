package com.ganim.killersudoku.engine.tools

import com.ganim.killersudoku.engine.generator.KillerGenerator
import com.ganim.killersudoku.engine.model.Difficulty
import kotlin.random.Random

/**
 * Calibration aid: for each cage shape, how often an attempt succeeds, what tier the
 * result rates at, and how long attempts take. Feeds the thresholds in
 * KillerGenerator.shapeFor.
 */
fun main(args: Array<String>) {
    val n = args.firstOrNull()?.toInt() ?: 40
    val generator = KillerGenerator()
    for (shapeFor in Difficulty.entries) {
        val tiers = IntArray(5)
        var failed = 0
        var cages = 0
        val start = System.nanoTime()
        repeat(n) { a ->
            val rated = generator.attempt(Random(shapeFor.ordinal * 1_000_000L + a), KillerGenerator.shapeFor(shapeFor))
            if (rated == null) failed++ else { tiers[rated.maxTier]++; cages += rated.puzzle.cages.size }
        }
        val ms = (System.nanoTime() - start) / 1_000_000 / n
        val ok = n - failed
        println("$shapeFor shape: ok $ok/$n, tiers 1..4 = ${tiers.drop(1)}, avg cages ${if (ok > 0) cages / ok else 0}, $ms ms/attempt")
    }
}
