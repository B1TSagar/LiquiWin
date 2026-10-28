package io.github.b1tsagar.liquiwin.core.tiles.source

import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId

/** A message in a chat (§5.7). [sender] is the name alone, without a group's title. */
data class ChatMessage(val item: TileItem, val sender: String, val isSelf: Boolean)

/** A chat's stored messages, oldest first (§5.7). */
data class Chat(
    val conversationKey: String,
    val title: String,
    val avatar: ImageRef?,
    val isGroup: Boolean,
    val messages: List<ChatMessage>,
) {
    /** The newest message whose notification takes replies right now. */
    val replyTo: TileItem? get() = messages.lastOrNull { it.item.canReply }?.item
}

/** A chat hidden from tiles (§5.8), under the name it had when hidden. */
data class HiddenChat(val sourceId: SourceId, val conversationKey: String, val name: String)
