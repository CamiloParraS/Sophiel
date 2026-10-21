package dev.sophiel.remote.parent

import dev.sophiel.remote.Envelope
import dev.sophiel.remote.Protocol
import dev.sophiel.remote.Reason
import dev.sophiel.remote.Request
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The banner under a card after Start or Stop (D81, D82's wording). */
enum class Outcome { STARTED, ALREADY_ON, STOPPED, ACCESSIBILITY_OFF, SWITCH_OFF, FAILED }

/** One Child phone's card on the Parent home (D81). Memory only: nothing about a Child phone's state is ever stored. */
sealed interface Card {
    data object Loading : Card // skeleton
    data object Offline : Card // "Sin conexión", Reintentar: no last-known state
    data object Gone : Card // "Se desvinculó", Quitar de la lista

    /** [at]: when this phone received the reply ("hace N s"). [busy]: an action is in flight, its pill spins. */
    data class Live(
        val protecting: Boolean,
        val accessibilityOff: Boolean,
        val at: Long,
        val busy: Boolean = false,
        val banner: Outcome? = null,
    ) : Card {
        val canStart get() = !protecting && !accessibilityOff && !busy
    }
}

/** The home's cards, one per paired Child phone: a `log` request draws each, Start and Stop act on it. */
class HomeCards(private val api: Api, private val session: ParentSession, private val clock: () -> Long = System::currentTimeMillis) {
    private val _cards = MutableStateFlow(session.children.list.value.associate { it.id to (Card.Loading as Card) })
    val cards: StateFlow<Map<String, Card>> = _cards

    /** Every card back to its skeleton, all asked at once. */
    suspend fun open() = coroutineScope {
        val ids = session.children.list.value.map { it.id }
        _cards.value = ids.associateWith { Card.Loading }
        ids.forEach { launch { ask(it, "log") } }
    }

    suspend fun refresh(id: String) { put(id, Card.Loading); ask(id, "log") }
    suspend fun start(id: String) = act(id, "start")
    suspend fun stop(id: String) = act(id, "stop")

    /** Quitar de la lista: the entry, its key and its card go. */
    fun remove(id: String) {
        session.children.remove(id)
        _cards.update { it - id }
    }

    /**
     * Desvincular (D79): the relay deletes the pairing and tells the Child phone. Offline or already gone, the entry still
     * goes (the Child phone's next hello is rejected); only a dead session keeps it, for after login.
     */
    suspend fun unpair(id: String) {
        val token = session.token.value ?: return
        val r = api.unpair(token, id)
        if (r is Reply.Fail && r.why == Failure.SESSION) return session.expireOn(r.why)
        remove(id)
    }

    private suspend fun act(id: String, type: String) {
        val live = _cards.value[id] as? Card.Live ?: return
        if (live.busy) return
        put(id, live.copy(busy = true, banner = null))
        ask(id, type)
    }

    private suspend fun ask(id: String, type: String) {
        val child = session.children.list.value.firstOrNull { it.id == id } ?: return
        val before = _cards.value[id] as? Card.Live
        fun failed(): Card = before?.copy(busy = false, banner = Outcome.FAILED) ?: Card.Offline
        val token = session.token.value ?: return
        val req = Request(UUID.randomUUID().toString(), clock(), type)
        val body = Protocol.toWire(Envelope.seal(child.key, child.id, Protocol.encode(req)))
        val card = when (val r = api.request(token, child.id, body)) {
            is Reply.Body -> {
                val reply = Protocol.fromWire(r.body)?.let { Envelope.open(child.key, child.id, it) }?.let(Protocol::decodeReply)
                if (reply == null || reply.id != req.id) failed()
                else Card.Live(reply.protecting, reply.accessibilityOff, clock(), banner = outcome(type, reply.ok, reply.reason))
            }
            is Reply.Fail -> when (r.why) {
                Failure.OFFLINE -> Card.Offline
                Failure.GONE -> Card.Gone
                else -> failed().also { session.expireOn(r.why) }
            }
            else -> failed()
        }
        put(id, card)
    }

    private fun outcome(type: String, ok: Boolean, reason: String?): Outcome? = when {
        type == "log" -> null
        reason == Reason.ALREADY_ON -> Outcome.ALREADY_ON
        reason == Reason.ACCESSIBILITY_OFF -> Outcome.ACCESSIBILITY_OFF
        reason == Reason.SWITCH_OFF -> Outcome.SWITCH_OFF
        !ok -> Outcome.FAILED
        type == "start" -> Outcome.STARTED
        else -> Outcome.STOPPED
    }

    /** A reply for a card removed meanwhile is dropped. */
    private fun put(id: String, c: Card) = _cards.update { if (id in it || c == Card.Loading) it + (id to c) else it }
}
