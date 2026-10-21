package dev.sophiel.remote

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A per-app rule as it travels (D79): the Spanish labels of D58. */
enum class Rule(val wire: String) { Normal("Normal"), Bloqueada("Bloqueada"), DeConfianza("De confianza") }

/** `type` is one of log, rules, start, stop. `rules` (package -> rule) only for `rules`. */
data class Request(val id: String, val ts: Long, val type: String, val rules: Map<String, Rule> = emptyMap())

data class Apps(val label: String, val pkg: String)

/** A Start reply's `reason` (D82); the Parent phone words it. A rejected Start has ok = false. */
object Reason {
    const val ALREADY_ON = "ALREADY_ON" // "Ya estaba protegido"
    const val ACCESSIBILITY_OFF = "ACCESSIBILITY_OFF" // "El servicio de accesibilidad está apagado en el teléfono del Hijo"
    const val SWITCH_OFF = "SWITCH_OFF" // "'Seguir protegiendo siempre' está apagado en el teléfono del Hijo"
}

/** `logCsv` and `apps` are set only on a `log` reply; such a reply is gzipped before encryption. */
data class Reply(
    val id: String,
    val ts: Long,
    val ok: Boolean,
    val reason: String? = null,
    val protecting: Boolean,
    val captureMode: String,
    val accessibilityOff: Boolean,
    val logCsv: String? = null,
    val apps: List<Apps>? = null,
)

/** Plaintext JSON of D79. Decoders return null for anything malformed. */
object Protocol {
    private val TYPES = setOf("log", "rules", "start", "stop")

    fun encode(r: Request): ByteArray {
        val o = JSONObject().put("id", r.id).put("ts", r.ts).put("type", r.type)
        if (r.type == "rules") o.put("rules", JSONObject().also { j -> r.rules.forEach { (p, v) -> j.put(p, v.wire) } })
        return o.toString().toByteArray()
    }

    fun decodeRequest(bytes: ByteArray): Request? = parse {
        val o = JSONObject(String(bytes))
        val type = o.getString("type")
        if (type !in TYPES) return@parse null
        val rules = o.optJSONObject("rules")?.let { j ->
            j.keys().asSequence().associateWith { k -> Rule.entries.first { it.wire == j.getString(k) } }
        } ?: emptyMap()
        Request(o.getString("id"), o.getLong("ts"), type, rules)
    }

    fun encode(r: Reply): ByteArray {
        val o = JSONObject().put("id", r.id).put("ts", r.ts).put("ok", r.ok).put("protecting", r.protecting)
            .put("captureMode", r.captureMode).put("accessibilityOff", r.accessibilityOff)
        r.reason?.let { o.put("reason", it) }
        r.logCsv?.let { o.put("logCsv", it) }
        r.apps?.let { a -> o.put("apps", JSONArray().also { j -> a.forEach { j.put(JSONObject().put("label", it.label).put("pkg", it.pkg)) } }) }
        val plain = o.toString().toByteArray()
        return if (r.logCsv == null) plain else ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(plain) } }.toByteArray()
    }

    fun decodeReply(bytes: ByteArray): Reply? = parse {
        val plain = if (bytes.size > 1 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        val o = JSONObject(String(plain))
        Reply(
            o.getString("id"), o.getLong("ts"), o.getBoolean("ok"), o.optString("reason").ifEmpty { null },
            o.getBoolean("protecting"), o.getString("captureMode"), o.getBoolean("accessibilityOff"),
            if (o.has("logCsv")) o.getString("logCsv") else null,
            o.optJSONArray("apps")?.let { a -> List(a.length()) { Apps(a.getJSONObject(it).getString("label"), a.getJSONObject(it).getString("pkg")) } },
        )
    }

    /** The relay's `body` field: opaque base64. */
    fun toWire(body: ByteArray): String = Base64.getEncoder().encodeToString(body)
    fun fromWire(s: String): ByteArray? = try { Base64.getDecoder().decode(s) } catch (_: IllegalArgumentException) { null }

    private inline fun <T> parse(block: () -> T?): T? = try {
        block()
    } catch (_: JSONException) {
        null
    } catch (_: NoSuchElementException) {
        null
    } catch (_: java.io.IOException) { // bad gzip
        null
    }
}
