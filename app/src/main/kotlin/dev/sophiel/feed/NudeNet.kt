package dev.sophiel.feed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.nio.FloatBuffer

/** Spike models (D24). [asset]/[inputSize] are null/0 for GantMan, the shipped D12 model. */
enum class SpikeModel(val label: String, val asset: String?, val inputSize: Int) {
    GANTMAN("GantMan 224", null, 0),
    NUDENET_320N("NudeNet 320n", "nudenet_320n.onnx", 320),
    NUDENET_640M("NudeNet 640m", "nudenet_640m.onnx", 640),
}

/** Unsafe score for a NudeNet result: the best exposed-class detection, 0 if none. */
fun List<Detection>.unsafeScore(): Float = filter { it.unsafe }.maxOfOrNull { it.score } ?: 0f

/** One NudeNet detection; [box] is normalised to the source bitmap. */
data class Detection(val label: String, val score: Float, val box: RectF) {
    val unsafe get() = label in UNSAFE
}

/**
 * Spike: NudeNet v3 (notAI-tech, YOLOv8 export) on ONNX Runtime CPU. Pre/post-processing
 * mirrors upstream `nudenet.py` (v3 branch): pad bottom/right to a black square, resize,
 * RGB / 255, CHW; output `[1, 4 + 18, anchors]` with centre-xywh boxes in input pixels.
 */
class NudeNet private constructor(private val session: OrtSession, private val size: Int) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val inputName = session.inputNames.first()

    /** Detections at or above upstream's 0.2 floor after per-class NMS, highest score first. */
    fun detect(bitmap: Bitmap): List<Detection> {
        val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(square).drawBitmap(bitmap, Matrix().apply { setScale(scale, scale) }, Paint(Paint.FILTER_BITMAP_FLAG))
        val plane = size * size
        val pixels = IntArray(plane).also { square.getPixels(it, 0, size, 0, 0, size, size) }
        square.recycle()
        val chw = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = (p shr 16 and 0xFF) / 255f
            chw[plane + i] = (p shr 8 and 0xFF) / 255f
            chw[2 * plane + i] = (p and 0xFF) / 255f
        }

        val out = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { (it[0] as OnnxTensor).floatBuffer }
        }
        val anchors = out.capacity() / (4 + LABELS.size)
        val found = ArrayList<Detection>()
        for (a in 0 until anchors) {
            var cls = 0
            for (c in 1 until LABELS.size) if (out[(4 + c) * anchors + a] > out[(4 + cls) * anchors + a]) cls = c
            val score = out[(4 + cls) * anchors + a]
            if (score < MIN_SCORE) continue
            val cx = out[a]; val cy = out[anchors + a]; val w = out[2 * anchors + a]; val h = out[3 * anchors + a]
            val toX = 1f / (scale * bitmap.width); val toY = 1f / (scale * bitmap.height)
            found += Detection(
                LABELS[cls], score,
                RectF((cx - w / 2) * toX, (cy - h / 2) * toY, (cx + w / 2) * toX, (cy + h / 2) * toY),
            )
        }
        // Per-class NMS (ticket 17: boxes drive masks, so two instances of a class need two boxes).
        // Upstream's NMS is class-agnostic; per class, a covered region can't suppress an exposed one.
        val kept = ArrayList<Detection>()
        for (d in found.sortedByDescending { it.score }) {
            if (kept.none { it.label == d.label && iou(it.box, d.box) > NMS_IOU }) kept += d
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        return w * h / (a.width() * a.height() + b.width() * b.height() - w * h)
    }

    override fun close() = session.close()

    companion object {
        private const val MIN_SCORE = 0.2f
        private const val NMS_IOU = 0.45f // upstream nudenet.py

        /**
         * Copies the asset to filesDir once (ORT wants a path; a 100 MB byte[] risks OOM).
         * [size] is passed in because the exported input shape is dynamic (-1).
         */
        fun load(context: Context, asset: String, size: Int): NudeNet {
            val file = File(context.filesDir, asset)
            if (!file.exists()) {
                val tmp = File(context.filesDir, "$asset.tmp")
                context.assets.open(asset).use { i -> tmp.outputStream().use { i.copyTo(it) } }
                check(tmp.renameTo(file)) { "could not install $asset" }
            }
            return NudeNet(OrtEnvironment.getEnvironment().createSession(file.path, OrtSession.SessionOptions()), size)
        }
    }
}

private val LABELS = listOf(
    "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED",
    "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED",
    "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE",
    "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED",
    "BUTTOCKS_COVERED",
)

// Calibration knob: which classes count toward the unsafe score (max over these).
private val UNSAFE = setOf(
    "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED", "FEMALE_GENITALIA_EXPOSED",
    "ANUS_EXPOSED", "MALE_GENITALIA_EXPOSED",
)
