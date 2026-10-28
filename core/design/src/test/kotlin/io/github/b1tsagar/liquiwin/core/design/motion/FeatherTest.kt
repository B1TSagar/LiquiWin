package io.github.b1tsagar.liquiwin.core.design.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatherTest {
    @Test fun anItemWaitsForTheItemsBeforeIt() {
        assertEquals(0f, featherInProgress(elapsedMs = 79f, index = 2), 0f) // item 2 starts at 80 ms
    }

    @Test fun anItemIsAtRestOnceItsSwingIsOver() {
        assertEquals(1f, featherInProgress(elapsedMs = 80f + 350f, index = 2), 1e-6f)
    }

    // WP's ExponentialEase with exponent 6, easing out: halfway through its time an item has covered 95% of its swing.
    @Test fun theSwingEasesOutExponentially() {
        assertEquals(0.9526f, featherInProgress(elapsedMs = 175f, index = 0), 0.0005f)
    }
}
