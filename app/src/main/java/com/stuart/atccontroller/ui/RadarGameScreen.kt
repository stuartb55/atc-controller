package com.stuart.atccontroller.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stuart.atccontroller.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GameScreen(state: GameUiState, onAction: (GameAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 800.dp || maxHeight < 430.dp
        val stacked = maxWidth < 600.dp && maxHeight >= 520.dp
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 12.dp)) {
                GameStatusBar(state, compact, onAction)
                Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
                if (stacked) {
                    RadarDisplay(
                        state = state,
                        onSelectAircraft = { onAction(GameAction.SelectAircraft(it)) },
                        onCommitRoute = { onAction(GameAction.CommitRoute(it)) },
                        onCycleConflict = { onAction(GameAction.CycleConflict(it)) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    CommandPanel(
                        state = state,
                        onAction = onAction,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 300.dp),
                    )
                } else {
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    ) {
                        RadarDisplay(
                            state = state,
                            onSelectAircraft = { onAction(GameAction.SelectAircraft(it)) },
                            onCommitRoute = { onAction(GameAction.CommitRoute(it)) },
                            onCycleConflict = { onAction(GameAction.CycleConflict(it)) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        CommandPanel(
                            state = state,
                            onAction = onAction,
                            compact = compact,
                            modifier = Modifier
                                .width(if (compact) 228.dp else 290.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
            }

            if (state.isPaused) {
                PauseOverlay(state, onAction)
            } else if (state.tutorialStep != null) {
                TutorialOverlay(state.tutorialStep, onAction, compact)
            }
        }
    }
}

@Composable
private fun GameStatusBar(state: GameUiState, compact: Boolean, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    val leaveDescription = stringResource(R.string.cd_leave_shift)
    val pauseDescription = stringResource(R.string.cd_pause_simulation)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 650.dp
        val leaveButton: @Composable () -> Unit = {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = leaveDescription
                    }
                    .clickable { onAction(GameAction.Navigate(AppScreen.HOME)) },
                color = colors.panel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("‹", color = colors.green, fontSize = 27.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        val title: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.approach_header,
                        state.visibleRunways.joinToString(" / ") { it.id }
                            .ifBlank { stringResource(R.string.not_available_short) },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                    maxLines = 1,
                )
                Text(
                    state.selectedMission?.title ?: stringResource(R.string.active_sector),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.white,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val pauseButton: @Composable () -> Unit = {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = pauseDescription
                    }
                    .clickable { onAction(GameAction.TogglePause) },
                color = colors.panel,
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, colors.line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Ⅱ", style = MaterialTheme.typography.titleMedium, color = colors.green)
                }
            }
        }

        if (narrow) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leaveButton()
                    Box(Modifier.weight(1f)) { title() }
                    pauseButton()
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DataPill(stringResource(R.string.time), formatElapsed(state.elapsedSeconds), Modifier.weight(1f))
                    DataPill(stringResource(R.string.score), localizedInteger(state.score), Modifier.weight(1f), colors.amber)
                    StrikeIndicator(state.strikes)
                    TimeControl(state.timeScale, onAction)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(if (compact) 48.dp else 50.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leaveButton()
                Box(Modifier.weight(1f)) { title() }
                if (!compact) DataPill(stringResource(R.string.wind), state.runway.wind, accent = colors.cyan)
                DataPill(stringResource(R.string.time), formatElapsed(state.elapsedSeconds))
                DataPill(stringResource(R.string.score), localizedInteger(state.score), accent = colors.amber)
                StrikeIndicator(state.strikes)
                TimeControl(state.timeScale, onAction)
                pauseButton()
            }
        }
    }
}

@Composable
private fun StrikeIndicator(strikes: Int) {
    val colors = MaterialTheme.atcColors
    val description = stringResource(R.string.cd_separation_strikes, strikes)
    Surface(
        color = if (strikes > 0) colors.red.copy(alpha = .12f) else colors.panel,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (strikes > 0) colors.red.copy(alpha = .6f) else colors.line),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (index < strikes) colors.red else colors.line, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TimeControl(timeScale: Int, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.panel,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(1, 2).forEach { value ->
                val speedDescription = stringResource(R.string.cd_simulation_speed, value)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (timeScale == value) colors.green else Color.Transparent)
                        .semantics {
                            role = Role.Button
                            contentDescription = speedDescription
                        }
                        .clickable { onAction(GameAction.SetTimeScale(value)) }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.simulation_speed_value, value),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (timeScale == value) colors.night else colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarDisplay(
    state: GameUiState,
    onSelectAircraft: (String?) -> Unit,
    onCommitRoute: (List<NormalizedPoint>) -> Unit,
    onCycleConflict: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    var draftRoute by remember(state.selectedAircraftId) { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    val latestAircraft by rememberUpdatedState(state.aircraft)
    val latestSelectedAircraft by rememberUpdatedState(state.selectedAircraft)
    val hitRadiusPx = with(LocalDensity.current) { 48.dp.toPx() }
    val radarDescription = stringResource(R.string.cd_terminal_radar)
    val directFix = state.selectedAircraft?.takeIf { it.phase == FlightPhase.DEPARTURE }?.let { selected ->
        state.fixes.minByOrNull { fix ->
            hypot(
                (fix.position.x - selected.position.x).toDouble(),
                (fix.position.y - selected.position.y).toDouble(),
            )
        }
    }
    val directActionLabel = directFix?.let { stringResource(R.string.action_direct_to_fix, it.name) }
    val aircraftActionLabels = state.aircraft.associate { aircraft ->
        aircraft.id to stringResource(R.string.action_select_aircraft, aircraft.callsign)
    }

    Surface(
        modifier = modifier,
        color = colors.radar,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.line),
        shadowElevation = 8.dp,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = radarDescription
                    customActions = buildList {
                        if (directFix != null && directActionLabel != null) {
                            add(
                                CustomAccessibilityAction(directActionLabel) {
                                    onCommitRoute(listOf(directFix.position))
                                    true
                                },
                            )
                        }
                        state.aircraft.forEach { aircraft ->
                            add(
                                CustomAccessibilityAction(
                                    aircraftActionLabels.getValue(aircraft.id),
                                ) {
                                    onSelectAircraft(aircraft.id)
                                    true
                                },
                            )
                        }
                    }
                },
        ) {
            val plotWidth = maxWidth
            val plotHeight = maxHeight
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Keep the gesture detector stable while the 10 Hz simulation updates positions.
                    // Restarting it for every snapshot can cancel an in-progress tap or drag.
                    .pointerInput(hitRadiusPx) {
                        detectTapGestures { tap ->
                            val nearest = latestAircraft.minByOrNull { aircraft ->
                                val dx = tap.x - aircraft.position.x * size.width
                                val dy = tap.y - aircraft.position.y * size.height
                                dx * dx + dy * dy
                            }
                            val selected = nearest?.takeIf {
                                val dx = tap.x - it.position.x * size.width
                                val dy = tap.y - it.position.y * size.height
                                dx * dx + dy * dy <= hitRadiusPx * hitRadiusPx
                            }
                            onSelectAircraft(selected?.id)
                        }
                    }
                    .pointerInput(state.selectedAircraftId) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val selected = latestSelectedAircraft ?: return@detectDragGestures
                                draftRoute = listOf(
                                    selected.position,
                                    NormalizedPoint(start.x / size.width, start.y / size.height).clamped(),
                                )
                            },
                            onDrag = { change, _ ->
                                if (state.selectedAircraftId != null) {
                                    val next = NormalizedPoint(
                                        change.position.x / size.width,
                                        change.position.y / size.height,
                                    ).clamped()
                                    val previous = draftRoute.lastOrNull()
                                    if (previous == null || hypot((next.x - previous.x).toDouble(), (next.y - previous.y).toDouble()) > .018) {
                                        draftRoute = draftRoute + next
                                    }
                                }
                            },
                            onDragEnd = {
                                if (draftRoute.size > 2) onCommitRoute(draftRoute.drop(1))
                                draftRoute = emptyList()
                            },
                            onDragCancel = { draftRoute = emptyList() },
                        )
                    },
            ) {
                drawRadarGrid(colors)
                drawAirport(state.visibleRunways, colors)
                drawFixes(state.fixes, colors)
                if (state.settings.trailsEnabled) drawTrails(state.aircraft, colors)
                drawRoutes(state.aircraft, state.selectedAircraftId, colors)
                if (draftRoute.isNotEmpty()) {
                    drawPolyline(
                        points = draftRoute,
                        color = colors.cyan,
                        width = 3.5f,
                        dashed = true,
                    )
                    draftRoute.forEach { point -> drawCircle(colors.cyan, 3.2f, point.toOffset(size)) }
                }
                drawConflicts(state, colors)
                drawAircraft(state.aircraft, state.selectedAircraftId, colors)
            }

            state.fixes.forEach { fix ->
                val x = (plotWidth * fix.position.x + 7.dp).coerceIn(2.dp, plotWidth - 48.dp)
                val y = (plotHeight * fix.position.y - 8.dp).coerceIn(2.dp, plotHeight - 18.dp)
                Text(
                    text = fix.name,
                    modifier = Modifier.offset(x, y),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fix.kind == FixKind.APPROACH) colors.cyan else colors.greenDim,
                    fontSize = 8.sp,
                )
            }

            state.visibleRunways.forEach { runway ->
                RunwayEndLabels(
                    runway = runway,
                    plotWidth = plotWidth,
                    plotHeight = plotHeight,
                )
            }

            CompassReference(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )

            state.aircraft.forEach { aircraft ->
                val labelX = (plotWidth * aircraft.position.x + 13.dp).coerceIn(2.dp, plotWidth - 98.dp)
                val labelY = (plotHeight * aircraft.position.y - 42.dp).coerceIn(2.dp, plotHeight - 52.dp)
                AircraftDataLabel(
                    aircraft = aircraft,
                    selected = aircraft.id == state.selectedAircraftId,
                    decluttered = state.settings.labelDeclutteringEnabled &&
                        aircraft.id != state.selectedAircraftId &&
                        aircraft.conflictLevel == ConflictLevel.NONE,
                    scale = state.settings.labelScale,
                    onClick = { onSelectAircraft(aircraft.id) },
                    modifier = Modifier.offset(labelX, labelY),
                )
            }

            state.activeConflict?.let { conflict ->
                ConflictBanner(
                    conflict = conflict,
                    position = state.activeConflictIndex + 1,
                    total = state.conflicts.size,
                    onPrevious = { onCycleConflict(-1) },
                    onNext = { onCycleConflict(1) },
                    onSelectAircraft = onSelectAircraft,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
            }

            state.conflictAnnouncement?.let { announcement ->
                ConflictLiveAnnouncement(
                    announcement = announcement,
                    modifier = Modifier.align(Alignment.TopStart).size(1.dp),
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(9.dp),
                color = colors.night.copy(alpha = .82f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.line.copy(alpha = .7f)),
            ) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TinyPlane(Modifier.size(16.dp), colors.cyan)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.selectedAircraftId == null) {
                            stringResource(R.string.radar_hint_select).uppercase()
                        } else {
                            stringResource(R.string.radar_hint_route).uppercase()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRadarGrid(colors: AtcPalette) {
    drawRect(
        brush = Brush.radialGradient(
            listOf(colors.radarGlow, colors.radar),
            center = Offset(size.width * .5f, size.height * .52f),
            radius = size.maxDimension * .7f,
        ),
    )
    val center = Offset(size.width * .5f, size.height * .52f)
    val radiusStep = size.minDimension * .13f
    repeat(5) { index ->
        drawCircle(
            colors.green.copy(alpha = .11f),
            radiusStep * (index + 1),
            center,
            style = Stroke(if (index == 3) 1.6f else 1f),
        )
    }
    repeat(12) { index ->
        val radians = Math.toRadians((index * 30.0) - 90.0)
        val edge = Offset(
            center.x + cos(radians).toFloat() * size.maxDimension,
            center.y + sin(radians).toFloat() * size.maxDimension,
        )
        drawLine(colors.green.copy(alpha = .075f), center, edge, 1f)
    }
    val grid = size.minDimension / 8f
    var x = center.x % grid
    while (x <= size.width) {
        drawLine(colors.line.copy(alpha = .17f), Offset(x, 0f), Offset(x, size.height), .8f)
        x += grid
    }
    var y = center.y % grid
    while (y <= size.height) {
        drawLine(colors.line.copy(alpha = .17f), Offset(0f, y), Offset(size.width, y), .8f)
        y += grid
    }
    drawCircle(colors.green.copy(alpha = .55f), 3f, center)
    drawArc(
        color = colors.green.copy(alpha = .08f),
        startAngle = -78f,
        sweepAngle = 28f,
        useCenter = true,
        topLeft = Offset(center.x - radiusStep * 5, center.y - radiusStep * 5),
        size = Size(radiusStep * 10, radiusStep * 10),
    )
}

private fun DrawScope.drawAirport(runways: List<RunwayUiModel>, colors: AtcPalette) {
    val runwayLength = size.minDimension * .29f
    runways.forEach { runway ->
        val center = runway.center.toOffset(size)
        val radians = Math.toRadians(runway.headingDegrees.toDouble())
        val axis = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
        val perp = Offset(-axis.y, axis.x)
        drawRunwayBody(center, axis, runwayLength, runway.isOccupied, alpha = .84f, colors = colors)

        // Final approach extends behind the landing threshold, opposite the direction of travel.
        val finalEnd = center - axis * (runwayLength * 2.2f)
        drawLine(
            colors.cyan.copy(alpha = .38f),
            center - axis * (runwayLength / 2f),
            finalEnd,
            1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
        repeat(5) { index ->
            val mark = center - axis * (runwayLength * (.75f + index * .27f))
            drawLine(colors.cyan.copy(alpha = .28f), mark - perp * 5f, mark + perp * 5f, 1f)
        }
    }

    // Early missions activate one runway, but retain the airport's second physical runway as
    // faint context. Parallel-operations missions supply both exact runway models above.
    if (runways.size == 1) {
        val runway = runways.single()
        val center = runway.center.toOffset(size)
        val radians = Math.toRadians(runway.headingDegrees.toDouble())
        val axis = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
        val perp = Offset(-axis.y, axis.x)
        drawRunwayBody(center + perp * 13f, axis, runwayLength, occupied = false, alpha = .42f, colors = colors)
    }
}

private fun DrawScope.drawRunwayBody(
    center: Offset,
    axis: Offset,
    runwayLength: Float,
    occupied: Boolean,
    alpha: Float,
    colors: AtcPalette,
) {
    val start = center - axis * (runwayLength / 2f)
    val end = center + axis * (runwayLength / 2f)
    drawLine(
        color = if (occupied) colors.amber else colors.white.copy(alpha = alpha),
        start = start,
        end = end,
        strokeWidth = if (alpha > .5f) 7f else 5f,
        cap = StrokeCap.Butt,
    )
    drawLine(
        color = colors.night.copy(alpha = .85f),
        start = start,
        end = end,
        strokeWidth = 1.2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f)),
    )
}

private fun DrawScope.drawFixes(fixes: List<FixUiModel>, colors: AtcPalette) {
    fixes.forEach { fix ->
        val p = fix.position.toOffset(size)
        val color = if (fix.kind == FixKind.APPROACH) colors.cyan else colors.greenDim
        val path = Path().apply {
            moveTo(p.x, p.y - 5f)
            lineTo(p.x + 4.5f, p.y + 4f)
            lineTo(p.x - 4.5f, p.y + 4f)
            close()
        }
        drawPath(path, color, style = Stroke(1.4f))
    }
}

private fun DrawScope.drawTrails(aircraft: List<AircraftUiModel>, colors: AtcPalette) {
    aircraft.forEach { item ->
        if (item.trail.isEmpty()) return@forEach
        val points = item.trail + item.position
        points.zipWithNext().forEachIndexed { index, pair ->
            drawLine(
                colors.green.copy(alpha = .10f + .10f * index / points.size),
                pair.first.toOffset(size),
                pair.second.toOffset(size),
                2f,
            )
        }
        item.trail.forEachIndexed { index, point ->
            drawCircle(colors.green.copy(alpha = .12f + .06f * index), 2f, point.toOffset(size))
        }
    }
}

private fun DrawScope.drawRoutes(aircraft: List<AircraftUiModel>, selectedId: String?, colors: AtcPalette) {
    aircraft.filter { it.route.isNotEmpty() }.forEach { item ->
        val selected = item.id == selectedId
        drawPolyline(
            points = listOf(item.position) + item.route,
            color = if (selected) colors.cyan else colors.greenDim.copy(alpha = .55f),
            width = if (selected) 2.8f else 1.4f,
            dashed = !selected,
        )
        if (selected) {
            item.route.forEach { drawCircle(colors.cyan.copy(alpha = .9f), 3.2f, it.toOffset(size)) }
        }
    }
}

private fun DrawScope.drawPolyline(points: List<NormalizedPoint>, color: Color, width: Float, dashed: Boolean) {
    if (points.size < 2) return
    val path = Path().apply {
        val first = points.first().toOffset(size)
        moveTo(first.x, first.y)
        points.drop(1).forEach {
            val point = it.toOffset(size)
            lineTo(point.x, point.y)
        }
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(9f, 7f)) else null,
        ),
    )
}

private fun DrawScope.drawConflicts(state: GameUiState, colors: AtcPalette) {
    state.conflicts.forEach { conflict ->
        val first = state.aircraft.firstOrNull { it.id == conflict.firstAircraftId } ?: return@forEach
        val second = state.aircraft.firstOrNull { it.id == conflict.secondAircraftId } ?: return@forEach
        val firstPoint = first.position.toOffset(size)
        val secondPoint = second.position.toOffset(size)
        val color = if (conflict.isLossOfSeparation) colors.red else colors.amber
        if (conflict.isLossOfSeparation) {
            val dx = secondPoint.x - firstPoint.x
            val dy = secondPoint.y - firstPoint.y
            val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val offset = Offset(-dy / length * 2.5f, dx / length * 2.5f)
            drawLine(color, firstPoint + offset, secondPoint + offset, 2.2f)
            drawLine(color, firstPoint - offset, secondPoint - offset, 2.2f)
            drawLossMarker(firstPoint, color)
            drawLossMarker(secondPoint, color)
        } else {
            drawLine(
                color.copy(alpha = .82f),
                firstPoint,
                secondPoint,
                2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f)),
            )
            drawCircle(
                color,
                22f,
                firstPoint,
                style = Stroke(1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
            )
            drawCircle(
                color,
                22f,
                secondPoint,
                style = Stroke(1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
            )
        }
    }
}

private fun DrawScope.drawLossMarker(center: Offset, color: Color) {
    val radius = 23f
    val diamond = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    drawPath(diamond, color.copy(alpha = .14f))
    drawPath(diamond, color, style = Stroke(2.2f))
    drawLine(color, center + Offset(-7f, -7f), center + Offset(7f, 7f), 2f)
    drawLine(color, center + Offset(-7f, 7f), center + Offset(7f, -7f), 2f)
}

private fun DrawScope.drawAircraft(aircraft: List<AircraftUiModel>, selectedId: String?, colors: AtcPalette) {
    aircraft.forEach { item ->
        val p = item.position.toOffset(size)
        val color = when (item.conflictLevel) {
            ConflictLevel.NONE -> if (item.id == selectedId) colors.cyan else colors.green
            ConflictLevel.PREDICTED -> colors.amber
            ConflictLevel.LOSS -> colors.red
        }
        if (item.id == selectedId) {
            drawCircle(color.copy(alpha = .12f), 18f, p)
            drawCircle(color.copy(alpha = .8f), 15f, p, style = Stroke(1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
        }
        rotate(item.headingDegrees, pivot = p) {
            // A short leader ahead of the target makes the current track easier to compare with
            // a runway centreline than the aircraft glyph alone.
            drawLine(
                color = color.copy(alpha = if (item.id == selectedId) .9f else .58f),
                start = p + Offset(0f, -10f),
                end = p + Offset(0f, if (item.id == selectedId) -29f else -22f),
                strokeWidth = if (item.id == selectedId) 1.8f else 1.2f,
                cap = StrokeCap.Round,
            )
            val shape = Path().apply {
                moveTo(p.x, p.y - 9f)
                lineTo(p.x + 6.5f, p.y + 7f)
                lineTo(p.x, p.y + 4f)
                lineTo(p.x - 6.5f, p.y + 7f)
                close()
            }
            drawPath(shape, color)
        }
        drawLine(color.copy(alpha = .6f), p + Offset(8f, -8f), p + Offset(13f, -13f), 1f)
    }
}

@Composable
private fun AircraftDataLabel(
    aircraft: AircraftUiModel,
    selected: Boolean,
    decluttered: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    val baseDescription = stringResource(
        R.string.cd_aircraft_target,
        aircraft.callsign,
        normalizedHeading(aircraft.headingDegrees),
        localizedInteger(aircraft.altitudeFeet),
        localizedInteger(aircraft.speedKnots),
        aircraft.clearance,
    )
    val conflictDescription = when (aircraft.conflictLevel) {
        ConflictLevel.NONE -> null
        ConflictLevel.PREDICTED -> stringResource(R.string.predicted_conflict)
        ConflictLevel.LOSS -> stringResource(R.string.loss_of_separation)
    }
    val aircraftDescription = listOfNotNull(baseDescription, conflictDescription).joinToString(". ")
    val accent = when (aircraft.conflictLevel) {
        ConflictLevel.NONE -> if (selected) colors.cyan else colors.green
        ConflictLevel.PREDICTED -> colors.amber
        ConflictLevel.LOSS -> colors.red
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .sizeIn(minWidth = 70.dp, minHeight = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Button,
            )
            .semantics {
                contentDescription = aircraftDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .shadow(if (selected) 8.dp else 0.dp, RoundedCornerShape(5.dp)),
            shape = RoundedCornerShape(5.dp),
            color = if (selected) colors.selectedDataLabel else colors.dataLabel,
            border = BorderStroke(if (selected) 1.5.dp else 1.dp, accent.copy(alpha = if (selected) 1f else .68f)),
        ) {
            Column(
                Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .then(if (decluttered) Modifier else Modifier.widthIn(min = 70.dp)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (aircraft.conflictLevel) {
                        ConflictLevel.PREDICTED -> Text("△", color = accent, fontSize = 9.sp)
                        ConflictLevel.LOSS -> Text("◆", color = accent, fontSize = 9.sp)
                        ConflictLevel.NONE -> Unit
                    }
                    Text(aircraft.callsign, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1)
                    if (!decluttered) {
                        Spacer(Modifier.weight(1f))
                        Text(aircraft.type, style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    }
                }
                if (!decluttered) {
                    Text(
                        stringResource(
                            R.string.aircraft_data_line,
                            stringResource(R.string.flight_level_code, aircraft.altitudeFeet / 100),
                            altitudeTrend(aircraft),
                            aircraft.speedKnots,
                            normalizedHeading(aircraft.headingDegrees),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.white,
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        stringResource(
                            R.string.aircraft_heading_compact,
                            normalizedHeading(aircraft.headingDegrees),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.white,
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunwayEndLabels(
    runway: RunwayUiModel,
    plotWidth: Dp,
    plotHeight: Dp,
) {
    val radians = Math.toRadians(runway.headingDegrees.toDouble())
    val axisX = sin(radians).toFloat()
    val axisY = -cos(radians).toFloat()
    val halfLength = minOf(plotWidth, plotHeight) * .145f
    val badgeGap = 16.dp
    val labelWidth = 76.dp
    val labelHeight = 22.dp

    // The model's threshold is the start of travel along the runway heading.
    val thresholdX = (plotWidth * runway.center.x - (halfLength + badgeGap) * axisX - labelWidth / 2)
        .coerceIn(2.dp, (plotWidth - labelWidth - 2.dp).coerceAtLeast(2.dp))
    val thresholdY = (plotHeight * runway.center.y - (halfLength + badgeGap) * axisY - labelHeight / 2)
        .coerceIn(2.dp, (plotHeight - labelHeight - 2.dp).coerceAtLeast(2.dp))
    val farEndX = (plotWidth * runway.center.x + (halfLength + badgeGap) * axisX - labelWidth / 2)
        .coerceIn(2.dp, (plotWidth - labelWidth - 2.dp).coerceAtLeast(2.dp))
    val farEndY = (plotHeight * runway.center.y + (halfLength + badgeGap) * axisY - labelHeight / 2)
        .coerceIn(2.dp, (plotHeight - labelHeight - 2.dp).coerceAtLeast(2.dp))

    RunwayBadge(
        text = stringResource(
            R.string.runway_heading_label,
            runway.id,
            normalizedHeading(runway.headingDegrees),
        ),
        active = true,
        modifier = Modifier.offset(thresholdX, thresholdY),
    )
    RunwayBadge(
        text = reciprocalRunwayId(runway.id),
        active = false,
        modifier = Modifier.offset(farEndX, farEndY),
    )
}

@Composable
private fun RunwayBadge(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.atcColors
    Surface(
        modifier = modifier.width(76.dp),
        color = colors.night.copy(alpha = if (active) .92f else .76f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (active) colors.cyan else colors.line),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            color = if (active) colors.cyan else colors.muted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (active) 8.sp else 7.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompassReference(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.atcColors
    Surface(
        modifier = modifier,
        color = colors.night.copy(alpha = .78f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, colors.line.copy(alpha = .8f)),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.compass_north), color = colors.green, fontFamily = FontFamily.Monospace, fontSize = 7.sp)
            Text(stringResource(R.string.compass_east_west), color = colors.muted, fontFamily = FontFamily.Monospace, fontSize = 6.sp)
            Text(stringResource(R.string.compass_south), color = colors.muted, fontFamily = FontFamily.Monospace, fontSize = 7.sp)
        }
    }
}

internal fun normalizedHeading(headingDegrees: Float): Int =
    ((headingDegrees.roundToInt() % 360) + 360) % 360

internal fun reciprocalRunwayId(runwayId: String): String {
    val match = Regex("^(\\d{2})([LRC]?)$").matchEntire(runwayId.uppercase(Locale.UK))
        ?: return runwayId
    val runwayNumber = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..36 }
        ?: return runwayId
    val reciprocalNumber = ((runwayNumber + 17) % 36) + 1
    val reciprocalSide = when (match.groupValues[2]) {
        "L" -> "R"
        "R" -> "L"
        else -> match.groupValues[2]
    }
    return String.format(Locale.UK, "%02d%s", reciprocalNumber, reciprocalSide)
}

@Composable
private fun ConflictBanner(
    conflict: ConflictUiModel,
    position: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectAircraft: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    val color = if (conflict.isLossOfSeparation) colors.red else colors.amber
    val status = stringResource(
        if (conflict.isLossOfSeparation) R.string.loss_of_separation else R.string.predicted_conflict,
    )
    val description = if (conflict.isLossOfSeparation) {
        stringResource(
            R.string.cd_loss_of_separation,
            conflict.firstAircraftCallsign,
            conflict.secondAircraftCallsign,
        )
    } else {
        pluralStringResource(
            R.plurals.cd_conflict_warning,
            conflict.secondsToConflict,
            conflict.firstAircraftCallsign,
            conflict.secondAircraftCallsign,
            conflict.secondsToConflict,
        )
    }
    Surface(
        modifier = modifier.semantics {
            contentDescription = description
        },
        color = colors.night.copy(alpha = .94f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (conflict.isLossOfSeparation) "◆" else "△", color = color, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(status.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
                if (total > 1) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(R.string.conflict_pair_position, position, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                    Spacer(Modifier.width(4.dp))
                    ConflictCycleButton("‹", stringResource(R.string.previous_conflict), onPrevious)
                    ConflictCycleButton("›", stringResource(R.string.next_conflict), onNext)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConflictAircraftButton(
                    callsign = conflict.firstAircraftCallsign,
                    color = color,
                    onClick = { onSelectAircraft(conflict.firstAircraftId) },
                )
                Text("/", color = colors.muted)
                ConflictAircraftButton(
                    callsign = conflict.secondAircraftCallsign,
                    color = color,
                    onClick = { onSelectAircraft(conflict.secondAircraftId) },
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (conflict.isLossOfSeparation) {
                        stringResource(R.string.separation_lost_short)
                    } else {
                        stringResource(R.string.conflict_time_short, conflict.secondsToConflict)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.white,
                )
            }
        }
    }
}

@Composable
private fun ConflictCycleButton(label: String, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.atcColors
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.green, fontSize = 20.sp)
    }
}

@Composable
private fun ConflictAircraftButton(callsign: String, color: Color, onClick: () -> Unit) {
    val description = stringResource(R.string.select_aircraft_callsign, callsign)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(callsign, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ConflictLiveAnnouncement(
    announcement: ConflictAnnouncementUiModel,
    modifier: Modifier = Modifier,
) {
    val description = if (announcement.isLossOfSeparation) {
        stringResource(
            R.string.cd_loss_of_separation,
            announcement.firstAircraftCallsign,
            announcement.secondAircraftCallsign,
        )
    } else {
        pluralStringResource(
            R.plurals.cd_conflict_warning,
            announcement.secondsToConflict,
            announcement.firstAircraftCallsign,
            announcement.secondAircraftCallsign,
            announcement.secondsToConflict,
        )
    }
    Box(
        modifier.semantics {
            contentDescription = description
            liveRegion = if (announcement.isLossOfSeparation) {
                LiveRegionMode.Assertive
            } else {
                LiveRegionMode.Polite
            }
        },
    )
}

@Composable
private fun CommandPanel(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    Surface(
        modifier = modifier,
        color = colors.panel.copy(alpha = .96f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.line),
    ) {
        val selected = state.selectedAircraft
        if (selected == null) {
            EmptySelection(state.aircraft.size)
        } else {
            val directFix = state.fixes.takeIf { selected.phase == FlightPhase.DEPARTURE }?.minByOrNull { fix ->
                hypot(
                    (fix.position.x - selected.position.x).toDouble(),
                    (fix.position.y - selected.position.y).toDouble(),
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(if (compact) 12.dp else 16.dp),
            ) {
                FlightStrip(selected, onDeselect = { onAction(GameAction.SelectAircraft(null)) })
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                directFix?.let { fix ->
                    SectionLabel(stringResource(R.string.route_shortcut))
                    Spacer(Modifier.height(7.dp))
                    SecondaryActionButton(
                        text = stringResource(R.string.action_direct_to_fix, fix.name),
                        onClick = { onAction(GameAction.CommitRoute(listOf(fix.position))) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                }
                SectionLabel(stringResource(R.string.vector_controls))
                Spacer(Modifier.height(7.dp))
                StepControl(
                    label = stringResource(R.string.target_altitude),
                    value = stringResource(R.string.flight_level_value, selected.targetAltitudeFeet / 100),
                    detail = stringResource(R.string.feet_value, localizedInteger(selected.targetAltitudeFeet)),
                    onDecrease = { onAction(GameAction.SetTargetAltitude(selected.targetAltitudeFeet - 500)) },
                    onIncrease = { onAction(GameAction.SetTargetAltitude(selected.targetAltitudeFeet + 500)) },
                )
                Spacer(Modifier.height(7.dp))
                StepControl(
                    label = stringResource(R.string.target_speed),
                    value = stringResource(R.string.knots_value, selected.targetSpeedKnots),
                    detail = stringResource(R.string.indicated_airspeed_short),
                    onDecrease = { onAction(GameAction.SetTargetSpeed(selected.targetSpeedKnots - 10)) },
                    onIncrease = { onAction(GameAction.SetTargetSpeed(selected.targetSpeedKnots + 10)) },
                )
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                SectionLabel(stringResource(R.string.clearance))
                Spacer(Modifier.height(7.dp))
                if (selected.phase == FlightPhase.DEPARTURE) {
                    ClearanceButton(
                        label = stringResource(R.string.clear_takeoff),
                        caption = selected.assignedRunway
                            ?: state.visibleRunways.firstOrNull()?.id
                            ?: stringResource(R.string.not_available_short),
                        accent = colors.green,
                        onClick = { onAction(GameAction.IssueClearance(ClearanceType.TAKE_OFF)) },
                    )
                } else {
                    ClearanceButton(
                        label = stringResource(R.string.prepare_approach),
                        caption = stringResource(
                            R.string.prepare_approach_caption,
                            selected.assignedRunway
                                ?: state.visibleRunways.firstOrNull()?.id
                                ?: stringResource(R.string.not_available_short),
                        ),
                        accent = colors.cyan,
                        onClick = { onAction(GameAction.PrepareApproach) },
                    )
                    Spacer(Modifier.height(6.dp))
                    ClearanceButton(
                        label = stringResource(R.string.clear_land),
                        caption = selected.assignedRunway
                            ?: state.visibleRunways.firstOrNull()?.id
                            ?: stringResource(R.string.not_available_short),
                        accent = colors.green,
                        onClick = { onAction(GameAction.IssueClearance(ClearanceType.LAND)) },
                    )
                    Spacer(Modifier.height(6.dp))
                    ClearanceButton(
                        label = stringResource(R.string.go_around),
                        caption = stringResource(R.string.climb_feet, localizedInteger(3_000)),
                        accent = colors.amber,
                        onClick = { onAction(GameAction.IssueClearance(ClearanceType.GO_AROUND)) },
                    )
                }
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                FuelStatus(selected.fuelPercent)
                Spacer(Modifier.height(9.dp))
                TextButton(
                    onClick = { onAction(GameAction.CompleteMission) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.muted),
                ) {
                    Text(stringResource(R.string.end_shift_debrief).uppercase(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EmptySelection(aircraftCount: Int) {
    val colors = MaterialTheme.atcColors
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RadarMark(64.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_aircraft_selected).uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.white)
        Spacer(Modifier.height(6.dp))
        Text(
            pluralStringResource(R.plurals.select_aircraft_help, aircraftCount, aircraftCount),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FlightStrip(aircraft: AircraftUiModel, onDeselect: () -> Unit) {
    val colors = MaterialTheme.atcColors
    val closeDescription = stringResource(R.string.cd_close_aircraft_strip)
    Surface(
        color = colors.panelRaised,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.greenDim),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TinyPlane(color = if (aircraft.phase == FlightPhase.DEPARTURE) colors.cyan else colors.green)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(aircraft.callsign, style = MaterialTheme.typography.titleLarge, color = colors.white, fontFamily = FontFamily.Monospace)
                    Text(stringResource(R.string.aircraft_type_phase, aircraft.type, phaseLabel(aircraft.phase).uppercase()), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .clickable(onClick = onDeselect)
                        .semantics { contentDescription = closeDescription },
                    shape = CircleShape,
                    color = colors.panel,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("×", color = colors.muted, fontSize = 16.sp) }
                }
            }
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(8.dp))
            Row {
                StripDatum(stringResource(R.string.altitude_short), stringResource(R.string.flight_level_short, aircraft.altitudeFeet / 100), Modifier.weight(1f))
                StripDatum(stringResource(R.string.speed_short), stringResource(R.string.knots_value, aircraft.speedKnots), Modifier.weight(1f))
                StripDatum(stringResource(R.string.heading_short), stringResource(R.string.heading_value, aircraft.headingDegrees.toInt()), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(aircraft.clearance, style = MaterialTheme.typography.labelSmall, color = colors.green)
        }
    }
}

@Composable
private fun StripDatum(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.atcColors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        Text(value, style = MaterialTheme.typography.labelMedium, color = colors.white)
    }
}

@Composable
private fun StepControl(
    label: String,
    value: String,
    detail: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colors = MaterialTheme.atcColors
    Surface(
        color = colors.night.copy(alpha = .62f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", stringResource(R.string.decrease_value, label), onDecrease)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(value, style = MaterialTheme.typography.titleMedium, color = colors.white, fontFamily = FontFamily.Monospace)
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.greenDim, maxLines = 1)
                }
            }
            StepButton("+", stringResource(R.string.increase_value, label), onIncrease)
        }
    }
}

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.atcColors
    Box(
        Modifier
            .fillMaxHeight()
            .width(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 22.sp, color = colors.green, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun ClearanceButton(label: String, caption: String, accent: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.atcColors
    val description = stringResource(R.string.cd_clearance_action, label, caption)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(onClick = onClick),
        color = accent.copy(alpha = .11f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .72f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(accent)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent)
                Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.muted)
            }
            Text("›", fontSize = 21.sp, color = accent)
        }
    }
}

@Composable
private fun FuelStatus(fuelPercent: Int) {
    val colors = MaterialTheme.atcColors
    Column {
        Row {
            Text(stringResource(R.string.fuel_time_margin).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.percent_value, fuelPercent), style = MaterialTheme.typography.labelMedium, color = if (fuelPercent < 25) colors.red else colors.green)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { fuelPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = if (fuelPercent < 25) colors.red else colors.greenDim,
            trackColor = colors.line,
        )
    }
}

@Composable
private fun PauseOverlay(state: GameUiState, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    val paneDescription = stringResource(R.string.pane_simulation_paused)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.night.copy(alpha = .82f))
                .semantics { paneTitle = paneDescription },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(min = 300.dp, max = 420.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.panel,
                border = BorderStroke(1.dp, colors.greenDim),
                shadowElevation = 18.dp,
            ) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sector_paused).uppercase(), style = MaterialTheme.typography.headlineMedium, color = colors.white)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.traffic_holding_time, formatElapsed(state.elapsedSeconds)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    Spacer(Modifier.height(22.dp))
                    PrimaryActionButton(stringResource(R.string.resume_control), { onAction(GameAction.TogglePause) }, Modifier.fillMaxWidth(), suffix = "▶")
                    Spacer(Modifier.height(8.dp))
                    SecondaryActionButton(stringResource(R.string.restart_shift), { onAction(GameAction.RestartMission) }, Modifier.fillMaxWidth())
                    TextButton(onClick = { onAction(GameAction.Navigate(AppScreen.HOME)) }) {
                        Text(stringResource(R.string.return_to_operations).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    }
                }
            }
        }
    }
}

private data class TutorialCopy(
    @StringRes val kicker: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val tutorialCopy = listOf(
    TutorialCopy(R.string.tutorial_identify_kicker, R.string.tutorial_identify_title, R.string.tutorial_identify_body),
    TutorialCopy(R.string.tutorial_select_kicker, R.string.tutorial_select_title, R.string.tutorial_select_body),
    TutorialCopy(R.string.tutorial_vector_kicker, R.string.tutorial_vector_title, R.string.tutorial_vector_body),
    TutorialCopy(R.string.tutorial_clear_kicker, R.string.tutorial_clear_title, R.string.tutorial_clear_body),
)

@Composable
private fun TutorialOverlay(step: Int, onAction: (GameAction) -> Unit, compact: Boolean) {
    val colors = MaterialTheme.atcColors
    val copy = tutorialCopy[step.coerceIn(tutorialCopy.indices)]
    val paneDescription = stringResource(R.string.pane_tutorial_step, step + 1, tutorialCopy.size)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.night.copy(alpha = .62f))
                .semantics { paneTitle = paneDescription },
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (compact) 18.dp else 28.dp, bottom = if (compact) 18.dp else 28.dp)
                    .widthIn(min = 280.dp, max = if (compact) 370.dp else 440.dp),
                shape = RoundedCornerShape(19.dp),
                color = colors.panel,
                border = BorderStroke(1.dp, colors.cyan),
                shadowElevation = 16.dp,
            ) {
                Column(Modifier.padding(if (compact) 16.dp else 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(copy.kicker).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.cyan)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.tutorial_progress, step + 1, tutorialCopy.size), style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(copy.title), style = MaterialTheme.typography.titleLarge, color = colors.white)
                    Text(stringResource(copy.body), style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = if (compact) 2 else 3)
                    Spacer(Modifier.height(13.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            tutorialCopy.indices.forEach { index ->
                                Box(
                                    Modifier
                                        .width(if (index == step) 20.dp else 6.dp)
                                        .height(5.dp)
                                        .background(if (index == step) colors.cyan else colors.line, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onAction(GameAction.DismissTutorial) }) {
                            Text(stringResource(R.string.skip).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                        }
                        Spacer(Modifier.width(4.dp))
                        PrimaryActionButton(
                            text = if (step == tutorialCopy.lastIndex) stringResource(R.string.take_control) else stringResource(R.string.next),
                            onClick = { if (step == tutorialCopy.lastIndex) onAction(GameAction.DismissTutorial) else onAction(GameAction.AdvanceTutorial) },
                            modifier = Modifier.width(if (step == tutorialCopy.lastIndex) 152.dp else 104.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun NormalizedPoint.toOffset(size: Size) = Offset(x * size.width, y * size.height)

private operator fun Offset.times(value: Float) = Offset(x * value, y * value)

private fun formatElapsed(seconds: Int): String = String.format(
    Locale.getDefault(),
    "%02d:%02d",
    seconds / 60,
    seconds % 60,
)
private fun altitudeTrend(aircraft: AircraftUiModel): String = when {
    aircraft.targetAltitudeFeet > aircraft.altitudeFeet -> "↑"
    aircraft.targetAltitudeFeet < aircraft.altitudeFeet -> "↓"
    else -> "•"
}

@Composable
private fun phaseLabel(phase: FlightPhase): String = stringResource(
    when (phase) {
        FlightPhase.ARRIVAL -> R.string.phase_arrival
        FlightPhase.DEPARTURE -> R.string.phase_departure
        FlightPhase.APPROACH -> R.string.phase_approach
        FlightPhase.LANDED -> R.string.phase_landed
        FlightPhase.EXITED -> R.string.phase_exited
    },
)
