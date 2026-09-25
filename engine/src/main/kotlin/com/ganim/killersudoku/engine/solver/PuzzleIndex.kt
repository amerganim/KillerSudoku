package com.ganim.killersudoku.engine.solver

import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle

/**
 * A known-sum group of cells that is not a cage: the innies or outies of a region,
 * from the rule of 45. Cells need not be distinct unless they share a house or cage.
 */
class SumConstraint(val cells: IntArray, val sum: Int, val tier: Int, val label: String)

/**
 * Everything the techniques need that depends only on the cage layout, computed once
 * per puzzle.
 */
class PuzzleIndex(val puzzle: KillerPuzzle) {
    val cages = puzzle.cages
    val cageOf = puzzle.cageOf

    /** [a * 81 + b] - true when a and b must differ: same house or same cage. */
    val mustDiffer = BooleanArray(Geometry.CELLS * Geometry.CELLS).also { m ->
        for (a in 0 until Geometry.CELLS) for (b in 0 until Geometry.CELLS) {
            if (a != b && (Geometry.sharesHouse(a, b) || cageOf[a] == cageOf[b])) m[a * 81 + b] = true
        }
    }

    /** House peers plus cage mates. */
    val allPeers: Array<IntArray> = Array(Geometry.CELLS) { c ->
        (0 until Geometry.CELLS).filter { mustDiffer[c * 81 + it] }.toIntArray()
    }

    /** Innie/outie constraints from single houses (tier 2). */
    val singleHouse45: List<SumConstraint>

    /** Innie/outie constraints from 2-3 adjacent rows or columns (tier 4). */
    val multiHouse45: List<SumConstraint>

    init {
        val single = ArrayList<SumConstraint>()
        for (h in 0 until Geometry.HOUSES) {
            addRegion(single, Geometry.houses[h], 1, 2, Geometry.houseName(h))
        }
        val multi = ArrayList<SumConstraint>()
        for (span in 2..3) for (start in 0..9 - span) {
            val end = start + span
            val rows = (start until end).flatMap { Geometry.houses[it].asIterable() }.toIntArray()
            val cols = (start until end).flatMap { Geometry.houses[9 + it].asIterable() }.toIntArray()
            addRegion(multi, rows, span, 4, "rows ${start + 1}-$end")
            addRegion(multi, cols, span, 4, "columns ${start + 1}-$end")
        }
        val seen = single.map { it.cells.toList() }.toMutableSet()
        singleHouse45 = single
        multiHouse45 = multi.filter { seen.add(it.cells.toList()) }
    }

    private fun addRegion(out: MutableList<SumConstraint>, region: IntArray, houses: Int, tier: Int, name: String) {
        val inRegion = BooleanArray(Geometry.CELLS).also { f -> region.forEach { f[it] = true } }
        var fullSum = 0
        var partialSum = 0
        val innies = ArrayList<Int>()
        val outies = ArrayList<Int>()
        for (cage in cages) {
            val inside = cage.cells.count { inRegion[it] }
            when (inside) {
                0 -> Unit
                cage.size -> fullSum += cage.sum
                else -> {
                    partialSum += cage.sum
                    cage.cells.forEach { if (inRegion[it]) innies.add(it) else outies.add(it) }
                }
            }
        }
        val innieSum = 45 * houses - fullSum
        val outieSum = partialSum - innieSum
        if (innies.size in 1..MAX_CELLS && !isWholeCage(innies)) {
            out.add(SumConstraint(innies.sorted().toIntArray(), innieSum, tier, "Rule of 45 on $name (innies)"))
        }
        if (outies.size in 1..MAX_CELLS && !isWholeCage(outies)) {
            out.add(SumConstraint(outies.sorted().toIntArray(), outieSum, tier, "Rule of 45 on $name (outies)"))
        }
    }

    private fun isWholeCage(cells: List<Int>): Boolean {
        val k = cageOf[cells[0]]
        return cells.all { cageOf[it] == k } && cells.size == cages[k].size
    }

    private companion object {
        /** Larger groups are rarely usable by a human and slow to analyse. */
        const val MAX_CELLS = 5
    }
}
