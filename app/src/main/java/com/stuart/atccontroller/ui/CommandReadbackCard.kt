package com.stuart.atccontroller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stuart.atccontroller.R

@Composable
internal fun CommandReadbackCard(
    readback: CommandReadbackUiModel,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    val accent = when (readback.status) {
        CommandReadbackStatus.SUBMITTED -> colors.cyan
        CommandReadbackStatus.ACCEPTED -> colors.green
        CommandReadbackStatus.REJECTED -> colors.red
    }
    val message = when (readback.status) {
        CommandReadbackStatus.SUBMITTED -> stringResource(
            R.string.command_readback_submitted,
            readback.callsign,
            readback.command,
        )
        CommandReadbackStatus.ACCEPTED -> stringResource(
            R.string.command_readback_accepted,
            readback.callsign,
            readback.command,
        )
        CommandReadbackStatus.REJECTED -> stringResource(
            R.string.command_readback_rejected,
            readback.callsign,
            readback.command,
            readback.detail ?: stringResource(R.string.rejection_unknown),
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = if (readback.status == CommandReadbackStatus.REJECTED) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            },
        color = accent.copy(alpha = .1f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .8f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }
    }
}
