package io.github.b1tsagar.liquiwin.core.tiles.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class TileState(val rotation: List<TileItem>, val unread: Int, val hiddenChats: List<HiddenChat> = emptyList())

/** Unread items of all [sources], newest first, at most 5 (§5.3), their summed counts, and every source's hidden chats. */
fun tileState(sources: List<TileSource>): Flow<TileState> {
    if (sources.isEmpty()) return flowOf(TileState(emptyList(), 0))
    val perSource = sources.map { source ->
        combine(source.items, source.unreadCount, source.hiddenChats) { items, unread, hidden -> Triple(items, unread, hidden) }
    }
    return combine(perSource) { parts ->
        val rotation = parts.flatMap { it.first }.filter { it.unread }
            .sortedWith(compareByDescending<TileItem> { it.time }.thenBy { it.key })
            .take(ROTATION_MAX)
        TileState(rotation, parts.sumOf { it.second }, parts.flatMap { it.third })
    }
}

private const val ROTATION_MAX = 5
