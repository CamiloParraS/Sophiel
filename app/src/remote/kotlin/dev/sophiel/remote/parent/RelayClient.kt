package dev.sophiel.remote.parent

import android.content.Context
import dev.sophiel.BuildConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RelayClient(private val http: OkHttpClient = OkHttpClient(), private val base: String = BuildConfig.RELAY_URL) : Api {
    private suspend fun call(path: String, method: String, token: String? = null, json: JSONObject? = null, login: Boolean = false): Reply =
        withContext(Dispatchers.IO) {
            val body = json?.toString()?.toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(base + path).method(method, body ?: if (method == "POST") "".toRequestBody() else null)
                .apply { if (token != null) header("Authorization", "Bearer $token") }.build()
            try {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use Reply.Fail(failureOf(r.code, login))
                    val o = runCatching { JSONObject(r.body.string()) }.getOrNull()
                    val t = o?.optString("token")
                    val p = o?.optString("pairingId")
                    val b = o?.optString("body")
                    when {
                        !t.isNullOrEmpty() -> Reply.Token(t)
                        !p.isNullOrEmpty() -> Reply.Paired(p)
                        !b.isNullOrEmpty() -> Reply.Body(b)
                        else -> Reply.Ok
                    }
                }
            } catch (e: IOException) {
                Reply.Fail(Failure.OFFLINE)
            }
        }

    private fun creds(email: String, password: String) = JSONObject().put("email", email).put("password", password)
    override suspend fun signup(email: String, password: String) = call("/signup", "POST", json = creds(email, password))
    override suspend fun login(email: String, password: String) = call("/login", "POST", json = creds(email, password), login = true)
    override suspend fun logout(token: String) = call("/logout", "POST", token)
    override suspend fun deleteAccount(token: String) = call("/account", "DELETE", token)
    override suspend fun pair(token: String, claimCode: String) = call("/pairings", "POST", token, JSONObject().put("claimCode", claimCode))
    override suspend fun request(token: String, pairingId: String, body: String) =
        call("/pairings/$pairingId/request", "POST", token, JSONObject().put("body", body))
}

/** One session per process, built in `Remote.attach`. */
object ParentGraph {
    lateinit var session: ParentSession; private set
    lateinit var home: HomeCards; private set

    fun init(ctx: Context) {
        if (::session.isInitialized) return
        val prefs = ctx.getSharedPreferences("sophiel_remote", Context.MODE_PRIVATE)
        val kv = object : Kv {
            override fun get(key: String) = prefs.getString(key, null)
            override fun put(key: String, value: String?) = prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
        val api = RelayClient()
        session = ParentSession(api, TokenStore(kv), Children(kv))
        home = HomeCards(api, session)
    }
}
