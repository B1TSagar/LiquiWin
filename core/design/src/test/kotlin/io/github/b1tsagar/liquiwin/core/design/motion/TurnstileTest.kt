package io.github.b1tsagar.liquiwin.core.design.motion

import org.junit.Assert.assertEquals
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
}
