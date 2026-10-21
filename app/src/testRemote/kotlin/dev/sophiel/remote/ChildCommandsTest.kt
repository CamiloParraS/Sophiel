package dev.sophiel.remote

import dev.sophiel.capture.CaptureMode
import dev.sophiel.capture.ModeState
import dev.sophiel.log.Entry
import dev.sophiel.log.EventLog
import dev.sophiel.settings.AppEntry
import dev.sophiel.settings.AppRule
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule as JRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val DAY = 24 * 3_600_000L

class ChildCommandsTest {
    @get:JRule val tmp = TemporaryFolder()
    private var now = 20 * DAY

    /** The capture controller and the Apps store, as the commands see them. */
    private inner class FakePhone : ChildPhone {
        var mode = ModeState(serviceBound = true, keepProtecting = true)
        val rules = HashMap<String, AppRule>()
        var stops = 0
        var starts = 0
        var entries = listOf<Entry>()

        override suspend fun state() = mode
        override suspend fun stop() { stops++; mode = mode.copy(protecting = false, mode = CaptureMode.NONE) }
        override suspend fun start() { starts++; mode = mode.copy(protecting = true, mode = CaptureMode.ACCESSIBILITY) }
        override suspend fun rules() = rules.toMap()
        override suspend fun setRule(pkg: String, rule: AppRule) { if (rule == AppRule.NORMAL) rules.remove(pkg) else rules[pkg] = rule }
        override suspend fun apps() = listOf(AppEntry("com.b", "Bravo"), AppEntry("com.a", "Alfa"), AppEntry("com.android.settings", "Ajustes"))
        override suspend fun excluded() = setOf("com.android.settings")
        override suspend fun log() = entries
    }

    private val phone = FakePhone()
    private val commands = ChildCommands(phone)
    private fun ask(type: String, rules: Map<String, Rule> = emptyMap()) = runBlocking { commands.handle(Request("r-$type", now, type, rules)) }

    @Test fun `log sends the 7-day rows and the Apps list with exclusions applied`() {
        phone.entries = listOf(Entry(now - 1000, "ON", listOf("BALANCED", "NORMAL")), Entry(now, "MASKED", listOf("BALANCED", "2", "0.90", "com.a")))
        phone.rules["com.b"] = AppRule.BLOCKED
        val r = ask("log")
        assertTrue(r.ok)
        assertEquals("r-log", r.id)
        assertEquals("${now - 1000},ON,BALANCED,NORMAL\n$now,MASKED,BALANCED,2,0.90,com.a\n", r.logCsv)
        assertEquals(listOf(Apps("Bravo", "com.b", Rule.Bloqueada), Apps("Alfa", "com.a")), r.apps) // changed first, then by label; Ajustes excluded
    }

    @Test fun `rules go through the Apps store, excluded apps are left alone`() {
        val r = ask("rules", mapOf("com.a" to Rule.Bloqueada, "com.b" to Rule.DeConfianza, "com.android.settings" to Rule.Bloqueada))
        assertTrue(r.ok)
        assertEquals(mapOf("com.a" to AppRule.BLOCKED, "com.b" to AppRule.TRUSTED), phone.rules)
        ask("rules", mapOf("com.a" to Rule.Normal))
        assertEquals(mapOf("com.b" to AppRule.TRUSTED), phone.rules)
    }

    @Test fun `stop stops and the reply says protection is off`() {
        phone.mode = phone.mode.copy(protecting = true, mode = CaptureMode.ACCESSIBILITY)
        val r = ask("stop")
        assertTrue(r.ok)
        assertEquals(1, phone.stops)
        assertFalse(r.protecting)
        assertEquals("NONE", r.captureMode)
    }

    @Test fun `start is on, then already on, then the two rejections`() {
        val on = ask("start")
        assertTrue(on.ok)
        assertNull(on.reason)
        assertTrue(on.protecting)
        assertEquals("ACCESSIBILITY", on.captureMode)

        val again = ask("start")
        assertTrue(again.ok)
        assertEquals(Reason.ALREADY_ON, again.reason)
        assertEquals(1, phone.starts)

        phone.mode = ModeState(serviceBound = false, keepProtecting = true)
        val noService = ask("start")
        assertFalse(noService.ok)
        assertEquals(Reason.ACCESSIBILITY_OFF, noService.reason)

        phone.mode = ModeState(serviceBound = true, keepProtecting = false)
        val switchOff = ask("start")
        assertFalse(switchOff.ok)
        assertEquals(Reason.SWITCH_OFF, switchOff.reason)
        assertEquals(1, phone.starts)
    }

    @Test fun `start from a Sin protección stretch resumes`() {
        phone.mode = ModeState(protecting = true, serviceBound = true, keepProtecting = true, interrupted = true)
        assertNull(ask("start").reason)
        assertEquals(1, phone.starts)
    }

    @Test fun `service off - log, stop and rules answer with the flag, start is rejected`() {
        phone.mode = ModeState(protecting = true, serviceBound = false, keepProtecting = true, interrupted = true)
        for (t in listOf("log", "stop", "rules")) {
            val r = ask(t, mapOf("com.a" to Rule.Bloqueada))
            assertTrue(t, r.ok)
            assertTrue(t, r.accessibilityOff)
        }
        assertEquals(AppRule.BLOCKED, phone.rules["com.a"]) // saved, applied when the service returns
        val start = ask("start")
        assertFalse(start.ok)
        assertTrue(start.accessibilityOff)
    }

    // --- the socket's frames: envelope, replay, unpair ---

    private val kv = HashMap<String, String>()
    private val store = ChildStore({ kv[it] }, { m -> m.forEach { (k, v) -> if (v == null) kv.remove(k) else kv[k] = v } })
    private val log by lazy { EventLog(tmp.root.resolve("log.csv"), { now }, { null }, { }, { 1 }, { 0L }, ZoneOffset.UTC) }
    private var unpairedCalls = 0
    private fun link() = ChildLink(store, log, commands, ReplayGuard { now }) { unpairedCalls++ }

    private fun paired(): ByteArray = store.key().also { store.savePaired("p1", "tok") }

    private fun req(key: ByteArray, r: Request, pairingId: String = "p1") =
        JSONObject().put("t", "req").put("rid", "rid1").put("body", Protocol.toWire(Envelope.seal(key, pairingId, Protocol.encode(r)))).toString()

    @Test fun `hello carries the device token, none when unpaired`() {
        assertNull(link().hello())
        paired()
        assertEquals("tok", JSONObject(link().hello()!!).getString("deviceToken"))
        assertEquals("hello", JSONObject(link().hello()!!).getString("t"))
    }

    @Test fun `a sealed request gets a sealed res, a replay or a bad envelope gets nothing`() {
        val key = paired()
        val l = link()
        val frame = req(key, Request("m1", now, "stop"))
        val res = JSONObject(runBlocking { l.onFrame(frame) }!!)
        assertEquals("res", res.getString("t"))
        assertEquals("rid1", res.getString("rid"))
        val reply = Protocol.decodeReply(Envelope.open(key, "p1", Protocol.fromWire(res.getString("body"))!!)!!)!!
        assertEquals("m1", reply.id)
        assertTrue(reply.ok)
        assertNull(runBlocking { l.onFrame(frame) }) // replayed id
        assertNull(runBlocking { l.onFrame(req(key, Request("m2", now - 3 * 60_000, "stop"))) }) // too old
        assertNull(runBlocking { l.onFrame(req(key, Request("m3", now, "stop"), pairingId = "p2")) }) // wrong pairing id
        assertEquals(1, phone.stops)
    }

    @Test fun `unpaired deletes key and token and logs Desvinculado por el Padre`() {
        val key = paired()
        assertNull(runBlocking { link().onFrame("""{"t":"unpaired"}""") })
        assertFalse(store.paired)
        assertFalse(kv.containsKey("key"))
        assertFalse(store.key().contentEquals(key))
        assertEquals(1, unpairedCalls)
        assertEquals(Entry(now, "UNPAIRED", listOf("PARENT")), log.entries().single())
    }

    @Test fun `unknown or malformed frames are ignored`() {
        paired()
        assertNull(runBlocking { link().onFrame("""{"t":"code","claimCode":"x"}""") })
        assertNull(runBlocking { link().onFrame("not json") })
        assertTrue(store.paired)
    }
}
