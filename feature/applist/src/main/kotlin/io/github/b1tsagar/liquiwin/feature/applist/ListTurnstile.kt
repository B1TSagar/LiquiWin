package io.github.b1tsagar.liquiwin.feature.applist

import io.github.b1tsagar.liquiwin.core.design.motion.Turnstile

/**
 * A list item's turn in the list's turnstile: top to bottom from the [first] item on screen, except that going out the
 * [tapped] row goes after all [onScreen] items.
 */
internal fun listTurnstileIndex(motion: Turnstile, item: Int, first: Int, tapped: Int?, onScreen: Int): Int =
    if (motion == Turnstile.OUT && item == tapped) onScreen else (item - first).coerceAtLeast(0)
