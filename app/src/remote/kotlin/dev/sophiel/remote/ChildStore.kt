package dev.sophiel.remote

import java.util.Base64
import org.json.JSONException
import org.json.JSONObject

/** The role chosen on first launch (D82). `offline` never has one. */
enum class Role { Hijo, Padre }

/**
 * The Child phone's Remote state (D82, D83): role, the pairing key, and after `paired` the device token and pairing id.
 * [read]/[write] are app-private SharedPreferences in the app (null removes a key). The key is made once and kept
 * across "Mostrar código otra vez"; [clear] drops key and token together.
 */
class ChildStore(private val read: (String) -> String?, private val write: (Map<String, String?>) -> Unit) {
    var role: Role?
        get() = read("role")?.let { r -> Role.entries.firstOrNull { it.name == r } }
        set(v) = write(mapOf("role" to v?.name))

    fun key(): ByteArray {
        read("key")?.let { return Base64.getDecoder().decode(it) }
        return Envelope.newKey().also { write(mapOf("key" to Base64.getEncoder().encodeToString(it))) }
    }

    val pairingId get() = read("pairingId")
    val deviceToken get() = read("deviceToken")
    val paired get() = deviceToken != null && pairingId != null

    fun savePaired(pairingId: String, deviceToken: String) = write(mapOf("pairingId" to pairingId, "deviceToken" to deviceToken))

    /** The claim socket's first frame. While paired it carries the device token, so the new code joins this pairing (D79). */
    fun claimFrame(): String = JSONObject().put("t", "claim").apply { deviceToken?.let { put("deviceToken", it) } }.toString()

    /** The relay said `paired`. No token: a Parent phone joined the existing pairing, nothing to save. True if newly paired. */
    fun onPaired(f: ChildFrame.Paired): Boolean {
        savePaired(f.pairingId, f.deviceToken ?: return false)
        return true
    }

    fun clear() = write(mapOf("key" to null, "pairingId" to null, "deviceToken" to null))

    /** Desvincular here: like [clear], but the token is kept as [unpairToken] until the relay has heard `unpair`. */
    // ponytail: one pending farewell; unpairing a second pairing before the first farewell lands drops the first (its Parent sees "Sin conexión")
    fun clearKeepingFarewell() = write(mapOf("key" to null, "pairingId" to null, "deviceToken" to null, "unpairToken" to deviceToken))
    val unpairToken get() = read("unpairToken")
    fun farewellDone() = write(mapOf("unpairToken" to null))
}

/** What the relay sends a Child socket (SPEC M9 deliverable 2). Anything else is ignored. */
sealed interface ChildFrame {
    data class Code(val claimCode: String) : ChildFrame
    /** [deviceToken] is null when a Parent phone joined the existing pairing ("Mostrar código otra vez"). */
    data class Paired(val pairingId: String, val deviceToken: String?) : ChildFrame
    data object Unpaired : ChildFrame
    data class Req(val rid: String, val body: String) : ChildFrame

    companion object {
        fun parse(text: String): ChildFrame? = try {
            val o = JSONObject(text)
            when (o.getString("t")) {
                "code" -> Code(o.getString("claimCode"))
                "paired" -> Paired(o.getString("pairingId"), o.optString("deviceToken").ifEmpty { null })
                "unpaired" -> Unpaired
                "req" -> Req(o.getString("rid"), o.getString("body"))
                else -> null
            }
        } catch (_: JSONException) {
            null
        }

        /** `RELAY_URL` (http/https) to the Child socket URL. */
        fun wsUrl(relay: String) = relay.replaceFirst("http", "ws").trimEnd('/') + "/child"
    }
}
