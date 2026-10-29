package io.github.b1tsagar.liquiwin.feature.start.live

import io.github.b1tsagar.liquiwin.core.tiles.source.TileItem

internal const val LABEL_STRIP_WP_PX = 40f // the strip along a tile's bottom edge that holds its label

/**
 * A stack's list as laid out, in px from the tile's top: its rows on screen, whole or in part, by their index in the
 * stack, and the list's own top and bottom, where the label strip starts.
 */
internal class StackLayout(val rows: List<StackRow>, val top: Float, val bottom: Float)

internal class StackRow(val index: Int, val top: Float, val bottom: Float)

/**
 * The item a tap at [y] opens, or null for the app (§5.5). The label strip, the bottom [labelStripPx] of a tile [heightPx]
 * tall, opens the app, and so does the front face. A stack opens the row under the finger in its [stack] layout.
 */
internal fun tileTapTarget(face: TileFace, y: Float, heightPx: Float, labelStripPx: Float, stack: StackLayout? = null): TileItem? =
    when (face) {
        TileFace.Front -> null
        is TileFace.Item -> face.item.takeIf { y < heightPx - labelStripPx }
        is TileFace.Stack -> stack?.rowAt(y)?.let { face.items.getOrNull(it.index) }
    }

/**
 * The item whose chat button a tap at ([x], [y]) is on, or null (§5.5). The button is the bottom-right [buttonPx] corner of
 * an item face, and of each of a stack's rows where the list shows it. A missed call has its own buttons there instead.
 */
internal fun tileChatTarget(
    face: TileFace, x: Float, y: Float, widthPx: Float, heightPx: Float, buttonPx: Float, stack: StackLayout? = null,
): TileItem? {
    if (x < widthPx - buttonPx) return null
    return when (face) {
        TileFace.Front -> null
        is TileFace.Item -> face.item.takeIf { y >= heightPx - buttonPx }
        is TileFace.Stack -> stack?.takeIf { y >= it.top }?.rowAt(y)?.takeIf { y >= it.bottom - buttonPx }?.let { face.items.getOrNull(it.index) }
    }?.takeIf { it.buttons.isEmpty() }
}

// The row under [y]: the top one for the margin above the list, and none for the label strip below it.
private fun StackLayout.rowAt(y: Float): StackRow? = if (y >= bottom) null else rows.firstOrNull { y < it.bottom }
