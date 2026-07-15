package com.stuart.atccontroller.simulation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object Navigation {
    fun normalizeHeading(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    fun signedHeadingDifference(fromDegrees: Double, toDegrees: Double): Double {
        val difference = normalizeHeading(toDegrees) - normalizeHeading(fromDegrees)
        return when {
            difference > 180.0 -> difference - 360.0
            difference < -180.0 -> difference + 360.0
            else -> difference
        }
    }

    fun turnTowards(currentDegrees: Double, targetDegrees: Double, maximumTurnDegrees: Double): Double {
        val difference = signedHeadingDifference(currentDegrees, targetDegrees)
        return normalizeHeading(currentDegrees + difference.coerceIn(-maximumTurnDegrees, maximumTurnDegrees))
    }

    fun distanceNm(from: Vec2, to: Vec2, mapWidthNm: Double, mapHeightNm: Double): Double =
        hypot((to.x - from.x) * mapWidthNm, (to.y - from.y) * mapHeightNm)

    fun bearingDegrees(from: Vec2, to: Vec2, mapWidthNm: Double, mapHeightNm: Double): Double {
        val eastNm = (to.x - from.x) * mapWidthNm
        val northNm = (from.y - to.y) * mapHeightNm
        return normalizeHeading(Math.toDegrees(atan2(eastNm, northNm)))
    }

    fun velocityNmPerSecond(headingDegrees: Double, speedKnots: Double): Pair<Double, Double> {
        val radians = Math.toRadians(normalizeHeading(headingDegrees))
        val nmPerSecond = speedKnots / 3_600.0
        return sin(radians) * nmPerSecond to -cos(radians) * nmPerSecond
    }

    fun move(
        position: Vec2,
        headingDegrees: Double,
        distanceNm: Double,
        mapWidthNm: Double,
        mapHeightNm: Double,
    ): Vec2 {
        val radians = Math.toRadians(normalizeHeading(headingDegrees))
        val x = position.x + sin(radians) * distanceNm / mapWidthNm
        val y = position.y - cos(radians) * distanceNm / mapHeightNm
        return Vec2(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0))
    }

    /** Perpendicular distance from [point] to the infinite centreline through the runway. */
    fun distanceToRunwayCentrelineNm(
        point: Vec2,
        runway: RunwayState,
        mapWidthNm: Double,
        mapHeightNm: Double,
    ): Double {
        val lineX = (runway.end.x - runway.threshold.x) * mapWidthNm
        val lineY = (runway.end.y - runway.threshold.y) * mapHeightNm
        val pointX = (point.x - runway.threshold.x) * mapWidthNm
        val pointY = (point.y - runway.threshold.y) * mapHeightNm
        val length = hypot(lineX, lineY)
        if (length == 0.0) return hypot(pointX, pointY)
        return abs(lineX * pointY - lineY * pointX) / length
    }

    /**
     * Signed distance along the runway axis from its threshold. Negative values are on the
     * approach side; positive values are beyond the threshold in the landing direction.
     */
    fun signedDistanceAlongRunwayNm(
        point: Vec2,
        runway: RunwayState,
        mapWidthNm: Double,
        mapHeightNm: Double,
    ): Double {
        val runwayX = (runway.end.x - runway.threshold.x) * mapWidthNm
        val runwayY = (runway.end.y - runway.threshold.y) * mapHeightNm
        val runwayLength = hypot(runwayX, runwayY)
        if (runwayLength == 0.0) return 0.0
        val pointX = (point.x - runway.threshold.x) * mapWidthNm
        val pointY = (point.y - runway.threshold.y) * mapHeightNm
        return (pointX * runwayX + pointY * runwayY) / runwayLength
    }

    /** True when the current ground-track velocity has a positive component toward [target]. */
    fun isMovingToward(
        position: Vec2,
        target: Vec2,
        headingDegrees: Double,
        speedKnots: Double,
        mapWidthNm: Double,
        mapHeightNm: Double,
    ): Boolean {
        val targetX = (target.x - position.x) * mapWidthNm
        val targetY = (target.y - position.y) * mapHeightNm
        if (hypot(targetX, targetY) == 0.0 || speedKnots <= 0.0) return false
        val velocity = velocityNmPerSecond(headingDegrees, speedKnots)
        return targetX * velocity.first + targetY * velocity.second > 0.0
    }
}
