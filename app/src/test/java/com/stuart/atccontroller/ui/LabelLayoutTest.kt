package com.stuart.atccontroller.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelLayoutTest {

    @Test
    fun labelBoundsOverlaps() {
        val a = LabelBounds(0f, 0f, 10f, 10f)
        val b = LabelBounds(5f, 5f, 10f, 10f)
        val c = LabelBounds(20f, 20f, 10f, 10f)

        assertTrue("Should overlap", a.overlaps(b))
        assertTrue("Should overlap vice versa", b.overlaps(a))
        assertFalse("Should not overlap", a.overlaps(c))
    }

    @Test
    fun managerAvoidsOverlaps() {
        val manager = LabelLayoutManager(
            plotWidthPx = 1000f,
            plotHeightPx = 1000f,
            density = 1f,
        )

        // Register a static label at the default Top-Right position
        // Anchor (100, 100), Top-Right offset (13, -42), width 70, height 48
        // Bounds: (113, 58, 70, 48)
        manager.registerStaticLabel(113f, 58f, 70.dp, 48.dp)

        val anchor = Offset(100f, 100f)
        val (position, _) = manager.findBestPosition(
            anchor,
            widthDp = 70.dp,
            heightDp = 48.dp,
            scale = 1f,
        )

        val placed = LabelBounds(position.x, position.y, 70f, 48f)
        assertFalse(placed.overlaps(LabelBounds(113f, 58f, 70f, 48f), margin = 4f))
    }

    @Test
    fun managerClampsToScreen() {
        val manager = LabelLayoutManager(
            plotWidthPx = 200f,
            plotHeightPx = 200f,
            density = 1f,
        )

        // Anchor near right edge
        val anchor = Offset(190f, 100f)
        val (pos, _) = manager.findBestPosition(
            anchor,
            widthDp = 70.dp,
            heightDp = 48.dp,
            scale = 1f,
        )

        // Label width is 70. Anchor 190 + offset 13 = 203 (out of bounds)
        // It should be clamped to 200 - 70 - 2 = 128
        assertTrue("Should be clamped within width", pos.x <= 130f)
        assertTrue("Should be at least 2", pos.x >= 2f)
    }

    @Test
    fun denseScaledLabelsStayWithinSafeInset() {
        val manager = LabelLayoutManager(320f, 220f, 1f, safeInsetPx = 6f)
        val anchors = listOf(
            Offset(2f, 2f), Offset(318f, 2f), Offset(2f, 218f), Offset(318f, 218f),
            Offset(155f, 105f), Offset(160f, 110f), Offset(165f, 115f),
        )
        anchors.forEach { anchor ->
            val (position, _) = manager.findBestPosition(anchor, 70.dp, 48.dp, scale = 1.4f)
            val bounds = LabelBounds(position.x, position.y, 98f, 67.2f)
            assertTrue(bounds.inside(320f, 220f, 6f))
        }
    }

    @Test
    fun previousValidPositionGetsHysteresis() {
        val previous = Offset(100f, 80f)
        val manager = LabelLayoutManager(500f, 400f, 1f)
        val (position, _) = manager.findBestPosition(
            Offset(90f, 140f), 70.dp, 48.dp, 1f, previous = previous,
        )
        assertTrue(kotlin.math.abs(position.x - previous.x) < 1f)
        assertTrue(kotlin.math.abs(position.y - previous.y) < 1f)
    }

    @Test
    fun mapLabelsFlipToTheLeftAtTheRightEdge() {
        val position = boundedMapLabelPosition(
            anchorPx = Offset(315f, 100f),
            plotWidthPx = 320f,
            plotHeightPx = 220f,
            labelWidthPx = 96f,
            labelHeightPx = 18f,
            gapPx = 7f,
            insetPx = 6f,
        )

        assertTrue(position.x < 315f)
        assertTrue(LabelBounds(position.x, position.y, 96f, 18f).inside(320f, 220f, 6f))
    }

    @Test
    fun diagonalRunwayBadgesSitBeyondTheRunwayEnds() {
        val (threshold, farEnd) = runwayEndLabelPositions(
            plotWidth = 360f,
            plotHeight = 420f,
            center = NormalizedPoint(.5f, .55f),
            headingDegrees = 231f,
            labelWidth = 76f,
            labelHeight = 22f,
            gap = 8f,
            inset = 6f,
        )
        val center = Offset(180f, 231f)
        val thresholdCenter = threshold + Offset(38f, 11f)
        val farEndCenter = farEnd + Offset(38f, 11f)
        val runwayHalfLength = 360f * .145f

        assertTrue((thresholdCenter - center).getDistance() > runwayHalfLength)
        assertTrue((farEndCenter - center).getDistance() > runwayHalfLength)
        assertTrue(LabelBounds(threshold.x, threshold.y, 76f, 22f).inside(360f, 420f, 6f))
        assertTrue(LabelBounds(farEnd.x, farEnd.y, 76f, 22f).inside(360f, 420f, 6f))
    }
}
