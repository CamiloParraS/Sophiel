package dev.sophiel.core.gate

import android.graphics.Bitmap

/**
 * Cheap pre-filter. Returns true if the frame plausibly contains skin and is
 * therefore worth classifying.
 *
 * Runs on a downscaled copy (64x64 is enough). If the skin-pixel ratio is
 * below [minRatio], skip the classifier entirely and return SAFE.
 *
 * [minRatio] defaults LOW (0.05). A false negative here is invisible to the
 * user and unrecoverable; a false positive merely costs one classifier run.
 * See SPEC.md §6.1.
 */
class SkinGate(private val minRatio: Float = 0.05f) {

    /** True if [frame] has enough skin-toned pixels to be worth classifying. */
    fun shouldClassify(frame: Bitmap): Boolean {
        val scaled = Bitmap.createScaledBitmap(frame, GATE_SIZE, GATE_SIZE, true)
        val pixels = IntArray(GATE_SIZE * GATE_SIZE)
        scaled.getPixels(pixels, 0, GATE_SIZE, 0, 0, GATE_SIZE, GATE_SIZE)
        if (scaled !== frame) scaled.recycle()
        return shouldClassifyPixels(pixels)
    }

    /** Pure pixel-array core of [shouldClassify]; testable without a real [Bitmap]. */
    internal fun shouldClassifyPixels(pixels: IntArray): Boolean = skinRatio(pixels) >= minRatio

    companion object {
        private const val GATE_SIZE = 64

        /** Fraction of [pixels] that pass [isSkinPixel]. */
        internal fun skinRatio(pixels: IntArray): Float {
            if (pixels.isEmpty()) return 0f
            var skinCount = 0
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (isSkinPixel(r, g, b)) skinCount++
            }
            return skinCount.toFloat() / pixels.size
        }

        /** Standard YCbCr skin-tone rule: Cr in [133,173], Cb in [77,127]. */
        internal fun isSkinPixel(r: Int, g: Int, b: Int): Boolean {
            val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
            val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
            return cr in 133.0..173.0 && cb in 77.0..127.0
        }
    }
}
