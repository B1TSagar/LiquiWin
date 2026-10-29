package io.github.b1tsagar.liquiwin.core.tiles.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class TileState(
    val rotation: List<TileItem>,
    val unread: Int,
    val hiddenChats: List<HiddenChat> = emptyList(),
    val history: List<TileItem> = emptyList(), // every source's read items, sorted as the rotation is (§5.3)
)

/** Unread items of all [sources], newest first (§5.3), their summed counts, every source's hidden chats, and the read rest. */
fun tileState(sources: List<TileSource>): Flow<TileState> {
    if (sources.isEmpty()) return flowOf(TileState(emptyList(), 0))
    val perSource = sources.map { source ->
        combine(source.items, source.unreadCount, source.hiddenChats) { items, unread, hidden -> Triple(items, unread, hidden) }
    }
    return combine(perSource) { parts ->
        val (rotation, history) = parts.flatMap { it.first }
            .sortedWith(compareByDescending<TileItem> { it.time }.thenBy { it.key })
            .partition { it.unread }
        TileState(rotation, parts.sumOf { it.second }, parts.flatMap { it.third }, history)
    }
}
