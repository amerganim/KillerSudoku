package com.ganim.killersudoku.engine

import com.ganim.killersudoku.engine.combos.DigitCombos
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle

/**
 * Independent solution counter. Shares nothing with the logical solver except the digit
 * table and geometry, so it can check the solver's uniqueness claims. Test-only, never
 * ships (puzzle-engine-core.md 4).
 *
 * Branch-and-propagate: at every node, each empty cell's legal digits are its house
 * constraints narrowed by an exact enumeration of its cage's remaining fills; forced
 * cells (one legal digit, or a digit with one home in a house) are placed without
 * branching. Then branch on the cell with the fewest options.
 */
object BacktrackingVerifier {

    /** Solutions found, capped at [limit]; -1 if [nodeBudget] ran out first. */
    fun countSolutions(puzzle: KillerPuzzle, limit: Int = 2, nodeBudget: Long = 2_000_000): Int {
        val s = Search(puzzle, limit, nodeBudget)
        s.search(IntArray(81))
        return if (s.exhausted) -1 else s.found
    }

    private class Search(val puzzle: KillerPuzzle, val limit: Int, var budget: Long) {
        var found = 0
        var exhausted = false
        private val cages = puzzle.cages.map { it.cells }

        fun search(values: IntArray) {
            if (found >= limit || exhausted) return
            if (--budget < 0) { exhausted = true; return }
            val legal = IntArray(81)
            // Propagate forced placements to a fixpoint.
            while (true) {
                if (!computeLegal(values, legal)) return
                val forced = findForced(values, legal) ?: break
                if (forced < 0) return
                values[forced shr 4] = forced and 15
            }
            var best = -1
            for (c in 0 until 81) {
                if (values[c] == 0 && (best < 0 || Integer.bitCount(legal[c]) < Integer.bitCount(legal[best]))) best = c
            }
            if (best < 0) {
                if (isSolution(values)) found++
                return
            }
            var bits = legal[best]
            while (bits != 0) {
                val d = Integer.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                val next = values.copyOf()
                next[best] = d
                search(next)
                if (found >= limit || exhausted) return
            }
        }

        /** Fills [legal] for empty cells; false if some empty cell has none. */
        private fun computeLegal(values: IntArray, legal: IntArray): Boolean {
            for (c in 0 until 81) {
                if (values[c] != 0) { legal[c] = 0; continue }
                var m = 0x3FE
                for (p in Geometry.peers[c]) if (values[p] != 0) m = m and (1 shl values[p]).inv()
                legal[c] = m
            }
            for ((k, cells) in cages.withIndex()) {
                val open = cells.filter { values[it] == 0 }
                if (open.isEmpty()) continue
                var used = 0
                var remaining = puzzle.cages[k].sum
                for (c in cells) if (values[c] != 0) { used = used or (1 shl values[c]); remaining -= values[c] }
                val union = IntArray(open.size)
                for (combo in DigitCombos.masksFor(open.size, remaining)) {
                    if (combo and used != 0) continue
                    assign(open, legal, combo, 0, 0, IntArray(open.size), union)
                }
                for (i in open.indices) {
                    legal[open[i]] = legal[open[i]] and union[i]
                    if (legal[open[i]] == 0) return false
                }
            }
            return true
        }

        /** Enumerates bijections of [combo]'s digits onto [open], OR-ing usable digits into [union]. */
        private fun assign(open: List<Int>, legal: IntArray, combo: Int, i: Int, taken: Int, chosen: IntArray, union: IntArray) {
            if (i == open.size) {
                for (j in open.indices) union[j] = union[j] or (1 shl chosen[j])
                return
            }
            var bits = combo and taken.inv() and legal[open[i]]
            while (bits != 0) {
                val d = Integer.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                chosen[i] = d
                assign(open, legal, combo, i + 1, taken or (1 shl d), chosen, union)
            }
        }

        /** (cell shl 4 | digit) for a forced placement, -1 for a dead end, null if none. */
        private fun findForced(values: IntArray, legal: IntArray): Int? {
            for (c in 0 until 81) {
                if (values[c] == 0 && Integer.bitCount(legal[c]) == 1) return (c shl 4) or Integer.numberOfTrailingZeros(legal[c])
            }
            for (house in Geometry.houses) {
                var placed = 0
                for (c in house) if (values[c] != 0) placed = placed or (1 shl values[c])
                for (d in 1..9) {
                    if (placed and (1 shl d) != 0) continue
                    var where = -1
                    var count = 0
                    for (c in house) if (legal[c] and (1 shl d) != 0) { where = c; count++ }
                    if (count == 0) return -1
                    if (count == 1) return (where shl 4) or d
                }
            }
            return null
        }

        private fun isSolution(values: IntArray): Boolean {
            for (house in Geometry.houses) if (house.fold(0) { a, c -> a or (1 shl values[c]) } != 0x3FE) return false
            for ((k, cells) in cages.withIndex()) {
                if (cells.sumOf { values[it] } != puzzle.cages[k].sum) return false
                if (cells.map { values[it] }.distinct().size != cells.size) return false
            }
            return true
        }
    }
}
