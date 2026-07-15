package com.stuart.atccontroller.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

internal data class LabelBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun overlaps(other: LabelBounds, margin: Float = 0f): Boolean {
        return (x < (other.x + other.width + margin)) &&
                ((x + width + margin) > other.x) &&
                (y < (other.y + other.height + margin)) &&
                ((y + height + margin) > other.y)
    }
}

internal enum class LabelQuadrant {
    TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, TOP_LEFT
}

internal class LabelLayoutManager(
    private val plotWidthPx: Float,
    private val plotHeightPx: Float,
    private val density: Float,
) {
    private val placedLabels = mutableListOf<LabelBounds>()

    fun registerStaticLabel(xPx: Float, yPx: Float, widthDp: Dp, heightDp: Dp) {
        placedLabels.add(LabelBounds(xPx, yPx, widthDp.value * density, heightDp.value * density))
    }

    fun findBestPosition(
        anchorPx: Offset,
        widthDp: Dp,
        heightDp: Dp,
        scale: Float,
    ): Pair<Offset, LabelQuadrant> {
        val w = widthDp.value * density * scale
        val h = heightDp.value * density * scale
        
        // Preferred offsets for each quadrant relative to the anchor point
        val candidates = listOf(
            LabelQuadrant.TOP_RIGHT to Offset(13f * density, (-42f * density) - (h - (48f * density * scale))),
            LabelQuadrant.BOTTOM_RIGHT to Offset(13f * density, 10f * density),
            LabelQuadrant.BOTTOM_LEFT to Offset(-(w + (13f * density)), 10f * density),
            LabelQuadrant.TOP_LEFT to Offset(-(w + (13f * density)), -42f * density - (h - 48f * density * scale)),
        )

        var bestQuadrant = LabelQuadrant.TOP_RIGHT
        var bestOffset = candidates[0].second
        var minOverlaps = Int.MAX_VALUE

        for ((quadrant, offset) in candidates) {
            val x = (anchorPx.x + offset.x).coerceIn(2f * density, (plotWidthPx - w - (2f * density)))
            val y = (anchorPx.y + offset.y).coerceIn(2f * density, (plotHeightPx - h - (2f * density)))
            val bounds = LabelBounds(x, y, w, h)
            
            val overlaps = placedLabels.count { it.overlaps(bounds, margin = 4f * density) }
            if (overlaps == 0) {
                placedLabels.add(bounds)
                return Offset(x, y) to quadrant
            }
            if (overlaps < minOverlaps) {
                minOverlaps = overlaps
                bestQuadrant = quadrant
                bestOffset = Offset(x, y)
            }
        }

        val bounds = LabelBounds(bestOffset.x, bestOffset.y, w, h)
        placedLabels.add(bounds)
        return bestOffset to bestQuadrant
    }
}
