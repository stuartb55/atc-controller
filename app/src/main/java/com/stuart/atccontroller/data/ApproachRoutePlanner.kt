package com.stuart.atccontroller.data

import com.stuart.atccontroller.simulation.AircraftState
import com.stuart.atccontroller.simulation.AtcSimulationEngine
import com.stuart.atccontroller.simulation.Navigation
import com.stuart.atccontroller.simulation.Route
import com.stuart.atccontroller.simulation.Vec2
import kotlin.math.abs
import kotlin.math.atan2
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
 * Builds progressive vectors onto a stabilized final instead of sending an aircraft to one
 * close-in point and requiring an abrupt turn onto the runway centreline.
 *
 * The curve is sampled internally, then reduced to a small number of replay-safe vector legs. A
 * suitable published terminal fix is retained as an exact point on the route; otherwise the route
 * is made entirely from vectors.
 */
object ApproachRoutePlanner {
    private const val FINAL_INTERCEPT_DISTANCE_NM = 2.0
    private const val CURVE_SAMPLE_SPACING_NM = 0.25
    private const val MAX_VECTOR_TURN_DEGREES = 30.0
    private const val MAX_VECTOR_DEVIATION_NM = 0.5
    private const val MIN_VECTOR_LEG_NM = 0.4
    private const val MIN_FIX_LEG_NM = 0.75
    private const val MAX_INITIAL_FIX_TURN_DEGREES = 90.0
    private const val MAX_FIX_TO_FINAL_ANGLE_DEGREES = 45.0
    private const val MAX_FIX_DETOUR_NM = 4.0
    private const val VECTOR_TURN_DEGREES = 30
    private const val VECTOR_LEG_NM = 1.25

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

        val approachSideRoom = distanceToMapEdgeNm(
            threshold,
            runway.headingDegrees + 180.0,
            airport,
        )
        val interceptDistance = min(
            FINAL_INTERCEPT_DISTANCE_NM,
            (approachSideRoom - VECTOR_LEG_NM * 2.0).coerceAtLeast(1.0),
        )
        val intercept = Navigation.move(
            position = threshold,
            headingDegrees = runway.headingDegrees + 180.0,
            distanceNm = interceptDistance,
            mapWidthNm = airport.mapWidthNm,
            mapHeightNm = airport.mapHeightNm,
        )
        val join = selectApproachJoin(
            aircraft = aircraft,
            intercept = intercept,
            threshold = threshold,
            runwayHeadingDegrees = runway.headingDegrees.toDouble(),
            airport = airport,
        )
        val preferredFix = selectTransitionFix(
            aircraft = aircraft,
            intercept = join.position,
            approachHeadingDegrees = join.outboundHeadingDegrees,
            airport = airport,
        )
        val preferredPoints = buildRoutePoints(aircraft, airport, join, preferredFix)
        val fix = preferredFix?.takeIf {
            maximumCourseChange(aircraft, preferredPoints, airport) <=
                MAX_FIX_TO_FINAL_ANGLE_DEGREES &&
                minimumLegDistanceNm(aircraft.position, preferredPoints, airport) >=
                MIN_VECTOR_LEG_NM
        }
        val points = if (fix == preferredFix) {
            preferredPoints
        } else {
            buildRoutePoints(aircraft, airport, join, fix = null)
        }

        return ApproachRoutePlan(
            route = Route(points),
            viaFixId = fix?.id,
        )
    }

    private fun buildRoutePoints(
        aircraft: AircraftState,
        airport: AirportDefinition,
        join: ApproachJoin,
        fix: NavigationFixDefinition?,
    ): List<Vec2> {
        val anchors = buildList {
            add(aircraft.position.toNm(airport))
            fix?.let { add(it.position.toNm(airport)) }
            add(join.position.toNm(airport))
        }
        val tangents = anchors.indices.map { index ->
            when (index) {
                0 -> headingVector(aircraft.headingDegrees).scaled(
                    min(anchors[0].distanceTo(anchors[1]) * 0.65, 4.0),
                )

                anchors.lastIndex -> headingVector(join.outboundHeadingDegrees).scaled(
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

        val vectorPoints = buildList {
            var inboundHeading = aircraft.headingDegrees
            for (index in 0 until anchors.lastIndex) {
                val chordLength = anchors[index].distanceTo(anchors[index + 1])
                val samples = ceil(chordLength / CURVE_SAMPLE_SPACING_NM)
                    .toInt()
                    .coerceAtLeast(16)
                val sampledCurve = (1..samples).map { sample ->
                    hermite(
                            start = anchors[index],
                            end = anchors[index + 1],
                            startTangent = tangents[index],
                            endTangent = tangents[index + 1],
                            t = sample.toDouble() / samples,
                        )
                }
                val vectors = simplifyCurveSegment(
                    start = anchors[index],
                    inboundHeadingDegrees = inboundHeading,
                    sampledCurve = sampledCurve,
                    outboundHeadingDegrees = tangents[index + 1].headingDegrees(),
                )
                addAll(vectors.map { it.toVec2(airport) })
                vectors.lastOrNull()?.let { last ->
                    val previous = if (vectors.size > 1) {
                        vectors[vectors.lastIndex - 1]
                    } else {
                        anchors[index]
                    }
                    inboundHeading = previous.bearingTo(last)
                }
            }
        }
        val distinctPoints = (vectorPoints + join.suffix)
            .fold(emptyList<Vec2>()) { result, point ->
                if (result.lastOrNull() == point) result else result + point
            }
        val protectedPoints = buildSet {
            fix?.let { add(it.position.toVec2()) }
            add(join.position)
            addAll(join.suffix)
        }
        val controllerPoints = removeShortAnonymousLegs(
            aircraft = aircraft,
            airport = airport,
            points = distinctPoints,
            protectedPoints = protectedPoints,
        )
        val points = if (controllerPoints.size <= AtcSimulationEngine.MAX_ROUTE_WAYPOINTS) {
            controllerPoints
        } else {
            controllerPoints.take(AtcSimulationEngine.MAX_ROUTE_WAYPOINTS - 1) + join.suffix.last()
        }
        return points
    }

    private fun removeShortAnonymousLegs(
        aircraft: AircraftState,
        airport: AirportDefinition,
        points: List<Vec2>,
        protectedPoints: Set<Vec2>,
    ): List<Vec2> {
        val result = points.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            var previous = aircraft.position
            for (index in result.indices) {
                if (distanceNm(previous, result[index], airport) < MIN_VECTOR_LEG_NM) {
                    val removableIndices = buildList {
                        if (result[index] !in protectedPoints) add(index)
                        if (index > 0 && result[index - 1] !in protectedPoints) add(index - 1)
                    }
                    val removable = removableIndices.firstOrNull { removalIndex ->
                        val candidate = result.toMutableList().apply { removeAt(removalIndex) }
                        maximumCourseChange(aircraft, candidate, airport) <=
                            MAX_FIX_TO_FINAL_ANGLE_DEGREES
                    }
                    if (removable != null) {
                        result.removeAt(removable)
                        changed = true
                        break
                    }
                }
                previous = result[index]
            }
        }
        return result
    }

    /**
     * Converts a smooth internal curve into controller-sized heading legs. The farthest point that
     * stays close to the curve and keeps both turns flyable is selected on each pass. Segment ends
     * remain exact, so a published fix is never replaced by an anonymous point beside it.
     */
    private fun simplifyCurveSegment(
        start: PointNm,
        inboundHeadingDegrees: Double,
        sampledCurve: List<PointNm>,
        outboundHeadingDegrees: Double,
    ): List<PointNm> {
        if (sampledCurve.isEmpty()) return emptyList()

        val result = mutableListOf<PointNm>()
        var current = start
        var currentSampleIndex = -1
        var inboundHeading = inboundHeadingDegrees

        while (currentSampleIndex < sampledCurve.lastIndex) {
            val candidateRange = (currentSampleIndex + 1)..sampledCurve.lastIndex
            val selectedIndex = candidateRange.lastOrNull { candidateIndex ->
                val candidate = sampledCurve[candidateIndex]
                if (current.distanceTo(candidate) < 1e-6) return@lastOrNull false

                val course = current.bearingTo(candidate)
                val entryTurn = abs(
                    Navigation.signedHeadingDifference(inboundHeading, course),
                )
                val exitTurn = if (candidateIndex == sampledCurve.lastIndex) {
                    abs(Navigation.signedHeadingDifference(course, outboundHeadingDegrees))
                } else {
                    0.0
                }
                entryTurn <= MAX_VECTOR_TURN_DEGREES &&
                    exitTurn <= MAX_VECTOR_TURN_DEGREES &&
                    maximumShortcutDeviationNm(
                        start = current,
                        end = candidate,
                        points = sampledCurve.subList(currentSampleIndex + 1, candidateIndex + 1),
                    ) <= MAX_VECTOR_DEVIATION_NM
            } ?: (currentSampleIndex + 1)

            val selected = sampledCurve[selectedIndex]
            if (current.distanceTo(selected) >= 1e-6) {
                inboundHeading = current.bearingTo(selected)
                result += selected
                current = selected
            }
            currentSampleIndex = selectedIndex
        }
        return result
    }

    private fun maximumShortcutDeviationNm(
        start: PointNm,
        end: PointNm,
        points: List<PointNm>,
    ): Double = points.maxOfOrNull { point ->
        point.distanceToSegment(start, end)
    } ?: 0.0

    private fun maximumCourseChange(
        aircraft: AircraftState,
        points: List<Vec2>,
        airport: AirportDefinition,
    ): Double {
        var previous = aircraft.position
        var course = aircraft.headingDegrees
        var maximum = 0.0
        points.forEach { point ->
            val nextCourse = Navigation.bearingDegrees(
                previous,
                point,
                airport.mapWidthNm,
                airport.mapHeightNm,
            )
            maximum = maxOf(
                maximum,
                abs(Navigation.signedHeadingDifference(course, nextCourse)),
            )
            previous = point
            course = nextCourse
        }
        return maximum
    }

    private fun routeDistanceNm(
        start: Vec2,
        points: List<Vec2>,
        airport: AirportDefinition,
    ): Double {
        var previous = start
        return points.sumOf { point ->
            distanceNm(previous, point, airport).also { previous = point }
        }
    }

    private fun minimumLegDistanceNm(
        start: Vec2,
        points: List<Vec2>,
        airport: AirportDefinition,
    ): Double {
        var previous = start
        return points.minOfOrNull { point ->
            distanceNm(previous, point, airport).also { previous = point }
        } ?: Double.POSITIVE_INFINITY
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

    /**
     * Chooses a point on a 30-degree vectoring arc onto final. This gives arrivals that begin on
     * the wrong side of the runway enough room to turn through base rather than forcing the final
     * Hermite segment to double back on itself.
     */
    private fun selectApproachJoin(
        aircraft: AircraftState,
        intercept: Vec2,
        threshold: Vec2,
        runwayHeadingDegrees: Double,
        airport: AirportDefinition,
    ): ApproachJoin {
        val candidates = buildList {
            add(
                scoredJoin(
                    aircraft,
                    intercept,
                    runwayHeadingDegrees,
                    listOf(threshold),
                    airport,
                ),
            )
            listOf(-1, 1).forEach { side ->
                var nearPoint = intercept
                var suffix = listOf(intercept, threshold)
                for (angle in VECTOR_TURN_DEGREES..180 step VECTOR_TURN_DEGREES) {
                    val outboundHeading = Navigation.normalizeHeading(
                        runwayHeadingDegrees + side * angle,
                    )
                    val farPoint = Navigation.move(
                        position = nearPoint,
                        headingDegrees = outboundHeading + 180.0,
                        distanceNm = VECTOR_LEG_NM,
                        mapWidthNm = airport.mapWidthNm,
                        mapHeightNm = airport.mapHeightNm,
                    )
                    val actualLeg = distanceNm(farPoint, nearPoint, airport)
                    val actualHeading = Navigation.bearingDegrees(
                        farPoint,
                        nearPoint,
                        airport.mapWidthNm,
                        airport.mapHeightNm,
                    )
                    if (actualLeg < VECTOR_LEG_NM * 0.7 ||
                        abs(Navigation.signedHeadingDifference(actualHeading, outboundHeading)) > 5.0
                    ) {
                        break
                    }
                    add(scoredJoin(aircraft, farPoint, outboundHeading, suffix, airport))
                    nearPoint = farPoint
                    suffix = listOf(farPoint) + suffix
                }
            }
        }
        val evaluated = candidates.map { candidate ->
            val points = buildRoutePoints(aircraft, airport, candidate, fix = null)
            EvaluatedJoin(
                join = candidate,
                maximumTurn = maximumCourseChange(aircraft, points, airport),
                routeDistanceNm = routeDistanceNm(aircraft.position, points, airport),
                minimumLegDistanceNm = minimumLegDistanceNm(
                    aircraft.position,
                    points,
                    airport,
                ),
            )
        }
        val flyable = evaluated.filter { candidate ->
            candidate.maximumTurn <= 45.0 &&
                candidate.minimumLegDistanceNm >= MIN_VECTOR_LEG_NM
        }.ifEmpty {
            evaluated.filter { it.maximumTurn <= 45.0 }
        }.ifEmpty { evaluated }
        return flyable.minBy { candidate ->
            candidate.routeDistanceNm + candidate.join.score * 0.05 +
                (candidate.maximumTurn - 45.0).coerceAtLeast(0.0) * 1_000.0 +
                (MIN_VECTOR_LEG_NM - candidate.minimumLegDistanceNm)
                    .coerceAtLeast(0.0) * 1_000.0
        }.join
    }

    private fun scoredJoin(
        aircraft: AircraftState,
        position: Vec2,
        outboundHeadingDegrees: Double,
        suffix: List<Vec2>,
        airport: AirportDefinition,
    ): ApproachJoin {
        val bearingToJoin = Navigation.bearingDegrees(
            aircraft.position,
            position,
            airport.mapWidthNm,
            airport.mapHeightNm,
        )
        val initialTurn = abs(
            Navigation.signedHeadingDifference(aircraft.headingDegrees, bearingToJoin),
        )
        val mergeTurn = abs(
            Navigation.signedHeadingDifference(bearingToJoin, outboundHeadingDegrees),
        )
        val connectionDistance = distanceNm(aircraft.position, position, airport)
        val excessiveTurnPenalty = maxOf(initialTurn, mergeTurn).coerceAtLeast(45.0) * 4.0
        return ApproachJoin(
            position = position,
            outboundHeadingDegrees = outboundHeadingDegrees,
            suffix = suffix,
            score = initialTurn + mergeTurn * 1.5 + connectionDistance * 0.25 + excessiveTurnPenalty,
        )
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

    private fun distanceToMapEdgeNm(
        position: Vec2,
        headingDegrees: Double,
        airport: AirportDefinition,
    ): Double {
        val direction = headingVector(headingDegrees)
        val xNm = position.x * airport.mapWidthNm
        val yNm = position.y * airport.mapHeightNm
        val xLimit = when {
            direction.x > 0.0 -> (airport.mapWidthNm - xNm) / direction.x
            direction.x < 0.0 -> -xNm / direction.x
            else -> Double.POSITIVE_INFINITY
        }
        val yLimit = when {
            direction.y > 0.0 -> (airport.mapHeightNm - yNm) / direction.y
            direction.y < 0.0 -> -yNm / direction.y
            else -> Double.POSITIVE_INFINITY
        }
        return min(xLimit, yLimit)
    }

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

    private data class ApproachJoin(
        val position: Vec2,
        val outboundHeadingDegrees: Double,
        val suffix: List<Vec2>,
        val score: Double,
    )

    private data class EvaluatedJoin(
        val join: ApproachJoin,
        val maximumTurn: Double,
        val routeDistanceNm: Double,
        val minimumLegDistanceNm: Double,
    )

    private data class PointNm(val x: Double, val y: Double) {
        operator fun plus(other: PointNm) = PointNm(x + other.x, y + other.y)
        operator fun minus(other: PointNm) = PointNm(x - other.x, y - other.y)
        fun scaled(scale: Double) = PointNm(x * scale, y * scale)
        fun distanceTo(other: PointNm) = hypot(other.x - x, other.y - y)
        fun bearingTo(other: PointNm): Double = Navigation.normalizeHeading(
            Math.toDegrees(atan2(other.x - x, -(other.y - y))),
        )
        fun headingDegrees(): Double = Navigation.normalizeHeading(
            Math.toDegrees(atan2(x, -y)),
        )
        fun distanceToSegment(start: PointNm, end: PointNm): Double {
            val segmentX = end.x - start.x
            val segmentY = end.y - start.y
            val lengthSquared = segmentX * segmentX + segmentY * segmentY
            if (lengthSquared == 0.0) return distanceTo(start)
            val projection = (
                ((x - start.x) * segmentX + (y - start.y) * segmentY) / lengthSquared
                ).coerceIn(0.0, 1.0)
            return distanceTo(
                PointNm(start.x + segmentX * projection, start.y + segmentY * projection),
            )
        }
        fun normalized(): PointNm {
            val length = hypot(x, y)
            return if (length == 0.0) PointNm(0.0, 0.0) else PointNm(x / length, y / length)
        }
    }
}
