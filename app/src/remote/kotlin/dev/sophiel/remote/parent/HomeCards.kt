package dev.sophiel.remote.parent

import android.util.Log
import dev.sophiel.remote.Envelope
import dev.sophiel.remote.Protocol
import dev.sophiel.remote.Reason
import dev.sophiel.remote.Request
import dev.sophiel.remote.RequestType
import dev.sophiel.remote.Rule
import java.util.UUID
import dev.sophiel.remote.Reply as ChildReply
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Answer {
    data class Got(val reply: ChildReply) : Answer
    data class No(val why: Failure) : Answer
}

/** One request to Child phone [id] through the relay, sealed and opened with its key: the reply, or why not. A dead session logs out. */
suspend fun ParentSession.ask(api: Api, id: String, type: RequestType, rules: Map<String, Rule> = emptyMap(), clock: () -> Long): Answer {
    val child = children.list.value.firstOrNull { it.id == id } ?: return Answer.No(Failure.GONE)
    val token = token.value ?: return Answer.No(Failure.SESSION)
    val req = Request(UUID.randomUUID().toString(), clock(), type, rules)
    val body = Protocol.toWire(Envelope.seal(child.key, child.id, Protocol.encode(req)))
    val t0 = System.nanoTime()
    return when (val r = api.request(token, child.id, body)) {
        is Reply.Body -> Protocol.fromWire(r.body)?.also { Log.d("Sophiel", "remote: $type reply ${it.size} B, ${(System.nanoTime() - t0) / 1_000_000} ms") }?.let { Envelope.open(child.key, child.id, it) }?.let(Protocol::decodeReply)
            ?.takeIf { it.id == req.id }?.let(Answer::Got) ?: Answer.No(Failure.OTHER)
        is Reply.Fail -> Answer.No(r.why).also { expireOn(r.why) }
        else -> Answer.No(Failure.OTHER)
    }
}

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
        ids.forEach { launch { ask(it, RequestType.LOG) } }
    }

    suspend fun refresh(id: String) { put(id, Card.Loading); ask(id, RequestType.LOG) }
    suspend fun start(id: String) = act(id, RequestType.START)
    suspend fun stop(id: String) = act(id, RequestType.STOP)

    /** Quitar de la lista: the entry, its key and its card go. */
    fun remove(id: String) {
        session.children.remove(id)
        _cards.update { it - id }
    }

    /**
     * Desvincular (D79): the relay deletes the pairing and tells the Child phone. Only success or 410 (already gone) drops the
     * entry; offline or a server error keeps it and returns false, a dead session keeps it for after login.
     */
    suspend fun unpair(id: String): Boolean {
        val token = session.token.value ?: return false
        val r = api.unpair(token, id)
        if (r is Reply.Fail && r.why != Failure.GONE) { session.expireOn(r.why); return false }
        remove(id)
        return true
    }

    private suspend fun act(id: String, type: RequestType) {
        val live = _cards.value[id] as? Card.Live ?: return
        if (live.busy) return
        put(id, live.copy(busy = true, banner = null))
        ask(id, type)
    }

    private suspend fun ask(id: String, type: RequestType) {
        val before = _cards.value[id] as? Card.Live
        val card = when (val a = session.ask(api, id, type, clock = clock)) {
            is Answer.Got -> Card.Live(a.reply.protecting, a.reply.accessibilityOff, clock(), banner = outcome(type, a.reply.ok, a.reply.reason))
            is Answer.No -> when (a.why) {
                Failure.OFFLINE -> Card.Offline
                Failure.GONE -> Card.Gone
                else -> before?.copy(busy = false, banner = Outcome.FAILED) ?: Card.Offline
            }
        }
        put(id, card)
    }

    private fun outcome(type: RequestType, ok: Boolean, reason: String?): Outcome? = when {
        type == RequestType.LOG -> null
        reason == Reason.ALREADY_ON -> Outcome.ALREADY_ON
        reason == Reason.ACCESSIBILITY_OFF -> Outcome.ACCESSIBILITY_OFF
        reason == Reason.SWITCH_OFF -> Outcome.SWITCH_OFF
        !ok -> Outcome.FAILED
        type == RequestType.START -> Outcome.STARTED
        else -> Outcome.STOPPED
    }

    /** A reply for a card removed meanwhile is dropped. */
    private fun put(id: String, c: Card) = _cards.update { if (id in it || c == Card.Loading) it + (id to c) else it }
}
