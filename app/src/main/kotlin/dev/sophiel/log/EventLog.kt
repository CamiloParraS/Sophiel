package dev.sophiel.log

import java.io.File
import java.time.Instant
import java.time.ZoneId

/** One line of `log.csv`: `epochMillis,KIND,fields...`. Scalars only (C3): no frames; an app is its package name (D57). */
data class Entry(val at: Long, val kind: String, val fields: List<String>) {
    fun line() = (listOf(at.toString(), kind) + fields).joinToString(",")
}

/** `OFF` reasons. D54: the screen and system ones, and the inferred ones, only with the switch off. */
object OffReason {
    const val USER = "USER"
    const val SCREEN_OFF = "SCREEN_OFF"
    const val SYSTEM = "SYSTEM"
    const val RESTARTED = "RESTARTED" // inferred: the boot count changed
    const val APP_CLOSED = "APP_CLOSED" // inferred: force-stop or crash
}

/** Why a Sin protección stretch (D54) began. */
object GapReason {
    const val ACCESSIBILITY_OFF = "ACCESSIBILITY_OFF"
    const val APP_CLOSED = OffReason.APP_CLOSED // inferred, like the OFF
    const val RESTARTED = OffReason.RESTARTED
}

/** What a tamper attempt (D56) tried to turn off. */
object TamperTarget {
    const val ACCESSIBILITY = "ACCESSIBILITY"
    const val OVERLAY = "OVERLAY"
    const val APP_INFO = "APP_INFO"
    const val UNINSTALL = "UNINSTALL"
    const val ACTIVE_APPS = "ACTIVE_APPS"
}

/** Why a blocked app's open was stopped (D58). */
object BlockReason {
    const val RULE = "RULE" // the Parent set it Bloqueada
    const val ESCALATION = "ESCALATION"
}

/** A Sin protección stretch still open: protection on, nothing capturing, since [at]. */
data class Gap(val at: Long, val reason: String)

/**
 * The Parent's Log (D39): `filesDir/log.csv`, wall clock, 7 days. Reads filter by age; older lines
 * leave the file once per process, in [recoverGap], so an append never reads it (masks log on the
 * frame lane). The last-alive heartbeat lives behind [read]/[write] (SharedPreferences in the app;
 * null removes a key) so a capture that died can be closed on the next start, and so does an open
 * Sin protección stretch (D54), which is written when it ends.
 */
class EventLog(
    private val file: File,
    private val wall: () -> Long,
    private val read: (String) -> String?,
    private val write: (Map<String, String?>) -> Unit,
    private val bootCount: () -> Int,
    private val bootedAt: () -> Long, // wall clock at boot
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private var running = false // a heartbeat that races the session's end must not re-arm the gap check
    private var bootStart = false // the first process since the boot: nothing of ours ran before it (D61)

    /** The Parent's Start (D54: not a resume after a stretch). */
    @Synchronized fun on(preset: String, sensitivity: String) = add(wall(), "ON", preset, sensitivity)

    @Synchronized fun off(reason: String) = add(wall(), "OFF", reason)

    /** A capture session opened or closed: the heartbeat runs only in between. */
    @Synchronized fun capturing(on: Boolean) {
        running = on
        if (on) heartbeat() else write(mapOf(ALIVE to null, BOOT to null))
    }

    /** Once a minute while capturing. */
    @Synchronized fun heartbeat() {
        if (running) write(mapOf(ALIVE to wall().toString(), BOOT to bootCount().toString()))
    }

    /**
     * On app start: a capture that never closed ends at its last heartbeat. Switch off (M6): an OFF.
     * Switch on (D54): protection is still on, so a Sin protección stretch: a restart's ends at the boot
     * (boot to first unlock is not a gap), an app stopped stays open until capture comes back.
     */
    @Synchronized fun recoverGap(keepProtecting: Boolean) {
        prune()
        val boot = bootCount().toString()
        bootStart = read(SEEN_BOOT) != boot
        if (bootStart) write(mapOf(SEEN_BOOT to boot))
        val at = read(ALIVE)?.toLongOrNull() ?: return
        val restarted = read(BOOT)?.toIntOrNull() != bootCount()
        write(mapOf(ALIVE to null, BOOT to null))
        when {
            !keepProtecting -> add(at, "OFF", if (restarted) OffReason.RESTARTED else OffReason.APP_CLOSED)
            // ponytail: the shutdown is not recorded; alive at the last heartbeat, the process ran up to it within a
            // minute, so a phone left off overnight is not "8 h". Record ACTION_SHUTDOWN if that minute matters.
            restarted -> addGap(at, minOf(bootedAt(), at + HEARTBEAT_MS), GapReason.RESTARTED)
            else -> gapOpened(GapReason.APP_CLOSED, at)
        }
    }

    /** D54: a Sin protección stretch began; kept on disk until [gapClosed]. An open one keeps its start and reason. */
    @Synchronized fun gapOpened(reason: String, at: Long = wall()) {
        if (gap() == null) write(mapOf(GAP_AT to at.toString(), GAP_WHY to reason))
    }

    /**
     * D61: a stretch found at app start, nothing capturing. The first process since the boot (accessibility off,
     * so nothing started us at the first unlock) begins it at the boot, so a Child's hours before Sophiel is
     * opened are not hidden; boot to first unlock is counted with them. A later process begins it now.
     */
    @Synchronized fun gapFound(reason: String) = gapOpened(reason, if (bootStart) bootedAt() else wall())

    /** Capture or protection is back: the open stretch is written, stamped at its start, with its length. */
    @Synchronized fun gapClosed() {
        val g = gap() ?: return
        write(mapOf(GAP_AT to null, GAP_WHY to null))
        addGap(g.at, wall(), g.reason)
    }

    @Synchronized fun gap(): Gap? = read(GAP_AT)?.toLongOrNull()?.let { Gap(it, read(GAP_WHY) ?: GapReason.ACCESSIBILITY_OFF) }

    private fun addGap(from: Long, to: Long, reason: String) = add(from, "UNPROTECTED", ((to - from).coerceAtLeast(0) / 1_000).toString(), reason)

    /** [app]: the Episode app's package (D57), last field; null writes it blank ("App desconocida"). */
    @Synchronized fun masked(preset: String, count: Int, score: Float, app: String?) =
        add(wall(), "MASKED", preset, count.toString(), "%.2f".format(java.util.Locale.ROOT, score), app.orEmpty())

    /** D45: written when the stretch ends, stamped at its start, with the app found then (D57). */
    @Synchronized fun unanalyzable(startedAt: Long, seconds: Int, app: String?) = add(startedAt, "UNANALYZABLE", seconds.toString(), app.orEmpty())

    /** D54: the share ended with the screen on and capture went on by display shots. */
    @Synchronized fun shareStopped() = add(wall(), "SHARE_STOPPED")

    /** D56: the guard closed a page. Merged as [merge] says. */
    @Synchronized fun tamper(target: String) = merge("TAMPER", target)

    /** D58: a blocked app came to the front and was sent Home. Merged per app and reason, as [merge] says. */
    @Synchronized fun blockedOpen(app: String, reason: String) = merge("BLOCKED_OPEN", app, reason)

    /** The last 7 days, in file order. */
    @Synchronized fun entries(): List<Entry> = load().filter { it.at >= wall() - WEEK_MS }

    /** The last line written: Status shows "Se detuvo" when it is an OFF the user didn't cause (switch off). */
    fun last(): Entry? = entries().lastOrNull()

    fun countToday(): Int {
        val midnight = Instant.ofEpochMilli(wall()).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return entries().count { it.kind == "MASKED" && it.at >= midnight }
    }

    @Synchronized fun clear() { file.delete() }

    // ponytail: a process alive past 7 days keeps older lines on disk until its next start; reads hide them.
    private fun prune() {
        val all = load()
        val fresh = all.filter { it.at >= wall() - WEEK_MS }
        if (fresh.size != all.size) file.writeText(fresh.joinToString("") { it.line() + "\n" })
    }

    /**
     * An entry of [kind] with the same [key] within 60 s of its first attempt adds to its count (the last field),
     * rewriting that line; rare, and never on the frame lane.
     */
    private fun merge(kind: String, vararg key: String) {
        val now = wall()
        val all = load()
        val k = key.toList()
        val i = all.indexOfLast { it.kind == kind && it.fields.dropLast(1) == k && now - it.at < MERGE_MS }
        if (i < 0) return add(now, kind, *key, "1")
        val e = all[i]
        val merged = e.copy(fields = k + ((e.fields.last().toIntOrNull() ?: 1) + 1).toString())
        file.writeText(all.mapIndexed { j, it -> if (j == i) merged else it }.joinToString("") { it.line() + "\n" })
    }

    private fun add(at: Long, kind: String, vararg fields: String) {
        file.appendText(Entry(at, kind, fields.toList()).line() + "\n")
        heartbeat() // a write is proof of life too: the inferred OFF must not land before the last entry
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
        const val HEARTBEAT_MS = 60_000L // = CaptureSession's
        const val MERGE_MS = 60_000L
        const val ALIVE = "last_alive"
        const val BOOT = "last_boot"
        const val GAP_AT = "gap_at"
        const val GAP_WHY = "gap_reason"
        const val SEEN_BOOT = "seen_boot" // boot count of the last process start
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

    /** A stretch has begun and not ended. */
    val open get() = since != null

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
