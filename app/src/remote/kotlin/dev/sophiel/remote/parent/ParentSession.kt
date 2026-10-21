package dev.sophiel.remote.parent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Why a relay call failed, as the screens name it (D81: one error for wrong email or password). */
enum class Failure { WRONG_CREDENTIALS, LOCKED, EMAIL_TAKEN, INVALID, SESSION, OFFLINE, OTHER }

sealed interface Reply {
    data object Ok : Reply
    data class Token(val value: String) : Reply
    data class Fail(val why: Failure) : Reply
}

/** A 401 on /login is a wrong password; anywhere else it is a dead session. */
fun failureOf(code: Int, login: Boolean): Failure = when (code) {
    401 -> if (login) Failure.WRONG_CREDENTIALS else Failure.SESSION
    429 -> Failure.LOCKED
    409 -> Failure.EMAIL_TAKEN
    400 -> Failure.INVALID
    else -> Failure.OTHER
}

interface Api {
    suspend fun signup(email: String, password: String): Reply
    suspend fun login(email: String, password: String): Reply
    suspend fun logout(token: String): Reply
    suspend fun deleteAccount(token: String): Reply
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

enum class ParentRole { CHILD, PARENT }

/** Which side this phone is. Unset means the Child, today's app. */
class RoleStore(private val kv: Kv) {
    val role = MutableStateFlow(if (kv.get("role") == "parent") ParentRole.PARENT else ParentRole.CHILD)
    fun set(r: ParentRole) { kv.put("role", r.name.lowercase()); role.value = r }
}

class ParentSession(private val api: Api, private val store: TokenStore) {
    private val _token = MutableStateFlow(store.token)
    val token: StateFlow<String?> = _token

    private fun save(t: String?) { store.token = t; _token.value = t }

    private fun take(r: Reply): Failure? = when (r) {
        is Reply.Token -> { save(r.value); null }
        is Reply.Fail -> r.why
        Reply.Ok -> Failure.OTHER
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

    /** Other tickets' calls report here: a dead session sends the Parent phone back to login. */
    fun expireOn(f: Failure?) { if (f == Failure.SESSION) save(null) }
}
