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
)

sealed interface GameAction {
    data class Navigate(val screen: AppScreen) : GameAction
    data class SelectMission(val id: String) : GameAction
    data object StartSelectedMission : GameAction
    data object ContinueLastGame : GameAction
    data class SelectAircraft(val id: String?) : GameAction
    data class CommitRoute(val points: List<NormalizedPoint>) : GameAction
    data class SetTargetAltitude(val feet: Int) : GameAction
    data class SetTargetSpeed(val knots: Int) : GameAction
    /** Builds a stable final route and selects safe landing altitude/speed targets. */
    data object PrepareApproach : GameAction
    data class IssueClearance(val type: ClearanceType) : GameAction
    data object TogglePause : GameAction
    data class SetTimeScale(val multiplier: Int) : GameAction
    data object AdvanceTutorial : GameAction
    data object DismissTutorial : GameAction
    data object CompleteMission : GameAction
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
}

enum class ClearanceType { TAKE_OFF, LAND, GO_AROUND }
