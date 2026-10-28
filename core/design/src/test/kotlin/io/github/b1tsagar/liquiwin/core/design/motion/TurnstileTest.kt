@file:OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)

package io.github.b1tsagar.liquiwin.core.design.motion

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnstileTest {
    @Test fun anItemWaitsForTheItemsBeforeIt() {
        assertEquals(0f, turnstileProgress(Turnstile.IN, elapsedMs = 79f, index = 2), 0f) // item 2 starts at 80 ms
    }

    @Test fun anItemIsDoneOnceItsSwingIsOver() {
        assertEquals(1f, turnstileProgress(Turnstile.IN, elapsedMs = 80f + 350f, index = 2), 1e-6f)
    }

    // WP's ExponentialEase with exponent 6: swinging in it eases out, covering 95% of the way in half its time...
    @Test fun swingingInEasesOut() {
        assertEquals(0.9526f, turnstileProgress(Turnstile.IN, elapsedMs = 175f, index = 0), 0.0005f)
    }

    // ...and swinging out it eases in, covering under 5%. Going out, items start 50 ms apart.
    @Test fun swingingOutEasesIn() {
        assertEquals(0.0474f, turnstileProgress(Turnstile.OUT, elapsedMs = 50f + 125f, index = 1), 0.0005f)
    }

    @Test fun rewindingTheWayOutLeavesTheItemsAtRest() = runTest {
        val clock = TurnstileClock()
        withContext(TestMonotonicFrameClock(this)) {
            clock.play(Turnstile.OUT, last = 2)
            clock.rewind(Turnstile.OUT, last = 2)
        }
        assertNull(clock.motion)
    }

    @Test fun rewindingTheWayInLeavesTheItemsHiddenUntilTheNextOne() = runTest {
        val clock = TurnstileClock()
        withContext(TestMonotonicFrameClock(this)) {
            clock.play(Turnstile.IN, last = 2)
            clock.rewind(Turnstile.IN, last = 2)
        }
        assertEquals(Turnstile.IN, clock.motion)
        assertEquals(0f, clock.elapsedMs, 0f)
    }
}
