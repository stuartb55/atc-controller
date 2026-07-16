package com.stuart.atccontroller.ui

/**
 * Presentation contract for the game. The simulation layer can map its immutable snapshot to
 * [GameUiState] and translate [GameAction] into engine commands without depending on Compose.
 */
data class GameUiState(
    val screen: AppScreen = AppScreen.HOME,
    val selectedMissionId: String = "",
    val missions: List<MissionUiModel> = emptyList(),
    val career: CareerUiState = CareerUiState(),
    val aircraft: List<AircraftUiModel> = emptyList(),
    val selectedAircraftId: String? = null,
    val runway: RunwayUiModel = RunwayUiModel(),
    /** All active runway directions; [runway] remains the primary compatibility value. */
    val runways: List<RunwayUiModel> = emptyList(),
    val fixes: List<FixUiModel> = emptyList(),
    val conflicts: List<ConflictUiModel> = emptyList(),
    val activeConflictIndex: Int = 0,
    val conflictAnnouncement: ConflictAnnouncementUiModel? = null,
    val score: Int = 0,
    val elapsedSeconds: Int = 0,
    val strikes: Int = 0,
    val isPaused: Boolean = false,
    val timeScale: Int = 1,
    val tutorialStep: Int? = null,
    val settings: SettingsUiState = SettingsUiState(),
    /** True after the first persisted settings value has been loaded. */
    val settingsLoaded: Boolean = false,
    /** True while a persisted simulation is being reconstructed off the main thread. */
    val isRestoring: Boolean = false,
    /** Signals that the current session could not be checkpointed and may not be resumable. */
    val sessionPersistenceFailed: Boolean = false,
    val result: MissionResultUiModel? = null,
    val canContinue: Boolean = false,
    val progressionSaveStatus: ProgressionSaveStatus = ProgressionSaveStatus.NOT_REQUIRED,
    val nextMissionId: String? = null,
    val abandonConfirmationVisible: Boolean = false,
    val objectiveProgress: List<ObjectiveProgressUiModel> = emptyList(),
    val movementsRemaining: Int = 0,
    val missionTimeRemainingSeconds: Int = 0,
    val missionOverdue: Boolean = false,
    val upcomingTraffic: List<UpcomingTrafficUiModel> = emptyList(),
    val eventFeed: List<EventFeedEntryUiModel> = emptyList(),
    val flightStrips: List<FlightStripUiModel> = emptyList(),
    val starForecast: StarForecastUiModel = StarForecastUiModel(),
    val weatherImpact: WeatherImpactUiModel = WeatherImpactUiModel(),
    val training: TrainingUiModel? = null,
    val replay: ReplayUiModel? = null,
    val completedReplays: List<CompletedReplayUiModel> = emptyList(),
    val runwayProceduresEnabled: Boolean = false,
) {
    val selectedAircraft: AircraftUiModel?
        get() = aircraft.firstOrNull { it.id == selectedAircraftId }

    val selectedMission: MissionUiModel?
        get() = missions.firstOrNull { it.id == selectedMissionId }

    val activeConflict: ConflictUiModel?
        get() = conflicts.getOrNull(activeConflictIndex)

    val visibleRunways: List<RunwayUiModel>
        get() = runways.ifEmpty { listOfNotNull(runway.takeIf { it.id.isNotBlank() }) }
}

enum class AppScreen { HOME, MISSIONS, GAME, RESULTS, SETTINGS, ABOUT }

data class NormalizedPoint(val x: Float, val y: Float) {
    fun clamped() = NormalizedPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

enum class FlightPhase { ARRIVAL, DEPARTURE, APPROACH, LANDED, EXITED }

data class AircraftUiModel(
    val id: String,
    val callsign: String,
    val type: String,
    val position: NormalizedPoint,
    val headingDegrees: Float,
    val altitudeFeet: Int,
    val targetAltitudeFeet: Int,
    val speedKnots: Int,
    val targetSpeedKnots: Int,
    val phase: FlightPhase,
    val clearance: String,
    val assignedRunway: String? = null,
    val fuelPercent: Int,
    val trail: List<NormalizedPoint> = emptyList(),
    val route: List<NormalizedPoint> = emptyList(),
    val conflictLevel: ConflictLevel = ConflictLevel.NONE,
)

enum class ConflictLevel { NONE, PREDICTED, LOSS }

data class ObjectiveProgressUiModel(
    val id: String,
    val label: String,
    val current: Int,
    val target: Int,
    val passed: Boolean,
)

data class UpcomingTrafficUiModel(
    val aircraftId: String,
    val callsign: String,
    val intent: String,
    val runwayId: String?,
    val secondsToEntry: Int,
)

enum class EventFeedSeverity { ROUTINE, SUCCESS, WARNING, CRITICAL }

data class EventFeedEntryUiModel(
    val sequence: Long,
    val elapsedSeconds: Int,
    val caption: String,
    val aircraftIds: List<String> = emptyList(),
    val severity: EventFeedSeverity = EventFeedSeverity.ROUTINE,
    val rejectionCode: String? = null,
)

data class FlightStripUiModel(
    val aircraftId: String,
    val callsign: String,
    val phase: FlightPhase,
    val runwayId: String?,
    val fuelPercent: Int,
    val conflictLevel: ConflictLevel,
    val clearance: String,
    val wakeRequiredSeconds: Int? = null,
    val wakeActualSeconds: Int? = null,
)

data class StarForecastUiModel(
    val securedStars: Int = 0,
    val pointsToNextStar: Int? = null,
)

data class WeatherImpactUiModel(
    val wind: String = "—",
    val visibility: String = "—",
    val crosswindByRunway: Map<String, Int> = emptyMap(),
    val windDriftActive: Boolean = false,
    val reducedVisibilityActive: Boolean = false,
)

data class TrainingUiModel(
    val lessonId: String,
    val stepIndex: Int,
    val stepCount: Int,
    val prompt: String,
    val actionGate: String,
    val canAdvance: Boolean,
)

data class ReplayUiModel(
    val isPlaying: Boolean = false,
    val tick: Long = 0,
    val terminalTick: Long = 0,
    val speed: Int = 1,
    val followedAircraftId: String? = null,
    val verification: ReplayVerification = ReplayVerification.PENDING,
)

enum class ReplayVerification { PENDING, VERIFIED, FAILED }

data class CompletedReplayUiModel(
    val id: String,
    val scenarioId: String,
    val score: Int,
    val terminalTick: Long,
)

data class ConflictUiModel(
    val firstAircraftId: String,
    val secondAircraftId: String,
    val firstAircraftCallsign: String,
    val secondAircraftCallsign: String,
    val secondsToConflict: Int,
    val isLossOfSeparation: Boolean = false,
)

data class ConflictAnnouncementUiModel(
    val sequence: Long,
    val firstAircraftCallsign: String,
    val secondAircraftCallsign: String,
    val secondsToConflict: Int,
    val isLossOfSeparation: Boolean,
)

data class RunwayUiModel(
    val id: String = "",
    val label: String = "",
    val center: NormalizedPoint = NormalizedPoint(.54f, .57f),
    val headingDegrees: Float = 0f,
    val isOccupied: Boolean = false,
    val wind: String = "—",
)

data class FixUiModel(
    val name: String,
    val position: NormalizedPoint,
    val kind: FixKind = FixKind.ENTRY,
)

enum class FixKind { ENTRY, EXIT, APPROACH }

data class MissionUiModel(
    val id: String,
    val number: Int,
    val title: String,
    val subtitle: String,
    val briefing: String,
    val trafficCount: Int,
    /** Null for modes such as endless that do not persist mission stars. */
    val bestStars: Int? = null,
    /** Null means no score was recorded on a legacy or unplayed mission; zero is valid. */
    val bestScore: Int? = null,
    val completed: Boolean = false,
    val runwayLabel: String = "—",
    val wind: String = "—",
    val windShort: String = "—",
    val weatherSummary: String = "—",
    val visibilityKm: Int = 0,
    val durationSeconds: Int = 0,
    val objectives: List<String> = emptyList(),
    val previewPositions: List<NormalizedPoint> = emptyList(),
    val locked: Boolean = false,
    val isEndless: Boolean = false,
)

data class CareerUiState(
    val completedShifts: Int = 0,
    val totalShifts: Int = 0,
    val earnedStars: Int = 0,
    val availableStars: Int = 0,
)

data class SettingsUiState(
    val musicVolume: Float = 0.65f,
    val effectsVolume: Float = 0.8f,
    val hapticsEnabled: Boolean = true,
    val trailsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val labelScale: Float = 1f,
    val labelDeclutteringEnabled: Boolean = true,
    val pauseOnFocusLoss: Boolean = true,
) {
    val musicEnabled: Boolean get() = musicVolume > 0f
    val effectsEnabled: Boolean get() = effectsVolume > 0f
}

data class MissionResultUiModel(
    val title: String,
    val stars: Int,
    val score: Int,
    val safeArrivals: Int,
    val safeDepartures: Int,
    val efficiencyBonus: Int,
    val separationPenalty: Int,
    val personalBest: Boolean,
    val successful: Boolean = true,
    val scoreRows: List<ScoreRowUiModel> = emptyList(),
    val pointsToNextStar: Int? = null,
)

data class ScoreRowUiModel(
    val id: String,
    val label: String,
    val points: Int,
)

sealed interface GameAction {
    data class Navigate(val screen: AppScreen) : GameAction
    data class SelectMission(val id: String) : GameAction
    data object StartSelectedMission : GameAction
    data object ContinueLastGame : GameAction
    data class SelectAircraft(val id: String?) : GameAction
    data class CommitRoute(
        val points: List<NormalizedPoint>,
        val terminalTarget: RouteTerminalTarget? = null,
    ) : GameAction
    data class DirectToFix(val name: String) : GameAction
    data class AppendFix(val name: String) : GameAction
    data object UndoWaypoint : GameAction
    data object ClearRoute : GameAction
    data class SelectEvent(val sequence: Long, val aircraftId: String? = null) : GameAction
    data class SelectFlightStrip(val aircraftId: String) : GameAction
    data class SetTargetAltitude(val feet: Int) : GameAction
    data class SetTargetSpeed(val knots: Int) : GameAction
    /** Builds a stable final route and selects safe landing altitude/speed targets. */
    data object PrepareApproach : GameAction
    data class IssueClearance(val type: ClearanceType) : GameAction
    data class AssignRunway(val runwayId: String) : GameAction
    data class AssignApproach(val runwayId: String) : GameAction
    data object CancelApproach : GameAction
    data object LineUpAndWait : GameAction
    data object CancelLandingClearance : GameAction
    data object CancelTakeoffClearance : GameAction
    data object TogglePause : GameAction
    data class SetTimeScale(val multiplier: Int) : GameAction
    data object AdvanceTutorial : GameAction
    data object DismissTutorial : GameAction
    data object RequestAbandonment : GameAction
    data object ConfirmAbandonment : GameAction
    data object CancelAbandonment : GameAction
    data object RetryProgressionPersistence : GameAction
    data object OpenNextMission : GameAction
    data object RestartMission : GameAction
    data class SetMusicVolume(val volume: Float) : GameAction
    data class SetEffectsVolume(val volume: Float) : GameAction
    data object ToggleMusicMute : GameAction
    data object ToggleEffectsMute : GameAction
    data object ToggleHaptics : GameAction
    data object ToggleTrails : GameAction
    data object ToggleReducedMotion : GameAction
    data object ToggleHighContrast : GameAction
    data object ToggleLabelDecluttering : GameAction
    data object TogglePauseOnFocusLoss : GameAction
    data class SetLabelScale(val scale: Float) : GameAction
    data class CycleConflict(val offset: Int) : GameAction
    data object ReplayTogglePlay : GameAction
    data class StartReplay(val replayId: String) : GameAction
    data object ReplayStep : GameAction
    data class ReplaySetSpeed(val multiplier: Int) : GameAction
    data class ReplaySeek(val tick: Long) : GameAction
    data class ReplayFollowAircraft(val aircraftId: String?) : GameAction
}

enum class ProgressionSaveStatus { NOT_REQUIRED, SAVING, SAVED, FAILED }

enum class ClearanceType { TAKE_OFF, LAND, GO_AROUND }
