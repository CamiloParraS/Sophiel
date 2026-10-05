package dev.sophiel.capture

import android.graphics.Bitmap
import dev.sophiel.core.TileRect
import kotlin.math.pow
import kotlin.math.sqrt

/** The tile mask look (SPEC.md §3.4, D28-D30) and how to spot it in a captured frame. */
object DebugMask {
    /** Pattern side, px. Every blob period divides it, so it tiles seamlessly. */
    const val SIDE = 256
    const val PERIOD = 16
    const val BRIGHTNESS = 100

    /** Muted colours for the camo, multiplied into its brightness. Any works: the check is colour-free (D30). */
    val TINTS = linkedMapOf(
        "Graphite" to 0xE1E1E1, "Slate" to 0xC8D6EB, "Sand" to 0xEBDEC8, "Sage" to 0xC8DECD, "Purple" to 0xFF40FF,
    )

    // Human pick on Device A (ticket 13): 16 px blobs, brightness 100 (luma ~82), Slate.
    const val TINT_NAME = "Slate"
    private val TINT = TINTS.getValue(TINT_NAME)

    /** The shipped camo, ARGB, [SIDE]². Seeded and tiled, never regenerated (D18). */
    val PATTERN = camo(PERIOD, BRIGHTNESS, TINT)

    /** [PATTERN]'s luminance, box-blurred over 3x3 like the capture's ~3x downscale. Indexed by screen pixel mod [SIDE]. */
    private val EXPECTED = FloatArray(SIDE * SIDE) { k ->
        var sum = 0f
        for (dy in -1..1) for (dx in -1..1) {
            sum += luma(PATTERN[Math.floorMod(k / SIDE + dy, SIDE) * SIDE + Math.floorMod(k % SIDE + dx, SIDE)])
        }
        sum / 9
    }

    fun patternBitmap(pattern: IntArray = PATTERN): Bitmap = Bitmap.createBitmap(pattern, SIDE, SIDE, Bitmap.Config.ARGB_8888)

    /**
     * High-contrast blobs at [period], period/2 and period/4 px plus 1 px grain, [SIDE]² and seamless
     * when tiled. 1 px grain alone is finer than the eye resolves on these screens, so it reads as
     * flat colour and the content's large shapes show through at 21%; the blobs put the mask's
     * contrast at the sizes those shapes have. Histogram-equalised, then skewed so the brightness
     * mean is [brightness], then multiplied by [tint] (RGB). No channel below 2: never pure black,
     * even at window alpha (BlackFrameDetector would read a full-screen Light mask as FLAG_SECURE).
     */
    fun camo(period: Int, brightness: Int, tint: Int, seed: Long = 11): IntArray {
        val rnd = java.util.Random(seed)
        val n = DoubleArray(SIDE * SIDE)
        fun smooth(t: Double) = t * t * (3 - 2 * t)
        for (octave in 0 until 3) {
            val p = period shr octave
            val cells = SIDE / p
            val lattice = DoubleArray(cells * cells) { rnd.nextDouble() }
            fun at(i: Int, j: Int) = lattice[j % cells * cells + i % cells]
            for (y in 0 until SIDE) for (x in 0 until SIDE) {
                val i = x / p
                val j = y / p
                val fx = smooth(x % p / p.toDouble())
                val top = at(i, j) + (at(i + 1, j) - at(i, j)) * fx
                val bottom = at(i, j + 1) + (at(i + 1, j + 1) - at(i, j + 1)) * fx
                n[y * SIDE + x] += (top + (bottom - top) * smooth(y % p / p.toDouble())) / (1 shl octave)
            }
        }
        for (k in n.indices) n[k] += 0.6 * rnd.nextDouble() // the grain
        // Rank -> u uniform in [0,1); v = 1 + 254 u^gamma has mean 1 + 254 / (gamma + 1) = brightness.
        val sorted = n.sortedArray()
        val gamma = 254.0 / (brightness - 1) - 1
        return IntArray(n.size) {
            val v = 1 + 254 * (sorted.binarySearch(n[it]) / n.size.toDouble()).pow(gamma)
            fun ch(shift: Int) = (v * (tint shr shift and 0xFF) / 255).toInt().coerceAtLeast(2)
            (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    private fun luma(c: Int) = 0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)

    /** What the mask alone puts at screen pixel ([x], [y]): the overlay's shader is anchored to the screen. */
    fun expectedAt(x: Int, y: Int) = EXPECTED[Math.floorMod(y, SIDE) * SIDE + Math.floorMod(x, SIDE)]

    private const val SAMPLES = 12 // per side, centre 6x6 (middle half) skipped: room for the lock chip (D28)

    // A masked tile is 0.79 * pattern + 0.21 * content, so its luminance tracks the pattern's blobs;
    // bare content is unrelated to our seeded blobs. Device run (2026-10-03, ticket 13, 215 probe
    // frames, portrait + landscape): masked 0.67-0.89, bare -0.29-0.26. Mid-gap, leaning to "masked".
    private const val THRESHOLD = 0.45

    /** One tile's captured samples and, for each, what the mask alone would put there. */
    class Samples(val pixels: IntArray, val expected: FloatArray)

    /** Samples [rect] of [frame]; [toScreen] maps a frame pixel to the screen pixel the overlay drew it from. */
    fun sample(frame: Bitmap, rect: TileRect, toScreen: (Int, Int) -> Pair<Int, Int>): Samples {
        val points = samplePoints(rect.width, rect.height).map { (x, y) -> rect.left + x to rect.top + y }
        return Samples(
            points.map { (x, y) -> frame.getPixel(x, y) }.toIntArray(),
            points.map { (x, y) -> toScreen(x, y).let { (sx, sy) -> expectedAt(sx, sy) } }.toFloatArray(),
        )
    }

    /** Pearson correlation of captured luminance with the pattern's; 0 when either is flat. */
    fun correlation(s: Samples): Double {
        val a = s.pixels.map { luma(it).toDouble() }
        val b = s.expected.map { it.toDouble() }
        val (ma, mb) = a.average() to b.average()
        val cov = a.indices.sumOf { (a[it] - ma) * (b[it] - mb) }
        val va = a.sumOf { (it - ma) * (it - ma) }
        val vb = b.sumOf { (it - mb) * (it - mb) }
        return if (va < 1e-6 || vb < 1e-6) 0.0 else cov / sqrt(va * vb)
    }

    /**
     * True when the tile's captured pixels follow the mask pattern. Colour-free, so the tint is free.
     * Errs towards "masked": a false yes only delays the probe until it re-masks, while a false
     * no would score the mask itself as SAFE and release the tile.
     */
    fun looksMasked(s: Samples): Boolean = correlation(s) >= THRESHOLD

    /** Diagnostic for a probe frame. */
    fun describe(s: Samples): String = "r=%.2f".format(correlation(s))

    /** Cell centres of a 12×12 grid over a [width]×[height] tile, centre 6×6 skipped. */
    fun samplePoints(width: Int, height: Int): List<Pair<Int, Int>> =
        (0 until SAMPLES * SAMPLES).filterNot { k -> k % SAMPLES in 3..8 && k / SAMPLES in 3..8 }.map { k ->
            (2 * (k % SAMPLES) + 1) * width / (2 * SAMPLES) to (2 * (k / SAMPLES) + 1) * height / (2 * SAMPLES)
        }
}
