package com.stuart.atccontroller.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

internal data class RadarHitRegion(
    val aircraftId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

internal fun hitTestAircraft(
    x: Float,
    y: Float,
    regions: List<RadarHitRegion>,
): String? = regions.lastOrNull { it.contains(x, y) }?.aircraftId

internal data class RadarViewport(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

internal fun mapToScreen(point: Offset, viewport: RadarViewport, viewportSize: Size): Offset {
    val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    return center + (point - center) * viewport.scale + viewport.offset
}

internal fun screenToMap(point: Offset, viewport: RadarViewport, viewportSize: Size): Offset {
    val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    return center + (point - center - viewport.offset) / viewport.scale
}

internal fun updateRadarViewport(
    current: RadarViewport,
    centroid: Offset,
    pan: Offset,
    zoomChange: Float,
    viewportSize: Size,
    minimumScale: Float = 1f,
    maximumScale: Float = 3f,
): RadarViewport {
    if (viewportSize.width <= 0f || viewportSize.height <= 0f) return current
    val newScale = (current.scale * zoomChange).coerceIn(minimumScale, maximumScale)
    if (newScale == minimumScale) return RadarViewport(minimumScale, Offset.Zero)

    val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val anchoredMapPoint = screenToMap(centroid, current, viewportSize)
    val rawOffset = centroid + pan - center - (anchoredMapPoint - center) * newScale
    val maximumOffset = Offset(
        viewportSize.width * (newScale - 1f) / 2f,
        viewportSize.height * (newScale - 1f) / 2f,
    )
    return RadarViewport(
        scale = newScale,
        offset = Offset(
            rawOffset.x.coerceIn(-maximumOffset.x, maximumOffset.x),
            rawOffset.y.coerceIn(-maximumOffset.y, maximumOffset.y),
        ),
    )
}

