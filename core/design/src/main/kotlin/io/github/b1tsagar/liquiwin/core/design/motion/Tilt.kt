package io.github.b1tsagar.liquiwin.core.design.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Tilt(val rotationXDeg: Float, val rotationYDeg: Float, val depression: Float)

private const val MAX_TILT_DEG = 17.1887f // 0.3 rad

/**
 * The tilt for a touch at [touch] on a tile of [size]: the edge under the finger moves away from the viewer.
 * A positive rotationY pushes the right edge back, a positive rotationX the top edge.
 */
fun tiltFor(touch: Offset, size: Size): Tilt {
    val nx = ((touch.x - size.width / 2) / (size.width / 2)).coerceIn(-1f, 1f)
    val ny = ((touch.y - size.height / 2) / (size.height / 2)).coerceIn(-1f, 1f)
    return Tilt(rotationXDeg = -ny * MAX_TILT_DEG, rotationYDeg = nx * MAX_TILT_DEG, depression = 1f - min(1f, hypot(nx, ny)))
}

/**
 * Tilts the element toward the finger. It only watches pointer events, so taps and long-presses still reach the
 * modifiers after it. After release it holds for 200 ms, then returns to rest over 100 ms.
 */
fun Modifier.tilt(enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    var tilt by remember { mutableStateOf(Tilt(0f, 0f, 0f)) }
    val amount = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        var release: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            release?.cancel()
            tilt = tiltFor(down.position, size.toSize())
            scope.launch { amount.snapTo(1f) }
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                tilt = tiltFor(change.position, size.toSize())
            }
            release = scope.launch {
                delay(200)
                amount.animateTo(0f, tween(durationMillis = 100, easing = LinearEasing))
            }
        }
    }.graphicsLayer {
        val a = amount.value
        rotationX = tilt.rotationXDeg * a
        rotationY = tilt.rotationYDeg * a
        val scale = 1f - 0.025f * tilt.depression * a
        scaleX = scale
        scaleY = scale
        cameraDistance = 12f * density
    }
}
