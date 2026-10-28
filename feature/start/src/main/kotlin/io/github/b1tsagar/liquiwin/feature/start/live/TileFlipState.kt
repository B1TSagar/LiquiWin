package io.github.b1tsagar.liquiwin.feature.start.live

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import io.github.b1tsagar.liquiwin.core.design.motion.tileFlipAngle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One tile's face: the one showing and, during a flip, the one it's turning from. */
@Stable
internal class TileFlipState(face: String) {
    /** The face showing, or being turned to. */
    var face by mutableStateOf(face)
        private set

    /** The face being turned away from, while a flip runs. */
    var from by mutableStateOf<String?>(null)
        private set

    /** [tileFlipAngle] of the flip's elapsed time; 180 when still. */
    var angle by mutableFloatStateOf(180f)
        private set

    private val turning = Mutex()
    private var flips = 0 // counts flips and snaps, so a flip knows when a snap has overtaken it

    /** Turns to [face] once any flip already running has ended. A cancelled flip ends at once on its new face. */
    suspend fun flipTo(face: String) = turning.withLock {
        if (face == this.face) return@withLock
        val flip = ++flips
        from = this.face
        this.face = face
        angle = 0f
        try {
            val start = withFrameMillis { it }
            while (angle < 180f) {
                val elapsed = withFrameMillis { it - start }
                if (flips != flip) break
                angle = tileFlipAngle(elapsed.toFloat())
            }
        } finally {
            if (flips == flip) {
                from = null
                angle = 180f
            }
        }
    }

    /** Shows [face] straight away, ending any flip. */
    fun snapTo(face: String) {
        flips++
        this.face = face
        from = null
        angle = 180f
    }
}
