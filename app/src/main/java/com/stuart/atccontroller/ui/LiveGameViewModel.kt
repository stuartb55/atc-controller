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
import com.stuart.atccontroller.data.EndlessScenarioGenerator
import com.stuart.atccontroller.data.FixUse
import com.stuart.atccontroller.data.ManchesterContent
import com.stuart.atccontroller.data.PlayerData
import com.stuart.atccontroller.data.PlayerPreferencesRepository
import com.stuart.atccontroller.data.PlayerProgress
import com.stuart.atccontroller.data.PlayerSettings
import com.stuart.atccontroller.data.ScenarioDefinition as ContentScenarioDefinition
import com.stuart.atccontroller.data.TrafficSpawnDefinition
import com.stuart.atccontroller.data.TutorialFocus
import com.stuart.atccontroller.data.atcControllerDataStore
import com.stuart.atccontroller.data.toSimulationScenario
import com.stuart.atccontroller.simulation.AircraftState
import com.stuart.atccontroller.simulation.AircraftStatus
import com.stuart.atccontroller.simulation.AircraftType
import com.stuart.atccontroller.simulation.AtcSimulationEngine
import com.stuart.atccontroller.simulation.Clearance
import com.stuart.atccontroller.simulation.Conflict
import com.stuart.atccontroller.simulation.ConflictKind
import com.stuart.atccontroller.simulation.FailureReason
import com.stuart.atccontroller.simulation.GameEvent
import com.stuart.atccontroller.simulation.GameSnapshot
import com.stuart.atccontroller.simulation.GameStatus
import com.stuart.atccontroller.simulation.PlayerCommand
import com.stuart.atccontroller.simulation.Route
import com.stuart.atccontroller.simulation.Vec2
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
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
    suspend fun recordEndlessHighScore(score: Int)
    suspend fun saveActiveSession(session: ActiveSessionRecord)
    suspend fun clearActiveSession()
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

    override suspend fun recordEndlessHighScore(score: Int) =
        repository.recordEndlessHighScore(score)

    override suspend fun saveActiveSession(session: ActiveSessionRecord) =
        repository.saveActiveSession(session)

    override suspend fun clearActiveSession() = repository.clearActiveSession()
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

    private var playerData = PlayerData()
    private var engine: AtcSimulationEngine? = null
    private var activeDescriptor: SessionDescriptor? = null
    private var activeContent: ContentScenarioDefinition? = null
    private var lastSnapshot: GameSnapshot? = null
    private var suppressPersistedSession = false
    private var pausedForTutorial = false
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
    private val trails = mutableMapOf<String, MutableList<NormalizedPoint>>()
    private val replayLog = mutableListOf<ReplayEntry>()

    var uiState by mutableStateOf(
        initialUiState(resources).copy(
            screen = when {
                restoredScreen == AppScreen.RESULTS && restoredResult == null -> AppScreen.HOME
                else -> restoredScreen
            },
            selectedMissionId = savedStateHandle.get<String>(STATE_SELECTED_MISSION)
                ?: ManchesterContent.FIRST_MISSION_ID,
            result = restoredResult,
            tutorialStep = restoredTutorialState?.takeIf { it in 0..LAST_TUTORIAL_STEP },
            isRestoring = restorePersistedSessionOnLoad,
        ),
    )
        private set

    var feedbackCue by mutableStateOf<LiveFeedbackCue?>(null)
        private set

    init {
        viewModelScope.launch {
            preferences.playerData.collectLatest(::applyPlayerData)
        }
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                val runningEngine = engine ?: continue
                if (uiState.screen != AppScreen.GAME) continue
                if (runningEngine.snapshot.status == GameStatus.RUNNING) {
                    publish(runningEngine.advance(TICK_SECONDS))
                }
            }
        }
    }

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.Navigate -> navigate(action.screen)
            is GameAction.SelectMission -> selectMission(action.id)
            GameAction.StartSelectedMission -> startSelectedMission()
            GameAction.ContinueLastGame -> continueLastGame()
            is GameAction.SelectAircraft -> selectAircraft(action.id)
            is GameAction.CommitRoute -> submitRoute(action.points)
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
            is GameAction.IssueClearance -> issueClearance(action.type)
            GameAction.TogglePause -> togglePause()
            is GameAction.SetTimeScale -> {
                submit(PlayerCommand.SetSimulationSpeed(action.multiplier.coerceIn(1, 2).toDouble()))
                checkpoint()
            }
            GameAction.AdvanceTutorial -> advanceTutorial()
            GameAction.DismissTutorial -> finishTutorial()
            GameAction.CompleteMission -> showInterimDebrief()
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
            GameAction.TogglePauseOnFocusLoss -> updateSettings {
                it.copy(pauseOnFocusLoss = !it.pauseOnFocusLoss)
            }
            is GameAction.SetLabelScale -> updateSettings {
                it.copy(labelScale = action.scale.coerceIn(0.8f, 1.4f))
            }
            is GameAction.CycleConflict -> cycleConflict(action.offset)
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
        uiState = uiState.copy(
            missions = missionModels(resources, playerData.progress),
            career = careerModel(playerData.progress),
            aircraft = snapshot?.let { live ->
                live.aircraft
                    .filter(AircraftState::isUiVisible)
                    .map { it.toUiModel(live) }
            }
                ?: uiState.aircraft,
            result = localizedResult,
        )
        persistUiState()
    }

    private fun navigate(screen: AppScreen) {
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
        if (!mission.locked) {
            uiState = uiState.copy(selectedMissionId = id)
            persistUiState()
        }
    }

    private fun startSelectedMission() {
        descriptorForSelection(uiState.selectedMissionId)?.let(::startScenario)
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
        engine = newEngine
        trails.clear()
        replayLog.clear()
        lastConflictAnnouncementKey = null
        lastCheckpointTick = -1L
        checkpointingDisabled = false
        suppressPersistedSession = false

        val showTutorial = descriptor.selectionId == ManchesterContent.FIRST_MISSION_ID &&
            !playerData.progress.tutorialCompleted
        pausedForTutorial = showTutorial
        uiState = uiState.copy(
            screen = AppScreen.GAME,
            selectedMissionId = descriptor.selectionId,
            selectedAircraftId = null,
            tutorialStep = if (showTutorial) 0 else null,
            result = null,
            canContinue = true,
            isRestoring = false,
            sessionPersistenceFailed = false,
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
            engine = restored.engine
            trails.clear()
            replayLog.clear()
            replayLog += saved.entries
            lastCheckpointTick = saved.savedTick
            checkpointingDisabled = false
            suppressPersistedSession = false
            pausedForTutorial = restored.restoreTutorial
            uiState = uiState.copy(
                screen = AppScreen.GAME,
                selectedMissionId = saved.descriptor.selectionId,
                selectedAircraftId = saved.selectedAircraftId?.takeIf { selected ->
                    restored.snapshot.aircraft.any { it.id == selected && it.isUiVisible() }
                },
                tutorialStep = if (restored.restoreTutorial) {
                    restoredTutorialState?.takeIf { it in 0..LAST_TUTORIAL_STEP } ?: 0
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
        val needsTutorial = saved.descriptor.selectionId == ManchesterContent.FIRST_MISSION_ID &&
            !tutorialCompleted && saved.savedTick == 0L && saved.entries.isEmpty()
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
    }

    private fun submitRoute(points: List<NormalizedPoint>) {
        val id = uiState.selectedAircraftId ?: return
        val waypoints = points.asSequence()
            .filter { it.x.isFinite() && it.y.isFinite() }
            .map(NormalizedPoint::clamped)
            .map { Vec2(it.x.toDouble(), it.y.toDouble()) }
            .take(MAX_ROUTE_POINTS)
            .toList()
        submit(PlayerCommand.SetRoute(id, Route(waypoints)))
    }

    private inline fun submitForSelected(command: (AircraftState) -> PlayerCommand) {
        val id = uiState.selectedAircraftId ?: return
        val aircraft = lastSnapshot?.aircraft?.firstOrNull { it.id == id } ?: return
        submit(command(aircraft))
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
        if (current >= LAST_TUTORIAL_STEP) {
            finishTutorial()
        } else {
            uiState = uiState.copy(tutorialStep = current + 1)
            persistUiState()
        }
    }

    private fun finishTutorial() {
        if (uiState.tutorialStep == null) return
        uiState = uiState.copy(tutorialStep = null)
        if (pausedForTutorial && lastSnapshot?.status == GameStatus.PAUSED) {
            pausedForTutorial = false
            engine?.submit(PlayerCommand.Resume)?.let(::publish)
        } else {
            pausedForTutorial = false
        }
        persistUiState()
        viewModelScope.launch { preferences.setTutorialCompleted() }
    }

    /** Opens an unrecorded interim debrief; only an engine terminal event awards progress. */
    private fun showInterimDebrief() {
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
            ),
            canContinue = true,
        )
        persistUiState()
        checkpoint()
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
        publish(currentEngine.submit(command))
        if (command.isReplayable()) checkpoint()
    }

    private fun publish(snapshot: GameSnapshot) {
        val wasTerminal = lastSnapshot?.status?.isTerminal() == true
        lastSnapshot = snapshot
        recordTrails(snapshot)

        val aircraft = snapshot.aircraft.filter(AircraftState::isUiVisible).map { it.toUiModel(snapshot) }
        val runways = snapshot.toRunwayUiModels()
        val callsigns = snapshot.aircraft.associate { it.id to it.callsign }
        val conflicts = conflictsToUiModels(snapshot.conflicts, callsigns)
        val previousPair = uiState.activeConflict?.canonicalPairKey()
        val activeConflictIndex = retainedConflictIndex(previousPair, conflicts)
        val conflictAnnouncement = conflictAnnouncement(snapshot.events, callsigns)
        if (conflicts.isEmpty() && conflictAnnouncement == null) {
            lastConflictAnnouncementKey = null
        }
        val selected = uiState.selectedAircraftId?.takeIf { selectedId ->
            aircraft.any { it.id == selectedId }
        }
        uiState = uiState.copy(
            aircraft = aircraft,
            selectedAircraftId = selected,
            runway = runways.firstOrNull() ?: RunwayUiModel(),
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
        )
        emitFeedback(snapshot.events)

        if (!wasTerminal && snapshot.status.isTerminal()) handleTerminal(snapshot)
        if (snapshot.status.isActive() && snapshot.tick - lastCheckpointTick >= CHECKPOINT_TICKS) {
            checkpoint()
        }
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
        val completed = snapshot.status == GameStatus.COMPLETED
        val isEndless = descriptor.selectionId == ENDLESS_SELECTION_ID

        if (completed && descriptor is SessionDescriptor.Endless) {
            val cumulativeScore = descriptor.cumulativeScore + snapshot.score.total
            viewModelScope.launch { preferences.recordEndlessHighScore(cumulativeScore) }
            startScenario(
                descriptor.copy(
                    stage = descriptor.stage + 1,
                    cumulativeScore = cumulativeScore,
                ),
            )
            return
        }

        val stars = if (completed) snapshot.stars.coerceIn(0, 3) else 0
        val finalScore = if (descriptor is SessionDescriptor.Endless) {
            descriptor.cumulativeScore + snapshot.score.total
        } else {
            snapshot.score.total
        }
        val previousBest = if (isEndless) {
            playerData.progress.endlessHighScore
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
            result = snapshot.toResult(title, stars, personalBest, finalScore),
            canContinue = false,
        )
        persistUiState()
        viewModelScope.launch {
            if (completed && !isEndless && descriptor.selectionId in ManchesterContent.missionIds) {
                preferences.recordMissionResult(descriptor.selectionId, stars, finalScore)
            }
            if (isEndless) preferences.recordEndlessHighScore(finalScore)
        }
        checkpointJob = viewModelScope.launch {
            preferences.clearActiveSession()
        }
    }

    private fun pauseAndCheckpoint(forcePause: Boolean) {
        val currentEngine = engine ?: return
        val snapshot = lastSnapshot ?: currentEngine.snapshot
        val shouldPause = forcePause || playerData.settings.pauseOnFocusLoss
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                disableSessionPersistence()
            }
        }
    }

    private fun applyPlayerData(updated: PlayerData) {
        playerData = updated
        val activeInMemory = lastSnapshot?.status?.isActive() == true
        val record = updated.activeSession
        val recordKey = record?.toSessionKey()
        val persisted = !activeInMemory && !suppressPersistedSession &&
            recordKey == validatedSessionKey && validatedSessionAvailable
        val missions = missionModels(resources, updated.progress)
        val selectedId = uiState.selectedMissionId.takeIf { id ->
            missions.any { it.id == id }
        } ?: ManchesterContent.FIRST_MISSION_ID
        uiState = uiState.copy(
            selectedMissionId = selectedId,
            missions = missions,
            career = careerModel(updated.progress),
            settings = updated.settings.toUiState(),
            settingsLoaded = true,
            canContinue = activeInMemory || persisted,
        )
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
                wind = wind,
            )
        }
    }

    private fun fixesFor(snapshot: GameSnapshot): List<FixUiModel> = buildList {
        ManchesterContent.airport.fixes.forEach { fix ->
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
        ManchesterContent.airport.runwayEnds.filter { it.id in activeRunways }.forEach { runway ->
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

    private fun emitFeedback(events: List<GameEvent>) {
        val kind = events.mapNotNull(GameEvent::feedbackKind).maxByOrNull(LiveFeedbackKind::priority)
            ?: return
        feedbackCue = LiveFeedbackCue(++feedbackSequence, kind)
    }

    private fun descriptorForSelection(id: String): SessionDescriptor? = when (id) {
        ENDLESS_SELECTION_ID -> SessionDescriptor.Endless(DEFAULT_ENDLESS_SEED, 1, 0)
        else -> ManchesterContent.mission(id)?.let { SessionDescriptor.Authored(it.id) }
    }

    private fun localizedScenarioTitle(descriptor: SessionDescriptor): String = when (descriptor) {
        is SessionDescriptor.Authored -> {
            val index = ManchesterContent.missionIds.indexOf(descriptor.missionId)
            MISSION_TITLE_RES_IDS.getOrNull(index)?.let { resources.getString(it) }
                ?: ManchesterContent.mission(descriptor.missionId)?.title
                ?: resources.getString(R.string.shift_fallback)
        }
        is SessionDescriptor.Endless -> resources.getString(
            R.string.endless_stage_title,
            descriptor.stage,
        )
    }

    private fun restorableFromRecord(record: ActiveSessionRecord?): RestorableSession? {
        if (record == null || record.schemaVersion != SESSION_SCHEMA) return null
        return runCatching {
            require(record.payload.length <= MAX_SESSION_PAYLOAD_CHARS)
            val lines = record.payload.split('\n')
            require(lines.size in 3..(MAX_REPLAY_ENTRIES + 3))
            require(lines[0] == SESSION_PAYLOAD_PREFIX)

            val descriptorFields = lines[1].split('|')
            require(descriptorFields.firstOrNull() == "D")
            val descriptor = when (descriptorFields.getOrNull(1)) {
                "A" -> {
                    require(descriptorFields.size == 3)
                    SessionDescriptor.Authored(descriptorFields[2]).also {
                        require(ManchesterContent.mission(it.missionId) != null)
                    }
                }
                "E" -> {
                    require(descriptorFields.size == 5)
                    SessionDescriptor.Endless(
                        seed = checkNotNull(descriptorFields[2].toLongOrNull()),
                        stage = checkNotNull(descriptorFields[3].toIntOrNull()),
                        cumulativeScore = checkNotNull(descriptorFields[4].toIntOrNull()),
                    ).also {
                        require(it.stage in 1..MAX_ENDLESS_STAGE)
                        require(it.cumulativeScore >= 0)
                    }
                }
                else -> error("Unknown session descriptor")
            }

            val metadata = lines[2].split('|')
            require(metadata.size == 5 && metadata[0] == "S")
            val savedTick = checkNotNull(metadata[1].toLongOrNull()).also { require(it >= 0) }
            val speed = checkNotNull(metadata[2].toDoubleOrNull()).also { value ->
                require(value == 1.0 || value == 2.0)
            }
            val paused = checkNotNull(metadata[3].toBooleanStrictOrNull())
            val selectedAircraftId = metadata[4].takeIf(String::isNotBlank)
            val entries = lines.drop(3).map(::parseReplayEntry)
            require(entries.zipWithNext().all { (first, second) -> first.tick <= second.tick })
            require(entries.all { it.tick <= savedTick })

            val content = descriptor.content()
            require(record.scenarioId == content.id)
            val aircraftIds = content.traffic.mapTo(mutableSetOf(), TrafficSpawnDefinition::id)
            require(selectedAircraftId == null || selectedAircraftId in aircraftIds)
            val runwayIds = content.runwayConfiguration.arrivalEndIds +
                content.runwayConfiguration.departureEndIds
            entries.forEach { entry ->
                require(entry.command.aircraftId() in aircraftIds)
                when (val command = entry.command) {
                    is PlayerCommand.ClearForTakeoff -> require(command.runwayId in runwayIds)
                    is PlayerCommand.ClearToLand -> require(command.runwayId in runwayIds)
                    else -> Unit
                }
            }
            RestorableSession(descriptor, savedTick, speed, paused, selectedAircraftId, entries)
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
                        )
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
            else -> error("Unknown replay command")
        }
        return ReplayEntry(tick, command)
    }

    private sealed interface SessionDescriptor {
        val selectionId: String
        fun content(): ContentScenarioDefinition

        data class Authored(val missionId: String) : SessionDescriptor {
            override val selectionId = missionId
            override fun content() = checkNotNull(ManchesterContent.mission(missionId))
        }

        data class Endless(
            val seed: Long,
            val stage: Int,
            val cumulativeScore: Int,
        ) : SessionDescriptor {
            override val selectionId = ENDLESS_SELECTION_ID
            override fun content() = EndlessScenarioGenerator.generate(seed, stage)
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
        val entries: List<ReplayEntry>,
    ) {
        fun toPayload(): String = buildList {
            add(SESSION_PAYLOAD_PREFIX)
            add(
                when (descriptor) {
                    is SessionDescriptor.Authored -> "D|A|${descriptor.missionId}"
                    is SessionDescriptor.Endless ->
                        "D|E|${descriptor.seed}|${descriptor.stage}|${descriptor.cumulativeScore}"
                },
            )
            add("S|$savedTick|$speedMultiplier|$wasPaused|${selectedAircraftId.orEmpty()}")
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

    private data class SessionRecordKey(
        val schemaVersion: Int,
        val scenarioId: String,
        val savedAtEpochMillis: Long,
        val payloadLength: Int,
    )

    companion object {
        private const val TICK_MILLIS = 100L
        private const val TICK_SECONDS = 0.1
        private const val TICKS_PER_SECOND = 10
        private const val CHECKPOINT_TICKS = 50L
        private const val RESTORE_STEP_CHUNK = 100
        private const val TRAIL_POINT_COUNT = 10
        private const val LAST_TUTORIAL_STEP = 3
        private const val SESSION_SCHEMA = 2
        private const val SESSION_PAYLOAD_PREFIX = "replay-v2"
        private const val MAX_REPLAY_ENTRIES = 4_096
        private const val MAX_ROUTE_POINTS = 256
        private const val MAX_ENDLESS_STAGE = 10_000
        private const val MAX_SESSION_PAYLOAD_CHARS = 500_000
        private const val ENDLESS_SELECTION_ID = "endless"
        private const val DEFAULT_ENDLESS_SEED = 20_260_416L
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
        )

        private fun initialUiState(resources: Resources) = GameUiState(
            selectedMissionId = ManchesterContent.FIRST_MISSION_ID,
            missions = missionModels(resources, PlayerProgress()),
            career = careerModel(PlayerProgress()),
            fixes = ManchesterContent.airport.fixes.map { fix ->
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
            val authored = ManchesterContent.authoredMissions.mapIndexed { index, mission ->
                mission.toUiModel(
                    resources = resources,
                    id = mission.id,
                    number = index + 1,
                    title = MISSION_TITLE_RES_IDS.getOrNull(index)
                        ?.let { resources.getString(it) }
                        ?: mission.title,
                    briefing = MISSION_BRIEFING_RES_IDS.getOrNull(index)
                        ?.let { resources.getString(it) }
                        ?: mission.briefing,
                    subtitle = mission.tutorialFocus.subtitle(resources),
                    bestStars = progress.missionStars[mission.id] ?: 0,
                    bestScore = progress.missionBestScores[mission.id],
                    completed = mission.id in progress.missionStars,
                    locked = mission.id !in progress.unlockedMissionIds,
                )
            }
            val introductoryMissionsCompleted = ManchesterContent.missionIds.getOrNull(4)
                ?.let { (progress.missionStars[it] ?: 0) > 0 } == true
            val highScore = progress.endlessHighScore
            val endless = EndlessScenarioGenerator.generate(DEFAULT_ENDLESS_SEED, 1)
            return authored + endless.toUiModel(
                resources = resources,
                id = ENDLESS_SELECTION_ID,
                number = authored.size + 1,
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
            )
        }

        private fun ContentScenarioDefinition.toUiModel(
            resources: Resources,
            id: String,
            number: Int,
            title: String,
            subtitle: String,
            briefing: String,
            bestStars: Int?,
            bestScore: Int?,
            completed: Boolean,
            locked: Boolean,
            isEndless: Boolean = false,
        ): MissionUiModel {
            val activeRunwayIds = runwayConfiguration.arrivalEndIds +
                runwayConfiguration.departureEndIds
            val runwayLabel = ManchesterContent.airport.runwayEnds
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
                        ?.let { fixId -> ManchesterContent.airport.fixes.firstOrNull { it.id == fixId }?.position }
                        ?: ManchesterContent.airport.runwayEnds
                            .firstOrNull { it.id == spawn.runwayEndId }
                            ?.threshold
                    point?.let { NormalizedPoint(it.x.toFloat(), it.y.toFloat()) }
                },
                locked = locked,
                isEndless = isEndless,
            )
        }

        private fun careerModel(progress: PlayerProgress) = CareerUiState(
            completedShifts = progress.completedMissionCount,
            totalShifts = ManchesterContent.missionIds.size,
            earnedStars = progress.totalStars,
            availableStars = ManchesterContent.missionIds.size * 3,
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
    AircraftStatus.INBOUND, AircraftStatus.GO_AROUND -> FlightPhase.ARRIVAL
    AircraftStatus.APPROACH, AircraftStatus.LANDING -> FlightPhase.APPROACH
    AircraftStatus.HOLDING_SHORT, AircraftStatus.TAKEOFF_ROLL, AircraftStatus.DEPARTING -> FlightPhase.DEPARTURE
    AircraftStatus.LANDED -> FlightPhase.LANDED
    AircraftStatus.EXITED, AircraftStatus.CRASHED -> FlightPhase.EXITED
}

private fun AircraftState.isUiVisible() = status !in setOf(
    AircraftStatus.LANDED,
    AircraftStatus.EXITED,
    AircraftStatus.CRASHED,
)

private fun Clearance.toUiText(status: AircraftStatus, resources: Resources): String = when (this) {
    Clearance.None -> when (status) {
        AircraftStatus.INBOUND -> resources.getString(R.string.clearance_radar_contact)
        AircraftStatus.APPROACH -> resources.getString(R.string.clearance_approach)
        AircraftStatus.GO_AROUND -> resources.getString(R.string.clearance_go_around_status)
        AircraftStatus.HOLDING_SHORT -> resources.getString(R.string.clearance_hold_short)
        AircraftStatus.TAKEOFF_ROLL -> resources.getString(R.string.clearance_takeoff_roll)
        AircraftStatus.DEPARTING -> resources.getString(R.string.clearance_climb_exit)
        AircraftStatus.LANDING -> resources.getString(R.string.clearance_landing_roll)
        AircraftStatus.LANDED -> resources.getString(R.string.clearance_landed)
        AircraftStatus.EXITED -> resources.getString(R.string.clearance_frequency_changed)
        AircraftStatus.CRASHED -> resources.getString(R.string.clearance_emergency)
    }
    is Clearance.Land -> resources.getString(R.string.clearance_land_runway, runwayId)
    is Clearance.Takeoff -> resources.getString(R.string.clearance_takeoff_runway, runwayId)
    is Clearance.GoAround -> resources.getString(
        R.string.clearance_go_around_altitude,
        NumberFormat.getIntegerInstance(resources.configuration.locales[0])
            .format(targetAltitudeFeet.roundToInt()),
    )
}

private fun Vec2.toUiPoint() = NormalizedPoint(x.toFloat(), y.toFloat())

private fun PlayerSettings.toUiState() = SettingsUiState(
    musicVolume = musicVolume,
    effectsVolume = effectsVolume,
    hapticsEnabled = hapticsEnabled,
    trailsEnabled = trailsEnabled,
    reducedMotion = reducedMotion,
    highContrast = highContrast,
    labelScale = labelScale,
    labelDeclutteringEnabled = labelDeclutteringEnabled,
    pauseOnFocusLoss = pauseOnFocusLoss,
)

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

private fun GameSnapshot.toResult(
    title: String,
    stars: Int,
    personalBest: Boolean,
    displayedScore: Int = score.total,
) = MissionResultUiModel(
    title = title,
    stars = stars,
    score = displayedScore,
    safeArrivals = score.safeArrivals,
    safeDepartures = score.safeDepartures,
    efficiencyBonus = score.efficiencyPoints + score.timeBonusPoints,
    separationPenalty = score.penalties,
    personalBest = personalBest,
    successful = status == GameStatus.COMPLETED,
)

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
    is PlayerCommand.ClearForTakeoff,
    is PlayerCommand.ClearToLand,
    is PlayerCommand.GoAround,
    is PlayerCommand.SetRoute,
    is PlayerCommand.SetTargetAltitude,
    is PlayerCommand.SetTargetSpeed -> true
    PlayerCommand.Pause,
    PlayerCommand.Resume,
    PlayerCommand.Start,
    is PlayerCommand.SetSimulationSpeed -> false
}

private fun PlayerCommand.aircraftId(): String = when (this) {
    is PlayerCommand.ClearForTakeoff -> aircraftId
    is PlayerCommand.ClearToLand -> aircraftId
    is PlayerCommand.GoAround -> aircraftId
    is PlayerCommand.SetRoute -> aircraftId
    is PlayerCommand.SetTargetAltitude -> aircraftId
    is PlayerCommand.SetTargetSpeed -> aircraftId
    PlayerCommand.Pause,
    PlayerCommand.Resume,
    PlayerCommand.Start,
    is PlayerCommand.SetSimulationSpeed -> error("Command does not target an aircraft")
}

private fun GameEvent.feedbackKind(): LiveFeedbackKind? = when (this) {
    is GameEvent.Collision, is GameEvent.RunwayIncursion, is GameEvent.ScenarioFailed -> LiveFeedbackKind.FAILURE
    is GameEvent.CommandRejected,
    is GameEvent.ConflictWarning,
    is GameEvent.SeparationLost,
    is GameEvent.StrikeIssued -> LiveFeedbackKind.WARNING
    is GameEvent.AircraftExited,
    is GameEvent.Landed,
    is GameEvent.ScenarioCompleted,
    is GameEvent.Takeoff,
    is GameEvent.Touchdown -> LiveFeedbackKind.SUCCESS
    is GameEvent.ClearanceIssued, is GameEvent.RouteUpdated -> LiveFeedbackKind.CONFIRMATION
    is GameEvent.AircraftLeftAirspace,
    is GameEvent.AircraftSpawned,
    is GameEvent.GoAround -> null
}

private fun TutorialFocus.subtitle(resources: Resources): String = resources.getString(when (this) {
    TutorialFocus.SELECTION_AND_ROUTING -> R.string.focus_selection_routing
    TutorialFocus.ALTITUDE -> R.string.focus_altitude
    TutorialFocus.SPEED -> R.string.focus_speed
    TutorialFocus.LANDING_AND_GO_AROUND -> R.string.focus_landing_go_around
    TutorialFocus.DEPARTURES -> R.string.focus_departures
    TutorialFocus.RUNWAY_OCCUPANCY -> R.string.focus_runway_occupancy
    TutorialFocus.MIXED_TRAFFIC -> R.string.focus_mixed_traffic
    TutorialFocus.PARALLEL_RUNWAYS -> R.string.focus_parallel_runways
    TutorialFocus.NONE -> R.string.focus_full_sector
})
