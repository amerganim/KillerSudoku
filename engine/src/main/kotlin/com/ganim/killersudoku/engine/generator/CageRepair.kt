package com.ganim.killersudoku.engine.generator

import com.ganim.killersudoku.engine.model.Geometry
import kotlin.random.Random

/**
 * Reshapes cages around the cells a stalled solve left open.
 *
 * Calibration (2026-09-25) showed that most random partitions genuinely have several
 * solutions - often a 4-cell "deadly rectangle" across two cages - so plain
 * generate-and-reject almost never accepts anything. Instead the generator solves,
 * and when the solver stalls it breaks the ambiguity locally and tries again:
 *
 *  - split a cage holding an open cell (strictly adds information), or
 *  - move an open cell into a neighbouring cage, or pull a neighbour in.
 *
 * Every change keeps cages connected, digit-distinct, within the size cap and within the
 * singleton budget.
 */
object CageRepair {

    fun repair(cageOf: IntArray, solution: IntArray, open: List<Int>, shape: CageShape, rng: Random): IntArray? {
        val cages = cageOf.toCages()
        for (x in open.shuffled(rng).take(12)) {
            val ops = listOf(0, 1, 2).shuffled(rng)
            for (op in ops) {
                val result = when (op) {
                    0 -> split(cages, cageOf[x], solution, shape, rng)
                    1 -> moveInto(cages, cageOf, x, solution, shape, rng)
                    else -> pullIn(cages, cageOf, x, solution, shape, rng)
                }
                if (result != null) return result.toLayout()
            }
        }
        return null
    }

    private fun IntArray.toCages(): List<List<Int>> =
        (0..max()).map { k -> (0 until Geometry.CELLS).filter { this[it] == k } }

    private fun List<List<Int>>.toLayout(): IntArray {
        val out = IntArray(Geometry.CELLS)
        filter { it.isNotEmpty() }.sortedBy { it.min() }.forEachIndexed { i, cells -> cells.forEach { out[it] = i } }
        return out
    }

    private fun singletons(cages: List<List<Int>>) = cages.count { it.size == 1 }

    /** Splits cage [k] into two connected parts. */
    private fun split(cages: List<List<Int>>, k: Int, solution: IntArray, shape: CageShape, rng: Random): List<List<Int>>? {
        val cage = cages[k]
        if (cage.size < 2) return null
        val budget = shape.maxSingletons - singletons(cages)
        repeat(8) {
            val minPart = if (budget > 0 && cage.size <= 3) 1 else 2
            if (cage.size < 2 * minPart) return null
            val size = rng.nextInt(minPart, cage.size - minPart + 1)
            val part = growWithin(cage, size, rng) ?: return@repeat
            val rest = cage - part.toSet()
            if (!connected(rest)) return@repeat
            val newSingles = (if (part.size == 1) 1 else 0) + (if (rest.size == 1) 1 else 0)
            if (newSingles > budget) return@repeat
            return cages.toMutableList().apply { this[k] = part; add(rest) }
        }
        return null
    }

    /** Moves [x] out of its cage into an adjacent one. */
    private fun moveInto(cages: List<List<Int>>, cageOf: IntArray, x: Int, solution: IntArray, shape: CageShape, rng: Random): List<List<Int>>? {
        val from = cageOf[x]
        val remaining = cages[from] - x
        if (remaining.isEmpty() || !connected(remaining)) return null
        if (remaining.size == 1 && singletons(cages) >= shape.maxSingletons) return null
        for (to in Geometry.orthogonalNeighbours(x).map { cageOf[it] }.distinct().filter { it != from }.shuffled(rng)) {
            val target = cages[to]
            if (target.size >= shape.maxSize || target.any { solution[it] == solution[x] }) continue
            val singlesAfter = singletons(cages) - (if (target.size == 1) 1 else 0) + (if (remaining.size == 1) 1 else 0)
            if (singlesAfter > shape.maxSingletons) continue
            return cages.toMutableList().apply { this[from] = remaining; this[to] = target + x }
        }
        return null
    }

    /** Pulls a neighbour of [x] from another cage into x's cage. */
    private fun pullIn(cages: List<List<Int>>, cageOf: IntArray, x: Int, solution: IntArray, shape: CageShape, rng: Random): List<List<Int>>? {
        val to = cageOf[x]
        val target = cages[to]
        if (target.size >= shape.maxSize) return null
        for (y in Geometry.orthogonalNeighbours(x).filter { cageOf[it] != to }.shuffled(rng)) {
            if (target.any { solution[it] == solution[y] }) continue
            val from = cageOf[y]
            val remaining = cages[from] - y
            if (remaining.isEmpty() || !connected(remaining)) continue
            val singlesAfter = singletons(cages) - (if (target.size == 1) 1 else 0) + (if (remaining.size == 1) 1 else 0)
            if (singlesAfter > shape.maxSingletons) continue
            return cages.toMutableList().apply { this[from] = remaining; this[to] = target + y }
        }
        return null
    }

    /** A random connected subset of [cells] of the given size. */
    private fun growWithin(cells: List<Int>, size: Int, rng: Random): List<Int>? {
        val pool = cells.toSet()
        val part = mutableListOf(cells.random(rng))
        while (part.size < size) {
            val frontier = part.flatMap { Geometry.orthogonalNeighbours(it).asList() }
                .filter { it in pool && it !in part }.distinct()
            if (frontier.isEmpty()) return null
            part.add(frontier.random(rng))
        }
        return part
    }

    fun connected(cells: List<Int>): Boolean {
        if (cells.isEmpty()) return true
        val pool = cells.toSet()
        val seen = mutableSetOf(cells[0])
        val queue = ArrayDeque(listOf(cells[0]))
        while (queue.isNotEmpty()) {
            for (n in Geometry.orthogonalNeighbours(queue.removeFirst())) {
                if (n in pool && seen.add(n)) queue.add(n)
            }
        }
        return seen.size == cells.size
    }
}
