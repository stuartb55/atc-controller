package com.stuart.atccontroller.data

import com.stuart.atccontroller.simulation.AircraftState
import com.stuart.atccontroller.simulation.AtcSimulationEngine
import com.stuart.atccontroller.simulation.Navigation
import com.stuart.atccontroller.simulation.Route
import com.stuart.atccontroller.simulation.Vec2
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** A deterministic, flyable route produced by the approach-setup assist. */
data class ApproachRoutePlan(
    val route: Route,
    /** Published terminal fix included in [route], when one is a sensible part of the arrival. */
    val viaFixId: String? = null,
)

/**
 * Builds a curved vector onto a three-mile final instead of sending an aircraft to one close-in
 * point and requiring an abrupt turn onto the runway centreline.
 *
 * The curve is represented by explicit replay-safe waypoints. A suitable published terminal fix
 * is retained as an exact point on the curve; otherwise the route is made entirely from vectors.
 */
object ApproachRoutePlanner {
    private const val FINAL_INTERCEPT_DISTANCE_NM = 2.5
    private const val SAMPLE_SPACING_NM = 0.75
    private const val MIN_FIX_LEG_NM = 0.75
    private const val MAX_INITIAL_FIX_TURN_DEGREES = 90.0
    private const val MAX_FIX_TO_FINAL_ANGLE_DEGREES = 45.0
    private const val MAX_FIX_DETOUR_NM = 4.0

    fun plan(
        aircraft: AircraftState,
        airport: AirportDefinition,
        runwayEndId: String,
    ): ApproachRoutePlan {
        val runway = airport.runwayEnds.firstOrNull { it.id == runwayEndId }
            ?: error("Unknown runway end $runwayEndId for ${airport.id}")
        val threshold = runway.threshold.toVec2()
        val distanceToThreshold = distanceNm(aircraft.position, threshold, airport)
        val headingError = abs(
            Navigation.signedHeadingDifference(aircraft.headingDegrees, runway.headingDegrees.toDouble()),
        )

        // Do not turn an aircraft back to an intercept it has already passed while established.
        if (distanceToThreshold <= FINAL_INTERCEPT_DISTANCE_NM && headingError <= 30.0) {
            return ApproachRoutePlan(Route(listOf(threshold)))
        }

        val intercept = Navigation.move(
            position = threshold,
            headingDegrees = runway.headingDegrees + 180.0,
            distanceNm = FINAL_INTERCEPT_DISTANCE_NM,
            mapWidthNm = airport.mapWidthNm,
            mapHeightNm = airport.mapHeightNm,
        )
        val fix = selectTransitionFix(
            aircraft = aircraft,
            intercept = intercept,
            approachHeadingDegrees = runway.headingDegrees.toDouble(),
            airport = airport,
        )
        val anchors = buildList {
            add(aircraft.position.toNm(airport))
            fix?.let { add(it.position.toNm(airport)) }
            add(intercept.toNm(airport))
        }
        val tangents = anchors.indices.map { index ->
            when (index) {
                0 -> headingVector(aircraft.headingDegrees).scaled(
                    min(anchors[0].distanceTo(anchors[1]) * 0.45, 4.0),
                )

                anchors.lastIndex -> headingVector(runway.headingDegrees.toDouble()).scaled(
                    min(anchors[index - 1].distanceTo(anchors[index]) * 0.65, 3.0),
                )

                else -> {
                    val direction = (anchors[index + 1] - anchors[index - 1]).normalized()
                    direction.scaled(
                        min(
                            anchors[index - 1].distanceTo(anchors[index]),
                            anchors[index].distanceTo(anchors[index + 1]),
                        ) * 0.8,
                    )
                }
            }
        }

        val curvedPoints = buildList {
            for (index in 0 until anchors.lastIndex) {
                val chordLength = anchors[index].distanceTo(anchors[index + 1])
                val samples = ceil(chordLength / SAMPLE_SPACING_NM).toInt().coerceAtLeast(3)
                for (sample in 1..samples) {
                    add(
                        hermite(
                            start = anchors[index],
                            end = anchors[index + 1],
                            startTangent = tangents[index],
                            endTangent = tangents[index + 1],
                            t = sample.toDouble() / samples,
                        ).toVec2(airport),
                    )
                }
            }
        }
        val distinctPoints = (curvedPoints + threshold)
            .fold(emptyList<Vec2>()) { result, point ->
                if (result.lastOrNull() == point) result else result + point
            }
        val points = if (distinctPoints.size <= AtcSimulationEngine.MAX_ROUTE_WAYPOINTS) {
            distinctPoints
        } else {
            distinctPoints.take(AtcSimulationEngine.MAX_ROUTE_WAYPOINTS - 1) + threshold
        }

        return ApproachRoutePlan(
            route = Route(points),
            viaFixId = fix?.id,
        )
    }

    private fun selectTransitionFix(
        aircraft: AircraftState,
        intercept: Vec2,
        approachHeadingDegrees: Double,
        airport: AirportDefinition,
    ): NavigationFixDefinition? {
        val directDistance = distanceNm(aircraft.position, intercept, airport)
        if (directDistance < 4.0) return null

        return airport.fixes.asSequence()
            .map { fix ->
                val position = fix.position.toVec2()
                val firstLeg = distanceNm(aircraft.position, position, airport)
                val secondLeg = distanceNm(position, intercept, airport)
                val initialTurn = abs(
                    Navigation.signedHeadingDifference(
                        aircraft.headingDegrees,
                        Navigation.bearingDegrees(
                            aircraft.position,
                            position,
                            airport.mapWidthNm,
                            airport.mapHeightNm,
                        ),
                    ),
                )
                val finalAngle = abs(
                    Navigation.signedHeadingDifference(
                        Navigation.bearingDegrees(
                            position,
                            intercept,
                            airport.mapWidthNm,
                            airport.mapHeightNm,
                        ),
                        approachHeadingDegrees,
                    ),
                )
                val detour = firstLeg + secondLeg - directDistance
                FixCandidate(fix, firstLeg, secondLeg, initialTurn, finalAngle, detour)
            }
            .filter { candidate ->
                candidate.firstLeg >= MIN_FIX_LEG_NM &&
                    candidate.secondLeg >= MIN_FIX_LEG_NM &&
                    candidate.initialTurn <= MAX_INITIAL_FIX_TURN_DEGREES &&
                    candidate.finalAngle <= MAX_FIX_TO_FINAL_ANGLE_DEGREES &&
                    candidate.detour <= maxOf(MAX_FIX_DETOUR_NM, directDistance * 0.2)
            }
            .minByOrNull { candidate ->
                candidate.detour + candidate.initialTurn / 60.0 + candidate.finalAngle / 30.0
            }
            ?.fix
    }

    private fun hermite(
        start: PointNm,
        end: PointNm,
        startTangent: PointNm,
        endTangent: PointNm,
        t: Double,
    ): PointNm {
        val t2 = t * t
        val t3 = t2 * t
        return start.scaled(2 * t3 - 3 * t2 + 1) +
            startTangent.scaled(t3 - 2 * t2 + t) +
            end.scaled(-2 * t3 + 3 * t2) +
            endTangent.scaled(t3 - t2)
    }

    private fun headingVector(headingDegrees: Double): PointNm {
        val radians = Math.toRadians(Navigation.normalizeHeading(headingDegrees))
        return PointNm(sin(radians), -cos(radians))
    }

    private fun distanceNm(from: Vec2, to: Vec2, airport: AirportDefinition): Double =
        Navigation.distanceNm(from, to, airport.mapWidthNm, airport.mapHeightNm)

    private fun NormalizedPoint.toVec2() = Vec2(x, y)

    private fun Vec2.toNm(airport: AirportDefinition) =
        PointNm(x * airport.mapWidthNm, y * airport.mapHeightNm)

    private fun NormalizedPoint.toNm(airport: AirportDefinition) =
        PointNm(x * airport.mapWidthNm, y * airport.mapHeightNm)

    private fun PointNm.toVec2(airport: AirportDefinition) = Vec2(
        (x / airport.mapWidthNm).coerceIn(0.0, 1.0),
        (y / airport.mapHeightNm).coerceIn(0.0, 1.0),
    )

    private data class FixCandidate(
        val fix: NavigationFixDefinition,
        val firstLeg: Double,
        val secondLeg: Double,
        val initialTurn: Double,
        val finalAngle: Double,
        val detour: Double,
    )

    private data class PointNm(val x: Double, val y: Double) {
        operator fun plus(other: PointNm) = PointNm(x + other.x, y + other.y)
        operator fun minus(other: PointNm) = PointNm(x - other.x, y - other.y)
        fun scaled(scale: Double) = PointNm(x * scale, y * scale)
        fun distanceTo(other: PointNm) = hypot(other.x - x, other.y - y)
        fun normalized(): PointNm {
            val length = hypot(x, y)
            return if (length == 0.0) PointNm(0.0, 0.0) else PointNm(x / length, y / length)
        }
    }
}
