package io.github.b1tsagar.liquiwin.core.tiles.source

import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** An image a source saved for one of its items. */
@JvmInline
value class ImageRef(val file: File)

/** The conversation an item belongs to, with the app's shortcut to it when there is one. */
data class ConversationRef(val key: String, val shortcutId: String?)

/** One thing a live tile can show, such as a message (§3.2). */
data class TileItem(
    val key: String,
    val sourceId: SourceId,
    val title: String, // the sender, or "sender · group"
    val text: String?,
    val avatar: ImageRef?,
    val image: ImageRef?,
    val time: Instant,
    val unread: Boolean,
    val canReply: Boolean,
    val conversation: ConversationRef?,
)

/** Anything that feeds a tile, such as all of one app's notifications (§3.2). */
interface TileSource {
    val id: SourceId
    val items: Flow<List<TileItem>> // newest first
    val unreadCount: Flow<Int>
    suspend fun open(item: TileItem?) // null opens the app
    suspend fun markRead(items: List<TileItem>) // local read state only
}
