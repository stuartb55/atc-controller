package com.stuart.atccontroller.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stuart.atccontroller.data.ActiveSessionRecord
import com.stuart.atccontroller.data.ManchesterContent
import com.stuart.atccontroller.data.PlayerData
import com.stuart.atccontroller.data.PlayerProgress
import com.stuart.atccontroller.data.PlayerSettings
import com.stuart.atccontroller.data.unlockedAfterMissionCompletion
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveGameViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun persistedSettingsAreAppliedBeforeTheyBecomeInteractive() {
        val persistence = FakeGamePersistence(
            PlayerData(
                settings = PlayerSettings(
                    musicVolume = 0f,
                    effectsVolume = 0f,
                    hapticsEnabled = false,
                    highContrast = true,
                ),
            ),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)

        waitUntil { viewModel.uiState.settingsLoaded }

        assertFalse(viewModel.uiState.settings.musicEnabled)
        assertFalse(viewModel.uiState.settings.effectsEnabled)
        assertEquals(0f, viewModel.uiState.settings.musicVolume, 0.001f)
        assertEquals(0f, viewModel.uiState.settings.effectsVolume, 0.001f)
        assertFalse(viewModel.uiState.settings.hapticsEnabled)
        assertTrue(viewModel.uiState.settings.highContrast)
    }

    @Test
    fun persistedProgressAndScenarioContentDrivePhaseZeroPresentation() {
        val first = ManchesterContent.FIRST_MISSION_ID
        val parallel = ManchesterContent.missionIds.last()
        val persistence = FakeGamePersistence(
            PlayerData(
                progress = PlayerProgress(
                    missionStars = mapOf(first to 0, parallel to 3),
                    missionBestScores = mapOf(first to 0, parallel to 12_345),
                    unlockedMissionIds = ManchesterContent.missionIds.toSet(),
                    endlessHighScore = 98_765,
                ),
            ),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)

        waitUntil { viewModel.uiState.settingsLoaded }
        instrumentation.runOnMainSync { viewModel.onAction(GameAction.SelectMission(parallel)) }

        val mission = viewModel.uiState.selectedMission!!
        assertEquals(2, viewModel.uiState.career.completedShifts)
        assertEquals(8, viewModel.uiState.career.totalShifts)
        assertEquals(3, viewModel.uiState.career.earnedStars)
        assertEquals(24, viewModel.uiState.career.availableStars)
        assertEquals(14, mission.trafficCount)
        assertEquals("23R / 23L", mission.runwayLabel)
        assertEquals("240°/10kt", mission.wind)
        assertEquals(20, mission.visibilityKm)
        assertEquals(780, mission.durationSeconds)
        assertEquals(4, mission.objectives.size)
        assertEquals(12_345, mission.bestScore)
        assertEquals(null, viewModel.uiState.missions.last().bestStars)
        assertEquals(98_765, viewModel.uiState.missions.last().bestScore)
    }

    @Test
    fun volumeMuteRestoresCustomLevelAndHiddenSettingsAreInteractive() {
        val persistence = FakeGamePersistence(
            PlayerData(
                settings = PlayerSettings(
                    musicVolume = .37f,
                    effectsVolume = .58f,
                    lastMusicVolume = .37f,
                    lastEffectsVolume = .58f,
                    labelDeclutteringEnabled = false,
                    pauseOnFocusLoss = false,
                ),
            ),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)
        waitUntil { viewModel.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.ToggleMusicMute)
            assertEquals(0f, viewModel.uiState.settings.musicVolume, 0.001f)
            viewModel.onAction(GameAction.ToggleMusicMute)
            viewModel.onAction(GameAction.SetEffectsVolume(.42f))
            viewModel.onAction(GameAction.ToggleLabelDecluttering)
            viewModel.onAction(GameAction.TogglePauseOnFocusLoss)
        }

        assertEquals(.37f, viewModel.uiState.settings.musicVolume, 0.001f)
        assertEquals(.42f, viewModel.uiState.settings.effectsVolume, 0.001f)
        assertTrue(viewModel.uiState.settings.labelDeclutteringEnabled)
        assertTrue(viewModel.uiState.settings.pauseOnFocusLoss)
        waitUntil {
            persistence.data.value.settings.musicVolume == .37f &&
                persistence.data.value.settings.effectsVolume == .42f
        }
    }

    @Test
    fun transientFocusLossObeysExposedPreference() {
        val persistence = FakeGamePersistence(
            PlayerData(
                settings = PlayerSettings(pauseOnFocusLoss = false),
                progress = PlayerProgress(tutorialCompleted = true),
            ),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)
        waitUntil { viewModel.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.StartSelectedMission)
            viewModel.onWindowFocusChanged(hasFocus = false)
        }
        assertFalse(viewModel.uiState.isPaused)

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.TogglePauseOnFocusLoss)
            viewModel.onWindowFocusChanged(hasFocus = false)
        }
        assertTrue(viewModel.uiState.isPaused)
    }

    @Test
    fun savedGameReconstructsThroughTheProductionStateHolder() {
        val persistence = FakeGamePersistence(PlayerData())
        val firstHandle = SavedStateHandle()
        val first = createViewModel(firstHandle, persistence)
        waitUntil { first.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            first.onAction(GameAction.SelectMission(ManchesterContent.FIRST_MISSION_ID))
            first.onAction(GameAction.StartSelectedMission)
        }
        waitUntil { persistence.savedSession.get() != null }
        val recreatedHandle = firstHandle.copyForRecreation()
        val recreated = createViewModel(recreatedHandle, FakeGamePersistence(persistence.data.value))

        waitUntil { recreated.uiState.screen == AppScreen.GAME && !recreated.uiState.isRestoring }

        assertTrue(recreated.uiState.canContinue)
        assertEquals(ManchesterContent.FIRST_MISSION_ID, recreated.uiState.selectedMissionId)
        assertNotNull(recreated.uiState.tutorialStep)
    }

    @Test
    fun navigationScreenSurvivesStateHandleRecreation() {
        val persistence = FakeGamePersistence(PlayerData())
        val firstHandle = SavedStateHandle()
        val first = createViewModel(firstHandle, persistence)
        waitUntil { first.uiState.settingsLoaded }
        instrumentation.runOnMainSync {
            first.onAction(GameAction.Navigate(AppScreen.SETTINGS))
        }

        val recreated = createViewModel(
            firstHandle.copyForRecreation(),
            FakeGamePersistence(persistence.data.value),
        )

        assertEquals(AppScreen.SETTINGS, recreated.uiState.screen)
    }

    @Test
    fun abandoningRequiresConfirmationAndNeverRecordsProgress() {
        val persistence = FakeGamePersistence(
            PlayerData(progress = PlayerProgress(tutorialCompleted = true)),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)
        waitUntil { viewModel.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.StartSelectedMission)
            viewModel.onAction(GameAction.RequestAbandonment)
        }
        assertTrue(viewModel.uiState.abandonConfirmationVisible)
        assertEquals(AppScreen.GAME, viewModel.uiState.screen)

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.CancelAbandonment)
            viewModel.onAction(GameAction.RequestAbandonment)
            viewModel.onAction(GameAction.ConfirmAbandonment)
        }
        waitUntil { persistence.data.value.activeSession == null }

        assertEquals(AppScreen.RESULTS, viewModel.uiState.screen)
        assertFalse(viewModel.uiState.result!!.successful)
        assertEquals(0, viewModel.uiState.result!!.stars)
        assertEquals(0, persistence.recordedMissionResults)
        assertEquals(setOf(ManchesterContent.FIRST_MISSION_ID), persistence.data.value.progress.unlockedMissionIds)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle,
        persistence: GamePersistence,
    ): LiveGameViewModel {
        lateinit var result: LiveGameViewModel
        instrumentation.runOnMainSync {
            result = LiveGameViewModel(application, savedStateHandle, persistence)
        }
        return result
    }

    private fun waitUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(10L)
        }
        assertTrue("Condition was not met within ${timeoutMillis}ms", condition())
    }

    private fun SavedStateHandle.copyForRecreation(): SavedStateHandle = SavedStateHandle(
        keys().associateWith { key -> get<Any?>(key) },
    )
}

private class FakeGamePersistence(initial: PlayerData) : GamePersistence {
    val data = MutableStateFlow(initial)
    val savedSession = AtomicReference<ActiveSessionRecord?>()
    var recordedMissionResults = 0
    override val playerData = data.asStateFlow()

    override suspend fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        data.value = data.value.copy(settings = transform(data.value.settings))
    }

    override suspend fun setTutorialCompleted() {
        data.value = data.value.copy(
            progress = data.value.progress.copy(tutorialCompleted = true),
        )
    }

    override suspend fun recordMissionResult(missionId: String, stars: Int, score: Int) {
        recordedMissionResults += 1
        val unlocked = unlockedAfterMissionCompletion(
            data.value.progress.unlockedMissionIds,
            missionId,
        )
        data.value = data.value.copy(
            progress = data.value.progress.copy(
                missionStars = data.value.progress.missionStars +
                    (missionId to maxOf(data.value.progress.missionStars[missionId] ?: 0, stars)),
                missionBestScores = data.value.progress.missionBestScores +
                    (missionId to maxOf(data.value.progress.missionBestScores[missionId] ?: 0, score)),
                unlockedMissionIds = unlocked,
            ),
        )
    }

    override suspend fun recordEndlessHighScore(score: Int) {
        data.value = data.value.copy(
            progress = data.value.progress.copy(endlessHighScore = score),
        )
    }

    override suspend fun saveActiveSession(session: ActiveSessionRecord) {
        savedSession.set(session)
        data.value = data.value.copy(activeSession = session)
    }

    override suspend fun clearActiveSession() {
        savedSession.set(null)
        data.value = data.value.copy(activeSession = null)
    }
}
