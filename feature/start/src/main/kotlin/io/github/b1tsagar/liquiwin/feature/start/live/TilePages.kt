package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.TileState

/** A tile's pages (§5.3): its new messages, or its history while nothing is new. */
enum class TilePage { NEW, HISTORY }

/** The page a swipe down from the front turns to (§5.3), or null for a small tile or one with nothing for either. */
internal fun pageFor(size: TileSize, state: TileState?): TilePage? = when {
    size == TileSize.SMALL || state == null -> null
    state.rotation.isNotEmpty() -> TilePage.NEW
    state.history.isNotEmpty() -> TilePage.HISTORY
    else -> null
}

/** [page] once the tile's items change: a new item turns history to new messages, and a page with nothing left gives way. */
internal fun settle(page: TilePage?, size: TileSize, state: TileState?): TilePage? = when (page) {
    null -> null
    TilePage.NEW -> page.takeIf { pageFor(size, state) == TilePage.NEW }
    TilePage.HISTORY -> pageFor(size, state)
}

/** The faces on [page], without the front; on the front, part 1's faces. A page with nothing on it shows the front. */
internal fun pageFaces(size: TileSize, state: TileState?, page: TilePage?): List<TileFace> = when (page) {
    null -> faces(size, state?.rotation.orEmpty())
    TilePage.NEW -> faces(size, state?.rotation.orEmpty()).drop(1)
    // Its own key, so a flip from it still draws it in its first half.
    TilePage.HISTORY -> faces(size, state?.history.orEmpty()).drop(1).map { (it as? TileFace.Stack)?.copy(key = "history") ?: it }
}.ifEmpty { listOf(TileFace.Front) }
