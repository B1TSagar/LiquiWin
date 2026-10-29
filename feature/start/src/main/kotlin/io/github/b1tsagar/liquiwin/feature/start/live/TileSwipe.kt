package io.github.b1tsagar.liquiwin.feature.start.live

import kotlin.math.abs

internal const val FLING_DP_PER_S = 600f // the pager's flick

/** Whether letting go of a swipe down of [drag] px at [velocity] px/s turns a tile [heightPx] tall (§5.5). */
internal fun turnsDown(drag: Float, velocity: Float, heightPx: Float, flingPx: Float): Boolean =
    if (abs(velocity) >= flingPx) velocity > 0 else drag >= heightPx / 3 // a flick decides, as on the pager

/** What a sideways swipe on a message does as it lets go (§5.5): left opens its chat, right clears it. */
enum class Sideways { CHAT, CLEAR }

/** What letting go of a sideways swipe does (§5.5). Null slides the message back. */
internal fun sideways(drag: Float, velocity: Float, widthPx: Float, flingPx: Float, canClear: Boolean): Sideways? {
    val way = if (abs(velocity) >= flingPx) velocity else drag.takeIf { abs(it) >= widthPx / 3 } ?: return null
    return if (way < 0) Sideways.CHAT else Sideways.CLEAR.takeIf { canClear }
}

/** The message under a sideways swipe, moved [offsetPx] from its place (negative is left). */
data class MessageSwipe(val key: String, val offsetPx: Float)
