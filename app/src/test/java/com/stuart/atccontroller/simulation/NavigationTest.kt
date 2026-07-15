package com.stuart.atccontroller.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    @Test
    fun `aviation bearings use north as zero and turn across north by shortest path`() {
        assertEquals(0.0, Navigation.bearingDegrees(Vec2(.5, .5), Vec2(.5, .25), 20.0, 20.0), 1e-6)
        assertEquals(90.0, Navigation.bearingDegrees(Vec2(.5, .5), Vec2(.75, .5), 20.0, 20.0), 1e-6)
        assertEquals(2.0, Navigation.turnTowards(358.0, 10.0, 4.0), 1e-6)
        assertEquals(356.0, Navigation.turnTowards(2.0, 350.0, 6.0), 1e-6)
    }

    @Test
    fun `movement and distance account for rectangular map dimensions`() {
        val start = Vec2(.5, .5)
        val moved = Navigation.move(start, 90.0, 2.0, mapWidthNm = 20.0, mapHeightNm = 10.0)

        assertEquals(.6, moved.x, 1e-6)
        assertEquals(.5, moved.y, 1e-6)
        assertEquals(2.0, Navigation.distanceNm(start, moved, 20.0, 10.0), 1e-6)
    }

    @Test
    fun `runway centreline distance is perpendicular physical distance`() {
        val runway = runway()
        val distance = Navigation.distanceToRunwayCentrelineNm(
            point = Vec2(.6, .5),
            runway = runway,
            mapWidthNm = 20.0,
            mapHeightNm = 10.0,
        )

        assertEquals(2.0, distance, 1e-6)
        assertTrue(Navigation.signedHeadingDifference(350.0, 10.0) > 0.0)
    }
}
