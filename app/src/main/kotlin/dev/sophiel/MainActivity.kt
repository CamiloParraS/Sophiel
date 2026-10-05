package dev.sophiel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import dev.sophiel.capture.ControllerPhase
import dev.sophiel.capture.MaskWindowService
import dev.sophiel.capture.ProjectionController
import dev.sophiel.capture.ProjectionService
import dev.sophiel.capture.isDebuggable
import dev.sophiel.core.Preset
import dev.sophiel.feed.SpikeModel
import dev.sophiel.feed.benchmarkScreen
import dev.sophiel.feed.maskLookScreen
import dev.sophiel.feed.testFeedScreen

/** Top-level app destinations. Benchmark is the heavy-model spike (branch spike/heavy-models). */
private enum class Destination { Status, TestFeed, Benchmark, Masks }

class MainActivity : ComponentActivity() {
    companion object {
        /** From ProjectionService's "paused — tap to resume" notification. */
        const val ACTION_RESUME = "dev.sophiel.RESUME"
    }

    // Consumed once effects are attached, so start() can run its phase effects.
    private var resumeRequested = false

    private val controller: ProjectionController
        get() = (application as SophielApp).container.projectionController

    // Consent result payload, needed to start the service (SPEC.md §4.4 STARTING_SERVICE).
    // Kept out of ProjectionController so its state machine stays Android-type-free and testable.
    private var pendingResultCode = Activity.RESULT_CANCELED
    private var pendingResultData: Intent? = null

    // D32: release builds start only with MaskWindowService on. Re-read on resume (back from Settings).
    private var maskWindowOn by mutableStateOf(false)

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var consentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resumeRequested = savedInstanceState == null && intent?.action == ACTION_RESUME

        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                controller.onNotificationsResult(granted)
            }
        consentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                pendingResultCode = result.resultCode
                pendingResultData = result.data
                controller.onConsentResult(result.resultCode == Activity.RESULT_OK && result.data != null)
            }

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var current by remember { mutableStateOf(Destination.Status) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Destination.entries.forEach { dest ->
                                TextButton(onClick = { current = dest }) { Text(dest.name) }
                            }
                        }
                    },
                ) { padding ->
                    when (current) {
                        Destination.Status -> statusScreen(controller, (application as SophielApp).container, maskWindowOn, Modifier.padding(padding))
                        Destination.TestFeed -> testFeedScreen(modifier = Modifier.padding(padding))
                        Destination.Benchmark -> benchmarkScreen(modifier = Modifier.padding(padding))
                        Destination.Masks -> maskLookScreen(modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.effects = activityEffects()
        controller.recheckOverlay() // re-check after a possible trip to Settings
        consumeResume()
    }

    override fun onResume() {
        super.onResume()
        maskWindowOn = MaskWindowService.instance != null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_RESUME) resumeRequested = true
        consumeResume() // already started (e.g. tapped from the shade over this Activity)
    }

    /** Needs effects attached, so it waits for onStart() if the Activity is stopped. */
    private fun consumeResume() {
        if (!resumeRequested || controller.effects == null) return
        resumeRequested = false
        if (controller.state.value.phase == ControllerPhase.IDLE && canStart(MaskWindowService.instance != null)) controller.start()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        (application as SophielApp).container.ownScreens
            .update(this, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !isInMultiWindowMode)
    }

    override fun onStop() {
        controller.effects = null
        super.onStop()
    }

    private fun activityEffects() = object : ProjectionController.Effects {
        override fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                controller.onNotificationsResult(granted = true)
            }
        }

        override fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this@MainActivity)

        override fun requestOverlayPermission() {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }

        override fun launchConsentRequest() {
            val manager = getSystemService(MediaProjectionManager::class.java)
            consentLauncher.launch(manager.createScreenCaptureIntent())
        }

        override fun startCaptureService() {
            val intent = Intent(this@MainActivity, ProjectionService::class.java)
                .putExtra(ProjectionService.EXTRA_RESULT_CODE, pendingResultCode)
                .putExtra(ProjectionService.EXTRA_RESULT_DATA, pendingResultData)
            startForegroundService(intent)
        }

        override fun stopCaptureService() {
            val intent = Intent(this@MainActivity, ProjectionService::class.java)
                .setAction(ProjectionService.ACTION_STOP)
            startService(intent)
        }
    }
}

/** D32: debug builds may start on the 0.79 app overlay; release needs the accessibility mask window. */
private fun Context.canStart(maskWindowOn: Boolean) = isDebuggable || maskWindowOn

/**
 * Ticket 10: on/off on hard-coded settings (Balanced, Normal) until M6's Settings. Starting needs no
 * PIN. The model, preset and peek chips are debug-only.
 */
@Composable
private fun statusScreen(controller: ProjectionController, container: AppContainer, maskWindowOn: Boolean, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val debug = context.isDebuggable
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.phase.name, style = MaterialTheme.typography.titleMedium)
            if (state.degraded) {
                Text("Notifications denied — status is logcat-only", style = MaterialTheme.typography.bodySmall)
            }
            if (state.phase == ControllerPhase.BLOCKED) {
                Text("Overlay permission is required.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            // Release: the hard-coded settings. Debug: the chips show what is picked.
            if (debug) debugChips(container) else Text("Balanced · Normal", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            if (!maskWindowOn && state.phase == ControllerPhase.IDLE) {
                Text(
                    if (debug) "Accessibility off: masks draw at 0.79 (debug only)" else "Turn on Sophiel in Accessibility settings to start.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Open Accessibility settings")
                }
            }
            when (state.phase) {
                ControllerPhase.IDLE -> Button(onClick = controller::start, enabled = context.canStart(maskWindowOn)) { Text("Start protection") }
                ControllerPhase.RUNNING -> Button(onClick = controller::stop) { Text("Stop protection") }
                ControllerPhase.BLOCKED -> Button(onClick = controller::recheckOverlay) { Text("Retry") }
                else -> Text("Working…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Spike and experiment switches (D24, D34, D35), live between frames. Move to M6's debug menu. */
@Composable
private fun debugChips(container: AppContainer) {
    var liveModel by remember { mutableStateOf(container.liveModel) }
    var livePreset by remember { mutableStateOf(container.livePreset) }
    var peek by remember { mutableStateOf(container.peekUnderMask) }
    var precise by remember { mutableStateOf(container.precise) }
    // Window shots (D34) need Android 14+; the accessibility service is checked live by the session.
    val canShoot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Spike (D24): switches the live model between frames, no restart needed. Precise picks its own.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpikeModel.entries.forEach { m ->
                FilterChip(
                    selected = m == liveModel,
                    onClick = { liveModel = m; container.liveModel = m },
                    enabled = !precise,
                    label = { Text(m.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Preset.entries.forEach { p ->
                FilterChip(
                    selected = !precise && p == livePreset,
                    onClick = { precise = false; container.precise = false; livePreset = p; container.livePreset = p },
                    label = { Text(p.name) },
                )
            }
            // D35: where shots stop working mid-session (service off), Precise runs Balanced tiles.
            FilterChip(
                selected = precise,
                onClick = {
                    precise = true; container.precise = true
                    livePreset = Preset.BALANCED; container.livePreset = Preset.BALANCED
                },
                enabled = canShoot,
                label = { Text(if (canShoot) "PRECISE" else "PRECISE (Android 14+)") },
            )
        }
        // Ticket 16: live, like the chips above. Needs the accessibility service on as well.
        FilterChip(
            selected = peek && canShoot,
            onClick = { peek = !peek; container.peekUnderMask = peek },
            enabled = canShoot,
            label = { Text(if (canShoot) "Peek under mask" else "Peek under mask (Android 14+)") },
        )
    }
}
