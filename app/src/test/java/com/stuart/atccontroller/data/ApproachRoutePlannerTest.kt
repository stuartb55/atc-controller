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
        assertTrue(
            "Expected controller-sized legs, got ${plan.route.waypoints.size}",
            plan.route.waypoints.size <= 10,
        )
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
            airport.fixes.filter { it.use != FixUse.WAYPOINT }.forEach { entry ->
                airport.runwayEnds.forEach { runway ->
                    val aircraft = inboundAtFix(airport, entry.id, runway.id)
                    val route = ApproachRoutePlanner.plan(aircraft, airport, runway.id).route

                    assertTrue("${airport.id}/${entry.id}/${runway.id}", route.waypoints.isNotEmpty())
                    assertTrue(
                        "${airport.id}/${entry.id}/${runway.id} has ${route.waypoints.size} legs",
                        route.waypoints.size <= 14,
                    )
                    assertEquals(
                        Vec2(runway.threshold.x, runway.threshold.y),
                        route.waypoints.last(),
                    )
                    val legDistances = buildList {
                        var previous = aircraft.position
                        route.waypoints.forEach { point ->
                            add(
                                Navigation.distanceNm(
                                    previous,
                                    point,
                                    airport.mapWidthNm,
                                    airport.mapHeightNm,
                                ),
                            )
                            previous = point
                        }
                    }
                    assertTrue(
                        "${airport.id}/${entry.id}/${runway.id} has tiny legs: $legDistances",
                        legDistances.all { it >= 0.4 },
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
                    val turns = (listOf(aircraft.headingDegrees) + courses)
                        .zipWithNext { first, second ->
                            abs(Navigation.signedHeadingDifference(first, second))
                        }
                    val maximumTurn = turns.maxOrNull() ?: 0.0
                    assertTrue(
                        "${airport.id}/${entry.id}/${runway.id} turns $maximumTurn°: $turns",
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
