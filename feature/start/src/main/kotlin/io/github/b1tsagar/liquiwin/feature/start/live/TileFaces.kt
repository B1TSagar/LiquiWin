package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.tiles.model.TileSize
import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem

/** What a live tile shows (§5.1): its front, one item, or a large tile's stack, the list of all its items. */
sealed interface TileFace {
    val key: String

    data object Front : TileFace { override val key = "front" }

    data class Item(val item: TileItem) : TileFace { override val key get() = item.key }

    data class Stack(val items: List<TileItem>) : TileFace { override val key get() = "stack" }
}

internal const val STACK_SIZE = 3 // the rows of a stack in view at once
private const val ROTATION_MAX = 5

/** Small: the front. Medium and wide: the front, then one face for each of the 5 newest items. Large: the front and a stack. */
internal fun faces(size: TileSize, rotation: List<TileItem>): List<TileFace> = when {
    rotation.isEmpty() || size == TileSize.SMALL -> listOf(TileFace.Front)
    size == TileSize.LARGE -> listOf(TileFace.Front, TileFace.Stack(rotation))
    else -> listOf(TileFace.Front) + rotation.take(ROTATION_MAX).map { TileFace.Item(it) }
}

/** The face after [current]. A face that's gone gives the front. */
internal fun nextFace(faces: List<TileFace>, current: String): TileFace {
    val i = faces.indexOfFirst { it.key == current }
    return if (i < 0) TileFace.Front else faces[(i + 1) % faces.size]
}
