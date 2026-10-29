package io.github.b1tsagar.liquiwin.core.design.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollThumbTest {
    @Test fun noThumbWhenEverythingFits() {
        assertNull(scrollThumb(viewportPx = 100f, contentPx = 100f, fromTopPx = 0f, minLengthPx = 10f))
    }

    @Test fun theThumbShowsWhichPartIsInView() {
        assertEquals(ScrollThumb(topPx = 0f, lengthPx = 50f), scrollThumb(100f, 200f, fromTopPx = 0f, minLengthPx = 10f))
        assertEquals(ScrollThumb(topPx = 50f, lengthPx = 50f), scrollThumb(100f, 200f, fromTopPx = 100f, minLengthPx = 10f))
    }

    @Test fun aLongChatsThumbKeepsItsMinimumLengthAndStaysInside() {
        assertEquals(ScrollThumb(topPx = 90f, lengthPx = 10f), scrollThumb(100f, 10_000f, fromTopPx = 9_900f, minLengthPx = 10f))
        assertEquals(ScrollThumb(topPx = 90f, lengthPx = 10f), scrollThumb(100f, 10_000f, fromTopPx = 12_000f, minLengthPx = 10f))
    }
}
