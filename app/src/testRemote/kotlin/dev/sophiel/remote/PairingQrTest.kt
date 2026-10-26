package dev.sophiel.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingQrTest {
    private val key = ByteArray(32) { (it * 7 + 200).toByte() } // forces '-' and '_' in base64url

    @Test fun roundTrip() {
        val text = PairingQr.encode("K7M2QX", key)
        assertEquals(true, text.startsWith("sophiel:1:K7M2QX:"))
        assertEquals(false, text.contains('=') || text.contains('+') || text.contains('/'))
        val back = PairingQr.parse(text)!!
        assertEquals("K7M2QX", back.claimCode)
        assertArrayEquals(key, back.key)
    }

    @Test fun malformedRejected() {
        val ok = PairingQr.encode("K7M2QX", key)
        val bad = listOf(
            "", "hello", "sophiel:1:K7M2QX", "sophiel:2:K7M2QX:" + ok.substringAfterLast(':'),
            "other:1:K7M2QX:" + ok.substringAfterLast(':'), "sophiel:1::" + ok.substringAfterLast(':'),
            "sophiel:1:K7M2QX:!!!", "sophiel:1:K7M2QX:AAAA", ok + ":x",
        )
        bad.forEach { assertNull(it, PairingQr.parse(it)) }
    }
}
