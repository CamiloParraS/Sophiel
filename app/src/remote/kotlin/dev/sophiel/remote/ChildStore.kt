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

    fun clear() = write(mapOf("key" to null, "pairingId" to null, "deviceToken" to null))
}

/** What the relay sends a Child socket during pairing (SPEC M9 deliverable 2). Anything else is ignored here. */
sealed interface ChildFrame {
    data class Code(val claimCode: String) : ChildFrame
    data class Paired(val pairingId: String, val deviceToken: String) : ChildFrame
    data object Unpaired : ChildFrame

    companion object {
        fun parse(text: String): ChildFrame? = try {
            val o = JSONObject(text)
            when (o.getString("t")) {
                "code" -> Code(o.getString("claimCode"))
                "paired" -> Paired(o.getString("pairingId"), o.getString("deviceToken"))
                "unpaired" -> Unpaired
                else -> null
            }
        } catch (_: JSONException) {
            null
        }

        /** `RELAY_URL` (http/https) to the Child socket URL. */
        fun wsUrl(relay: String) = relay.replaceFirst("http", "ws").trimEnd('/') + "/child"
    }
}
