package io.github.b1tsagar.liquiwin.core.design.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class SwivelTest {
    @Test fun swivellingInStartsTiltedAndSettlesEarly() {
        assertEquals(-45f, swivelAngle(Swivel.IN, 0f), 0.001f)
        assertEquals(-2.13f, swivelAngle(Swivel.IN, 175f), 0.05f) // exponent 6, easing out
        assertEquals(0f, swivelAngle(Swivel.IN, 350f), 0.001f)
        assertEquals(0f, swivelAngle(Swivel.IN, 1_000f), 0.001f)
    }

    @Test fun swivellingOutWaitsThenTurnsEdgeOn() {
        assertEquals(0.05f, swivelAngle(Swivel.OUT, 125f), 0.01f) // exponent 15, easing in
        assertEquals(90f, swivelAngle(Swivel.OUT, 250f), 0.001f)
    }
}
