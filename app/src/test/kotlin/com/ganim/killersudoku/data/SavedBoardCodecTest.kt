package com.ganim.killersudoku.data

import com.ganim.killersudoku.game.BoardSnapshot
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class SavedBoardCodecTest {

    private fun board() = SavedBoard(
        values = IntArray(81).also { it[3] = 7 },
        notes = IntArray(81).also { it[4] = 0b10_0000_0110 },
        mistakes = 2,
        elapsedMs = 91_000,
        undo = listOf(BoardSnapshot(IntArray(81), IntArray(81))),
        hintsUsed = 3,
    )

    @Test
    fun `round trips every field, including hints used`() {
        val back = SavedBoardCodec.decode(SavedBoardCodec.encode(board())).shouldNotBeNull()
        back.values.toList() shouldBe board().values.toList()
        back.notes.toList() shouldBe board().notes.toList()
        back.mistakes shouldBe 2
        back.elapsedMs shouldBe 91_000
        back.undo shouldBe board().undo
        back.hintsUsed shouldBe 3
    }

    @Test
    fun `a version 1 board saved before the update still loads, with no hints counted`() {
        // v1: version | mistakes | elapsed | board | undoCount - no hintsUsed byte.
        val b = board()
        val buf = ByteBuffer.allocate(1 + 1 + 8 + 243 + 1)
        buf.put(1).put(b.mistakes.toByte()).putLong(b.elapsedMs)
        b.values.forEach { buf.put(it.toByte()) }
        b.notes.forEach { buf.putShort(it.toShort()) }
        buf.put(0)
        val back = SavedBoardCodec.decode(buf.array()).shouldNotBeNull()
        back.values.toList() shouldBe b.values.toList()
        back.mistakes shouldBe 2
        back.hintsUsed shouldBe 0
    }

    @Test
    fun `only the newest undo steps are kept`() {
        val many = board().let { SavedBoard(it.values, it.notes, 0, 0, List(80) { i -> BoardSnapshot(IntArray(81) { i % 10 }, IntArray(81)) }) }
        val back = SavedBoardCodec.decode(SavedBoardCodec.encode(many)).shouldNotBeNull()
        back.undo.size shouldBe SavedBoardCodec.MAX_UNDO
        back.undo.last() shouldBe many.undo.last()
    }
}
