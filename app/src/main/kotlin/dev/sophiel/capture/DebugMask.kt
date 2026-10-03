package dev.sophiel.capture

import android.graphics.Bitmap
import dev.sophiel.core.TileRect
import kotlin.math.sqrt

/** The tile mask look (SPEC.md §3.4, D28/D29) and how to spot it in a captured frame. */
object DebugMask {
    private const val NOISE_SIZE = 64

    /**
     * Strong ~1 px grain over the full brightness range, tinted purple so its mean sits well off
     * the grey axis that ordinary photos average to (D29). Seeded and tiled, never regenerated:
     * a mask that changes per draw makes a static screen send frames forever (D18).
     * Never pure black: BlackFrameDetector would read a full-screen Light mask as FLAG_SECURE.
     */
    val NOISE = java.util.Random(11).let { rnd ->
        IntArray(NOISE_SIZE * NOISE_SIZE) {
            val v = 1 + rnd.nextInt(255)
            (0xFF shl 24) or (v shl 16) or (v / 4 shl 8) or v
        }
    }

    /** Mean (r, g, b) of [NOISE], about (128, 32, 128). */
    val MEAN = Triple(mean(NOISE, 16), mean(NOISE, 8), mean(NOISE, 0))

    /** [NOISE] as a bitmap, for a REPEAT `BitmapShader`. */
    fun noiseBitmap(): Bitmap = Bitmap.createBitmap(NOISE, NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888)

    private const val SAMPLES = 8 // per side, centre 4x4 skipped: room for the lock chip (D28)

    // A masked tile's captured mean is OVERLAY_ALPHA * MEAN + 0.21 * content, so it lies within
    // 0.21 * 287 ≈ 60 of MEAN (content at the farthest RGB corner). Any grey is ≥ 77 away.
    // ponytail: starting value from that maths; set from device logs (ticket 06 device run).
    private const val TOLERANCE = 70.0

    /**
     * True when the mean colour of [pixels] (sampled from one captured tile) is near the mask's.
     * Errs towards "masked": a false yes only delays the probe until it re-masks, while a false
     * no would score the mask itself as SAFE and release the tile.
     */
    fun looksMasked(pixels: IntArray): Boolean = distance(pixels) <= TOLERANCE

    private fun distance(pixels: IntArray): Double {
        val (r, g, b) = MEAN
        fun sq(x: Double) = x * x
        return sqrt(sq(mean(pixels, 16) - r) + sq(mean(pixels, 8) - g) + sq(mean(pixels, 0) - b))
    }

    private fun mean(pixels: IntArray, shift: Int) = pixels.sumOf { it shr shift and 0xFF } / pixels.size.toDouble()

    /** Diagnostic for a probe frame: mean colour of the samples and its distance from the mask's. */
    fun describe(pixels: IntArray): String =
        "mean=#%02x%02x%02x mask=#%02x%02x%02x d=%.0f".format(
            mean(pixels, 16).toInt(), mean(pixels, 8).toInt(), mean(pixels, 0).toInt(),
            MEAN.first.toInt(), MEAN.second.toInt(), MEAN.third.toInt(), distance(pixels),
        )

    /** Cell centres of an 8×8 grid over a [width]×[height] tile, centre 4×4 skipped. */
    fun samplePoints(width: Int, height: Int): List<Pair<Int, Int>> =
        (0 until SAMPLES * SAMPLES).filterNot { k -> k % SAMPLES in 2..5 && k / SAMPLES in 2..5 }.map { k ->
            (2 * (k % SAMPLES) + 1) * width / (2 * SAMPLES) to (2 * (k / SAMPLES) + 1) * height / (2 * SAMPLES)
        }

    fun sample(frame: Bitmap, rect: TileRect): IntArray =
        samplePoints(rect.width, rect.height).map { (x, y) -> frame.getPixel(rect.left + x, rect.top + y) }.toIntArray()
}
