package dev.sophiel.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import dev.sophiel.MainActivity
import dev.sophiel.R
import dev.sophiel.SophielApp
import dev.sophiel.log.OffReason

/**
 * Foreground service owning the [MediaProjection] session (SPEC.md §4.4 STARTING_SERVICE
 * through STOPPING). No overlay yet (M3 scope) — results go to the notification and Logcat.
 * The capture pipeline itself lives in [CaptureSession].
 *
 * `startForeground()` is called before [MediaProjectionManager.getMediaProjection] on every
 * path through [onStartCommand]; reordering that throws `SecurityException` on API 34+
 * (SPEC.md §4.2).
 *
 * Every way a session ends goes through [teardown], which reports it to the controller once.
 */
class ProjectionService : Service() {

    companion object {
        private const val TAG = "Sophiel"
        private const val CHANNEL_ID = "capture"
        private const val ALERT_CHANNEL_ID = "capture_alert" // importance is fixed once created, so a new id
        private const val NOTIFICATION_ID = 1
        private const val RESUME_NOTIFICATION_ID = 2

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val ACTION_STOP = "dev.sophiel.capture.STOP"
    }

    private val container get() = (application as SophielApp).container
    private val controller get() = container.projectionController
    private var session: CaptureSession? = null
    private var isTornDown = false
    private var logged = false // ON was written, so OFF must be

    // API 35+: tear down proactively on screen-off rather than depend on when/whether the OS revokes
    // the projection (D18). Below 35 the share survives it (D55): pause frames, resume at screen-on.
    // Switch off, or no service to fall back on: the share ends at screen-off on every API (SPEC.md §4.7).
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                if (session != null) Log.d(TAG, "screen on; frames resume")
                session?.screenOff(false)
                return
            }
            val keep = container.settings.value.keepProtecting && MaskWindowService.instance != null
            if (keep && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                Log.d(TAG, "screen off; pausing frames (D55)")
                session?.screenOff(true)
                return
            }
            Log.d(TAG, "screen off; ending capture session")
            teardown(screenOff = true) // the broadcast is the proof: isInteractive can still read true here
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // From MainActivity's stop(). The notification has no Stop (D38: stopping needs the PIN).
        if (intent?.action == ACTION_STOP) {
            teardown(userStop = true)
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }

        startForegroundWithType()
        controller.onServiceStarted()

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.d(TAG, "missing consent result; stopping")
            teardown()
            return START_NOT_STICKY
        }

        val projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.d(TAG, "getMediaProjection() returned null; stopping")
            teardown()
            return START_NOT_STICKY
        }
        controller.onProjectionAcquired()
        container.let { c ->
            logged = true
            c.log.on(c.settings.value.preset.name, c.settings.value.sensitivity.name)
        }
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = teardown()
        }, null)

        session = CaptureSession(this, projection, captureSize(this), container)
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        session?.resize(captureSize(this))
    }

    private fun teardown(userStop: Boolean = false, screenOff: Boolean = false) {
        if (isTornDown) return
        isTornDown = true
        unregisterReceiver(screenReceiver)
        session?.close() // writes an open unanalyzable stretch before the OFF
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        var systemStop = false
        if (logged) {
            val reason = when {
                userStop -> OffReason.USER
                screenOff || !interactive -> OffReason.SCREEN_OFF
                else -> OffReason.SYSTEM
            }
            container.log.off(reason)
            systemStop = reason == OffReason.SYSTEM
        }
        // Switch off: protection ends with the share (SPEC.md §4.7), or a stale on resumes it without a Start.
        if (!userStop && !container.settings.value.keepProtecting) container.protection.protecting = false
        controller.onTeardownComplete()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Screen off means the OS or our receiver ended it, not the user: offer a one-tap restart.
        // Covers both orders D19 observed (projection onStop() first, or the receiver first).
        if (!interactive) postResumeNotification(R.string.notif_paused_title, R.string.notif_paused_text)
        // The system ended it while the screen was on: protection is silently gone, so say so.
        else if (systemStop) postResumeNotification(R.string.notif_off_title, R.string.notif_off_text)
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /** Tapping it opens MainActivity, which starts the normal flow; consent is still fresh (§4.2). */
    private fun postResumeNotification(title: Int, text: Int) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_RESUME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle(getString(title))
            .setContentText(getString(text))
            .setSmallIcon(R.drawable.ic_stat_sophiel)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(RESUME_NOTIFICATION_ID, notification)
    }

    private fun startForegroundWithType() {
        getSystemService(NotificationManager::class.java).cancel(RESUME_NOTIFICATION_ID)
        val notification = buildNotification(R.string.notif_starting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle(getString(R.string.notif_running_title))
            .setContentText(getString(text))
            .setSmallIcon(R.drawable.ic_stat_sophiel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        val alert = NotificationChannel(ALERT_CHANNEL_ID, getString(R.string.notif_alert_channel), NotificationManager.IMPORTANCE_HIGH)
            .apply { enableVibration(true) }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, alert))
    }
}
