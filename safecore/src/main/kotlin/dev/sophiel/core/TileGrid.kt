package dev.sophiel.core

/** Pixel rectangle, [right]/[bottom] exclusive. Plain type so it is JVM-testable. */
data class TileRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Rectangle of tile [index] (row-major) for a [width]×[height] frame. Edges are
 * `size * k / n`, so tiles abut exactly and cover every pixel for any size.
 */
fun Preset.tileRect(index: Int, width: Int, height: Int): TileRect {
    val col = index % cols
    val row = index / cols
    return TileRect(
        width * col / cols, height * row / rows,
        width * (col + 1) / cols, height * (row + 1) / rows,
    )
}
