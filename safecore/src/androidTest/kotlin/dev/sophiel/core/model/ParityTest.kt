package dev.sophiel.core.model

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sophiel.core.DetectionPipeline
import dev.sophiel.core.Preset
import dev.sophiel.core.gate.SkinGate
import dev.sophiel.core.policy.PolicyEngine
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1.V3 — the parity gate. For every fixture, the on-device interpreter's
 * score must match `tools/reference_infer.py`'s score within 1e-2 absolute.
 *
 * If this fails, do not add a fudge factor or lower the tolerance: check (in
 * order) channel order, normalization range, resize interpolation, and
 * tensor layout in Preprocessor.kt. See SPEC.md M1.
 */
@RunWith(AndroidJUnit4::class)
class ParityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun onDeviceScoresMatchThePythonReferenceWithinTolerance() {
        val expected = loadExpectedLogits()
        check(expected.isNotEmpty()) { "expected_logits.json has no fixtures" }

        val classifier = NsfwClassifier.load(context)
        try {
            for ((fileName, expectedScore) in expected) {
                val bitmap = assets.open("fixtures/$fileName").use { BitmapFactory.decodeStream(it) }
                val actualScore = classifier.classify(Preprocessor.toInputBuffer(bitmap))
                bitmap.recycle()

                assertEquals(
                    "score for $fileName",
                    expectedScore,
                    actualScore.toDouble(),
                    TOLERANCE,
                )
            }
        } finally {
            classifier.close()
        }
    }

    /** Ticket 02: Light's single tile must score what the whole-frame path scored. */
    @Test
    fun lightTileMatchesThePythonReferenceWithinTolerance() = runBlocking {
        val pipeline = DetectionPipeline(
            classifier = NsfwClassifier.load(context),
            policy = PolicyEngine(),
            dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
            gate = SkinGate(minRatio = 0f), // never gate, so every fixture reaches the classifier
        )
        try {
            for ((fileName, expectedScore) in loadExpectedLogits()) {
                val bitmap = assets.open("fixtures/$fileName").use { BitmapFactory.decodeStream(it) }
                val tile = pipeline.analyze(bitmap, Preset.LIGHT).single()
                bitmap.recycle()
                assertEquals("Light score for $fileName", expectedScore, tile.score.toDouble(), TOLERANCE)
            }
        } finally {
            pipeline.close()
        }
    }

    private fun loadExpectedLogits(): Map<String, Double> {
        val json = assets.open("fixtures/expected_logits.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        return obj.keys().asSequence().associateWith { obj.getDouble(it) }
    }

    companion object {
        private const val TOLERANCE = 1e-2
    }
}
