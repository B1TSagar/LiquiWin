package io.github.b1tsagar.liquiwin.core.tiles.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class TileState(val rotation: List<TileItem>, val unread: Int)

/** Unread items of all [sources], newest first, at most 5 (§5.3), and their summed counts. */
fun tileState(sources: List<TileSource>): Flow<TileState> {
    if (sources.isEmpty()) return flowOf(TileState(emptyList(), 0))
    val perSource = sources.map { source -> source.items.combine(source.unreadCount) { items, unread -> items to unread } }
    return combine(perSource) { parts ->
        val rotation = parts.flatMap { it.first }.filter { it.unread }
            .sortedWith(compareByDescending<TileItem> { it.time }.thenBy { it.key })
            .take(ROTATION_MAX)
        TileState(rotation, parts.sumOf { it.second })
    }
}

private const val ROTATION_MAX = 5
