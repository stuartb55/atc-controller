package com.stuart.atccontroller.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
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
            Spacer(Modifier.height(7.dp))
            Text(
                pluralStringResource(
                    R.plurals.service_record_summary,
                    state.serviceRecord.totalSafeMovements,
                    state.serviceRecord.totalSafeMovements,
                    state.serviceRecord.currentSafeStreak,
                    state.serviceRecord.achievementCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.cyan,
            )
            if (state.serviceRecord.recommendation.isNotBlank()) {
                Text(
                    state.serviceRecord.recommendation,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
            SecondaryActionButton(
                text = stringResource(R.string.custom_shift_setup),
                onClick = { onAction(GameAction.Navigate(AppScreen.CUSTOM_SHIFT)) },
                modifier = Modifier.fillMaxWidth(),
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
                                onTraining = mission.takeIf { it.trainingAvailable }?.let {
                                    { onAction(GameAction.StartTrainingLesson(it.id)) }
                                },
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
                        onTraining = mission.takeIf { it.trainingAvailable }?.let {
                            { onAction(GameAction.StartTrainingLesson(it.id)) }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
fun CustomShiftScreen(configuration: CustomShiftUiModel, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_configuration)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = stringResource(R.string.offline_practice),
                title = stringResource(R.string.custom_shift_setup),
                onBack = { onAction(GameAction.Navigate(AppScreen.MISSIONS)) },
            )
        }
        item {
            Text(
                stringResource(R.string.custom_shift_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        item {
            Surface(
                color = colors.panel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.cyan),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    SectionLabel(stringResource(R.string.daily_shift_hud))
                    Text(
                        stringResource(R.string.daily_local_explanation, configuration.dailyDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                    Text(
                        if (configuration.dailyCompleted) {
                            stringResource(
                                R.string.daily_completed_summary,
                                configuration.dailyBestScore ?: 0,
                                configuration.dailyStreak,
                            )
                        } else {
                            stringResource(R.string.daily_not_completed)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cyan,
                    )
                    PrimaryActionButton(
                        stringResource(R.string.start_daily),
                        { onAction(GameAction.StartDailyShift) },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = configuration.seed,
                onValueChange = { onAction(GameAction.SetCustomSeed(it.filter(Char::isDigit).take(18))) },
                label = { Text(stringResource(R.string.seed)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ConfigCycleRow(
                stringResource(R.string.content_pack),
                configuration.contentPackName,
                { onAction(GameAction.CycleCustomContentPack(-1)) },
                { onAction(GameAction.CycleCustomContentPack(1)) },
            )
            Text(
                configuration.airportName,
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
        }
        item {
            ConfigCycleRow(
                stringResource(R.string.traffic_density),
                localizedConfigurationValue(configuration.density),
                { onAction(GameAction.CycleCustomDensity(-1)) },
                { onAction(GameAction.CycleCustomDensity(1)) },
            )
        }
        item {
            ConfigCycleRow(
                stringResource(R.string.arrival_mix),
                stringResource(R.string.percent_value, configuration.arrivalPercent),
                { onAction(GameAction.SetCustomArrivalPercent(configuration.arrivalPercent - 10)) },
                { onAction(GameAction.SetCustomArrivalPercent(configuration.arrivalPercent + 10)) },
            )
        }
        item {
            ConfigCycleRow(
                stringResource(R.string.runway_direction),
                configuration.runwayEndLabel,
                { onAction(GameAction.CycleCustomRunwayDirection(-1)) },
                { onAction(GameAction.CycleCustomRunwayDirection(1)) },
            )
        }
        item {
            ConfigCycleRow(
                stringResource(R.string.weather_preset),
                localizedConfigurationValue(configuration.weatherPreset),
                { onAction(GameAction.CycleCustomWeather(-1)) },
                { onAction(GameAction.CycleCustomWeather(1)) },
            )
        }
        item {
            ConfigCycleRow(
                stringResource(R.string.fuel_pressure),
                localizedConfigurationValue(configuration.fuelPressure),
                { onAction(GameAction.CycleCustomFuelPressure(-1)) },
                { onAction(GameAction.CycleCustomFuelPressure(1)) },
            )
        }
        item {
            ConfigCycleRow(
                stringResource(R.string.strike_limit),
                configuration.strikeLimit.toString(),
                { onAction(GameAction.SetCustomStrikeLimit(configuration.strikeLimit - 1)) },
                { onAction(GameAction.SetCustomStrikeLimit(configuration.strikeLimit + 1)) },
            )
        }
        item {
            SettingToggle(
                stringResource(R.string.assist_approach_setup),
                stringResource(R.string.assist_practice_only),
                configuration.approachSetup,
            ) { onAction(GameAction.ToggleCustomApproachSetup) }
            SettingToggle(
                stringResource(R.string.assist_conflict_prediction),
                stringResource(R.string.assist_practice_only),
                configuration.conflictPrediction,
            ) { onAction(GameAction.ToggleCustomConflictPrediction) }
        }
        item {
            Surface(
                color = colors.panel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.line),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    SectionLabel(stringResource(R.string.configuration_preview))
                    Text(
                        stringResource(
                            R.string.custom_traffic_preview,
                            configuration.previewTraffic,
                            configuration.previewArrivals,
                            configuration.previewDepartures,
                        ),
                        color = colors.white,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            R.string.configuration_identity,
                            configuration.configurationIdentity,
                        ),
                        color = colors.cyan,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        item {
            SectionLabel(stringResource(R.string.share_configuration))
            Text(
                configuration.shareCode,
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    stringResource(R.string.copy_code),
                    {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("ATC shift configuration", configuration.shareCode),
                        )
                    },
                    Modifier.weight(1f),
                )
                SecondaryActionButton(
                    stringResource(R.string.share_code),
                    {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, configuration.shareCode)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, shareChooserTitle),
                        )
                    },
                    Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = configuration.importCode,
                onValueChange = { onAction(GameAction.SetShareCodeInput(it)) },
                label = { Text(stringResource(R.string.import_share_code)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                supportingText = configuration.importError?.let { error ->
                    { Text(error, color = colors.amber) }
                },
            )
            SecondaryActionButton(
                stringResource(R.string.import_code),
                { onAction(GameAction.ImportShareCode) },
                Modifier.fillMaxWidth(),
                enabled = configuration.importCode.isNotBlank(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    stringResource(R.string.use_ranked_preset),
                    { onAction(GameAction.UseRankedSeededPreset) },
                    Modifier.weight(1f),
                )
                PrimaryActionButton(
                    if (configuration.ranked) {
                        stringResource(R.string.start_ranked_seeded)
                    } else {
                        stringResource(R.string.start_practice)
                    },
                    { onAction(GameAction.StartCustomShift) },
                    Modifier.weight(1f),
                    enabled = configuration.seed.isNotBlank(),
                )
            }
        }
    }
}

@Composable
fun EndlessMilestoneScreen(state: GameUiState, onAction: (GameAction) -> Unit) {
    val colors = MaterialTheme.atcColors
    val milestone = state.endlessMilestone ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(.86f).widthIn(max = 720.dp),
            color = colors.panel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colors.cyan),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.endless_stage_complete, milestone.completedStage).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                )
                Text(
                    localizedInteger(milestone.cumulativeScore),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.greenBright,
                )
                Text(
                    stringResource(R.string.endless_stage_score, milestone.stageScore),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.white,
                )
                Text(
                    if (milestone.personalBestDelta >= 0) {
                        pluralStringResource(
                            R.plurals.endless_above_best,
                            milestone.personalBestDelta,
                            milestone.personalBestDelta,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.endless_below_best,
                            -milestone.personalBestDelta,
                            -milestone.personalBestDelta,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.amber,
                )
                HorizontalDivider(color = colors.line)
                SectionLabel(stringResource(R.string.endless_next_preview, milestone.nextStage))
                Text(
                    pluralStringResource(
                        R.plurals.endless_next_traffic,
                        milestone.nextTrafficCount,
                        milestone.nextTrafficCount,
                        milestone.nextObjective,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.white,
                )
                Text(milestone.nextWeather, style = MaterialTheme.typography.bodySmall, color = colors.cyan)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryActionButton(
                        stringResource(R.string.cash_out),
                        { onAction(GameAction.CashOutEndlessRun) },
                        Modifier.weight(1f),
                        enabled = !milestone.choicePending,
                    )
                    PrimaryActionButton(
                        stringResource(R.string.continue_next_stage),
                        { onAction(GameAction.ContinueEndlessRun) },
                        Modifier.weight(1f),
                        enabled = !milestone.choicePending,
                    )
                }
                if (milestone.choicePending) {
                    Text(
                        stringResource(R.string.endless_choice_saving),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigCycleRow(
    label: String,
    value: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.atcColors
    Surface(color = colors.panel, shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted)
                Text(value, style = MaterialTheme.typography.titleMedium, color = colors.white)
            }
            TextButton(onClick = onPrevious) { Text("−") }
            TextButton(onClick = onNext) { Text("+") }
        }
    }
}

@Composable
private fun localizedConfigurationValue(value: String): String = stringResource(when (value) {
    "LIGHT" -> R.string.config_light
    "BALANCED" -> R.string.config_balanced
    "BUSY" -> R.string.config_busy
    "WESTERLY" -> R.string.config_westerly
    "EASTERLY" -> R.string.config_easterly
    "CALM" -> R.string.config_calm
    "WINDY" -> R.string.config_windy
    "LOW_VISIBILITY" -> R.string.config_low_visibility
    "RELAXED" -> R.string.config_relaxed
    "STANDARD" -> R.string.config_standard
    "TIGHT" -> R.string.config_tight
    else -> R.string.not_available_short
})

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
            .clickable(enabled = !mission.locked || mission.trainingAvailable, onClick = onClick),
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
                    mission.campaignName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.night.copy(alpha = .72f) else colors.cyan,
                    maxLines = 1,
                )
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
                    StarRating(
                        stars = stars,
                        compact = true,
                        color = if (selected) colors.night else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionBriefing(
    mission: MissionUiModel,
    compact: Boolean,
    onStart: () -> Unit,
    onTraining: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrollContent: Boolean = true,
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
                Text(
                    stringResource(R.string.campaign_airport, mission.campaignName, mission.airportName),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.green,
                )
                Text(mission.subtitle, style = MaterialTheme.typography.titleMedium, color = colors.cyan)
                Text(scoreLabel, style = MaterialTheme.typography.labelMedium, color = colors.amber)
                Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                Text(mission.briefing, style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                if (mission.packOverview.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(mission.packOverview, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
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
                if (mission.sourceAttribution.isNotBlank()) {
                    Text(
                        mission.sourceAttribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                onTraining?.let { launchTraining ->
                    SecondaryActionButton(
                        text = stringResource(R.string.practice_lesson),
                        onClick = launchTraining,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
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
                    item { GameUseNotice() }
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        SettingsPanel(audioPanelTitle, Modifier.weight(1f), content = audioContent)
                        SettingsPanel(radarPanelTitle, Modifier.weight(1f), content = radarContent)
                    }
                    GameUseNotice()
                }
            }
        }
    }
}

@Composable
private fun GameUseNotice() {
    val colors = MaterialTheme.atcColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.amber.copy(alpha = .08f),
        border = BorderStroke(1.dp, colors.amber.copy(alpha = .45f)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.about_entertainment_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.amber,
            )
            Text(
                stringResource(R.string.about_entertainment_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
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
                    if (result.isPractice) {
                        Text(
                            stringResource(R.string.practice_result_not_ranked).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cyan,
                        )
                    }
                    if (result.isDaily) {
                        Text(
                            stringResource(R.string.daily_local_result).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cyan,
                        )
                    }
                    result.configurationIdentity?.let { identity ->
                        Text(
                            stringResource(R.string.configuration_identity, identity.takeLast(16)),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                        )
                    }
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
                        state = state,
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
                if (result.isPractice) {
                    Text(
                        stringResource(R.string.practice_result_not_ranked).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cyan,
                    )
                }
                if (result.isDaily) {
                    Text(
                        stringResource(R.string.daily_local_result).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cyan,
                    )
                }
                result.configurationIdentity?.let { identity ->
                    Text(
                        stringResource(R.string.configuration_identity, identity.takeLast(16)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                }
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
                state = state,
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
    state: GameUiState,
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
        Column(
            Modifier
                .padding(if (compact) 18.dp else 26.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.performance_breakdown))
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            if (result.scoreRows.isNotEmpty()) {
                result.scoreRows.forEach { row ->
                    ResultLine(
                        row.label,
                        when {
                            row.points > 0 -> "+${localizedInteger(row.points)}"
                            row.points < 0 -> "−${localizedInteger(-row.points)}"
                            else -> "0"
                        },
                        positive = row.points >= 0,
                    )
                }
                result.pointsToNextStar?.let { points ->
                    Text(
                        pluralStringResource(R.plurals.points_to_next_star, points, points),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.amber,
                    )
                }
            } else {
                ResultLine(stringResource(R.string.safe_arrivals), localizedInteger(result.safeArrivals), positive = true)
                ResultLine(stringResource(R.string.safe_departures), localizedInteger(result.safeDepartures), positive = true)
                ResultLine(stringResource(R.string.efficiency_bonus), "+${localizedInteger(result.efficiencyBonus)}", positive = true)
                ResultLine(stringResource(R.string.separation_penalty), "−${localizedInteger(result.separationPenalty)}", positive = result.separationPenalty == 0)
            }
            when (state.progressionSaveStatus) {
                ProgressionSaveStatus.SAVING -> Text(
                    stringResource(R.string.progress_saving),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                ProgressionSaveStatus.FAILED -> {
                    Text(
                        stringResource(R.string.progress_save_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.amber,
                    )
                    TextButton(onClick = { onAction(GameAction.RetryProgressionPersistence) }) {
                        Text(stringResource(R.string.retry_save).uppercase())
                    }
                }
                ProgressionSaveStatus.NOT_REQUIRED, ProgressionSaveStatus.SAVED -> Unit
            }
            if (result.flights.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.flight_debrief))
                Spacer(Modifier.height(6.dp))
                result.flights.forEach { flight ->
                    FlightDebriefCard(flight)
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (result.timeline.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionLabel(stringResource(R.string.flight_timeline))
                Spacer(Modifier.height(6.dp))
                result.timeline.takeLast(20).forEach { event ->
                    Text(
                        "%02d:%02d  %s".format(
                            Locale.ROOT,
                            event.elapsedSeconds / 60,
                            event.elapsedSeconds % 60,
                            event.caption,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                    )
                }
            }
            state.replayError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = colors.amber)
            }
            val relevantReplays = state.completedReplays
                .filter { it.scenarioId == state.selectedMissionId }
                .ifEmpty { state.completedReplays }
            relevantReplays.forEach { replay ->
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryActionButton(
                        stringResource(R.string.review_replay),
                        { onAction(GameAction.StartReplay(replay.id)) },
                        Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAction(GameAction.DeleteReplay(replay.id)) }) {
                        Text(stringResource(R.string.delete_replay).uppercase())
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(stringResource(R.string.restart), { onAction(GameAction.RestartMission) }, Modifier.weight(.8f))
                if (result.successful) {
                    PrimaryActionButton(
                        stringResource(R.string.next_mission),
                        { onAction(GameAction.OpenNextMission) },
                        Modifier.weight(1.2f),
                        enabled = state.progressionSaveStatus == ProgressionSaveStatus.SAVED,
                    )
                } else {
                    PrimaryActionButton(
                        stringResource(R.string.next_shift),
                        { onAction(GameAction.Navigate(AppScreen.MISSIONS)) },
                        Modifier.weight(1.2f),
                    )
                }
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
private fun FlightDebriefCard(flight: FlightDebriefUiModel) {
    val colors = MaterialTheme.atcColors
    Surface(
        color = colors.night.copy(alpha = .55f),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column(Modifier.padding(9.dp)) {
            Text(flight.callsign, style = MaterialTheme.typography.labelMedium, color = colors.white)
            Text(
                stringResource(
                    R.string.flight_debrief_summary,
                    flight.operation,
                    flight.outcome,
                    flight.handlingSeconds,
                    flight.distanceTenthsNm / 10,
                    flight.distanceTenthsNm % 10,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
            Text(
                stringResource(
                    R.string.flight_points_summary,
                    flight.routeEfficiencyPoints,
                    flight.timeBonusPoints,
                    flight.associatedPenaltyPoints,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (flight.associatedPenaltyPoints == 0) colors.green else colors.amber,
            )
            Text(
                pluralStringResource(
                    R.plurals.flight_events_count,
                    flight.eventSequences.size,
                    flight.eventSequences.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
            if (flight.holdSeconds > 0) {
                Text(
                    stringResource(R.string.flight_hold_time, flight.holdSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.amber,
                )
            }
            if (flight.routeHeatmap.size > 1) {
                val routeDescription = stringResource(R.string.route_heatmap)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .semantics { contentDescription = routeDescription },
                ) {
                    flight.routeHeatmap.zipWithNext().forEachIndexed { index, (first, second) ->
                        val progress = (index + 1f) / flight.routeHeatmap.lastIndex
                        drawLine(
                            color = colors.cyan.copy(alpha = .3f + .7f * progress),
                            start = Offset(first.x * size.width, first.y * size.height),
                            end = Offset(second.x * size.width, second.y * size.height),
                            strokeWidth = 3f,
                        )
                    }
                }
            }
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
