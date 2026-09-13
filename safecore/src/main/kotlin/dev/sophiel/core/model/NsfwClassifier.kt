package dev.sophiel.core.model

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * Wraps the bundled `nsfw.tflite` interpreter.
 *
 * Model provenance, license, and output semantics are recorded in
 * docs/DECISIONS.md (D9). The model is a full-integer-quantized 2-class
 * (nonnude/nude) classifier; [classify] returns the "nude" class probability
 * as the unsafe score in [0,1].
 */
class NsfwClassifier private constructor(private val interpreter: Interpreter) {

    /** Runs one [Preprocessor]-shaped input through the interpreter. Blocking; call off the main thread. */
    fun classify(input: ByteBuffer): Float {
        val output = ByteBuffer.allocateDirect(OUTPUT_CLASSES).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        output.get() // "nonnude" class byte, unused
        val unsafeRaw = output.get().toInt() and 0xFF
        return dequantize(unsafeRaw, OUTPUT_SCALE, OUTPUT_ZERO_POINT)
    }

    /** Releases the interpreter. The instance is unusable afterwards. */
    fun close() = interpreter.close()

    companion object {
        private const val MODEL_ASSET = "nsfw.tflite"
        private const val OUTPUT_CLASSES = 2

        // Output tensor quantization params baked into nsfw.tflite (see docs/DECISIONS.md D9).
        private const val OUTPUT_SCALE = 0.00390625f
        private const val OUTPUT_ZERO_POINT = 0

        /** (raw - zeroPoint) * scale. Pure so it can be unit-tested without a loaded interpreter. */
        internal fun dequantize(raw: Int, scale: Float, zeroPoint: Int): Float =
            (raw - zeroPoint) * scale

        /** Loads and memory-maps `nsfw.tflite` from assets. Throws if the asset is missing or compressed. */
        fun load(context: Context): NsfwClassifier {
            val assetFd = context.assets.openFd(MODEL_ASSET)
            val model = assetFd.createInputStream().channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength,
            )
            return NsfwClassifier(Interpreter(model))
        }
    }
}
