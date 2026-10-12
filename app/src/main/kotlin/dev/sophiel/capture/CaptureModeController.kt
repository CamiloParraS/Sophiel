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
 * the Log's ON / OFF are written. Main thread only.
 */
class CaptureModeController(context: Context, private val container: AppContainer) {
    private val context = context.applicationContext
    private val power = context.getSystemService(PowerManager::class.java)

    private val _state = MutableStateFlow(
        ModeState(
            protecting = container.protection.protecting,
            screenOn = power.isInteractive,
            serviceBound = serviceBound(),
            keepProtecting = container.settings.value.keepProtecting,
            shareSurvivesScreenOff = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM, // D55
        ),
    )
    val state: StateFlow<ModeState> = _state.asStateFlow()

    private var session: CaptureSession? = null

    init {
        this.context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) =
                onEvent(if (intent.action == Intent.ACTION_SCREEN_ON) ModeEvent.ScreenOn else ModeEvent.ScreenOff)
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

    /** The Parent's PIN Stop. */
    fun stop() = onEvent(ModeEvent.Stop)

    private fun onEvent(e: ModeEvent) {
        val old = _state.value
        val new = CaptureModeMachine.reduce(old, e)
        if (new == old) return
        _state.value = new
        Log.i(TAG, "$e: capture mode ${old.mode} -> ${new.mode}, protecting=${new.protecting} screenOn=${new.screenOn}")
        if (new.protecting != old.protecting) container.protection.protecting = new.protecting
        applyToSession(old, new)
        if (old.protecting && !new.protecting) context.cancelResumeNotification() // a stale "tap to resume" must not start a session; the OFF below may post its own
        if (CaptureModeMachine.slowMode(old, new)) { // D54: the Parent can ask for the fast mode back; Sophiel never does
            container.log.shareStopped()
            context.postResumeNotification(R.string.notif_slow_title, R.string.notif_slow_text)
        }
        // After the session: closing it writes an open unanalyzable stretch, which goes before the OFF.
        CaptureModeMachine.offReason(old, new, e)?.let { reason ->
            container.log.off(reason)
            when (reason) { // D21: offer a one-tap restart; consent is still fresh (SPEC.md §4.2)
                OffReason.SCREEN_OFF -> context.postResumeNotification(R.string.notif_paused_title, R.string.notif_paused_text)
                OffReason.SYSTEM -> context.postResumeNotification(R.string.notif_off_title, R.string.notif_off_text)
            }
        }
    }

    private fun applyToSession(old: ModeState, new: ModeState) {
        val shareEnded = old.mode == CaptureMode.SCREEN_SHARE && new.mode != CaptureMode.SCREEN_SHARE
        if (!new.holdsSession) {
            session?.close()
            session = null
        } else {
            if (session == null && new.mode == CaptureMode.ACCESSIBILITY) session = newSession(null) // a bind, after a restart too
            session?.setScreenOff(!new.screenOn) // D55 pause; before endShare, so the shot loop starts paused
            if (shareEnded) session?.endShare()
        }
        // The machine can end the share itself (screen-off, Stop): no FGS without a share (SPEC.md §4.7).
        if (shareEnded) context.stopService(Intent(context, ProjectionService::class.java))
    }

    /** D39: every session is an ON (and starts the heartbeat), so a restart into accessibility is logged too. */
    private fun newSession(projection: MediaProjection?): CaptureSession {
        val settings = container.settings.value
        container.log.on(settings.preset.name, settings.sensitivity.name)
        return CaptureSession(context, projection, captureSize(context), container)
    }

    private companion object {
        const val TAG = "Sophiel"

        /** Bound (D41), and able to take display shots (API 30+): without them there is nothing to fall back on. */
        fun serviceBound() = MaskWindowService.instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}
