package dev.sophiel.capture

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.projection.MediaProjection
import android.os.Build
import android.os.PowerManager
import android.util.Log
import dev.sophiel.AppContainer
import dev.sophiel.R
import dev.sophiel.log.GapReason
import dev.sophiel.log.OffReason
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives [CaptureModeMachine] (SPEC.md §4.7) and makes the one [CaptureSession] follow it: frames from
 * the share, then from display shots once it ends, masks and tracker carried over (D54). Process-wide
 * in [AppContainer], because the session outlives the share's [ProjectionService]. Also the one place
 * the Log's ON / OFF and Sin protección stretches are written. Main thread only.
 */
class CaptureModeController(context: Context, private val container: AppContainer) {
    private val context = context.applicationContext
    private val power = context.getSystemService(PowerManager::class.java)

    private val _state = MutableStateFlow(
        ModeState(
            // Switch off, protection ended with the share; a new process has none, so it ended (recoverGap wrote the OFF).
            protecting = container.protection.protecting && container.settings.value.keepProtecting,
            screenOn = power.isInteractive,
            serviceBound = serviceBound(),
            keepProtecting = container.settings.value.keepProtecting,
            shareSurvivesScreenOff = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM, // D55
        ),
    )
    val state: StateFlow<ModeState> = _state.asStateFlow()

    private var session: CaptureSession? = null

    init {
        container.protection.protecting = _state.value.protecting
        followGap(_state.value, found = true) // back from a kill or a reboot: a stretch to open, or a recovered one to end
        this.context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) container.unlock.lockNow() // D56: the guard's Unlock outlives the app, not the screen
                onEvent(if (intent.action == Intent.ACTION_SCREEN_ON) ModeEvent.ScreenOn else ModeEvent.ScreenOff)
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })
        // Rotation must resize, never recreate the display (SPEC.md §4.2); in accessibility mode no service of ours that lives as long as the session gets it.
        this.context.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                session?.resize(captureSize(this@CaptureModeController.context))
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
        MainScope().launch { // the switch, from Ajustes (and the wizard, ticket 18)
            container.settings.state.collect {
                if (it.keepProtecting != _state.value.keepProtecting) onEvent(if (it.keepProtecting) ModeEvent.SwitchOn else ModeEvent.SwitchOff)
            }
        }
    }

    /** The share's projection is acquired (SPEC.md §4.4 RUNNING): Start is complete. */
    fun shareStarted(projection: MediaProjection) {
        // D54: the Parent's Start: from off, or ending a Sin protección stretch (not a return from slow mode).
        if (!_state.value.protecting || container.log.gap() != null) container.settings.value.let { container.log.on(it.preset.name, it.sensitivity.name) }
        val live = session
        if (live != null && !live.sharing) {
            live.startShare(projection) // D54: from accessibility, the same session: projection first, then the shots stop
        } else {
            live?.close()
            session = newSession(projection)
        }
        onEvent(ModeEvent.ShareStarted)
    }

    /**
     * The share's projection stopped, however it ended. D19: its onStop() can beat the screen-off
     * broadcast, so the screen-off goes first: it decides the mode and the OFF reason.
     */
    fun shareEnded() {
        if (!power.isInteractive) onEvent(ModeEvent.ScreenOff)
        onEvent(ModeEvent.ShareEnded)
    }

    /** MaskWindowService bound or unbound. */
    fun serviceChanged() = onEvent(if (serviceBound()) ModeEvent.ServiceBound else ModeEvent.ServiceUnbound)

    /** MainActivity came to the front. */
    fun uiOpened() = onEvent(ModeEvent.UiOpened)

    /** The Parent's PIN Stop. */
    fun stop() {
        container.escalation.clear() // D58
        onEvent(ModeEvent.Stop)
    }

    private fun onEvent(e: ModeEvent) {
        val old = _state.value
        val new = CaptureModeMachine.reduce(old, e)
        if (new == old) return
        _state.value = new
        Log.i(TAG, "$e: capture mode ${old.mode} -> ${new.mode}, protecting=${new.protecting} screenOn=${new.screenOn}")
        if (new.protecting != old.protecting) container.protection.protecting = new.protecting
        applyToSession(old, new)
        // After the session: closing it writes an open unanalyzable stretch, which goes before the stretch and the OFF.
        followGap(new)
        if (old.protecting && !new.protecting) context.cancelResumeNotification() // a stale "tap to resume" must not start a session; the OFF below may post its own
        if (CaptureModeMachine.slowMode(old, new)) { // D54: the Parent can ask for the fast mode back; Sophiel never does
            container.log.shareStopped()
            context.postResumeNotification(R.string.notif_slow_title, R.string.notif_slow_text)
        }
        if (CaptureModeMachine.serviceLost(old, new)) context.postResumeNotification(R.string.notif_interrupted_title, R.string.notif_interrupted_text, resume = false)
        val reason = CaptureModeMachine.offReason(old, new, e)
        reason?.let(container.log::off)
        // D21: offer a one-tap restart; consent is still fresh (SPEC.md §4.2). Switch off it ends protection, switch on without the service a stretch follows (D46).
        if (reason == OffReason.SYSTEM || reason == OffReason.SCREEN_OFF || CaptureModeMachine.lostShare(old, new)) {
            if (new.screenOn) context.postResumeNotification(R.string.notif_off_title, R.string.notif_off_text)
            else context.postResumeNotification(R.string.notif_paused_title, R.string.notif_paused_text)
        }
    }

    /**
     * D54: open or end the Sin protección stretch. Opened live, it is the service going away (a dead process's is
     * opened by EventLog.recoverGap); [found] at app start, it may date from the boot (D61).
     */
    private fun followGap(s: ModeState, found: Boolean = false) {
        val open = container.log.gap() != null
        if (CaptureModeMachine.gapOpens(open, s)) {
            if (found) container.log.gapFound(GapReason.ACCESSIBILITY_OFF) else container.log.gapOpened(GapReason.ACCESSIBILITY_OFF)
        }
        if (CaptureModeMachine.gapEnds(open, s)) container.log.gapClosed()
    }

    private fun applyToSession(old: ModeState, new: ModeState) {
        val shareEnded = old.mode == CaptureMode.SCREEN_SHARE && new.mode != CaptureMode.SCREEN_SHARE
        if (!new.holdsSession) {
            session?.let {
                it.close()
                container.log.capturing(false)
            }
            session = null
        } else {
            if (session == null && new.mode == CaptureMode.ACCESSIBILITY) session = newSession(null) // a bind, after a restart too
            session?.setScreenOff(!new.screenOn) // D55 pause; before endShare, so the shot loop starts paused
            if (shareEnded) session?.endShare()
        }
        // The machine can end the share itself (screen-off, Stop): no FGS without a share (SPEC.md §4.7).
        if (shareEnded) context.stopService(Intent(context, ProjectionService::class.java))
    }

    /** D39: the heartbeat runs while a session does, so a dead process's gap can be inferred. */
    private fun newSession(projection: MediaProjection?): CaptureSession {
        container.log.capturing(true)
        return CaptureSession(context, projection, captureSize(context), container)
    }

    private companion object {
        const val TAG = "Sophiel"

        /** Bound (D41), and able to take display shots (API 30+): without them there is nothing to fall back on. */
        fun serviceBound() = MaskWindowService.instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
