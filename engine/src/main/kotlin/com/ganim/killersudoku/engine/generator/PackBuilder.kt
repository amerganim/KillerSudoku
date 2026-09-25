package com.ganim.killersudoku.engine.generator

import com.ganim.killersudoku.engine.model.Difficulty
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Parallel generation into per-difficulty buckets (puzzle-engine-core.md 7).
 *
 * Deterministic for a given seed and quota: attempt n always uses its own Random, every
 * accepted attempt is recorded, and each bucket keeps its lowest-numbered attempts - so
 * thread timing never changes the output.
 */
class PackBuilder(private val seed: Long, private val threads: Int = Runtime.getRuntime().availableProcessors()) {

    class Result(val puzzles: Map<Difficulty, List<Rated>>, val attempts: Long, val failed: Long)

    fun build(quota: Map<Difficulty, Int>, progress: ((Long, Map<Difficulty, Int>) -> Unit)? = null): Result {
        val accepted = Difficulty.entries.associateWith { ConcurrentHashMap<Long, Rated>() }
        val next = AtomicLong()
        val failed = AtomicLong()
        fun full(d: Difficulty) = accepted.getValue(d).size >= (quota[d] ?: 0)

        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.execute {
                val generator = KillerGenerator()
                while (!Difficulty.entries.all(::full)) {
                    val n = next.getAndIncrement()
                    // Cycle the cage shapes so every bucket gets candidates; the rating decides the bucket.
                    val shape = KillerGenerator.shapeFor(Difficulty.entries[(n % 4).toInt()])
                    val rated = generator.attempt(Random(seed * 1_000_003 + n), shape)
                    if (rated == null) failed.incrementAndGet()
                    else accepted.getValue(rated.puzzle.difficulty)[n] = rated
                    if (progress != null && n % 500 == 0L) progress(n, accepted.mapValues { it.value.size })
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.DAYS)

        val chosen = Difficulty.entries.associateWith { d ->
            accepted.getValue(d).entries.sortedBy { it.key }.take(quota[d] ?: 0).map { it.value }
        }
        return Result(chosen, next.get(), failed.get())
    }
}
