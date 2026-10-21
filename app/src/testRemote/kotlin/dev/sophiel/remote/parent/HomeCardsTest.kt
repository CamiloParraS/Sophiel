package dev.sophiel.remote.parent

import dev.sophiel.remote.Envelope
import dev.sophiel.remote.Protocol
import dev.sophiel.remote.Reason
import dev.sophiel.remote.Request
import dev.sophiel.remote.RequestType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.sophiel.remote.Reply as ChildReply

private class Kv1 : Kv {
    val m = mutableMapOf<String, String>()
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
}

/** A relay with one Child phone behind it: it opens the envelope like the Child would, or fails with [fail]. */
private class FakeRelay(private val key: ByteArray) : Api {
    var fail: Failure? = null
    var protecting = false
    var accessibilityOff = false
    var startReason: String? = null
    var startOk = true
    val seen = mutableListOf<String>()
    override suspend fun signup(email: String, password: String) = Reply.Ok
    override suspend fun login(email: String, password: String) = Reply.Token("t1")
    override suspend fun logout(token: String) = Reply.Ok
    override suspend fun deleteAccount(token: String) = Reply.Ok
    override suspend fun pair(token: String, claimCode: String) = Reply.Ok
    var unpairFail: Failure? = null
    override suspend fun unpair(token: String, pairingId: String): Reply {
        seen += "unpair $pairingId"
        return unpairFail?.let { Reply.Fail(it) } ?: Reply.Ok
    }
    override suspend fun request(token: String, pairingId: String, body: String): Reply {
        fail?.let { return Reply.Fail(it) }
        val req: Request = Protocol.decodeRequest(Envelope.open(key, pairingId, Protocol.fromWire(body)!!)!!)!!
        seen += req.type.wire
        var ok = true
        var reason: String? = null
        when (req.type) {
            RequestType.START -> { ok = startOk; reason = startReason; if (ok && reason == null) protecting = true }
            RequestType.STOP -> protecting = false
            else -> {}
        }
        val r = ChildReply(req.id, req.ts, ok, reason, protecting, "ACCESSIBILITY", accessibilityOff, logCsv = if (req.type == RequestType.LOG) "" else null)
        return Reply.Body(Protocol.toWire(Envelope.seal(key, pairingId, Protocol.encode(r))))
    }
}

class HomeCardsTest {
    private val kv = Kv1()
    private val key = ByteArray(32) { 7 }
    private val relay = FakeRelay(key)
    private var now = 1_000L
    private val session = ParentSession(relay, TokenStore(kv), Children(kv)).apply {
        runBlocking { login("a@b.co", "password1") }
        children.add(Child("p1", "Sofi", key))
    }
    private val home = HomeCards(relay, session) { now }
    private fun card() = home.cards.value["p1"]

    @Test fun openShowsLoadingThenTheStateFromALogRequest() = runBlocking {
        assertEquals(Card.Loading, card())
        relay.protecting = true
        home.open()
        assertEquals(listOf("log"), relay.seen)
        val c = card() as Card.Live
        assertTrue(c.protecting)
        assertEquals(1_000L, c.at)
        assertNull(c.banner)
    }

    @Test fun startAndStopDropABannerPerOutcome() = runBlocking {
        home.open()
        home.start("p1")
        assertEquals(Card.Live(true, false, 1_000L, banner = Outcome.STARTED), card())
        home.start("p1".also { relay.startReason = Reason.ALREADY_ON })
        assertEquals(Outcome.ALREADY_ON, (card() as Card.Live).banner)
        home.stop("p1")
        assertEquals(Card.Live(false, false, 1_000L, banner = Outcome.STOPPED), card())
        relay.startOk = false; relay.startReason = Reason.SWITCH_OFF
        home.start("p1")
        assertEquals(Outcome.SWITCH_OFF, (card() as Card.Live).banner)
        relay.startReason = Reason.ACCESSIBILITY_OFF; relay.accessibilityOff = true
        home.start("p1")
        val c = card() as Card.Live
        assertEquals(Outcome.ACCESSIBILITY_OFF, c.banner)
        assertFalse(c.canStart) // the accessibility-off flag disables Start
    }

    @Test fun accessibilityOffDisablesStart() = runBlocking {
        relay.accessibilityOff = true
        home.open()
        assertFalse((card() as Card.Live).canStart)
        relay.accessibilityOff = false
        home.refresh("p1")
        assertTrue((card() as Card.Live).canStart)
    }

    @Test fun unreachableIsSinConexionAndKeepsNoLastKnownState() = runBlocking {
        relay.protecting = true
        home.open()
        now = 5_000L
        relay.fail = Failure.OFFLINE
        home.stop("p1")
        assertEquals(Card.Offline, card())
        home.refresh("p1") // Reintentar
        assertEquals(Card.Offline, card())
        relay.fail = null
        home.refresh("p1")
        assertEquals(Card.Live(true, false, 5_000L), card())
    }

    @Test fun relayStatusesMapToTheCards() {
        assertEquals(Failure.OFFLINE, failureOf(503, login = false))
        assertEquals(Failure.OFFLINE, failureOf(504, login = false))
        assertEquals(Failure.GONE, failureOf(410, login = false))
    }

    @Test fun goneCardRemovesTheEntryAndItsKey() = runBlocking {
        relay.fail = Failure.GONE
        home.open()
        assertEquals(Card.Gone, card())
        home.remove("p1")
        assertNull(card())
        assertTrue(session.children.list.value.isEmpty())
        assertFalse(kv.m["children"].orEmpty().contains("p1")) // the key went with it
        assertTrue(Children(kv).list.value.isEmpty())
    }

    @Test fun aDeadSessionGoesBackToLogin() = runBlocking {
        relay.fail = Failure.SESSION
        home.open()
        assertNull(session.token.value)
    }

    @Test fun otherFailuresKeepTheCardAndNameIt() = runBlocking {
        home.open()
        relay.fail = Failure.OTHER
        home.start("p1")
        assertEquals(Card.Live(false, false, 1_000L, banner = Outcome.FAILED), card())
    }

    @Test fun unpairDeletesThePairingThenDropsTheEntryAndKey() = runBlocking {
        home.open()
        home.unpair("p1")
        assertEquals(listOf("log", "unpair p1"), relay.seen)
        assertNull(card())
        assertTrue(Children(kv).list.value.isEmpty())
    }

    @Test fun unpairGoneDropsLocally() = runBlocking {
        relay.unpairFail = Failure.GONE
        assertTrue(home.unpair("p1"))
        assertTrue(Children(kv).list.value.isEmpty())
    }

    @Test fun unpairOfflineOrServerErrorKeepsTheEntryAndReportsIt() = runBlocking {
        for (f in listOf(Failure.OFFLINE, Failure.OTHER)) {
            relay.unpairFail = f
            assertFalse(home.unpair("p1"))
            assertEquals(listOf("p1"), Children(kv).list.value.map { it.id })
        }
    }

    @Test fun unpairWithADeadSessionKeepsTheEntryForAfterLogin() = runBlocking {
        relay.unpairFail = Failure.SESSION
        home.unpair("p1")
        assertNull(session.token.value)
        assertEquals(listOf("p1"), Children(kv).list.value.map { it.id })
    }
}
