package dev.sophiel.remote

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** What the claim socket tells the pairing screen. */
sealed interface ClaimEvent {
    data object Open : ClaimEvent
    data class Frame(val frame: ChildFrame) : ClaimEvent
    data object Closed : ClaimEvent
}

/**
 * The pairing screen's socket (D78, D82): `{"t":"claim"}` on open, then relay frames. It is open exactly as long as
 * the flow is collected: cancelling the collector closes it. [newCode] asks for a fresh claim code on the same socket.
 */
class ClaimSocket(private val url: String, private val client: OkHttpClient = OkHttpClient()) {
    @Volatile private var socket: WebSocket? = null

    fun newCode() { socket?.send("""{"t":"claim"}""") }

    fun events(): Flow<ClaimEvent> = callbackFlow {
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"t":"claim"}""")
                trySend(ClaimEvent.Open)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                ChildFrame.parse(text)?.let { trySend(ClaimEvent.Frame(it)) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { trySend(ClaimEvent.Closed) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { trySend(ClaimEvent.Closed) }
        })
        socket = ws
        awaitClose { socket = null; ws.close(1000, null) }
    }
}
