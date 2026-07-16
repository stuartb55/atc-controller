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
    val serviceRecord: ServiceRecordUiModel = ServiceRecordUiModel(),
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
    val missionClockState: MissionClockState = MissionClockState.ACTIVE,
    val upcomingTraffic: List<UpcomingTrafficUiModel> = emptyList(),
    val eventFeed: List<EventFeedEntryUiModel> = emptyList(),
    val flightStrips: List<FlightStripUiModel> = emptyList(),
    val starForecast: StarForecastUiModel = StarForecastUiModel(),
    val weatherImpact: WeatherImpactUiModel = WeatherImpactUiModel(),
    val training: TrainingUiModel? = null,
    val replay: ReplayUiModel? = null,
    val replayError: String? = null,
    val completedReplays: List<CompletedReplayUiModel> = emptyList(),
    val runwayProceduresEnabled: Boolean = false,
    val proceduralControlEnabled: Boolean = false,
    val customShift: CustomShiftUiModel = CustomShiftUiModel(),
    val isPracticeSession: Boolean = false,
    val isDailySession: Boolean = false,
    val configurationIdentity: String? = null,
    val activeAssistLabels: List<String> = emptyList(),
    val routeSnappingAssistEnabled: Boolean = true,
    val approachSetupAssistEnabled: Boolean = true,
    val conflictPredictionAssistEnabled: Boolean = true,
    val endlessMilestone: EndlessMilestoneUiModel? = null,
    val dynamicEvents: List<DynamicEventUiModel> = emptyList(),
) {
    val selectedAircraft: AircraftUiModel?
        get() = aircraft.firstOrNull { it.id == selectedAircraftId }

    val selectedMission: MissionUiModel?
        get() = missions.firstOrNull { it.id == selectedMissionId }

    val activeConflict: ConflictUiModel?
        get() = conflicts.getOrNull(activeConflictIndex)

    val visibleRunways: List<RunwayUiModel>
        get() = runways.filter(RunwayUiModel::isActive)
            .ifEmpty { listOfNotNull(runway.takeIf { it.id.isNotBlank() && it.isActive }) }
}

enum class AppScreen { HOME, MISSIONS, CUSTOM_SHIFT, GAME, MILESTONE, RESULTS, SETTINGS, ABOUT }

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
    val holdFixName: String? = null,
    val holdSeconds: Int = 0,
    val handoffStatus: String? = null,
    val exitClearanceGranted: Boolean = false,
)

enum class ConflictLevel { NONE, PREDICTED, LOSS }

data class ObjectiveProgressUiModel(
    val id: String,
    val kind: ObjectiveProgressKind,
    val current: Int,
    val target: Int,
    val passed: Boolean,
)

enum class ObjectiveProgressKind { SAFE_MOVEMENTS, ARRIVALS, DEPARTURES, SCORE, STRIKES }

data class UpcomingTrafficUiModel(
    val aircraftId: String,
    val callsign: String,
    val intent: UpcomingTrafficIntent,
    val runwayId: String?,
    val secondsToEntry: Int,
)

enum class UpcomingTrafficIntent { ARRIVAL, DEPARTURE }

enum class MissionClockState { ACTIVE, OVERDUE, FAILED }

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
    /** Stable domain-derived priority; never inferred from localized clearance text. */
    val runwayStatePriority: Int = 0,
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
    val changeNotice: String? = null,
)

data class DynamicEventUiModel(
    val id: String,
    val title: String,
    val lifecycle: String,
    val recoveryGoal: String,
    val affectedLabel: String,
    val canAcknowledge: Boolean,
    val failed: Boolean = false,
)

data class TrainingUiModel(
    val lessonId: String,
    val title: String,
    val stepIndex: Int,
    val stepCount: Int,
    val prompt: String,
    val actionGate: String,
    val canAdvance: Boolean,
    val rejectionMessage: String? = null,
    val isPractice: Boolean = false,
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
    val isActive: Boolean = true,
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
    val contentPackId: String = "",
    val campaignName: String = "",
    val airportName: String = "",
    val packOverview: String = "",
    val sourceAttribution: String = "",
    val contentDisclaimer: String = "",
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
    val trainingAvailable: Boolean = false,
)

data class CareerUiState(
    val completedShifts: Int = 0,
    val totalShifts: Int = 0,
    val earnedStars: Int = 0,
    val availableStars: Int = 0,
)

data class MasteryUiModel(
    val focus: String,
    val level: Int,
    val contributingResults: Int,
)

data class ServiceRecordUiModel(
    val totalSafeMovements: Int = 0,
    val currentSafeStreak: Int = 0,
    val bestSafeStreak: Int = 0,
    val achievementCount: Int = 0,
    val mastery: List<MasteryUiModel> = emptyList(),
    val recommendation: String = "",
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
    val routeSnappingEnabled: Boolean = true,
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
    val flights: List<FlightDebriefUiModel> = emptyList(),
    val timeline: List<EventFeedEntryUiModel> = emptyList(),
    val isPractice: Boolean = false,
    val configurationIdentity: String? = null,
    val isDaily: Boolean = false,
)

data class ScoreRowUiModel(
    val id: String,
    val label: String,
    val points: Int,
)

data class FlightDebriefUiModel(
    val aircraftId: String,
    val callsign: String,
    val operation: String,
    val outcome: String,
    val handlingSeconds: Int,
    val distanceTenthsNm: Int,
    val routeEfficiencyPoints: Int,
    val timeBonusPoints: Int,
    val associatedPenaltyPoints: Int,
    val eventSequences: List<Long>,
    val routeHeatmap: List<NormalizedPoint>,
    val holdSeconds: Int = 0,
)

data class CustomShiftUiModel(
    val contentPackId: String = "",
    val contentPackName: String = "",
    val airportName: String = "",
    val runwayEndLabel: String = "—",
    val seed: String = "20260716",
    val density: String = "BALANCED",
    val arrivalPercent: Int = 60,
    val runwayDirection: String = "WESTERLY",
    val weatherPreset: String = "CALM",
    val fuelPressure: String = "STANDARD",
    val strikeLimit: Int = 3,
    val routeSnapping: Boolean = true,
    val approachSetup: Boolean = true,
    val conflictPrediction: Boolean = true,
    val previewTraffic: Int = 16,
    val previewArrivals: Int = 10,
    val previewDepartures: Int = 6,
    val validationMessage: String? = null,
    val configurationIdentity: String = "",
    val ranked: Boolean = false,
    val shareCode: String = "",
    val importCode: String = "",
    val importError: String? = null,
    val dailyDate: String = "",
    val dailyConfigurationIdentity: String = "",
    val dailyCompleted: Boolean = false,
    val dailyBestScore: Int? = null,
    val dailyStreak: Int = 0,
)

data class EndlessMilestoneUiModel(
    val completedStage: Int,
    val stageScore: Int,
    val cumulativeScore: Int,
    val personalBestDelta: Int,
    val nextStage: Int,
    val nextTrafficCount: Int,
    val nextObjective: String,
    val nextWeather: String,
    val choicePending: Boolean = false,
)

sealed interface GameAction {
    data class Navigate(val screen: AppScreen) : GameAction
    data class SelectMission(val id: String) : GameAction
    data object StartSelectedMission : GameAction
    data class StartTrainingLesson(val missionId: String) : GameAction
    data class SetCustomSeed(val seed: String) : GameAction
    data class CycleCustomContentPack(val offset: Int) : GameAction
    data class CycleCustomDensity(val offset: Int) : GameAction
    data class SetCustomArrivalPercent(val percent: Int) : GameAction
    data class CycleCustomRunwayDirection(val offset: Int) : GameAction
    data class CycleCustomWeather(val offset: Int) : GameAction
    data class CycleCustomFuelPressure(val offset: Int) : GameAction
    data class SetCustomStrikeLimit(val limit: Int) : GameAction
    data object ToggleCustomRouteSnapping : GameAction
    data object ToggleCustomApproachSetup : GameAction
    data object ToggleCustomConflictPrediction : GameAction
    data object UseRankedSeededPreset : GameAction
    data object StartCustomShift : GameAction
    data class SetShareCodeInput(val code: String) : GameAction
    data object ImportShareCode : GameAction
    data object StartDailyShift : GameAction
    data object ContinueEndlessRun : GameAction
    data object CashOutEndlessRun : GameAction
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
    data class AssignHold(
        val fixName: String,
        val rightTurns: Boolean = true,
        val legSeconds: Int = 60,
    ) : GameAction
    data object CancelHold : GameAction
    data object IssueExitClearance : GameAction
    data object AcknowledgeInboundHandoff : GameAction
    data object InitiateOutboundHandoff : GameAction
    data class CrossRunway(val runwayId: String) : GameAction
    data class AcknowledgeDynamicEvent(val eventId: String) : GameAction
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
    data object ToggleRouteSnapping : GameAction
    data object TogglePauseOnFocusLoss : GameAction
    data class SetLabelScale(val scale: Float) : GameAction
    data class CycleConflict(val offset: Int) : GameAction
    data object ReplayTogglePlay : GameAction
    data class StartReplay(val replayId: String) : GameAction
    data class DeleteReplay(val replayId: String) : GameAction
    data object ReplayStep : GameAction
    data class ReplaySetSpeed(val multiplier: Int) : GameAction
    data class ReplaySeek(val tick: Long) : GameAction
    data class ReplayFollowAircraft(val aircraftId: String?) : GameAction
}

enum class ProgressionSaveStatus { NOT_REQUIRED, SAVING, SAVED, FAILED }

enum class ClearanceType { TAKE_OFF, LAND, GO_AROUND }
