package dev.sophiel.core

/** Pixel rectangle, [right]/[bottom] exclusive. Plain type so it is JVM-testable. */
data class TileRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** (cols, rows) for a [width]×[height] frame: landscape swaps the portrait grid (SPEC.md §3.3). */
fun Preset.grid(width: Int, height: Int): Pair<Int, Int> =
    if (width > height) rows to cols else cols to rows

/**
 * Rectangle of tile [index] (row-major on [grid]) for a [width]×[height] frame.
 * Edges are `size * k / n`, so tiles abut exactly and cover every pixel for any size.
 */
fun Preset.tileRect(index: Int, width: Int, height: Int): TileRect {
    val (cols, rows) = grid(width, height)
    val col = index % cols
    val row = index / cols
    return TileRect(
        width * col / cols, height * row / rows,
        width * (col + 1) / cols, height * (row + 1) / rows,
    )
}
