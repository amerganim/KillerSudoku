package com.ganim.killersudoku.engine.model

/**
 * Fixed 9x9 geometry. Cells are indexed 0..80 in row-major order.
 * Houses 0..8 are rows, 9..17 columns, 18..26 boxes.
 */
object Geometry {
    const val SIZE = 9
    const val CELLS = 81
    const val HOUSES = 27

    fun row(cell: Int): Int = cell / 9
    fun col(cell: Int): Int = cell % 9
    fun box(cell: Int): Int = (cell / 27) * 3 + (cell % 9) / 3
    fun cell(row: Int, col: Int): Int = row * 9 + col

    /** Cells of each house. */
    val houses: Array<IntArray> = Array(HOUSES) { h ->
        when {
            h < 9 -> IntArray(9) { cell(h, it) }
            h < 18 -> IntArray(9) { cell(it, h - 9) }
            else -> {
                val b = h - 18
                IntArray(9) { cell((b / 3) * 3 + it / 3, (b % 3) * 3 + it % 3) }
            }
        }
    }

    /** The three houses (row, column, box) containing each cell. */
    val housesOf: Array<IntArray> = Array(CELLS) { intArrayOf(row(it), 9 + col(it), 18 + box(it)) }

    /** The 20 cells sharing a house with each cell. */
    val peers: Array<IntArray> = Array(CELLS) { c ->
        (0 until CELLS).filter { it != c && (row(it) == row(c) || col(it) == col(c) || box(it) == box(c)) }
            .toIntArray()
    }

    fun sharesHouse(a: Int, b: Int): Boolean =
        row(a) == row(b) || col(a) == col(b) || box(a) == box(b)

    fun areOrthogonal(a: Int, b: Int): Boolean =
        (row(a) == row(b) && kotlin.math.abs(col(a) - col(b)) == 1) ||
            (col(a) == col(b) && kotlin.math.abs(row(a) - row(b)) == 1)

    fun orthogonalNeighbours(cell: Int): IntArray {
        val r = row(cell)
        val c = col(cell)
        return buildList {
            if (r > 0) add(cell - 9)
            if (r < 8) add(cell + 9)
            if (c > 0) add(cell - 1)
            if (c < 8) add(cell + 1)
        }.toIntArray()
    }

    /** Player-facing names, 1-based, used in hint text. */
    fun houseName(house: Int): String = when {
        house < 9 -> "row ${house + 1}"
        house < 18 -> "column ${house - 8}"
        else -> "box ${house - 17}"
    }

    fun cellName(cell: Int): String = "R${row(cell) + 1}C${col(cell) + 1}"
}
