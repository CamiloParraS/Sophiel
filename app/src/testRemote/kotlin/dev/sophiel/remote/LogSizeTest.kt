package dev.sophiel.remote

import dev.sophiel.log.Entry
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC M9 V3: a 7-day `log` reply, gzipped, sealed and base64'd as the relay carries it, stays under the relay's 256 KB cap. */
class LogSizeTest {
    @Test fun worstCaseWeekFitsTheRelayCap() {
        val rnd = Random(7)
        val pkgs = List(40) { "com.example.vendor$it.app.name${it * 7}" }
        val start = 1_760_000_000_000L
        val lines = ArrayList<Entry>()
        // A heavy week: 16 h a day on the phone, a masked episode every minute, an unanalyzable stretch every 5 min,
        // blocked opens, guarded-page attempts, a few ON / OFF / gaps per day. Far beyond any real Child phone.
        for (day in 0 until 7) for (min in 0 until 16 * 60) {
            val at = start + day * 86_400_000L + min * 60_000L + rnd.nextLong(60_000)
            val app = pkgs[rnd.nextInt(pkgs.size)]
            lines += Entry(at, "MASKED", listOf("BALANCED", "${1 + rnd.nextInt(6)}", String.format(Locale.ROOT, "%.2f", 0.55 + rnd.nextDouble() * 0.45), app))
            if (min % 5 == 0) lines += Entry(at + 1, "UNANALYZABLE", listOf("${rnd.nextInt(1, 300)}", app))
            if (min % 30 == 0) lines += Entry(at + 2, "BLOCKED_OPEN", listOf(app, "RULE", "${rnd.nextInt(1, 9)}"))
            if (min % 60 == 0) lines += Entry(at + 3, "TAMPER", listOf("ACCESSIBILITY", "${rnd.nextInt(1, 5)}"))
            if (min % 120 == 0) {
                lines += Entry(at + 4, "OFF", listOf("SCREEN"))
                lines += Entry(at + 5, "ON", listOf("BALANCED", "NORMAL", "REMOTE"))
                lines += Entry(at + 6, "UNPROTECTED", listOf("${rnd.nextInt(1, 3600)}", "ACCESSIBILITY_OFF"))
                lines += Entry(at + 7, "APP_BLOCKED", listOf(app, "15"))
            }
        }
        val apps = List(300) { Apps("Aplicación con un nombre largo número $it", "com.example.vendor$it.app.name", Rule.entries[it % 3]) }
        val reply = Reply("8f1c0d1e-2b3a-4c5d-9e8f-001122334455", start, true, null, true, "ACCESSIBILITY", false, lines.joinToString("") { it.line() + "\n" }, apps)
        val body = Protocol.toWire(Envelope.seal(Envelope.newKey(), "pairing-id-0123456789", Protocol.encode(reply)))
        println("V3 worst-case 7-day log reply: ${lines.size} lines, csv ${reply.logCsv!!.length} B, wire body ${body.length} B of ${256 * 1024}")
        assertTrue("wire body ${body.length} B over the 256 KB cap", body.length < 256 * 1024)
    }
}
