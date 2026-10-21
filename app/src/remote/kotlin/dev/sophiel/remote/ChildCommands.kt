package dev.sophiel.remote

import dev.sophiel.capture.ModeState
import dev.sophiel.log.Entry
import dev.sophiel.log.EventLog
import dev.sophiel.settings.AppEntry
import dev.sophiel.settings.AppRule
import dev.sophiel.settings.appList
import org.json.JSONObject

/** What a Remote command touches on the Child phone: the capture controller, the Apps store, the Log (D82). */
interface ChildPhone {
    suspend fun state(): ModeState
    suspend fun stop() // CaptureModeController.stop(remote = true): a PIN Stop, OFF reason REMOTE
    suspend fun start() // CaptureModeController.startRemote()
    suspend fun rules(): Map<String, AppRule>
    suspend fun setRule(pkg: String, rule: AppRule)
    suspend fun apps(): List<AppEntry> // launchable
    suspend fun excluded(): Set<String>
    suspend fun log(): List<Entry> // the last 7 days
}

/** D82, D83: one decrypted request in, its reply out. Every reply carries protecting, captureMode, accessibilityOff. */
class ChildCommands(private val phone: ChildPhone, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun handle(req: Request): Reply {
        var logCsv: String? = null
        var apps: List<Apps>? = null
        var ok = true
        var reason: String? = null
        when (req.type) {
            "log" -> {
                logCsv = phone.log().joinToString("") { it.line() + "\n" }
                apps = apps()
            }
            "rules" -> {
                val excluded = phone.excluded()
                req.rules.filterKeys { it !in excluded }.forEach { (pkg, r) -> phone.setRule(pkg, r.local) }
                apps = apps() // the Parent redraws from this, not from what it asked
            }
            "stop" -> phone.stop()
            "start" -> {
                val s = phone.state()
                when {
                    s.protecting && s.holdsSession -> reason = Reason.ALREADY_ON
                    !s.serviceBound -> { ok = false; reason = Reason.ACCESSIBILITY_OFF }
                    !s.keepProtecting -> { ok = false; reason = Reason.SWITCH_OFF }
                    else -> phone.start()
                }
            }
        }
        val s = phone.state()
        return Reply(req.id, clock(), ok, reason, s.protecting, s.mode.name, !s.serviceBound, logCsv, apps)
    }

    private suspend fun apps(): List<Apps> {
        val rules = phone.rules()
        return appList(phone.apps(), rules, phone.excluded(), "").map { Apps(it.label, it.pkg, Rule.of(rules[it.pkg] ?: AppRule.NORMAL)) }
    }
}

/**
 * The paired Child socket's frames (relay protocol, SPEC M9 deliverable 2), socket-free: [hello] to send on open,
 * [onFrame] for each text frame, returning the frame to send back or null. `unpaired` (the Parent unpaired, or the
 * relay rejected the hello) deletes key and token and logs Desvinculado (por el Padre).
 */
class ChildLink(
    private val store: ChildStore,
    private val log: EventLog,
    private val commands: ChildCommands,
    private val guard: ReplayGuard,
    private val onUnpaired: () -> Unit,
) {
    fun hello(): String? = store.deviceToken?.let(::helloFrame)

    /** After [unpairHere]: what a fresh socket sends so the relay deletes the pairing (D79: cleanup on next contact). */
    fun farewell(): List<String>? = store.unpairToken?.let { listOf(helloFrame(it), JSONObject().put("t", "unpair").toString()) }

    /** The relay closed the farewell socket (it got `unpair`) or rejected the token: nothing left to tell it. */
    fun farewellDone() = store.farewellDone()

    private fun helloFrame(token: String) = JSONObject().put("t", "hello").put("deviceToken", token).toString()

    suspend fun onFrame(text: String): String? = when (val f = ChildFrame.parse(text)) {
        ChildFrame.Unpaired -> {
            store.farewellDone()
            if (store.paired) {
                store.clear()
                log.unpaired(byParent = true)
                onUnpaired()
            }
            null
        }
        is ChildFrame.Req -> answer(f.rid, f.body)
        else -> null
    }

    private suspend fun answer(rid: String, body: String): String? {
        val pairingId = store.pairingId ?: return null
        val key = store.key()
        val req = Protocol.fromWire(body)?.let { Envelope.open(key, pairingId, it) }?.let(Protocol::decodeRequest) ?: return null
        if (!guard.accept(req.id, req.ts)) return null // no reply: the Parent sees a timeout, never a replayed Stop
        val reply = Envelope.seal(key, pairingId, Protocol.encode(commands.handle(req)))
        return JSONObject().put("t", "res").put("rid", rid).put("body", Protocol.toWire(reply)).toString()
    }
}

/** "Desvincular" on the Child phone: key and token go, the Log says Desvinculado (este teléfono). False if not paired. */
fun unpairHere(store: ChildStore, log: EventLog): Boolean {
    if (!store.paired) return false
    store.clearKeepingFarewell()
    log.unpaired(byParent = false)
    return true
}
