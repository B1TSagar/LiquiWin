package io.github.b1tsagar.liquiwin.feature.start.live

import kotlin.math.abs

internal const val FLING_DP_PER_S = 600f // the pager's flick

/** Whether letting go of a swipe down of [drag] px at [velocity] px/s turns a tile [heightPx] tall (§5.5). */
internal fun turnsDown(drag: Float, velocity: Float, heightPx: Float, flingPx: Float): Boolean =
    if (abs(velocity) >= flingPx) velocity > 0 else drag >= heightPx / 3 // a flick decides, as on the pager
