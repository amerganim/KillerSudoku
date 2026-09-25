package com.ganim.killersudoku.data

import com.ganim.killersudoku.engine.model.Geometry
import com.ganim.killersudoku.game.BoardSnapshot
import java.nio.ByteBuffer

/** An in-progress game as stored: the board, lives, clock and recent undo history. */
class SavedBoard(
    val values: IntArray,
    val notes: IntArray,
    val mistakes: Int,
    val elapsedMs: Long,
    val undo: List<BoardSnapshot>,
)

/**
 * Packs a [SavedBoard] into the progress row's blob.
 *
 *   version u8 | mistakes u8 | elapsedMs i64 | board | undoCount u8 | board x undoCount
 *   board = values 81 x u8, notes 81 x u16
 *
 * A board is 243 bytes. Only the newest [MAX_UNDO] steps are kept, which caps a row near
 * 12 KB: enough history that undo still works after the app is killed (the bar Nonogram
 * set), without writing the full 200-step stack on every move.
 */
object SavedBoardCodec {
    private const val VERSION = 1
    const val MAX_UNDO = 50
    private const val BOARD_BYTES = Geometry.CELLS * 3

    fun encode(board: SavedBoard): ByteArray {
        val undo = board.undo.takeLast(MAX_UNDO)
        val buf = ByteBuffer.allocate(1 + 1 + 8 + BOARD_BYTES + 1 + undo.size * BOARD_BYTES)
        buf.put(VERSION.toByte()).put(board.mistakes.toByte()).putLong(board.elapsedMs)
        putBoard(buf, board.values, board.notes)
        buf.put(undo.size.toByte())
        undo.forEach { putBoard(buf, it.values, it.notes) }
        return buf.array()
    }

    /** Null for a blob this version cannot read, which starts the puzzle afresh rather than crashing. */
    fun decode(bytes: ByteArray): SavedBoard? = runCatching {
        val buf = ByteBuffer.wrap(bytes)
        require(buf.get().toInt() == VERSION)
        val mistakes = buf.get().toInt()
        val elapsed = buf.getLong()
        val (values, notes) = getBoard(buf)
        val undo = List(buf.get().toInt() and 0xFF) { getBoard(buf).let { (v, n) -> BoardSnapshot(v, n) } }
        SavedBoard(values, notes, mistakes, elapsed, undo)
    }.getOrNull()

    private fun putBoard(buf: ByteBuffer, values: IntArray, notes: IntArray) {
        values.forEach { buf.put(it.toByte()) }
        notes.forEach { buf.putShort(it.toShort()) }
    }

    private fun getBoard(buf: ByteBuffer): Pair<IntArray, IntArray> {
        val values = IntArray(Geometry.CELLS) { buf.get().toInt() }
        val notes = IntArray(Geometry.CELLS) { buf.getShort().toInt() and 0xFFFF }
        require(values.all { it in 0..9 } && notes.all { it and 1.inv() and 0x3FE.inv() == 0 })
        return values to notes
    }
}
