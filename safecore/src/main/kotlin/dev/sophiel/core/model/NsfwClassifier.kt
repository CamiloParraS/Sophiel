package dev.sophiel.core.model

import android.content.Context
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * Wraps the bundled `nsfw.tflite` interpreter.
 *
 * Model provenance, license, and output semantics are recorded in
 * docs/DECISIONS.md (D12). The model is GantMan/nsfw_model's 5-class
 * MobileNetV2 (drawings, hentai, neutral, porn, sexy) with float32 I/O;
 * [classify] returns the unsafe score in [0,1] per [unsafeScore].
 */
class NsfwClassifier private constructor(private val interpreter: Interpreter) {

    /** Runs one [Preprocessor]-shaped input through the interpreter. Blocking; call off the main thread. */
    fun classify(input: ByteBuffer): Float {
        val output = Array(1) { FloatArray(CLASSES) }
        interpreter.run(input, output)
        return unsafeScore(output[0])
    }

    /** Releases the interpreter. The instance is unusable afterwards. */
    fun close() = interpreter.close()

    companion object {
        private const val MODEL_ASSET = "nsfw.tflite"

        // Output order of GantMan/nsfw_model: drawings, hentai, neutral, porn, sexy.
        private const val CLASSES = 5
        private const val HENTAI = 1
        private const val PORN = 3
        private const val SEXY = 4

        /**
         * Unsafe probability: `hentai + porn + sexy`. `sexy` is included because
         * not all NSFW content is nudity; the cost is that swimwear-style frames
         * score high too (docs/DECISIONS.md D12). Pure so it can be unit-tested.
         */
        internal fun unsafeScore(probabilities: FloatArray): Float =
            probabilities[HENTAI] + probabilities[PORN] + probabilities[SEXY]

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
