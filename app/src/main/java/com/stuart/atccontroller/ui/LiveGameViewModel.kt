package com.stuart.atccontroller.ui

import android.app.Application
import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.stuart.atccontroller.R
import com.stuart.atccontroller.data.ActiveSessionRecord
import com.stuart.atccontroller.data.CompletedReplayRecord
import com.stuart.atccontroller.data.ControllerServiceRecord
import com.stuart.atccontroller.data.ContentRegistry
import com.stuart.atccontroller.data.CustomShiftGenerator
import com.stuart.atccontroller.data.DailyShift
import com.stuart.atccontroller.data.EndlessScenarioGenerator
import com.stuart.atccontroller.data.EndlessMilestoneChoice
import com.stuart.atccontroller.data.EndlessMilestoneRecord
import com.stuart.atccontroller.data.FixUse
import com.stuart.atccontroller.data.FuelPressure
import com.stuart.atccontroller.data.PlayerData
import com.stuart.atccontroller.data.PlayerPreferencesRepository
import com.stuart.atccontroller.data.PlayerProgress
import com.stuart.atccontroller.data.PlayerSettings
import com.stuart.atccontroller.data.PracticeResultRecord
import com.stuart.atccontroller.data.RunwayDirection
import com.stuart.atccontroller.data.ScenarioDefinition as ContentScenarioDefinition
import com.stuart.atccontroller.data.ShiftConfiguration
import com.stuart.atccontroller.data.ShiftConfigurationCodec
import com.stuart.atccontroller.data.TrafficSpawnDefinition
import com.stuart.atccontroller.data.TrainingAcademy
import com.stuart.atccontroller.data.TrainingAction
import com.stuart.atccontroller.data.TrainingState
import com.stuart.atccontroller.data.TrainingTargetOperation
import com.stuart.atccontroller.data.TrafficDensity
import com.stuart.atccontroller.data.ValidatedMissionResult
import com.stuart.atccontroller.data.WeatherPreset
import com.stuart.atccontroller.data.TutorialFocus
import com.stuart.atccontroller.data.atcControllerDataStore
import com.stuart.atccontroller.data.toSimulationScenario
import com.stuart.atccontroller.simulation.AircraftState
import com.stuart.atccontroller.simulation.AircraftStatus
import com.stuart.atccontroller.simulation.AircraftType
import com.stuart.atccontroller.simulation.AtcSimulationEngine
import com.stuart.atccontroller.simulation.Clearance
import com.stuart.atccontroller.simulation.CancelledClearanceType
import com.stuart.atccontroller.simulation.CommandRejectionReason
import com.stuart.atccontroller.simulation.Conflict
import com.stuart.atccontroller.simulation.ConflictKind
import com.stuart.atccontroller.simulation.FailureReason
import com.stuart.atccontroller.simulation.FlightOperation
import com.stuart.atccontroller.simulation.FlightOutcome
import com.stuart.atccontroller.simulation.DynamicEventLifecycle
import com.stuart.atccontroller.simulation.DynamicEventType
import com.stuart.atccontroller.simulation.DynamicRecoveryGoal
import com.stuart.atccontroller.simulation.GameEvent
import com.stuart.atccontroller.simulation.GameSnapshot
import com.stuart.atccontroller.simulation.GameStatus
import com.stuart.atccontroller.simulation.HandoffStatus
import com.stuart.atccontroller.simulation.HoldTurnDirection
import com.stuart.atccontroller.simulation.PlayerCommand
import com.stuart.atccontroller.simulation.Route
import com.stuart.atccontroller.simulation.Vec2
import java.text.NumberFormat
import java.time.LocalDate
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A one-shot cue consumed by [com.stuart.atccontroller.MainActivity]. */
data class LiveFeedbackCue(
    val sequence: Long,
    val kind: LiveFeedbackKind,
)

enum class LiveFeedbackKind { CONFIRMATION, SUCCESS, WARNING, FAILURE }

/** Narrow persistence boundary used by the production state holder and deterministic tests. */
internal interface GamePersistence {
    val playerData: Flow<PlayerData>

    suspend fun updateSettings(transform: (PlayerSettings) -> PlayerSettings)
    suspend fun setTutorialCompleted()
    suspend fun recordMissionResult(missionId: String, stars: Int, score: Int)
    suspend fun recordValidatedMissionResult(result: ValidatedMissionResult) =
        recordMissionResult(result.missionId, result.stars, result.score)
    suspend fun recordEndlessHighScore(contentPackId: String, score: Int)
    suspend fun saveActiveSession(session: ActiveSessionRecord)
    suspend fun clearActiveSession()
    suspend fun saveTrainingState(state: TrainingState) = Unit
    suspend fun saveCompletedReplay(replay: CompletedReplayRecord) = Unit
    suspend fun deleteCompletedReplay(id: String) = Unit
    suspend fun savePracticeResult(result: PracticeResultRecord) = Unit
    suspend fun recordDailyResult(
        localDate: LocalDate,
        configurationIdentity: String,
        resultId: String,
        score: Int,
    ) = Unit
    suspend fun saveEndlessMilestone(milestone: EndlessMilestoneRecord) = Unit
    suspend fun clearEndlessMilestone() = Unit
    suspend fun reconcileUnlocks() = Unit
}

private class PreferencesGamePersistence(
    private val repository: PlayerPreferencesRepository,
) : GamePersistence {
    override val playerData = repository.playerData

    override suspend fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) =
        repository.updateSettings(transform)

    override suspend fun setTutorialCompleted() = repository.setTutorialCompleted()

    override suspend fun recordMissionResult(missionId: String, stars: Int, score: Int) =
        repository.recordMissionResult(missionId, stars, score)

    override suspend fun recordValidatedMissionResult(result: ValidatedMissionResult) =
        repository.recordValidatedMissionResult(result)

    override suspend fun recordEndlessHighScore(contentPackId: String, score: Int) =
        repository.recordEndlessHighScore(contentPackId, score)

    override suspend fun saveActiveSession(session: ActiveSessionRecord) =
        repository.saveActiveSession(session)

    override suspend fun clearActiveSession() = repository.clearActiveSession()
    override suspend fun saveTrainingState(state: TrainingState) = repository.saveTrainingState(state)
    override suspend fun saveCompletedReplay(replay: CompletedReplayRecord) =
        repository.saveCompletedReplay(replay)

    override suspend fun deleteCompletedReplay(id: String) = repository.deleteCompletedReplay(id)

    override suspend fun savePracticeResult(result: PracticeResultRecord) =
        repository.savePracticeResult(result)

    override suspend fun recordDailyResult(
        localDate: LocalDate,
        configurationIdentity: String,
        resultId: String,
        score: Int,
    ) = repository.recordDailyResult(localDate, configurationIdentity, resultId, score)

    override suspend fun saveEndlessMilestone(milestone: EndlessMilestoneRecord) =
        repository.saveEndlessMilestone(milestone)

    override suspend fun clearEndlessMilestone() = repository.clearEndlessMilestone()
    override suspend fun reconcileUnlocks() = repository.reconcileUnlocks()
}

/**
 * Production presentation adapter for the authored content, deterministic simulation, and Compose
 * UI contract. The engine is advanced from a single main-thread coroutine at its native 10 Hz.
 *
 * The engine has no mutable snapshot import API. For process-death recovery we therefore persist a
 * bounded log of typed player commands with their simulation ticks, then deterministically replay
 * those commands and fixed steps. A five-second periodic checkpoint bounds progress loss if Android
 * kills the process without first delivering the normal activity stop callback.
 */
class LiveGameViewModel internal constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val preferences: GamePersistence,
) : AndroidViewModel(application) {
    private val resources = application.resources
    private val fixNamesByPosition: Map<Vec2, String>
        get() = activeAirport().fixes.associate { fix ->
            Vec2(fix.position.x, fix.position.y) to fix.displayName
        }

    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application = application,
        savedStateHandle = savedStateHandle,
        preferences = PreferencesGamePersistence(
            PlayerPreferencesRepository(application.atcControllerDataStore),
        ),
    )

    private val restoredScreen = savedStateHandle.get<String>(STATE_SCREEN)
        ?.let { encoded -> runCatching { AppScreen.valueOf(encoded) }.getOrNull() }
        ?: AppScreen.HOME
    private val restoredResult = resultFromSavedState()
    private val restoredTutorialState = savedStateHandle.get<Int>(STATE_TUTORIAL_STEP)
    private var restorePersistedSessionOnLoad = restoredScreen == AppScreen.GAME
    private var resumePendingProgressionOnLoad = restoredScreen == AppScreen.RESULTS &&
        restoredResult?.successful == true

    private var playerData = PlayerData()
    private var engine: AtcSimulationEngine? = null
    private var activeDescriptor: SessionDescriptor? = null
    private var activeContent: ContentScenarioDefinition? = null
    private var activeAttemptId: String? = null
    private var lastSnapshot: GameSnapshot? = null
    private var suppressPersistedSession = false
    private var pausedForTutorial = false
    private var pausedForAbandonConfirmation = false
    private var lastCheckpointTick = -1L
    private var checkpointingDisabled = false
    private var restoreJob: Job? = null
    private var checkpointJob: Job? = null
    private var sessionValidationJob: Job? = null
    private var validatedSessionKey: SessionRecordKey? = null
    private var validatedSessionAvailable = false
    private var feedbackSequence = 0L
    private var conflictAnnouncementSequence = 0L
    private var lastConflictAnnouncementKey: String? = null
    private var pendingProgression: PendingProgression? = null
    private val trails = mutableMapOf<String, MutableList<NormalizedPoint>>()
    private val replayLog = mutableListOf<ReplayEntry>()
    private var replayController: ReplayController? = null
    private var trainingRejectionMessage: String? = null
    private var suppressTrainingObservation = false
    private var customConfiguration = ShiftConfiguration()
    private var resumeMilestoneActionOnLoad = true

    var uiState by mutableStateOf(
        initialUiState(resources).copy(
            screen = when {
                restoredScreen == AppScreen.RESULTS && restoredResult == null -> AppScreen.HOME
                else -> restoredScreen
            },
            selectedMissionId = savedStateHandle.get<String>(STATE_SELECTED_MISSION)
                ?: ContentRegistry.firstMissionIds.first(),
            result = restoredResult,
            progressionSaveStatus = savedStateHandle.get<String>(STATE_PROGRESSION_STATUS)
                ?.let { runCatching { ProgressionSaveStatus.valueOf(it) }.getOrNull() }
                ?: ProgressionSaveStatus.NOT_REQUIRED,
            nextMissionId = savedStateHandle.get<String>(STATE_NEXT_MISSION),
            tutorialStep = restoredTutorialState?.takeIf { it in 0 until MAX_TRAINING_STEPS },
            isRestoring = restorePersistedSessionOnLoad,
        ),
    )
        private set

    var feedbackCue by mutableStateOf<LiveFeedbackCue?>(null)
        private set

    init {
        viewModelScope.launch { preferences.reconcileUnlocks() }
        viewModelScope.launch {
            preferences.playerData.collectLatest(::applyPlayerData)
        }
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                if (replayController != null) {
                    if (uiState.replay?.isPlaying == true) {
                        advanceReplay(uiState.replay?.speed ?: 1)
                    }
                    continue
                }
                val runningEngine = engine ?: continue
                if (uiState.screen != AppScreen.GAME) continue
                if (runningEngine.snapshot.status == GameStatus.RUNNING) {
                    publish(runningEngine.advance(TICK_SECONDS))
                }
            }
        }
    }

    fun onAction(action: GameAction) {
        if (replayController != null && action !is GameAction.Navigate &&
            action !is GameAction.SelectAircraft && action !is GameAction.ReplayTogglePlay &&
            action !is GameAction.SelectFlightStrip &&
            action !is GameAction.ReplayStep && action !is GameAction.ReplaySetSpeed &&
            action !is GameAction.ReplaySeek && action !is GameAction.ReplayFollowAircraft
        ) return
        when (action) {
            is GameAction.Navigate -> navigate(action.screen)
            is GameAction.SelectMission -> selectMission(action.id)
            GameAction.StartSelectedMission -> startSelectedMission()
            is GameAction.StartTrainingLesson -> startTrainingLesson(action.missionId)
            is GameAction.SetCustomSeed -> updateCustomConfiguration { current ->
                action.seed.toLongOrNull()?.let { current.copy(seed = it) } ?: current
            }
            is GameAction.CycleCustomContentPack -> updateCustomConfiguration {
                val packIds = ContentRegistry.packs.map { pack -> pack.id }
                it.copy(contentPackId = packIds.cycle(it.contentPackId, action.offset))
            }
            is GameAction.CycleCustomDensity -> updateCustomConfiguration {
                it.copy(density = TrafficDensity.entries.cycle(it.density, action.offset))
            }
            is GameAction.SetCustomArrivalPercent -> updateCustomConfiguration {
                it.copy(arrivalPercent = (action.percent.coerceIn(20, 80) / 10) * 10)
            }
            is GameAction.CycleCustomRunwayDirection -> updateCustomConfiguration {
                it.copy(runwayDirection = RunwayDirection.entries.cycle(it.runwayDirection, action.offset))
            }
            is GameAction.CycleCustomWeather -> updateCustomConfiguration {
                it.copy(weatherPreset = WeatherPreset.entries.cycle(it.weatherPreset, action.offset))
            }
            is GameAction.CycleCustomFuelPressure -> updateCustomConfiguration {
                it.copy(fuelPressure = FuelPressure.entries.cycle(it.fuelPressure, action.offset))
            }
            is GameAction.SetCustomStrikeLimit -> updateCustomConfiguration {
                it.copy(strikeLimit = action.limit.coerceIn(1, 5))
            }
            GameAction.ToggleCustomRouteSnapping -> updateCustomConfiguration {
                it.copy(assists = it.assists.copy(routeSnapping = !it.assists.routeSnapping))
            }
            GameAction.ToggleCustomApproachSetup -> updateCustomConfiguration {
                it.copy(assists = it.assists.copy(approachSetup = !it.assists.approachSetup))
            }
            GameAction.ToggleCustomConflictPrediction -> updateCustomConfiguration {
                it.copy(assists = it.assists.copy(conflictPrediction = !it.assists.conflictPrediction))
            }
            GameAction.UseRankedSeededPreset -> {
                customConfiguration = ShiftConfiguration.rankedPreset(
                    customConfiguration.seed,
                    customConfiguration.contentPackId,
                )
                uiState = uiState.copy(customShift = customShiftUiWithDaily(customConfiguration))
            }
            GameAction.StartCustomShift -> startScenario(SessionDescriptor.Custom(customConfiguration))
            is GameAction.SetShareCodeInput -> uiState = uiState.copy(
                customShift = uiState.customShift.copy(
                    importCode = action.code.take(512),
                    importError = null,
                ),
            )
            GameAction.ImportShareCode -> {
                val imported = ShiftConfigurationCodec.decode(uiState.customShift.importCode)
                if (imported == null) {
                    uiState = uiState.copy(
                        customShift = uiState.customShift.copy(
                            importError = resources.getString(R.string.share_code_invalid),
                        ),
                    )
                } else {
                    customConfiguration = imported.copy(rankedPresetId = null)
                    uiState = uiState.copy(customShift = customShiftUiWithDaily(customConfiguration))
                }
            }
            GameAction.StartDailyShift -> {
                val date = LocalDate.now()
                startScenario(SessionDescriptor.Daily(date, DailyShift.configurationFor(date)))
            }
            GameAction.ContinueEndlessRun -> continueEndlessRun()
            GameAction.CashOutEndlessRun -> cashOutEndlessRun()
            GameAction.ContinueLastGame -> continueLastGame()
            is GameAction.SelectAircraft -> {
                selectAircraft(action.id)
                if (replayController != null) {
                    uiState = uiState.copy(
                        replay = uiState.replay?.copy(followedAircraftId = action.id),
                    )
                }
            }
            is GameAction.CommitRoute -> submitRoute(action.points, action.terminalTarget)
            is GameAction.DirectToFix -> routeToFix(action.name, append = false)
            is GameAction.AppendFix -> routeToFix(action.name, append = true)
            GameAction.UndoWaypoint -> submitForSelected { PlayerCommand.UndoWaypoint(it.id) }
            GameAction.ClearRoute -> submitForSelected { PlayerCommand.ClearRoute(it.id) }
            is GameAction.SelectEvent -> selectAircraft(action.aircraftId ?: uiState.eventFeed
                .firstOrNull { it.sequence == action.sequence }?.aircraftIds?.firstOrNull())
            is GameAction.SelectFlightStrip -> {
                selectAircraft(action.aircraftId)
                if (replayController != null) {
                    uiState = uiState.copy(
                        replay = uiState.replay?.copy(followedAircraftId = action.aircraftId),
                    )
                }
            }
            is GameAction.SetTargetAltitude -> submitForSelected { aircraft ->
                PlayerCommand.SetTargetAltitude(
                    aircraftId = aircraft.id,
                    altitudeFeet = action.feet.coerceIn(0, 12_000).toDouble(),
                )
            }
            is GameAction.SetTargetSpeed -> submitForSelected { aircraft ->
                PlayerCommand.SetTargetSpeed(
                    aircraftId = aircraft.id,
                    speedKnots = action.knots.coerceIn(80, aircraft.type.maxSpeedKnots.toInt()).toDouble(),
                )
            }
            GameAction.PrepareApproach -> prepareApproach()
            is GameAction.IssueClearance -> issueClearance(action.type)
            is GameAction.AssignRunway -> submitForSelected {
                PlayerCommand.AssignRunway(it.id, action.runwayId)
            }
            is GameAction.AssignApproach -> submitForSelected {
                PlayerCommand.AssignApproach(it.id, action.runwayId)
            }
            GameAction.CancelApproach -> submitForSelected { PlayerCommand.CancelApproach(it.id) }
            GameAction.LineUpAndWait -> submitForSelected { aircraft ->
                PlayerCommand.LineUpAndWait(
                    aircraft.id,
                    aircraft.runwayId ?: lastSnapshot?.runways?.firstOrNull()?.id.orEmpty(),
                )
            }
            GameAction.CancelLandingClearance -> submitForSelected {
                PlayerCommand.CancelLandingClearance(it.id)
            }
            GameAction.CancelTakeoffClearance -> submitForSelected {
                PlayerCommand.CancelTakeoffClearance(it.id)
            }
            is GameAction.AssignHold -> submitForSelected { aircraft ->
                val fix = activeAirport().fixes.firstOrNull {
                    it.displayName == action.fixName
                } ?: return@submitForSelected PlayerCommand.CancelHold(aircraft.id)
                PlayerCommand.AssignHold(
                    aircraftId = aircraft.id,
                    fix = Vec2(fix.position.x, fix.position.y),
                    inboundCourseDegrees = aircraft.headingDegrees,
                    altitudeFeet = aircraft.targetAltitudeFeet.coerceIn(1_000.0, 20_000.0),
                    turnDirection = if (action.rightTurns) {
                        HoldTurnDirection.RIGHT
                    } else {
                        HoldTurnDirection.LEFT
                    },
                    legSeconds = action.legSeconds.coerceIn(20, 180).toDouble(),
                )
            }
            GameAction.CancelHold -> submitForSelected { PlayerCommand.CancelHold(it.id) }
            GameAction.IssueExitClearance -> submitForSelected {
                PlayerCommand.IssueExitClearance(it.id)
            }
            GameAction.AcknowledgeInboundHandoff -> submitForSelected {
                PlayerCommand.AcknowledgeInboundHandoff(it.id)
            }
            GameAction.InitiateOutboundHandoff -> submitForSelected {
                PlayerCommand.InitiateOutboundHandoff(it.id)
            }
            is GameAction.CrossRunway -> submitForSelected {
                PlayerCommand.CrossRunway(it.id, action.runwayId)
            }
            is GameAction.AcknowledgeDynamicEvent -> submit(
                PlayerCommand.AcknowledgeDynamicEvent(action.eventId),
            )
            GameAction.TogglePause -> togglePause()
            is GameAction.SetTimeScale -> {
                submit(PlayerCommand.SetSimulationSpeed(action.multiplier.coerceIn(1, 2).toDouble()))
                checkpoint()
            }
            GameAction.AdvanceTutorial -> advanceTutorial()
            GameAction.DismissTutorial -> dismissTutorial()
            GameAction.RequestAbandonment -> requestAbandonment()
            GameAction.ConfirmAbandonment -> confirmAbandonment()
            GameAction.CancelAbandonment -> cancelAbandonment()
            GameAction.RetryProgressionPersistence -> persistPendingProgression()
            GameAction.OpenNextMission -> openNextMission()
            GameAction.RestartMission -> activeDescriptor?.let(::startScenario)
            is GameAction.SetMusicVolume -> updateSettings { settings ->
                val volume = action.volume.finiteOrZero().coerceIn(0f, 1f)
                settings.copy(
                    musicVolume = volume,
                    lastMusicVolume = volume.takeIf { it > 0f } ?: settings.lastMusicVolume,
                )
            }
            is GameAction.SetEffectsVolume -> updateSettings { settings ->
                val volume = action.volume.finiteOrZero().coerceIn(0f, 1f)
                settings.copy(
                    effectsVolume = volume,
                    lastEffectsVolume = volume.takeIf { it > 0f } ?: settings.lastEffectsVolume,
                )
            }
            GameAction.ToggleMusicMute -> updateSettings { settings ->
                if (settings.musicVolume > 0f) {
                    settings.copy(musicVolume = 0f, lastMusicVolume = settings.musicVolume)
                } else {
                    settings.copy(musicVolume = settings.lastMusicVolume)
                }
            }
            GameAction.ToggleEffectsMute -> updateSettings { settings ->
                if (settings.effectsVolume > 0f) {
                    settings.copy(effectsVolume = 0f, lastEffectsVolume = settings.effectsVolume)
                } else {
                    settings.copy(effectsVolume = settings.lastEffectsVolume)
                }
            }
            GameAction.ToggleHaptics -> updateSettings { it.copy(hapticsEnabled = !it.hapticsEnabled) }
            GameAction.ToggleTrails -> updateSettings { it.copy(trailsEnabled = !it.trailsEnabled) }
            GameAction.ToggleReducedMotion -> updateSettings { it.copy(reducedMotion = !it.reducedMotion) }
            GameAction.ToggleHighContrast -> updateSettings { it.copy(highContrast = !it.highContrast) }
            GameAction.ToggleLabelDecluttering -> updateSettings {
                it.copy(labelDeclutteringEnabled = !it.labelDeclutteringEnabled)
            }
            GameAction.ToggleRouteSnapping -> updateSettings {
                it.copy(routeSnappingEnabled = !it.routeSnappingEnabled)
            }
            GameAction.TogglePauseOnFocusLoss -> updateSettings {
                it.copy(pauseOnFocusLoss = !it.pauseOnFocusLoss)
            }
            is GameAction.SetLabelScale -> updateSettings {
                it.copy(labelScale = action.scale.coerceIn(0.8f, 1.4f))
            }
            is GameAction.CycleConflict -> cycleConflict(action.offset)
            is GameAction.StartReplay -> startReplay(action.replayId)
            is GameAction.DeleteReplay -> deleteReplay(action.replayId)
            GameAction.ReplayTogglePlay -> uiState = uiState.copy(
                replay = uiState.replay?.copy(isPlaying = uiState.replay?.isPlaying != true),
            )
            GameAction.ReplayStep -> advanceReplay(1)
            is GameAction.ReplaySetSpeed -> uiState = uiState.copy(
                replay = uiState.replay?.copy(speed = action.multiplier.coerceIn(1, 4)),
            )
            is GameAction.ReplaySeek -> seekReplay(action.tick)
            is GameAction.ReplayFollowAircraft -> {
                selectAircraft(action.aircraftId)
                uiState = uiState.copy(
                    replay = uiState.replay?.copy(followedAircraftId = action.aircraftId),
                )
            }
        }
    }

    /** Always pauses and checkpoints an active game when the activity is no longer visible. */
    fun onHostStopped() {
        pauseAndCheckpoint(forcePause = true)
    }

    /** Obeys the persisted pause-on-focus-loss preference for transient focus changes. */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) pauseAndCheckpoint(forcePause = false)
    }

    fun consumeFeedback(sequence: Long) {
        if (feedbackCue?.sequence == sequence) feedbackCue = null
    }

    /** Re-resolves presentation strings after Android recreates the host for a locale change. */
    fun refreshLocalizedContent() {
        val snapshot = lastSnapshot
        val descriptor = activeDescriptor
        val result = uiState.result
        val localizedResult = if (result != null && descriptor != null) {
            val scenarioTitle = localizedScenarioTitle(descriptor)
            result.copy(
                title = when {
                    result.successful -> scenarioTitle
                    snapshot?.status == GameStatus.FAILED -> resources.getString(
                        R.string.result_failure_title,
                        scenarioTitle,
                        snapshot.failureReason.toDisplayText(resources),
                    )
                    snapshot?.status?.isActive() == true -> resources.getString(
                        R.string.result_score_not_recorded,
                        scenarioTitle,
                    )
                    else -> result.title
                },
            )
        } else {
            result
        }
        val localizedAircraft = snapshot?.let { live ->
            live.aircraft
                .filter(AircraftState::isUiVisible)
                .map { it.toUiModel(live) }
        }
        val callsigns = snapshot?.aircraft?.associate { it.id to it.callsign }.orEmpty()
        uiState = uiState.copy(
            missions = missionModels(resources, playerData.progress),
            career = careerModel(playerData.progress),
            serviceRecord = serviceRecordUi(playerData.serviceRecord, playerData.progress),
            aircraft = localizedAircraft ?: uiState.aircraft,
            eventFeed = snapshot?.toEventFeed(callsigns) ?: uiState.eventFeed,
            training = if (snapshot != null) trainingUi(snapshot) else uiState.training,
            result = localizedResult,
        )
        persistUiState()
    }

    private fun navigate(screen: AppScreen) {
        if (replayController != null && screen != AppScreen.GAME) {
            replayController = null
            engine = null
            lastSnapshot = null
            activeDescriptor = null
            activeContent = null
            activeAttemptId = null
            uiState = uiState.copy(replay = null)
        }
        if (uiState.isRestoring && screen != AppScreen.GAME) {
            restoreJob?.cancel()
            restoreJob = null
            restorePersistedSessionOnLoad = false
        }
        if (uiState.screen == AppScreen.GAME && screen != AppScreen.GAME) {
            pauseAndCheckpoint(forcePause = true)
        }
        uiState = uiState.copy(screen = screen, isRestoring = false)
        persistUiState()
    }

    private fun selectMission(id: String) {
        val mission = uiState.missions.firstOrNull { it.id == id } ?: return
        uiState = uiState.copy(selectedMissionId = mission.id)
        persistUiState()
    }

    private fun startSelectedMission() {
        descriptorForSelection(uiState.selectedMissionId)?.let(::startScenario)
    }

    private fun startTrainingLesson(missionId: String) {
        val mission = ContentRegistry.mission(missionId) ?: return
        val lesson = TrainingAcademy.lessonFor(mission.tutorialFocus) ?: return
        val missionModel = uiState.missions.firstOrNull { it.id == missionId } ?: return
        if (!missionModel.trainingAvailable) return
        val trainingState = playerData.trainingState.copy(
            activeLessonId = lesson.id,
            activeStep = 0,
        )
        playerData = playerData.copy(trainingState = trainingState)
        viewModelScope.launch { preferences.saveTrainingState(trainingState) }
        startScenario(SessionDescriptor.Training(missionId))
    }

    private fun updateCustomConfiguration(
        transform: (ShiftConfiguration) -> ShiftConfiguration,
    ) {
        customConfiguration = runCatching {
            transform(customConfiguration).copy(rankedPresetId = null)
        }.getOrElse { customConfiguration }
        uiState = uiState.copy(customShift = customShiftUiWithDaily(customConfiguration))
    }

    private fun customShiftUiWithDaily(configuration: ShiftConfiguration): CustomShiftUiModel {
        val date = LocalDate.now()
        val entry = playerData.dailyRecord.entries[date.toString()]
        return customShiftUi(configuration).copy(
            shareCode = ShiftConfigurationCodec.encode(configuration),
            importCode = uiState.customShift.importCode,
            dailyDate = date.toString(),
            dailyConfigurationIdentity = DailyShift.identityFor(date).takeLast(20),
            dailyCompleted = entry != null,
            dailyBestScore = entry?.bestScore,
            dailyStreak = playerData.dailyRecord.currentStreak,
        )
    }

    private fun continueLastGame() {
        if (uiState.isRestoring) return
        val snapshot = lastSnapshot
        val runningEngine = engine
        if (runningEngine != null && snapshot != null && snapshot.status.isActive()) {
            val resumed = if (snapshot.status == GameStatus.PAUSED && !pausedForTutorial) {
                runningEngine.submit(PlayerCommand.Resume)
            } else {
                snapshot
            }
            uiState = uiState.copy(screen = AppScreen.GAME, result = null)
            publish(resumed)
            persistUiState()
            return
        }

        playerData.endlessMilestone?.let { milestone ->
            uiState = uiState.copy(
                screen = AppScreen.MILESTONE,
                endlessMilestone = milestone.toUiModel(),
                canContinue = true,
            )
            persistUiState()
            return
        }

        val record = playerData.activeSession
        if (record != null) {
            restoreScenario(record)
        } else {
            uiState = uiState.copy(
                screen = AppScreen.MISSIONS,
                canContinue = false,
                isRestoring = false,
            )
            persistUiState()
        }
    }

    private fun startScenario(descriptor: SessionDescriptor) {
        restoreJob?.cancel()
        checkpointJob?.cancel()
        sessionValidationJob?.cancel()
        restoreJob = null
        checkpointJob = null
        val content = descriptor.content()
        val newEngine = AtcSimulationEngine(content.toSimulationScenario())
        activeDescriptor = descriptor
        activeContent = content
        activeAttemptId = UUID.randomUUID().toString()
        engine = newEngine
        trails.clear()
        replayLog.clear()
        lastConflictAnnouncementKey = null
        lastCheckpointTick = -1L
        checkpointingDisabled = false
        suppressPersistedSession = false
        trainingRejectionMessage = null

        val lesson = TrainingAcademy.lessonFor(content.tutorialFocus)
        val showTutorial = descriptor is SessionDescriptor.Training ||
            (descriptor.selectionId in ContentRegistry.firstMissionIds &&
                !playerData.progress.tutorialCompleted)
        val tutorialStep = playerData.trainingState
            .takeIf { state -> state.matches(lesson, content.tutorialFocus) }
            ?.activeStep
            ?.takeIf { step -> lesson != null && step in lesson.steps.indices }
            ?: 0
        pausedForTutorial = showTutorial
        uiState = uiState.copy(
            screen = AppScreen.GAME,
            selectedMissionId = descriptor.selectionId,
            selectedAircraftId = null,
            tutorialStep = if (showTutorial) tutorialStep else null,
            result = null,
            canContinue = true,
            isRestoring = false,
            sessionPersistenceFailed = false,
            progressionSaveStatus = ProgressionSaveStatus.NOT_REQUIRED,
            nextMissionId = null,
            abandonConfirmationVisible = false,
        )
        var firstSnapshot = newEngine.submit(PlayerCommand.Start)
        if (showTutorial) firstSnapshot = newEngine.submit(PlayerCommand.Pause)
        publish(firstSnapshot)
        persistUiState()
        checkpoint()
    }

    private fun restoreScenario(record: ActiveSessionRecord) {
        restorePersistedSessionOnLoad = false
        restoreJob?.cancel()
        checkpointJob?.cancel()
        sessionValidationJob?.cancel()
        checkpointJob = null
        uiState = uiState.copy(
            screen = AppScreen.GAME,
            selectedAircraftId = null,
            result = null,
            canContinue = false,
            isRestoring = true,
            sessionPersistenceFailed = false,
        )
        persistUiState()
        val tutorialCompleted = playerData.progress.tutorialCompleted ||
            restoredTutorialState == NO_TUTORIAL_STEP
        restoreJob = viewModelScope.launch {
            val reconstructed = try {
                withContext(Dispatchers.Default) {
                    val saved = restorableFromRecord(record) ?: return@withContext null
                    ReconstructedSession(
                        saved = saved,
                        restored = rebuildScenario(saved, tutorialCompleted),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            if (reconstructed == null) {
                suppressPersistedSession = true
                engine = null
                activeDescriptor = null
                activeContent = null
                activeAttemptId = null
                lastSnapshot = null
                replayLog.clear()
                checkpointingDisabled = true
                uiState = uiState.copy(
                    screen = AppScreen.MISSIONS,
                    canContinue = false,
                    isRestoring = false,
                    sessionPersistenceFailed = true,
                )
                persistUiState()
                try {
                    preferences.clearActiveSession()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The in-memory record stays suppressed even if clearing corrupt data fails.
                }
                restoreJob = null
                return@launch
            }

            val saved = reconstructed.saved
            val restored = reconstructed.restored
            validatedSessionKey = record.toSessionKey()
            validatedSessionAvailable = true
            activeDescriptor = saved.descriptor
            activeContent = restored.content
            activeAttemptId = saved.attemptId ?: "legacy-${record.savedAtEpochMillis}"
            engine = restored.engine
            trails.clear()
            replayLog.clear()
            replayLog += saved.entries
            lastCheckpointTick = saved.savedTick
            checkpointingDisabled = false
            suppressPersistedSession = false
            pausedForTutorial = restored.restoreTutorial
            trainingRejectionMessage = null
            val lesson = TrainingAcademy.lessonFor(restored.content.tutorialFocus)
            val savedTutorialStep = saved.trainingStep
                ?.takeIf { step ->
                    lesson != null && saved.trainingLessonId == lesson.id &&
                        step in lesson.steps.indices
                }
            val persistedTutorialStep = playerData.trainingState
                .takeIf { state -> state.matches(lesson, restored.content.tutorialFocus) }
                ?.activeStep
                ?.takeIf { step -> lesson != null && step in lesson.steps.indices }
            uiState = uiState.copy(
                screen = AppScreen.GAME,
                selectedMissionId = saved.descriptor.selectionId,
                selectedAircraftId = saved.selectedAircraftId?.takeIf { selected ->
                    restored.snapshot.aircraft.any { it.id == selected && it.isUiVisible() }
                },
                tutorialStep = if (restored.restoreTutorial) {
                    savedTutorialStep
                        ?: restoredTutorialState?.takeIf { step ->
                        lesson != null && step in lesson.steps.indices
                    }
                        ?: persistedTutorialStep
                        ?: 0
                } else {
                    null
                },
                result = null,
                canContinue = true,
                isRestoring = false,
                sessionPersistenceFailed = false,
            )
            publish(restored.snapshot)
            persistUiState()
            restoreJob = null
        }
    }

    private suspend fun rebuildScenario(
        saved: RestorableSession,
        tutorialCompleted: Boolean,
    ): RestoredEngine {
        val content = saved.descriptor.content()
        val maximumTick = (content.maxDurationSeconds * TICKS_PER_SECOND).toLong() + 1L
        require(saved.savedTick in 0..maximumTick) { "Saved tick is outside the scenario" }
        require(saved.entries.size <= MAX_REPLAY_ENTRIES) { "Replay log is too large" }

        val restoredEngine = AtcSimulationEngine(content.toSimulationScenario())
        var snapshot = restoredEngine.submit(PlayerCommand.Start)
        var replayTick = snapshot.tick
        for (entry in saved.entries) {
            currentCoroutineContext().ensureActive()
            require(entry.tick in replayTick..saved.savedTick) { "Replay ticks are not ordered" }
            snapshot = advanceForRestore(restoredEngine, snapshot, entry.tick - replayTick)
            require(snapshot.status == GameStatus.RUNNING) { "Replay reached a terminal state early" }
            snapshot = restoredEngine.submit(entry.command)
            replayTick = snapshot.tick
        }
        snapshot = advanceForRestore(restoredEngine, snapshot, saved.savedTick - replayTick)
        require(snapshot.tick == saved.savedTick && snapshot.status == GameStatus.RUNNING) {
            "Saved simulation point cannot be reconstructed"
        }
        snapshot = restoredEngine.submit(PlayerCommand.SetSimulationSpeed(saved.speedMultiplier))
        if (saved.wasPaused) snapshot = restoredEngine.submit(PlayerCommand.Pause)
        val needsTutorial = saved.descriptor is SessionDescriptor.Training ||
            (saved.descriptor.selectionId in ContentRegistry.firstMissionIds &&
                !tutorialCompleted)
        if (needsTutorial && snapshot.status == GameStatus.RUNNING) {
            snapshot = restoredEngine.submit(PlayerCommand.Pause)
        }
        return RestoredEngine(content, restoredEngine, snapshot, needsTutorial)
    }

    private suspend fun advanceForRestore(
        restoredEngine: AtcSimulationEngine,
        initialSnapshot: GameSnapshot,
        totalSteps: Long,
    ): GameSnapshot {
        require(totalSteps >= 0L && totalSteps <= Int.MAX_VALUE)
        var remaining = totalSteps.toInt()
        var snapshot = initialSnapshot
        while (remaining > 0) {
            currentCoroutineContext().ensureActive()
            val stepCount = minOf(remaining, RESTORE_STEP_CHUNK)
            snapshot = restoredEngine.advanceFixedSteps(stepCount)
            remaining -= stepCount
        }
        return snapshot
    }

    private fun selectAircraft(id: String?) {
        val validId = id?.takeIf { selected -> lastSnapshot?.aircraft?.any { it.id == selected } == true }
        uiState = uiState.copy(selectedAircraftId = validId)
        persistUiState()
        checkpoint()
        if (validId != null) {
            val aircraft = lastSnapshot?.aircraft?.firstOrNull { it.id == validId }
            val step = currentTrainingStep()
            if (aircraft != null && step?.action == TrainingAction.SELECT_AIRCRAFT &&
                aircraft.matches(step.targetOperation)
            ) {
                trainingRejectionMessage = null
                advanceTutorial()
            }
        }
    }

    private fun submitRoute(points: List<NormalizedPoint>, terminalTarget: RouteTerminalTarget?) {
        val id = uiState.selectedAircraftId ?: return
        val aircraft = lastSnapshot?.aircraft?.firstOrNull { it.id == id } ?: return
        val routedPoints = when (terminalTarget) {
            is RouteTerminalTarget.AssignedRunway -> {
                if (!routeTerminalIsAllowed(
                        terminalTarget,
                        aircraft.runwayId,
                        aircraft.operation == FlightOperation.ARRIVAL,
                    )) {
                    return
                }
                val final = finalApproachPoints(terminalTarget.runwayId).map {
                    NormalizedPoint(it.x.toFloat(), it.y.toFloat())
                }
                composeApproachRoute(points, final)
            }
            is RouteTerminalTarget.NavigationFix -> {
                val fix = uiState.fixes.firstOrNull { it.name == terminalTarget.name } ?: return
                points.dropLastWhile { it == fix.position } + fix.position
            }
            null -> points
        }
        val waypoints = routedPoints.asSequence()
            .filter { it.x.isFinite() && it.y.isFinite() }
            .map(NormalizedPoint::clamped)
            .map { Vec2(it.x.toDouble(), it.y.toDouble()) }
            .take(MAX_ROUTE_POINTS)
            .toList()
        submit(PlayerCommand.SetRoute(id, Route(waypoints)))
    }

    private fun routeToFix(name: String, append: Boolean) {
        val fix = uiState.fixes.firstOrNull { it.name == name } ?: return
        submitForSelected { aircraft ->
            val point = Vec2(fix.position.x.toDouble(), fix.position.y.toDouble())
            if (append) PlayerCommand.AppendWaypoint(aircraft.id, point)
            else PlayerCommand.DirectTo(aircraft.id, point)
        }
    }

    private inline fun submitForSelected(command: (AircraftState) -> PlayerCommand) {
        val id = uiState.selectedAircraftId ?: return
        val aircraft = lastSnapshot?.aircraft?.firstOrNull { it.id == id } ?: return
        submit(command(aircraft))
    }

    /**
     * Touch-friendly equivalent to drawing an exact final and repeatedly stepping altitude and
     * speed down. The three engine commands remain explicit in the deterministic replay log.
     */
    private fun prepareApproach() {
        val aircraft = uiState.selectedAircraftId?.let { selectedId ->
            lastSnapshot?.aircraft?.firstOrNull { it.id == selectedId }
        } ?: return
        if (aircraft.operation != FlightOperation.ARRIVAL) return
        val runwayId = aircraft.runwayId ?: return
        val finalApproach = finalApproachPoints(runwayId)
        val expectedAction = currentTrainingStep()?.action
        trainingRejectionMessage = null
        suppressTrainingObservation = true
        try {
            submit(
                PlayerCommand.SetRoute(
                    aircraft.id,
                    Route(
                        finalApproach.map { Vec2(it.x, it.y) },
                    ),
                ),
            )
            submit(PlayerCommand.SetTargetAltitude(aircraft.id, 0.0))
            submit(PlayerCommand.SetTargetSpeed(aircraft.id, aircraft.type.maxLandingSpeedKnots))
        } finally {
            suppressTrainingObservation = false
        }
        if (expectedAction == TrainingAction.PREPARE_APPROACH && trainingRejectionMessage == null) {
            observeTrainingAction(TrainingAction.PREPARE_APPROACH, aircraft.id)
        }
    }

    private fun issueClearance(type: ClearanceType) {
        submitForSelected { aircraft ->
            val runwayId = aircraft.runwayId
                ?: lastSnapshot?.runways?.firstOrNull()?.id
                ?: return@submitForSelected PlayerCommand.GoAround(aircraft.id)
            when (type) {
                ClearanceType.TAKE_OFF -> PlayerCommand.ClearForTakeoff(aircraft.id, runwayId)
                ClearanceType.LAND -> PlayerCommand.ClearToLand(aircraft.id, runwayId)
                ClearanceType.GO_AROUND -> PlayerCommand.GoAround(aircraft.id, 3_000.0)
            }
        }
    }

    private fun togglePause() {
        if (pausedForTutorial) return
        when (lastSnapshot?.status) {
            GameStatus.RUNNING -> submit(PlayerCommand.Pause)
            GameStatus.PAUSED -> submit(PlayerCommand.Resume)
            else -> Unit
        }
    }

    private fun advanceTutorial() {
        val current = uiState.tutorialStep ?: return
        val lesson = activeTrainingLesson() ?: return
        if (current >= lesson.steps.lastIndex) {
            finishTutorial(completed = true)
        } else {
            uiState = uiState.copy(tutorialStep = current + 1)
            persistUiState()
            val trainingState = playerData.trainingState.copy(
                activeLessonId = lesson.id,
                activeStep = current + 1,
            )
            playerData = playerData.copy(trainingState = trainingState)
            trainingRejectionMessage = null
            lastSnapshot?.let { snapshot ->
                uiState = uiState.copy(training = trainingUi(snapshot))
            }
            viewModelScope.launch {
                preferences.saveTrainingState(trainingState)
            }
            checkpoint()
        }
    }

    private fun dismissTutorial() = finishTutorial(completed = false)

    private fun finishTutorial(completed: Boolean) {
        if (uiState.tutorialStep == null) return
        val descriptor = activeDescriptor
        val lesson = activeTrainingLesson()
        val completedLessons = if (completed && lesson != null) {
            playerData.trainingState.completedLessonIds + lesson.id
        } else {
            playerData.trainingState.completedLessonIds
        }
        val trainingState = playerData.trainingState.copy(
            activeLessonId = null,
            activeStep = 0,
            completedLessonIds = completedLessons,
        )
        playerData = playerData.copy(trainingState = trainingState)
        trainingRejectionMessage = null

        if (descriptor is SessionDescriptor.Training) {
            pausedForTutorial = false
            suppressPersistedSession = true
            checkpointingDisabled = true
            checkpointJob?.cancel()
            engine = null
            lastSnapshot = null
            activeDescriptor = null
            activeContent = null
            activeAttemptId = null
            replayLog.clear()
            uiState = uiState.copy(
                screen = AppScreen.MISSIONS,
                tutorialStep = null,
                training = null,
                selectedAircraftId = null,
                canContinue = false,
                isPaused = false,
            )
            persistUiState()
            checkpointJob = viewModelScope.launch {
                preferences.saveTrainingState(trainingState)
                runCatching { preferences.clearActiveSession() }
            }
            return
        }

        uiState = uiState.copy(tutorialStep = null, training = null)
        if (pausedForTutorial && lastSnapshot?.status == GameStatus.PAUSED) {
            pausedForTutorial = false
            engine?.submit(PlayerCommand.Resume)?.let(::publish)
        } else {
            pausedForTutorial = false
        }
        persistUiState()
        viewModelScope.launch {
            preferences.setTutorialCompleted()
            preferences.saveTrainingState(trainingState)
        }
    }

    private fun requestAbandonment() {
        if (lastSnapshot?.status?.isActive() != true) return
        pausedForAbandonConfirmation = lastSnapshot?.status == GameStatus.RUNNING
        if (pausedForAbandonConfirmation) {
            engine?.submit(PlayerCommand.Pause)?.let(::publish)
        }
        uiState = uiState.copy(abandonConfirmationVisible = true)
    }

    private fun cancelAbandonment() {
        uiState = uiState.copy(abandonConfirmationVisible = false)
        if (pausedForAbandonConfirmation && lastSnapshot?.status == GameStatus.PAUSED) {
            engine?.submit(PlayerCommand.Resume)?.let(::publish)
        }
        pausedForAbandonConfirmation = false
    }

    /** Ends the attempt without recording a result or unlocking any mission. */
    private fun confirmAbandonment() {
        if (!uiState.abandonConfirmationVisible) return
        if (activeDescriptor is SessionDescriptor.Training) {
            finishTutorial(completed = false)
            return
        }
        pausedForAbandonConfirmation = false
        val snapshot = lastSnapshot ?: return
        val paused = if (snapshot.status == GameStatus.RUNNING) {
            engine?.submit(PlayerCommand.Pause) ?: snapshot
        } else {
            snapshot
        }
        publish(paused)
        uiState = uiState.copy(
            screen = AppScreen.RESULTS,
            result = paused.toResult(
                title = resources.getString(
                    R.string.result_score_not_recorded,
                    activeDescriptor?.let(::localizedScenarioTitle)
                        ?: resources.getString(R.string.shift_fallback),
                ),
                stars = 0,
                personalBest = false,
                displayedScore = (activeDescriptor as? SessionDescriptor.Endless)
                    ?.cumulativeScore
                    ?.plus(paused.score.total)
                    ?: paused.score.total,
                resources = resources,
                fixNamesByPosition = fixNamesByPosition,
                isPractice = (activeDescriptor as? SessionDescriptor.Custom)?.configuration?.isRanked == false,
                configurationIdentity = when (val descriptor = activeDescriptor) {
                    is SessionDescriptor.Custom -> descriptor.configuration
                    is SessionDescriptor.Daily -> descriptor.configuration
                    else -> null
                }?.let(ShiftConfigurationCodec::encode),
                isDaily = activeDescriptor is SessionDescriptor.Daily,
            ),
            canContinue = false,
            abandonConfirmationVisible = false,
            progressionSaveStatus = ProgressionSaveStatus.NOT_REQUIRED,
            nextMissionId = null,
        )
        persistUiState()
        suppressPersistedSession = true
        checkpointingDisabled = true
        checkpointJob?.cancel()
        engine = null
        lastSnapshot = null
        replayLog.clear()
        checkpointJob = viewModelScope.launch {
            runCatching { preferences.clearActiveSession() }
        }
    }

    private fun submit(command: PlayerCommand) {
        val currentEngine = engine ?: return
        val before = currentEngine.snapshot
        if (command.isReplayable() && before.status.isActive()) {
            if (replayLog.size < MAX_REPLAY_ENTRIES) {
                replayLog += ReplayEntry(before.tick, command)
            } else {
                disableSessionPersistence()
            }
        }
        val result = currentEngine.submit(command)
        publish(result)
        val rejection = result.events.filterIsInstance<GameEvent.CommandRejected>().lastOrNull()
        if (rejection != null && uiState.tutorialStep != null) {
            val callsigns = result.aircraft.associate { it.id to it.callsign }
            trainingRejectionMessage = eventCaption(
                rejection,
                callsigns,
                resources,
                fixNamesByPosition,
            )
            uiState = uiState.copy(training = trainingUi(result))
        } else if (!suppressTrainingObservation) {
            trainingRejectionMessage = null
            observeTrainingCommand(command, result)
        }
        if (command.isReplayable()) checkpoint()
    }

    private fun observeTrainingCommand(command: PlayerCommand, snapshot: GameSnapshot) {
        val action = when (command) {
            is PlayerCommand.SetRoute,
            is PlayerCommand.DirectTo,
            is PlayerCommand.AppendWaypoint -> TrainingAction.SET_ROUTE
            is PlayerCommand.SetTargetAltitude -> TrainingAction.SET_ALTITUDE
            is PlayerCommand.SetTargetSpeed -> TrainingAction.SET_SPEED
            is PlayerCommand.ClearToLand -> TrainingAction.CLEAR_TO_LAND
            is PlayerCommand.GoAround -> TrainingAction.GO_AROUND
            is PlayerCommand.LineUpAndWait -> TrainingAction.LINE_UP_AND_WAIT
            is PlayerCommand.ClearForTakeoff -> TrainingAction.CLEAR_FOR_TAKEOFF
            is PlayerCommand.AssignRunway -> TrainingAction.ASSIGN_RUNWAY
            is PlayerCommand.AssignApproach -> TrainingAction.ASSIGN_APPROACH
            is PlayerCommand.AssignHold -> TrainingAction.ASSIGN_HOLD
            is PlayerCommand.CancelHold -> TrainingAction.CANCEL_HOLD
            is PlayerCommand.AcknowledgeInboundHandoff -> TrainingAction.ACKNOWLEDGE_HANDOFF
            is PlayerCommand.CancelApproach,
            is PlayerCommand.CancelLandingClearance,
            is PlayerCommand.CancelTakeoffClearance,
            is PlayerCommand.IssueExitClearance,
            is PlayerCommand.InitiateOutboundHandoff,
            is PlayerCommand.AcknowledgeDynamicEvent,
            is PlayerCommand.CrossRunway,
            is PlayerCommand.ClearRoute,
            is PlayerCommand.UndoWaypoint,
            PlayerCommand.Start,
            PlayerCommand.Pause,
            PlayerCommand.Resume,
            is PlayerCommand.SetSimulationSpeed -> return
        }
        if (action == TrainingAction.SET_ROUTE && snapshot.events.none { it is GameEvent.RouteUpdated }) {
            return
        }
        observeTrainingAction(action, command.aircraftId())
    }

    private fun observeTrainingAction(action: TrainingAction, aircraftId: String) {
        val step = currentTrainingStep() ?: return
        val aircraft = lastSnapshot?.aircraft?.firstOrNull { it.id == aircraftId } ?: return
        if (step.action == action && aircraft.matches(step.targetOperation)) advanceTutorial()
    }

    private fun activeTrainingLesson() = activeContent?.tutorialFocus?.let(TrainingAcademy::lessonFor)

    private fun currentTrainingStep() = uiState.tutorialStep?.let { stepIndex ->
        activeTrainingLesson()?.steps?.getOrNull(stepIndex)
    }

    private fun trainingUi(snapshot: GameSnapshot): TrainingUiModel? {
        val stepIndex = uiState.tutorialStep ?: return null
        val lesson = activeTrainingLesson() ?: return null
        val step = lesson.steps.getOrNull(stepIndex) ?: return null
        val target = uiState.selectedAircraftId?.let { selectedId ->
            snapshot.aircraft.firstOrNull { it.id == selectedId && it.matches(step.targetOperation) }
        } ?: snapshot.aircraft.firstOrNull { state ->
            state.isUiVisible() && state.matches(step.targetOperation)
        }
        val targetName = target?.callsign ?: resources.getString(R.string.training_aircraft_fallback)
        val prompt = resources.getString(when (step.action) {
            TrainingAction.SELECT_AIRCRAFT -> R.string.training_prompt_select
            TrainingAction.SET_ROUTE -> R.string.training_prompt_route
            TrainingAction.SET_ALTITUDE -> R.string.training_prompt_altitude
            TrainingAction.SET_SPEED -> R.string.training_prompt_speed
            TrainingAction.PREPARE_APPROACH -> R.string.training_prompt_prepare_approach
            TrainingAction.CLEAR_TO_LAND -> R.string.training_prompt_clear_land
            TrainingAction.GO_AROUND -> R.string.training_prompt_go_around
            TrainingAction.LINE_UP_AND_WAIT -> R.string.training_prompt_line_up
            TrainingAction.CLEAR_FOR_TAKEOFF -> R.string.training_prompt_takeoff
            TrainingAction.ASSIGN_RUNWAY -> R.string.training_prompt_assign_runway
            TrainingAction.ASSIGN_APPROACH -> R.string.training_prompt_assign_approach
            TrainingAction.ASSIGN_HOLD -> R.string.training_prompt_assign_hold
            TrainingAction.CANCEL_HOLD -> R.string.training_prompt_cancel_hold
            TrainingAction.ACKNOWLEDGE_HANDOFF -> R.string.training_prompt_ack_handoff
        }, targetName)
        return TrainingUiModel(
            lessonId = lesson.id,
            title = lesson.focus.subtitle(resources),
            stepIndex = stepIndex,
            stepCount = lesson.steps.size,
            prompt = prompt,
            actionGate = resources.getString(R.string.training_action_gate),
            canAdvance = false,
            rejectionMessage = trainingRejectionMessage,
            isPractice = activeDescriptor is SessionDescriptor.Training,
        )
    }

    private fun publish(snapshot: GameSnapshot) {
        val wasTerminal = lastSnapshot?.status?.isTerminal() == true
        lastSnapshot = snapshot
        recordTrails(snapshot)

        val aircraft = snapshot.aircraft.filter(AircraftState::isUiVisible).map { it.toUiModel(snapshot) }
        val runways = snapshot.toRunwayUiModels()
        val callsigns = snapshot.aircraft.associate { it.id to it.callsign }
        val shiftConfiguration = when (val descriptor = activeDescriptor) {
            is SessionDescriptor.Custom -> descriptor.configuration
            is SessionDescriptor.Daily -> descriptor.configuration
            else -> null
        }
        val predictionOutage = snapshot.dynamicEventStates.any {
            it.definition.type == DynamicEventType.EQUIPMENT_OUTAGE &&
                it.lifecycle in setOf(DynamicEventLifecycle.ACTIVE, DynamicEventLifecycle.RECOVERY)
        }
        val conflicts = conflictsToUiModels(snapshot.conflicts, callsigns).filter { conflict ->
            (!predictionOutage && shiftConfiguration?.assists?.conflictPrediction != false) ||
                conflict.isLossOfSeparation
        }
        val previousPair = uiState.activeConflict?.canonicalPairKey()
        val activeConflictIndex = retainedConflictIndex(previousPair, conflicts)
        val conflictAnnouncement = conflictAnnouncement(snapshot.events, callsigns)
        if (conflicts.isEmpty() && conflictAnnouncement == null) {
            lastConflictAnnouncementKey = null
        }
        val selected = uiState.selectedAircraftId?.takeIf { selectedId ->
            aircraft.any { it.id == selectedId }
        }
        val objectives = snapshot.toObjectiveProgress()
        val movementTarget = maxOf(
            snapshot.objectives.safeMovementsToComplete,
            snapshot.objectives.arrivalsToLand + snapshot.objectives.departuresToExit,
        )
        val movementsRemaining = (movementTarget - snapshot.score.safeArrivals - snapshot.score.safeDepartures)
            .coerceAtLeast(0)
        val eventFeed = snapshot.toEventFeed(callsigns)
        val flightStrips = aircraft.map { item ->
            val state = snapshot.aircraft.first { it.id == item.id }
            FlightStripUiModel(
                aircraftId = item.id,
                callsign = item.callsign,
                phase = item.phase,
                runwayId = item.assignedRunway,
                fuelPercent = item.fuelPercent,
                conflictLevel = item.conflictLevel,
                clearance = item.clearance,
                wakeRequiredSeconds = snapshot.wakeSpacing
                    .firstOrNull { it.followerAircraftId == item.id }?.requiredSeconds?.roundToInt(),
                wakeActualSeconds = snapshot.wakeSpacing
                    .firstOrNull { it.followerAircraftId == item.id }?.actualSeconds?.roundToInt(),
                runwayStatePriority = when (state.status) {
                    AircraftStatus.APPROACH, AircraftStatus.LANDING,
                    AircraftStatus.LINED_UP, AircraftStatus.TAKEOFF_ROLL -> 2
                    AircraftStatus.HOLDING_SHORT, AircraftStatus.GO_AROUND -> 1
                    else -> 0
                },
            )
        }.sortedWith(
            compareByDescending<FlightStripUiModel> { it.conflictLevel.ordinal }
                .thenBy { it.fuelPercent }
                .thenByDescending { it.runwayStatePriority }
                .thenBy { it.callsign },
        )
        uiState = uiState.copy(
            aircraft = aircraft,
            selectedAircraftId = selected,
            runway = runways.firstOrNull(RunwayUiModel::isActive) ?: RunwayUiModel(),
            runways = runways,
            fixes = fixesFor(snapshot),
            conflicts = conflicts,
            activeConflictIndex = activeConflictIndex,
            conflictAnnouncement = when {
                conflictAnnouncement != null -> conflictAnnouncement
                conflicts.isEmpty() -> null
                else -> uiState.conflictAnnouncement
            },
            score = (activeDescriptor as? SessionDescriptor.Endless)
                ?.cumulativeScore
                ?.plus(snapshot.score.total)
                ?: snapshot.score.total,
            elapsedSeconds = snapshot.elapsedSeconds.toInt(),
            strikes = snapshot.strikes,
            // A tutorial-owned pause must not replace the tutorial with the normal pause overlay.
            isPaused = snapshot.status == GameStatus.PAUSED && !pausedForTutorial,
            timeScale = snapshot.speedMultiplier.roundToInt().coerceIn(1, 2),
            canContinue = snapshot.status.isActive(),
            objectiveProgress = objectives,
            movementsRemaining = movementsRemaining,
            missionTimeRemainingSeconds = ceil(snapshot.maxDurationSeconds - snapshot.elapsedSeconds)
                .toInt().coerceAtLeast(0),
            missionClockState = missionClockState(
                status = snapshot.status,
                failureReason = snapshot.failureReason,
                elapsedSeconds = snapshot.elapsedSeconds,
                maxDurationSeconds = snapshot.maxDurationSeconds,
            ),
            upcomingTraffic = snapshot.upcomingAircraft.map { upcoming ->
                UpcomingTrafficUiModel(
                    aircraftId = upcoming.aircraftId,
                    callsign = upcoming.callsign,
                    intent = when (upcoming.operation) {
                        FlightOperation.ARRIVAL -> UpcomingTrafficIntent.ARRIVAL
                        FlightOperation.DEPARTURE -> UpcomingTrafficIntent.DEPARTURE
                    },
                    runwayId = upcoming.runwayId,
                    secondsToEntry = secondsUntilEntry(
                        spawnAtSeconds = upcoming.spawnAtSeconds,
                        elapsedSeconds = snapshot.elapsedSeconds,
                    ),
                )
            },
            eventFeed = eventFeed,
            flightStrips = flightStrips,
            starForecast = starForecastFor(
                score = snapshot.score.total,
                thresholds = snapshot.objectives.starScoreThresholds,
            ),
            weatherImpact = snapshot.toWeatherImpact(),
            training = trainingUi(snapshot),
            runwayProceduresEnabled = snapshot.mechanicVersions.runwayProcedures > 0,
            proceduralControlEnabled = snapshot.mechanicVersions.proceduralControl > 0,
            isPracticeSession = activeDescriptor is SessionDescriptor.Custom &&
                shiftConfiguration?.isRanked == false,
            isDailySession = activeDescriptor is SessionDescriptor.Daily,
            configurationIdentity = shiftConfiguration?.let(ShiftConfigurationCodec::encode),
            activeAssistLabels = shiftConfiguration?.assists?.let { assists ->
                buildList {
                    if (assists.routeSnapping) add(resources.getString(R.string.assist_route_snapping))
                    if (assists.approachSetup) add(resources.getString(R.string.assist_approach_setup))
                    if (assists.conflictPrediction) add(resources.getString(R.string.assist_conflict_prediction))
                }
            }.orEmpty(),
            routeSnappingAssistEnabled = shiftConfiguration?.assists?.routeSnapping != false,
            approachSetupAssistEnabled = shiftConfiguration?.assists?.approachSetup != false,
            conflictPredictionAssistEnabled = shiftConfiguration?.assists?.conflictPrediction != false,
            dynamicEvents = snapshot.dynamicEventStates
                .filter {
                    it.lifecycle !in setOf(
                        DynamicEventLifecycle.SCHEDULED,
                        DynamicEventLifecycle.RESOLVED,
                    )
                }
                .map { it.toUiModel(callsigns) },
        )
        emitFeedback(snapshot.events)

        if (replayController == null && !wasTerminal && snapshot.status.isTerminal()) handleTerminal(snapshot)
        if (snapshot.status.isActive() && snapshot.tick - lastCheckpointTick >= CHECKPOINT_TICKS) {
            checkpoint()
        }
    }

    private fun com.stuart.atccontroller.simulation.DynamicEventState.toUiModel(
        callsigns: Map<String, String>,
    ): DynamicEventUiModel {
        val typeLabel = resources.getString(when (definition.type) {
            DynamicEventType.LOW_FUEL_PRIORITY -> R.string.dynamic_low_fuel
            DynamicEventType.REJECTED_TAKEOFF -> R.string.dynamic_rejected_takeoff
            DynamicEventType.RUNWAY_CLOSURE -> R.string.dynamic_runway_closure
            DynamicEventType.EQUIPMENT_OUTAGE -> R.string.dynamic_equipment_outage
            DynamicEventType.PRIORITY_FLIGHT -> R.string.dynamic_priority_flight
        })
        val goal = resources.getString(when (definition.recoveryGoal) {
            DynamicRecoveryGoal.LAND_PRIORITY_AIRCRAFT -> R.string.dynamic_goal_land_priority
            DynamicRecoveryGoal.RESEQUENCE_DEPARTURE -> R.string.dynamic_goal_resequence
            DynamicRecoveryGoal.KEEP_RUNWAY_CLEAR -> R.string.dynamic_goal_keep_clear
            DynamicRecoveryGoal.CONTROL_WITHOUT_PREDICTION -> R.string.dynamic_goal_without_prediction
            DynamicRecoveryGoal.EXPEDITE_PRIORITY_FLIGHT -> R.string.dynamic_goal_expedite
        })
        val affected = definition.aircraftId?.let { callsigns[it] ?: it }
            ?: definition.runwayId?.let { resources.getString(R.string.runway_value, it) }
            ?: resources.getString(R.string.radar_system)
        return DynamicEventUiModel(
            id = definition.id,
            title = typeLabel,
            lifecycle = resources.getString(
                R.string.dynamic_lifecycle,
                lifecycle.name.lowercase().replace('_', ' '),
            ),
            recoveryGoal = goal,
            affectedLabel = affected,
            canAcknowledge = lifecycle == DynamicEventLifecycle.ACTIVE,
            failed = lifecycle == DynamicEventLifecycle.FAILED,
        )
    }

    private fun GameSnapshot.toObjectiveProgress(): List<ObjectiveProgressUiModel> = buildList {
        fun addProgress(
            id: String,
            kind: ObjectiveProgressKind,
            current: Int,
            target: Int,
            inverse: Boolean = false,
        ) {
            if (target <= 0 && id != "strikes") return
            add(
                ObjectiveProgressUiModel(
                    id = id,
                    kind = kind,
                    current = current,
                    target = target,
                    passed = if (inverse) current <= target else current >= target,
                ),
            )
        }
        addProgress(
            "movements",
            ObjectiveProgressKind.SAFE_MOVEMENTS,
            score.safeArrivals + score.safeDepartures,
            objectives.safeMovementsToComplete,
        )
        addProgress(
            "arrivals",
            ObjectiveProgressKind.ARRIVALS,
            score.safeArrivals,
            objectives.arrivalsToLand,
        )
        addProgress(
            "departures",
            ObjectiveProgressKind.DEPARTURES,
            score.safeDepartures,
            objectives.departuresToExit,
        )
        addProgress("score", ObjectiveProgressKind.SCORE, score.total, objectives.minimumScore)
        addProgress(
            "strikes",
            ObjectiveProgressKind.STRIKES,
            strikes,
            objectives.maximumStrikes,
            inverse = true,
        )
    }

    private fun GameSnapshot.toWeatherImpact(): WeatherImpactUiModel {
        val crosswind = runways.associate { runway ->
            val angle = Math.toRadians(weather.windDirectionDegrees - runway.headingDegrees)
            runway.id to abs(weather.windSpeedKnots * sin(angle)).roundToInt()
        }
        return WeatherImpactUiModel(
            wind = String.format(
                Locale.UK,
                "%03.0f° / %02.0f kt",
                weather.windDirectionDegrees,
                weather.windSpeedKnots,
            ),
            visibility = String.format(Locale.UK, "%.0f km", weather.visibilityKm),
            crosswindByRunway = crosswind,
            windDriftActive = mechanicVersions.windDrift > 0,
            reducedVisibilityActive = mechanicVersions.reducedVisibility > 0 && weather.visibilityKm < 8.0,
            changeNotice = weatherChangeStates.firstOrNull {
                it.lifecycle == com.stuart.atccontroller.simulation.WeatherChangeLifecycle.WARNING
            }?.definition?.let { change ->
                resources.getString(
                    R.string.weather_change_notice,
                    change.activeRunwayIds.sorted().joinToString(" / "),
                    ceil(change.effectiveSeconds - elapsedSeconds).toInt().coerceAtLeast(0),
                )
            },
        )
    }

    private fun GameSnapshot.toEventFeed(callsigns: Map<String, String>): List<EventFeedEntryUiModel> =
        eventHistory.takeLast(EVENT_FEED_CAPACITY).mapIndexed { index, event ->
            val retainedOffset = (eventHistory.size - EVENT_FEED_CAPACITY).coerceAtLeast(0)
            val ids = event.aircraftIdsForPresentation()
            EventFeedEntryUiModel(
                sequence = eventHistoryStartSequence + retainedOffset + index,
                elapsedSeconds = event.elapsedSeconds.toInt(),
                caption = eventCaption(event, callsigns, resources, fixNamesByPosition),
                aircraftIds = ids,
                severity = event.toFeedSeverity(),
                rejectionCode = (event as? GameEvent.CommandRejected)?.reasonCode?.name,
            )
        }

    private fun cycleConflict(offset: Int) {
        val count = uiState.conflicts.size
        if (count == 0 || offset == 0) return
        uiState = uiState.copy(activeConflictIndex = Math.floorMod(uiState.activeConflictIndex + offset, count))
    }

    private fun conflictAnnouncement(
        events: List<GameEvent>,
        callsigns: Map<String, String>,
    ): ConflictAnnouncementUiModel? {
        val event = events.lastOrNull {
            it is GameEvent.ConflictWarning || it is GameEvent.SeparationLost || it is GameEvent.Collision
        } ?: return null
        val conflict = when (event) {
            is GameEvent.ConflictWarning -> event.conflict
            is GameEvent.SeparationLost -> event.conflict
            is GameEvent.Collision -> event.conflict
            else -> return null
        }
        val isLoss = event !is GameEvent.ConflictWarning
        val key = "${conflict.canonicalPairKey()}:$isLoss"
        if (key == lastConflictAnnouncementKey) return null
        lastConflictAnnouncementKey = key
        val firstId = minOf(conflict.firstAircraftId, conflict.secondAircraftId)
        val secondId = maxOf(conflict.firstAircraftId, conflict.secondAircraftId)
        return ConflictAnnouncementUiModel(
            sequence = ++conflictAnnouncementSequence,
            firstAircraftCallsign = callsigns[firstId] ?: firstId,
            secondAircraftCallsign = callsigns[secondId] ?: secondId,
            secondsToConflict = ceil(conflict.timeToClosestApproachSeconds).toInt().coerceAtLeast(0),
            isLossOfSeparation = isLoss,
        )
    }

    private fun handleTerminal(snapshot: GameSnapshot) {
        val descriptor = activeDescriptor ?: return
        if (descriptor is SessionDescriptor.Training) {
            finishTutorial(completed = snapshot.status == GameStatus.COMPLETED)
            return
        }
        saveCompletedReplay(descriptor, snapshot)
        val completed = snapshot.status == GameStatus.COMPLETED
        val isEndless = descriptor is SessionDescriptor.Endless
        val recordsProgression = descriptor is SessionDescriptor.Authored || isEndless

        if (completed && descriptor is SessionDescriptor.Endless) {
            val cumulativeScore = descriptor.cumulativeScore + snapshot.score.total
            val milestone = EndlessMilestoneRecord(
                contentPackId = descriptor.contentPackId,
                seed = descriptor.seed,
                completedStage = descriptor.stage,
                stageScore = snapshot.score.total,
                cumulativeScore = cumulativeScore,
            )
            playerData = playerData.copy(endlessMilestone = milestone)
            suppressPersistedSession = true
            checkpointingDisabled = true
            checkpointJob?.cancel()
            engine = null
            lastSnapshot = null
            uiState = uiState.copy(
                screen = AppScreen.MILESTONE,
                endlessMilestone = milestone.toUiModel(),
                canContinue = true,
                progressionSaveStatus = ProgressionSaveStatus.NOT_REQUIRED,
            )
            persistUiState()
            checkpointJob = viewModelScope.launch {
                preferences.saveEndlessMilestone(milestone)
            }
            return
        }

        val stars = if (completed) snapshot.stars.coerceIn(0, 3) else 0
        val finalScore = if (descriptor is SessionDescriptor.Endless) {
            descriptor.cumulativeScore + snapshot.score.total
        } else {
            snapshot.score.total
        }
        val previousBest = if (isEndless) {
            playerData.progress.endlessHighScoreFor(
                descriptor.contentPackId,
            )
        } else if (descriptor is SessionDescriptor.Custom || descriptor is SessionDescriptor.Daily) {
            Int.MAX_VALUE
        } else {
            playerData.progress.missionBestScores[descriptor.selectionId] ?: -1
        }
        val personalBest = finalScore > previousBest
        val scenarioTitle = localizedScenarioTitle(descriptor)
        val title = if (completed) scenarioTitle else resources.getString(
            R.string.result_failure_title,
            scenarioTitle,
            snapshot.failureReason.toDisplayText(resources),
        )

        suppressPersistedSession = true
        checkpointingDisabled = true
        val pendingCheckpoint = checkpointJob
        pendingCheckpoint?.cancel()
        uiState = uiState.copy(
            screen = AppScreen.RESULTS,
            tutorialStep = if (pausedForTutorial) 0 else null,
            result = snapshot.toResult(
                title,
                stars,
                personalBest,
                finalScore,
                resources,
                fixNamesByPosition,
                isPractice = (descriptor as? SessionDescriptor.Custom)?.configuration?.isRanked == false,
                configurationIdentity = when (descriptor) {
                    is SessionDescriptor.Custom -> descriptor.configuration
                    is SessionDescriptor.Daily -> descriptor.configuration
                    else -> null
                }?.let(ShiftConfigurationCodec::encode),
                isDaily = descriptor is SessionDescriptor.Daily,
            ),
            canContinue = false,
            progressionSaveStatus = if (completed && recordsProgression) {
                ProgressionSaveStatus.SAVING
            } else {
                ProgressionSaveStatus.NOT_REQUIRED
            },
            nextMissionId = if (completed && descriptor is SessionDescriptor.Authored) {
                ContentRegistry.nextMissionId(descriptor.selectionId)
            } else null,
            abandonConfirmationVisible = false,
        )
        persistUiState()
        pendingProgression = when {
            completed && !isEndless && descriptor.selectionId in ContentRegistry.missionIds -> {
                val safeMovements = snapshot.score.safeArrivals + snapshot.score.safeDepartures
                val maximumEfficiency = safeMovements *
                    (activeContent?.scoring?.maximumRouteEfficiencyBonusPoints ?: 0)
                PendingProgression(
                    missionId = descriptor.selectionId,
                    stars = stars,
                    score = finalScore,
                    endless = false,
                    validatedResult = ValidatedMissionResult(
                        resultId = activeAttemptId ?: UUID.randomUUID().toString(),
                        missionId = descriptor.selectionId,
                        focus = activeContent?.tutorialFocus ?: TutorialFocus.NONE,
                        stars = stars,
                        score = finalScore,
                        completionSeconds = snapshot.elapsedSeconds.roundToInt(),
                        safeMovements = safeMovements,
                        strikes = snapshot.strikes,
                        departures = snapshot.score.safeDepartures,
                        missedExits = snapshot.eventHistory.filterIsInstance<GameEvent.AircraftExited>()
                            .count { !it.correctExit },
                        routeEfficiencyPercent = if (maximumEfficiency <= 0) {
                            0
                        } else {
                            (snapshot.score.efficiencyPoints * 100 / maximumEfficiency)
                                .coerceIn(0, 100)
                        },
                    ),
                )
            }
            isEndless -> PendingProgression(descriptor.selectionId, stars, finalScore, true, null)
            else -> null
        }
        if (pendingProgression != null) {
            persistPendingProgression()
        } else if (completed && descriptor is SessionDescriptor.Daily) {
            checkpointJob = viewModelScope.launch {
                runCatching {
                    preferences.recordDailyResult(
                        localDate = descriptor.localDate,
                        configurationIdentity = DailyShift.identityFor(descriptor.localDate),
                        resultId = activeAttemptId ?: UUID.randomUUID().toString(),
                        score = finalScore,
                    )
                    preferences.clearActiveSession()
                }
            }
        } else if (completed && descriptor is SessionDescriptor.Custom) {
            checkpointJob = viewModelScope.launch {
                runCatching {
                    preferences.savePracticeResult(
                        PracticeResultRecord(
                            resultId = activeAttemptId ?: UUID.randomUUID().toString(),
                            configurationIdentity = ShiftConfigurationCodec.encode(descriptor.configuration),
                            score = finalScore,
                            stars = stars,
                            completedAtEpochMillis = System.currentTimeMillis(),
                            rankedPreset = descriptor.configuration.isRanked,
                        ),
                    )
                    preferences.clearActiveSession()
                }
            }
        } else {
            checkpointJob = viewModelScope.launch { runCatching { preferences.clearActiveSession() } }
        }
    }

    private fun saveCompletedReplay(descriptor: SessionDescriptor, snapshot: GameSnapshot) {
        val payload = RestorableSession(
            descriptor = descriptor,
            savedTick = snapshot.tick,
            speedMultiplier = snapshot.speedMultiplier,
            wasPaused = true,
            selectedAircraftId = uiState.selectedAircraftId,
            trainingLessonId = null,
            trainingStep = null,
            attemptId = activeAttemptId,
            entries = replayLog.toList(),
        ).toPayload()
        if (payload.length > MAX_SESSION_PAYLOAD_CHARS) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            preferences.saveCompletedReplay(
                CompletedReplayRecord(
                    schemaVersion = SESSION_SCHEMA,
                    id = "${snapshot.scenarioId}-$now-${snapshot.tick}",
                    scenarioId = snapshot.scenarioId,
                    savedAtEpochMillis = now,
                    terminalTick = snapshot.tick,
                    finalScore = snapshot.score.total,
                    terminalHash = snapshot.terminalHash(),
                    payload = payload,
                ),
            )
        }
    }

    private fun startReplay(replayId: String) {
        val record = playerData.completedReplays.firstOrNull { it.id == replayId }
            ?: return showReplayError()
        val saved = restorableFromRecord(
            ActiveSessionRecord(
                schemaVersion = record.schemaVersion,
                scenarioId = record.scenarioId,
                savedAtEpochMillis = record.savedAtEpochMillis,
                payload = record.payload,
            ),
        ) ?: return showReplayError()
        if (record.terminalTick != saved.savedTick || record.terminalHash.isBlank()) {
            return showReplayError()
        }
        val content = saved.descriptor.content()
        val replayEngine = AtcSimulationEngine(content.toSimulationScenario())
        val first = replayEngine.submit(PlayerCommand.Start)
        replayController = ReplayController(
            saved = saved,
            terminalTick = record.terminalTick,
            expectedScore = record.finalScore,
            expectedTerminalHash = record.terminalHash,
            nextEntryIndex = 0,
        )
        activeDescriptor = saved.descriptor
        activeContent = content
        engine = replayEngine
        lastSnapshot = null
        trails.clear()
        uiState = uiState.copy(
            screen = AppScreen.GAME,
            selectedMissionId = saved.descriptor.selectionId,
            selectedAircraftId = null,
            tutorialStep = null,
            replay = ReplayUiModel(
                isPlaying = false,
                tick = 0,
                terminalTick = record.terminalTick,
                speed = 1,
            ),
            result = null,
            canContinue = false,
            replayError = null,
        )
        publish(first)
    }

    private fun showReplayError() {
        uiState = uiState.copy(replayError = resources.getString(R.string.replay_incompatible))
    }

    private fun deleteReplay(replayId: String) {
        if (playerData.completedReplays.none { it.id == replayId }) return
        playerData = playerData.copy(
            completedReplays = playerData.completedReplays.filterNot { it.id == replayId },
        )
        uiState = uiState.copy(
            completedReplays = uiState.completedReplays.filterNot { it.id == replayId },
            replayError = null,
        )
        viewModelScope.launch { preferences.deleteCompletedReplay(replayId) }
    }

    private fun advanceReplay(stepCount: Int) {
        val controller = replayController ?: return
        var current = engine?.snapshot ?: return
        if (current.tick >= controller.terminalTick || current.status.isTerminal()) {
            finishReplayVerification(current, controller)
            return
        }
        repeat(stepCount.coerceIn(1, 4)) {
            if (current.tick >= controller.terminalTick || current.status.isTerminal()) return@repeat
            while (controller.nextEntryIndex < controller.saved.entries.size &&
                controller.saved.entries[controller.nextEntryIndex].tick == current.tick
            ) {
                current = checkNotNull(engine).submit(
                    controller.saved.entries[controller.nextEntryIndex].command,
                )
                controller.nextEntryIndex += 1
            }
            current = checkNotNull(engine).advanceFixedSteps(1)
        }
        publish(current)
        if (current.tick >= controller.terminalTick || current.status.isTerminal()) {
            finishReplayVerification(current, controller)
        } else {
            uiState = uiState.copy(
                replay = uiState.replay?.copy(tick = current.tick),
            )
        }
    }

    private fun seekReplay(requestedTick: Long) {
        val controller = replayController ?: return
        val target = requestedTick.coerceIn(0L, controller.terminalTick)
        val content = controller.saved.descriptor.content()
        val replayEngine = AtcSimulationEngine(content.toSimulationScenario())
        var snapshot = replayEngine.submit(PlayerCommand.Start)
        var entryIndex = 0
        while (snapshot.tick < target && !snapshot.status.isTerminal()) {
            while (entryIndex < controller.saved.entries.size &&
                controller.saved.entries[entryIndex].tick == snapshot.tick
            ) {
                snapshot = replayEngine.submit(controller.saved.entries[entryIndex].command)
                entryIndex += 1
            }
            snapshot = replayEngine.advanceFixedSteps(1)
        }
        while (entryIndex < controller.saved.entries.size &&
            controller.saved.entries[entryIndex].tick == snapshot.tick &&
            snapshot.status.isActive()
        ) {
            snapshot = replayEngine.submit(controller.saved.entries[entryIndex].command)
            entryIndex += 1
        }
        engine = replayEngine
        controller.nextEntryIndex = entryIndex
        lastSnapshot = null
        trails.clear()
        publish(snapshot)
        if (snapshot.tick >= controller.terminalTick || snapshot.status.isTerminal()) {
            finishReplayVerification(snapshot, controller)
        } else {
            uiState = uiState.copy(
                replay = uiState.replay?.copy(
                    isPlaying = false,
                    tick = snapshot.tick,
                    verification = ReplayVerification.PENDING,
                ),
            )
        }
    }

    private fun finishReplayVerification(snapshot: GameSnapshot, controller: ReplayController) {
        val verified = snapshot.tick == controller.terminalTick &&
            snapshot.status.isTerminal() &&
            snapshot.score.total == controller.expectedScore &&
            snapshot.terminalHash() == controller.expectedTerminalHash
        uiState = uiState.copy(
            replay = uiState.replay?.copy(
                isPlaying = false,
                tick = snapshot.tick,
                verification = if (verified) {
                    ReplayVerification.VERIFIED
                } else {
                    ReplayVerification.FAILED
                },
            ),
        )
    }

    private fun persistPendingProgression() {
        val pending = pendingProgression ?: restoredPendingProgression() ?: return
        pendingProgression = pending
        uiState = uiState.copy(progressionSaveStatus = ProgressionSaveStatus.SAVING)
        persistUiState()
        checkpointJob = viewModelScope.launch {
            try {
                if (pending.endless) {
                    val packId = (activeDescriptor as? SessionDescriptor.Endless)
                        ?.contentPackId ?: ContentRegistry.DEFAULT_PACK_ID
                    preferences.recordEndlessHighScore(packId, pending.score)
                } else {
                    pending.validatedResult?.let {
                        preferences.recordValidatedMissionResult(it)
                    } ?: preferences.recordMissionResult(
                        pending.missionId,
                        pending.stars,
                        pending.score,
                    )
                }
                preferences.clearActiveSession()
                pendingProgression = null
                uiState = uiState.copy(progressionSaveStatus = ProgressionSaveStatus.SAVED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                uiState = uiState.copy(progressionSaveStatus = ProgressionSaveStatus.FAILED)
            }
            persistUiState()
        }
    }

    private fun continueEndlessRun() {
        val milestone = playerData.endlessMilestone ?: return
        if (milestone.choice != EndlessMilestoneChoice.AWAITING) return
        val pending = milestone.copy(choice = EndlessMilestoneChoice.CONTINUE_PENDING)
        playerData = playerData.copy(endlessMilestone = pending)
        uiState = uiState.copy(endlessMilestone = pending.toUiModel())
        viewModelScope.launch {
            preferences.saveEndlessMilestone(pending)
            startScenario(
                SessionDescriptor.Endless(
                    seed = pending.seed,
                    stage = pending.completedStage + 1,
                    cumulativeScore = pending.cumulativeScore,
                    contentPackId = pending.contentPackId,
                ),
            )
        }
    }

    private fun cashOutEndlessRun() {
        val milestone = playerData.endlessMilestone ?: return
        if (milestone.choice == EndlessMilestoneChoice.CONTINUE_PENDING) return
        val pending = milestone.copy(choice = EndlessMilestoneChoice.CASH_OUT_PENDING)
        val personalBest = pending.cumulativeScore >
            playerData.progress.endlessHighScoreFor(pending.contentPackId)
        playerData = playerData.copy(endlessMilestone = pending)
        uiState = uiState.copy(endlessMilestone = pending.toUiModel())
        viewModelScope.launch {
            try {
                preferences.saveEndlessMilestone(pending)
                preferences.recordEndlessHighScore(pending.contentPackId, pending.cumulativeScore)
                preferences.clearEndlessMilestone()
                playerData = playerData.copy(endlessMilestone = null)
                uiState = uiState.copy(
                    screen = AppScreen.RESULTS,
                    endlessMilestone = null,
                    canContinue = false,
                    result = MissionResultUiModel(
                        title = resources.getString(R.string.endless_cash_out_title),
                        stars = 0,
                        score = pending.cumulativeScore,
                        safeArrivals = 0,
                        safeDepartures = 0,
                        efficiencyBonus = 0,
                        separationPenalty = 0,
                        personalBest = personalBest,
                        scoreRows = listOf(
                            ScoreRowUiModel(
                                "carried",
                                localizedScoreRowLabel("carried", resources),
                                pending.cumulativeScore,
                            ),
                        ),
                    ),
                    progressionSaveStatus = ProgressionSaveStatus.SAVED,
                )
                persistUiState()
            } catch (_: Exception) {
                uiState = uiState.copy(
                    endlessMilestone = pending.toUiModel(),
                    sessionPersistenceFailed = true,
                )
            }
        }
    }

    private fun openNextMission() {
        if (uiState.progressionSaveStatus != ProgressionSaveStatus.SAVED) return
        val nextId = uiState.nextMissionId ?: return navigate(AppScreen.MISSIONS)
        if (uiState.missions.any { it.id == nextId }) {
            uiState = uiState.copy(selectedMissionId = nextId, screen = AppScreen.MISSIONS)
            persistUiState()
        }
    }

    private fun pauseAndCheckpoint(forcePause: Boolean) {
        val shouldPause = forcePause || playerData.settings.pauseOnFocusLoss
        if (replayController != null) {
            if (shouldPause) {
                uiState = uiState.copy(replay = uiState.replay?.copy(isPlaying = false))
            }
            return
        }
        val currentEngine = engine ?: return
        val snapshot = lastSnapshot ?: currentEngine.snapshot
        if (shouldPause && snapshot.status == GameStatus.RUNNING) {
            publish(currentEngine.submit(PlayerCommand.Pause))
        }
        if (snapshot.status.isActive()) checkpoint()
    }

    private fun checkpoint() {
        val descriptor = activeDescriptor ?: return
        val snapshot = lastSnapshot ?: return
        if (!snapshot.status.isActive() || checkpointingDisabled) return
        if (replayLog.size > MAX_REPLAY_ENTRIES) {
            disableSessionPersistence()
            return
        }
        val restorable = RestorableSession(
            descriptor = descriptor,
            savedTick = snapshot.tick,
            speedMultiplier = snapshot.speedMultiplier,
            wasPaused = snapshot.status == GameStatus.PAUSED,
            selectedAircraftId = uiState.selectedAircraftId,
            trainingLessonId = if (uiState.tutorialStep != null) {
                activeTrainingLesson()?.id
            } else {
                null
            },
            trainingStep = uiState.tutorialStep,
            attemptId = activeAttemptId,
            entries = replayLog.toList(),
        )
        checkpointJob?.cancel()
        lastCheckpointTick = snapshot.tick
        checkpointJob = viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.Default) { restorable.toPayload() }
                if (payload.length > MAX_SESSION_PAYLOAD_CHARS) {
                    disableSessionPersistence()
                    return@launch
                }
                preferences.saveActiveSession(
                    ActiveSessionRecord(
                        schemaVersion = SESSION_SCHEMA,
                        scenarioId = snapshot.scenarioId,
                        savedAtEpochMillis = System.currentTimeMillis(),
                        payload = payload,
                    ),
                )
                if (descriptor is SessionDescriptor.Endless &&
                    playerData.endlessMilestone?.choice == EndlessMilestoneChoice.CONTINUE_PENDING
                ) {
                    preferences.clearEndlessMilestone()
                    playerData = playerData.copy(endlessMilestone = null)
                    uiState = uiState.copy(endlessMilestone = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                disableSessionPersistence()
            }
        }
    }

    private fun applyPlayerData(updated: PlayerData) {
        playerData = updated
        val activeInMemory = replayController == null && lastSnapshot?.status?.isActive() == true
        val record = updated.activeSession
        val recordKey = record?.toSessionKey()
        val persisted = !activeInMemory && !suppressPersistedSession &&
            recordKey == validatedSessionKey && validatedSessionAvailable
        val missions = missionModels(resources, updated.progress)
        val selectedId = activeDescriptor?.selectionId?.takeIf {
            it == CUSTOM_SELECTION_ID || it == DAILY_SELECTION_ID
        }
            ?: uiState.selectedMissionId.takeIf { id ->
            missions.any { it.id == id }
        } ?: ContentRegistry.firstMissionIds.first()
        uiState = uiState.copy(
            selectedMissionId = selectedId,
            missions = missions,
            career = careerModel(updated.progress),
            serviceRecord = serviceRecordUi(updated.serviceRecord, updated.progress),
            customShift = customShiftUiWithDaily(customConfiguration),
            settings = updated.settings.toUiState(),
            settingsLoaded = true,
            canContinue = activeInMemory || persisted || updated.endlessMilestone != null,
            endlessMilestone = updated.endlessMilestone?.toUiModel(),
            completedReplays = updated.completedReplays.map { replay ->
                CompletedReplayUiModel(
                    id = replay.id,
                    scenarioId = replay.scenarioId,
                    score = replay.finalScore,
                    terminalTick = replay.terminalTick,
                )
            },
        )
        if (restoredScreen == AppScreen.MILESTONE && updated.endlessMilestone != null &&
            uiState.screen != AppScreen.GAME
        ) {
            uiState = uiState.copy(screen = AppScreen.MILESTONE)
        }
        if (resumeMilestoneActionOnLoad) {
            resumeMilestoneActionOnLoad = false
            when {
                updated.endlessMilestone?.choice == EndlessMilestoneChoice.CASH_OUT_PENDING ->
                    cashOutEndlessRun()
                updated.endlessMilestone?.choice == EndlessMilestoneChoice.CONTINUE_PENDING &&
                    updated.activeSession == null -> startScenario(
                        SessionDescriptor.Endless(
                            updated.endlessMilestone.seed,
                            updated.endlessMilestone.completedStage + 1,
                            updated.endlessMilestone.cumulativeScore,
                            updated.endlessMilestone.contentPackId,
                        ),
                    )
            }
        }
        if (resumePendingProgressionOnLoad &&
            uiState.progressionSaveStatus in setOf(ProgressionSaveStatus.SAVING, ProgressionSaveStatus.FAILED)
        ) {
            resumePendingProgressionOnLoad = false
            persistPendingProgression()
        }
        if (restorePersistedSessionOnLoad) {
            restorePersistedSessionOnLoad = false
            if (record != null) {
                restoreScenario(record)
            } else {
                uiState = uiState.copy(
                    screen = AppScreen.MISSIONS,
                    isRestoring = false,
                    canContinue = false,
                )
                persistUiState()
            }
        } else {
            persistUiState()
            when {
                activeInMemory || suppressPersistedSession -> Unit
                record == null -> {
                    sessionValidationJob?.cancel()
                    validatedSessionKey = null
                    validatedSessionAvailable = false
                }
                recordKey != validatedSessionKey -> validatePersistedSession(record)
            }
        }
    }

    private fun validatePersistedSession(record: ActiveSessionRecord) {
        val key = record.toSessionKey()
        sessionValidationJob?.cancel()
        validatedSessionKey = key
        validatedSessionAvailable = false
        uiState = uiState.copy(canContinue = false)
        sessionValidationJob = viewModelScope.launch {
            val valid = withContext(Dispatchers.Default) { restorableFromRecord(record) != null }
            if (
                playerData.activeSession?.toSessionKey() != key ||
                lastSnapshot?.status?.isActive() == true
            ) {
                return@launch
            }
            validatedSessionAvailable = valid
            uiState = uiState.copy(
                canContinue = valid,
                sessionPersistenceFailed = uiState.sessionPersistenceFailed || !valid,
            )
            if (!valid) {
                suppressPersistedSession = true
                try {
                    preferences.clearActiveSession()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the invalid record suppressed if storage is temporarily unavailable.
                }
            }
        }
    }

    private fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        if (!uiState.settingsLoaded) return
        val optimistic = transform(playerData.settings)
        playerData = playerData.copy(settings = optimistic)
        uiState = uiState.copy(settings = optimistic.toUiState())
        viewModelScope.launch { preferences.updateSettings(transform) }
    }

    private fun disableSessionPersistence() {
        checkpointingDisabled = true
        lastCheckpointTick = lastSnapshot?.tick ?: lastCheckpointTick
        if (!uiState.sessionPersistenceFailed) {
            uiState = uiState.copy(sessionPersistenceFailed = true)
        }
    }

    private fun persistUiState() {
        savedStateHandle[STATE_SCREEN] = uiState.screen.name
        savedStateHandle[STATE_SELECTED_MISSION] = uiState.selectedMissionId
        savedStateHandle[STATE_TUTORIAL_STEP] = uiState.tutorialStep ?: NO_TUTORIAL_STEP
        val result = uiState.result
        if (result == null) {
            RESULT_STATE_KEYS.forEach { savedStateHandle.remove<Any>(it) }
            return
        }
        savedStateHandle[STATE_RESULT_TITLE] = result.title
        savedStateHandle[STATE_RESULT_STARS] = result.stars
        savedStateHandle[STATE_RESULT_SCORE] = result.score
        savedStateHandle[STATE_RESULT_ARRIVALS] = result.safeArrivals
        savedStateHandle[STATE_RESULT_DEPARTURES] = result.safeDepartures
        savedStateHandle[STATE_RESULT_EFFICIENCY] = result.efficiencyBonus
        savedStateHandle[STATE_RESULT_SEPARATION] = result.separationPenalty
        savedStateHandle[STATE_RESULT_PERSONAL_BEST] = result.personalBest
        savedStateHandle[STATE_RESULT_SUCCESSFUL] = result.successful
        savedStateHandle[STATE_RESULT_ROWS] = result.scoreRows.joinToString(";") { "${it.id}:${it.points}" }
        if (result.pointsToNextStar == null) {
            savedStateHandle.remove<Int>(STATE_RESULT_NEXT_STAR)
        } else {
            savedStateHandle[STATE_RESULT_NEXT_STAR] = result.pointsToNextStar
        }
        savedStateHandle[STATE_PROGRESSION_STATUS] = uiState.progressionSaveStatus.name
        savedStateHandle[STATE_NEXT_MISSION] = uiState.nextMissionId
        val validated = pendingProgression?.validatedResult
        if (validated == null) {
            PENDING_RESULT_STATE_KEYS.forEach { key -> savedStateHandle.remove<Any?>(key) }
        } else {
            savedStateHandle[STATE_PENDING_RESULT_ID] = validated.resultId
            savedStateHandle[STATE_PENDING_RESULT_FOCUS] = validated.focus.name
            savedStateHandle[STATE_PENDING_RESULT_COMPLETION] = validated.completionSeconds
            savedStateHandle[STATE_PENDING_RESULT_MOVEMENTS] = validated.safeMovements
            savedStateHandle[STATE_PENDING_RESULT_STRIKES] = validated.strikes
            savedStateHandle[STATE_PENDING_RESULT_DEPARTURES] = validated.departures
            savedStateHandle[STATE_PENDING_RESULT_MISSED_EXITS] = validated.missedExits
            savedStateHandle[STATE_PENDING_RESULT_EFFICIENCY] = validated.routeEfficiencyPercent
        }
    }

    private fun restoredPendingProgression(): PendingProgression? {
        val result = uiState.result?.takeIf { it.successful } ?: return null
        val missionId = uiState.selectedMissionId.takeIf { it in ContentRegistry.missionIds }
            ?: return null
        if (uiState.progressionSaveStatus !in setOf(
                ProgressionSaveStatus.SAVING,
                ProgressionSaveStatus.FAILED,
            )) return null
        val validated = savedStateHandle.get<String>(STATE_PENDING_RESULT_ID)?.let { resultId ->
            ValidatedMissionResult(
                resultId = resultId,
                missionId = missionId,
                focus = savedStateHandle.get<String>(STATE_PENDING_RESULT_FOCUS)
                    ?.let { runCatching { TutorialFocus.valueOf(it) }.getOrNull() }
                    ?: ContentRegistry.mission(missionId)?.tutorialFocus
                    ?: TutorialFocus.NONE,
                stars = result.stars,
                score = result.score,
                completionSeconds = savedStateHandle.get<Int>(STATE_PENDING_RESULT_COMPLETION) ?: 0,
                safeMovements = savedStateHandle.get<Int>(STATE_PENDING_RESULT_MOVEMENTS) ?: 0,
                strikes = savedStateHandle.get<Int>(STATE_PENDING_RESULT_STRIKES) ?: 0,
                departures = savedStateHandle.get<Int>(STATE_PENDING_RESULT_DEPARTURES) ?: 0,
                missedExits = savedStateHandle.get<Int>(STATE_PENDING_RESULT_MISSED_EXITS) ?: 0,
                routeEfficiencyPercent = savedStateHandle.get<Int>(STATE_PENDING_RESULT_EFFICIENCY) ?: 0,
            )
        }
        return PendingProgression(missionId, result.stars, result.score, false, validated)
    }

    private fun resultFromSavedState(): MissionResultUiModel? {
        val title = savedStateHandle.get<String>(STATE_RESULT_TITLE) ?: return null
        return MissionResultUiModel(
            title = title,
            stars = savedStateHandle.get<Int>(STATE_RESULT_STARS) ?: return null,
            score = savedStateHandle.get<Int>(STATE_RESULT_SCORE) ?: return null,
            safeArrivals = savedStateHandle.get<Int>(STATE_RESULT_ARRIVALS) ?: return null,
            safeDepartures = savedStateHandle.get<Int>(STATE_RESULT_DEPARTURES) ?: return null,
            efficiencyBonus = savedStateHandle.get<Int>(STATE_RESULT_EFFICIENCY) ?: return null,
            separationPenalty = savedStateHandle.get<Int>(STATE_RESULT_SEPARATION) ?: return null,
            personalBest = savedStateHandle.get<Boolean>(STATE_RESULT_PERSONAL_BEST) ?: false,
            successful = savedStateHandle.get<Boolean>(STATE_RESULT_SUCCESSFUL) ?: true,
            scoreRows = savedStateHandle.get<String>(STATE_RESULT_ROWS).orEmpty()
                .split(';')
                .mapNotNull { encoded ->
                    val parts = encoded.split(':', limit = 2)
                    if (parts.size != 2) null else parts[1].toIntOrNull()?.let { points ->
                        ScoreRowUiModel(parts[0], scoreRowLabel(parts[0]), points)
                    }
                },
            pointsToNextStar = savedStateHandle.get<Int>(STATE_RESULT_NEXT_STAR),
        )
    }

    private fun scoreRowLabel(id: String): String = when (id) {
        else -> localizedScoreRowLabel(id, resources)
    }

    private fun serviceRecordUi(
        record: ControllerServiceRecord,
        progress: PlayerProgress,
    ): ServiceRecordUiModel {
        val mastery = TutorialFocus.entries.filterNot { it == TutorialFocus.NONE }.map { focus ->
            val value = record.masteryFor(focus)
            MasteryUiModel(
                focus = focus.subtitle(resources),
                level = value.level,
                contributingResults = value.contributingResultIds.size,
            )
        }
        val nextMission = ContentRegistry.authoredMissions.firstOrNull { mission ->
            mission.id in progress.unlockedMissionIds && mission.id !in progress.missionStars
        }
        val weakest = mastery.minWithOrNull(
            compareBy<MasteryUiModel> { it.level }.thenBy { it.focus },
        )
        val recommendation = when {
            progress.missionStars.isEmpty() -> resources.getString(R.string.recommend_first_shift)
            nextMission != null -> resources.getString(
                R.string.recommend_next_mission,
                localizedScenarioTitle(SessionDescriptor.Authored(nextMission.id)),
            )
            weakest != null -> resources.getString(
                R.string.recommend_weak_focus,
                weakest.focus,
                weakest.level,
            )
            else -> resources.getString(R.string.recommend_first_shift)
        }
        return ServiceRecordUiModel(
            totalSafeMovements = record.totalSafeMovements,
            currentSafeStreak = record.currentSafeStreak,
            bestSafeStreak = record.bestSafeStreak,
            achievementCount = record.achievementIds.size,
            mastery = mastery,
            recommendation = recommendation,
        )
    }

    private fun EndlessMilestoneRecord.toUiModel(): EndlessMilestoneUiModel {
        val next = EndlessScenarioGenerator.generate(seed, completedStage + 1, contentPackId)
        return EndlessMilestoneUiModel(
            completedStage = completedStage,
            stageScore = stageScore,
            cumulativeScore = cumulativeScore,
            personalBestDelta = cumulativeScore - playerData.progress.endlessHighScoreFor(contentPackId),
            nextStage = completedStage + 1,
            nextTrafficCount = next.traffic.size,
            nextObjective = resources.getQuantityString(
                R.plurals.endless_next_objective,
                next.traffic.size,
                next.traffic.size,
            ),
            nextWeather = resources.getString(
                R.string.weather_wind_visibility,
                String.format(
                    Locale.UK,
                    "%03d°/%02dkt",
                    next.weather.windDirectionDegrees,
                    next.weather.windSpeedKnots,
                ),
                next.weather.visibilityKm,
            ),
            choicePending = choice != EndlessMilestoneChoice.AWAITING,
        )
    }

    private fun recordTrails(snapshot: GameSnapshot) {
        val visibleAircraft = snapshot.aircraft.filter(AircraftState::isUiVisible)
        val liveIds = visibleAircraft.mapTo(mutableSetOf(), AircraftState::id)
        trails.keys.retainAll(liveIds)
        visibleAircraft.forEach { aircraft ->
            val point = aircraft.position.toUiPoint()
            val history = trails.getOrPut(aircraft.id) { mutableListOf() }
            if (history.lastOrNull() != point) {
                history += point
                while (history.size > TRAIL_POINT_COUNT + 1) history.removeAt(0)
            }
        }
    }

    private fun AircraftState.toUiModel(snapshot: GameSnapshot): AircraftUiModel {
        val conflict = snapshot.conflicts
            .filter { it.firstAircraftId == id || it.secondAircraftId == id }
            .maxByOrNull { it.kind.severity }
        return AircraftUiModel(
            id = id,
            callsign = callsign,
            type = type.displayCode,
            position = position.toUiPoint(),
            headingDegrees = headingDegrees.toFloat(),
            altitudeFeet = altitudeFeet.roundToInt(),
            targetAltitudeFeet = targetAltitudeFeet.roundToInt(),
            speedKnots = speedKnots.roundToInt(),
            targetSpeedKnots = targetSpeedKnots.roundToInt(),
            phase = status.toUiPhase(),
            clearance = clearance.toUiText(status, resources),
            assignedRunway = runwayId,
            fuelPercent = ((fuelRemainingSeconds / fuelCapacitySeconds) * 100.0)
                .roundToInt()
                .coerceIn(0, 100),
            trail = trails[id].orEmpty().dropLast(1),
            route = route.waypoints.drop(routeIndex).map(Vec2::toUiPoint),
            conflictLevel = when (conflict?.kind) {
                ConflictKind.PREDICTED -> ConflictLevel.PREDICTED
                ConflictKind.LOSS_OF_SEPARATION, ConflictKind.COLLISION -> ConflictLevel.LOSS
                null -> ConflictLevel.NONE
            },
            holdFixName = hold?.fix?.let { fixNamesByPosition[it] },
            holdSeconds = holdAccumulatedSeconds.roundToInt(),
            handoffStatus = handoff?.status?.name,
            exitClearanceGranted = exitClearanceGranted,
        )
    }

    private fun GameSnapshot.toRunwayUiModels(): List<RunwayUiModel> {
        val content = activeContent
        val wind = content?.weather?.let {
            String.format(Locale.UK, "%03d° / %02d kt", it.windDirectionDegrees, it.windSpeedKnots)
        } ?: "Calm"
        return runways.map { runway ->
            RunwayUiModel(
                id = runway.id,
                label = runway.id,
                center = NormalizedPoint(
                    ((runway.threshold.x + runway.end.x) / 2.0).toFloat(),
                    ((runway.threshold.y + runway.end.y) / 2.0).toFloat(),
                ),
                headingDegrees = runway.headingDegrees.toFloat(),
                isOccupied = runway.occupiedByAircraftId != null,
                isActive = runway.active,
                wind = wind,
            )
        }
    }

    private fun fixesFor(snapshot: GameSnapshot): List<FixUiModel> = buildList {
        val airport = activeAirport()
        airport.fixes.forEach { fix ->
            add(
                FixUiModel(
                    name = fix.displayName,
                    position = NormalizedPoint(fix.position.x.toFloat(), fix.position.y.toFloat()),
                    kind = when (fix.use) {
                        FixUse.ENTRY -> FixKind.ENTRY
                        FixUse.EXIT -> FixKind.EXIT
                        FixUse.ENTRY_AND_EXIT -> FixKind.ENTRY
                    },
                ),
            )
        }
        val activeRunways = snapshot.runways.mapTo(mutableSetOf()) { it.id }
        airport.runwayEnds.filter { it.id in activeRunways }.forEach { runway ->
            add(
                FixUiModel(
                    name = "I-${runway.id}",
                    position = NormalizedPoint(
                        runway.approachGate.x.toFloat(),
                        runway.approachGate.y.toFloat(),
                    ),
                    kind = FixKind.APPROACH,
                ),
            )
        }
    }

    private fun activeAirport() = activeContent?.airportId
        ?.let(ContentRegistry::airport)
        ?: ContentRegistry.mission(uiState.selectedMissionId)
            ?.airportId
            ?.let(ContentRegistry::airport)
        ?: checkNotNull(ContentRegistry.pack(ContentRegistry.DEFAULT_PACK_ID)).airport

    private fun finalApproachPoints(runwayEndId: String): List<com.stuart.atccontroller.data.NormalizedPoint> {
        val airport = activeAirport()
        val runway = airport.runwayEnds.firstOrNull { it.id == runwayEndId }
            ?: error("Unknown runway end $runwayEndId for ${airport.id}")
        val intercept = com.stuart.atccontroller.simulation.Navigation.move(
            position = Vec2(runway.threshold.x, runway.threshold.y),
            headingDegrees = runway.headingDegrees + 180.0,
            distanceNm = 2.5,
            mapWidthNm = airport.mapWidthNm,
            mapHeightNm = airport.mapHeightNm,
        )
        return listOf(
            com.stuart.atccontroller.data.NormalizedPoint(intercept.x, intercept.y),
            runway.threshold,
        )
    }

    private fun emitFeedback(events: List<GameEvent>) {
        val kind = events.mapNotNull(GameEvent::feedbackKind).maxByOrNull(LiveFeedbackKind::priority)
            ?: return
        feedbackCue = LiveFeedbackCue(++feedbackSequence, kind)
    }

    private fun descriptorForSelection(id: String): SessionDescriptor? {
        val endlessPackId = ContentRegistry.packs.firstOrNull { endlessSelectionId(it.id) == id }?.id
        return endlessPackId?.let {
            SessionDescriptor.Endless(DEFAULT_ENDLESS_SEED, 1, 0, it)
        } ?: ContentRegistry.mission(id)?.let { SessionDescriptor.Authored(it.id) }
    }

    private fun localizedScenarioTitle(descriptor: SessionDescriptor): String = when (descriptor) {
        is SessionDescriptor.Authored -> {
            val index = ContentRegistry.pack(ContentRegistry.DEFAULT_PACK_ID)
                ?.authoredMissions?.indexOfFirst { it.id == descriptor.missionId } ?: -1
            MISSION_TITLE_RES_IDS.getOrNull(index)?.let { resources.getString(it) }
                ?: ContentRegistry.mission(descriptor.missionId)?.title
                ?: resources.getString(R.string.shift_fallback)
        }
        is SessionDescriptor.Training -> resources.getString(
            R.string.training_title_format,
            localizedScenarioTitle(SessionDescriptor.Authored(descriptor.missionId)),
        )
        is SessionDescriptor.Custom -> resources.getString(
            if (descriptor.configuration.isRanked) {
                R.string.ranked_seeded_shift
            } else {
                R.string.custom_practice_shift
            },
        )
        is SessionDescriptor.Daily -> resources.getString(
            R.string.daily_shift_title,
            descriptor.localDate,
        )
        is SessionDescriptor.Endless -> resources.getString(
            R.string.endless_stage_title,
            descriptor.stage,
        )
    }

    private fun restorableFromRecord(record: ActiveSessionRecord?): RestorableSession? {
        if (record == null || record.schemaVersion !in MIN_SESSION_SCHEMA..SESSION_SCHEMA) return null
        return runCatching {
            require(record.payload.length <= MAX_SESSION_PAYLOAD_CHARS)
            val lines = record.payload.split('\n')
            require(lines.size in 3..(MAX_REPLAY_ENTRIES + 3))
            require(lines[0] == "replay-v${record.schemaVersion}")

            val descriptorFields = lines[1].split('|')
            require(descriptorFields.firstOrNull() == "D")
            val descriptor = when (descriptorFields.getOrNull(1)) {
                "A" -> {
                    require(descriptorFields.size == 3)
                    SessionDescriptor.Authored(descriptorFields[2]).also {
                        require(ContentRegistry.mission(it.missionId) != null)
                    }
                }
                "T" -> {
                    require(record.schemaVersion >= 3 && descriptorFields.size == 3)
                    SessionDescriptor.Training(descriptorFields[2]).also {
                        require(ContentRegistry.mission(it.missionId) != null)
                    }
                }
                "E" -> {
                    require(descriptorFields.size == 5 ||
                        (record.schemaVersion >= 5 && descriptorFields.size == 6))
                    SessionDescriptor.Endless(
                        seed = checkNotNull(descriptorFields[2].toLongOrNull()),
                        stage = checkNotNull(descriptorFields[3].toIntOrNull()),
                        cumulativeScore = checkNotNull(descriptorFields[4].toIntOrNull()),
                        contentPackId = descriptorFields.getOrNull(5)
                            ?: ContentRegistry.DEFAULT_PACK_ID,
                    ).also {
                        require(it.stage in 1..MAX_ENDLESS_STAGE)
                        require(it.cumulativeScore >= 0)
                        require(ContentRegistry.pack(it.contentPackId) != null)
                    }
                }
                "C" -> {
                    require(record.schemaVersion >= 4 && descriptorFields.size == 3)
                    SessionDescriptor.Custom(
                        checkNotNull(ShiftConfigurationCodec.decode(descriptorFields[2])),
                    )
                }
                "Y" -> {
                    require(record.schemaVersion >= 4 && descriptorFields.size == 4)
                    val date = LocalDate.parse(descriptorFields[2])
                    val configuration = checkNotNull(
                        ShiftConfigurationCodec.decode(descriptorFields[3]),
                    )
                    require(configuration == DailyShift.configurationFor(date))
                    SessionDescriptor.Daily(date, configuration)
                }
                else -> error("Unknown session descriptor")
            }

            val metadata = lines[2].split('|')
            require(metadata[0] == "S")
            require(
                (record.schemaVersion == 2 && metadata.size == 5) ||
                    (record.schemaVersion == 3 && metadata.size == 7) ||
                    (record.schemaVersion >= 4 && metadata.size == 8),
            )
            val savedTick = checkNotNull(metadata[1].toLongOrNull()).also { require(it >= 0) }
            val speed = checkNotNull(metadata[2].toDoubleOrNull()).also { value ->
                require(value == 1.0 || value == 2.0)
            }
            val paused = checkNotNull(metadata[3].toBooleanStrictOrNull())
            val selectedAircraftId = metadata[4].takeIf(String::isNotBlank)
            val trainingLessonId = metadata.getOrNull(5)?.takeIf(String::isNotBlank)
            val trainingStep = metadata.getOrNull(6)?.takeIf(String::isNotBlank)?.let { value ->
                checkNotNull(value.toIntOrNull()).also { require(it in 0 until MAX_TRAINING_STEPS) }
            }
            require((trainingLessonId == null) == (trainingStep == null))
            val attemptId = metadata.getOrNull(7)?.takeIf(String::isNotBlank)
            require(attemptId == null || attemptId.length <= 100)
            val entries = lines.drop(3).map(::parseReplayEntry)
            require(entries.zipWithNext().all { (first, second) -> first.tick <= second.tick })
            require(entries.all { it.tick <= savedTick })

            val content = descriptor.content()
            require(record.scenarioId == content.id)
            val trainingLesson = TrainingAcademy.lessonFor(content.tutorialFocus)
            if (trainingLessonId != null) {
                require(trainingLesson != null)
                require(trainingLessonId == trainingLesson.id)
                require(trainingStep in trainingLesson.steps.indices)
            }
            if (descriptor is SessionDescriptor.Training) {
                require(trainingLessonId != null)
            }
            val aircraftIds = content.traffic.mapTo(mutableSetOf(), TrafficSpawnDefinition::id)
            require(selectedAircraftId == null || selectedAircraftId in aircraftIds)
            val runwayIds = content.runwayConfiguration.arrivalEndIds +
                content.runwayConfiguration.departureEndIds +
                content.weatherChanges.flatMap { it.activeRunwayEndIds }
            val dynamicEventIds = content.dynamicEvents.mapTo(mutableSetOf()) { it.id }
            entries.forEach { entry ->
                when (val command = entry.command) {
                    is PlayerCommand.AcknowledgeDynamicEvent -> require(command.eventId in dynamicEventIds)
                    is PlayerCommand.ClearForTakeoff -> require(command.runwayId in runwayIds)
                    is PlayerCommand.ClearToLand -> require(command.runwayId in runwayIds)
                    is PlayerCommand.AssignRunway -> require(command.runwayId in runwayIds)
                    is PlayerCommand.AssignApproach -> require(command.runwayId in runwayIds)
                    is PlayerCommand.LineUpAndWait -> require(command.runwayId in runwayIds)
                    is PlayerCommand.CrossRunway -> require(command.runwayId in runwayIds)
                    else -> Unit
                }
                if (entry.command !is PlayerCommand.AcknowledgeDynamicEvent) {
                    require(entry.command.aircraftId() in aircraftIds)
                }
            }
            RestorableSession(
                descriptor = descriptor,
                savedTick = savedTick,
                speedMultiplier = speed,
                wasPaused = paused,
                selectedAircraftId = selectedAircraftId,
                trainingLessonId = trainingLessonId,
                trainingStep = trainingStep,
                attemptId = attemptId,
                entries = entries,
            )
        }.getOrNull()
    }

    private fun ActiveSessionRecord.toSessionKey() = SessionRecordKey(
        schemaVersion = schemaVersion,
        scenarioId = scenarioId,
        savedAtEpochMillis = savedAtEpochMillis,
        payloadLength = payload.length,
    )

    private fun parseReplayEntry(line: String): ReplayEntry {
        val fields = line.split('|', limit = 5)
        require(fields.size == 5 && fields[0] == "C")
        val tick = checkNotNull(fields[1].toLongOrNull()).also { require(it >= 0) }
        val aircraftId = fields[3].also { require(it.isNotBlank() && it.length <= 100) }
        val value = fields[4]
        val command = when (fields[2]) {
            "R" -> {
                val points = if (value.isEmpty()) {
                    emptyList()
                } else {
                    value.split(';').also { require(it.size <= MAX_ROUTE_POINTS) }.map { encoded ->
                        val coordinates = encoded.split(',', limit = 2)
                        require(coordinates.size == 2)
                        Vec2(
                            x = checkNotNull(coordinates[0].toDoubleOrNull()),
                            y = checkNotNull(coordinates[1].toDoubleOrNull()),
                        ).also { require(it.isValidNormalizedWaypoint()) }
                    }
                }
                PlayerCommand.SetRoute(aircraftId, Route(points))
            }
            "A" -> PlayerCommand.SetTargetAltitude(
                aircraftId,
                checkNotNull(value.toDoubleOrNull()).also { require(it in 0.0..50_000.0) },
            )
            "V" -> PlayerCommand.SetTargetSpeed(
                aircraftId,
                checkNotNull(value.toDoubleOrNull()).also { require(it in 0.0..1_000.0) },
            )
            "L" -> PlayerCommand.ClearToLand(aircraftId, value.also { require(it.isNotBlank()) })
            "T" -> PlayerCommand.ClearForTakeoff(aircraftId, value.also { require(it.isNotBlank()) })
            "G" -> PlayerCommand.GoAround(
                aircraftId,
                checkNotNull(value.toDoubleOrNull()).also { require(it in 1_000.0..10_000.0) },
            )
            "D" -> parseWaypoint(value).let { PlayerCommand.DirectTo(aircraftId, it) }
            "P" -> parseWaypoint(value).let { PlayerCommand.AppendWaypoint(aircraftId, it) }
            "U" -> PlayerCommand.UndoWaypoint(aircraftId)
            "C" -> PlayerCommand.ClearRoute(aircraftId)
            "W" -> PlayerCommand.AssignRunway(aircraftId, value.also { require(it.isNotBlank()) })
            "I" -> PlayerCommand.AssignApproach(aircraftId, value.also { require(it.isNotBlank()) })
            "X" -> PlayerCommand.CancelApproach(aircraftId)
            "Q" -> PlayerCommand.LineUpAndWait(aircraftId, value.also { require(it.isNotBlank()) })
            "K" -> PlayerCommand.CancelLandingClearance(aircraftId)
            "O" -> PlayerCommand.CancelTakeoffClearance(aircraftId)
            "H" -> {
                val parts = value.split(',', limit = 6)
                require(parts.size == 6)
                PlayerCommand.AssignHold(
                    aircraftId = aircraftId,
                    fix = Vec2(
                        checkNotNull(parts[0].toDoubleOrNull()),
                        checkNotNull(parts[1].toDoubleOrNull()),
                    ),
                    inboundCourseDegrees = checkNotNull(parts[2].toDoubleOrNull()),
                    altitudeFeet = checkNotNull(parts[3].toDoubleOrNull()),
                    turnDirection = HoldTurnDirection.valueOf(parts[4]),
                    legSeconds = checkNotNull(parts[5].toDoubleOrNull()),
                )
            }
            "Z" -> PlayerCommand.CancelHold(aircraftId)
            "E" -> PlayerCommand.IssueExitClearance(aircraftId)
            "B" -> PlayerCommand.AcknowledgeInboundHandoff(aircraftId)
            "N" -> PlayerCommand.InitiateOutboundHandoff(
                aircraftId,
                value.also { require(it.isNotBlank()) },
            )
            "Y" -> PlayerCommand.AcknowledgeDynamicEvent(aircraftId)
            "J" -> PlayerCommand.CrossRunway(
                aircraftId,
                value.also { require(it.isNotBlank()) },
            )
            else -> error("Unknown replay command")
        }
        return ReplayEntry(tick, command)
    }

    private fun parseWaypoint(value: String): Vec2 {
        val coordinates = value.split(',', limit = 2)
        require(coordinates.size == 2)
        return Vec2(
            checkNotNull(coordinates[0].toDoubleOrNull()),
            checkNotNull(coordinates[1].toDoubleOrNull()),
        ).also { require(it.isValidNormalizedWaypoint()) }
    }

    private sealed interface SessionDescriptor {
        val selectionId: String
        fun content(): ContentScenarioDefinition

        data class Authored(val missionId: String) : SessionDescriptor {
            override val selectionId = missionId
            override fun content() = checkNotNull(ContentRegistry.mission(missionId))
        }

        data class Training(val missionId: String) : SessionDescriptor {
            override val selectionId = missionId
            override fun content() = checkNotNull(ContentRegistry.mission(missionId)).toTrainingScenario()
        }

        data class Custom(val configuration: ShiftConfiguration) : SessionDescriptor {
            override val selectionId = CUSTOM_SELECTION_ID
            override fun content() = CustomShiftGenerator.generate(configuration)
        }

        data class Daily(
            val localDate: LocalDate,
            val configuration: ShiftConfiguration,
        ) : SessionDescriptor {
            override val selectionId = DAILY_SELECTION_ID
            override fun content() = CustomShiftGenerator.generate(configuration).copy(
                id = "manchester_daily_${localDate}_v${configuration.generatorVersion}",
                title = "Daily Shift $localDate",
            )
        }

        data class Endless(
            val seed: Long,
            val stage: Int,
            val cumulativeScore: Int,
            val contentPackId: String = ContentRegistry.DEFAULT_PACK_ID,
        ) : SessionDescriptor {
            override val selectionId = endlessSelectionId(contentPackId)
            override fun content() = EndlessScenarioGenerator.generate(seed, stage, contentPackId)
        }
    }

    private data class ReplayEntry(val tick: Long, val command: PlayerCommand) {
        fun toPayloadLine(): String {
            val encoded = when (command) {
                is PlayerCommand.SetRoute -> listOf(
                    "R",
                    command.aircraftId,
                    command.route.waypoints.joinToString(";") { "${it.x},${it.y}" },
                )
                is PlayerCommand.SetTargetAltitude -> listOf("A", command.aircraftId, command.altitudeFeet.toString())
                is PlayerCommand.SetTargetSpeed -> listOf("V", command.aircraftId, command.speedKnots.toString())
                is PlayerCommand.ClearToLand -> listOf("L", command.aircraftId, command.runwayId)
                is PlayerCommand.ClearForTakeoff -> listOf("T", command.aircraftId, command.runwayId)
                is PlayerCommand.GoAround -> listOf("G", command.aircraftId, command.targetAltitudeFeet.toString())
                is PlayerCommand.DirectTo -> listOf("D", command.aircraftId, "${command.waypoint.x},${command.waypoint.y}")
                is PlayerCommand.AppendWaypoint -> listOf("P", command.aircraftId, "${command.waypoint.x},${command.waypoint.y}")
                is PlayerCommand.UndoWaypoint -> listOf("U", command.aircraftId, "")
                is PlayerCommand.ClearRoute -> listOf("C", command.aircraftId, "")
                is PlayerCommand.AssignRunway -> listOf("W", command.aircraftId, command.runwayId)
                is PlayerCommand.AssignApproach -> listOf("I", command.aircraftId, command.runwayId)
                is PlayerCommand.CancelApproach -> listOf("X", command.aircraftId, "")
                is PlayerCommand.LineUpAndWait -> listOf("Q", command.aircraftId, command.runwayId)
                is PlayerCommand.CancelLandingClearance -> listOf("K", command.aircraftId, "")
                is PlayerCommand.CancelTakeoffClearance -> listOf("O", command.aircraftId, "")
                is PlayerCommand.AssignHold -> listOf(
                    "H",
                    command.aircraftId,
                    listOf(
                        command.fix.x,
                        command.fix.y,
                        command.inboundCourseDegrees,
                        command.altitudeFeet,
                        command.turnDirection.name,
                        command.legSeconds,
                    ).joinToString(","),
                )
                is PlayerCommand.CancelHold -> listOf("Z", command.aircraftId, "")
                is PlayerCommand.IssueExitClearance -> listOf("E", command.aircraftId, "")
                is PlayerCommand.AcknowledgeInboundHandoff -> listOf("B", command.aircraftId, "")
                is PlayerCommand.InitiateOutboundHandoff -> listOf(
                    "N",
                    command.aircraftId,
                    command.sectorId,
                )
                is PlayerCommand.AcknowledgeDynamicEvent -> listOf("Y", command.eventId, "")
                is PlayerCommand.CrossRunway -> listOf("J", command.aircraftId, command.runwayId)
                PlayerCommand.Start,
                PlayerCommand.Pause,
                PlayerCommand.Resume,
                is PlayerCommand.SetSimulationSpeed -> error("Non-replayable command")
            }
            return "C|$tick|${encoded.joinToString("|")}"
        }
    }

    private data class RestorableSession(
        val descriptor: SessionDescriptor,
        val savedTick: Long,
        val speedMultiplier: Double,
        val wasPaused: Boolean,
        val selectedAircraftId: String?,
        val trainingLessonId: String?,
        val trainingStep: Int?,
        val attemptId: String?,
        val entries: List<ReplayEntry>,
    ) {
        fun toPayload(): String = buildList {
            add(SESSION_PAYLOAD_PREFIX)
            add(
                when (descriptor) {
                    is SessionDescriptor.Authored -> "D|A|${descriptor.missionId}"
                    is SessionDescriptor.Training -> "D|T|${descriptor.missionId}"
                    is SessionDescriptor.Custom ->
                        "D|C|${ShiftConfigurationCodec.encode(descriptor.configuration)}"
                    is SessionDescriptor.Daily ->
                        "D|Y|${descriptor.localDate}|${ShiftConfigurationCodec.encode(descriptor.configuration)}"
                    is SessionDescriptor.Endless ->
                        "D|E|${descriptor.seed}|${descriptor.stage}|${descriptor.cumulativeScore}|" +
                            descriptor.contentPackId
                },
            )
            add(
                "S|$savedTick|$speedMultiplier|$wasPaused|${selectedAircraftId.orEmpty()}|" +
                    "${trainingLessonId.orEmpty()}|${trainingStep?.toString().orEmpty()}|" +
                    attemptId.orEmpty(),
            )
            entries.forEach { add(it.toPayloadLine()) }
        }.joinToString("\n")
    }

    private data class RestoredEngine(
        val content: ContentScenarioDefinition,
        val engine: AtcSimulationEngine,
        val snapshot: GameSnapshot,
        val restoreTutorial: Boolean,
    )

    private data class ReconstructedSession(
        val saved: RestorableSession,
        val restored: RestoredEngine,
    )

    private data class ReplayController(
        val saved: RestorableSession,
        val terminalTick: Long,
        val expectedScore: Int,
        val expectedTerminalHash: String,
        var nextEntryIndex: Int,
    )

    private data class SessionRecordKey(
        val schemaVersion: Int,
        val scenarioId: String,
        val savedAtEpochMillis: Long,
        val payloadLength: Int,
    )

    private data class PendingProgression(
        val missionId: String,
        val stars: Int,
        val score: Int,
        val endless: Boolean,
        val validatedResult: ValidatedMissionResult?,
    )

    companion object {
        private const val TICK_MILLIS = 100L
        private const val TICK_SECONDS = 0.1
        private const val TICKS_PER_SECOND = 10
        private const val CHECKPOINT_TICKS = 50L
        private const val RESTORE_STEP_CHUNK = 100
        private const val TRAIL_POINT_COUNT = 10
        private const val EVENT_FEED_CAPACITY = AtcSimulationEngine.EVENT_HISTORY_CAPACITY
        private const val MAX_TRAINING_STEPS = 100
        private const val MIN_SESSION_SCHEMA = 2
        private const val SESSION_SCHEMA = 5
        private const val SESSION_PAYLOAD_PREFIX = "replay-v5"
        private const val MAX_REPLAY_ENTRIES = 4_096
        private const val MAX_ROUTE_POINTS = AtcSimulationEngine.MAX_ROUTE_WAYPOINTS
        private const val MAX_ENDLESS_STAGE = 10_000
        private const val MAX_SESSION_PAYLOAD_CHARS = 500_000
        private const val ENDLESS_SELECTION_ID = "endless"
        private const val CUSTOM_SELECTION_ID = "custom"
        private const val DAILY_SELECTION_ID = "daily"
        private const val DEFAULT_ENDLESS_SEED = 20_260_416L

        private fun endlessSelectionId(contentPackId: String): String =
            if (contentPackId == ContentRegistry.DEFAULT_PACK_ID) {
                ENDLESS_SELECTION_ID
            } else {
                "$ENDLESS_SELECTION_ID:$contentPackId"
            }
        private const val STATE_SCREEN = "ui.screen"
        private const val STATE_SELECTED_MISSION = "ui.selectedMission"
        private const val STATE_TUTORIAL_STEP = "ui.tutorialStep"
        private const val NO_TUTORIAL_STEP = -1
        private const val STATE_RESULT_TITLE = "ui.result.title"
        private const val STATE_RESULT_STARS = "ui.result.stars"
        private const val STATE_RESULT_SCORE = "ui.result.score"
        private const val STATE_RESULT_ARRIVALS = "ui.result.arrivals"
        private const val STATE_RESULT_DEPARTURES = "ui.result.departures"
        private const val STATE_RESULT_EFFICIENCY = "ui.result.efficiency"
        private const val STATE_RESULT_SEPARATION = "ui.result.separation"
        private const val STATE_RESULT_PERSONAL_BEST = "ui.result.personalBest"
        private const val STATE_RESULT_SUCCESSFUL = "ui.result.successful"
        private const val STATE_RESULT_ROWS = "ui.result.rows"
        private const val STATE_RESULT_NEXT_STAR = "ui.result.nextStar"
        private const val STATE_PROGRESSION_STATUS = "ui.progression.status"
        private const val STATE_NEXT_MISSION = "ui.progression.nextMission"
        private const val STATE_PENDING_RESULT_ID = "ui.progression.resultId"
        private const val STATE_PENDING_RESULT_FOCUS = "ui.progression.focus"
        private const val STATE_PENDING_RESULT_COMPLETION = "ui.progression.completion"
        private const val STATE_PENDING_RESULT_MOVEMENTS = "ui.progression.movements"
        private const val STATE_PENDING_RESULT_STRIKES = "ui.progression.strikes"
        private const val STATE_PENDING_RESULT_DEPARTURES = "ui.progression.departures"
        private const val STATE_PENDING_RESULT_MISSED_EXITS = "ui.progression.missedExits"
        private const val STATE_PENDING_RESULT_EFFICIENCY = "ui.progression.efficiencyPercent"
        private val PENDING_RESULT_STATE_KEYS = listOf(
            STATE_PENDING_RESULT_ID,
            STATE_PENDING_RESULT_FOCUS,
            STATE_PENDING_RESULT_COMPLETION,
            STATE_PENDING_RESULT_MOVEMENTS,
            STATE_PENDING_RESULT_STRIKES,
            STATE_PENDING_RESULT_DEPARTURES,
            STATE_PENDING_RESULT_MISSED_EXITS,
            STATE_PENDING_RESULT_EFFICIENCY,
        )
        private val RESULT_STATE_KEYS = listOf(
            STATE_RESULT_TITLE,
            STATE_RESULT_STARS,
            STATE_RESULT_SCORE,
            STATE_RESULT_ARRIVALS,
            STATE_RESULT_DEPARTURES,
            STATE_RESULT_EFFICIENCY,
            STATE_RESULT_SEPARATION,
            STATE_RESULT_PERSONAL_BEST,
            STATE_RESULT_SUCCESSFUL,
            STATE_RESULT_ROWS,
            STATE_RESULT_NEXT_STAR,
            STATE_PROGRESSION_STATUS,
            STATE_NEXT_MISSION,
        )

        private fun initialUiState(resources: Resources) = GameUiState(
            selectedMissionId = ContentRegistry.firstMissionIds.first(),
            missions = missionModels(resources, PlayerProgress()),
            career = careerModel(PlayerProgress()),
            fixes = checkNotNull(ContentRegistry.pack(ContentRegistry.DEFAULT_PACK_ID)).airport.fixes.map { fix ->
                FixUiModel(
                    fix.displayName,
                    NormalizedPoint(fix.position.x.toFloat(), fix.position.y.toFloat()),
                )
            },
        )

        private fun missionModels(
            resources: Resources,
            progress: PlayerProgress,
        ): List<MissionUiModel> {
            val authored = ContentRegistry.authoredMissions.map { mission ->
                val airport = checkNotNull(ContentRegistry.airport(mission.airportId))
                val pack = checkNotNull(ContentRegistry.packForMission(mission.id))
                val localizedIndex = ContentRegistry.pack(ContentRegistry.DEFAULT_PACK_ID)
                    ?.authoredMissions?.indexOfFirst { it.id == mission.id } ?: -1
                mission.toUiModel(
                    resources = resources,
                    airport = airport,
                    id = mission.id,
                    number = pack.authoredMissions.indexOf(mission) + 1,
                    title = MISSION_TITLE_RES_IDS.getOrNull(localizedIndex)
                        ?.let { resources.getString(it) }
                        ?: mission.title,
                    briefing = MISSION_BRIEFING_RES_IDS.getOrNull(localizedIndex)
                        ?.let { resources.getString(it) }
                        ?: mission.briefing,
                    subtitle = mission.tutorialFocus.subtitle(resources),
                    bestStars = progress.missionStars[mission.id] ?: 0,
                    bestScore = progress.missionBestScores[mission.id],
                    completed = mission.id in progress.missionStars,
                    locked = mission.id !in progress.unlockedMissionIds,
                    trainingAvailable = progress.tutorialCompleted ||
                        mission.id in progress.unlockedMissionIds,
                    pack = pack,
                )
            }
            val endlessModes = ContentRegistry.packs.map { pack ->
                val introductoryMissionsCompleted = pack.authoredMissions.firstOrNull()
                    ?.let { (progress.missionStars[it.id] ?: 0) > 0 } == true
                val highScore = progress.endlessHighScoreFor(pack.id)
                EndlessScenarioGenerator.generate(DEFAULT_ENDLESS_SEED, 1, pack.id).toUiModel(
                    resources = resources,
                    airport = pack.airport,
                    id = endlessSelectionId(pack.id),
                    number = pack.authoredMissions.size + 1,
                    title = resources.getString(R.string.endless_sector_title),
                    subtitle = if (highScore > 0) {
                        resources.getString(
                            R.string.endless_high_score,
                            NumberFormat.getIntegerInstance(resources.configuration.locales[0])
                                .format(highScore),
                        )
                    } else {
                        resources.getString(R.string.endless_seeded_subtitle)
                    },
                    briefing = resources.getString(R.string.endless_briefing),
                    bestStars = null,
                    bestScore = highScore.takeIf { it > 0 },
                    completed = highScore > 0,
                    locked = !introductoryMissionsCompleted,
                    isEndless = true,
                    trainingAvailable = false,
                    pack = pack,
                )
            }
            return authored + endlessModes
        }

        private fun ContentScenarioDefinition.toUiModel(
            resources: Resources,
            airport: com.stuart.atccontroller.data.AirportDefinition,
            id: String,
            number: Int,
            title: String,
            subtitle: String,
            briefing: String,
            bestStars: Int?,
            bestScore: Int?,
            completed: Boolean,
            locked: Boolean,
            trainingAvailable: Boolean,
            isEndless: Boolean = false,
            pack: com.stuart.atccontroller.data.ContentPack,
        ): MissionUiModel {
            val activeRunwayIds = runwayConfiguration.arrivalEndIds +
                runwayConfiguration.departureEndIds
            val runwayLabel = airport.runwayEnds
                .asSequence()
                .map { it.id }
                .filter { it in activeRunwayIds }
                .distinct()
                .joinToString(" / ")
                .ifBlank { resources.getString(R.string.not_available_short) }
            val wind = String.format(
                Locale.UK,
                "%03d°/%02dkt",
                weather.windDirectionDegrees,
                weather.windSpeedKnots,
            )
            return MissionUiModel(
                id = id,
                number = number,
                title = title,
                subtitle = subtitle,
                briefing = briefing,
                trafficCount = traffic.size,
                contentPackId = pack.id,
                campaignName = pack.displayName,
                airportName = airport.displayName,
                packOverview = pack.overview,
                sourceAttribution = airport.source.attribution,
                contentDisclaimer = airport.source.disclaimer,
                bestStars = bestStars,
                bestScore = bestScore,
                completed = completed,
                runwayLabel = runwayLabel,
                wind = wind,
                windShort = String.format(
                    Locale.UK,
                    "%03d/%02d",
                    weather.windDirectionDegrees,
                    weather.windSpeedKnots,
                ),
                weatherSummary = resources.getString(
                    R.string.weather_wind_visibility,
                    wind,
                    weather.visibilityKm,
                ),
                visibilityKm = weather.visibilityKm,
                durationSeconds = maxDurationSeconds,
                objectives = objectives.map { it.description },
                previewPositions = traffic.take(5).mapNotNull { spawn ->
                    val point = spawn.entryFixId
                        ?.let { fixId -> airport.fixes.firstOrNull { it.id == fixId }?.position }
                        ?: airport.runwayEnds
                            .firstOrNull { it.id == spawn.runwayEndId }
                            ?.threshold
                    point?.let { NormalizedPoint(it.x.toFloat(), it.y.toFloat()) }
                },
                locked = locked,
                isEndless = isEndless,
                trainingAvailable = trainingAvailable && !isEndless &&
                    TrainingAcademy.lessonFor(tutorialFocus) != null,
            )
        }

        private fun careerModel(progress: PlayerProgress) = CareerUiState(
            completedShifts = progress.completedMissionCount,
            totalShifts = ContentRegistry.missionIds.size,
            earnedStars = progress.totalStars,
            availableStars = ContentRegistry.missionIds.size * 3,
        )

        private val MISSION_TITLE_RES_IDS = intArrayOf(
            R.string.mission_01_title,
            R.string.mission_02_title,
            R.string.mission_03_title,
            R.string.mission_04_title,
            R.string.mission_05_title,
            R.string.mission_06_title,
            R.string.mission_07_title,
            R.string.mission_08_title,
            R.string.mission_09_title,
            R.string.mission_10_title,
            R.string.mission_11_title,
            R.string.mission_12_title,
        )
        private val MISSION_BRIEFING_RES_IDS = intArrayOf(
            R.string.mission_01_briefing,
            R.string.mission_02_briefing,
            R.string.mission_03_briefing,
            R.string.mission_04_briefing,
            R.string.mission_05_briefing,
            R.string.mission_06_briefing,
            R.string.mission_07_briefing,
            R.string.mission_08_briefing,
            R.string.mission_09_briefing,
            R.string.mission_10_briefing,
            R.string.mission_11_briefing,
            R.string.mission_12_briefing,
        )
    }
}

internal fun ConflictUiModel.canonicalPairKey(): String = canonicalPairKey(
    firstAircraftId,
    secondAircraftId,
)

internal fun conflictsToUiModels(
    conflicts: List<Conflict>,
    callsigns: Map<String, String>,
): List<ConflictUiModel> = conflicts.map { conflict ->
    val firstId = minOf(conflict.firstAircraftId, conflict.secondAircraftId)
    val secondId = maxOf(conflict.firstAircraftId, conflict.secondAircraftId)
    ConflictUiModel(
        firstAircraftId = firstId,
        secondAircraftId = secondId,
        firstAircraftCallsign = callsigns[firstId] ?: firstId,
        secondAircraftCallsign = callsigns[secondId] ?: secondId,
        secondsToConflict = ceil(conflict.timeToClosestApproachSeconds).toInt().coerceAtLeast(0),
        isLossOfSeparation = conflict.kind != ConflictKind.PREDICTED,
    )
}.sortedBy { it.canonicalPairKey() }

internal fun retainedConflictIndex(
    previousPairKey: String?,
    conflicts: List<ConflictUiModel>,
): Int = previousPairKey
    ?.let { pair -> conflicts.indexOfFirst { it.canonicalPairKey() == pair } }
    ?.takeIf { it >= 0 }
    ?: 0

internal fun starForecastFor(score: Int, thresholds: List<Int>): StarForecastUiModel {
    val securedStars = thresholds.count { score >= it }
    return StarForecastUiModel(
        securedStars = securedStars,
        pointsToNextStar = thresholds.getOrNull(securedStars)
            ?.minus(score)
            ?.coerceAtLeast(0),
    )
}

internal fun secondsUntilEntry(spawnAtSeconds: Double, elapsedSeconds: Double): Int =
    ceil(spawnAtSeconds - elapsedSeconds).toInt().coerceAtLeast(0)

internal fun missionClockState(
    status: GameStatus,
    failureReason: FailureReason?,
    elapsedSeconds: Double,
    maxDurationSeconds: Double,
): MissionClockState = when {
    status == GameStatus.FAILED && failureReason == FailureReason.TIME_EXPIRED ->
        MissionClockState.FAILED
    maxDurationSeconds > 0.0 && elapsedSeconds >= maxDurationSeconds ->
        MissionClockState.OVERDUE
    else -> MissionClockState.ACTIVE
}

private fun Conflict.canonicalPairKey(): String = canonicalPairKey(firstAircraftId, secondAircraftId)

private fun canonicalPairKey(firstId: String, secondId: String): String =
    if (firstId <= secondId) "$firstId\u0000$secondId" else "$secondId\u0000$firstId"

private val ConflictKind.severity: Int
    get() = when (this) {
        ConflictKind.PREDICTED -> 1
        ConflictKind.LOSS_OF_SEPARATION -> 2
        ConflictKind.COLLISION -> 3
    }

private val LiveFeedbackKind.priority: Int
    get() = when (this) {
        LiveFeedbackKind.CONFIRMATION -> 1
        LiveFeedbackKind.SUCCESS -> 2
        LiveFeedbackKind.WARNING -> 3
        LiveFeedbackKind.FAILURE -> 4
    }

private val AircraftType.displayCode: String
    get() = when (this) {
        AircraftType.LIGHT -> "LGT"
        AircraftType.REGIONAL -> "E145"
        AircraftType.JET -> "A320"
        AircraftType.HEAVY -> "HEAVY"
    }

private fun AircraftStatus.toUiPhase(): FlightPhase = when (this) {
    AircraftStatus.INBOUND, AircraftStatus.GO_AROUND, AircraftStatus.HOLDING -> FlightPhase.ARRIVAL
    AircraftStatus.APPROACH, AircraftStatus.LANDING -> FlightPhase.APPROACH
    AircraftStatus.HOLDING_SHORT, AircraftStatus.LINED_UP,
    AircraftStatus.CROSSING_RUNWAY, AircraftStatus.TAKEOFF_ROLL,
    AircraftStatus.DEPARTING -> FlightPhase.DEPARTURE
    AircraftStatus.LANDED -> FlightPhase.LANDED
    AircraftStatus.EXITED, AircraftStatus.CRASHED -> FlightPhase.EXITED
}

private fun AircraftState.isUiVisible() = status !in setOf(
    AircraftStatus.LANDED,
    AircraftStatus.EXITED,
    AircraftStatus.CRASHED,
)

private fun AircraftState.matches(target: TrainingTargetOperation?): Boolean = when (target) {
    TrainingTargetOperation.ARRIVAL -> operation == FlightOperation.ARRIVAL
    TrainingTargetOperation.DEPARTURE -> operation == FlightOperation.DEPARTURE
    null -> true
}

private fun TrainingState.matches(
    lesson: com.stuart.atccontroller.data.TrainingLessonDefinition?,
    focus: TutorialFocus,
): Boolean = lesson != null && activeLessonId in setOf(lesson.id, focus.name)

private fun Clearance.toUiText(status: AircraftStatus, resources: Resources): String = when (this) {
    Clearance.None -> when (status) {
        AircraftStatus.INBOUND -> resources.getString(R.string.clearance_radar_contact)
        AircraftStatus.APPROACH -> resources.getString(R.string.clearance_approach)
        AircraftStatus.GO_AROUND -> resources.getString(R.string.clearance_go_around_status)
        AircraftStatus.HOLDING -> resources.getString(R.string.clearance_holding)
        AircraftStatus.HOLDING_SHORT -> resources.getString(R.string.clearance_hold_short)
        AircraftStatus.CROSSING_RUNWAY -> resources.getString(R.string.clearance_crossing_runway)
        AircraftStatus.LINED_UP -> "Lined up and waiting"
        AircraftStatus.TAKEOFF_ROLL -> resources.getString(R.string.clearance_takeoff_roll)
        AircraftStatus.DEPARTING -> resources.getString(R.string.clearance_climb_exit)
        AircraftStatus.LANDING -> resources.getString(R.string.clearance_landing_roll)
        AircraftStatus.LANDED -> resources.getString(R.string.clearance_landed)
        AircraftStatus.EXITED -> resources.getString(R.string.clearance_frequency_changed)
        AircraftStatus.CRASHED -> resources.getString(R.string.clearance_emergency)
    }
    is Clearance.Approach -> resources.getString(R.string.clearance_approach_runway, runwayId)
    is Clearance.LineUpAndWait -> resources.getString(R.string.clearance_line_up_runway, runwayId)
    is Clearance.Land -> resources.getString(R.string.clearance_land_runway, runwayId)
    is Clearance.Takeoff -> resources.getString(R.string.clearance_takeoff_runway, runwayId)
    is Clearance.GoAround -> resources.getString(
        R.string.clearance_go_around_altitude,
        NumberFormat.getIntegerInstance(resources.configuration.locales[0])
            .format(targetAltitudeFeet.roundToInt()),
    )
    is Clearance.Hold -> resources.getString(
        R.string.clearance_hold_fix,
        resources.getString(
            if (turnDirection == HoldTurnDirection.RIGHT) R.string.turn_right else R.string.turn_left,
        ),
        legSeconds.roundToInt(),
    )
    is Clearance.Exit -> resources.getString(R.string.clearance_exit)
    is Clearance.CrossRunway -> resources.getString(R.string.clearance_cross_runway, runwayId)
}

private fun Vec2.toUiPoint() = NormalizedPoint(x.toFloat(), y.toFloat())

private fun Vec2.isValidNormalizedWaypoint(): Boolean =
    x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0

private fun PlayerSettings.toUiState() = SettingsUiState(
    musicVolume = musicVolume,
    effectsVolume = effectsVolume,
    hapticsEnabled = hapticsEnabled,
    trailsEnabled = trailsEnabled,
    reducedMotion = reducedMotion,
    highContrast = highContrast,
    labelScale = labelScale,
    labelDeclutteringEnabled = labelDeclutteringEnabled,
    routeSnappingEnabled = routeSnappingEnabled,
    pauseOnFocusLoss = pauseOnFocusLoss,
)

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

private fun customShiftUi(configuration: ShiftConfiguration): CustomShiftUiModel {
    val pack = checkNotNull(ContentRegistry.pack(configuration.contentPackId))
    val traffic = configuration.density.trafficLabel
    val arrivals = (traffic * configuration.arrivalPercent / 100.0).roundToInt()
    return CustomShiftUiModel(
        contentPackId = pack.id,
        contentPackName = pack.displayName,
        airportName = pack.airport.displayName,
        runwayEndLabel = pack.runwayEnds(configuration.runwayDirection).joinToString(" / "),
        seed = configuration.seed.toString(),
        density = configuration.density.name,
        arrivalPercent = configuration.arrivalPercent,
        runwayDirection = configuration.runwayDirection.name,
        weatherPreset = configuration.weatherPreset.name,
        fuelPressure = configuration.fuelPressure.name,
        strikeLimit = configuration.strikeLimit,
        routeSnapping = configuration.assists.routeSnapping,
        approachSetup = configuration.assists.approachSetup,
        conflictPrediction = configuration.assists.conflictPrediction,
        previewTraffic = traffic,
        previewArrivals = arrivals,
        previewDepartures = traffic - arrivals,
        configurationIdentity = ShiftConfigurationCodec.encode(configuration).takeLast(20),
        ranked = configuration.isRanked,
    )
}

private fun <T> List<T>.cycle(current: T, offset: Int): T {
    if (isEmpty() || offset == 0) return current
    val index = indexOf(current).coerceAtLeast(0)
    return this[Math.floorMod(index + offset, size)]
}

private fun GameSnapshot.toResult(
    title: String,
    stars: Int,
    personalBest: Boolean,
    displayedScore: Int = score.total,
    resources: Resources,
    fixNamesByPosition: Map<Vec2, String>,
    isPractice: Boolean = false,
    configurationIdentity: String? = null,
    isDaily: Boolean = false,
): MissionResultUiModel {
    val callsigns = aircraft.associate { it.id to it.callsign }
    val timeline = eventHistory.mapIndexed { index, event ->
        EventFeedEntryUiModel(
            sequence = eventHistoryStartSequence + index,
            elapsedSeconds = event.elapsedSeconds.toInt(),
            caption = eventCaption(event, callsigns, resources, fixNamesByPosition),
            aircraftIds = event.aircraftIdsForPresentation(),
            severity = event.toFeedSeverity(),
            rejectionCode = (event as? GameEvent.CommandRejected)?.reasonCode?.name,
        )
    }
    return MissionResultUiModel(
    title = title,
    stars = stars,
    score = displayedScore,
    safeArrivals = score.safeArrivals,
    safeDepartures = score.safeDepartures,
    efficiencyBonus = score.efficiencyPoints + score.timeBonusPoints,
    separationPenalty = score.penalties,
    personalBest = personalBest,
    successful = status == GameStatus.COMPLETED,
    scoreRows = buildList {
        fun row(id: String, points: Int) = add(
            ScoreRowUiModel(id, localizedScoreRowLabel(id, resources), points),
        )
        row("movements", score.basePoints)
        row("route", score.efficiencyPoints)
        row("time", score.timeBonusPoints)
        row("completion", score.completionBonusPoints)
        row("go_around", -score.goAroundPenaltyPoints)
        row("missed_exit", -score.missedExitPenaltyPoints)
        row("separation", -score.separationPenaltyPoints)
        row("runway", -score.runwayProcedurePenaltyPoints)
        row("wake", -score.wakePenaltyPoints)
        row("procedure", -score.proceduralPenaltyPoints)
        row("dynamic_bonus", score.dynamicEventBonusPoints)
        row("dynamic_penalty", -score.dynamicEventPenaltyPoints)
        val categorized = score.goAroundPenaltyPoints + score.missedExitPenaltyPoints +
            score.separationPenaltyPoints + score.runwayProcedurePenaltyPoints +
            score.wakePenaltyPoints + score.proceduralPenaltyPoints + score.dynamicEventPenaltyPoints
        val otherPenalty = (score.penalties - categorized).coerceAtLeast(0)
        if (otherPenalty > 0) row("other", -otherPenalty)
        val represented = sumOf(ScoreRowUiModel::points)
        if (displayedScore != represented) {
            row("carried", displayedScore - represented)
        }
    },
    pointsToNextStar = objectives.starScoreThresholds.firstOrNull { displayedScore < it }
        ?.minus(displayedScore),
    flights = flightPerformances.map { performance ->
        FlightDebriefUiModel(
            aircraftId = performance.aircraftId,
            callsign = performance.callsign,
            operation = resources.getString(
                if (performance.operation == FlightOperation.ARRIVAL) {
                    R.string.phase_arrival
                } else {
                    R.string.phase_departure
                },
            ),
            outcome = resources.getString(when (performance.outcome) {
                FlightOutcome.ACTIVE -> R.string.flight_outcome_active
                FlightOutcome.LANDED -> R.string.flight_outcome_landed
                FlightOutcome.CORRECT_EXIT -> R.string.flight_outcome_correct_exit
                FlightOutcome.WRONG_EXIT -> R.string.flight_outcome_wrong_exit
                FlightOutcome.LOST -> R.string.flight_outcome_lost
                FlightOutcome.CRASHED -> R.string.flight_outcome_crashed
            }),
            handlingSeconds = performance.handlingSeconds.roundToInt(),
            distanceTenthsNm = (performance.distanceTravelledNm * 10.0).roundToInt(),
            routeEfficiencyPoints = performance.efficiencyPoints,
            timeBonusPoints = performance.timeBonusPoints,
            associatedPenaltyPoints = performance.associatedPenaltyPoints,
            eventSequences = timeline.filter { performance.aircraftId in it.aircraftIds }
                .map(EventFeedEntryUiModel::sequence),
            routeHeatmap = routeHistory[performance.aircraftId].orEmpty().map(Vec2::toUiPoint),
            holdSeconds = performance.holdSeconds.roundToInt(),
        )
    },
    timeline = timeline,
    isPractice = isPractice,
    configurationIdentity = configurationIdentity,
    isDaily = isDaily,
    )
}

private fun localizedScoreRowLabel(id: String, resources: Resources): String = resources.getString(
    when (id) {
        "movements" -> R.string.score_safe_movements
        "route" -> R.string.score_route_efficiency
        "time" -> R.string.score_time
        "completion" -> R.string.score_completion
        "go_around" -> R.string.score_go_arounds
        "missed_exit" -> R.string.score_missed_exits
        "separation" -> R.string.score_separation
        "runway" -> R.string.score_runway_procedure
        "wake" -> R.string.score_wake_spacing
        "procedure" -> R.string.score_procedural_control
        "dynamic_bonus" -> R.string.score_dynamic_bonus
        "dynamic_penalty" -> R.string.score_dynamic_penalty
        "other" -> R.string.score_other_penalties
        "carried" -> R.string.score_carried
        else -> R.string.score_other
    },
)

private fun GameSnapshot.terminalHash(): String {
    val canonical = buildString {
        append(scenarioId).append('|').append(tick).append('|').append(status.name)
        append('|').append(score.total).append('|').append(strikes)
        aircraft.sortedBy { it.id }.forEach { state ->
            append('|').append(state.id).append(':').append(state.status.name)
            append(':').append(state.position.x).append(',').append(state.position.y)
            append(':').append(state.altitudeFeet).append(':').append(state.speedKnots)
        }
        runways.sortedBy { it.id }.forEach { runway ->
            append('|').append(runway.id).append(':').append(runway.occupiedByAircraftId.orEmpty())
        }
        append("|events@").append(eventHistoryStartSequence)
        eventHistory.forEach { event -> append('|').append(event.toString()) }
        flightPerformances.forEach { flight -> append('|').append(flight.toString()) }
        routeHistory.toSortedMap().forEach { (aircraftId, points) ->
            append("|route:").append(aircraftId)
            points.forEach { point -> append(':').append(point.x).append(',').append(point.y) }
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT, it) }
}

private fun FailureReason?.toDisplayText(resources: Resources): String = resources.getString(when (this) {
    FailureReason.TOO_MANY_STRIKES -> R.string.failure_too_many_strikes
    FailureReason.COLLISION -> R.string.failure_collision
    FailureReason.RUNWAY_INCURSION -> R.string.failure_runway_incursion
    FailureReason.AIRCRAFT_LOST -> R.string.failure_aircraft_lost
    FailureReason.FUEL_EXHAUSTED -> R.string.failure_fuel_exhausted
    FailureReason.TIME_EXPIRED -> R.string.failure_time_expired
    null -> R.string.failure_unknown
})

private fun GameStatus.isActive() = this == GameStatus.RUNNING || this == GameStatus.PAUSED
private fun GameStatus.isTerminal() = this == GameStatus.COMPLETED || this == GameStatus.FAILED

private fun PlayerCommand.isReplayable() = when (this) {
    is PlayerCommand.AppendWaypoint,
    is PlayerCommand.AssignApproach,
    is PlayerCommand.AssignRunway,
    is PlayerCommand.CancelApproach,
    is PlayerCommand.CancelLandingClearance,
    is PlayerCommand.CancelTakeoffClearance,
    is PlayerCommand.AssignHold,
    is PlayerCommand.CancelHold,
    is PlayerCommand.IssueExitClearance,
    is PlayerCommand.AcknowledgeInboundHandoff,
    is PlayerCommand.InitiateOutboundHandoff,
    is PlayerCommand.AcknowledgeDynamicEvent,
    is PlayerCommand.CrossRunway,
    is PlayerCommand.ClearRoute,
    is PlayerCommand.ClearForTakeoff,
    is PlayerCommand.ClearToLand,
    is PlayerCommand.DirectTo,
    is PlayerCommand.GoAround,
    is PlayerCommand.LineUpAndWait,
    is PlayerCommand.SetRoute,
    is PlayerCommand.SetTargetAltitude,
    is PlayerCommand.SetTargetSpeed,
    is PlayerCommand.UndoWaypoint -> true
    PlayerCommand.Pause,
    PlayerCommand.Resume,
    PlayerCommand.Start,
    is PlayerCommand.SetSimulationSpeed -> false
}

private fun PlayerCommand.aircraftId(): String = when (this) {
    is PlayerCommand.AppendWaypoint -> aircraftId
    is PlayerCommand.AssignApproach -> aircraftId
    is PlayerCommand.AssignRunway -> aircraftId
    is PlayerCommand.CancelApproach -> aircraftId
    is PlayerCommand.CancelLandingClearance -> aircraftId
    is PlayerCommand.CancelTakeoffClearance -> aircraftId
    is PlayerCommand.AssignHold -> aircraftId
    is PlayerCommand.CancelHold -> aircraftId
    is PlayerCommand.IssueExitClearance -> aircraftId
    is PlayerCommand.AcknowledgeInboundHandoff -> aircraftId
    is PlayerCommand.InitiateOutboundHandoff -> aircraftId
    is PlayerCommand.AcknowledgeDynamicEvent -> error("Command does not target an aircraft")
    is PlayerCommand.CrossRunway -> aircraftId
    is PlayerCommand.ClearRoute -> aircraftId
    is PlayerCommand.ClearForTakeoff -> aircraftId
    is PlayerCommand.ClearToLand -> aircraftId
    is PlayerCommand.DirectTo -> aircraftId
    is PlayerCommand.GoAround -> aircraftId
    is PlayerCommand.LineUpAndWait -> aircraftId
    is PlayerCommand.SetRoute -> aircraftId
    is PlayerCommand.SetTargetAltitude -> aircraftId
    is PlayerCommand.SetTargetSpeed -> aircraftId
    is PlayerCommand.UndoWaypoint -> aircraftId
    PlayerCommand.Pause,
    PlayerCommand.Resume,
    PlayerCommand.Start,
    is PlayerCommand.SetSimulationSpeed -> error("Command does not target an aircraft")
}

private fun GameEvent.feedbackKind(): LiveFeedbackKind? = when (this) {
    is GameEvent.Collision, is GameEvent.RunwayIncursion, is GameEvent.ScenarioFailed,
    is GameEvent.WakeViolation -> LiveFeedbackKind.FAILURE
    is GameEvent.CommandRejected,
    is GameEvent.ConflictWarning,
    is GameEvent.SeparationLost,
    is GameEvent.StrikeIssued, is GameEvent.WakeWarning -> LiveFeedbackKind.WARNING
    is GameEvent.AircraftExited,
    is GameEvent.Landed,
    is GameEvent.ScenarioCompleted,
    is GameEvent.Takeoff,
    is GameEvent.Touchdown -> LiveFeedbackKind.SUCCESS
    is GameEvent.ClearanceIssued, is GameEvent.RouteUpdated, is GameEvent.RunwayAssigned,
    is GameEvent.ApproachAssigned, is GameEvent.ClearanceCancelled,
    is GameEvent.HoldAssigned, is GameEvent.HoldEstablished, is GameEvent.HoldCancelled,
    is GameEvent.ExitClearanceIssued -> LiveFeedbackKind.CONFIRMATION
    is GameEvent.HandoffChanged -> when (status) {
        HandoffStatus.TIMED_OUT -> LiveFeedbackKind.WARNING
        HandoffStatus.COMPLETED -> LiveFeedbackKind.SUCCESS
        else -> LiveFeedbackKind.CONFIRMATION
    }
    is GameEvent.DynamicEventChanged -> when (lifecycle) {
        DynamicEventLifecycle.FAILED -> LiveFeedbackKind.FAILURE
        DynamicEventLifecycle.WARNING, DynamicEventLifecycle.ACTIVE -> LiveFeedbackKind.WARNING
        DynamicEventLifecycle.RESOLVED -> LiveFeedbackKind.SUCCESS
        else -> LiveFeedbackKind.CONFIRMATION
    }
    is GameEvent.RunwayCrossingStarted -> LiveFeedbackKind.CONFIRMATION
    is GameEvent.RunwayCrossingCompleted -> LiveFeedbackKind.SUCCESS
    is GameEvent.WeatherChangeWarning -> LiveFeedbackKind.WARNING
    is GameEvent.WeatherChanged -> LiveFeedbackKind.CONFIRMATION
    is GameEvent.AircraftLeftAirspace,
    is GameEvent.AircraftSpawned,
    is GameEvent.GoAround, is GameEvent.RunwayOccupied, is GameEvent.RunwayVacated -> null
}

private fun GameEvent.aircraftIdsForPresentation(): List<String> = when (this) {
    is GameEvent.AircraftSpawned -> listOf(aircraftId)
    is GameEvent.RouteUpdated -> listOf(aircraftId)
    is GameEvent.ClearanceIssued -> listOf(aircraftId)
    is GameEvent.CommandRejected -> listOfNotNull(aircraftId)
    is GameEvent.ConflictWarning -> listOf(conflict.firstAircraftId, conflict.secondAircraftId)
    is GameEvent.SeparationLost -> listOf(conflict.firstAircraftId, conflict.secondAircraftId)
    is GameEvent.StrikeIssued -> aircraftIds.sorted()
    is GameEvent.Collision -> listOf(conflict.firstAircraftId, conflict.secondAircraftId)
    is GameEvent.GoAround -> listOf(aircraftId)
    is GameEvent.Touchdown -> listOf(aircraftId)
    is GameEvent.Landed -> listOf(aircraftId)
    is GameEvent.Takeoff -> listOf(aircraftId)
    is GameEvent.AircraftExited -> listOf(aircraftId)
    is GameEvent.AircraftLeftAirspace -> listOf(aircraftId)
    is GameEvent.RunwayIncursion -> aircraftIds.sorted()
    is GameEvent.RunwayAssigned -> listOf(aircraftId)
    is GameEvent.ApproachAssigned -> listOf(aircraftId)
    is GameEvent.ClearanceCancelled -> listOf(aircraftId)
    is GameEvent.RunwayOccupied -> listOf(aircraftId)
    is GameEvent.RunwayVacated -> listOf(aircraftId)
    is GameEvent.WakeWarning -> listOf(leaderAircraftId, followerAircraftId)
    is GameEvent.WakeViolation -> listOf(leaderAircraftId, followerAircraftId)
    is GameEvent.HoldAssigned -> listOf(aircraftId)
    is GameEvent.HoldEstablished -> listOf(aircraftId)
    is GameEvent.HoldCancelled -> listOf(aircraftId)
    is GameEvent.ExitClearanceIssued -> listOf(aircraftId)
    is GameEvent.HandoffChanged -> listOf(aircraftId)
    is GameEvent.DynamicEventChanged -> listOfNotNull(aircraftId)
    is GameEvent.RunwayCrossingStarted -> listOf(aircraftId)
    is GameEvent.RunwayCrossingCompleted -> listOf(aircraftId)
    is GameEvent.WeatherChangeWarning, is GameEvent.WeatherChanged -> emptyList()
    is GameEvent.ScenarioCompleted, is GameEvent.ScenarioFailed -> emptyList()
}

private fun GameEvent.toFeedSeverity(): EventFeedSeverity = when (this) {
    is GameEvent.Collision, is GameEvent.RunwayIncursion, is GameEvent.ScenarioFailed,
    is GameEvent.WakeViolation -> EventFeedSeverity.CRITICAL
    is GameEvent.CommandRejected, is GameEvent.ConflictWarning, is GameEvent.SeparationLost,
    is GameEvent.StrikeIssued, is GameEvent.AircraftLeftAirspace,
    is GameEvent.WakeWarning -> EventFeedSeverity.WARNING
    is GameEvent.HandoffChanged -> if (status == HandoffStatus.TIMED_OUT) {
        EventFeedSeverity.WARNING
    } else if (status == HandoffStatus.COMPLETED) {
        EventFeedSeverity.SUCCESS
    } else {
        EventFeedSeverity.ROUTINE
    }
    is GameEvent.DynamicEventChanged -> when (lifecycle) {
        DynamicEventLifecycle.FAILED -> EventFeedSeverity.CRITICAL
        DynamicEventLifecycle.WARNING, DynamicEventLifecycle.ACTIVE -> EventFeedSeverity.WARNING
        DynamicEventLifecycle.RESOLVED -> EventFeedSeverity.SUCCESS
        else -> EventFeedSeverity.ROUTINE
    }
    is GameEvent.Landed, is GameEvent.Takeoff, is GameEvent.AircraftExited,
    is GameEvent.ScenarioCompleted -> EventFeedSeverity.SUCCESS
    else -> EventFeedSeverity.ROUTINE
}

internal fun eventCaption(
    event: GameEvent,
    callsigns: Map<String, String>,
    resources: Resources,
    fixNamesByPosition: Map<Vec2, String> = emptyMap(),
): String = with(event) {
    fun call(id: String) = callsigns[id] ?: id
    when (this) {
        is GameEvent.AircraftSpawned -> resources.getString(
            R.string.event_aircraft_spawned,
            call(aircraftId),
        )
        is GameEvent.RouteUpdated -> resources.getString(
            R.string.event_route_updated,
            call(aircraftId),
        )
        is GameEvent.ClearanceIssued -> resources.getString(
            R.string.event_clearance_issued,
            call(aircraftId),
            clearance.toEventText(resources),
        )
        is GameEvent.CommandRejected -> aircraftId?.let { id ->
            resources.getString(
                R.string.event_command_rejected,
                call(id),
                reasonCode.toDisplayText(resources),
            )
        } ?: resources.getString(
            R.string.event_command_rejected_general,
            reasonCode.toDisplayText(resources),
        )
        is GameEvent.ConflictWarning -> resources.getString(
            R.string.event_conflict_predicted,
            call(conflict.firstAircraftId),
            call(conflict.secondAircraftId),
        )
        is GameEvent.SeparationLost -> resources.getString(
            R.string.event_separation_lost,
            call(conflict.firstAircraftId),
            call(conflict.secondAircraftId),
        )
        is GameEvent.StrikeIssued -> resources.getString(
            R.string.event_separation_strike,
            aircraftIds.sorted().joinToString(" / ") { call(it) },
        )
        is GameEvent.Collision -> resources.getString(
            R.string.event_collision,
            call(conflict.firstAircraftId),
            call(conflict.secondAircraftId),
        )
        is GameEvent.GoAround -> resources.getString(
            if (automatic) R.string.event_go_around_automatic else R.string.event_go_around,
            call(aircraftId),
        )
        is GameEvent.Touchdown -> resources.getString(
            R.string.event_touchdown,
            call(aircraftId),
            runwayId,
        )
        is GameEvent.Landed -> resources.getString(
            R.string.event_landed,
            call(aircraftId),
            runwayId,
        )
        is GameEvent.Takeoff -> resources.getString(
            R.string.event_takeoff,
            call(aircraftId),
            runwayId,
        )
        is GameEvent.AircraftExited -> {
            val fixName = assignedExitPoint?.let(fixNamesByPosition::get)
                ?: resources.getString(R.string.event_exit_fix_unknown)
            resources.getString(
                if (correctExit) R.string.event_aircraft_exited else R.string.event_aircraft_wrong_exit,
                call(aircraftId),
                fixName,
            )
        }
        is GameEvent.AircraftLeftAirspace -> resources.getString(
            R.string.event_aircraft_left_airspace,
            call(aircraftId),
        )
        is GameEvent.RunwayIncursion -> resources.getString(
            R.string.event_runway_incursion,
            runwayId,
            aircraftIds.sorted().joinToString(" / ") { call(it) },
        )
        is GameEvent.RunwayAssigned -> resources.getString(
            R.string.event_runway_assigned,
            call(aircraftId),
            runwayId,
        )
        is GameEvent.ApproachAssigned -> runwayId?.let { assignedRunway ->
            resources.getString(
                R.string.event_approach_assigned,
                call(aircraftId),
                assignedRunway,
            )
        } ?: resources.getString(R.string.event_approach_cancelled, call(aircraftId))
        is GameEvent.ClearanceCancelled -> resources.getString(
            R.string.event_clearance_cancelled,
            call(aircraftId),
            clearanceType.toClearanceTypeText(resources),
        )
        is GameEvent.RunwayOccupied -> resources.getString(
            R.string.event_runway_occupied,
            runwayEndId,
            call(aircraftId),
        )
        is GameEvent.RunwayVacated -> resources.getString(
            R.string.event_runway_vacated,
            runwayEndId,
            call(aircraftId),
        )
        is GameEvent.WakeWarning -> resources.getString(
            R.string.event_wake_warning,
            call(leaderAircraftId),
            call(followerAircraftId),
            actualSeconds.roundToInt(),
            requiredSeconds.roundToInt(),
        )
        is GameEvent.WakeViolation -> resources.getString(
            R.string.event_wake_violation,
            call(leaderAircraftId),
            call(followerAircraftId),
        )
        is GameEvent.HoldAssigned -> resources.getString(
            R.string.event_hold_assigned,
            call(aircraftId),
            fixNamesByPosition[fix] ?: resources.getString(R.string.event_exit_fix_unknown),
            resources.getString(
                if (turnDirection == HoldTurnDirection.RIGHT) R.string.turn_right else R.string.turn_left,
            ),
            altitudeFeet.roundToInt(),
        )
        is GameEvent.HoldEstablished -> resources.getString(
            R.string.event_hold_established,
            call(aircraftId),
            fixNamesByPosition[fix] ?: resources.getString(R.string.event_exit_fix_unknown),
        )
        is GameEvent.HoldCancelled -> resources.getString(
            R.string.event_hold_cancelled,
            call(aircraftId),
            totalHoldSeconds.roundToInt(),
        )
        is GameEvent.ExitClearanceIssued -> resources.getString(
            R.string.event_exit_clearance,
            call(aircraftId),
            fixNamesByPosition[exitPoint] ?: resources.getString(R.string.event_exit_fix_unknown),
        )
        is GameEvent.HandoffChanged -> resources.getString(
            R.string.event_handoff_changed,
            call(aircraftId),
            sectorId,
            status.name.lowercase().replace('_', ' '),
        )
        is GameEvent.DynamicEventChanged -> resources.getString(
            R.string.event_dynamic_changed,
            resources.getString(when (type) {
                DynamicEventType.LOW_FUEL_PRIORITY -> R.string.dynamic_low_fuel
                DynamicEventType.REJECTED_TAKEOFF -> R.string.dynamic_rejected_takeoff
                DynamicEventType.RUNWAY_CLOSURE -> R.string.dynamic_runway_closure
                DynamicEventType.EQUIPMENT_OUTAGE -> R.string.dynamic_equipment_outage
                DynamicEventType.PRIORITY_FLIGHT -> R.string.dynamic_priority_flight
            }),
            lifecycle.name.lowercase().replace('_', ' '),
            aircraftId?.let { call(it) } ?: runwayId ?: resources.getString(R.string.radar_system),
        )
        is GameEvent.RunwayCrossingStarted -> resources.getString(
            R.string.event_crossing_started,
            call(aircraftId),
            runwayId,
        )
        is GameEvent.RunwayCrossingCompleted -> resources.getString(
            R.string.event_crossing_completed,
            call(aircraftId),
            runwayId,
        )
        is GameEvent.WeatherChangeWarning -> resources.getString(
            R.string.event_weather_warning,
            activeRunwayIds.sorted().joinToString(" / "),
            (effectiveSeconds - elapsedSeconds).roundToInt().coerceAtLeast(0),
            weather.windDirectionDegrees.roundToInt(),
            weather.windSpeedKnots.roundToInt(),
        )
        is GameEvent.WeatherChanged -> resources.getString(
            R.string.event_weather_changed,
            activeRunwayIds.sorted().joinToString(" / "),
            weather.windDirectionDegrees.roundToInt(),
            weather.windSpeedKnots.roundToInt(),
            weather.visibilityKm.roundToInt(),
        )
        is GameEvent.ScenarioCompleted -> resources.getString(R.string.event_scenario_completed)
        is GameEvent.ScenarioFailed -> resources.getString(
            R.string.event_scenario_failed,
            reason.toDisplayText(resources),
        )
    }
}

private fun Clearance.toEventText(resources: Resources): String = when (this) {
    Clearance.None -> resources.getString(R.string.event_clearance_none)
    is Clearance.Approach -> resources.getString(R.string.event_clearance_approach, runwayId)
    is Clearance.LineUpAndWait -> resources.getString(R.string.event_clearance_line_up, runwayId)
    is Clearance.Land -> resources.getString(R.string.event_clearance_land, runwayId)
    is Clearance.Takeoff -> resources.getString(R.string.event_clearance_takeoff, runwayId)
    is Clearance.GoAround -> resources.getString(
        R.string.event_clearance_go_around,
        NumberFormat.getIntegerInstance(resources.configuration.locales[0])
            .format(targetAltitudeFeet.roundToInt()),
    )
    is Clearance.Hold -> resources.getString(
        R.string.event_clearance_hold,
        legSeconds.roundToInt(),
    )
    is Clearance.Exit -> resources.getString(R.string.event_clearance_exit)
    is Clearance.CrossRunway -> resources.getString(R.string.event_clearance_cross, runwayId)
}

private fun CancelledClearanceType.toClearanceTypeText(resources: Resources): String = resources.getString(when (this) {
    CancelledClearanceType.APPROACH -> R.string.event_clearance_type_approach
    CancelledClearanceType.LANDING -> R.string.event_clearance_type_landing
    CancelledClearanceType.TAKEOFF -> R.string.event_clearance_type_takeoff
})

private fun CommandRejectionReason.toDisplayText(resources: Resources): String =
    resources.getString(when (this) {
        CommandRejectionReason.SIMULATION_NOT_RUNNING -> R.string.rejection_simulation_not_running
        CommandRejectionReason.SIMULATION_NOT_PAUSED -> R.string.rejection_simulation_not_paused
        CommandRejectionReason.SIMULATION_ALREADY_STARTED -> R.string.rejection_simulation_started
        CommandRejectionReason.SIMULATION_NOT_ACTIVE -> R.string.rejection_simulation_not_active
        CommandRejectionReason.MECHANIC_NOT_ENABLED -> R.string.rejection_mechanic_not_enabled
        CommandRejectionReason.AIRCRAFT_NOT_ACTIVE -> R.string.rejection_aircraft_not_active
        CommandRejectionReason.AIRCRAFT_STATE_INELIGIBLE -> R.string.rejection_aircraft_ineligible
        CommandRejectionReason.INVALID_ALTITUDE -> R.string.rejection_invalid_altitude
        CommandRejectionReason.INVALID_SPEED -> R.string.rejection_invalid_speed
        CommandRejectionReason.INVALID_ROUTE -> R.string.rejection_invalid_route
        CommandRejectionReason.UNKNOWN_RUNWAY -> R.string.rejection_unknown_runway
        CommandRejectionReason.RUNWAY_INACTIVE -> R.string.rejection_runway_inactive
        CommandRejectionReason.RUNWAY_OCCUPIED -> R.string.rejection_runway_occupied
        CommandRejectionReason.RUNWAY_RECIPROCAL_CONFLICT -> R.string.rejection_runway_reciprocal
        CommandRejectionReason.RUNWAY_CLASS_INCOMPATIBLE -> R.string.rejection_runway_class
        CommandRejectionReason.RUNWAY_WIND_UNSUITABLE -> R.string.rejection_runway_wind
        CommandRejectionReason.RUNWAY_ASSIGNMENT_MISMATCH -> R.string.rejection_runway_assignment
        CommandRejectionReason.ARRIVAL_REQUIRED -> R.string.rejection_arrival_required
        CommandRejectionReason.DEPARTURE_REQUIRED -> R.string.rejection_departure_required
        CommandRejectionReason.APPROACH_NOT_ASSIGNED -> R.string.rejection_approach_not_assigned
        CommandRejectionReason.CLEARANCE_NOT_ACTIVE -> R.string.rejection_clearance_not_active
        CommandRejectionReason.WAKE_SPACING_INSUFFICIENT -> R.string.rejection_wake_spacing
        CommandRejectionReason.INVALID_GO_AROUND_ALTITUDE -> R.string.rejection_go_around_altitude
        CommandRejectionReason.HOLD_ALREADY_ACTIVE -> R.string.rejection_hold_active
        CommandRejectionReason.HOLD_NOT_ACTIVE -> R.string.rejection_hold_not_active
        CommandRejectionReason.INVALID_HOLD -> R.string.rejection_invalid_hold
        CommandRejectionReason.EXIT_NOT_AVAILABLE -> R.string.rejection_exit_unavailable
        CommandRejectionReason.EXIT_CLEARANCE_REQUIRED -> R.string.rejection_exit_required
        CommandRejectionReason.HANDOFF_NOT_ELIGIBLE -> R.string.rejection_handoff_ineligible
        CommandRejectionReason.HANDOFF_ALREADY_ACTIVE -> R.string.rejection_handoff_active
        CommandRejectionReason.CROSSING_NOT_ELIGIBLE -> R.string.rejection_crossing_ineligible
        CommandRejectionReason.UNKNOWN_DYNAMIC_EVENT -> R.string.rejection_dynamic_unknown
        CommandRejectionReason.DYNAMIC_EVENT_NOT_ACTIVE -> R.string.rejection_dynamic_inactive
        CommandRejectionReason.UNKNOWN -> R.string.rejection_unknown
    })

private fun TutorialFocus.subtitle(resources: Resources): String = resources.getString(when (this) {
    TutorialFocus.SELECTION_AND_ROUTING -> R.string.focus_selection_routing
    TutorialFocus.ALTITUDE -> R.string.focus_altitude
    TutorialFocus.SPEED -> R.string.focus_speed
    TutorialFocus.LANDING_AND_GO_AROUND -> R.string.focus_landing_go_around
    TutorialFocus.DEPARTURES -> R.string.focus_departures
    TutorialFocus.RUNWAY_OCCUPANCY -> R.string.focus_runway_occupancy
    TutorialFocus.MIXED_TRAFFIC -> R.string.focus_mixed_traffic
    TutorialFocus.PARALLEL_RUNWAYS -> R.string.focus_parallel_runways
    TutorialFocus.WAKE_TURBULENCE -> R.string.focus_wake_turbulence
    TutorialFocus.WEATHER_OPERATIONS -> R.string.focus_weather_operations
    TutorialFocus.PROCEDURAL_CONTROL -> R.string.focus_procedural_control
    TutorialFocus.DYNAMIC_EVENTS -> R.string.focus_dynamic_events
    TutorialFocus.NONE -> R.string.focus_full_sector
})

private fun ContentScenarioDefinition.toTrainingScenario(): ContentScenarioDefinition {
    val runwayLesson = tutorialFocus in setOf(
        TutorialFocus.DEPARTURES,
        TutorialFocus.RUNWAY_OCCUPANCY,
        TutorialFocus.PARALLEL_RUNWAYS,
    )
    return copy(
        id = "${id}_academy_v1",
        title = "$title Academy",
        traffic = traffic.mapIndexed { index, spawn ->
            if (index == 0) spawn.copy(spawnAtSeconds = 0) else spawn
        },
        mechanicVersions = mechanicVersions.copy(
            runwayProcedures = if (runwayLesson) 1 else mechanicVersions.runwayProcedures,
        ),
    )
}
