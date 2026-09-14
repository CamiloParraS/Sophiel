package dev.sophiel.core

import android.graphics.BitmapFactory
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2.V5 supporting evidence: [Detector.close] + re-creation across 5 cycles
 * must not grow native heap (interpreter + XNNPACK packed weights, ~tens of MB
 * per leaked instance) or leak the inference thread. SPEC.md still asks for
 * the Android Studio Profiler pass; this makes the check repeatable.
 */
@RunWith(AndroidJUnit4::class)
class DetectorLeakTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun closeAndRecreateFiveTimesDoesNotLeak() = runBlocking {
        val frame = assets.open("fixtures/fixture_00.png").use { BitmapFactory.decodeStream(it) }
        val nativeKb = LongArray(CYCLES)
        val threads = IntArray(CYCLES)
        settle()
        val baselineKb = Debug.getNativeHeapAllocatedSize() / 1024
        Log.i("Sophiel", "V5 baseline: nativeHeap=$baselineKb KB")

        repeat(CYCLES) { i ->
            val detector = DetectorFactory.create(context)
            repeat(FRAMES_PER_CYCLE) { detector.analyze(frame) }
            detector.close()
            settle()
            nativeKb[i] = Debug.getNativeHeapAllocatedSize() / 1024
            threads[i] = Thread.getAllStackTraces().size
            Log.i("Sophiel", "V5 cycle ${i + 1}: nativeHeap=${nativeKb[i]} KB, threads=${threads[i]}")
        }
        frame.recycle()

        // Compare against the pre-create baseline, not cycle-to-cycle: a skipped close()
        // shows up as one ~30 MB jump that GC then partly reclaims, so deltas between
        // later cycles stay small even while leaking (verified by removing close()).
        val worstKb = nativeKb.max() - baselineKb
        assertTrue("native heap $worstKb KB above baseline after close()", worstKb < MAX_GROWTH_KB)
        assertTrue("threads grew ${threads[1]} -> ${threads.last()}", threads.last() - threads[1] < 3)
    }

    private fun settle() = repeat(3) {
        System.gc()
        System.runFinalization()
        Thread.sleep(200)
    }

    private companion object {
        const val CYCLES = 5
        const val FRAMES_PER_CYCLE = 10

        // ponytail: fixed bound, well under one leaked interpreter (~17 MB weights); tighten if it flakes low.
        const val MAX_GROWTH_KB = 8 * 1024L
    }
}
