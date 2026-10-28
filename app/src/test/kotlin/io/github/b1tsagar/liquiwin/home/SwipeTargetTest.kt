package io.github.b1tsagar.liquiwin.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeTargetTest {
    @Test fun aFifthOfTheScreenLeftOnStartOpensTheList() {
        assertEquals(1, swipeTarget(page = 0, drag = -250f, velocity = 0f, width = 1000f, fling = 1000f))
    }

    @Test fun aShortSlowSwipeGoesNowhere() {
        assertNull(swipeTarget(page = 0, drag = -100f, velocity = -200f, width = 1000f, fling = 1000f))
    }

    @Test fun aFlickCountsHoweverShort() {
        assertEquals(1, swipeTarget(page = 0, drag = -40f, velocity = -1500f, width = 1000f, fling = 1000f))
        assertEquals(0, swipeTarget(page = 1, drag = 40f, velocity = 1500f, width = 1000f, fling = 1000f))
    }

    @Test fun swipingTowardTheEdgeGoesNowhere() {
        assertNull(swipeTarget(page = 0, drag = 300f, velocity = 0f, width = 1000f, fling = 1000f))
        assertNull(swipeTarget(page = 1, drag = -300f, velocity = 0f, width = 1000f, fling = 1000f))
    }
}
