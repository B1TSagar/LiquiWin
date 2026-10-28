package io.github.b1tsagar.liquiwin.feature.start

import io.github.b1tsagar.liquiwin.feature.start.layout.Cell

/**
 * Each tile's turn in start's turnstile: medium columns from the left, the tiles in one column together, and the
 * [tapped] tile after all of them.
 */
internal fun startTurnstileOrder(cells: Map<Long, Cell>, tapped: Long?): Map<Long, Int> {
    val order = cells.mapValues { it.value.x / 2 }
    if (tapped == null || tapped !in order) return order
    return order + (tapped to (order.values.max() + 1))
}
