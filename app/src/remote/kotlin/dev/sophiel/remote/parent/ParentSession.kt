package dev.sophiel.remote.parent

import dev.sophiel.remote.PairingQr
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Why a relay call failed, as the screens name it (D81: one error for wrong email or password). */
enum class Failure { WRONG_CREDENTIALS, LOCKED, EMAIL_TAKEN, INVALID, SESSION, OFFLINE, BAD_CODE, OTHER }

sealed interface Reply {
    data object Ok : Reply
    data class Token(val value: String) : Reply
    data class Paired(val pairingId: String) : Reply
    data class Fail(val why: Failure) : Reply
}

/** A 401 on /login is a wrong password; anywhere else it is a dead session. */
fun failureOf(code: Int, login: Boolean): Failure = when (code) {
    401 -> if (login) Failure.WRONG_CREDENTIALS else Failure.SESSION
    429 -> Failure.LOCKED
    409 -> Failure.EMAIL_TAKEN
    404 -> Failure.BAD_CODE // POST /pairings: unknown, used, expired or too many wrong guesses
    400 -> Failure.INVALID
    else -> Failure.OTHER
}

interface Api {
    suspend fun signup(email: String, password: String): Reply
    suspend fun login(email: String, password: String): Reply
    suspend fun logout(token: String): Reply
    suspend fun deleteAccount(token: String): Reply
    suspend fun pair(token: String, claimCode: String): Reply
}

/** Key-value storage; app-private SharedPreferences on the phone. */
interface Kv {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class TokenStore(private val kv: Kv) {
    var token: String?
        get() = kv.get("token")
        set(v) = kv.put("token", v)
}

/** One paired Child phone as this Parent phone knows it. The name never leaves this phone (D81). */
class Child(val id: String, val name: String, val key: ByteArray)

/** The Child phone list, app-private; a reinstall empties it (D81). */
class Children(private val kv: Kv) {
    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<Child>> = _list

    private fun load(): List<Child> {
        val a = JSONArray(kv.get("children") ?: return emptyList())
        return List(a.length()) { i ->
            a.getJSONObject(i).run { Child(getString("id"), getString("name"), Base64.getDecoder().decode(getString("key"))) }
        }
    }

    private fun save(l: List<Child>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("key", Base64.getEncoder().encodeToString(it.key))) }
        kv.put("children", a.toString())
        _list.value = l
    }

    fun add(c: Child) = save(_list.value.filter { it.id != c.id } + c)

    fun rename(id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        save(_list.value.map { if (it.id == id) Child(it.id, n, it.key) else it })
    }
}

sealed interface Scan {
    data class Ok(val pairingId: String) : Scan
    data class Fail(val why: Failure) : Scan
}

enum class ParentRole { CHILD, PARENT }

/** Which side this phone is. Unset means the Child, today's app. */
class RoleStore(private val kv: Kv) {
    val role = MutableStateFlow(if (kv.get("role") == "parent") ParentRole.PARENT else ParentRole.CHILD)
    fun set(r: ParentRole) { kv.put("role", r.name.lowercase()); role.value = r }
}

class ParentSession(private val api: Api, private val store: TokenStore, val children: Children) {
    private val _token = MutableStateFlow(store.token)
    val token: StateFlow<String?> = _token

    private fun save(t: String?) { store.token = t; _token.value = t }

    private fun take(r: Reply): Failure? = when (r) {
        is Reply.Token -> { save(r.value); null }
        is Reply.Fail -> r.why
        else -> Failure.OTHER
    }

    suspend fun login(email: String, password: String) = take(api.login(email.trim(), password))

    suspend fun signup(email: String, password: String): Failure? =
        if (!email.contains('@') || password.length < 8) Failure.INVALID else take(api.signup(email.trim(), password))

    /** Local logout never fails: the relay call is best effort. */
    suspend fun logout() {
        _token.value?.let { api.logout(it) }
        save(null)
    }

    suspend fun deleteAccount(): Failure? {
        val t = _token.value ?: return null
        return when (val r = api.deleteAccount(t)) {
            is Reply.Fail -> r.why.also { expireOn(it) }
            else -> { save(null); null }
        }
    }

    /**
     * A scanned QR: anything not a Sophiel code never reaches the relay. A good one is saved at once under [defaultName]
     * (the key must survive the app dying before the name screen), then renamed.
     */
    suspend fun scan(qrText: String, defaultName: String): Scan {
        val qr = PairingQr.parse(qrText.trim()) ?: return Scan.Fail(Failure.BAD_CODE)
        val t = _token.value ?: return Scan.Fail(Failure.SESSION)
        return when (val r = api.pair(t, qr.claimCode)) {
            is Reply.Paired -> { children.add(Child(r.pairingId, defaultName, qr.key)); Scan.Ok(r.pairingId) }
            is Reply.Fail -> Scan.Fail(r.why.also { expireOn(it) })
            else -> Scan.Fail(Failure.OTHER)
        }
    }

    /** Other tickets' calls report here: a dead session sends the Parent phone back to login. */
    fun expireOn(f: Failure?) { if (f == Failure.SESSION) save(null) }
}
