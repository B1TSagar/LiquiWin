package io.github.b1tsagar.liquiwin.feature.applist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.b1tsagar.liquiwin.core.design.theme.LocalWpUnits
import kotlin.math.abs
import kotlin.math.roundToInt

private val STRIP_WIDTH = 32.dp

/** The letter at height [y] of a strip [height] tall, with [labels] shared evenly from top to bottom. */
internal fun letterAt(y: Float, height: Float, labels: List<String>): String? =
    if (labels.isEmpty() || height <= 0f) null else labels[(y / height * labels.size).toInt().coerceIn(0, labels.lastIndex)]

private class Scrub(val label: String, val y: Float, val height: Int)

/**
 * A strip along the right edge of [content] that scrubs through [labels]. A drag that starts within 32 dp of the edge
 * and moves mostly up or down calls [onLetter] for each letter it reaches, with a haptic tick, and shows that letter in
 * a square beside the finger. Taps, sideways drags and touches elsewhere reach [content] as usual.
 */
@Composable
internal fun LetterScrubber(
    labels: List<String>,
    enabled: Boolean,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var scrub by remember { mutableStateOf<Scrub?>(null) }
    val haptics = LocalHapticFeedback.current
    val currentLabels by rememberUpdatedState(labels)
    val currentOnLetter by rememberUpdatedState(onLetter)
    Box(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val strip = STRIP_WIDTH.toPx()
            awaitEachGesture {
                // The Initial pass sees each touch before the list and its rows, so a claimed drag never scrolls them.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.x < size.width - strip) return@awaitEachGesture
                val claimed = awaitVerticalSlop(down) ?: return@awaitEachGesture
                fun follow(change: PointerInputChange) {
                    change.consume()
                    val label = letterAt(change.position.y, size.height.toFloat(), currentLabels) ?: return
                    if (label != scrub?.label) {
                        currentOnLetter(label)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    scrub = Scrub(label, change.position.y, size.height)
                }
                try {
                    follow(claimed)
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        follow(change)
                    }
                } finally {
                    scrub = null
                }
            }
        },
    ) {
        content()
        scrub?.let { ScrubLetter(it, Modifier.align(Alignment.TopEnd)) }
    }
}

// Follows the touch past the slop: the change if it went mostly up or down, null if it lifted or went sideways.
private suspend fun AwaitPointerEventScope.awaitVerticalSlop(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return null
        if (!change.pressed) return null
        val moved = change.position - down.position
        if (moved.getDistance() < viewConfiguration.touchSlop) continue
        return if (abs(moved.y) > abs(moved.x)) change else null
    }
}

// The scrubbed letter in the letter grid's square, just left of the strip and centred on the finger.
@Composable
private fun ScrubLetter(scrub: Scrub, modifier: Modifier) {
    val square = LocalWpUnits.current.dp(96f)
    LetterSquare(
        scrub.label,
        filled = true,
        modifier = modifier.offset {
            val side = square.roundToPx()
            val top = (scrub.y - side / 2f).coerceAtMost((scrub.height - side).toFloat()).coerceAtLeast(0f)
            IntOffset(-(STRIP_WIDTH + 8.dp).roundToPx(), top.roundToInt())
        },
    )
}
