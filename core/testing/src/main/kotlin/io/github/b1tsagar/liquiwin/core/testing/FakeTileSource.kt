package io.github.b1tsagar.liquiwin.core.testing

import io.github.b1tsagar.liquiwin.core.tiles.model.SourceId
import io.github.b1tsagar.liquiwin.core.tiles.source.ConversationRef
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem
import io.github.b1tsagar.liquiwin.core.tiles.source.TileSource
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A [TileSource] whose items tests set directly, and which records what was opened and read. */
class FakeTileSource(override val id: SourceId) : TileSource {
    val itemState = MutableStateFlow<List<TileItem>>(emptyList())
    val opened = mutableListOf<TileItem?>()
    val read = mutableListOf<TileItem>()

    override val items: Flow<List<TileItem>> = itemState
    override val unreadCount: Flow<Int> = itemState.map { items -> items.count { it.unread } }

    override suspend fun open(item: TileItem?) {
        opened += item
    }

    override suspend fun markRead(items: List<TileItem>) {
        read += items
    }
}

/** A message from [title], with no images, no reply, and the conversation named after [title]. */
fun testItem(
    key: String,
    title: String = "Alex",
    text: String? = "see you at 6",
    at: Long = 0,
    unread: Boolean = true,
    source: SourceId = SourceId("app:com.example.chat@0"),
): TileItem = TileItem(
    key = key,
    sourceId = source,
    title = title,
    text = text,
    avatar = null,
    image = null,
    time = Instant.ofEpochMilli(at),
    unread = unread,
    canReply = false,
    conversation = ConversationRef(title.lowercase(), null),
)
