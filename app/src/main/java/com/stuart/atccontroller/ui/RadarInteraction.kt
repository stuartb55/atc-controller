package com.stuart.atccontroller.ui

import kotlin.math.hypot

/** Semantic end point retained with a drawn route after a magnetic snap. */
sealed interface RouteTerminalTarget {
    data class AssignedRunway(val runwayId: String) : RouteTerminalTarget
    data class NavigationFix(val name: String) : RouteTerminalTarget
}

internal data class RadarHitRegion(
    val aircraftId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

internal data class RadarSnapTarget(
    val terminal: RouteTerminalTarget,
    val position: NormalizedPoint,
    val radius: Float,
)

internal fun hitTestAircraft(
    x: Float,
    y: Float,
    regions: List<RadarHitRegion>,
): String? = regions.lastOrNull { it.contains(x, y) }?.aircraftId

internal fun selectSnapTarget(
    point: NormalizedPoint,
    targets: List<RadarSnapTarget>,
): RadarSnapTarget? = targets
    .asSequence()
    .map { target ->
        target to hypot(
            (point.x - target.position.x).toDouble(),
            (point.y - target.position.y).toDouble(),
        ).toFloat()
    }
    .filter { (target, distance) -> distance <= target.radius }
    .minByOrNull { (_, distance) -> distance }
    ?.first

internal fun routeTerminalIsAllowed(
    target: RouteTerminalTarget,
    assignedRunwayId: String?,
    isArrival: Boolean,
): Boolean = when (target) {
    is RouteTerminalTarget.AssignedRunway -> isArrival && target.runwayId == assignedRunwayId
    is RouteTerminalTarget.NavigationFix -> true
}

/** Reduces noisy finger samples while always retaining the gesture's start and end. */
internal fun simplifyFingerPath(
    points: List<NormalizedPoint>,
    tolerance: Float = .008f,
): List<NormalizedPoint> {
    if (points.size <= 2) return points.distinct()
    val toleranceSquared = tolerance * tolerance

    fun perpendicularDistanceSquared(
        point: NormalizedPoint,
        start: NormalizedPoint,
        end: NormalizedPoint,
    ): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (dx == 0f && dy == 0f) {
            val px = point.x - start.x
            val py = point.y - start.y
            return px * px + py * py
        }
        val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) /
            (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val px = point.x - (start.x + t * dx)
        val py = point.y - (start.y + t * dy)
        return px * px + py * py
    }

    fun reduce(first: Int, last: Int, keep: BooleanArray) {
        var furthest = toleranceSquared
        var furthestIndex = -1
        for (index in first + 1 until last) {
            val distance = perpendicularDistanceSquared(points[index], points[first], points[last])
            if (distance > furthest) {
                furthest = distance
                furthestIndex = index
            }
        }
        if (furthestIndex >= 0) {
            keep[furthestIndex] = true
            reduce(first, furthestIndex, keep)
            reduce(furthestIndex, last, keep)
        }
    }

    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    reduce(0, points.lastIndex, keep)
    return points.filterIndexed { index, _ -> keep[index] }
}

internal fun isRouteGestureLongEnough(
    points: List<NormalizedPoint>,
    minimumDistance: Float = .035f,
): Boolean {
    if (points.size < 2) return false
    var distance = 0.0
    points.zipWithNext().forEach { (first, second) ->
        distance += hypot((second.x - first.x).toDouble(), (second.y - first.y).toDouble())
    }
    return distance >= minimumDistance
}

/** Joins a freehand vector to the validated final without granting a landing clearance. */
internal fun composeApproachRoute(
    drawnPoints: List<NormalizedPoint>,
    finalApproachPoints: List<NormalizedPoint>,
): List<NormalizedPoint> {
    if (finalApproachPoints.isEmpty()) return drawnPoints
    val intercept = finalApproachPoints.first()
    val prefix = drawnPoints
        .dropLastWhile { point ->
            hypot(
                (point.x - intercept.x).toDouble(),
                (point.y - intercept.y).toDouble(),
            ) < .025
        }
    return (prefix + finalApproachPoints).fold(emptyList()) { result, point ->
        if (result.lastOrNull() == point) result else result + point
    }
}
