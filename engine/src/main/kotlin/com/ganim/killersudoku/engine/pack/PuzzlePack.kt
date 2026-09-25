package com.ganim.killersudoku.engine.pack

import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.engine.model.KillerPuzzle
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class BitWriter {
    private val out = ByteArrayOutputStream()
    private var acc = 0L
    private var bits = 0

    fun write(value: Int, width: Int) {
        require(value >= 0 && value < (1 shl width)) { "$value does not fit in $width bits" }
        acc = (acc shl width) or value.toLong()
        bits += width
        while (bits >= 8) {
            bits -= 8
            out.write(((acc shr bits) and 0xFF).toInt())
        }
    }

    fun toByteArray(): ByteArray {
        if (bits > 0) {
            out.write(((acc shl (8 - bits)) and 0xFF).toInt())
            bits = 0
        }
        return out.toByteArray()
    }
}

class BitReader(private val data: ByteArray, private var pos: Int = 0) {
    private var acc = 0L
    private var bits = 0

    fun read(width: Int): Int {
        while (bits < width) {
            acc = (acc shl 8) or (data[pos++].toLong() and 0xFF)
            bits += 8
        }
        bits -= width
        return ((acc shr bits) and ((1L shl width) - 1)).toInt()
    }
}

/**
 * Binary pack (build plan 5 "Pack"):
 *
 *   magic "KSDK" | version u8 | count u32 | per-difficulty count u32 x4
 *   record offsets u32 x count
 *   records: cageCount(6) | cageOf 81x6 | sums cageCount x6 | solution 81x4
 *
 * Records are grouped by difficulty in enum order, so a puzzle's difficulty is implied by
 * its position and its id is its index. About 110 bytes per puzzle.
 */
object PuzzlePack {
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'D'.code.toByte(), 'K'.code.toByte())
    private const val VERSION = 1

    fun encodeRecord(puzzle: KillerPuzzle): ByteArray {
        val w = BitWriter()
        w.write(puzzle.cages.size, 6)
        puzzle.cageOf.forEach { w.write(it, 6) }
        puzzle.cages.forEach { w.write(it.sum, 6) }
        puzzle.solution.forEach { w.write(it, 4) }
        return w.toByteArray()
    }

    fun decodeRecord(data: ByteArray, offset: Int, id: Int, difficulty: Difficulty): KillerPuzzle {
        val r = BitReader(data, offset)
        val count = r.read(6)
        val cageOf = IntArray(Geometry.CELLS) { r.read(6) }
        val sums = IntArray(count) { r.read(6) }
        val solution = IntArray(Geometry.CELLS) { r.read(4) }
        val puzzle = KillerPuzzle.fromLayout(id, solution, cageOf, difficulty)
        check(puzzle.cages.map { it.sum } == sums.toList()) { "pack record $id: sums do not match solution" }
        return puzzle
    }

    /** [puzzles] must already be sorted by difficulty; ids are reassigned to indices. */
    fun encode(puzzles: List<KillerPuzzle>): ByteArray {
        require(puzzles.zipWithNext().all { (a, b) -> a.difficulty <= b.difficulty }) { "sort by difficulty first" }
        val records = puzzles.map { encodeRecord(it) }
        val headerSize = 4 + 1 + 4 + 4 * Difficulty.entries.size + 4 * puzzles.size
        val buf = ByteBuffer.allocate(headerSize + records.sumOf { it.size })
        buf.put(MAGIC).put(VERSION.toByte()).putInt(puzzles.size)
        Difficulty.entries.forEach { d -> buf.putInt(puzzles.count { it.difficulty == d }) }
        var offset = headerSize
        records.forEach { buf.putInt(offset); offset += it.size }
        records.forEach { buf.put(it) }
        return buf.array()
    }

    /** Lazily decoded pack. Only the header is parsed up front. */
    class Pack(private val data: ByteArray) {
        val size: Int
        private val counts: IntArray
        private val offsets: IntArray

        init {
            val buf = ByteBuffer.wrap(data)
            val magic = ByteArray(4).also { buf.get(it) }
            require(magic.contentEquals(MAGIC)) { "not a killer sudoku pack" }
            val version = buf.get().toInt()
            require(version == VERSION) { "unsupported pack version $version" }
            size = buf.getInt()
            counts = IntArray(Difficulty.entries.size) { buf.getInt() }
            offsets = IntArray(size) { buf.getInt() }
        }

        fun count(difficulty: Difficulty): Int = counts[difficulty.ordinal]

        /** Index of the first puzzle of [difficulty]. */
        fun firstIndex(difficulty: Difficulty): Int = counts.take(difficulty.ordinal).sum()

        fun difficultyOf(index: Int): Difficulty {
            var i = index
            for (d in Difficulty.entries) {
                if (i < counts[d.ordinal]) return d
                i -= counts[d.ordinal]
            }
            throw IndexOutOfBoundsException("puzzle $index of $size")
        }

        operator fun get(index: Int): KillerPuzzle = decodeRecord(data, offsets[index], index, difficultyOf(index))
    }

    fun decode(data: ByteArray): Pack = Pack(data)
}
