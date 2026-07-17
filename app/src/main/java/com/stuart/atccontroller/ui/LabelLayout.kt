package com.stuart.atccontroller.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import kotlin.math.hypot

internal data class LabelBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun overlaps(other: LabelBounds, margin: Float = 0f): Boolean =
        x < other.right + margin && right + margin > other.x &&
            y < other.bottom + margin && bottom + margin > other.y

    fun overlapArea(other: LabelBounds, margin: Float = 0f): Float {
        val overlapWidth = minOf(right + margin, other.right + margin) - maxOf(x, other.x)
        val overlapHeight = minOf(bottom + margin, other.bottom + margin) - maxOf(y, other.y)
        return maxOf(0f, overlapWidth) * maxOf(0f, overlapHeight)
    }

    fun inside(width: Float, height: Float, inset: Float): Boolean =
        x >= inset && y >= inset && right <= width - inset && bottom <= height - inset
}

internal enum class LabelQuadrant { TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, TOP_LEFT }

/** Places a map annotation beside its anchor while keeping the full label inside the radar. */
internal fun boundedMapLabelPosition(
    anchorPx: Offset,
    plotWidthPx: Float,
    plotHeightPx: Float,
    labelWidthPx: Float,
    labelHeightPx: Float,
    gapPx: Float,
    insetPx: Float,
): Offset {
    val roomOnRight = anchorPx.x + gapPx + labelWidthPx <= plotWidthPx - insetPx
    val desiredX = if (roomOnRight) {
        anchorPx.x + gapPx
    } else {
        anchorPx.x - gapPx - labelWidthPx
    }
    return Offset(
        desiredX.coerceIn(insetPx, maxOf(insetPx, plotWidthPx - labelWidthPx - insetPx)),
        (anchorPx.y - labelHeightPx / 2f)
            .coerceIn(insetPx, maxOf(insetPx, plotHeightPx - labelHeightPx - insetPx)),
    )
}

private data class LayoutObstacle(val bounds: LabelBounds, val protected: Boolean)

/** Stable, scored placement shared by every radar size and label scale. */
internal class LabelLayoutManager(
    private val plotWidthPx: Float,
    private val plotHeightPx: Float,
    private val density: Float,
    private val safeInsetPx: Float = 6f * density,
) {
    private val obstacles = mutableListOf<LayoutObstacle>()

    fun registerObstacle(bounds: LabelBounds, protected: Boolean = true) {
        obstacles += LayoutObstacle(bounds, protected)
    }

    fun registerStaticLabel(xPx: Float, yPx: Float, widthDp: Dp, heightDp: Dp) {
        registerObstacle(LabelBounds(xPx, yPx, widthDp.value * density, heightDp.value * density))
    }

    fun findBestPosition(
        anchorPx: Offset,
        widthDp: Dp,
        heightDp: Dp,
        scale: Float,
        previous: Offset? = null,
        priority: Boolean = false,
    ): Pair<Offset, LabelQuadrant> {
        val w = widthDp.value * density * maxOf(1f, scale)
        val h = heightDp.value * density * maxOf(1f, scale)
        val gap = 13f * density
        val candidates = buildList {
            listOf(0f, 12f, 26f, 42f).forEach { extra ->
                add(Offset(gap + extra, -h - gap - extra) to LabelQuadrant.TOP_RIGHT)
                add(Offset(gap + extra, gap + extra) to LabelQuadrant.BOTTOM_RIGHT)
                add(Offset(-w - gap - extra, gap + extra) to LabelQuadrant.BOTTOM_LEFT)
                add(Offset(-w - gap - extra, -h - gap - extra) to LabelQuadrant.TOP_LEFT)
                add(Offset(-w / 2f, -h - gap - extra) to LabelQuadrant.TOP_RIGHT)
                add(Offset(gap + extra, -h / 2f) to LabelQuadrant.BOTTOM_RIGHT)
                add(Offset(-w / 2f, gap + extra) to LabelQuadrant.BOTTOM_LEFT)
                add(Offset(-w - gap - extra, -h / 2f) to LabelQuadrant.TOP_LEFT)
            }
        }

        fun clamped(offset: Offset): Offset = Offset(
            (anchorPx.x + offset.x).coerceIn(safeInsetPx, maxOf(safeInsetPx, plotWidthPx - w - safeInsetPx)),
            (anchorPx.y + offset.y).coerceIn(safeInsetPx, maxOf(safeInsetPx, plotHeightPx - h - safeInsetPx)),
        )

        fun score(position: Offset): Float {
            val bounds = LabelBounds(position.x, position.y, w, h)
            var result = hypot(
                (position.x + w / 2f - anchorPx.x).toDouble(),
                (position.y + h / 2f - anchorPx.y).toDouble(),
            ).toFloat()
            obstacles.forEach { obstacle ->
                val area = bounds.overlapArea(obstacle.bounds, 4f * density)
                if (area > 0f) result += area * if (obstacle.protected || priority) 100f else 20f
            }
            previous?.let {
                result += hypot((position.x - it.x).toDouble(), (position.y - it.y).toDouble()).toFloat() * .35f
                if (hypot((position.x - it.x).toDouble(), (position.y - it.y).toDouble()) < 3f * density) {
                    result -= 30f * density
                }
            }
            return result
        }

        val previousCandidate = previous?.takeIf {
            LabelBounds(it.x, it.y, w, h).inside(plotWidthPx, plotHeightPx, safeInsetPx)
        }
        val options = buildList {
            previousCandidate?.let { add(it to quadrantFor(anchorPx, it, w, h)) }
            candidates.forEach { (offset, quadrant) -> add(clamped(offset) to quadrant) }
        }
        val chosen = options.minByOrNull { score(it.first) }
            ?: (Offset(safeInsetPx, safeInsetPx) to LabelQuadrant.TOP_RIGHT)
        registerObstacle(LabelBounds(chosen.first.x, chosen.first.y, w, h), protected = priority)
        return chosen
    }

    private fun quadrantFor(anchor: Offset, position: Offset, width: Float, height: Float): LabelQuadrant {
        val right = position.x + width / 2f >= anchor.x
        val bottom = position.y + height / 2f >= anchor.y
        return when {
            right && !bottom -> LabelQuadrant.TOP_RIGHT
            right -> LabelQuadrant.BOTTOM_RIGHT
            bottom -> LabelQuadrant.BOTTOM_LEFT
            else -> LabelQuadrant.TOP_LEFT
        }
    }
}
