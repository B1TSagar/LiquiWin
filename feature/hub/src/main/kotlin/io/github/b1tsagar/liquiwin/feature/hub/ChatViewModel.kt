package io.github.b1tsagar.liquiwin.feature.hub

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.Chat
import io.github.b1tsagar.liquiwin.core.tiles.source.ReplyResult
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/** An open chat window (§5.7). */
data class ChatWindowState(
    val sourceId: SourceId,
    val chat: Chat,
    val pending: List<PendingReply>, // sent, and not yet back from the app; oldest first
    val showsReplyBox: Boolean, // since the chat first took replies while open
    val sending: Boolean,
    val failed: Boolean, // "Couldn't send."
)

/** A reply sent from the window, shown until the app's notification brings it back. */
data class PendingReply(val id: Long, val text: String, val sentAt: Instant, val sending: Boolean)

/**
 * The chat window (§5.7): the shown chat as its source has it, read up to its newest message, and the replies sent
 * through the app's notification. Everything a shown chat starts runs in its own scope, which ends when the window
 * closes or shows another chat, so nothing of one chat reaches the next.
 */
class ChatViewModel(private val sources: SourceRegistry, private val now: () -> Instant = Instant::now) : ViewModel() {
    private val state = MutableStateFlow<ChatWindowState?>(null)
    val window: StateFlow<ChatWindowState?> = state.asStateFlow() // null while closed
    val draft = TextFieldState() // the text box's
    private var shown: CoroutineScope? = null
    private var nextReplyId = 0L

    /** Opens [item]'s chat. An item with no conversation, or whose source is gone, opens nothing. */
    fun show(item: TileItem) {
        val key = item.conversation?.key ?: return
        val source = sources.source(item.sourceId) ?: return
        close()
        draft.clearText()
        val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext.job))
        shown = scope
        scope.launch {
            var readUpTo: String? = null
            var yours: Set<String>? = null // the keys of your messages since opening
            source.chat(key).collect { chat ->
                if (chat == null) {
                    close()
                    return@collect
                }
                val newest = chat.messages.lastOrNull()?.item
                if (newest != null && newest.key != readUpTo) {
                    readUpTo = newest.key
                    source.markRead(listOf(newest)) // §5.3
                }
                // Each of yours that's new is a reply coming back, whatever the app made of its text.
                val mine = chat.messages.filter { it.isSelf }.mapTo(HashSet()) { it.item.key }
                val back = yours?.let { (mine - it).size } ?: 0
                yours = yours.orEmpty() + mine
                state.update { old ->
                    ChatWindowState(
                        sourceId = source.id,
                        chat = chat,
                        pending = old?.pending.orEmpty().drop(back),
                        showsReplyBox = old?.showsReplyBox == true || chat.replyTo != null,
                        sending = old?.sending == true,
                        failed = old?.failed == true,
                    )
                }
            }
        }
    }

    /** Replies with the draft to the newest message that takes replies. Nothing while sending, or for a blank draft. */
    fun send() {
        val window = state.value ?: return
        val scope = shown ?: return
        val text = draft.text.toString()
        if (window.sending || text.isBlank()) return
        val to = window.chat.replyTo
        val source = sources.source(window.sourceId)
        if (to == null || source == null) {
            state.update { it?.copy(failed = true) }
            return
        }
        state.update { it?.copy(sending = true) }
        scope.launch {
            when (source.reply(to, text)) {
                ReplyResult.Sent -> {
                    val reply = PendingReply(nextReplyId++, text, now(), sending = true)
                    state.update { it?.copy(pending = it.pending + reply, sending = false, failed = false) }
                    if (draft.text.toString() == text) draft.clearText()
                    delay(SENDING_MS)
                    state.update { it?.copy(pending = it.pending.map { p -> if (p.id == reply.id) p.copy(sending = false) else p }) }
                }
                ReplyResult.ActionGone -> state.update { it?.copy(sending = false, failed = true) }
            }
        }
    }

    /** Opens [item], or else the newest message, in its app, through §5.5's chain, which reads it, and closes. */
    fun openInApp(item: TileItem? = null) {
        val window = state.value ?: return
        val source = sources.source(window.sourceId)
        val opened = item ?: window.chat.messages.lastOrNull()?.item
        close()
        if (source != null && opened != null) viewModelScope.launch { source.open(opened) }
    }

    /**
     * Presses the newest message's [button], a missed call's (§5.7), and closes. A button gone since the row was drawn is
     * the source's to settle: it opens the message instead (§5.5).
     */
    fun press(button: Int) {
        val window = state.value ?: return
        val source = sources.source(window.sourceId)
        val newest = window.chat.messages.lastOrNull()?.item
        close()
        if (source != null && newest != null) viewModelScope.launch { source.press(newest, button) }
    }

    /** Hides the chat from tiles (§5.8). The window stays until [close], so it can swivel out. */
    fun hide() {
        val window = state.value ?: return
        val source = sources.source(window.sourceId) ?: return
        viewModelScope.launch { source.hide(window.chat) }
    }

    fun close() {
        shown?.cancel()
        shown = null
        state.value = null
    }
}

// How long a reply says "sending…" when the app never brings it back.
private const val SENDING_MS = 10_000L
