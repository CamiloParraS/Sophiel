package dev.sophiel.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

/**
 * Wraps an [ImageReader] as a frame producer for a [android.media.projection.MediaProjection]'s
 * [android.media.projection.MediaProjection.createVirtualDisplay]. Delivers decoded [Bitmap]s
 * on a private background thread via [onFrame].
 *
 * Handles both SPEC.md §4.5 traps: `rowStride` padding (cropped away before [onFrame] sees the
 * bitmap) and closing every acquired [Image] — a leaked one stalls the reader after [maxImages]
 * frames with no exception thrown.
 */
class FrameSource(
    val width: Int,
    val height: Int,
    private val onFrame: (Bitmap) -> Unit,
) {
    private val maxImages = 2
    private val thread = HandlerThread("FrameSource").apply { start() }
    private val handler = Handler(thread.looper)

    private val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, maxImages).apply {
        setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                onFrame(image.toCroppedBitmap(width, height))
            } finally {
                image.close()
            }
        }, handler)
    }

    val surface: Surface get() = imageReader.surface

    fun close() {
        // Close on the reader's own thread, after any in-progress listener call. Closing from
        // another thread mid-copyPixelsFromBuffer frees the buffer under the copy: SIGSEGV on
        // the FrameSource thread, or "Image is already closed" (Device B crash log, 2026-09-15).
        handler.post { imageReader.close() }
        thread.quitSafely()
    }
}

/** Applies the SPEC.md §4.5 rowStride crop. */
private fun Image.toCroppedBitmap(width: Int, height: Int): Bitmap {
    val plane = planes[0]
    val rowPadding = plane.rowStride - plane.pixelStride * width
    val padded = Bitmap.createBitmap(
        width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888,
    ).apply { copyPixelsFromBuffer(plane.buffer) }
    return if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
}
