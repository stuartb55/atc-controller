package com.stuart.atccontroller.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stuart.atccontroller.data.ActiveSessionRecord
import com.stuart.atccontroller.data.CompletedReplayRecord
import com.stuart.atccontroller.data.CoastalContent
import com.stuart.atccontroller.data.ContentRegistry
import com.stuart.atccontroller.data.EndlessMilestoneChoice
import com.stuart.atccontroller.data.EndlessMilestoneRecord
import com.stuart.atccontroller.data.ManchesterContent
import com.stuart.atccontroller.data.PlayerData
import com.stuart.atccontroller.data.PlayerProgress
import com.stuart.atccontroller.data.PlayerSettings
import com.stuart.atccontroller.data.TrainingState
import com.stuart.atccontroller.data.unlockedAfterMissionCompletion
import com.stuart.atccontroller.simulation.CommandRejectionReason
import com.stuart.atccontroller.simulation.GameEvent
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
        val parallel = ManchesterContent.missionIds[7]
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
        assertEquals(16, viewModel.uiState.career.totalShifts)
        assertEquals(3, viewModel.uiState.career.earnedStars)
        assertEquals(48, viewModel.uiState.career.availableStars)
        assertEquals(14, mission.trafficCount)
        assertEquals("23R / 23L", mission.runwayLabel)
        assertEquals("240°/10kt", mission.wind)
        assertEquals(20, mission.visibilityKm)
        assertEquals(780, mission.durationSeconds)
        assertEquals(4, mission.objectives.size)
        assertEquals(12_345, mission.bestScore)
        val manchesterEndless = viewModel.uiState.missions.first { it.id == "endless" }
        assertEquals(null, manchesterEndless.bestStars)
        assertEquals(98_765, manchesterEndless.bestScore)
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
                    routeSnappingEnabled = false,
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
            viewModel.onAction(GameAction.ToggleRouteSnapping)
            viewModel.onAction(GameAction.TogglePauseOnFocusLoss)
        }

        assertEquals(.37f, viewModel.uiState.settings.musicVolume, 0.001f)
        assertEquals(.42f, viewModel.uiState.settings.effectsVolume, 0.001f)
        assertTrue(viewModel.uiState.settings.labelDeclutteringEnabled)
        assertTrue(viewModel.uiState.settings.routeSnappingEnabled)
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
        val expectedObjectives = first.uiState.objectiveProgress
        val expectedUpcomingTraffic = first.uiState.upcomingTraffic
        val expectedStarForecast = first.uiState.starForecast
        val recreatedHandle = firstHandle.copyForRecreation()
        val recreated = createViewModel(recreatedHandle, FakeGamePersistence(persistence.data.value))

        waitUntil { recreated.uiState.screen == AppScreen.GAME && !recreated.uiState.isRestoring }

        assertTrue(recreated.uiState.canContinue)
        assertEquals(ManchesterContent.FIRST_MISSION_ID, recreated.uiState.selectedMissionId)
        assertNotNull(recreated.uiState.tutorialStep)
        assertEquals(expectedObjectives, recreated.uiState.objectiveProgress)
        assertEquals(expectedUpcomingTraffic, recreated.uiState.upcomingTraffic)
        assertEquals(expectedStarForecast, recreated.uiState.starForecast)
        assertEquals(MissionClockState.ACTIVE, recreated.uiState.missionClockState)
    }

    @Test
    fun coastalCampaignSaveReconstructsWithoutManchesterRunwaysOrFixes() {
        val persistence = FakeGamePersistence(
            PlayerData(progress = PlayerProgress(tutorialCompleted = true)),
        )
        val firstHandle = SavedStateHandle()
        val first = createViewModel(firstHandle, persistence)
        waitUntil { first.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            first.onAction(GameAction.SelectMission(CoastalContent.FIRST_MISSION_ID))
            first.onAction(GameAction.StartSelectedMission)
        }
        waitUntil { persistence.savedSession.get()?.scenarioId == CoastalContent.FIRST_MISSION_ID }

        assertTrue(first.uiState.runways.all { it.id in setOf("09", "27", "18", "36") })
        assertTrue(first.uiState.fixes.any { it.name == "Harbour East" })
        assertTrue(first.uiState.fixes.none { it.name == "North Gate" })

        val recreated = createViewModel(
            firstHandle.copyForRecreation(),
            FakeGamePersistence(persistence.data.value),
        )
        waitUntil { recreated.uiState.screen == AppScreen.GAME && !recreated.uiState.isRestoring }

        assertEquals(CoastalContent.FIRST_MISSION_ID, recreated.uiState.selectedMissionId)
        assertTrue(recreated.uiState.runways.all { it.id in setOf("09", "27", "18", "36") })
        assertTrue(recreated.uiState.fixes.any { it.name == "Harbour East" })
    }

    @Test
    fun routeEditsSerializeAndReconstructWithTheSelectedFlightStrip() {
        val persistence = FakeGamePersistence(
            PlayerData(progress = PlayerProgress(tutorialCompleted = true)),
        )
        val firstHandle = SavedStateHandle()
        val first = createViewModel(firstHandle, persistence)
        waitUntil { first.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            first.onAction(GameAction.StartSelectedMission)
            first.onAction(GameAction.SelectFlightStrip("m01_a1"))
            first.onAction(GameAction.DirectToFix("North-east Gate"))
            first.onAction(GameAction.AppendFix("North Gate"))
        }
        waitUntil {
            persistence.savedSession.get()?.payload?.contains("|P|m01_a1|") == true
        }

        val recreatedPersistence = FakeGamePersistence(persistence.data.value)
        val recreated = createViewModel(firstHandle.copyForRecreation(), recreatedPersistence)
        waitUntil { recreated.uiState.screen == AppScreen.GAME && !recreated.uiState.isRestoring }

        assertEquals("m01_a1", recreated.uiState.selectedAircraftId)
        assertEquals(2, recreated.uiState.selectedAircraft!!.route.size)
        assertEquals(
            recreated.uiState.aircraft.map { it.id }.toSet(),
            recreated.uiState.flightStrips.map { it.aircraftId }.toSet(),
        )
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
        assertEquals(ContentRegistry.firstMissionIds, persistence.data.value.progress.unlockedMissionIds)
    }

    @Test
    fun tutorialRestoreAndUpdatesPreserveCompletedLessons() {
        val legacySelectionLesson = "SELECTION_AND_ROUTING"
        val selectionLesson = "selection_and_routing"
        val persistence = FakeGamePersistence(
            PlayerData(
                trainingState = TrainingState(
                    activeLessonId = legacySelectionLesson,
                    activeStep = 2,
                    completedLessonIds = setOf("ALTITUDE"),
                ),
            ),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)
        waitUntil { viewModel.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.StartSelectedMission)
        }
        assertEquals(2, viewModel.uiState.tutorialStep)

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.AdvanceTutorial)
        }
        waitUntil { persistence.data.value.trainingState.activeStep == 3 }
        assertEquals(
            setOf("ALTITUDE"),
            persistence.data.value.trainingState.completedLessonIds,
        )

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.AdvanceTutorial)
        }
        waitUntil { selectionLesson in persistence.data.value.trainingState.completedLessonIds }
        assertEquals(
            setOf("ALTITUDE", selectionLesson),
            persistence.data.value.trainingState.completedLessonIds,
        )
    }

    @Test
    fun academyUsesRealActionsAndCallsignsWithoutChangingRankedProgress() {
        val firstMission = ManchesterContent.FIRST_MISSION_ID
        val persistence = FakeGamePersistence(
            PlayerData(progress = PlayerProgress(tutorialCompleted = true)),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)
        waitUntil { viewModel.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.StartTrainingLesson(firstMission))
        }

        assertEquals(AppScreen.GAME, viewModel.uiState.screen)
        assertFalse(viewModel.uiState.isPaused)
        assertTrue(viewModel.uiState.training!!.isPractice)
        assertTrue(viewModel.uiState.training!!.prompt.contains("NORTH 201"))
        assertEquals(0, viewModel.uiState.training!!.stepIndex)

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.SelectAircraft("m01_a1"))
        }
        assertEquals(1, viewModel.uiState.training!!.stepIndex)

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.DirectToFix("North-east Gate"))
        }
        assertEquals(2, viewModel.uiState.training!!.stepIndex)

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.DismissTutorial)
        }
        waitUntil { persistence.data.value.activeSession == null }

        assertEquals(AppScreen.MISSIONS, viewModel.uiState.screen)
        assertEquals(0, persistence.recordedMissionResults)
        assertEquals(
            ContentRegistry.firstMissionIds,
            persistence.data.value.progress.unlockedMissionIds,
        )
        assertFalse(
            "Exiting early must not mark a lesson complete",
            "selection_and_routing" in persistence.data.value.trainingState.completedLessonIds,
        )
    }

    @Test
    fun academySessionRestoresTheExactLessonStepAfterProcessDeath() {
        val altitudeMission = ManchesterContent.missionIds[1]
        val persistence = FakeGamePersistence(
            PlayerData(progress = PlayerProgress(tutorialCompleted = true)),
        )
        val firstHandle = SavedStateHandle()
        val first = createViewModel(firstHandle, persistence)
        waitUntil { first.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            first.onAction(GameAction.SelectMission(altitudeMission))
            first.onAction(GameAction.StartTrainingLesson(altitudeMission))
            first.onAction(GameAction.SelectAircraft("m02_a1"))
        }
        waitUntil {
            persistence.savedSession.get()?.payload?.contains("|altitude|1") == true
        }

        val recreatedPersistence = FakeGamePersistence(persistence.data.value)
        val recreated = createViewModel(firstHandle.copyForRecreation(), recreatedPersistence)
        waitUntil { recreated.uiState.screen == AppScreen.GAME && !recreated.uiState.isRestoring }

        assertEquals(altitudeMission, recreated.uiState.selectedMissionId)
        assertEquals("altitude", recreated.uiState.training!!.lessonId)
        assertEquals(1, recreated.uiState.training!!.stepIndex)
        assertTrue(recreated.uiState.training!!.isPractice)
        assertFalse(recreated.uiState.isPaused)
    }

    @Test
    fun customPracticeSavesExactlyAndCannotRecordRankedProgress() {
        val persistence = FakeGamePersistence(
            PlayerData(progress = PlayerProgress(tutorialCompleted = true)),
        )
        val firstHandle = SavedStateHandle()
        val first = createViewModel(firstHandle, persistence)
        waitUntil { first.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            first.onAction(GameAction.SetCustomSeed("424242"))
            first.onAction(GameAction.CycleCustomDensity(1))
            first.onAction(GameAction.ToggleCustomApproachSetup)
            first.onAction(GameAction.StartCustomShift)
        }
        waitUntil { persistence.savedSession.get()?.payload?.contains("D|C|ATC1.") == true }
        val originalIdentity = first.uiState.configurationIdentity

        val recreatedPersistence = FakeGamePersistence(persistence.data.value)
        val recreated = createViewModel(firstHandle.copyForRecreation(), recreatedPersistence)
        waitUntil { recreated.uiState.screen == AppScreen.GAME && !recreated.uiState.isRestoring }

        assertTrue(recreated.uiState.isPracticeSession)
        assertEquals(originalIdentity, recreated.uiState.configurationIdentity)
        assertFalse(recreated.uiState.approachSetupAssistEnabled)

        instrumentation.runOnMainSync {
            recreated.onAction(GameAction.RequestAbandonment)
            recreated.onAction(GameAction.ConfirmAbandonment)
        }
        assertEquals(0, recreatedPersistence.recordedMissionResults)
        assertEquals(
            ContentRegistry.firstMissionIds,
            recreatedPersistence.data.value.progress.unlockedMissionIds,
        )
    }

    @Test
    fun endlessMilestoneCanBeReopenedAndCashedOutExactlyOnce() {
        val milestone = EndlessMilestoneRecord(
            seed = 42L,
            completedStage = 3,
            stageScore = 2_400,
            cumulativeScore = 7_100,
        )
        val persistence = FakeGamePersistence(
            PlayerData(
                progress = PlayerProgress(endlessHighScore = 6_500),
                endlessMilestone = milestone,
            ),
        )
        val viewModel = createViewModel(SavedStateHandle(), persistence)
        waitUntil { viewModel.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.ContinueLastGame)
        }
        assertEquals(AppScreen.MILESTONE, viewModel.uiState.screen)
        assertEquals(3, viewModel.uiState.endlessMilestone!!.completedStage)

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.CashOutEndlessRun)
            viewModel.onAction(GameAction.CashOutEndlessRun)
        }
        waitUntil { viewModel.uiState.screen == AppScreen.RESULTS }

        assertEquals(7_100, persistence.data.value.progress.endlessHighScore)
        assertEquals(1, persistence.recordedEndlessHighScores)
        assertEquals(null, persistence.data.value.endlessMilestone)
        assertEquals(7_100, viewModel.uiState.result!!.score)
        assertTrue(viewModel.uiState.result!!.personalBest)
    }

    @Test
    fun pendingEndlessChoicesRecoverAcrossProcessDeath() {
        val cashPersistence = FakeGamePersistence(
            PlayerData(
                endlessMilestone = EndlessMilestoneRecord(
                    seed = 99L,
                    completedStage = 2,
                    stageScore = 1_500,
                    cumulativeScore = 3_500,
                    choice = EndlessMilestoneChoice.CASH_OUT_PENDING,
                ),
            ),
        )
        val cashedOut = createViewModel(SavedStateHandle(), cashPersistence)
        waitUntil { cashedOut.uiState.screen == AppScreen.RESULTS }
        assertEquals(3_500, cashPersistence.data.value.progress.endlessHighScore)
        assertEquals(null, cashPersistence.data.value.endlessMilestone)

        val continuePersistence = FakeGamePersistence(
            PlayerData(
                progress = PlayerProgress(tutorialCompleted = true),
                endlessMilestone = EndlessMilestoneRecord(
                    seed = 99L,
                    completedStage = 2,
                    stageScore = 1_500,
                    cumulativeScore = 3_500,
                    choice = EndlessMilestoneChoice.CONTINUE_PENDING,
                ),
            ),
        )
        val continued = createViewModel(SavedStateHandle(), continuePersistence)
        waitUntil {
            continued.uiState.screen == AppScreen.GAME &&
                continuePersistence.data.value.activeSession != null &&
                continuePersistence.data.value.endlessMilestone == null
        }

        assertTrue(continued.uiState.selectedMission?.isEndless == true)
        assertTrue(
            continuePersistence.data.value.activeSession!!.payload.contains("D|E|99|3|3500"),
        )
    }

    @Test
    fun backgroundingAReplayStopsPlaybackWithoutPausingTheReplayEngine() {
        val scenarioId = ManchesterContent.FIRST_MISSION_ID
        val replay = CompletedReplayRecord(
            schemaVersion = 2,
            id = "replay-1",
            scenarioId = scenarioId,
            savedAtEpochMillis = 1L,
            terminalTick = 0L,
            finalScore = 0,
            terminalHash = "intentionally-invalid",
            payload = "replay-v2\nD|A|$scenarioId\nS|0|1.0|true|",
        )
        val viewModel = createViewModel(
            SavedStateHandle(),
            FakeGamePersistence(PlayerData(completedReplays = listOf(replay))),
        )
        waitUntil { viewModel.uiState.settingsLoaded }

        instrumentation.runOnMainSync {
            viewModel.onAction(GameAction.StartReplay(replay.id))
            viewModel.onAction(GameAction.ReplayTogglePlay)
            viewModel.onHostStopped()
        }

        assertFalse(viewModel.uiState.replay!!.isPlaying)
        assertFalse(viewModel.uiState.isPaused)
    }

    @Test
    fun eventCaptionsUseStableLocalizedRejectionReasonsInsteadOfEngineText() {
        val rawEngineText = "internal implementation detail"
        val caption = eventCaption(
            event = GameEvent.CommandRejected(
                aircraftId = "a1",
                reason = rawEngineText,
                elapsedSeconds = 12.0,
                reasonCode = CommandRejectionReason.INVALID_SPEED,
            ),
            callsigns = mapOf("a1" to "NORTH 201"),
            resources = application.resources,
        )

        assertEquals(
            application.getString(
                com.stuart.atccontroller.R.string.event_command_rejected,
                "NORTH 201",
                application.getString(com.stuart.atccontroller.R.string.rejection_invalid_speed),
            ),
            caption,
        )
        assertFalse(caption.contains(rawEngineText))
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
    var recordedEndlessHighScores = 0
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

    override suspend fun recordEndlessHighScore(contentPackId: String, score: Int) {
        recordedEndlessHighScores += 1
        val previous = data.value.progress.endlessHighScoreFor(contentPackId)
        data.value = data.value.copy(
            progress = data.value.progress.copy(
                endlessHighScore = if (contentPackId == ContentRegistry.DEFAULT_PACK_ID) {
                    maxOf(data.value.progress.endlessHighScore, score)
                } else {
                    data.value.progress.endlessHighScore
                },
                endlessHighScores = data.value.progress.endlessHighScores +
                    (contentPackId to maxOf(previous, score)),
            ),
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

    override suspend fun saveTrainingState(state: TrainingState) {
        data.value = data.value.copy(trainingState = state)
    }

    override suspend fun saveCompletedReplay(replay: CompletedReplayRecord) {
        data.value = data.value.copy(completedReplays = listOf(replay) + data.value.completedReplays)
    }

    override suspend fun deleteCompletedReplay(id: String) {
        data.value = data.value.copy(
            completedReplays = data.value.completedReplays.filterNot { it.id == id },
        )
    }

    override suspend fun saveEndlessMilestone(milestone: EndlessMilestoneRecord) {
        savedSession.set(null)
        data.value = data.value.copy(activeSession = null, endlessMilestone = milestone)
    }

    override suspend fun clearEndlessMilestone() {
        data.value = data.value.copy(endlessMilestone = null)
    }
}
