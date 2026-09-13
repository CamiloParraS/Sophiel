package dev.sophiel.core.model

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Resizes frames to the classifier's expected input and packs them into the
 * raw uint8 RGB byte layout [NsfwClassifier]'s model consumes directly.
 *
 * SINGLE source of truth for preprocessing. `tools/reference_infer.py` must
 * match this exactly (channel order, byte layout) or the M1 parity gate
 * fails — see SPEC.md M1.
 */
object Preprocessor {
    const val INPUT_SIZE = 224
    private const val CHANNELS = 3

    /** Resizes [bitmap] to [INPUT_SIZE] if needed and returns a ready-to-run RGB uint8 buffer. */
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
     * Packs ARGB_8888 pixels into RGB uint8 bytes, dropping alpha.
     *
     * The model's input tensor is itself uint8 with baked-in quantization
     * (see docs/DECISIONS.md D9), so raw 0-255 pixel bytes are the correct
     * input — no float normalization step belongs here.
     */
    internal fun packRgb(pixels: IntArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(pixels.size * CHANNELS)
        buffer.order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            buffer.put(((pixel shr 8) and 0xFF).toByte()) // G
            buffer.put((pixel and 0xFF).toByte()) // B
        }
        buffer.rewind()
        return buffer
    }
}
