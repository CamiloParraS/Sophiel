package dev.sophiel.pin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinStoreTest {
    private val disk = HashMap<String, String>()
    private var now = 1_000L
    private var boot = 1

    private fun store() = PinStore({ disk[it] }, { disk.putAll(it) }, { now }, { boot }, iterations = 10)
        .also { runBlocking { if (!it.exists()) it.set("1234") } }

    private fun PinStore.wrong(n: Int) = repeat(n) { runBlocking { verify("0000") } }

    @Test fun `correct pin is ok, wrong is wrong, and the pin is not on disk`() {
        val s = store()
        assertEquals(PinResult.Ok, runBlocking { s.verify("1234") })
        assertEquals(PinResult.Wrong, runBlocking { s.verify("1235") })
        assertTrue(disk.values.none { "1234" in it })
    }

    @Test fun `five failures lock 30 s, then 60 s, doubling to the 1 h cap`() {
        val s = store()
        var len = 30_000L
        repeat(10) {
            s.wrong(5)
            // the right PIN is refused while locked
            assertEquals(PinResult.Locked(len), runBlocking { s.verify("1234") })
            now += len
            len = minOf(len * 2, 3_600_000L)
        }
        assertEquals(3_600_000L, len)
    }

    @Test fun `attempts left count down and a correct pin resets`() {
        val s = store()
        s.wrong(3)
        assertEquals(2, s.lockout.value.attemptsLeft)
        runBlocking { s.verify("1234") }
        assertEquals(5, s.lockout.value.attemptsLeft)
        s.wrong(5)
        now += 30_000
        runBlocking { s.verify("1234") }
        s.wrong(5)
        assertEquals(PinResult.Locked(30_000), runBlocking { s.verify("1234") }) // back to 30 s, not 60
    }

    @Test fun `a reboot restarts the lockout at full length, and it survives a new store`() {
        store().wrong(5)
        boot = 2
        now = 500 // elapsedRealtime restarted
        assertEquals(PinResult.Locked(30_000), runBlocking { store().verify("1234") })
    }

    @Test fun `change needs the old pin`() {
        val s = store()
        assertEquals(PinResult.Wrong, runBlocking { s.change("9999", "5555") })
        assertEquals(PinResult.Ok, runBlocking { s.change("1234", "5555") })
        assertEquals(PinResult.Ok, runBlocking { s.verify("5555") })
        assertEquals(PinResult.Wrong, runBlocking { s.verify("1234") })
    }
}
