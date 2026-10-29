package io.github.b1tsagar.liquiwin.core.design.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TiltGestureTest {
    @get:Rule val compose = createComposeRule()

    // A large tile's list scrolls under a still finger, so a tilt that followed it would rock the tile as it went.
    @Test fun aScrollInsideLetsGoOfTheTilt() {
        lateinit var tilted: LayoutCoordinates
        compose.setContent {
            Box(Modifier.size(200.dp).tilt().onPlaced { tilted = it }.testTag("tilted")) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Box(Modifier.size(200.dp, 1_000.dp)) }
            }
        }
        val rest = tilted.boundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tilted").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        assertNotEquals(rest, tilted.boundsInRoot()) // pressed in
        compose.onNodeWithTag("tilted").performTouchInput { repeat(5) { moveBy(Offset(0f, -40f)) } }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(rest, tilted.boundsInRoot()) // at rest again, with the finger still down
        compose.onNodeWithTag("tilted").performTouchInput { up() }
    }
}
