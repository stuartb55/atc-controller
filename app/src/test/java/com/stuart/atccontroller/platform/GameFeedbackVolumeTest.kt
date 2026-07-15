package com.stuart.atccontroller.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFeedbackVolumeTest {
    @Test
    fun feedbackVolumeRejectsNonFiniteValuesAndClampsBounds() {
        assertEquals(0f, Float.NaN.normalizedFeedbackVolume(), 0f)
        assertEquals(0f, Float.POSITIVE_INFINITY.normalizedFeedbackVolume(), 0f)
        assertEquals(0f, (-0.2f).normalizedFeedbackVolume(), 0f)
        assertEquals(.42f, .42f.normalizedFeedbackVolume(), 0f)
        assertEquals(1f, 1.4f.normalizedFeedbackVolume(), 0f)
    }
}
