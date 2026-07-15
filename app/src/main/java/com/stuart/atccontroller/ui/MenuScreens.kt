package com.stuart.atccontroller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stuart.atccontroller.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(state: GameUiState, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 800.dp || maxHeight < 440.dp
        val narrow = maxWidth < 700.dp
        if (narrow) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ScreenHeader(
                        eyebrow = stringResource(R.string.home_eyebrow),
                        title = stringResource(R.string.home_title),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.home_hero),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.white,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_summary),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.muted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(
                            text = stringResource(R.string.choose_shift),
                            onClick = { onAction(GameAction.Navigate(AppScreen.MISSIONS)) },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryActionButton(
                            text = stringResource(R.string.continue_game),
                            onClick = { onAction(GameAction.ContinueLastGame) },
                            modifier = Modifier.width(120.dp),
                            enabled = state.canContinue,
                        )
                    }
                }
                item {
                    HomeSectorCard(
                        state = state,
                        compact = true,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onAction(GameAction.Navigate(AppScreen.SETTINGS)) }) {
                            Text(stringResource(R.string.settings).uppercase(), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onAction(GameAction.Navigate(AppScreen.ABOUT)) }) {
                            Text(stringResource(R.string.about_safety).uppercase(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(stringResource(R.string.version_offline), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
            }
        } else Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 24.dp else 42.dp, vertical = if (compact) 18.dp else 28.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 48.dp),
        ) {
            Column(Modifier.weight(1.08f).fillMaxHeight()) {
                ScreenHeader(
                    eyebrow = stringResource(R.string.home_eyebrow),
                    title = stringResource(R.string.home_title),
                    trailing = {
                        DataPill(stringResource(R.string.network), stringResource(R.string.offline), accent = colors.green)
                    },
                )
                Spacer(Modifier.weight(.7f))
                Text(
                    text = stringResource(R.string.home_hero),
                    style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                    color = colors.white,
                    maxLines = 2,
                )
                Spacer(Modifier.height(if (compact) 7.dp else 12.dp))
                Text(
                    text = stringResource(R.string.home_summary),
                    modifier = Modifier.widthIn(max = 570.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
                Row(
                    modifier = Modifier.widthIn(max = 570.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PrimaryActionButton(
                        text = stringResource(R.string.choose_shift),
                        onClick = { onAction(GameAction.Navigate(AppScreen.MISSIONS)) },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryActionButton(
                        text = stringResource(R.string.continue_game),
                        onClick = { onAction(GameAction.ContinueLastGame) },
                        modifier = Modifier.width(132.dp),
                        enabled = state.canContinue,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onAction(GameAction.Navigate(AppScreen.SETTINGS)) }) {
                        Text(stringResource(R.string.settings).uppercase(), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("•", color = colors.line)
                    TextButton(onClick = { onAction(GameAction.Navigate(AppScreen.ABOUT)) }) {
                        Text(stringResource(R.string.about_safety).uppercase(), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.version_offline), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
            }

            HomeSectorCard(
                state = state,
                compact = compact,
                modifier = Modifier
                    .weight(.72f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun HomeSectorCard(state: GameUiState, compact: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.atcColors
    val mission = state.selectedMission
    Surface(
        modifier = modifier.widthIn(min = 245.dp, max = 430.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.panel.copy(alpha = .94f),
        border = BorderStroke(1.dp, colors.line),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(if (compact) 16.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(colors.green)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        R.string.selected_shift_preview,
                        mission?.title ?: stringResource(R.string.shift_fallback),
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.aircraft_count_short, mission?.trafficCount ?: 0),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.white,
                )
            }
            Spacer(Modifier.height(12.dp))
            MiniRadar(
                aircraftPositions = mission?.previewPositions.orEmpty(),
                shiftTitle = mission?.title ?: stringResource(R.string.shift_fallback),
                scheduledAircraft = mission?.trafficCount ?: 0,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.radar),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataPill(
                    stringResource(R.string.runway_short),
                    mission?.runwayLabel ?: stringResource(R.string.not_available_short),
                    Modifier.weight(1f),
                )
                DataPill(
                    stringResource(R.string.wind),
                    mission?.windShort ?: stringResource(R.string.not_available_short),
                    Modifier.weight(1f),
                    colors.cyan,
                )
                DataPill(
                    stringResource(R.string.visibility),
                    mission?.let { stringResource(R.string.visibility_km, it.visibilityKm) }
                        ?: stringResource(R.string.not_available_short),
                    Modifier.weight(1f),
                    colors.amber,
                )
            }
            Spacer(Modifier.height(if (compact) 9.dp else 14.dp))
            HorizontalDivider(color = colors.line)
            Spacer(Modifier.height(if (compact) 9.dp else 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.certification).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    Text(
                        pluralStringResource(
                            R.plurals.shifts_cleared_dynamic,
                            state.career.completedShifts,
                            state.career.completedShifts,
                            state.career.totalShifts,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.white,
                    )
                }
                Text(
                    stringResource(
                        R.string.stars_earned_dynamic,
                        state.career.earnedStars,
                        state.career.availableStars,
                    ),
                    color = colors.amber,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun MiniRadar(
    aircraftPositions: List<NormalizedPoint>,
    shiftTitle: String,
    scheduledAircraft: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    val description = pluralStringResource(
        R.plurals.cd_selected_shift_preview,
        scheduledAircraft,
        shiftTitle,
        scheduledAircraft,
    )
    Canvas(modifier.semantics { contentDescription = description }) {
        val center = Offset(size.width * .52f, size.height * .52f)
        repeat(4) { index ->
            drawCircle(
                colors.green.copy(alpha = .12f),
                radius = size.minDimension * .13f * (index + 1),
                center = center,
                style = Stroke(1f),
            )
        }
        drawLine(colors.line.copy(alpha = .6f), Offset(center.x, 0f), Offset(center.x, size.height), 1f)
        drawLine(colors.line.copy(alpha = .6f), Offset(0f, center.y), Offset(size.width, center.y), 1f)
        drawLine(
            colors.white.copy(alpha = .7f),
            Offset(size.width * .39f, size.height * .63f),
            Offset(size.width * .65f, size.height * .44f),
            5f,
        )
        aircraftPositions.forEach { position ->
            val p = Offset(position.x * size.width, position.y * size.height)
            drawCircle(colors.green, 3.5f, p)
            drawLine(colors.green.copy(alpha = .5f), p, p + Offset(13f, -8f), 1f)
        }
    }
}

@Composable
fun MissionSelectScreen(state: GameUiState, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 820.dp || maxHeight < 430.dp
        val narrow = maxWidth < 700.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 20.dp else 34.dp, vertical = if (compact) 14.dp else 24.dp),
        ) {
            ScreenHeader(
                eyebrow = stringResource(R.string.training_programme),
                title = stringResource(R.string.select_shift),
                onBack = { onAction(GameAction.Navigate(AppScreen.HOME)) },
                trailing = {
                    DataPill(
                        stringResource(R.string.earned),
                        stringResource(
                            R.string.stars_earned_dynamic,
                            state.career.earnedStars,
                            state.career.availableStars,
                        ),
                        accent = colors.amber,
                    )
                },
            )
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
            if (narrow) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 240.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = colors.panel.copy(alpha = .92f),
                            border = BorderStroke(1.dp, colors.line),
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                items(state.missions, key = { it.id }) { mission ->
                                    MissionListItem(
                                        mission = mission,
                                        selected = mission.id == state.selectedMissionId,
                                        onClick = { onAction(GameAction.SelectMission(mission.id)) },
                                    )
                                }
                            }
                        }
                    }
                    state.selectedMission?.let { mission ->
                        item {
                            MissionBriefing(
                                mission = mission,
                                compact = true,
                                scrollContent = false,
                                onStart = { onAction(GameAction.StartSelectedMission) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 330.dp),
                            )
                        }
                    }
                }
            } else Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp),
            ) {
                Surface(
                    modifier = Modifier.width(if (compact) 240.dp else 290.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.panel.copy(alpha = .92f),
                    border = BorderStroke(1.dp, colors.line),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(state.missions, key = { it.id }) { mission ->
                            MissionListItem(
                                mission = mission,
                                selected = mission.id == state.selectedMissionId,
                                onClick = { onAction(GameAction.SelectMission(mission.id)) },
                            )
                        }
                    }
                }

                state.selectedMission?.let { mission ->
                    MissionBriefing(
                        mission = mission,
                        compact = compact,
                        onStart = { onAction(GameAction.StartSelectedMission) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionListItem(mission: MissionUiModel, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.atcColors
    val locale = LocalConfiguration.current.locales[0]
    val bestScore = mission.bestScore?.let { NumberFormat.getIntegerInstance(locale).format(it) }
    val scoreLabel = if (mission.isEndless) {
        bestScore?.let { stringResource(R.string.high_score_value, it) }
            ?: stringResource(R.string.high_score_not_recorded)
    } else {
        bestScore?.let { stringResource(R.string.best_score_value, it) }
            ?: stringResource(R.string.best_score_not_recorded)
    }
    val description = when {
        mission.locked -> stringResource(R.string.cd_mission_locked, mission.title)
        mission.isEndless -> stringResource(R.string.cd_endless_result, mission.title, scoreLabel)
        else -> pluralStringResource(
            R.plurals.cd_mission_result,
            mission.bestStars ?: 0,
            mission.title,
            mission.bestStars ?: 0,
            scoreLabel,
        )
    }
    val foreground = when {
        mission.locked -> colors.muted.copy(alpha = .55f)
        selected -> colors.night
        else -> colors.white
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(enabled = !mission.locked, onClick = onClick),
        color = if (selected) colors.green else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(if (selected) colors.night.copy(alpha = .16f) else colors.panelRaised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (mission.locked) "×" else stringResource(R.string.mission_number_short, mission.number),
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(mission.title, style = MaterialTheme.typography.titleMedium, color = foreground, maxLines = 1)
                Text(
                    mission.subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.night.copy(alpha = .7f) else colors.muted,
                    maxLines = 1,
                )
                Text(
                    scoreLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.night.copy(alpha = .76f) else colors.greenDim,
                    maxLines = 1,
                )
            }
            if (!mission.locked || mission.completed) {
                mission.bestStars?.let { stars ->
                    StarRating(stars, compact = true)
                }
            }
        }
    }
}

@Composable
private fun MissionBriefing(
    mission: MissionUiModel,
    compact: Boolean,
    scrollContent: Boolean = true,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    val locale = LocalConfiguration.current.locales[0]
    val scoreText = mission.bestScore?.let { NumberFormat.getIntegerInstance(locale).format(it) }
    val scoreLabel = if (mission.isEndless) {
        scoreText?.let { stringResource(R.string.high_score_value, it) }
            ?: stringResource(R.string.high_score_not_recorded)
    } else {
        scoreText?.let { stringResource(R.string.best_score_value, it) }
            ?: stringResource(R.string.best_score_not_recorded)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = colors.panel.copy(alpha = .92f),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.fillMaxSize().padding(if (compact) 18.dp else 26.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (scrollContent) {
                            Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (mission.isEndless) {
                            stringResource(R.string.endless).uppercase()
                        } else {
                            stringResource(R.string.shift_number, mission.number).uppercase()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.green,
                    )
                    Spacer(Modifier.weight(1f))
                    mission.bestStars?.let { StarRating(it) }
                }
                Spacer(Modifier.height(8.dp))
                Text(mission.title, style = MaterialTheme.typography.headlineLarge, color = colors.white)
                Text(mission.subtitle, style = MaterialTheme.typography.titleMedium, color = colors.cyan)
                Text(scoreLabel, style = MaterialTheme.typography.labelMedium, color = colors.amber)
                Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                Text(mission.briefing, style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                if (mission.objectives.isNotEmpty()) {
                    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                    SectionLabel(stringResource(R.string.objectives))
                    Spacer(Modifier.height(6.dp))
                    mission.objectives.forEach { objective ->
                        Text(
                            stringResource(R.string.objective_item, objective),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.white,
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DataPill(
                        stringResource(R.string.traffic),
                        stringResource(R.string.aircraft_count, mission.trafficCount),
                        Modifier.weight(1f),
                    )
                    DataPill(
                        stringResource(R.string.runway),
                        mission.runwayLabel,
                        Modifier.weight(1f),
                        colors.cyan,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DataPill(stringResource(R.string.wind), mission.wind, Modifier.weight(1f), colors.cyan)
                    DataPill(
                        stringResource(R.string.visibility),
                        stringResource(R.string.visibility_km, mission.visibilityKm),
                        Modifier.weight(1f),
                        colors.amber,
                    )
                    DataPill(
                        stringResource(R.string.duration),
                        formatBriefingDuration(mission.durationSeconds),
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                PrimaryActionButton(
                    text = if (mission.isEndless) {
                        stringResource(R.string.begin_endless_shift)
                    } else {
                        stringResource(R.string.start_briefing)
                    },
                    onClick = onStart,
                    enabled = !mission.locked,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!compact) {
                Spacer(Modifier.width(24.dp))
                MissionPreview(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.radar),
                )
            }
        }
    }
}

@Composable
private fun MissionPreview(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.atcColors
    val description = stringResource(R.string.cd_terminal_area_preview)
    Canvas(modifier.semantics { contentDescription = description }) {
        val center = Offset(size.width * .52f, size.height * .51f)
        repeat(4) { index ->
            drawCircle(colors.green.copy(alpha = .12f), size.minDimension * .13f * (index + 1), center, style = Stroke(1f))
        }
        repeat(12) { index ->
            val angle = Math.toRadians((index * 30).toDouble())
            drawLine(
                colors.line.copy(alpha = .45f),
                center,
                Offset(center.x + cos(angle).toFloat() * size.maxDimension, center.y + sin(angle).toFloat() * size.maxDimension),
                1f,
            )
        }
        val route = Path().apply {
            moveTo(size.width * .06f, size.height * .25f)
            cubicTo(size.width * .32f, size.height * .29f, size.width * .40f, size.height * .58f, size.width * .68f, size.height * .62f)
            lineTo(size.width * .94f, size.height * .78f)
        }
        drawPath(route, colors.cyan.copy(alpha = .8f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))))
        drawLine(colors.white.copy(alpha = .85f), Offset(size.width * .41f, size.height * .58f), Offset(size.width * .67f, size.height * .43f), 7f)
        val points = listOf(Offset(.18f, .30f), Offset(.40f, .54f), Offset(.76f, .65f), Offset(.82f, .24f))
        points.forEachIndexed { index, point ->
            drawCircle(if (index == 2) colors.amber else colors.green, 4f, Offset(point.x * size.width, point.y * size.height))
        }
    }
}

@Composable
fun SettingsScreen(settings: SettingsUiState, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    val radarLabelDescription = stringResource(R.string.cd_radar_label_size)
    val audioPanelTitle = stringResource(R.string.audio_feedback).uppercase()
    val radarPanelTitle = stringResource(R.string.radar_accessibility).uppercase()
    val audioContent: @Composable () -> Unit = {
        VolumeSetting(
            title = stringResource(R.string.music),
            subtitle = stringResource(R.string.music_summary),
            volume = settings.musicVolume,
            onVolumeChange = { onAction(GameAction.SetMusicVolume(it)) },
            onToggleMute = { onAction(GameAction.ToggleMusicMute) },
        )
        VolumeSetting(
            title = stringResource(R.string.radio_alerts),
            subtitle = stringResource(R.string.radio_alerts_summary),
            volume = settings.effectsVolume,
            onVolumeChange = { onAction(GameAction.SetEffectsVolume(it)) },
            onToggleMute = { onAction(GameAction.ToggleEffectsMute) },
        )
        SettingToggle(stringResource(R.string.haptic_feedback), stringResource(R.string.haptic_feedback_summary), settings.hapticsEnabled) { onAction(GameAction.ToggleHaptics) }
    }
    val radarContent: @Composable () -> Unit = {
        SettingToggle(stringResource(R.string.aircraft_trails), stringResource(R.string.aircraft_trails_summary), settings.trailsEnabled) { onAction(GameAction.ToggleTrails) }
        SettingToggle(stringResource(R.string.reduced_motion), stringResource(R.string.reduced_motion_summary), settings.reducedMotion) { onAction(GameAction.ToggleReducedMotion) }
        SettingToggle(stringResource(R.string.high_contrast), stringResource(R.string.high_contrast_summary), settings.highContrast) { onAction(GameAction.ToggleHighContrast) }
        SettingToggle(
            stringResource(R.string.label_decluttering),
            stringResource(R.string.label_decluttering_summary),
            settings.labelDeclutteringEnabled,
        ) { onAction(GameAction.ToggleLabelDecluttering) }
        SettingToggle(
            stringResource(R.string.pause_on_focus_loss),
            stringResource(R.string.pause_on_focus_loss_summary),
            settings.pauseOnFocusLoss,
        ) { onAction(GameAction.TogglePauseOnFocusLoss) }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.label_size).uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.white)
                Text(stringResource(R.string.percent_value, (settings.labelScale * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = colors.green)
            }
            Slider(
                value = settings.labelScale,
                onValueChange = { onAction(GameAction.SetLabelScale(it)) },
                valueRange = .8f..1.4f,
                steps = 5,
                modifier = Modifier.weight(1.2f).semantics { contentDescription = radarLabelDescription },
                colors = SliderDefaults.colors(
                    thumbColor = colors.green,
                    activeTrackColor = colors.green,
                    inactiveTrackColor = colors.line,
                ),
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 430.dp
        val narrow = maxWidth < 700.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 24.dp else 40.dp, vertical = if (compact) 14.dp else 24.dp),
        ) {
            ScreenHeader(
                eyebrow = stringResource(R.string.preferences),
                title = stringResource(R.string.controller_settings),
                onBack = { onAction(GameAction.Navigate(AppScreen.HOME)) },
                trailing = { DataPill(stringResource(R.string.saved), stringResource(R.string.on_device)) },
            )
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
            if (narrow) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        SettingsPanel(
                            title = audioPanelTitle,
                            modifier = Modifier.fillMaxWidth(),
                            fillHeight = false,
                            scrollable = false,
                            content = audioContent,
                        )
                    }
                    item {
                        SettingsPanel(
                            title = radarPanelTitle,
                            modifier = Modifier.fillMaxWidth(),
                            fillHeight = false,
                            scrollable = false,
                            content = radarContent,
                        )
                    }
                }
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    SettingsPanel(audioPanelTitle, Modifier.weight(1f), content = audioContent)
                    SettingsPanel(radarPanelTitle, Modifier.weight(1f), content = radarContent)
                }
            }
        }
    }
}

@Composable
private fun VolumeSetting(
    title: String,
    subtitle: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    val colors = MaterialTheme.atcColors
    val sliderDescription = stringResource(R.string.cd_volume_level, title)
    val muteDescription = if (volume > 0f) {
        stringResource(R.string.mute_named_audio, title)
    } else {
        stringResource(R.string.unmute_named_audio, title)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.white)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 1)
            }
            TextButton(
                onClick = onToggleMute,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = muteDescription },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.green),
            ) {
                Text(
                    stringResource(if (volume > 0f) R.string.mute else R.string.unmute).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = volume.coerceIn(0f, 1f),
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                steps = 9,
                modifier = Modifier.weight(1f).semantics { contentDescription = sliderDescription },
                colors = SliderDefaults.colors(
                    thumbColor = colors.green,
                    activeTrackColor = colors.green,
                    inactiveTrackColor = colors.line,
                ),
            )
            Text(
                stringResource(R.string.percent_value, (volume.coerceIn(0f, 1f) * 100).toInt()),
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.green,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.atcColors
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = colors.panel.copy(alpha = .94f),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SectionLabel(title)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.atcColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = title }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.white)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 2)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.night,
                checkedTrackColor = colors.green,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.panelRaised,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

@Composable
fun AboutScreen(onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 440.dp
        val narrow = maxWidth < 700.dp
        if (narrow) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                ScreenHeader(
                    eyebrow = stringResource(R.string.about),
                    title = stringResource(R.string.manchester_control),
                    onBack = { onAction(GameAction.Navigate(AppScreen.HOME)) },
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        RadarMark(72.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.about_heading), style = MaterialTheme.typography.headlineLarge, color = colors.white)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.about_summary), style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.version_snapshot), style = MaterialTheme.typography.labelSmall, color = colors.green)
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = colors.panel.copy(alpha = .94f),
                            border = BorderStroke(1.dp, colors.line),
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) { AboutDetailsContent() }
                        }
                    }
                }
            }
        } else Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 28.dp else 44.dp, vertical = if (compact) 18.dp else 30.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Column(Modifier.weight(.75f).fillMaxHeight()) {
                ScreenHeader(
                    eyebrow = stringResource(R.string.about),
                    title = stringResource(R.string.manchester_control),
                    onBack = { onAction(GameAction.Navigate(AppScreen.HOME)) },
                )
                Spacer(Modifier.weight(1f))
                RadarMark(if (compact) 72.dp else 98.dp)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.about_heading), style = MaterialTheme.typography.headlineLarge, color = colors.white)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.about_summary), style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.version_snapshot), style = MaterialTheme.typography.labelSmall, color = colors.green)
            }
            Surface(
                modifier = Modifier.weight(1.25f).fillMaxHeight(),
                shape = RoundedCornerShape(22.dp),
                color = colors.panel.copy(alpha = .94f),
                border = BorderStroke(1.dp, colors.line),
            ) {
                Column(
                    Modifier.padding(if (compact) 20.dp else 28.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
                ) {
                    AboutDetailsContent()
                }
            }
        }
    }
}

@Composable
private fun AboutDetailsContent() {
    val colors = MaterialTheme.atcColors
    AboutSection(
        stringResource(R.string.about_entertainment_title).uppercase(),
        stringResource(R.string.about_entertainment_body),
        colors.amber,
    )
    HorizontalDivider(color = colors.line)
    AboutSection(
        stringResource(R.string.about_data_title).uppercase(),
        stringResource(R.string.about_data_body),
        colors.cyan,
    )
    HorizontalDivider(color = colors.line)
    AboutSection(
        stringResource(R.string.about_privacy_title).uppercase(),
        stringResource(R.string.about_privacy_body),
        colors.green,
    )
    Text(
        stringResource(R.string.about_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted,
    )
}

@Composable
private fun AboutSection(title: String, body: String, accent: Color) {
    val colors = MaterialTheme.atcColors
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.width(3.dp).heightIn(min = 50.dp).background(accent, RoundedCornerShape(2.dp)))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
            Spacer(Modifier.height(5.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.white)
        }
    }
}

@Composable
fun ResultsScreen(state: GameUiState, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    val result = state.result ?: MissionResultUiModel(stringResource(R.string.shift_complete), 3, state.score, 0, 0, 0, 0, false)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 430.dp
        val narrow = maxWidth < 700.dp
        if (narrow) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Text(
                        if (result.successful) stringResource(R.string.shift_complete).uppercase() else stringResource(R.string.shift_ended).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (result.successful) colors.green else colors.amber,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(result.title, style = MaterialTheme.typography.headlineLarge, color = colors.white)
                    Spacer(Modifier.height(10.dp))
                    StarRating(result.stars)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        localizedInteger(result.score),
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.greenBright,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(stringResource(R.string.final_score).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    if (result.personalBest) {
                        Spacer(Modifier.height(10.dp))
                        DataPill(stringResource(R.string.new_label), stringResource(R.string.personal_best), accent = colors.amber)
                    }
                }
                item {
                    ResultsPerformancePanel(
                        result = result,
                        onAction = onAction,
                        compact = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                    )
                }
            }
        } else Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 30.dp else 54.dp, vertical = if (compact) 20.dp else 36.dp),
            horizontalArrangement = Arrangement.spacedBy(42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(.9f)) {
                Text(
                    if (result.successful) stringResource(R.string.shift_complete).uppercase() else stringResource(R.string.shift_ended).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result.successful) colors.green else colors.amber,
                )
                Spacer(Modifier.height(6.dp))
                Text(result.title, style = MaterialTheme.typography.headlineLarge, color = colors.white)
                Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
                StarRating(result.stars)
                Spacer(Modifier.height(8.dp))
                Text(
                    localizedInteger(result.score),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.greenBright,
                    fontFamily = FontFamily.Monospace,
                )
                Text(stringResource(R.string.final_score).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                if (result.personalBest) {
                    Spacer(Modifier.height(10.dp))
                    DataPill(stringResource(R.string.new_label), stringResource(R.string.personal_best), accent = colors.amber)
                }
            }
            ResultsPerformancePanel(
                result = result,
                onAction = onAction,
                compact = compact,
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ResultsPerformancePanel(
    result: MissionResultUiModel,
    onAction: (GameAction) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = colors.panel.copy(alpha = .94f),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column(Modifier.padding(if (compact) 18.dp else 26.dp)) {
            SectionLabel(stringResource(R.string.performance_breakdown))
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            ResultLine(stringResource(R.string.safe_arrivals), localizedInteger(result.safeArrivals), positive = true)
            ResultLine(stringResource(R.string.safe_departures), localizedInteger(result.safeDepartures), positive = true)
            ResultLine(stringResource(R.string.efficiency_bonus), "+${localizedInteger(result.efficiencyBonus)}", positive = true)
            ResultLine(stringResource(R.string.separation_penalty), "−${localizedInteger(result.separationPenalty)}", positive = result.separationPenalty == 0)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(stringResource(R.string.restart), { onAction(GameAction.RestartMission) }, Modifier.weight(.8f))
                PrimaryActionButton(stringResource(R.string.next_shift), { onAction(GameAction.Navigate(AppScreen.MISSIONS)) }, Modifier.weight(1.2f))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onAction(GameAction.Navigate(AppScreen.HOME)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.muted),
            ) { Text(stringResource(R.string.return_to_operations).uppercase(), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String, positive: Boolean) {
    val colors = MaterialTheme.atcColors
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (positive) colors.green else colors.red,
            fontFamily = FontFamily.Monospace,
        )
    }
    HorizontalDivider(color = colors.line.copy(alpha = .65f))
}

private fun formatBriefingDuration(totalSeconds: Int): String = String.format(
    Locale.getDefault(),
    "%d:%02d",
    totalSeconds.coerceAtLeast(0) / 60,
    totalSeconds.coerceAtLeast(0) % 60,
)
