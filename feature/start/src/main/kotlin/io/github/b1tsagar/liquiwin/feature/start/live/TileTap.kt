package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem

internal const val LABEL_STRIP_WP_PX = 40f // the strip along a tile's bottom edge that holds its label

/**
 * The item a tap at [y] opens, or null for the app (§5.5). The label strip, the bottom [labelStripPx] of a tile [heightPx]
 * tall, opens the app, and so does the front face. A stack gives each of its items a row of the rest.
 */
internal fun tileTapTarget(face: TileFace, y: Float, heightPx: Float, labelStripPx: Float): TileItem? {
    val body = heightPx - labelStripPx
    if (y >= body) return null
    return when (face) {
        TileFace.Front -> null
        is TileFace.Item -> face.item
        is TileFace.Stack -> face.items.getOrNull((y / (body / STACK_SIZE)).toInt())
    }
}
