package dev.sophiel

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sophiel.pin.PinStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/** Ticket 12 calibration, not pass/fail: read `adb logcat -s SophielBench`; pick ITERATIONS for ~150 ms on Device A. */
@RunWith(AndroidJUnit4::class)
class PinTiming {
    @Test fun verifyTime() {
        val disk = HashMap<String, String>()
        val s = PinStore({ disk[it] }, { disk.putAll(it) }, { 0 }, { 0 })
        runBlocking { s.set("1234"); s.verify("1234") } // warm up
        val ms = (1..5).map { measureTimeMillis { runBlocking { s.verify("1234") } } }
        Log.i("SophielBench", "PIN verify at ${PinStore.ITERATIONS} iterations: $ms ms")
    }
}
