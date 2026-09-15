package dev.sophiel.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import dev.sophiel.R
import dev.sophiel.SophielApp
import dev.sophiel.core.Detector
import dev.sophiel.core.DetectorFactory
import dev.sophiel.core.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service owning the [MediaProjection] session (SPEC.md §4.4 STARTING_SERVICE
 * through STOPPING). No overlay yet (M3 scope) — results go to the notification and Logcat.
 *
 * `startForeground()` is called before [MediaProjectionManager.getMediaProjection] on every
 * path through [onStartCommand]; reordering that throws `SecurityException` on API 34+
 * (SPEC.md §4.2).
 */
class ProjectionService : Service() {

    companion object {
        private const val TAG = "Sophiel"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1
        private const val MIN_CAPTURE_SHORT_SIDE = 360f
        private const val FRAME_INTERVAL_MS = 80L

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val ACTION_STOP = "dev.sophiel.capture.STOP"
    }

    private val controller get() = (application as SophielApp).container.projectionController
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var frameSource: FrameSource? = null
    private var detector: Detector? = null
    private val throttle = FrameThrottle(FRAME_INTERVAL_MS)
    private var isTornDown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, android.app.Activity.RESULT_CANCELED)
            ?: android.app.Activity.RESULT_CANCELED
        val resultData = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }

        startForegroundWithType()
        controller.onServiceStarted()

        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
            Log.d(TAG, "missing consent result; stopping")
            teardown()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.d(TAG, "getMediaProjection() returned null; stopping")
            teardown()
            return START_NOT_STICKY
        }
        controller.onProjectionAcquired()
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                controller.onProjectionStopped()
                teardown()
            }
        }, null)

        startCapture(projection)
        return START_NOT_STICKY
    }

    private fun startCapture(projection: MediaProjection) {
        detector = DetectorFactory.create(applicationContext)
        val size = computeCaptureSize()
        val source = FrameSource(size.width, size.height, ::onFrame)
        frameSource = source
        virtualDisplay = projection.createVirtualDisplay(
            "SophielCapture",
            size.width, size.height, size.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            source.surface, null, null,
        )
    }

    /** Rotation must `resize()` + `setSurface()`, never recreate the [VirtualDisplay] (SPEC.md §4.2). */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val display = virtualDisplay ?: return
        val size = computeCaptureSize()
        val newSource = FrameSource(size.width, size.height, ::onFrame)
        display.resize(size.width, size.height, size.densityDpi)
        display.setSurface(newSource.surface)
        frameSource?.close()
        frameSource = newSource
    }

    private fun onFrame(bitmap: Bitmap) {
        val now = SystemClock.elapsedRealtime()
        if (!throttle.shouldProcess(now)) return
        val detector = detector ?: return

        serviceScope.launch {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            if (BlackFrameDetector.isAllBlack(pixels)) {
                Log.d(TAG, "protected content (all-black frame, likely FLAG_SECURE)")
                updateNotification("Protected content — not analyzable")
                return@launch
            }
            val verdict = detector.analyze(bitmap)
            Log.d(
                TAG,
                "severity=${verdict.severity} score=${verdict.score} gated=${verdict.gated} latencyMs=${verdict.latencyMs}"
            )
            updateNotification(verdict)
        }
    }

    private fun teardown() {
        if (isTornDown) return
        isTornDown = true
        virtualDisplay?.release()
        frameSource?.close()
        mediaProjection?.stop()
        detector?.close()
        serviceScope.cancel()
        controller.onTeardownComplete()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun computeCaptureSize(): CaptureSize {
        val windowManager = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val scale = MIN_CAPTURE_SHORT_SIDE / minOf(metrics.widthPixels, metrics.heightPixels)
        val width = (metrics.widthPixels * scale).toInt() and 0xFFFFFFFE.toInt()
        val height = (metrics.heightPixels * scale).toInt() and 0xFFFFFFFE.toInt()
        return CaptureSize(width, height, metrics.densityDpi)
    }

    private fun startForegroundWithType() {
        val notification = buildNotification("Starting protection…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(verdict: Verdict) {
        val text = "%s · score=%.2f".format(verdict.severity, verdict.score)
        updateNotification(text)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle("Sophiel protection running")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Screen protection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

private data class CaptureSize(val width: Int, val height: Int, val densityDpi: Int)
