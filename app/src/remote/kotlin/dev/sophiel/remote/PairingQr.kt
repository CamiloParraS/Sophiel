package dev.sophiel.remote

import java.util.Base64

/** QR text `sophiel:1:<claimCode>:<base64url key>` (D79). */
object PairingQr {
    class Payload(val claimCode: String, val key: ByteArray)

    fun encode(claimCode: String, key: ByteArray): String =
        "sophiel:1:$claimCode:" + Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    fun parse(text: String): Payload? {
        val p = text.split(":")
        if (p.size != 4 || p[0] != "sophiel" || p[1] != "1" || p[2].isEmpty()) return null
        val key = try { Base64.getUrlDecoder().decode(p[3]) } catch (_: IllegalArgumentException) { return null }
        return if (key.size == 32) Payload(p[2], key) else null
    }
}
