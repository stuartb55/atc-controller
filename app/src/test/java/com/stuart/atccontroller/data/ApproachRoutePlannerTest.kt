package com.stuart.atccontroller.data

import com.stuart.atccontroller.simulation.AircraftState
import com.stuart.atccontroller.simulation.AircraftType
import com.stuart.atccontroller.simulation.Navigation
import com.stuart.atccontroller.simulation.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ApproachRoutePlannerTest {
    @Test
    fun `published terminal fix is used when it provides a sensible transition`() {
        val airport = ManchesterContent.airport
        val aircraft = AircraftState.inbound(
            id = "test-waypoint-transition",
            callsign = "TEST 123",
            type = AircraftType.JET,
            position = Vec2(0.78, 0.02),
            headingDegrees = 180.0,
            altitudeFeet = 5_000.0,
            speedKnots = 220.0,
        ).copy(runwayId = "23R")

        val plan = ApproachRoutePlanner.plan(aircraft, airport, "23R")
        val pol = airport.fixes.single { it.id == "GAME_NORTHEAST" }.position

        assertEquals("GAME_NORTHEAST", plan.viaFixId)
        assertTrue(plan.route.waypoints.contains(Vec2(pol.x, pol.y)))
    }

    @Test
    fun `approach route uses progressive vectors and aligns with the runway`() {
        val airport = ManchesterContent.airport
        val aircraft = inboundAtFix(airport, "GAME_NORTH", "23R")
        val route = ApproachRoutePlanner.plan(aircraft, airport, "23R").route
        val runway = airport.runwayEnds.single { it.id == "23R" }
        val courses = buildList {
            var previous = aircraft.position
            route.waypoints.forEach { point ->
                add(Navigation.bearingDegrees(previous, point, airport.mapWidthNm, airport.mapHeightNm))
                previous = point
            }
        }
        val changes = (listOf(aircraft.headingDegrees) + courses).zipWithNext { first, second ->
            abs(Navigation.signedHeadingDifference(first, second))
        }

        assertTrue("Abrupt course changes: $changes", changes.all { it <= 35.0 })
        assertTrue(
            abs(Navigation.signedHeadingDifference(courses.last(), runway.headingDegrees.toDouble())) < 0.1,
        )
        assertEquals(Vec2(runway.threshold.x, runway.threshold.y), route.waypoints.last())
    }

    @Test
    fun `all content-pack entry and runway combinations produce bounded valid routes`() {
        listOf(ManchesterContent.airport, CoastalContent.airport).forEach { airport ->
            airport.fixes.forEach { entry ->
                airport.runwayEnds.forEach { runway ->
                    val aircraft = inboundAtFix(airport, entry.id, runway.id)
                    val route = ApproachRoutePlanner.plan(aircraft, airport, runway.id).route

                    assertTrue("${airport.id}/${entry.id}/${runway.id}", route.waypoints.isNotEmpty())
                    assertTrue(route.waypoints.size <= 64)
                    assertEquals(
                        Vec2(runway.threshold.x, runway.threshold.y),
                        route.waypoints.last(),
                    )
                    val courses = buildList {
                        var previous = aircraft.position
                        route.waypoints.forEach { point ->
                            add(
                                Navigation.bearingDegrees(
                                    previous,
                                    point,
                                    airport.mapWidthNm,
                                    airport.mapHeightNm,
                                ),
                            )
                            previous = point
                        }
                    }
                    val maximumTurn = (listOf(aircraft.headingDegrees) + courses)
                        .zipWithNext { first, second ->
                            abs(Navigation.signedHeadingDifference(first, second))
                        }
                        .maxOrNull() ?: 0.0
                    assertTrue(
                        "${airport.id}/${entry.id}/${runway.id} turns $maximumTurn°",
                        maximumTurn <= 45.0,
                    )
                }
            }
        }
    }

    private fun inboundAtFix(
        airport: AirportDefinition,
        fixId: String,
        runwayId: String,
    ): AircraftState {
        val position = airport.fixes.single { it.id == fixId }.position.let { Vec2(it.x, it.y) }
        val centre = Vec2(0.5, 0.5)
        return AircraftState.inbound(
            id = "test-$fixId-$runwayId",
            callsign = "TEST 123",
            type = AircraftType.JET,
            position = position,
            headingDegrees = Navigation.bearingDegrees(
                position,
                centre,
                airport.mapWidthNm,
                airport.mapHeightNm,
            ),
            altitudeFeet = 5_000.0,
            speedKnots = 220.0,
        ).copy(runwayId = runwayId)
    }
}
