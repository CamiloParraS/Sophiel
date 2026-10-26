package dev.sophiel.remote

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** D79: body = nonce (12 random bytes) || AES-256-GCM ciphertext+tag, pairing id as associated data. */
object Envelope {
    private const val NONCE = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun seal(key: ByteArray, pairingId: String, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        return nonce + cipher(Cipher.ENCRYPT_MODE, key, nonce, pairingId).doFinal(plain)
    }

    /** The plaintext, or null for any failure (bad tag, wrong key or pairing id, too short). */
    fun open(key: ByteArray, pairingId: String, body: ByteArray): ByteArray? {
        if (body.size < NONCE + TAG_BITS / 8) return null
        return try {
            cipher(Cipher.DECRYPT_MODE, key, body.copyOfRange(0, NONCE), pairingId)
                .doFinal(body, NONCE, body.size - NONCE)
        } catch (_: java.security.GeneralSecurityException) {
            null
        }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, pairingId: String) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(pairingId.toByteArray())
        }
}
