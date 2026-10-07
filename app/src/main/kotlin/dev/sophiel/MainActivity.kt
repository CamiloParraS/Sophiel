package dev.sophiel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import dev.sophiel.R
import dev.sophiel.capture.ControllerPhase
import dev.sophiel.capture.MaskWindowService
import dev.sophiel.capture.ProjectionController
import dev.sophiel.capture.ProjectionService
import dev.sophiel.feed.benchmarkScreen
import dev.sophiel.feed.maskLookScreen
import dev.sophiel.feed.testFeedScreen
import dev.sophiel.ui.SetupWizard
import dev.sophiel.ui.Need
import dev.sophiel.ui.StatusActions
import dev.sophiel.ui.StatusInput
import dev.sophiel.ui.StatusModel
import dev.sophiel.ui.StatusScreen
import dev.sophiel.ui.Door
import dev.sophiel.ui.DoorHost
import dev.sophiel.ui.FlatIconButton
import dev.sophiel.ui.GearButton
import dev.sophiel.ui.HeaderBar
import dev.sophiel.ui.DebugMenu
import dev.sophiel.ui.DebugPage
import dev.sophiel.ui.LogScreen
import dev.sophiel.ui.SettingsScreen
import dev.sophiel.ui.PillButton
import dev.sophiel.ui.PillStyle
import dev.sophiel.ui.UnlockedBanner
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.SophielTheme

class MainActivity : ComponentActivity() {
    companion object {
        /** From ProjectionService's "paused — tap to resume" notification. */
        const val ACTION_RESUME = "dev.sophiel.RESUME"
    }

    // Consumed once effects are attached, so start() can run its phase effects.
    private var resumeRequested = false

    // Status re-reads overlay and notifications whenever this changes (D42): every resume, and a permission result.
    private val resumes = mutableIntStateOf(0)
    private var fixAskedAt = 0L // when a Status "Permitir" asked for notifications, to tell a shown dialog from a silent denial

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
        resumeRequested = savedInstanceState == null && intent?.action == ACTION_RESUME

        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                controller.onNotificationsResult(granted) // ignored outside the start flow
                // Android stopped showing the dialog (denied twice): it returns at once, so open the settings page instead.
                if (!granted && fixAskedAt != 0L && SystemClock.elapsedRealtime() - fixAskedAt < 400) openNotificationSettings()
                fixAskedAt = 0
                resumes.intValue++
            }
        consentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                pendingResultCode = result.resultCode
                pendingResultData = result.data
                controller.onConsentResult(result.resultCode == Activity.RESULT_OK && result.data != null)
            }

        // Light-only theme (D43): dark system-bar icons whatever the system mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            SophielTheme {
                val container = (application as SophielApp).container
                // D42: the wizard runs while no PIN exists. Saved, so it survives rotation once step 1 has made the PIN;
                // a wizard swiped away after that opens on Status next time.
                var setup by rememberSaveable { mutableStateOf(!container.pin.exists()) }
                if (setup) {
                    SetupWizard(container, resumes.intValue, ::fix, start = { setup = false; controller.start() })
                } else {
                    DoorHost(container) { door -> appBody(container, door, rerunWizard = { setup = true }) }
                }
            }
        }
    }

    @Composable
    private fun appBody(container: AppContainer, door: Door, rerunWizard: () -> Unit) {
        // Behind the door: rotation keeps them, a relock closes them all (D38).
        var logOpen by rememberSaveable { mutableStateOf(false) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var debugOpen by rememberSaveable { mutableStateOf(false) }
        var debugPage by rememberSaveable { mutableStateOf<DebugPage?>(null) }
        LaunchedEffect(door.unlocked) {
            if (!door.unlocked) { logOpen = false; settingsOpen = false; debugOpen = false; debugPage = null }
        }
        BackHandler(logOpen || settingsOpen || debugOpen) {
            when {
                debugPage != null -> debugPage = null
                debugOpen -> debugOpen = false
                logOpen -> logOpen = false
                else -> settingsOpen = false
            }
        }
        if (debugOpen) {
            val page = debugPage
            if (page == null) {
                DebugMenu(container, door, onBack = { debugOpen = false }, open = { debugPage = it }, rerunWizard = rerunWizard)
            } else {
                Column(Modifier.fillMaxSize()) {
                    HeaderBar(stringResource(page.title), start = { FlatIconButton(R.drawable.ic_back, stringResource(R.string.back), { debugPage = null }) })
                    val pageModifier = Modifier.weight(1f).navigationBarsPadding()
                    when (page) {
                        DebugPage.TestFeed -> testFeedScreen(pageModifier)
                        DebugPage.Benchmark -> benchmarkScreen(pageModifier)
                        DebugPage.Masks -> maskLookScreen(pageModifier)
                    }
                }
            }
            return
        }
        if (logOpen) {
            LogScreen(container.log, door, onBack = { logOpen = false })
            return
        }
        if (settingsOpen) {
            SettingsScreen(container, door, onBack = { settingsOpen = false }, onHistory = { logOpen = true }, onDebug = { debugOpen = true })
            return
        }
        val actions = remember {
            StatusActions(
                start = { if (controller.state.value.phase == ControllerPhase.BLOCKED) controller.recheckOverlay() else controller.start() },
                stop = { door.pass(controller::stop) },
                fix = ::fix,
                openSettings = { settingsOpen = true },
                seeLog = { door.pass { logOpen = true } },
            )
        }
        statusScreen(controller, container, resumes.intValue, door, actions)
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }

    /** Status fix buttons (D41, D42): no PIN, each opens the one place that fixes it. */
    private fun fix(need: Need) {
        when (need) {
            Need.OVERLAY -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Need.SERVICE -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Need.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    fixAskedAt = SystemClock.elapsedRealtime()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings()
                }
        }
    }

    private fun openNotificationSettings() =
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

    override fun onStart() {
        super.onStart()
        controller.effects = activityEffects()
        controller.recheckOverlay() // re-check after a possible trip to Settings
        consumeResume()
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
        if (controller.state.value.phase == ControllerPhase.IDLE) controller.start()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        (application as SophielApp).container.ownScreens
            .update(this, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !isInMultiWindowMode)
    }

    override fun onStop() {
        (application as SophielApp).container.unlock.onActivityStop(isChangingConfigurations)
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

/** Reads the live inputs (D42) into a [StatusModel] and draws it; the debug chips sit below the fold in debug builds. */
@Composable
private fun statusScreen(controller: ProjectionController, container: AppContainer, resumes: Int, door: Door, actions: StatusActions, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val settings by container.settings.state.collectAsState()
    val bound = MaskWindowService.bound.collectAsState().value != null
    val context = LocalContext.current
    val overlay = remember(resumes) { Settings.canDrawOverlays(context) }
    val notifications = remember(resumes) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    // The Log is written at ON / OFF, which coincide with phase changes.
    val last = remember(state.phase, resumes) { container.log.last() }
    val model = StatusModel.of(
        StatusInput(state.phase, bound, overlay, notifications, last, settings.preset, settings.sensitivity, Build.VERSION.SDK_INT),
    )
    StatusScreen(model, door, actions, modifier)
}
