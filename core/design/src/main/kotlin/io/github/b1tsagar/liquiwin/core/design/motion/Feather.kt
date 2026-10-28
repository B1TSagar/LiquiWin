package io.github.b1tsagar.liquiwin.core.design.motion

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.exp

/** WP's turnstile feather, forward in (reference §5): item n starts 40 ms after item n − 1 and swings in over 350 ms. */
const val FEATHER_STAGGER_MS = 40
const val FEATHER_DURATION_MS = 350

/** How far item [index] has swung in, [elapsedMs] into the feather: 0 before its turn, 1 at rest. */
fun featherInProgress(elapsedMs: Float, index: Int): Float {
    val t = ((elapsedMs - index * FEATHER_STAGGER_MS) / FEATHER_DURATION_MS).coerceIn(0f, 1f)
    return (1 - exp(-6 * t)) / (1 - exp(-6f)) // WP's ExponentialEase, exponent 6, easing out
}

/**
 * Feathers the element in as item [index]: it swings in around a hinge 20% of its width to its left, from 80° with its
 * right edge pushed back, while sliding in from [slideFromPx] to the right. [elapsedMs] is read when drawing.
 */
fun Modifier.featherIn(elapsedMs: () -> Float, index: Int, slideFromPx: Float = 0f): Modifier = graphicsLayer {
    val elapsed = elapsedMs()
    val p = featherInProgress(elapsed, index)
    rotationY = 80f * (1 - p)
    translationX = slideFromPx * (1 - p)
    alpha = ((elapsed - index * FEATHER_STAGGER_MS) / 10f).coerceIn(0f, 1f) // WP fades each item in over 10 ms
    transformOrigin = TransformOrigin(-0.2f, 0.5f)
}
