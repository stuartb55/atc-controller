package com.stuart.atccontroller.simulation

/** A position in normalized map coordinates: (0, 0) is north-west and (1, 1) south-east. */
data class Vec2(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite()) { "Coordinates must be finite" }
        require(x in 0.0..1.0 && y in 0.0..1.0) { "Coordinates must be normalized" }
    }
}

data class Route(val waypoints: List<Vec2>) {
    companion object {
        val EMPTY = Route(emptyList())
    }
}

enum class FlightOperation { ARRIVAL, DEPARTURE }

enum class AircraftStatus {
    INBOUND,
    APPROACH,
    GO_AROUND,
    HOLDING,
    HOLDING_SHORT,
    CROSSING_RUNWAY,
    LINED_UP,
    TAKEOFF_ROLL,
    DEPARTING,
    LANDING,
    LANDED,
    EXITED,
    CRASHED,
}

enum class AircraftType(
    val maxSpeedKnots: Double,
    val takeoffSpeedKnots: Double,
    val maxLandingSpeedKnots: Double,
    val turnRateDegreesPerSecond: Double,
    val climbRateFeetPerMinute: Double,
    val descentRateFeetPerMinute: Double,
    val accelerationKnotsPerSecond: Double,
    val takeoffRollSeconds: Double,
    val landingRollSeconds: Double,
) {
    LIGHT(250.0, 90.0, 105.0, 15.0, 1_800.0, 1_500.0, 12.0, 7.0, 8.0),
    REGIONAL(330.0, 125.0, 140.0, 10.0, 2_000.0, 1_600.0, 10.0, 10.0, 11.0),
    JET(480.0, 150.0, 165.0, 8.0, 2_500.0, 2_000.0, 8.0, 13.0, 14.0),
    HEAVY(520.0, 165.0, 180.0, 5.0, 2_200.0, 1_800.0, 6.0, 17.0, 19.0),
}

sealed interface Clearance {
    object None : Clearance {
        override fun toString() = "None"
    }

    data class Approach(val runwayId: String) : Clearance
    data class LineUpAndWait(val runwayId: String) : Clearance
    data class Land(val runwayId: String) : Clearance
    data class Takeoff(val runwayId: String) : Clearance
    data class GoAround(val targetAltitudeFeet: Double) : Clearance
    data class Hold(
        val fix: Vec2,
        val inboundCourseDegrees: Double,
        val altitudeFeet: Double,
        val turnDirection: HoldTurnDirection,
        val legSeconds: Double,
    ) : Clearance
    data class Exit(val exitPoint: Vec2) : Clearance
    data class CrossRunway(val runwayId: String) : Clearance
}

enum class HoldTurnDirection { LEFT, RIGHT }

/** Explicit procedural state; it is separate from free-route waypoints for replay clarity. */
data class HoldState(
    val fix: Vec2,
    val inboundCourseDegrees: Double,
    val altitudeFeet: Double,
    val turnDirection: HoldTurnDirection,
    val legSeconds: Double,
    val established: Boolean = false,
    val elapsedSeconds: Double = 0.0,
) {
    init {
        require(inboundCourseDegrees.isFinite())
        require(altitudeFeet.isFinite() && altitudeFeet in 1_000.0..20_000.0)
        require(legSeconds.isFinite() && legSeconds in 20.0..180.0)
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0)
    }
}

enum class HandoffDirection { INBOUND, OUTBOUND }
enum class HandoffStatus { OFFERED, REQUESTED, ACKNOWLEDGED, COMPLETED, TIMED_OUT }

data class HandoffState(
    val sectorId: String,
    val direction: HandoffDirection,
    val status: HandoffStatus,
    val requestedAtSeconds: Double,
) {
    init {
        require(sectorId.isNotBlank())
        require(requestedAtSeconds.isFinite() && requestedAtSeconds >= 0.0)
    }
}

data class RunwayCrossingState(
    val runwayId: String,
    val origin: Vec2,
    val destination: Vec2,
    val elapsedSeconds: Double = 0.0,
) {
    init {
        require(runwayId.isNotBlank())
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0)
    }
}

data class AircraftState(
    val id: String,
    val callsign: String,
    val type: AircraftType,
    val operation: FlightOperation,
    val status: AircraftStatus,
    val position: Vec2,
    /** Aviation heading: 0 is north, 90 east. */
    val headingDegrees: Double,
    /** Last controller-assigned vector. Null while the aircraft is navigating a named route. */
    val assignedHeadingDegrees: Double? = null,
    val altitudeFeet: Double,
    val speedKnots: Double,
    val targetAltitudeFeet: Double = altitudeFeet,
    val targetSpeedKnots: Double = speedKnots,
    val route: Route = Route.EMPTY,
    val routeIndex: Int = 0,
    val clearance: Clearance = Clearance.None,
    /** Assigned runway for a departure, or the runway currently used by an arrival. */
    val runwayId: String? = null,
    /** Runway end whose simplified final approach has been assigned. */
    val approachRunwayId: String? = null,
    /** The desired terminal-area exit for a departure. */
    val exitPoint: Vec2? = null,
    /** Total deterministic endurance assigned when this aircraft enters the scenario. */
    val fuelCapacitySeconds: Double = 600.0,
    /** Remaining deterministic endurance. This is decremented only by fixed simulation steps. */
    val fuelRemainingSeconds: Double = fuelCapacitySeconds,
    val distanceTravelledNm: Double = 0.0,
    val statusElapsedSeconds: Double = 0.0,
    val hold: HoldState? = null,
    val holdAccumulatedSeconds: Double = 0.0,
    val exitClearanceGranted: Boolean = false,
    val handoff: HandoffState? = null,
    val runwayCrossing: RunwayCrossingState? = null,
) {
    init {
        require(id.isNotBlank()) { "Aircraft id must not be blank" }
        require(callsign.isNotBlank()) { "Callsign must not be blank" }
        require(headingDegrees.isFinite()) { "Heading must be finite" }
        require(assignedHeadingDegrees == null || assignedHeadingDegrees.isFinite()) {
            "Assigned heading must be finite"
        }
        require(altitudeFeet >= 0.0 && altitudeFeet.isFinite()) { "Altitude must be non-negative" }
        require(speedKnots >= 0.0 && speedKnots.isFinite()) { "Speed must be non-negative" }
        require(targetAltitudeFeet >= 0.0 && targetAltitudeFeet.isFinite()) {
            "Target altitude must be non-negative"
        }
        require(targetSpeedKnots >= 0.0 && targetSpeedKnots.isFinite()) {
            "Target speed must be non-negative"
        }
        require(fuelCapacitySeconds > 0.0 && fuelCapacitySeconds.isFinite()) {
            "Fuel capacity must be finite and positive"
        }
        require(fuelRemainingSeconds.isFinite() && fuelRemainingSeconds in 0.0..fuelCapacitySeconds) {
            "Remaining fuel must be finite and within the aircraft's capacity"
        }
        require(distanceTravelledNm >= 0.0 && distanceTravelledNm.isFinite()) {
            "Distance travelled must be finite and non-negative"
        }
        require(statusElapsedSeconds >= 0.0 && statusElapsedSeconds.isFinite()) {
            "Status elapsed time must be finite and non-negative"
        }
        require(holdAccumulatedSeconds >= 0.0 && holdAccumulatedSeconds.isFinite())
        require(routeIndex in 0..route.waypoints.size) { "Route index is out of range" }
    }

    companion object {
        fun inbound(
            id: String,
            callsign: String,
            type: AircraftType,
            position: Vec2,
            headingDegrees: Double,
            altitudeFeet: Double,
            speedKnots: Double,
            route: Route = Route.EMPTY,
        ) = AircraftState(
            id = id,
            callsign = callsign,
            type = type,
            operation = FlightOperation.ARRIVAL,
            status = AircraftStatus.INBOUND,
            position = position,
            headingDegrees = headingDegrees,
            altitudeFeet = altitudeFeet,
            speedKnots = speedKnots,
            route = Route(route.waypoints.toList()),
        )

        fun holdingShort(
            id: String,
            callsign: String,
            type: AircraftType,
            runway: RunwayState,
            exitPoint: Vec2,
            holdingPosition: Vec2,
            route: Route = Route(listOf(exitPoint)),
        ) = AircraftState(
            id = id,
            callsign = callsign,
            type = type,
            operation = FlightOperation.DEPARTURE,
            status = AircraftStatus.HOLDING_SHORT,
            position = holdingPosition,
            headingDegrees = runway.headingDegrees,
            altitudeFeet = 0.0,
            speedKnots = 0.0,
            targetAltitudeFeet = 5_000.0,
            targetSpeedKnots = type.takeoffSpeedKnots + 60.0,
            route = Route(route.waypoints.toList()),
            runwayId = runway.id,
            exitPoint = exitPoint,
        )
    }
}

data class RunwayState(
    val id: String,
    val threshold: Vec2,
    val end: Vec2,
    val headingDegrees: Double,
    val active: Boolean = true,
    val occupiedByAircraftId: String? = null,
    /** Both directional ends of one strip share this stable physical identity. */
    val physicalRunwayId: String = id,
    /** Empty means no aircraft performance restriction. */
    val compatibleAircraftTypes: Set<AircraftType> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "Runway id must not be blank" }
        require(headingDegrees.isFinite()) { "Runway heading must be finite" }
        require(threshold != end) { "Runway endpoints must differ" }
    }
}

/** Authored, deterministic conditions. Mechanics remain disabled until their version is non-zero. */
data class WeatherState(
    val windDirectionDegrees: Double = 0.0,
    val windSpeedKnots: Double = 0.0,
    val visibilityKm: Double = 10.0,
) {
    init {
        require(windDirectionDegrees.isFinite())
        require(windSpeedKnots.isFinite() && windSpeedKnots >= 0.0)
        require(visibilityKm.isFinite() && visibilityKm > 0.0)
    }
}

data class MechanicVersions(
    val runwayProcedures: Int = 0,
    val wakeTurbulence: Int = 0,
    val windDrift: Int = 0,
    val reducedVisibility: Int = 0,
    val proceduralControl: Int = 0,
    val dynamicEvents: Int = 0,
    val weatherChanges: Int = 0,
) {
    init {
        require(
            listOf(
                runwayProcedures,
                wakeTurbulence,
                windDrift,
                reducedVisibility,
                proceduralControl,
                dynamicEvents,
                weatherChanges,
            ).all { it >= 0 },
        )
    }
}

data class ScheduledAircraft(
    val spawnTimeSeconds: Double,
    val aircraft: AircraftState,
    /** Deterministic symmetric jitter applied from the scenario seed. */
    val spawnJitterSeconds: Double = 0.0,
) {
    init {
        require(spawnTimeSeconds >= 0.0 && spawnTimeSeconds.isFinite()) {
            "Spawn time must be non-negative"
        }
        require(spawnJitterSeconds >= 0.0 && spawnJitterSeconds.isFinite()) {
            "Spawn jitter must be non-negative"
        }
    }
}

data class ScenarioObjectives(
    val safeMovementsToComplete: Int = 0,
    val arrivalsToLand: Int = 0,
    val departuresToExit: Int = 0,
    val minimumScore: Int = 0,
    /** Maximum strikes permitted by the authored objective before the mission fails. */
    val maximumStrikes: Int = Int.MAX_VALUE,
    val completeWhenAllTrafficResolved: Boolean = true,
    val starScoreThresholds: List<Int> = listOf(1_000, 2_000, 3_000),
) {
    init {
        require(safeMovementsToComplete >= 0 && arrivalsToLand >= 0 && departuresToExit >= 0)
        require(minimumScore >= 0) { "Minimum score must not be negative" }
        require(maximumStrikes >= 0) { "Maximum strikes must not be negative" }
        require(starScoreThresholds.size == 3) { "Exactly three star thresholds are required" }
        require(
            starScoreThresholds.all { it > 0 } &&
                starScoreThresholds == starScoreThresholds.sorted() &&
                starScoreThresholds.distinct().size == starScoreThresholds.size,
        ) {
            "Star thresholds must be positive and strictly ascending"
        }
    }
}

/** Executable scoring rules carried by every scenario instead of hard-coded engine constants. */
data class ScenarioScoringRules(
    val safeArrivalPoints: Int = 1_000,
    val safeDeparturePoints: Int = 750,
    val maximumRouteEfficiencyBonusPoints: Int = 250,
    val routeInefficiencyPenaltyPointsPerNm: Double = 15.0,
    val maximumTimeBonusPoints: Int = 300,
    val timeBonusDecayPointsPerSecond: Double = 1.0,
    val completionBonusPoints: Int = 0,
    val conflictPenaltyPoints: Int = 500,
    val automaticGoAroundPenaltyPoints: Int = 200,
    val manualGoAroundPenaltyPoints: Int = 50,
    val missedExitPenaltyPoints: Int = 250,
    val runwayProcedurePenaltyPoints: Int = 250,
    val wakeViolationPenaltyPoints: Int = 300,
    val proceduralControlPenaltyPoints: Int = 200,
    val dynamicEventSuccessBonusPoints: Int = 250,
    val dynamicEventFailurePenaltyPoints: Int = 300,
) {
    init {
        require(
            listOf(
                safeArrivalPoints,
                safeDeparturePoints,
                maximumRouteEfficiencyBonusPoints,
                maximumTimeBonusPoints,
                completionBonusPoints,
                conflictPenaltyPoints,
                automaticGoAroundPenaltyPoints,
                manualGoAroundPenaltyPoints,
                missedExitPenaltyPoints,
                runwayProcedurePenaltyPoints,
                wakeViolationPenaltyPoints,
                proceduralControlPenaltyPoints,
                dynamicEventSuccessBonusPoints,
                dynamicEventFailurePenaltyPoints,
            ).all { it >= 0 },
        ) { "Scoring points and penalties must not be negative" }
        require(
            routeInefficiencyPenaltyPointsPerNm.isFinite() &&
                routeInefficiencyPenaltyPointsPerNm >= 0.0,
        ) { "Route inefficiency penalty must be finite and non-negative" }
        require(timeBonusDecayPointsPerSecond.isFinite() && timeBonusDecayPointsPerSecond >= 0.0) {
            "Time bonus decay must be finite and non-negative"
        }
    }
}

data class ScenarioDefinition(
    val id: String,
    val title: String,
    val seed: Long,
    val runways: List<RunwayState>,
    val traffic: List<ScheduledAircraft>,
    val objectives: ScenarioObjectives,
    val scoring: ScenarioScoringRules = ScenarioScoringRules(),
    val mapWidthNm: Double = 24.0,
    val mapHeightNm: Double = 24.0,
    val maxDurationSeconds: Double = 600.0,
    val maxStrikes: Int = 3,
    val weather: WeatherState = WeatherState(),
    val mechanicVersions: MechanicVersions = MechanicVersions(),
    val dynamicEvents: List<DynamicEventDefinition> = emptyList(),
    val weatherChanges: List<WeatherChangeDefinition> = emptyList(),
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(runways.isNotEmpty()) { "At least one runway is required" }
        require(runways.map { it.id }.distinct().size == runways.size) { "Runway ids must be unique" }
        require(traffic.map { it.aircraft.id }.distinct().size == traffic.size) {
            "Aircraft ids must be unique"
        }
        require(dynamicEvents.map(DynamicEventDefinition::id).distinct().size == dynamicEvents.size)
        require(weatherChanges.map(WeatherChangeDefinition::id).distinct().size == weatherChanges.size)
        require(mapWidthNm.isFinite() && mapWidthNm > 0.0) { "Map width must be finite and positive" }
        require(mapHeightNm.isFinite() && mapHeightNm > 0.0) { "Map height must be finite and positive" }
        require(maxDurationSeconds.isFinite() && maxDurationSeconds > 0.0) {
            "Maximum duration must be finite and positive"
        }
        require(maxStrikes > 0)
    }
}

enum class ConflictKind { PREDICTED, LOSS_OF_SEPARATION, COLLISION }

data class Conflict(
    val firstAircraftId: String,
    val secondAircraftId: String,
    val kind: ConflictKind,
    val horizontalDistanceNm: Double,
    val verticalDistanceFeet: Double,
    val timeToClosestApproachSeconds: Double,
)

enum class FailureReason {
    TOO_MANY_STRIKES,
    COLLISION,
    RUNWAY_INCURSION,
    AIRCRAFT_LOST,
    FUEL_EXHAUSTED,
    TIME_EXPIRED,
}

enum class GameStatus { READY, RUNNING, PAUSED, COMPLETED, FAILED }

data class ScoreBreakdown(
    val safeArrivals: Int = 0,
    val safeDepartures: Int = 0,
    val basePoints: Int = 0,
    val efficiencyPoints: Int = 0,
    val timeBonusPoints: Int = 0,
    val completionBonusPoints: Int = 0,
    /** A positive number which is subtracted from the earned score. */
    val penalties: Int = 0,
    val goAroundPenaltyPoints: Int = 0,
    val missedExitPenaltyPoints: Int = 0,
    val separationPenaltyPoints: Int = 0,
    val runwayProcedurePenaltyPoints: Int = 0,
    val wakePenaltyPoints: Int = 0,
    val proceduralPenaltyPoints: Int = 0,
    val dynamicEventBonusPoints: Int = 0,
    val dynamicEventPenaltyPoints: Int = 0,
) {
    val total: Int
        get() = (
            basePoints.toLong() + efficiencyPoints + timeBonusPoints + completionBonusPoints +
                dynamicEventBonusPoints - penalties
        ).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

enum class CommandRejectionReason {
    SIMULATION_NOT_RUNNING,
    SIMULATION_NOT_PAUSED,
    SIMULATION_ALREADY_STARTED,
    SIMULATION_NOT_ACTIVE,
    MECHANIC_NOT_ENABLED,
    AIRCRAFT_NOT_ACTIVE,
    AIRCRAFT_STATE_INELIGIBLE,
    INVALID_ALTITUDE,
    INVALID_SPEED,
    INVALID_HEADING,
    INVALID_ROUTE,
    UNKNOWN_RUNWAY,
    RUNWAY_INACTIVE,
    RUNWAY_OCCUPIED,
    RUNWAY_RECIPROCAL_CONFLICT,
    RUNWAY_CLASS_INCOMPATIBLE,
    RUNWAY_WIND_UNSUITABLE,
    RUNWAY_ASSIGNMENT_MISMATCH,
    ARRIVAL_REQUIRED,
    DEPARTURE_REQUIRED,
    APPROACH_NOT_ASSIGNED,
    CLEARANCE_NOT_ACTIVE,
    WAKE_SPACING_INSUFFICIENT,
    INVALID_GO_AROUND_ALTITUDE,
    HOLD_ALREADY_ACTIVE,
    HOLD_NOT_ACTIVE,
    INVALID_HOLD,
    EXIT_NOT_AVAILABLE,
    EXIT_CLEARANCE_REQUIRED,
    HANDOFF_NOT_ELIGIBLE,
    HANDOFF_ALREADY_ACTIVE,
    CROSSING_NOT_ELIGIBLE,
    UNKNOWN_DYNAMIC_EVENT,
    DYNAMIC_EVENT_NOT_ACTIVE,
    UNKNOWN,
}

/** Version-one simplified time minima in seconds, deliberately independent of localized UI. */
object WakeSeparationRules {
    const val VERSION = 1

    fun requiredSeconds(leader: AircraftType, follower: AircraftType, version: Int = VERSION): Double {
        require(version == VERSION) { "Unknown wake-separation version $version" }
        return when (leader) {
            AircraftType.HEAVY -> when (follower) {
                AircraftType.LIGHT -> 180.0
                AircraftType.REGIONAL -> 150.0
                AircraftType.JET -> 120.0
                AircraftType.HEAVY -> 90.0
            }
            AircraftType.JET -> when (follower) {
                AircraftType.LIGHT -> 120.0
                AircraftType.REGIONAL -> 90.0
                AircraftType.JET, AircraftType.HEAVY -> 60.0
            }
            AircraftType.REGIONAL -> if (follower == AircraftType.LIGHT) 90.0 else 60.0
            AircraftType.LIGHT -> 60.0
        }
    }
}

object WeatherOperations {
    fun crosswindKnots(weather: WeatherState, runwayHeadingDegrees: Double): Double {
        val angle = Math.toRadians(weather.windDirectionDegrees - runwayHeadingDegrees)
        return kotlin.math.abs(weather.windSpeedKnots * kotlin.math.sin(angle))
    }

    fun maximumCrosswindKnots(type: AircraftType): Double = when (type) {
        AircraftType.LIGHT -> 18.0
        AircraftType.REGIONAL -> 25.0
        AircraftType.JET -> 30.0
        AircraftType.HEAVY -> 32.0
    }

    fun isRunwaySuitable(weather: WeatherState, runway: RunwayState, type: AircraftType): Boolean =
        (runway.compatibleAircraftTypes.isEmpty() || type in runway.compatibleAircraftTypes) &&
            crosswindKnots(weather, runway.headingDegrees) <= maximumCrosswindKnots(type)

    fun stabilizedApproachGateNm(visibilityKm: Double, normalGateNm: Double = 1.0): Double = when {
        visibilityKm < 3.0 -> maxOf(normalGateNm, 2.5)
        visibilityKm < 6.0 -> maxOf(normalGateNm, 1.8)
        else -> normalGateNm
    }

    fun runwayOccupancyMultiplier(visibilityKm: Double): Double = when {
        visibilityKm < 3.0 -> 1.5
        visibilityKm < 6.0 -> 1.25
        else -> 1.0
    }
}

enum class DynamicEventType {
    LOW_FUEL_PRIORITY,
    REJECTED_TAKEOFF,
    RUNWAY_CLOSURE,
    EQUIPMENT_OUTAGE,
    PRIORITY_FLIGHT,
}

enum class DynamicEventLifecycle { SCHEDULED, WARNING, ACTIVE, RECOVERY, RESOLVED, FAILED }

enum class DynamicRecoveryGoal {
    LAND_PRIORITY_AIRCRAFT,
    RESEQUENCE_DEPARTURE,
    KEEP_RUNWAY_CLEAR,
    CONTROL_WITHOUT_PREDICTION,
    EXPEDITE_PRIORITY_FLIGHT,
}

data class DynamicEventDefinition(
    val id: String,
    val type: DynamicEventType,
    val triggerSeconds: Double,
    val warningLeadSeconds: Double,
    val recoveryWindowSeconds: Double,
    val recoveryGoal: DynamicRecoveryGoal,
    val aircraftId: String? = null,
    val runwayId: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(triggerSeconds.isFinite() && triggerSeconds >= 0.0)
        require(warningLeadSeconds.isFinite() && warningLeadSeconds >= 5.0)
        require(recoveryWindowSeconds.isFinite() && recoveryWindowSeconds >= 15.0)
        require(aircraftId != null || runwayId != null || type == DynamicEventType.EQUIPMENT_OUTAGE)
    }
}

data class DynamicEventState(
    val definition: DynamicEventDefinition,
    val lifecycle: DynamicEventLifecycle = DynamicEventLifecycle.SCHEDULED,
    val acknowledged: Boolean = false,
)

enum class WeatherChangeLifecycle { SCHEDULED, WARNING, APPLIED }

data class WeatherChangeDefinition(
    val id: String,
    val effectiveSeconds: Double,
    val warningLeadSeconds: Double,
    val weather: WeatherState,
    val activeRunwayIds: Set<String>,
) {
    init {
        require(id.isNotBlank())
        require(effectiveSeconds.isFinite() && effectiveSeconds > 0.0)
        require(warningLeadSeconds.isFinite() && warningLeadSeconds >= 15.0)
        require(activeRunwayIds.isNotEmpty())
    }
}

data class WeatherChangeState(
    val definition: WeatherChangeDefinition,
    val lifecycle: WeatherChangeLifecycle = WeatherChangeLifecycle.SCHEDULED,
)

enum class CancelledClearanceType { APPROACH, LANDING, TAKEOFF }

sealed interface GameEvent {
    val elapsedSeconds: Double

    data class AircraftSpawned(val aircraftId: String, override val elapsedSeconds: Double) : GameEvent
    data class RouteUpdated(val aircraftId: String, override val elapsedSeconds: Double) : GameEvent
    data class ClearanceIssued(
        val aircraftId: String,
        val clearance: Clearance,
        override val elapsedSeconds: Double,
    ) : GameEvent

    data class CommandRejected(
        val aircraftId: String?,
        val reason: String,
        override val elapsedSeconds: Double,
        val reasonCode: CommandRejectionReason = CommandRejectionReason.UNKNOWN,
    ) : GameEvent

    data class ConflictWarning(val conflict: Conflict, override val elapsedSeconds: Double) : GameEvent
    data class SeparationLost(val conflict: Conflict, override val elapsedSeconds: Double) : GameEvent
    data class StrikeIssued(val aircraftIds: Set<String>, override val elapsedSeconds: Double) : GameEvent
    data class Collision(val conflict: Conflict, override val elapsedSeconds: Double) : GameEvent
    data class GoAround(
        val aircraftId: String,
        val automatic: Boolean,
        override val elapsedSeconds: Double,
    ) : GameEvent

    data class Touchdown(val aircraftId: String, val runwayId: String, override val elapsedSeconds: Double) : GameEvent
    data class Landed(val aircraftId: String, val runwayId: String, override val elapsedSeconds: Double) : GameEvent
    data class Takeoff(val aircraftId: String, val runwayId: String, override val elapsedSeconds: Double) : GameEvent
    data class AircraftExited(
        val aircraftId: String,
        val correctExit: Boolean,
        val assignedExitPoint: Vec2?,
        override val elapsedSeconds: Double,
    ) : GameEvent

    data class AircraftLeftAirspace(val aircraftId: String, override val elapsedSeconds: Double) : GameEvent
    data class RunwayIncursion(
        val runwayId: String,
        val aircraftIds: Set<String>,
        override val elapsedSeconds: Double,
    ) : GameEvent

    data class RunwayAssigned(
        val aircraftId: String,
        val runwayId: String,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class ApproachAssigned(
        val aircraftId: String,
        val runwayId: String?,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class ClearanceCancelled(
        val aircraftId: String,
        val clearanceType: CancelledClearanceType,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class RunwayOccupied(
        val physicalRunwayId: String,
        val runwayEndId: String,
        val aircraftId: String,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class RunwayVacated(
        val physicalRunwayId: String,
        val runwayEndId: String,
        val aircraftId: String,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class WakeWarning(
        val leaderAircraftId: String,
        val followerAircraftId: String,
        val requiredSeconds: Double,
        val actualSeconds: Double,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class WakeViolation(
        val leaderAircraftId: String,
        val followerAircraftId: String,
        val requiredSeconds: Double,
        val actualSeconds: Double,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class HoldAssigned(
        val aircraftId: String,
        val fix: Vec2,
        val inboundCourseDegrees: Double,
        val altitudeFeet: Double,
        val turnDirection: HoldTurnDirection,
        val legSeconds: Double,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class HoldEstablished(
        val aircraftId: String,
        val fix: Vec2,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class HoldCancelled(
        val aircraftId: String,
        val totalHoldSeconds: Double,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class ExitClearanceIssued(
        val aircraftId: String,
        val exitPoint: Vec2,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class HandoffChanged(
        val aircraftId: String,
        val sectorId: String,
        val direction: HandoffDirection,
        val status: HandoffStatus,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class DynamicEventChanged(
        val eventId: String,
        val type: DynamicEventType,
        val lifecycle: DynamicEventLifecycle,
        val recoveryGoal: DynamicRecoveryGoal,
        val aircraftId: String?,
        val runwayId: String?,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class RunwayCrossingStarted(
        val aircraftId: String,
        val runwayId: String,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class RunwayCrossingCompleted(
        val aircraftId: String,
        val runwayId: String,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class WeatherChangeWarning(
        val changeId: String,
        val effectiveSeconds: Double,
        val weather: WeatherState,
        val activeRunwayIds: Set<String>,
        override val elapsedSeconds: Double,
    ) : GameEvent
    data class WeatherChanged(
        val changeId: String,
        val weather: WeatherState,
        val activeRunwayIds: Set<String>,
        override val elapsedSeconds: Double,
    ) : GameEvent

    data class ScenarioCompleted(override val elapsedSeconds: Double) : GameEvent
    data class ScenarioFailed(val reason: FailureReason, override val elapsedSeconds: Double) : GameEvent
}

sealed interface PlayerCommand {
    object Start : PlayerCommand
    object Pause : PlayerCommand
    object Resume : PlayerCommand
    data class SetSimulationSpeed(val multiplier: Double) : PlayerCommand
    data class SetRoute(val aircraftId: String, val route: Route) : PlayerCommand
    data class DirectTo(val aircraftId: String, val waypoint: Vec2) : PlayerCommand
    data class AppendWaypoint(val aircraftId: String, val waypoint: Vec2) : PlayerCommand
    data class UndoWaypoint(val aircraftId: String) : PlayerCommand
    data class ClearRoute(val aircraftId: String) : PlayerCommand
    data class SetTargetHeading(val aircraftId: String, val headingDegrees: Double) : PlayerCommand
    data class SetTargetAltitude(val aircraftId: String, val altitudeFeet: Double) : PlayerCommand
    data class SetTargetSpeed(val aircraftId: String, val speedKnots: Double) : PlayerCommand
    data class ClearToLand(val aircraftId: String, val runwayId: String) : PlayerCommand
    data class ClearForTakeoff(val aircraftId: String, val runwayId: String) : PlayerCommand
    data class AssignRunway(val aircraftId: String, val runwayId: String) : PlayerCommand
    data class AssignApproach(val aircraftId: String, val runwayId: String) : PlayerCommand
    data class CancelApproach(val aircraftId: String) : PlayerCommand
    data class LineUpAndWait(val aircraftId: String, val runwayId: String) : PlayerCommand
    data class CancelLandingClearance(val aircraftId: String) : PlayerCommand
    data class CancelTakeoffClearance(val aircraftId: String) : PlayerCommand
    data class GoAround(val aircraftId: String, val targetAltitudeFeet: Double = 3_000.0) : PlayerCommand
    data class AssignHold(
        val aircraftId: String,
        val fix: Vec2,
        val inboundCourseDegrees: Double,
        val altitudeFeet: Double,
        val turnDirection: HoldTurnDirection = HoldTurnDirection.RIGHT,
        val legSeconds: Double = 60.0,
    ) : PlayerCommand
    data class CancelHold(val aircraftId: String) : PlayerCommand
    data class IssueExitClearance(val aircraftId: String) : PlayerCommand
    data class AcknowledgeInboundHandoff(val aircraftId: String) : PlayerCommand
    data class InitiateOutboundHandoff(
        val aircraftId: String,
        val sectorId: String = "TERMINAL_EXIT",
    ) : PlayerCommand
    data class AcknowledgeDynamicEvent(val eventId: String) : PlayerCommand
    data class CrossRunway(val aircraftId: String, val runwayId: String) : PlayerCommand
}

data class UpcomingAircraft(
    val aircraftId: String,
    val callsign: String,
    val operation: FlightOperation,
    val runwayId: String?,
    val spawnAtSeconds: Double,
)

data class WakeSpacing(
    val leaderAircraftId: String,
    val followerAircraftId: String,
    val runwayId: String,
    val requiredSeconds: Double,
    val actualSeconds: Double,
    val violation: Boolean,
)

enum class FlightOutcome {
    ACTIVE,
    LANDED,
    CORRECT_EXIT,
    WRONG_EXIT,
    LOST,
    CRASHED,
}

/** Deterministic terminal accounting for one flight, derived only from fixed-step engine state. */
data class FlightPerformance(
    val aircraftId: String,
    val callsign: String,
    val operation: FlightOperation,
    val outcome: FlightOutcome,
    val handlingSeconds: Double,
    val distanceTravelledNm: Double,
    val minimumUsefulDistanceNm: Double,
    val efficiencyPoints: Int,
    val timeBonusPoints: Int,
    val associatedPenaltyPoints: Int,
    val holdSeconds: Double = 0.0,
)

data class GameSnapshot(
    val scenarioId: String,
    val tick: Long,
    val elapsedSeconds: Double,
    val status: GameStatus,
    val failureReason: FailureReason?,
    val speedMultiplier: Double,
    val aircraft: List<AircraftState>,
    val runways: List<RunwayState>,
    val conflicts: List<Conflict>,
    val strikes: Int,
    val score: ScoreBreakdown,
    val stars: Int,
    val pendingAircraftCount: Int,
    /** Events produced by the most recent engine operation. */
    val events: List<GameEvent>,
    val eventHistory: List<GameEvent> = events,
    /** Stable sequence number of the first retained event, even after bounded-history eviction. */
    val eventHistoryStartSequence: Long = 0L,
    val upcomingAircraft: List<UpcomingAircraft> = emptyList(),
    val objectives: ScenarioObjectives = ScenarioObjectives(),
    val maxDurationSeconds: Double = 0.0,
    val weather: WeatherState = WeatherState(),
    val mechanicVersions: MechanicVersions = MechanicVersions(),
    val wakeSpacing: List<WakeSpacing> = emptyList(),
    val dynamicEventStates: List<DynamicEventState> = emptyList(),
    val weatherChangeStates: List<WeatherChangeState> = emptyList(),
    /** Populated for terminal snapshots; one stable report per spawned aircraft. */
    val flightPerformances: List<FlightPerformance> = emptyList(),
    /** One-Hz fixed-step samples, populated only for terminal snapshots to keep live snapshots lean. */
    val routeHistory: Map<String, List<Vec2>> = emptyMap(),
)

data class SimulationParameters(
    val fixedStepSeconds: Double = 0.1,
    val horizontalSeparationNm: Double = 3.0,
    val verticalSeparationFeet: Double = 1_000.0,
    val predictionHorizonSeconds: Double = 30.0,
    val collisionHorizontalNm: Double = 0.15,
    val collisionVerticalFeet: Double = 200.0,
    val waypointCaptureNm: Double = 0.08,
    val approachCaptureDistanceNm: Double = 3.0,
    val approachLateralToleranceNm: Double = 0.35,
    val approachHeadingToleranceDegrees: Double = 18.0,
    val touchdownRadiusNm: Double = 0.12,
    val maxTouchdownAltitudeFeet: Double = 350.0,
    val exitCaptureNm: Double = 0.3,
) {
    init {
        val values = listOf(
            fixedStepSeconds,
            horizontalSeparationNm,
            verticalSeparationFeet,
            predictionHorizonSeconds,
            collisionHorizontalNm,
            collisionVerticalFeet,
            waypointCaptureNm,
            approachCaptureDistanceNm,
            approachLateralToleranceNm,
            approachHeadingToleranceDegrees,
            touchdownRadiusNm,
            maxTouchdownAltitudeFeet,
            exitCaptureNm,
        )
        require(values.all(Double::isFinite)) { "Simulation parameters must be finite" }
        require(fixedStepSeconds > 0.0) { "Fixed step must be positive" }
        require(collisionHorizontalNm > 0.0 && horizontalSeparationNm > collisionHorizontalNm) {
            "Horizontal separation must exceed the positive collision distance"
        }
        require(collisionVerticalFeet > 0.0 && verticalSeparationFeet > collisionVerticalFeet) {
            "Vertical separation must exceed the positive collision distance"
        }
        require(predictionHorizonSeconds > 0.0) { "Prediction horizon must be positive" }
        require(waypointCaptureNm > 0.0) { "Waypoint capture distance must be positive" }
        require(approachCaptureDistanceNm > touchdownRadiusNm && touchdownRadiusNm > 0.0) {
            "Approach capture distance must exceed the positive touchdown radius"
        }
        require(approachLateralToleranceNm > 0.0) { "Approach lateral tolerance must be positive" }
        require(approachHeadingToleranceDegrees in 0.0..90.0) {
            "Approach heading tolerance must be from 0 to 90 degrees"
        }
        require(maxTouchdownAltitudeFeet >= 0.0) { "Touchdown altitude must not be negative" }
        require(exitCaptureNm > 0.0) { "Exit capture distance must be positive" }
    }
}
