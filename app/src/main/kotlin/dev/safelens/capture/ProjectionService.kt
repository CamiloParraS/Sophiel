package dev.safelens.capture

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that will own the [android.media.projection.MediaProjection]
 * session. M0 stub so the manifest entry (SPEC.md §4.1) resolves; the §4.4 startup
 * state machine and capture pipeline land in M3.
 */
class ProjectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
