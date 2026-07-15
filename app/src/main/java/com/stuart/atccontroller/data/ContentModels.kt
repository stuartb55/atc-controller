package com.stuart.atccontroller.data

/**
 * Coordinates in authored content are normalized to the playable radar area.
 * (0, 0) is the top-left and (1, 1) is the bottom-right.
 */
data class NormalizedPoint(
    val x: Double,
    val y: Double,
)

data class ContentSourceMetadata(
    val sourceName: String,
    val effectiveDateIso: String,
    val sourceUrl: String,
    val attribution: String,
    val disclaimer: String,
)

data class RunwayEndDefinition(
    val id: String,
    val physicalRunwayId: String,
    val reciprocalEndId: String,
    val threshold: NormalizedPoint,
    val approachGate: NormalizedPoint,
    val headingDegrees: Int,
)

data class PhysicalRunwayDefinition(
    val id: String,
    val displayName: String,
    val endIds: Set<String>,
)

enum class FixUse {
    ENTRY,
    EXIT,
    ENTRY_AND_EXIT,
}

data class NavigationFixDefinition(
    val id: String,
    val displayName: String,
    val position: NormalizedPoint,
    val use: FixUse,
    val isFictional: Boolean = true,
)

data class AirportDefinition(
    val id: String,
    val displayName: String,
    val mapWidthNm: Double,
    val mapHeightNm: Double,
    val physicalRunways: List<PhysicalRunwayDefinition>,
    val runwayEnds: List<RunwayEndDefinition>,
    val fixes: List<NavigationFixDefinition>,
    val source: ContentSourceMetadata,
)

data class RunwayConfigurationDefinition(
    val arrivalEndIds: Set<String>,
    val departureEndIds: Set<String>,
)

data class WeatherDefinition(
    val windDirectionDegrees: Int,
    val windSpeedKnots: Int,
    val visibilityKm: Int,
)

enum class TrafficIntent {
    ARRIVAL,
    DEPARTURE,
}

enum class AircraftPerformanceClass {
    LIGHT,
    MEDIUM,
    HEAVY,
}

/**
 * A compact, engine-independent traffic instruction. The simulation adapter derives the
 * initial map position from [entryFixId] or [runwayEndId].
 */
data class TrafficSpawnDefinition(
    val id: String,
    val callsign: String,
    val intent: TrafficIntent,
    val performanceClass: AircraftPerformanceClass,
    val spawnAtSeconds: Int,
    val entryFixId: String? = null,
    val exitFixId: String? = null,
    val runwayEndId: String,
    val initialAltitudeFeet: Int,
    val initialSpeedKnots: Int,
    val fuelSeconds: Int = 600,
)

enum class ObjectiveType {
    COMPLETE_SAFE_MOVEMENTS,
    LAND_ARRIVALS,
    DEPART_AIRCRAFT,
    LIMIT_STRIKES,
    SCORE_AT_LEAST,
}

data class ObjectiveDefinition(
    val type: ObjectiveType,
    val target: Int,
    val description: String,
)

data class StarThresholds(
    val oneStar: Int,
    val twoStars: Int,
    val threeStars: Int,
)

data class ScoringDefinition(
    val safeArrivalPoints: Int = 1_000,
    val safeDeparturePoints: Int = 750,
    val maximumRouteEfficiencyBonusPoints: Int = 250,
    val routeInefficiencyPenaltyPointsPerNm: Double = 15.0,
    val maximumTimeBonusPoints: Int = 300,
    val timeBonusDecayPointsPerSecond: Double = 1.0,
    val completionBonus: Int = 0,
    val conflictPenalty: Int = 500,
    val avoidableGoAroundPenalty: Int = 200,
    val manualGoAroundPenalty: Int = 50,
    val missedExitPenalty: Int = 250,
    val thresholds: StarThresholds,
)

enum class TutorialFocus {
    SELECTION_AND_ROUTING,
    ALTITUDE,
    SPEED,
    LANDING_AND_GO_AROUND,
    DEPARTURES,
    RUNWAY_OCCUPANCY,
    MIXED_TRAFFIC,
    PARALLEL_RUNWAYS,
    NONE,
}

data class ScenarioDefinition(
    val id: String,
    val title: String,
    val briefing: String,
    val airportId: String,
    val seed: Long,
    val difficulty: Int,
    val maxDurationSeconds: Int,
    val maxStrikes: Int = 3,
    val runwayConfiguration: RunwayConfigurationDefinition,
    val weather: WeatherDefinition,
    val traffic: List<TrafficSpawnDefinition>,
    val objectives: List<ObjectiveDefinition>,
    val scoring: ScoringDefinition,
    val tutorialFocus: TutorialFocus = TutorialFocus.NONE,
    val isEndless: Boolean = false,
)
