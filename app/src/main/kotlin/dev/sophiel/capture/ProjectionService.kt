package dev.sophiel.capture

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
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
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
    private var debugPill: DebugPillOverlay? = null

    // Frame being analysed, if any. New frames are dropped while it runs (SPEC.md §4.5): the
    // throttle alone let frames queue on the single inference thread whenever analysis took
    // longer than 80 ms, so latency and held bitmaps grew without bound.
    @Volatile private var inFlight: Job? = null

    // Human-observed on-device (2026-09-14, Device B): the session was left in an unclear
    // state after the screen turned off mid-capture, and starting again didn't work cleanly.
    // Rather than depend on exactly when/whether the OS revokes MediaProjection on screen-off
    // (unconfirmed, varies by device), tear down proactively so the next Start always begins
    // from a guaranteed-clean IDLE with fresh consent (SPEC.md §4.2's "no remember my choice"
    // already means a session can't survive this anyway).
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "screen off; ending capture session")
            controller.onProjectionStopped()
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
        if (intent?.action == ACTION_STOP) {
            // Only path to stop from the status bar on this device/OS (no separate system
            // "stop casting" chip was found on Android 13/One UI) — treat it as an external
            // stop so the controller reaches STOPPING even if no Activity is alive to have
            // called ProjectionController.stop() first (SPEC.md M3.V5).
            controller.onProjectionStopped()
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
        val source = FrameSource(size.width, size.height, size.crop, ::wantsFrame, ::onFrame)
        frameSource = source
        virtualDisplay = projection.createVirtualDisplay(
            "SophielCapture",
            size.width, size.height, size.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            source.surface, null, null,
        )
        if (isDebuggable) debugPill = DebugPillOverlay(this).also { it.show() }
    }

    /** Rotation must `resize()` + `setSurface()`, never recreate the [VirtualDisplay] (SPEC.md §4.2). */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val display = virtualDisplay ?: return
        val size = computeCaptureSize()
        val newSource = FrameSource(size.width, size.height, size.crop, ::wantsFrame, ::onFrame)
        display.resize(size.width, size.height, size.densityDpi)
        display.setSurface(newSource.surface)
        frameSource?.close()
        frameSource = newSource
    }

    /** Called on the FrameSource thread before an Image is decoded; false drops it undecoded. */
    private fun wantsFrame(): Boolean =
        detector != null && inFlight?.isActive != true && throttle.shouldProcess(SystemClock.elapsedRealtime())

    private fun onFrame(bitmap: Bitmap) {
        val detector = detector ?: return bitmap.recycle()

        inFlight = serviceScope.launch {
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                if (BlackFrameDetector.isAllBlack(pixels)) {
                    Log.d(TAG, "protected content (all-black frame, likely FLAG_SECURE)")
                    updateNotification("Protected content — not analyzable")
                    debugPill?.update("PROTECTED — not analyzable")
                    return@launch
                }
                val verdict = detector.analyze(bitmap)
                Log.d(
                    TAG,
                    "severity=${verdict.severity} score=${verdict.score} gated=${verdict.gated} latencyMs=${verdict.latencyMs}"
                )
                updateNotification(verdict)
                debugPill?.update(
                    "%s · score=%.2f · gated=%b · %dms".format(
                        verdict.severity, verdict.score, verdict.gated, verdict.latencyMs,
                    ),
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun teardown() {
        if (isTornDown) return
        isTornDown = true
        debugPill?.hide()
        unregisterReceiver(screenOffReceiver)
        virtualDisplay?.release()
        frameSource?.close()
        mediaProjection?.stop()
        serviceScope.cancel()
        detector?.close() // waits out any in-flight inference
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

        // Crop status/navigation bars and the cutout (SPEC.md §4.6): never content, always in
        // the frame. "Ignoring visibility" keeps the crop stable when an app hides the bars,
        // so the dHash cache isn't invalidated by a fullscreen toggle.
        val crop = Rect(0, 0, width, height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            crop.set(
                (bars.left * scale).toInt(), (bars.top * scale).toInt(),
                width - (bars.right * scale).toInt(), height - (bars.bottom * scale).toInt(),
            )
        } // ponytail: no bar crop below API 30 (no insets API from a Service); both project devices are 33+.
        Log.d(TAG, "capture ${width}x$height crop=$crop")
        return CaptureSize(width, height, metrics.densityDpi, crop)
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

private data class CaptureSize(val width: Int, val height: Int, val densityDpi: Int, val crop: Rect)
