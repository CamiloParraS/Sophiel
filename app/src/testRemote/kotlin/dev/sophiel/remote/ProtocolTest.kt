package dev.sophiel.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolTest {
    @Test fun simpleRequestsRoundTrip() {
        for (type in listOf(RequestType.LOG, RequestType.START, RequestType.STOP)) {
            val r = Request("id1", 123L, type)
            assertEquals(r, Protocol.decodeRequest(Protocol.encode(r)))
        }
    }

    @Test fun rulesRoundTrip() {
        val r = Request("id2", 5L, RequestType.RULES, mapOf("com.a" to Rule.Bloqueada, "com.b" to Rule.DeConfianza, "com.c" to Rule.Normal))
        assertEquals(r, Protocol.decodeRequest(Protocol.encode(r)))
    }

    @Test fun ruleWireNamesAreTheSpanishLabels() {
        val json = String(Protocol.encode(Request("i", 1, RequestType.RULES, mapOf("p" to Rule.DeConfianza))))
        assertEquals(true, json.contains("\"De confianza\""))
    }

    @Test fun badRequestsRejected() {
        assertNull(Protocol.decodeRequest("not json".toByteArray()))
        assertNull(Protocol.decodeRequest("""{"id":"x","ts":1,"type":"format"}""".toByteArray()))
        assertNull(Protocol.decodeRequest("""{"ts":1,"type":"log"}""".toByteArray()))
        assertNull(Protocol.decodeRequest("""{"id":"x","ts":1,"type":"rules","rules":{"p":"Otra"}}""".toByteArray()))
    }

    @Test fun plainReplyRoundTripsAndIsNotGzipped() {
        val r = Reply("id3", 9L, ok = false, reason = "sin accesibilidad", protecting = false, captureMode = "OFF", accessibilityOff = true)
        val bytes = Protocol.encode(r)
        assertEquals('{'.code.toByte(), bytes[0])
        assertEquals(r, Protocol.decodeReply(bytes))
    }

    @Test fun logReplyIsGzippedAndRoundTrips() {
        val csv = (1..2000).joinToString("\n") { "1700000$it,MASK,com.example.app,0.91" }
        val r = Reply(
            "id4", 9L, ok = true, protecting = true, captureMode = "ACCESSIBILITY", accessibilityOff = false,
            logCsv = csv, apps = listOf(Apps("Chat", "com.chat", Rule.DeConfianza),Apps("Mapas \"Ñ\"", "com.maps")),
        )
        val bytes = Protocol.encode(r)
        assertEquals(0x1f.toByte(), bytes[0])
        assertEquals(0x8b.toByte(), bytes[1])
        assertEquals(true, bytes.size < csv.length / 4)
        assertEquals(r, Protocol.decodeReply(bytes))
    }

    @Test fun badRepliesRejected() {
        assertNull(Protocol.decodeReply("{}".toByteArray()))
        assertNull(Protocol.decodeReply(byteArrayOf(0x1f, 0x8b.toByte(), 1, 2)))
    }

    @Test fun fullPipelineThroughEnvelopeAndWire() {
        val key = Envelope.newKey()
        val wire = Protocol.toWire(Envelope.seal(key, "pid", Protocol.encode(Request("i", 1, RequestType.STOP))))
        val back = Protocol.decodeRequest(Envelope.open(key, "pid", Protocol.fromWire(wire)!!)!!)
        assertEquals(Request("i", 1, RequestType.STOP), back)
        assertNull(Protocol.fromWire("***"))
    }
}
