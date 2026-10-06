package dev.sophiel.pin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The wrong-attempt rule (D38), pure. Every 5th wrong attempt in a row locks for 30 s, doubling, capped
 * at 1 h; only a correct PIN resets it. Times are `elapsedRealtime` ms, valid within one [boot].
 */
data class Lockout(val fails: Int = 0, val lockLen: Long = 0, val lockUntil: Long = 0, val boot: Int = 0) {
    val attemptsLeft get() = ATTEMPTS - fails % ATTEMPTS
    fun remaining(now: Long) = (lockUntil - now).coerceAtLeast(0)

    fun wrong(now: Long, boot: Int): Lockout {
        val f = fails + 1
        if (f % ATTEMPTS != 0) return copy(fails = f)
        val len = minOf(BASE_MS shl minOf(f / ATTEMPTS - 1, 20), MAX_MS)
        return Lockout(f, len, now + len, boot)
    }

    /** After a reboot `elapsedRealtime` restarted, so the running lockout starts over at full length. */
    fun seen(now: Long, boot: Int) = if (lockLen == 0L || boot == this.boot) this else copy(lockUntil = now + lockLen, boot = boot)

    companion object {
        const val ATTEMPTS = 5
        const val BASE_MS = 30_000L
        const val MAX_MS = 3_600_000L
    }
}

sealed interface PinResult {
    object Ok : PinResult
    object Wrong : PinResult
    data class Locked(val remainingMs: Long) : PinResult
}

/**
 * PIN hash and lockout (D38). Salt, hash, count and lockout go through [read]/[write] (SharedPreferences
 * in the app); the PIN itself is never stored. Hashing runs on [Dispatchers.Default].
 */
class PinStore(
    private val read: (String) -> String?,
    private val write: (Map<String, String>) -> Unit,
    private val clock: () -> Long, // elapsedRealtime
    private val bootCount: () -> Int, // Settings.Global.BOOT_COUNT
    private val iterations: Int = ITERATIONS,
) {
    private val _lockout = MutableStateFlow(load())

    /** For the PIN sheet: attempts left and the countdown (`remaining(clock())`). */
    val lockout: StateFlow<Lockout> = _lockout.asStateFlow()

    fun exists() = read(HASH) != null

    suspend fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt, iterations)
        write(mapOf(SALT to hex(salt), HASH to hex(hash), COUNT to iterations.toString()))
    }

    suspend fun verify(pin: String): PinResult {
        val now = clock()
        val cur = _lockout.value.seen(now, bootCount()).also { save(it) }
        if (cur.remaining(now) > 0) return PinResult.Locked(cur.remaining(now))
        val salt = unhex(read(SALT) ?: return PinResult.Wrong)
        val want = unhex(read(HASH) ?: return PinResult.Wrong)
        val n = read(COUNT)?.toIntOrNull() ?: iterations
        val ok = MessageDigest.isEqual(hash(pin, salt, n), want)
        save(if (ok) Lockout() else cur.wrong(clock(), bootCount()))
        return if (ok) PinResult.Ok else PinResult.Wrong
    }

    /** Needs the old PIN (the PIN pad asks for it first); the new one replaces it on success. */
    suspend fun change(old: String, new: String): PinResult = verify(old).also { if (it == PinResult.Ok) set(new) }

    private fun save(l: Lockout) {
        _lockout.value = l
        write(mapOf(FAILS to l.fails.toString(), LEN to l.lockLen.toString(), UNTIL to l.lockUntil.toString(), BOOT to l.boot.toString()))
    }

    private fun load() = Lockout(
        fails = read(FAILS)?.toIntOrNull() ?: 0,
        lockLen = read(LEN)?.toLongOrNull() ?: 0,
        lockUntil = read(UNTIL)?.toLongOrNull() ?: 0,
        boot = read(BOOT)?.toIntOrNull() ?: 0,
    )

    private suspend fun hash(pin: String, salt: ByteArray, n: Int): ByteArray = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(pin.toCharArray(), salt, n, 256)
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        // ponytail: provisional; set so one verify is ~150 ms on Device A (PinTiming logs it, ticket 12).
        const val ITERATIONS = 100_000
        private const val SALT = "pin_salt"
        private const val HASH = "pin_hash"
        private const val COUNT = "pin_iterations"
        private const val FAILS = "lock_fails"
        private const val LEN = "lock_len"
        private const val UNTIL = "lock_until"
        private const val BOOT = "lock_boot"
    }
}
