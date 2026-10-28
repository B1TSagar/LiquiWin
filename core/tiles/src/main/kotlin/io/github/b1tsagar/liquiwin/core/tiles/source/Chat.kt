package io.github.b1tsagar.liquiwin.core.tiles.source

import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId

/** A message in a chat (§5.7). [sender] is the name alone, without a group's title. */
data class ChatMessage(val item: TileItem, val sender: String, val isSelf: Boolean)

/**
 * A chat's stored messages, oldest first (§5.7). Only a chat or group can be hidden (§5.8), so [canHide] is false for a
 * plain notification, whose slot an app can fill with unrelated posts.
 */
data class Chat(
    val conversationKey: String,
    val title: String,
    val avatar: ImageRef?,
    val isGroup: Boolean,
    val messages: List<ChatMessage>,
    val canHide: Boolean,
) {
    /** The newest message whose notification takes replies right now. */
    val replyTo: TileItem? get() = messages.lastOrNull { it.item.canReply }?.item
}

/** A chat hidden from tiles (§5.8), under the name it had when hidden. */
data class HiddenChat(val sourceId: SourceId, val conversationKey: String, val name: String)
