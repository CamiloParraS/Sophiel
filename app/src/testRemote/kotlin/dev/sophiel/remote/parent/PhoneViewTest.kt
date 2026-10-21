package dev.sophiel.remote.parent

import dev.sophiel.capture.ModeState
import dev.sophiel.log.Entry
import dev.sophiel.remote.Apps
import dev.sophiel.remote.ChildCommands
import dev.sophiel.remote.ChildPhone
import dev.sophiel.remote.Envelope
import dev.sophiel.remote.Protocol
import dev.sophiel.remote.RequestType
import dev.sophiel.remote.Rule
import dev.sophiel.settings.AppEntry
import dev.sophiel.settings.AppRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MapKv : Kv {
    val m = mutableMapOf<String, String>()
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
}

/** A Child phone as the commands see it. */
private class Phone : ChildPhone {
    var mode = ModeState(serviceBound = true)
    val rules = HashMap<String, AppRule>()
    var entries = listOf<Entry>()
    override suspend fun state() = mode
    override suspend fun stop() {}
    override suspend fun start() {}
    override suspend fun rules() = rules.toMap()
    override suspend fun setRule(pkg: String, rule: AppRule) { if (rule == AppRule.NORMAL) rules.remove(pkg) else rules[pkg] = rule }
    override suspend fun apps() = listOf(AppEntry("com.b", "Bravo"), AppEntry("com.a", "Alfa"), AppEntry("com.c", "Charlie"))
    override suspend fun excluded() = emptySet<String>()
    override suspend fun log() = entries
}

/** The relay with the real Child commands behind it, through the real envelope. */
private class Relay(private val key: ByteArray, private val phone: Phone) : Api {
    var fail: Failure? = null
    val sent = mutableListOf<dev.sophiel.remote.Request>()
    override suspend fun signup(email: String, password: String) = Reply.Ok
    override suspend fun login(email: String, password: String) = Reply.Token("t1")
    override suspend fun logout(token: String) = Reply.Ok
    override suspend fun deleteAccount(token: String) = Reply.Ok
    override suspend fun pair(token: String, claimCode: String) = Reply.Ok
    override suspend fun unpair(token: String, pairingId: String) = Reply.Ok
    override suspend fun request(token: String, pairingId: String, body: String): Reply {
        fail?.let { return Reply.Fail(it) }
        val req = Protocol.decodeRequest(Envelope.open(key, pairingId, Protocol.fromWire(body)!!)!!)!!
        sent += req
        val reply = ChildCommands(phone).handle(req)
        return Reply.Body(Protocol.toWire(Envelope.seal(key, pairingId, Protocol.encode(reply))))
    }
}

class PhoneViewTest {
    private val key = ByteArray(32) { 3 }
    private val phone = Phone()
    private val relay = Relay(key, phone)
    private val kv = MapKv()
    private val session = ParentSession(relay, TokenStore(kv), Children(kv)).apply {
        runBlocking { login("a@b.co", "password1") }
        children.add(Child("p1", "Sofi", key))
    }
    private val view = PhoneView(relay, session, "p1") { 9_000L }
    private fun ready() = view.state.value as PhoneState.Ready

    @Test fun fetchDrawsTheLogRowsAndTheAppsWithTheirRules() = runBlocking {
        assertEquals(PhoneState.Loading, view.state.value)
        phone.entries = listOf(Entry(1_000, "ON", listOf("BALANCED", "NORMAL")), Entry(2_000, "MASKED", listOf("BALANCED", "2", "0.90", "com.a")))
        phone.rules["com.b"] = AppRule.BLOCKED
        view.fetch()
        val r = ready()
        assertEquals(phone.entries, r.entries)
        assertEquals(listOf(Apps("Bravo", "com.b", Rule.Bloqueada), Apps("Alfa", "com.a"), Apps("Charlie", "com.c")), r.apps)
        assertEquals(mapOf("com.b" to "Bravo", "com.a" to "Alfa", "com.c" to "Charlie"), r.labels)
        assertEquals(9_000L, r.at)
        assertEquals(listOf(RequestType.LOG), relay.sent.map { it.type })
    }

    @Test fun aRuleChangeSendsRulesAndRedrawsFromTheReplyInTheSameOrder() = runBlocking {
        view.fetch()
        view.setRule("com.c", Rule.DeConfianza)
        assertEquals(mapOf("com.c" to Rule.DeConfianza), relay.sent.last().rules)
        assertEquals(AppRule.TRUSTED, phone.rules["com.c"])
        val r = ready()
        // The reply would pin Charlie first; the rows stay where they were under the finger.
        assertEquals(listOf("com.a", "com.b", "com.c"), r.apps.map { it.pkg })
        assertEquals(Rule.DeConfianza, r.apps.last().rule)
        assertNull(r.unsaved)
    }

    @Test fun theReplyIsTheTruthNotTheRequest() = runBlocking {
        view.fetch()
        phone.rules["com.a"] = AppRule.BLOCKED // changed on the Child meanwhile
        view.setRule("com.b", Rule.Bloqueada)
        assertEquals(Rule.Bloqueada, ready().apps.first { it.pkg == "com.a" }.rule)
    }

    @Test fun aFailedRuleChangeLeavesTheRowAsItWasAndSaysSo() = runBlocking {
        view.fetch()
        relay.fail = Failure.OFFLINE
        view.setRule("com.a", Rule.Bloqueada)
        val r = ready()
        assertEquals(Rule.Normal, r.apps.first { it.pkg == "com.a" }.rule)
        assertEquals("com.a", r.unsaved)
        relay.fail = null
        view.setRule("com.a", Rule.Bloqueada)
        assertNull(ready().unsaved)
    }

    @Test fun accessibilityOffDisablesTheApps() = runBlocking {
        phone.mode = ModeState(serviceBound = false)
        view.fetch()
        assertTrue(ready().accessibilityOff)
        assertTrue(!ready().appsEnabled)
        phone.mode = ModeState(serviceBound = true)
        view.fetch()
        assertTrue(ready().appsEnabled)
    }

    @Test fun unreachableAndGone() = runBlocking {
        relay.fail = Failure.OFFLINE
        view.fetch()
        assertEquals(PhoneState.Offline, view.state.value)
        relay.fail = Failure.GONE
        view.fetch()
        assertEquals(PhoneState.Gone, view.state.value)
        relay.fail = Failure.SESSION
        view.fetch()
        assertNull(session.token.value)
    }
}
