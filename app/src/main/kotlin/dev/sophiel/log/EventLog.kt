package dev.sophiel.log

import java.io.File
import java.time.Instant
import java.time.ZoneId

/** One line of `log.csv`: `epochMillis,KIND,fields...`. Scalars only (C3): no frames, no app names. */
data class Entry(val at: Long, val kind: String, val fields: List<String>) {
    fun line() = (listOf(at.toString(), kind) + fields).joinToString(",")
}

/** `OFF` reasons. */
object OffReason {
    const val USER = "USER"
    const val SCREEN_OFF = "SCREEN_OFF"
    const val SYSTEM = "SYSTEM"
    const val RESTARTED = "RESTARTED" // inferred: the boot count changed
    const val APP_CLOSED = "APP_CLOSED" // inferred: force-stop or crash
}

/**
 * The Parent's Log (D39): `filesDir/log.csv`, wall clock, 7 days. Appends drop older lines (rewriting only
 * when something drops) and reads filter by age too. The last-alive heartbeat lives behind [read]/[write]
 * (SharedPreferences in the app; null removes a key) so an ON with no OFF can be closed on the next start.
 */
class EventLog(
    private val file: File,
    private val wall: () -> Long,
    private val read: (String) -> String?,
    private val write: (Map<String, String?>) -> Unit,
    private val bootCount: () -> Int,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private var running = false // a heartbeat that races the OFF must not re-arm the gap check

    @Synchronized fun on(preset: String, sensitivity: String) {
        add(wall(), "ON", preset, sensitivity)
        running = true
        heartbeat()
    }

    @Synchronized fun off(reason: String) {
        add(wall(), "OFF", reason)
        running = false
        write(mapOf(ALIVE to null, BOOT to null))
    }

    /** Once a minute while running. */
    @Synchronized fun heartbeat() {
        if (running) write(mapOf(ALIVE to wall().toString(), BOOT to bootCount().toString()))
    }

    /** On app start: an ON that never got its OFF ends at its last heartbeat. */
    @Synchronized fun recoverGap() {
        val at = read(ALIVE)?.toLongOrNull() ?: return
        add(at, "OFF", if (read(BOOT)?.toIntOrNull() != bootCount()) OffReason.RESTARTED else OffReason.APP_CLOSED)
        write(mapOf(ALIVE to null, BOOT to null))
    }

    @Synchronized fun masked(preset: String, count: Int, score: Float) =
        add(wall(), "MASKED", preset, count.toString(), "%.2f".format(java.util.Locale.ROOT, score))

    /** D45: written when the stretch ends, stamped at its start. */
    @Synchronized fun unanalyzable(startedAt: Long, seconds: Int) = add(startedAt, "UNANALYZABLE", seconds.toString())

    /** The last 7 days, in file order. */
    @Synchronized fun entries(): List<Entry> = load().filter { it.at >= wall() - WEEK_MS }

    /** The last line written: Status shows "Se detuvo" when it is an OFF the user didn't cause. */
    fun last(): Entry? = entries().lastOrNull()

    fun countToday(): Int {
        val midnight = Instant.ofEpochMilli(wall()).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return entries().count { it.kind == "MASKED" && it.at >= midnight }
    }

    @Synchronized fun clear() { file.delete() }

    private fun add(at: Long, kind: String, vararg fields: String) {
        val all = load()
        val fresh = all.filter { it.at >= wall() - WEEK_MS }
        if (fresh.size != all.size) file.writeText(fresh.joinToString("") { it.line() + "\n" })
        file.appendText(Entry(at, kind, fields.toList()).line() + "\n")
    }

    private fun load(): List<Entry> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { l ->
            val p = l.split(',')
            p.getOrNull(0)?.toLongOrNull()?.let { Entry(it, p.getOrElse(1) { "" }, p.drop(2)) }
        }
    }

    private companion object {
        const val WEEK_MS = 7 * 24 * 3_600_000L
        const val ALIVE = "last_alive"
        const val BOOT = "last_boot"
    }
}

/**
 * The episode rule (D39), pure: fed "is anything masked" (probing and peeking count) on each change, it
 * says when a new masking episode starts. Masks back within [gapMs] of clearing continue the episode;
 * one instance per session, so a new session always starts one.
 */
class Episodes(private val gapMs: Long = 3_000) { // = TileMaskTracker.RECENT_RELEASE_MS
    private var masked = false
    private var clearedAt: Long? = null

    fun update(anyMasked: Boolean, now: Long): Boolean {
        if (anyMasked == masked) return false
        masked = anyMasked
        if (!anyMasked) {
            clearedAt = now
            return false
        }
        return clearedAt?.let { now - it < gapMs } != true
    }
}

/**
 * Protected (FLAG_SECURE) stretches (D39, D45), pure: a stretch of at least [minMs] is reported when it
 * ends, on the first unprotected frame or [end] at teardown. A still secure screen sends no frames, so
 * the end is what is waited for, not a timer.
 */
class Stretches(private val minMs: Long = 1_000) {
    private var since: Long? = null

    /** How long ago it began and its length in whole seconds, or null. */
    class Done(val startedAgoMs: Long, val seconds: Int)

    fun frame(protected: Boolean, now: Long): Done? {
        if (protected) {
            if (since == null) since = now
            return null
        }
        return end(now)
    }

    fun end(now: Long): Done? {
        val start = since ?: return null
        since = null
        val len = now - start
        return if (len >= minMs) Done(len, (len / 1_000).toInt()) else null
    }
}
