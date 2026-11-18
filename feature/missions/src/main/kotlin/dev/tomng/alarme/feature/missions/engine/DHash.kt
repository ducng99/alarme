package dev.tomng.alarme.feature.missions.engine

/**
 * The 64-bit difference hash used by the photo mission. The centered square of a luminance image is
 * area-averaged down to 9x8 cells; each bit says whether a cell is brighter than its right
 * neighbour. Two photos of the same scene land within [MATCH_THRESHOLD] bits of each other.
 */
object DHash {
    const val MATCH_THRESHOLD = 12

    private const val COLUMNS = 9
    private const val ROWS = 8

    /** [luma] holds one 0..255 value per pixel, row-major, [width] x [height]. */
    fun compute(luma: IntArray, width: Int, height: Int): Long {
        require(width >= COLUMNS && height >= COLUMNS) { "image too small: ${width}x$height" }
        require(luma.size >= width * height) { "luma has ${luma.size} values for ${width}x$height" }
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cells =
            Array(ROWS) { row ->
                val y0 = top + row * side / ROWS
                val y1 = top + (row + 1) * side / ROWS
                LongArray(COLUMNS) { column ->
                    val x0 = left + column * side / COLUMNS
                    val x1 = left + (column + 1) * side / COLUMNS
                    areaAverage(luma, width, x0, x1, y0, y1)
                }
            }
        var hash = 0L
        for (row in 0 until ROWS) {
            for (column in 0 until COLUMNS - 1) {
                if (cells[row][column] > cells[row][column + 1]) {
                    hash = hash or (1L shl (row * (COLUMNS - 1) + column))
                }
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun matches(reference: Long, candidate: Long): Boolean =
        distance(reference, candidate) <= MATCH_THRESHOLD

    /** Mean scaled by 1024 so integer comparison keeps sub-level precision. */
    private fun areaAverage(luma: IntArray, width: Int, x0: Int, x1: Int, y0: Int, y1: Int): Long {
        var sum = 0L
        for (y in y0 until y1) {
            val rowStart = y * width
            for (x in x0 until x1) sum += luma[rowStart + x]
        }
        return sum * 1024 / ((x1 - x0) * (y1 - y0))
    }
}
