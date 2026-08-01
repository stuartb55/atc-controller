package com.stuart.atccontroller.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorAssignmentControlsTest {
    @Test
    fun targetsSnapDeterministicallyAndStayInBounds() {
        assertEquals(355f, snapVectorTarget(358f, 0f..355f, 5f), 0f)
        assertEquals(0f, snapVectorTarget(-12f, 0f..355f, 5f), 0f)
        assertEquals(7_000f, snapVectorTarget(7_249f, 0f..12_000f, 500f), 0f)
        assertEquals(400f, snapVectorTarget(900f, 80f..400f, 10f), 0f)
    }

    @Test
    fun sliderStepCountMatchesEveryReachableTarget() {
        assertEquals(70, sliderSteps(0f..355f, 5f))
        assertEquals(23, sliderSteps(0f..12_000f, 500f))
        assertEquals(31, sliderSteps(80f..400f, 10f))
    }
}
