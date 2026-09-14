package dev.sophiel.core.gate

import android.graphics.Bitmap

/**
 * 64-bit difference hash (dHash) for cheap near-duplicate frame detection.
 *
 * Two frames with a small [hammingDistance] between their hashes are visually
 * similar and can share a cached [dev.sophiel.core.Verdict] instead of being
 * re-run through the classifier.
 */
object PerceptualHash {
    private const val HASH_WIDTH = 9 // HASH_HEIGHT + 1 columns so every row has HASH_HEIGHT right-neighbour comparisons
    private const val HASH_HEIGHT = 8

    /** Computes the dHash of [bitmap]. Downscales to a 9x8 grayscale grid first. */
    fun hash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)
        val pixels = IntArray(HASH_WIDTH * HASH_HEIGHT)
        scaled.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)
        if (scaled !== bitmap) scaled.recycle()
        return hashGrayscale(pixels, HASH_WIDTH, HASH_HEIGHT)
    }

    /**
     * Sets bit `(row * (width-1) + col)` when pixel `(col, row)` is darker than
     * its right neighbour `(col+1, row)`. Pure function of pixel data so it is
     * testable without a real [Bitmap].
     */
    internal fun hashGrayscale(pixels: IntArray, width: Int, height: Int): Long {
        var hash = 0L
        var bit = 0
        for (row in 0 until height) {
            for (col in 0 until width - 1) {
                val left = luma(pixels[row * width + col])
                val right = luma(pixels[row * width + col + 1])
                if (left < right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Number of differing bits between two hashes. */
    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    private fun luma(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r + g + b) / 3
    }
}
