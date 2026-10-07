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

    /**
     * Resizes [bitmap] to [INPUT_SIZE] if needed and returns a ready-to-run float32 RGB buffer.
     * A caller on one thread can pass [pixels] and [buffer] to reuse them; [buffer] is overwritten and returned.
     */
    fun toInputBuffer(
        bitmap: Bitmap,
        pixels: IntArray = IntArray(INPUT_SIZE * INPUT_SIZE),
        buffer: ByteBuffer = inputBuffer(INPUT_SIZE * INPUT_SIZE),
    ): ByteBuffer {
        val scaled = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== bitmap) scaled.recycle()
        return packRgb(pixels, buffer)
    }

    /** A direct, native-order buffer for [pixelCount] RGB float32 pixels. */
    fun inputBuffer(pixelCount: Int = INPUT_SIZE * INPUT_SIZE): ByteBuffer =
        ByteBuffer.allocateDirect(pixelCount * CHANNELS * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

    /**
     * Packs ARGB_8888 pixels into NHWC float32 RGB in `[0,1]`, dropping alpha.
     *
     * `[0,1]` (divide by 255) matches GantMan/nsfw_model's own `predict.py`
     * (see docs/DECISIONS.md D12).
     */
    internal fun packRgb(pixels: IntArray, buffer: ByteBuffer = inputBuffer(pixels.size)): ByteBuffer {
        buffer.clear()
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f) // G
            buffer.putFloat((pixel and 0xFF) / 255f) // B
        }
        buffer.rewind()
        return buffer
    }
}
