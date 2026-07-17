package com.stuart.atccontroller.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    fun panAndZoomTransformRoundTripsRadarCoordinates() {
        val size = Size(400f, 300f)
        val viewport = updateRadarViewport(
            current = RadarViewport(),
            centroid = Offset(200f, 150f),
            pan = Offset(-30f, 20f),
            zoomChange = 2f,
            viewportSize = size,
        )
        val mapPoint = Offset(100f, 90f)

        val screenPoint = mapToScreen(mapPoint, viewport, size)

        assertEquals(mapPoint.x, screenToMap(screenPoint, viewport, size).x, .001f)
        assertEquals(mapPoint.y, screenToMap(screenPoint, viewport, size).y, .001f)
        assertEquals(2f, viewport.scale, 0f)
    }

    @Test
    fun viewportPanIsClampedAndZoomingOutResetsIt() {
        val size = Size(320f, 220f)
        val zoomed = updateRadarViewport(
            RadarViewport(),
            Offset(160f, 110f),
            Offset(10_000f, -10_000f),
            3f,
            size,
        )
        assertTrue(zoomed.offset.x <= 320f)
        assertTrue(zoomed.offset.y >= -220f)

        val reset = updateRadarViewport(zoomed, Offset(160f, 110f), Offset.Zero, .01f, size)
        assertEquals(RadarViewport(), reset)
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
