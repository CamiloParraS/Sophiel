package dev.sophiel.capture

/**
 * Detects the black frames produced by `FLAG_SECURE` windows (SPEC.md §4.2), which should
 * surface as "protected content" rather than be scored as SAFE.
 *
 * Requires [BLACK_FRACTION_THRESHOLD] of pixels to be black, not literally every pixel:
 * confirmed on-device (2026-09-14, real banking app, Device B) that a captured frame is never
 * purely black even over a fully `FLAG_SECURE` app. Our own overlays are composited into the
 * capture (SPEC.md §4.2), and below API 30 the system bars aren't cropped out (§4.6).
 */
object BlackFrameDetector {
    private const val BLACK_FRACTION_THRESHOLD = 0.9f

    /** @param pixels ARGB_8888 pixels, as from [android.graphics.Bitmap.getPixels]. */
    fun isMostlyBlack(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return true
        val blackCount = pixels.count { it and 0x00FFFFFF == 0 }
        return blackCount.toFloat() / pixels.size >= BLACK_FRACTION_THRESHOLD
    }
}
