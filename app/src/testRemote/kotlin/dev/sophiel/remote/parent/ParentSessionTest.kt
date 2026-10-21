package dev.sophiel.remote.parent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class MemKv : Kv {
    val m = mutableMapOf<String, String>()
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
}

private class FakeApi(var reply: Reply = Reply.Ok) : Api {
    var calls = mutableListOf<String>()
    override suspend fun signup(email: String, password: String) = reply.also { calls += "signup" }
    override suspend fun login(email: String, password: String) = reply.also { calls += "login" }
    override suspend fun logout(token: String) = reply.also { calls += "logout:$token" }
    override suspend fun deleteAccount(token: String) = reply.also { calls += "delete:$token" }
    override suspend fun pair(token: String, claimCode: String) = reply
}

class ParentSessionTest {
    private val kv = MemKv()
    private val api = FakeApi()
    private fun session() = ParentSession(api, TokenStore(kv), Children(kv))

    @Test fun failureMapping() {
        assertEquals(Failure.WRONG_CREDENTIALS, failureOf(401, login = true))
        assertEquals(Failure.SESSION, failureOf(401, login = false))
        assertEquals(Failure.LOCKED, failureOf(429, login = true))
        assertEquals(Failure.EMAIL_TAKEN, failureOf(409, login = false))
        assertEquals(Failure.INVALID, failureOf(400, login = false))
        assertEquals(Failure.OTHER, failureOf(500, login = true))
    }

    @Test fun loginStoresTokenAndSurvivesRestart() = runBlocking {
        api.reply = Reply.Token("t1")
        val s = session()
        assertNull(s.login("a@b.co", "password1"))
        assertEquals("t1", s.token.value)
        assertEquals("t1", session().token.value) // a new process reads it back
    }

    @Test fun wrongPasswordKeepsLoggedOut() = runBlocking {
        api.reply = Reply.Fail(Failure.WRONG_CREDENTIALS)
        val s = session()
        assertEquals(Failure.WRONG_CREDENTIALS, s.login("a@b.co", "password1"))
        assertNull(s.token.value)
    }

    @Test fun signupChecksLocallyBeforeAskingTheRelay() = runBlocking {
        val s = session()
        assertEquals(Failure.INVALID, s.signup("a@b.co", "short"))
        assertEquals(Failure.INVALID, s.signup("no-at-sign", "password1"))
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun logoutClearsTokenEvenOffline() = runBlocking {
        api.reply = Reply.Token("t1"); val s = session(); s.login("a@b.co", "password1")
        api.reply = Reply.Fail(Failure.OFFLINE)
        s.logout()
        assertNull(s.token.value)
        assertNull(session().token.value)
        assertEquals("logout:t1", api.calls.last())
    }

    @Test fun deleteAccountKeepsSessionIfItFailed() = runBlocking {
        api.reply = Reply.Token("t1"); val s = session(); s.login("a@b.co", "password1")
        api.reply = Reply.Fail(Failure.OFFLINE)
        assertEquals(Failure.OFFLINE, s.deleteAccount())
        assertEquals("t1", s.token.value)
        api.reply = Reply.Ok
        assertNull(s.deleteAccount())
        assertNull(s.token.value)
    }

    @Test fun logoutAndDeleteAccountDropTheChildListAndKeys() = runBlocking {
        api.reply = Reply.Token("t1"); val s = session(); s.login("a@b.co", "password1")
        s.children.add(Child("p1", "Tablet", ByteArray(32)))
        s.logout()
        assertEquals(emptyList<Child>(), s.children.list.value)
        assertEquals(emptyList<Child>(), session().children.list.value) // gone from storage too

        api.reply = Reply.Token("t2"); s.login("a@b.co", "password1")
        s.children.add(Child("p2", "Tablet", ByteArray(32)))
        api.reply = Reply.Fail(Failure.OFFLINE)
        s.deleteAccount()
        assertEquals(1, s.children.list.value.size) // a failed delete keeps everything
        api.reply = Reply.Ok
        s.deleteAccount()
        assertEquals(emptyList<Child>(), session().children.list.value)
    }

    @Test fun aSessionFailureAnywhereReturnsToLogin() = runBlocking {
        api.reply = Reply.Token("t1"); val s = session(); s.login("a@b.co", "password1")
        s.expireOn(Failure.OFFLINE); assertEquals("t1", s.token.value)
        s.expireOn(Failure.SESSION); assertNull(s.token.value)
    }
}
