package dev.sophiel.remote

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * One socket as a flow: [onOpen] when it opens, then each text frame with its socket; completes when the socket closes,
 * and cancelling the collector closes it. [onServerClose] runs when the relay starts the close.
 */
internal fun OkHttpClient.socket(url: String, onOpen: (WebSocket) -> Unit, onServerClose: () -> Unit = {}): Flow<Pair<WebSocket, String>> =
    callbackFlow {
        val ws = newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onOpen(webSocket)
            override fun onMessage(webSocket: WebSocket, text: String) { trySend(webSocket to text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { onServerClose(); webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { channel.close() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { channel.close() }
        })
        awaitClose { ws.cancel() }
    }

/** What the claim socket tells the pairing screen. */
sealed interface ClaimEvent {
    data object Open : ClaimEvent
    data class Frame(val frame: ChildFrame) : ClaimEvent
    data object Closed : ClaimEvent
}

/**
 * The pairing screen's socket (D78, D82): [claim] on open, then relay frames. It is open exactly as long as the flow is
 * collected. [newCode] asks for a fresh claim code on the same socket ([claim] carries the device token while paired, D79).
 */
class ClaimSocket(private val url: String, private val claim: () -> String, private val client: OkHttpClient = OkHttpClient()) {
    @Volatile private var socket: WebSocket? = null

    fun newCode() { socket?.send(claim()) }

    fun events(): Flow<ClaimEvent> = channelFlow {
        client.socket(url, onOpen = { socket = it; it.send(claim()); trySend(ClaimEvent.Open) })
            .collect { (_, text) -> ChildFrame.parse(text)?.let { send(ClaimEvent.Frame(it)) } }
        send(ClaimEvent.Closed)
    }.onCompletion { socket = null }
}
