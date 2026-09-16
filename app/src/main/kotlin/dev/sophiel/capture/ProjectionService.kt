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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import dev.sophiel.R
import dev.sophiel.SophielApp

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
        private const val NOTIFICATION_ID = 1

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val ACTION_STOP = "dev.sophiel.capture.STOP"
    }

    private val controller get() = (application as SophielApp).container.projectionController
    private var session: CaptureSession? = null
    private var isTornDown = false

    // Tear down proactively on screen-off rather than depend on when/whether the OS revokes the
    // projection (varies by device; DECISIONS.md D18). The next Start needs fresh consent anyway.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "screen off; ending capture session")
            teardown()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // From the notification's Stop action (the only status-bar stop on One UI 13, D17) or
        // from MainActivity's stop().
        if (intent?.action == ACTION_STOP) {
            teardown()
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
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = teardown()
        }, null)

        session = CaptureSession(this, projection, captureSize(this), ::updateNotification)
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        session?.resize(captureSize(this))
    }

    private fun teardown() {
        if (isTornDown) return
        isTornDown = true
        unregisterReceiver(screenOffReceiver)
        session?.close()
        controller.onTeardownComplete()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun startForegroundWithType() {
        val notification = buildNotification("Starting protection…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, ProjectionService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle("Sophiel protection running")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Screen protection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
