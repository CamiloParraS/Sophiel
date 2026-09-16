package dev.sophiel

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import dev.sophiel.capture.ControllerPhase
import dev.sophiel.capture.ProjectionController
import dev.sophiel.capture.ProjectionService
import dev.sophiel.feed.testFeedScreen

/** Top-level app destinations. Benchmark is an M0 stub — it lands in a later milestone. */
private enum class Destination { Protection, TestFeed, Benchmark }

class MainActivity : ComponentActivity() {
    private val controller: ProjectionController
        get() = (application as SophielApp).container.projectionController

    // Consent result payload, needed to start the service (SPEC.md §4.4 STARTING_SERVICE).
    // Kept out of ProjectionController so its state machine stays Android-type-free and testable.
    private var pendingResultCode = Activity.RESULT_CANCELED
    private var pendingResultData: Intent? = null

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var consentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                var current by remember { mutableStateOf(Destination.Protection) }
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
                        Destination.Protection -> protectionScreen(controller, modifier = Modifier.padding(padding))
                        Destination.TestFeed -> testFeedScreen(modifier = Modifier.padding(padding))
                        Destination.Benchmark -> Box(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) { Text("${current.name} — coming soon") }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.effects = activityEffects()
        controller.recheckOverlay() // re-check after a possible trip to Settings
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

/** M3 scope: no overlay yet — this just drives the permission flow and shows the raw state. */
@Composable
private fun protectionScreen(controller: ProjectionController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
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
            when (state.phase) {
                ControllerPhase.IDLE -> Button(onClick = controller::start) { Text("Start protection") }
                ControllerPhase.RUNNING -> Button(onClick = controller::stop) { Text("Stop protection") }
                ControllerPhase.BLOCKED -> Button(onClick = controller::recheckOverlay) { Text("Retry") }
                else -> Text("Working…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
