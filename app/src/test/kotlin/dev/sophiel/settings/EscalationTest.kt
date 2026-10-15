package dev.sophiel.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationTest {
    private var now = 1_000_000L
    private var saved = emptyMap<String, Long>()
    private fun escalation() = Escalation({ now }, { saved }, { saved = it })
    private val min = 60_000L

    @Test fun `three in a rolling 10 min block for 15 min`() {
        val e = escalation()
        assertFalse(e.hit("a")); now += 4 * min
        assertFalse(e.hit("a")); now += 5 * min
        assertTrue(e.hit("a"))
        assertEquals(now + 15 * min, e.until("a"))
        assertEquals(now + 15 * min, saved["a"]) // persisted
        assertNull(e.until("b"))
    }

    @Test fun `old hits roll out of the window`() {
        val e = escalation()
        e.hit("a"); now += 6 * min
        e.hit("a"); now += 6 * min // the first is 12 min old
        assertFalse(e.hit("a"))
        now += 1 * min
        assertTrue(e.hit("a")) // now 2, 7, 13 min: all within 10 of the last
    }

    @Test fun `apps count apart, the count restarts after a block, a block ends by time`() {
        val e = escalation()
        repeat(2) { e.hit("a"); e.hit("b") }
        assertTrue(e.hit("a"))
        assertFalse(e.hit("a")) // blocked: not counted
        assertTrue(e.hit("b")) // b's count was its own
        now += 15 * min
        assertNull(e.until("a"))
        assertFalse(e.hit("a")); assertFalse(e.hit("a")) // restarted: the first of three
    }

    @Test fun `end and clear drop blocks and counts, a reload keeps live blocks only`() {
        val e = escalation()
        repeat(3) { e.hit("a") }
        e.end("a")
        assertNull(e.until("a"))
        assertFalse(e.hit("a")); assertFalse(e.hit("a"))
        repeat(3) { e.hit("b") }
        assertEquals(setOf("b"), saved.keys)
        assertEquals(now + 15 * min, escalation().until("b")) // after a reboot
        e.clear()
        assertEquals(emptyMap<String, Long>(), saved)
    }
}
