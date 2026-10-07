package dev.sophiel.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface

/**
 * Wraps an [ImageReader] as a frame producer for a [android.media.projection.MediaProjection]'s
 * [android.media.projection.MediaProjection.createVirtualDisplay]. Delivers decoded [Bitmap]s,
 * cropped to [crop], on a private background thread via [onFrame] — but only when [decideFrame]
 * says so, checked before decoding. A [FrameDecision.HOLD] frame is decoded and kept, then offered again
 * every [HOLD_RETRY_MS] until it runs, is dropped, or is replaced by a newer image.
 *
 * Handles both SPEC.md §4.5 traps: `rowStride` padding (cropped away before [onFrame] sees the
 * bitmap) and closing every acquired [Image] — a leaked one stalls the reader after [maxImages]
 * frames with no exception thrown.
 */
class FrameSource(
    val width: Int,
    val height: Int,
    private val crop: Rect,
    private val decideFrame: (offeredAgain: Boolean) -> FrameDecision,
    private val onFrame: (bitmap: Bitmap, availableAtMs: Long) -> Unit,
) {
    private val maxImages = 2
    private val thread = HandlerThread("FrameSource").apply { start() }
    private val handler = Handler(thread.looper)

    private val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, maxImages).apply {
        setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val availableAt = SystemClock.elapsedRealtime()
            try {
                // Decide before decoding: the display pushes up to 60-120 frames/s, and copying
                // each one into a Bitmap only for the throttle to drop it was most of the cost.
                // A newer image always replaces a held one.
                dropHeld()
                when (decideFrame(false)) {
                    FrameDecision.RUN -> onFrame(toCroppedBitmap(image), availableAt)
                    FrameDecision.HOLD -> hold(toCroppedBitmap(image), availableAt)
                    FrameDecision.DROP -> Unit
                }
            } finally {
                image.close()
            }
        }, handler)
    }

    // Row-padded copy of the last image, reused: only the listener (this thread) touches it, and
    // onFrame gets a cropped copy. Sized on the first image, since rowStride is only known then.
    private var padded: Bitmap? = null

    /** Applies the SPEC.md §4.5 rowStride crop, then [crop] (system bars, SPEC.md §4.6). Always a new bitmap. */
    private fun toCroppedBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val paddedWidth = image.width + (plane.rowStride - plane.pixelStride * image.width) / plane.pixelStride
        val buffer = padded?.takeIf { it.width == paddedWidth && it.height == image.height }
            ?: Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888).also { padded = it }
        buffer.copyPixelsFromBuffer(plane.buffer)
        // Copies even when the crop is the whole bitmap: a mutable source is never returned as-is.
        return Bitmap.createBitmap(buffer, crop.left, crop.top, crop.width(), crop.height())
    }

    // A frame [decideFrame] can't run yet but must not lose (a probe or a pending tile waits for it).
    // Only this thread touches it.
    private var held: Pair<Bitmap, Long>? = null

    // One retry task: re-posting it replaces the pending one, so a newer held image never adds a loop.
    private val offerHeldTask = Runnable { offerHeld() }

    private fun hold(bitmap: Bitmap, availableAt: Long) {
        held = bitmap to availableAt
        handler.removeCallbacks(offerHeldTask)
        handler.postDelayed(offerHeldTask, HOLD_RETRY_MS)
    }

    private fun offerHeld() {
        val (bitmap, availableAt) = held ?: return
        when (decideFrame(true)) {
            FrameDecision.RUN -> {
                held = null
                onFrame(bitmap, availableAt)
            }
            FrameDecision.HOLD -> handler.postDelayed(offerHeldTask, HOLD_RETRY_MS)
            FrameDecision.DROP -> dropHeld()
        }
    }

    private fun dropHeld() {
        held?.first?.recycle()
        held = null
    }

    val surface: Surface get() = imageReader.surface

    fun close() {
        // Close on the reader's own thread, after any in-progress listener call. Closing from
        // another thread mid-copyPixelsFromBuffer frees the buffer under the copy: SIGSEGV on
        // the FrameSource thread, or "Image is already closed" (Device B crash log, 2026-09-15).
        handler.post {
            imageReader.close()
            padded?.recycle()
            dropHeld()
        }
        thread.quitSafely()
    }
}

/** What [FrameSource] does with an image: decode it and run it (onFrame), decode and hold it to offer again, or drop it undecoded. */
enum class FrameDecision { RUN, HOLD, DROP }

private const val HOLD_RETRY_MS = 10L
