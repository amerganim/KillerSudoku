package com.ganim.killersudoku.engine.solver

import com.ganim.killersudoku.engine.model.Digits
import com.ganim.killersudoku.engine.model.Geometry

/**
 * The technique library, cheapest first (build plan 4). X-Wing, Swordfish and chains are
 * deliberately absent: puzzles that need them are rejected at generation, not solved.
 *
 * Calibration 2026-09-25: with Rule of 45 at tier 2 as the plan proposed, 0 of 160
 * generated puzzles solved at tier 1 - no killer of normal cage density falls without
 * it. It is the first technique every killer player learns, so single-house Rule of 45
 * sits in tier 1. Pointing pairs moved from tier 4 to 3 (conventional sudoku grading
 * ranks them below hidden triples), which also rebalances HARD (9% of output) against
 * EXPERT (28%) toward the plan's 25/15 split.
 */
object Techniques {
    val standard: List<Technique> = listOf(
        NakedSingle,
        HiddenSingle,
        CageCombination,
        RuleOf45(tier = 1),
        CageUnitElimination,
        NakedSubset,
        HiddenSubset,
        IntersectionRemoval,
        RuleOf45(tier = 4),
    )
}

private fun cellList(cells: IntArray): String = cells.joinToString(", ") { Geometry.cellName(it) }

/** A solved cell's digit is removed from every cell that must differ from it. */
object NakedSingle : Technique {
    override val name = "Naked single"
    override val tier = 1

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        for (c in 0 until Geometry.CELLS) {
            if (!grid.isSolved(c)) continue
            val bit = grid.masks[c]
            val changed = index.allPeers[c].filter { grid.eliminate(it, bit) }
            if (changed.isNotEmpty()) {
                val d = Digits.single(bit)
                return Step(this, "${Geometry.cellName(c)} is $d, so no other cell in its row, column, box or cage can be $d",
                    changed.toIntArray())
            }
        }
        return null
    }
}

/** A digit with only one possible cell in a house goes there. */
object HiddenSingle : Technique {
    override val name = "Hidden single"
    override val tier = 1

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        for (h in 0 until Geometry.HOUSES) {
            val house = Geometry.houses[h]
            for (d in 1..9) {
                val bit = Digits.bit(d)
                var where = -1
                var count = 0
                for (c in house) if (grid.masks[c] and bit != 0) { where = c; count++ }
                if (count == 0) {
                    grid.masks[house[0]] = 0
                    return Step(this, "$d has nowhere to go in ${Geometry.houseName(h)}", intArrayOf(house[0]))
                }
                if (count == 1 && grid.restrict(where, bit)) {
                    return Step(this, "$d can only go in ${Geometry.cellName(where)} within ${Geometry.houseName(h)}",
                        intArrayOf(where))
                }
            }
        }
        return null
    }
}

/** Each cage cell keeps only digits that appear in some valid fill of the cage. */
object CageCombination : Technique {
    override val name = "Cage combinations"
    override val tier = 1

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        for (cage in index.cages) {
            if (cage.cells.all { grid.isSolved(it) }) continue
            val analysis = SumAnalyzer.analyze(cage.cells, cage.sum, grid, index.mustDiffer)
            val changed = cage.cells.filterIndexed { i, c -> grid.restrict(c, analysis.possible[i]) }
            if (changed.isNotEmpty()) {
                val digits = analysis.possible.fold(0) { a, m -> a or m }
                return Step(this, "The ${cage.size}-cell cage of ${cage.sum} can only use ${Digits.format(digits)}",
                    changed.toIntArray())
            }
        }
        return null
    }
}

/** Innies/outies: the cells left over when whole cages are removed from a set of houses. */
class RuleOf45(override val tier: Int) : Technique {
    private val multi = tier >= 3
    override val name = if (multi) "Rule of 45 (multiple houses)" else "Rule of 45"

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        val constraints = if (multi) index.multiHouse45 else index.singleHouse45
        for (sc in constraints) {
            if (sc.cells.all { grid.isSolved(it) }) continue
            val analysis = SumAnalyzer.analyze(sc.cells, sc.sum, grid, index.mustDiffer)
            val changed = sc.cells.filterIndexed { i, c -> grid.restrict(c, analysis.possible[i]) }
            if (changed.isNotEmpty()) {
                return Step(this, "${sc.label}: ${cellList(sc.cells)} must add up to ${sc.sum}", changed.toIntArray())
            }
        }
        return null
    }
}

/**
 * A digit every fill of a cage must use, confined to one house within the cage, cannot
 * appear elsewhere in that house. Covers the "cage wholly inside a house" case.
 */
object CageUnitElimination : Technique {
    override val name = "Cage-house elimination"
    override val tier = 2

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        for (cage in index.cages) {
            if (cage.cells.all { grid.isSolved(it) }) continue
            val analysis = SumAnalyzer.analyze(cage.cells, cage.sum, grid, index.mustDiffer, needRequired = true)
            if (!analysis.hasSolution) continue
            var req = analysis.required
            while (req != 0) {
                val d = Integer.numberOfTrailingZeros(req)
                req = req and (req - 1)
                val bit = Digits.bit(d)
                val holders = cage.cells.filterIndexed { i, _ -> analysis.possible[i] and bit != 0 }
                if (holders.isEmpty()) continue
                for (h in Geometry.housesOf[holders[0]]) {
                    if (holders.any { h !in Geometry.housesOf[it] }) continue
                    val changed = Geometry.houses[h].filter { c -> index.cageOf[c] != index.cageOf[holders[0]] && grid.eliminate(c, bit) }
                    if (changed.isNotEmpty()) {
                        return Step(this, "The cage of ${cage.sum} must contain a $d inside ${Geometry.houseName(h)}, " +
                            "so the rest of ${Geometry.houseName(h)} cannot be $d", changed.toIntArray())
                    }
                }
            }
        }
        return null
    }
}

/** N cells in a house holding only N digits between them: those digits leave the rest of the house. */
object NakedSubset : Technique {
    override val name = "Naked pair/triple"
    override val tier = 3

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        for (size in 2..3) for (h in 0 until Geometry.HOUSES) {
            val house = Geometry.houses[h]
            val open = house.filter { !grid.isSolved(it) && Digits.count(grid.masks[it]) <= size }
            forEachCombination(open.size, size) { pick ->
                val cells = pick.map { open[it] }
                val union = cells.fold(0) { a, c -> a or grid.masks[c] }
                if (Digits.count(union) == size) {
                    val changed = house.filter { it !in cells && grid.eliminate(it, union) }
                    if (changed.isNotEmpty()) {
                        return Step(this, "${cellList(cells.toIntArray())} hold only ${Digits.format(union)} in " +
                            "${Geometry.houseName(h)}", changed.toIntArray())
                    }
                }
            }
        }
        return null
    }
}

/** N digits confined to the same N cells of a house: those cells hold nothing else. */
object HiddenSubset : Technique {
    override val name = "Hidden pair/triple"
    override val tier = 3

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        for (size in 2..3) for (h in 0 until Geometry.HOUSES) {
            val house = Geometry.houses[h]
            val placed = house.fold(0) { a, c -> if (grid.isSolved(c)) a or grid.masks[c] else a }
            val digits = (1..9).filter { placed and Digits.bit(it) == 0 }
            forEachCombination(digits.size, size) { pick ->
                val set = pick.fold(0) { a, i -> a or Digits.bit(digits[i]) }
                val cells = house.filter { grid.masks[it] and set != 0 }
                if (cells.size == size) {
                    val changed = cells.filter { grid.restrict(it, set) }
                    if (changed.isNotEmpty()) {
                        return Step(this, "${Digits.format(set)} can only go in ${cellList(cells.toIntArray())} " +
                            "within ${Geometry.houseName(h)}", changed.toIntArray())
                    }
                }
            }
        }
        return null
    }
}

/** Pointing pairs and box/line reduction. */
object IntersectionRemoval : Technique {
    override val name = "Pointing / box-line reduction"
    override val tier = 3

    override fun apply(index: PuzzleIndex, grid: CandidateGrid): Step? {
        for (h in 0 until Geometry.HOUSES) {
            val house = Geometry.houses[h]
            for (d in 1..9) {
                val bit = Digits.bit(d)
                val holders = house.filter { grid.masks[it] and bit != 0 }
                if (holders.size < 2 || holders.any { grid.isSolved(it) }) continue
                for (other in Geometry.housesOf[holders[0]]) {
                    if (other == h || holders.any { other !in Geometry.housesOf[it] }) continue
                    val changed = Geometry.houses[other].filter { it !in holders && grid.eliminate(it, bit) }
                    if (changed.isNotEmpty()) {
                        return Step(this, "In ${Geometry.houseName(h)}, $d must be in ${Geometry.houseName(other)}, " +
                            "so the rest of ${Geometry.houseName(other)} cannot be $d", changed.toIntArray())
                    }
                }
            }
        }
        return null
    }
}

/** Calls [block] with each k-subset of 0 until n, in lexicographic order. */
internal inline fun forEachCombination(n: Int, k: Int, block: (IntArray) -> Unit) {
    if (k > n || k <= 0) return
    val idx = IntArray(k) { it }
    while (true) {
        block(idx)
        var i = k - 1
        while (i >= 0 && idx[i] == n - k + i) i--
        if (i < 0) return
        idx[i]++
        for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
    }
}
