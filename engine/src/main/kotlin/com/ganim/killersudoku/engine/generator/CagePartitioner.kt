package com.ganim.killersudoku.engine.generator

import com.ganim.killersudoku.engine.model.Geometry
import kotlin.random.Random

/**
 * Shape parameters for a partition. Bigger cages and fewer singletons make harder
 * puzzles (build plan 5).
 */
class CageShape(
    /** Relative weight of cage sizes 1..6 (index 0 = size 1). */
    val sizeWeights: IntArray,
    val maxSingletons: Int,
    val maxSize: Int = 6,
)

/**
 * Random-walk growth of cages over a solved grid, refusing any step that would repeat a
 * digit inside a cage and preferring compact shapes.
 */
object CagePartitioner {

    /** Returns cell -> cage index, or null if the attempt left too many singletons. */
    fun partition(solution: IntArray, shape: CageShape, rng: Random): IntArray? {
        val cageOf = IntArray(Geometry.CELLS) { -1 }
        val cages = ArrayList<MutableList<Int>>()

        while (true) {
            val seed = pickSeed(cageOf, rng) ?: break
            val target = sampleSize(shape.sizeWeights, rng)
            val cells = mutableListOf(seed)
            var digits = 1 shl solution[seed]
            val k = cages.size
            cageOf[seed] = k
            while (cells.size < target) {
                val next = pickGrowth(cells, digits, cageOf, solution, rng) ?: break
                cells.add(next)
                cageOf[next] = k
                digits = digits or (1 shl solution[next])
            }
            cages.add(cells)
        }

        mergeSingletons(cages, cageOf, solution, shape.maxSize)
        val singletons = cages.count { it.size == 1 }
        if (singletons > shape.maxSingletons) return null

        // Renumber by top-left cell, which also drops cages emptied by merging.
        val live = cages.filter { it.isNotEmpty() }.sortedBy { it.min() }
        val out = IntArray(Geometry.CELLS)
        live.forEachIndexed { i, cells -> cells.forEach { out[it] = i } }
        return out
    }

    /** The unassigned cell with the fewest unassigned neighbours - cuts down stranded cells. */
    private fun pickSeed(cageOf: IntArray, rng: Random): Int? {
        var best = -1
        var bestScore = Int.MAX_VALUE
        var ties = 0
        for (c in 0 until Geometry.CELLS) {
            if (cageOf[c] >= 0) continue
            val free = Geometry.orthogonalNeighbours(c).count { cageOf[it] < 0 }
            when {
                free < bestScore -> { best = c; bestScore = free; ties = 1 }
                free == bestScore -> { ties++; if (rng.nextInt(ties) == 0) best = c }
            }
        }
        return if (best < 0) null else best
    }

    private fun sampleSize(weights: IntArray, rng: Random): Int {
        var roll = rng.nextInt(weights.sum())
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll < 0) return i + 1
        }
        return weights.size
    }

    /** Weighted toward cells touching the cage on several sides, so cages stay compact. */
    private fun pickGrowth(cells: List<Int>, digits: Int, cageOf: IntArray, solution: IntArray, rng: Random): Int? {
        val candidates = LinkedHashMap<Int, Int>()
        for (c in cells) for (n in Geometry.orthogonalNeighbours(c)) {
            if (cageOf[n] >= 0 || digits and (1 shl solution[n]) != 0) continue
            candidates[n] = (candidates[n] ?: 0) + 1
        }
        if (candidates.isEmpty()) return null
        val weighted = candidates.map { (cell, touches) -> cell to touches * touches * 4 + 1 }
        var roll = rng.nextInt(weighted.sumOf { it.second })
        for ((cell, w) in weighted) {
            roll -= w
            if (roll < 0) return cell
        }
        return weighted.last().first
    }

    /** Folds each singleton into a neighbouring cage when that keeps digits distinct. */
    private fun mergeSingletons(cages: MutableList<MutableList<Int>>, cageOf: IntArray, solution: IntArray, maxSize: Int) {
        for (k in cages.indices) {
            val cage = cages[k]
            if (cage.size != 1) continue
            val cell = cage[0]
            val target = Geometry.orthogonalNeighbours(cell)
                .map { cageOf[it] }
                .distinct()
                .filter { t ->
                    t != k && cages[t].size in 1 until maxSize && cages[t].none { solution[it] == solution[cell] }
                }
                .minByOrNull { cages[it].size } ?: continue
            cages[target].add(cell)
            cageOf[cell] = target
            cage.clear()
        }
    }
}
