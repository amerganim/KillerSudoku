package com.ganim.killersudoku.engine.model

/**
 * Candidate sets are plain `Int` bitmasks: bit d set means digit d (1..9) is still possible.
 * Bit 0 is never used. Kept as raw ints rather than the value class sketched in
 * puzzle-engine-core.md 3 because the solver's inner loops allocate nothing this way.
 */
object Digits {
    const val ALL: Int = 0x3FE // bits 1..9

    fun bit(digit: Int): Int = 1 shl digit

    fun count(mask: Int): Int = Integer.bitCount(mask)

    fun isSingle(mask: Int): Boolean = mask != 0 && mask and (mask - 1) == 0

    /** The digit of a single-bit mask. */
    fun single(mask: Int): Int = Integer.numberOfTrailingZeros(mask)

    fun contains(mask: Int, digit: Int): Boolean = mask and (1 shl digit) != 0

    fun min(mask: Int): Int = Integer.numberOfTrailingZeros(mask)

    fun max(mask: Int): Int = 31 - Integer.numberOfLeadingZeros(mask)

    fun sum(mask: Int): Int {
        var s = 0
        var m = mask
        while (m != 0) {
            val d = Integer.numberOfTrailingZeros(m)
            s += d
            m = m and (m - 1)
        }
        return s
    }

    fun toList(mask: Int): List<Int> = (1..9).filter { contains(mask, it) }

    fun format(mask: Int): String = toList(mask).joinToString("")
}
