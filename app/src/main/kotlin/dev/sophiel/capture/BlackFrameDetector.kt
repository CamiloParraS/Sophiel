package dev.sophiel.capture

/**
 * Detects the all-black frames produced by `FLAG_SECURE` windows (SPEC.md §4.2), which should
 * surface as "protected content" rather than be scored as SAFE.
 */
object BlackFrameDetector {
    /** @param pixels ARGB_8888 pixels, as from [android.graphics.Bitmap.getPixels]. */
    fun isAllBlack(pixels: IntArray): Boolean = pixels.all { it and 0x00FFFFFF == 0 }
}
