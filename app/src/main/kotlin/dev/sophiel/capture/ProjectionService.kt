package dev.sophiel.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import dev.sophiel.MainActivity
import dev.sophiel.R
import dev.sophiel.SophielApp

private const val CHANNEL_ID = "capture"
private const val ALERT_CHANNEL_ID = "capture_alert" // importance is fixed once created, so a new id
private const val RESUME_NOTIFICATION_ID = 2

/**
 * Foreground service that acquires the [MediaProjection] (SPEC.md §4.4 STARTING_SERVICE through STOPPING)
 * and hands it to [CaptureModeController], which owns the session, the screen-off rules and the Log.
 * This service exists only while a share does.
 *
 * `startForeground()` is called before [MediaProjectionManager.getMediaProjection] on every
 * path through [onStartCommand]; reordering that throws `SecurityException` on API 34+
 * (SPEC.md §4.2).
 *
 * Every way a share ends goes through [teardown], which reports it once.
 */
class ProjectionService : Service() {

    companion object {
        private const val TAG = "Sophiel"
        private const val NOTIFICATION_ID = 1

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
    }

    private val container get() = (application as SophielApp).container
    private val controller get() = container.projectionController
    private var isTornDown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        container.captureModes.shareStarted(projection)
        return START_NOT_STICKY
    }

    private fun teardown() {
        if (isTornDown) return
        isTornDown = true
        container.captureModes.shareEnded() // a late one (the controller ended it) changes nothing
        controller.onTeardownComplete()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
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

/**
 * D21: protection ended on its own. Tapping it opens MainActivity, which starts the normal flow; consent
 * is still fresh (§4.2). The alert channel exists: only a share's end posts this, after the service ran.
 */
internal fun Context.postResumeNotification(title: Int, text: Int) {
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
