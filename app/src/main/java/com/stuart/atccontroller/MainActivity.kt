package com.stuart.atccontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import com.stuart.atccontroller.platform.GameFeedback
import com.stuart.atccontroller.ui.AtcControllerApp
import com.stuart.atccontroller.ui.AtcControllerTheme
import com.stuart.atccontroller.ui.LiveFeedbackKind
import com.stuart.atccontroller.ui.LiveGameViewModel

class MainActivity : ComponentActivity() {
    private val gameViewModel: LiveGameViewModel by viewModels()
    private lateinit var gameFeedback: GameFeedback
    private val delayedFocusLoss = Runnable {
        if (
            !hasWindowFocus() &&
            !isChangingConfigurations &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            gameViewModel.onWindowFocusChanged(hasFocus = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameFeedback = GameFeedback(this)
        gameViewModel.refreshLocalizedContent()
        enableEdgeToEdge()
        setContent {
            val state = gameViewModel.uiState
            val feedbackCue = gameViewModel.feedbackCue

            LaunchedEffect(state.settingsLoaded, state.settings) {
                if (state.settingsLoaded) gameFeedback.applySettings(state.settings)
            }
            LaunchedEffect(feedbackCue?.sequence, state.settingsLoaded) {
                feedbackCue?.takeIf { state.settingsLoaded }?.let { cue ->
                    when (cue.kind) {
                        LiveFeedbackKind.CONFIRMATION -> gameFeedback.clearance(state.settings)
                        LiveFeedbackKind.SUCCESS -> gameFeedback.missionComplete(state.settings)
                        LiveFeedbackKind.WARNING,
                        LiveFeedbackKind.FAILURE -> gameFeedback.warning(state.settings)
                    }
                    gameViewModel.consumeFeedback(cue.sequence)
                }
            }

            AtcControllerTheme(highContrast = state.settings.highContrast) {
                AtcControllerApp(
                    state = state,
                    onAction = gameViewModel::onAction,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::gameFeedback.isInitialized && gameViewModel.uiState.settingsLoaded) {
            gameFeedback.applySettings(gameViewModel.uiState.settings)
        }
    }

    override fun onStop() {
        // The ViewModel survives a configuration change, so rotating or resizing must not alter
        // the running simulation. A genuine background transition is still paused and saved.
        if (!isChangingConfigurations) gameViewModel.onHostStopped()
        if (::gameFeedback.isInitialized) gameFeedback.onBackground()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        window.decorView.removeCallbacks(delayedFocusLoss)
        if (!hasFocus) {
            // Give configuration changes time to reach onStop so rotation/resizing is not treated
            // as a genuine focus-loss pause.
            window.decorView.postDelayed(delayedFocusLoss, FOCUS_LOSS_DEBOUNCE_MILLIS)
        }
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(delayedFocusLoss)
        if (::gameFeedback.isInitialized) gameFeedback.close()
        super.onDestroy()
    }

    private companion object {
        const val FOCUS_LOSS_DEBOUNCE_MILLIS = 250L
    }
}
