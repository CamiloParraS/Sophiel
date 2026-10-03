package dev.sophiel.capture

import android.graphics.Bitmap
import dev.sophiel.core.TileRect
import kotlin.math.abs

/** Ticket 06's crude, debug-only tile mask (SPEC.md §3.4): its colour and how to spot it in a captured frame. */
object DebugMask {
    /** Never pure black: BlackFrameDetector would read a full-screen Light mask as FLAG_SECURE. */
    const val COLOR = 0xFF9C27B0.toInt()

    private const val SAMPLES = 8 // per side

    // The window's alpha lets (1 - alpha) of the content through, so a captured mask pixel is
    // at most 0.2 * 255 = 51 off COLOR per channel.
    private const val TOLERANCE = 56

    /**
     * True when at least half of [pixels] (sampled from one captured tile) look like the mask.
     * Errs towards "masked": a false yes only delays the probe until it re-masks, while a false
     * no would score the mask itself as SAFE and release the tile.
     */
    fun looksMasked(pixels: IntArray): Boolean = pixels.count { near(it) } * 2 >= pixels.size

    private fun near(pixel: Int) = intArrayOf(16, 8, 0).all { shift ->
        abs((pixel shr shift and 0xFF) - (COLOR shr shift and 0xFF)) <= TOLERANCE
    }

    /** Diagnostic for a probe frame: how many samples matched, and their mean colour. */
    fun describe(pixels: IntArray): String {
        fun mean(shift: Int) = pixels.sumOf { it shr shift and 0xFF } / pixels.size
        return "match=${pixels.count { near(it) }}/${pixels.size} mean=#%02x%02x%02x mask=#%06x"
            .format(mean(16), mean(8), mean(0), COLOR and 0xFFFFFF)
    }

    /** An 8×8 grid of cell centres inside [rect] of a captured frame. */
    fun sample(frame: Bitmap, rect: TileRect): IntArray =
        IntArray(SAMPLES * SAMPLES) { k ->
            frame.getPixel(
                rect.left + (2 * (k % SAMPLES) + 1) * rect.width / (2 * SAMPLES),
                rect.top + (2 * (k / SAMPLES) + 1) * rect.height / (2 * SAMPLES),
            )
        }
}
