package io.github.b1tsagar.liquiwin.core.design.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.exp

/**
 * WP's turnstile motions (reference §5). Each item turns around a hinge to its left, starting [staggerMs] after the item
 * before it. A positive angle pushes the right edge back; a negative one lifts it toward the viewer.
 */
enum class Turnstile(val fromDeg: Float, val toDeg: Float, val durationMs: Int, val staggerMs: Int, val easesIn: Boolean) {
    /** Forward in: from 80°, right edge pushed back, to flat. */
    IN(fromDeg = 80f, toDeg = 0f, durationMs = 350, staggerMs = 40, easesIn = false),

    /** Forward out: from flat until the right edge has lifted 50° toward the viewer, fading at the end. */
    OUT(fromDeg = 0f, toDeg = -50f, durationMs = 250, staggerMs = 50, easesIn = true),

    /** Back in: from 50° lifted back to flat. */
    BACK_IN(fromDeg = -50f, toDeg = 0f, durationMs = 350, staggerMs = 50, easesIn = false),
    ;

    /** From the first item's start until item [last] is done. */
    fun totalMs(last: Int): Int = last.coerceAtLeast(0) * staggerMs + durationMs
}

/** How far item [index] has turned through [motion], [elapsedMs] in: 0 before its turn, 1 once done. */
fun turnstileProgress(motion: Turnstile, elapsedMs: Float, index: Int): Float {
    val t = ((elapsedMs - index * motion.staggerMs) / motion.durationMs).coerceIn(0f, 1f)
    // WP's ExponentialEase, exponent 6
    return if (motion.easesIn) (exp(6 * t) - 1) / (exp(6f) - 1) else (1 - exp(-6 * t)) / (1 - exp(-6f))
}

/**
 * One clock for the turnstile motions of a screen's items. The items rest while [motion] is null, wait hidden for an
 * in-motion that hasn't played yet, and stay gone after [Turnstile.OUT].
 */
@Stable
class TurnstileClock(hiddenFor: Turnstile? = null) {
    var motion: Turnstile? by mutableStateOf(hiddenFor)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    private var done by mutableStateOf(false)
    private val clock = Animatable(0f)

    /** Milliseconds into [motion], infinite once it's done. Read it when drawing. */
    val elapsedMs: Float get() = if (done) Float.POSITIVE_INFINITY else clock.value

    /** Plays [motion] until item [last] is done. In-motions leave the items at rest; [Turnstile.OUT] leaves them gone. */
    suspend fun play(motion: Turnstile, last: Int) {
        clock.snapTo(0f)
        this.motion = motion
        done = false
        isPlaying = true
        try {
            val end = motion.totalMs(last)
            clock.animateTo(end.toFloat(), tween(end, easing = LinearEasing))
            done = true
            if (motion != Turnstile.OUT) this.motion = null
        } finally {
            isPlaying = false
        }
    }

    /** Hides the items until [motion], an in-motion, plays. */
    suspend fun hideFor(motion: Turnstile) {
        clock.snapTo(0f)
        this.motion = motion
        done = false
    }
}

/**
 * Draws the element as item [index] of [clock]'s motion, turned around a hinge at [pivotX] of its width (WP's −0.2).
 * In an in-motion it's hidden before its turn; going out it fades over its last 50 ms. During [Turnstile.IN] it also
 * slides in from [slideFromPx] to the right. The clock is read when drawing.
 */
fun Modifier.turnstile(clock: TurnstileClock, index: Int, slideFromPx: Float = 0f, pivotX: Float = -0.2f): Modifier = graphicsLayer {
    val motion = clock.motion ?: return@graphicsLayer
    val elapsed = clock.elapsedMs
    val p = turnstileProgress(motion, elapsed, index)
    rotationY = motion.fromDeg + (motion.toDeg - motion.fromDeg) * p
    if (motion == Turnstile.IN) translationX = slideFromPx * (1 - p)
    val sinceStart = elapsed - index * motion.staggerMs
    alpha = if (motion == Turnstile.OUT) ((motion.durationMs - sinceStart) / 50f).coerceIn(0f, 1f) else (sinceStart / 10f).coerceIn(0f, 1f)
    transformOrigin = TransformOrigin(pivotX, 0.5f)
}
