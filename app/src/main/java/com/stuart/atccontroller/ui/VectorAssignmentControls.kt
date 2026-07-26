package com.stuart.atccontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.stuart.atccontroller.R
import kotlin.math.roundToInt

@Composable
internal fun DirectVectorAssignmentControls(
    aircraft: AircraftUiModel,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VectorTargetSlider(
            kind = VectorTargetKind.HEADING,
            label = stringResource(R.string.target_heading),
            currentValue = aircraft.targetHeadingDegrees.toFloat(),
            valueRange = 0f..355f,
            interval = 5f,
            onCommit = { onAction(GameAction.SetTargetHeading(it.roundToInt())) },
        )
        VectorTargetSlider(
            kind = VectorTargetKind.ALTITUDE,
            label = stringResource(R.string.target_altitude),
            currentValue = aircraft.targetAltitudeFeet.toFloat(),
            valueRange = 0f..12_000f,
            interval = 500f,
            onCommit = { onAction(GameAction.SetTargetAltitude(it.roundToInt())) },
        )
        VectorTargetSlider(
            kind = VectorTargetKind.SPEED,
            label = stringResource(R.string.target_speed),
            currentValue = aircraft.targetSpeedKnots.toFloat(),
            valueRange = 80f..400f,
            interval = 10f,
            onCommit = { onAction(GameAction.SetTargetSpeed(it.roundToInt())) },
        )
    }
}

@Composable
private fun VectorTargetSlider(
    kind: VectorTargetKind,
    label: String,
    currentValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    interval: Float,
    onCommit: (Float) -> Unit,
) {
    val colors = MaterialTheme.atcColors
    var pendingValue by remember(label, currentValue) {
        mutableFloatStateOf(snapVectorTarget(currentValue, valueRange, interval))
    }
    val pendingValueText = when (kind) {
        VectorTargetKind.HEADING -> stringResource(
            R.string.heading_value,
            Math.floorMod(pendingValue.roundToInt(), 360),
        )
        VectorTargetKind.ALTITUDE -> stringResource(
            R.string.flight_level_code,
            pendingValue.roundToInt() / 100,
        )
        VectorTargetKind.SPEED -> stringResource(
            R.string.knots_value,
            pendingValue.roundToInt(),
        )
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            Text(
                text = pendingValueText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.green,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = pendingValueText
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = pendingValue,
                        range = valueRange,
                        steps = sliderSteps(valueRange, interval),
                    )
                    setProgress { requestedValue ->
                        val nextValue = snapVectorTarget(requestedValue, valueRange, interval)
                        pendingValue = nextValue
                        onCommit(nextValue)
                        true
                    }
                },
        ) {
            Slider(
                value = pendingValue,
                onValueChange = {
                    pendingValue = snapVectorTarget(it, valueRange, interval)
                },
                onValueChangeFinished = { onCommit(pendingValue) },
                valueRange = valueRange,
                steps = sliderSteps(valueRange, interval),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .clearAndSetSemantics { },
            )
        }
    }
}

private enum class VectorTargetKind { HEADING, ALTITUDE, SPEED }

internal fun snapVectorTarget(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    interval: Float,
): Float {
    require(interval > 0f)
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val intervals = ((clamped - range.start) / interval).roundToInt()
    return (range.start + intervals * interval).coerceIn(range.start, range.endInclusive)
}

internal fun sliderSteps(
    range: ClosedFloatingPointRange<Float>,
    interval: Float,
): Int = (((range.endInclusive - range.start) / interval).roundToInt() - 1).coerceAtLeast(0)
