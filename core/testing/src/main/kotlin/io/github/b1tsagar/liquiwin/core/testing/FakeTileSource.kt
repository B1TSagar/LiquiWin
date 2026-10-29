package io.github.b1tsagar.liquiwin.core.testing

import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.Chat
import io.github.b1tsagar.liquiwin.core.tiles.source.ChatMessage
import io.github.b1tsagar.liquiwin.core.tiles.source.ConversationRef
import io.github.b1tsagar.liquiwin.core.tiles.source.HiddenChat
import io.github.b1tsagar.liquiwin.core.tiles.source.ItemButton
import io.github.b1tsagar.liquiwin.core.tiles.source.ReplyResult
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.core.tiles.source.TileSource
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A [TileSource] whose items and chat tests set directly, and which records what was opened, read and replied. */
class FakeTileSource(override val id: SourceId) : TileSource {
    val itemState = MutableStateFlow<List<TileItem>>(emptyList())
    val opened = mutableListOf<TileItem?>()
    val read = mutableListOf<TileItem>()
    val chatState = MutableStateFlow<Chat?>(null) // chat(key) gives it while its key matches
    val hiddenState = MutableStateFlow<List<HiddenChat>>(emptyList())
    val replies = mutableListOf<Pair<TileItem, String>>()
    val cleared = mutableListOf<TileItem>()
    val pressed = mutableListOf<Pair<TileItem, Int>>()
    var replyResult: ReplyResult = ReplyResult.Sent
    var replyGate: CompletableDeferred<Unit>? = null // when set, reply() records, then waits for it

    override val items: Flow<List<TileItem>> = itemState
    override val unreadCount: Flow<Int> = itemState.map { items -> items.count { it.unread } }
    override val hiddenChats: Flow<List<HiddenChat>> = hiddenState

    override suspend fun open(item: TileItem?) {
        opened += item
    }

    override suspend fun markRead(items: List<TileItem>) {
        read += items
    }

    override fun chat(conversationKey: String): Flow<Chat?> = chatState.map { it?.takeIf { chat -> chat.conversationKey == conversationKey } }

    override suspend fun reply(item: TileItem, text: String): ReplyResult {
        replies += item to text
        replyGate?.await()
        return replyResult
    }

    // Items don't change: tests set them.
    override suspend fun hide(chat: Chat) {
        hiddenState.value = listOf(HiddenChat(id, chat.conversationKey, chat.title)) +
            hiddenState.value.filterNot { it.conversationKey == chat.conversationKey }
    }

    override suspend fun showAgain(conversationKey: String) {
        hiddenState.value = hiddenState.value.filterNot { it.conversationKey == conversationKey }
    }

    override suspend fun clear(item: TileItem) {
        cleared += item
    }

    override suspend fun press(item: TileItem, button: Int) {
        pressed += item to button
    }
}

/** A message from [title], with no images, and the conversation named after [title]. */
fun testItem(
    key: String,
    title: String = "Alex",
    text: String? = "see you at 6",
    at: Long = 0,
    unread: Boolean = true,
    source: SourceId = SourceId("app:com.example.chat@0"),
    canReply: Boolean = false,
): TileItem = TileItem(
    key = key,
    sourceId = source,
    title = title,
    text = text,
    avatar = null,
    image = null,
    time = Instant.ofEpochMilli(at),
    unread = unread,
    canReply = canReply,
    conversation = ConversationRef(title.lowercase(), null),
)

/** A missed call from Alex, with [buttons] as its notification's. */
fun testCall(
    key: String = "call",
    at: Long = 0,
    buttons: List<ItemButton> = listOf(ItemButton("Call back", false), ItemButton("Message", false)),
    source: SourceId = SourceId("app:com.example.chat@0"),
): TileItem = testItem(key, title = "Alex", text = "missed call", at = at, source = source).copy(buttons = buttons)

/** A message in the chat "alex", unread unless it's yours. */
fun testMessage(
    key: String,
    text: String? = "see you at 6",
    at: Long = 0,
    self: Boolean = false,
    sender: String = if (self) "You" else "Alex",
    canReply: Boolean = true,
    source: SourceId = SourceId("app:com.example.chat@0"),
): ChatMessage = ChatMessage(
    item = testItem(key, sender, text, at, unread = !self, source, canReply).copy(conversation = ConversationRef("alex", null)),
    sender = sender,
    isSelf = self,
)

/** The chat "alex" of [messages], with no avatar. */
fun testChat(vararg messages: ChatMessage, title: String = "Alex", isGroup: Boolean = false, canHide: Boolean = true): Chat =
    Chat("alex", title, avatar = null, isGroup, messages.toList(), canHide)
