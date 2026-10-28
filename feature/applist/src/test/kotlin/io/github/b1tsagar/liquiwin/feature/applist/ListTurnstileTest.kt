package io.github.b1tsagar.liquiwin.feature.applist

import io.github.b1tsagar.liquiwin.core.design.motion.Turnstile
import org.junit.Assert.assertEquals
import org.junit.Test

class ListTurnstileTest {
    @Test fun rowsGoTopToBottomFromTheFirstOnScreenAfterTheSearchBox() {
        assertEquals(1, listTurnstileIndex(Turnstile.OUT, item = 7, first = 7, tapped = null, onScreen = 10))
        assertEquals(4, listTurnstileIndex(Turnstile.OUT, item = 10, first = 7, tapped = null, onScreen = 10))
    }

    @Test fun goingOutTheTappedRowGoesLast() {
        assertEquals(10, listTurnstileIndex(Turnstile.OUT, item = 8, first = 7, tapped = 8, onScreen = 10))
    }

    @Test fun comingBackTheTappedRowKeepsItsPlace() {
        assertEquals(2, listTurnstileIndex(Turnstile.BACK_IN, item = 8, first = 7, tapped = 8, onScreen = 10))
    }
}
