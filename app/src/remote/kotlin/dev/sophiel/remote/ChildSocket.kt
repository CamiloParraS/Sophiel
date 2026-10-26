package dev.sophiel.remote

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dev.sophiel.BuildConfig
import dev.sophiel.SophielApp
import dev.sophiel.capture.MaskWindowService
import dev.sophiel.settings.AppRule
import dev.sophiel.settings.excludedApps
import dev.sophiel.settings.launchableApps
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/** The Child phone's real [ChildPhone]: the controller is main-thread only (CaptureModeController). */
private class AndroidChildPhone(private val app: SophielApp) : ChildPhone {
    private val c get() = app.container
    override suspend fun state() = c.captureModes.state.value
    override suspend fun stop() = withContext(Dispatchers.Main) { c.captureModes.stop(remote = true) }
    override suspend fun start() = withContext(Dispatchers.Main) { c.captureModes.startRemote() }
    override suspend fun rules() = c.appRules.state.value
    override suspend fun setRule(pkg: String, rule: AppRule) = withContext(Dispatchers.Main) {
        c.appRules.set(pkg, rule)
        // D82: Bloqueada with the app in front blocks at once, like an escalation; De confianza waits for the next window change.
        if (rule == AppRule.BLOCKED && c.captureModes.state.value.protecting) MaskWindowService.instance?.blockNow(pkg)
    }
    override suspend fun apps() = withContext(Dispatchers.IO) { launchableApps(app) }
    override suspend fun excluded() = withContext(Dispatchers.IO) { excludedApps(app) }
    override suspend fun log() = withContext(Dispatchers.IO) { c.log.entries() }
}

/**
 * D78, D83: while the process lives and a pairing exists, hold the Child socket: `hello` on open, answer each `req`.
 * Reconnect on network change and screen on (forced), and on a 5-min `setAndAllowWhileIdle` alarm (forced: a silently dead socket is found by the next check).
 */
object ChildSocket {
    private const val TAG = "Sophiel"
    private const val ALARM = "dev.sophiel.remote.CHECK"
    private const val CHECK_MS = 5 * 60_000L
    private val kicks = Channel<Boolean>(Channel.CONFLATED) // true: drop an open socket and reconnect
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build() // a half-open socket dies while awake; the server pings too
    private val _connected = MutableStateFlow(false)
    /** Whether the paired socket is open now: the pairing screen's "Conectado" / "Sin conexión". */
    val connected: StateFlow<Boolean> get() = _connected

    fun start(app: SophielApp) {
        watch(app)
        scope.launch {
            RemoteState.paired.collectLatest { // the container only once paired or saying goodbye
                if (it) hold(app, link(app)) else if (RemoteState.child.unpairToken != null) farewell(link(app))
            }
        }
    }

    private fun link(app: SophielApp) =
        ChildLink(RemoteState.child, app.container.log, ChildCommands(AndroidChildPhone(app)), ReplayGuard(System::currentTimeMillis)) {
            Log.i(TAG, "remote: unpaired by the relay")
            RemoteState.unpaired()
        }

    private fun watch(app: Context) {
        var network: Network? = null
        app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                if (network != null && n != network) kick("network change", force = true)
                network = n
                kick("network", force = false)
            }
        })
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = kick("screen on", force = true)
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (RemoteState.paired.value) arm(context)
                kick("alarm", force = true) // a silently dead socket only shows up by reconnecting (limitation 28)
            }
        }, IntentFilter(ALARM), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun kick(why: String, force: Boolean) {
        if (!RemoteState.paired.value && RemoteState.child.unpairToken == null) return
        Log.d(TAG, "remote: check ($why)")
        kicks.trySend(force)
    }

    private fun alarm(ctx: Context) =
        PendingIntent.getBroadcast(ctx, 0, Intent(ALARM).setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun arm(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)
        .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + CHECK_MS, alarm(ctx))

    private suspend fun hold(app: Context, link: ChildLink) {
        arm(app)
        try {
            var backoff = 2_000L
            while (true) {
                val hello = link.hello() ?: return
                var forced = false
                coroutineScope {
                    val sock = launch {
                        try {
                            open(listOf(hello), onOpen = { _connected.value = true }).collect { (ws, text) ->
                                backoff = 2_000L
                                link.onFrame(text)?.let { ws.send(it) }
                            }
                        } finally {
                            _connected.value = false
                        }
                    }
                    val watcher = launch { while (!kicks.receive()) Unit; forced = true; sock.cancel() }
                    sock.join()
                    watcher.cancel()
                }
                Log.i(TAG, "remote: socket closed${if (forced) ", reconnecting" else ""}")
                if (!forced) {
                    withTimeoutOrNull(backoff) { kicks.receive() } // the alarm covers deep sleep, where this delay stops
                    backoff = (backoff * 2).coerceAtMost(CHECK_MS)
                }
            }
        } finally {
            app.getSystemService(AlarmManager::class.java).cancel(alarm(app))
        }
    }

    /**
     * After Desvincular here: a socket that sends `hello` + `unpair` until the relay closes it (it deleted the pairing)
     * or rejects the token. Offline, it retries on network change, screen on and backoff, like [hold].
     */
    private suspend fun farewell(link: ChildLink) {
        var backoff = 2_000L
        while (true) {
            val frames = link.farewell() ?: return
            var heard = false
            open(frames, onServerClose = { heard = true }).collect { (_, text) -> link.onFrame(text) }
            if (heard) { Log.i(TAG, "remote: relay heard unpair"); link.farewellDone(); return }
            withTimeoutOrNull(backoff) { kicks.receive() }
            backoff = (backoff * 2).coerceAtMost(CHECK_MS)
        }
    }

    /** One socket: [frames] on open, then each text frame with its socket; completes when the socket closes. */
    private fun open(frames: List<String>, onOpen: () -> Unit = {}, onServerClose: () -> Unit = {}) =
        client.socket(ChildFrame.wsUrl(BuildConfig.RELAY_URL), onOpen = { ws ->
            Log.i(TAG, "remote: socket open")
            frames.forEach(ws::send)
            onOpen()
        }, onServerClose)
}
