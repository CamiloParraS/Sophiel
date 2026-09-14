package dev.sophiel.core.model

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Resizes frames to the classifier's expected input and packs them into the
 * float32 RGB layout [NsfwClassifier]'s model consumes.
 *
 * SINGLE source of truth for preprocessing. `tools/reference_infer.py` must
 * match this exactly (channel order, normalization, byte layout) or the M1
 * parity gate fails — see SPEC.md M1.
 */
object Preprocessor {
    const val INPUT_SIZE = 224
    private const val CHANNELS = 3

    /** Resizes [bitmap] to [INPUT_SIZE] if needed and returns a ready-to-run float32 RGB buffer. */
    fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== bitmap) scaled.recycle()
        return packRgb(pixels)
    }

    /**
     * Packs ARGB_8888 pixels into NHWC float32 RGB in `[0,1]`, dropping alpha.
     *
     * `[0,1]` (divide by 255) matches GantMan/nsfw_model's own `predict.py`
     * (see docs/DECISIONS.md D12).
     */
    internal fun packRgb(pixels: IntArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(pixels.size * CHANNELS * Float.SIZE_BYTES)
        buffer.order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f) // G
            buffer.putFloat((pixel and 0xFF) / 255f) // B
        }
        buffer.rewind()
        return buffer
    }
}
