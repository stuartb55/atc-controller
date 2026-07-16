package com.stuart.atccontroller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stuart.atccontroller.R
import kotlin.math.min

@Composable
fun AtcControllerApp(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.screen != AppScreen.HOME) {
        onAction(GameAction.Navigate(AppScreen.HOME))
    }

    AtcBackground(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        val screenContent: @Composable (AppScreen) -> Unit = { screen ->
            when (screen) {
                AppScreen.HOME -> HomeScreen(state, onAction)
                AppScreen.MISSIONS -> MissionSelectScreen(state, onAction)
                AppScreen.CUSTOM_SHIFT -> CustomShiftScreen(state.customShift, onAction)
                AppScreen.GAME -> GameScreen(state, onAction)
                AppScreen.MILESTONE -> EndlessMilestoneScreen(state, onAction)
                AppScreen.RESULTS -> ResultsScreen(state, onAction)
                AppScreen.SETTINGS -> SettingsScreen(state.settings, onAction)
                AppScreen.ABOUT -> AboutScreen(onAction)
            }
        }

        if (state.settings.reducedMotion) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) { screenContent(state.screen) }
        } else {
            Crossfade(
                targetState = state.screen,
                label = "screen transition",
            ) { screen ->
                Box(Modifier.fillMaxSize().safeDrawingPadding()) { screenContent(screen) }
            }
        }
            SessionStatusBanners(
                isRestoring = state.isRestoring,
                persistenceFailed = state.sessionPersistenceFailed,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SessionStatusBanners(
    isRestoring: Boolean,
    persistenceFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.atcColors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (isRestoring) {
            Surface(
                color = colors.panel.copy(alpha = .97f),
                border = BorderStroke(1.dp, colors.cyan),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = stringResource(R.string.status_restoring_shift),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.cyan,
                )
            }
        }
        if (persistenceFailed) {
            Surface(
                color = colors.panel.copy(alpha = .97f),
                border = BorderStroke(1.dp, colors.amber),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(top = if (isRestoring) 6.dp else 0.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = stringResource(R.string.warning_session_persistence),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.amber,
                )
            }
        }
    }
}

@Composable
private fun AtcBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.atcColors
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(colors.night, colors.radarGlow, colors.night),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = false) {},
        ) {
            val step = 64f
            var x = 0f
            while (x < size.width) {
                drawLine(colors.line.copy(alpha = .12f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(colors.line.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            val center = Offset(size.width * .76f, size.height * .38f)
            val radiusStep = min(size.width, size.height) * .14f
            repeat(4) { index ->
                drawCircle(
                    color = colors.green.copy(alpha = .045f),
                    radius = radiusStep * (index + 1),
                    center = center,
                    style = Stroke(width = 1.2f),
                )
            }
            drawLine(
                color = colors.green.copy(alpha = .06f),
                start = center,
                end = Offset(size.width, size.height * .08f),
                strokeWidth = 1.4f,
                cap = StrokeCap.Round,
            )
        }
        content()
    }
}
