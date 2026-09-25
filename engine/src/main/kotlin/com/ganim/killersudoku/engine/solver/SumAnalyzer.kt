package com.ganim.killersudoku.engine.solver

import com.ganim.killersudoku.engine.model.Digits

/**
 * Enumerates every assignment of a small cell group that hits a target sum, respecting
 * current candidates and must-differ pairs. Used for cages (all distinct) and for
 * rule-of-45 innie/outie groups (distinct only where cells share a house or cage).
 */
class SumAnalysis(
    /** Per cell (same order as the input), the digits used by at least one valid assignment. */
    val possible: IntArray,
    /** Digits used by every valid assignment. Only exact when requested. */
    val required: Int,
    val hasSolution: Boolean,
)

object SumAnalyzer {

    fun analyze(
        cells: IntArray,
        sum: Int,
        grid: CandidateGrid,
        mustDiffer: BooleanArray,
        needRequired: Boolean = false,
    ): SumAnalysis {
        val n = cells.size
        // Most constrained cells first: prunes far earlier.
        val order = cells.indices.sortedBy { Digits.count(grid.masks[cells[it]]) }.toIntArray()
        val ordered = IntArray(n) { cells[order[it]] }
        val masks = IntArray(n) { grid.masks[ordered[it]] }
        val suffixMin = IntArray(n + 1)
        val suffixMax = IntArray(n + 1)
        for (i in n - 1 downTo 0) {
            if (masks[i] == 0) return SumAnalysis(IntArray(n), 0, false)
            suffixMin[i] = suffixMin[i + 1] + Digits.min(masks[i])
            suffixMax[i] = suffixMax[i + 1] + Digits.max(masks[i])
        }
        val search = Search(ordered, masks, suffixMin, suffixMax, mustDiffer, needRequired)
        search.run(0, sum)

        val possible = IntArray(n)
        for (i in 0 until n) possible[order[i]] = search.possible[i]
        return SumAnalysis(possible, if (search.found) search.required else 0, search.found)
    }

    private class Search(
        val cells: IntArray,
        val masks: IntArray,
        val suffixMin: IntArray,
        val suffixMax: IntArray,
        val mustDiffer: BooleanArray,
        val needRequired: Boolean,
    ) {
        val n = cells.size
        val chosen = IntArray(n)
        val possible = IntArray(n)
        var required = Digits.ALL
        var found = false
        private var saturated = false

        fun run(i: Int, remaining: Int) {
            if (saturated) return
            if (i == n) {
                if (remaining != 0) return
                found = true
                var used = 0
                for (j in 0 until n) {
                    val b = 1 shl chosen[j]
                    possible[j] = possible[j] or b
                    used = used or b
                }
                required = required and used
                // Once every candidate is known possible there is nothing left to learn.
                if (!needRequired && (0 until n).all { possible[it] == masks[it] }) saturated = true
                return
            }
            if (remaining < suffixMin[i] || remaining > suffixMax[i]) return
            var m = masks[i]
            val cell = cells[i]
            while (m != 0) {
                val d = Integer.numberOfTrailingZeros(m)
                m = m and (m - 1)
                if (d > remaining) break
                var clash = false
                for (j in 0 until i) {
                    if (chosen[j] == d && mustDiffer[cell * 81 + cells[j]]) {
                        clash = true
                        break
                    }
                }
                if (clash) continue
                chosen[i] = d
                run(i + 1, remaining - d)
                if (saturated) return
            }
        }
    }
}
