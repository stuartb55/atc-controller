package com.stuart.atccontroller.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarInteractionTest {
    @Test
    fun hitRegionsIncludeGlyphAndLabelArea() {
        val regions = listOf(RadarHitRegion("A1", 10f, 20f, 80f, 90f))
        assertEquals("A1", hitTestAircraft(12f, 22f, regions))
        assertEquals("A1", hitTestAircraft(75f, 85f, regions))
        assertNull(hitTestAircraft(9f, 50f, regions))
    }

    @Test
    fun shortDragIsRejected() {
        assertFalse(isRouteGestureLongEnough(listOf(NormalizedPoint(.2f, .2f), NormalizedPoint(.21f, .21f))))
        assertTrue(isRouteGestureLongEnough(listOf(NormalizedPoint(.2f, .2f), NormalizedPoint(.3f, .3f))))
    }

    @Test
    fun simplificationKeepsShapeAndEndpoints() {
        val points = listOf(
            NormalizedPoint(0f, 0f), NormalizedPoint(.1f, .001f),
            NormalizedPoint(.2f, 0f), NormalizedPoint(.2f, .2f),
        )
        val simplified = simplifyFingerPath(points, .01f)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertEquals(3, simplified.size)
    }

    @Test
    fun nearestEnteredSnapZoneWins() {
        val fix = RadarSnapTarget(RouteTerminalTarget.NavigationFix("FIX"), NormalizedPoint(.5f, .5f), .1f)
        val runway = RadarSnapTarget(RouteTerminalTarget.AssignedRunway("23R"), NormalizedPoint(.55f, .5f), .1f)
        assertEquals(runway, selectSnapTarget(NormalizedPoint(.56f, .5f), listOf(fix, runway)))
        assertNull(selectSnapTarget(NormalizedPoint(.9f, .9f), listOf(fix, runway)))
    }

    @Test
    fun approachCompositionAppendsValidatedInterceptAndThreshold() {
        val drawn = listOf(NormalizedPoint(.1f, .1f), NormalizedPoint(.4f, .4f))
        val final = listOf(NormalizedPoint(.6f, .6f), NormalizedPoint(.7f, .7f))
        assertEquals(drawn + final, composeApproachRoute(drawn, final))
    }

    @Test
    fun onlyAssignedRunwayIsAcceptedForAnArrival() {
        assertTrue(routeTerminalIsAllowed(RouteTerminalTarget.AssignedRunway("23R"), "23R", true))
        assertFalse(routeTerminalIsAllowed(RouteTerminalTarget.AssignedRunway("23L"), "23R", true))
        assertFalse(routeTerminalIsAllowed(RouteTerminalTarget.AssignedRunway("23R"), "23R", false))
        assertTrue(routeTerminalIsAllowed(RouteTerminalTarget.NavigationFix("NORTH"), null, false))
    }
}
