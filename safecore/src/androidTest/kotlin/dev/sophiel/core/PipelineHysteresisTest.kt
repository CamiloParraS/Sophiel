package dev.sophiel.core

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sophiel.core.gate.SkinGate
import dev.sophiel.core.model.NsfwClassifier
import dev.sophiel.core.policy.PolicyEngine
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A static screen is the same frame repeatedly, so every frame after the
 * first is a cache hit. Hysteresis must still advance on those hits, or
 * EXPLICIT can never engage on static content.
 */
@RunWith(AndroidJUnit4::class)
class PipelineHysteresisTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun cacheHitsStillAdvanceHysteresis() = runBlocking {
        val frame = assets.open("fixtures/fixture_00.png").use { BitmapFactory.decodeStream(it) }
        // Thresholds of 0 put every score "above", isolating hysteresis from model output.
        val pipeline = DetectionPipeline(
            classifier = NsfwClassifier.load(context),
            policy = PolicyEngine(explicitThreshold = 0f, suggestiveThreshold = 0f),
            dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
            gate = SkinGate(minRatio = 0f),
        )
        try {
            assertEquals(Severity.SUGGESTIVE, pipeline.analyze(frame).severity)
            val second = pipeline.analyze(frame)
            assertTrue("second frame should be a cache hit", second.cacheHit)
            assertEquals(Severity.EXPLICIT, second.severity)
        } finally {
            pipeline.close()
            frame.recycle()
        }
    }
}
