package dev.sophiel.remote.parent

import dev.sophiel.remote.PairingQr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class Kv0 : Kv {
    val m = mutableMapOf<String, String>()
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
}

private class PairApi(var reply: Reply) : Api {
    val calls = mutableListOf<String>()
    override suspend fun signup(email: String, password: String) = Reply.Ok
    override suspend fun login(email: String, password: String) = Reply.Token("t1")
    override suspend fun logout(token: String) = Reply.Ok
    override suspend fun deleteAccount(token: String) = Reply.Ok
    override suspend fun pair(token: String, claimCode: String) = reply.also { calls += "pair:$token:$claimCode" }
}

class PairingTest {
    private val kv = Kv0()
    private val key = ByteArray(32) { it.toByte() }
    private val qr = PairingQr.encode("ABC123", key)
    private val api = PairApi(Reply.Paired("p1"))
    private fun session() = ParentSession(api, TokenStore(kv), Children(kv)).also { runBlocking { it.login("a@b.co", "password1") } }

    @Test fun goodScanPairsAndKeepsIdNameAndKeyAcrossRestart() = runBlocking {
        val s = session()
        val r = s.scan(qr, "Teléfono del Hijo")
        assertEquals(Scan.Ok("p1"), r)
        assertEquals(listOf("pair:t1:ABC123"), api.calls)
        s.children.rename("p1", "  Sofi ")
        val back = Children(kv).list.value.single() // a new process reads it back
        assertEquals("p1", back.id)
        assertEquals("Sofi", back.name)
        assertArrayEquals(key, back.key)
    }

    @Test fun notASophielCodeNeverReachesTheRelay() = runBlocking {
        val s = session()
        assertEquals(Scan.Fail(Failure.BAD_CODE), s.scan("https://example.com", "x"))
        assertEquals(Scan.Fail(Failure.BAD_CODE), s.scan("sophiel:1:ABC:short", "x"))
        assertTrue(api.calls.isEmpty())
        assertTrue(s.children.list.value.isEmpty())
    }

    @Test fun expiredOrUnknownCodeNamesTheFix() = runBlocking {
        api.reply = Reply.Fail(failureOf(404, login = false))
        val s = session()
        assertEquals(Scan.Fail(Failure.BAD_CODE), s.scan(qr, "x"))
        assertTrue(s.children.list.value.isEmpty())
    }

    @Test fun deadSessionSendsBackToLogin() = runBlocking {
        api.reply = Reply.Fail(failureOf(401, login = false))
        val s = session()
        assertEquals(Scan.Fail(Failure.SESSION), s.scan(qr, "x"))
        assertNull(s.token.value)
    }

    @Test fun rescanAfterReinstallRestoresTheSameKey() = runBlocking {
        session().scan(qr, "Hijo")
        kv.m.clear() // reinstall: app-private storage is gone
        api.reply = Reply.Paired("p2")
        val s = session()
        assertEquals(Scan.Ok("p2"), s.scan(PairingQr.encode("NEW999", key), "Hijo"))
        assertArrayEquals(key, s.children.list.value.single().key)
    }
}
