package dev.sophiel.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvelopeTest {
    private val key = ByteArray(32) { it.toByte() }
    private val msg = "hola".toByteArray()

    @Test fun roundTrip() {
        assertArrayEquals(msg, Envelope.open(key, "p1", Envelope.seal(key, "p1", msg)))
    }

    @Test fun nonceIsFreshPerMessage() {
        val a = Envelope.seal(key, "p1", msg)
        val b = Envelope.seal(key, "p1", msg)
        assertEquals(false, a.contentEquals(b))
        assertEquals(12 + msg.size + 16, a.size)
    }

    @Test fun flippedBitRejected() {
        val body = Envelope.seal(key, "p1", msg)
        for (i in body.indices) {
            val bad = body.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNull("byte $i", Envelope.open(key, "p1", bad))
        }
    }

    @Test fun wrongPairingIdRejected() {
        assertNull(Envelope.open(key, "p2", Envelope.seal(key, "p1", msg)))
    }

    @Test fun wrongKeyRejected() {
        assertNull(Envelope.open(ByteArray(32) { 9 }, "p1", Envelope.seal(key, "p1", msg)))
    }

    @Test fun truncatedRejected() {
        assertNull(Envelope.open(key, "p1", ByteArray(5)))
    }
}
