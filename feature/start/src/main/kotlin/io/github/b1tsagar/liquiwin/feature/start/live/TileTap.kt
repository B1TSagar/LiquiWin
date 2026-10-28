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

/**
 * The item whose chat button a tap at ([x], [y]) is on, or null (§5.5). The button is the bottom-right [buttonPx] corner of
 * an item face, and of each of a stack's rows above the label strip.
 */
internal fun tileChatTarget(
    face: TileFace, x: Float, y: Float, widthPx: Float, heightPx: Float, labelStripPx: Float, buttonPx: Float,
): TileItem? {
    if (x < widthPx - buttonPx) return null
    return when (face) {
        TileFace.Front -> null
        is TileFace.Item -> face.item.takeIf { y >= heightPx - buttonPx }
        is TileFace.Stack -> {
            val body = heightPx - labelStripPx
            if (y >= body) return null
            val row = body / STACK_SIZE
            val i = (y / row).toInt()
            face.items.getOrNull(i)?.takeIf { y >= (i + 1) * row - buttonPx }
        }
    }
}
