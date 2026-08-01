package com.stuart.atccontroller.ui

/** Independently comparable tick data consumed by the radar surface. */
data class RadarUiSlice(
    val aircraft: List<AircraftUiModel>,
    val selectedAircraftId: String?,
    val runways: List<RunwayUiModel>,
    val fixes: List<FixUiModel>,
    val conflicts: List<ConflictUiModel>,
    val conflictAnnouncement: ConflictAnnouncementUiModel?,
)

/** Command-deck state separated from geometry so selection/readback can update independently. */
data class CommandUiSlice(
    val selectedAircraft: AircraftUiModel?,
    val commandReadback: CommandReadbackUiModel?,
    val runwayProceduresEnabled: Boolean,
    val proceduralControlEnabled: Boolean,
    val approachSetupAssistEnabled: Boolean,
)

data class ObjectiveUiSlice(
    val progress: List<ObjectiveProgressUiModel>,
    val movementsRemaining: Int,
    val timeRemainingSeconds: Int,
    val clockState: MissionClockState,
    val starForecast: StarForecastUiModel,
)

internal fun GameUiState.toRadarSlice() = RadarUiSlice(
    aircraft = aircraft,
    selectedAircraftId = selectedAircraftId,
    runways = runways,
    fixes = fixes,
    conflicts = conflicts,
    conflictAnnouncement = conflictAnnouncement,
)

internal fun GameUiState.toCommandSlice() = CommandUiSlice(
    selectedAircraft = selectedAircraft,
    commandReadback = commandReadback,
    runwayProceduresEnabled = runwayProceduresEnabled,
    proceduralControlEnabled = proceduralControlEnabled,
    approachSetupAssistEnabled = approachSetupAssistEnabled,
)

internal fun GameUiState.toObjectiveSlice() = ObjectiveUiSlice(
    progress = objectiveProgress,
    movementsRemaining = movementsRemaining,
    timeRemainingSeconds = missionTimeRemainingSeconds,
    clockState = missionClockState,
    starForecast = starForecast,
)
