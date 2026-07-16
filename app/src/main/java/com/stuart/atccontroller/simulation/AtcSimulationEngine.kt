package com.stuart.atccontroller.simulation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic, Android-free simulation engine.
 *
 * A UI normally calls [submit] for player actions and [advance] from its frame clock. Physics is
 * always evaluated at [SimulationParameters.fixedStepSeconds], irrespective of render cadence.
 */
class AtcSimulationEngine(
    scenario: ScenarioDefinition,
    private val parameters: SimulationParameters = SimulationParameters(),
) {
    private val scenario = scenario.deepCopy()
    private val runways = linkedMapOf<String, RunwayState>()
    private val aircraft = linkedMapOf<String, AircraftState>()
    private val resolvedTraffic: List<ResolvedSpawn>
    private val spawnTimes = mutableMapOf<String, Double>()
    private val minimumDistances = mutableMapOf<String, Double>()
    private val lastRunwayMovement = mutableMapOf<String, RunwayMovement>()

    private var nextSpawnIndex = 0
    private var tick = 0L
    private var elapsedSeconds = 0.0
    private var accumulatorSeconds = 0.0
    private var status = GameStatus.READY
    private var failureReason: FailureReason? = null
    private var speedMultiplier = 1.0
    private var strikes = 0
    private var score = ScoreBreakdown()
    private var currentConflicts = emptyList<Conflict>()
    private var previousLossPairs = emptySet<PairKey>()
    private var previousPredictedPairs = emptySet<PairKey>()
    private var previousWakeWarningPairs = emptySet<PairKey>()
    private var previousWakeViolationPairs = emptySet<PairKey>()
    private var lastEvents = emptyList<GameEvent>()
    private val eventHistory = ArrayDeque<GameEvent>()

    init {
        this.scenario.runways.forEach { runways[it.id] = it.copy(occupiedByAircraftId = null) }
        val random = StableRandom(this.scenario.seed)
        resolvedTraffic = this.scenario.traffic.mapIndexed { index, scheduled ->
            val jitter = if (scheduled.spawnJitterSeconds == 0.0) {
                0.0
            } else {
                (random.nextDouble() * 2.0 - 1.0) * scheduled.spawnJitterSeconds
            }
            ResolvedSpawn(
                timeSeconds = (scheduled.spawnTimeSeconds + jitter).coerceAtLeast(0.0),
                originalOrder = index,
                aircraft = scheduled.aircraft.deepCopy(),
            )
        }.sortedWith(compareBy<ResolvedSpawn> { it.timeSeconds }.thenBy { it.originalOrder })
    }

    val snapshot: GameSnapshot
        @Synchronized get() = createSnapshot(lastEvents)

    /** Applies one typed player action and returns the resulting immutable state. */
    @Synchronized
    fun submit(command: PlayerCommand): GameSnapshot {
        val events = mutableListOf<GameEvent>()
        when (command) {
            PlayerCommand.Start -> start(events)
            PlayerCommand.Pause -> {
                if (status == GameStatus.RUNNING) status = GameStatus.PAUSED
                else reject(null, CommandRejectionReason.SIMULATION_NOT_RUNNING, "The simulation is not running", events)
            }
            PlayerCommand.Resume -> {
                if (status == GameStatus.PAUSED) status = GameStatus.RUNNING
                else reject(null, CommandRejectionReason.SIMULATION_NOT_PAUSED, "The simulation is not paused", events)
            }
            is PlayerCommand.SetSimulationSpeed -> setSpeed(command.multiplier, events)
            is PlayerCommand.SetRoute -> updateAircraft(command.aircraftId, events) { state ->
                if (!state.canReceiveFlightCommands()) return@updateAircraft null
                if (command.route.waypoints.size > MAX_ROUTE_WAYPOINTS) {
                    reject(state.id, CommandRejectionReason.INVALID_ROUTE, "Route has too many waypoints", events)
                    return@updateAircraft state
                }
                state.copy(route = Route(command.route.waypoints.toList()), routeIndex = 0)
            }
            is PlayerCommand.DirectTo -> updateAircraft(command.aircraftId, events) { state ->
                if (!state.canReceiveFlightCommands()) return@updateAircraft null
                state.copy(route = Route(listOf(command.waypoint)), routeIndex = 0)
            }
            is PlayerCommand.AppendWaypoint -> updateAircraft(command.aircraftId, events) { state ->
                if (!state.canReceiveFlightCommands()) return@updateAircraft null
                val remaining = state.route.waypoints.drop(state.routeIndex)
                if (remaining.size >= MAX_ROUTE_WAYPOINTS) {
                    reject(state.id, CommandRejectionReason.INVALID_ROUTE, "Route has too many waypoints", events)
                    state
                } else {
                    state.copy(route = Route(remaining + command.waypoint), routeIndex = 0)
                }
            }
            is PlayerCommand.UndoWaypoint -> updateAircraft(command.aircraftId, events) { state ->
                if (!state.canReceiveFlightCommands()) return@updateAircraft null
                val remaining = state.route.waypoints.drop(state.routeIndex)
                state.copy(route = Route(remaining.dropLast(1)), routeIndex = 0)
            }
            is PlayerCommand.ClearRoute -> updateAircraft(command.aircraftId, events) { state ->
                if (!state.canReceiveFlightCommands()) return@updateAircraft null
                state.copy(route = Route.EMPTY, routeIndex = 0)
            }
            is PlayerCommand.SetTargetAltitude -> updateAircraft(command.aircraftId, events) { state ->
                if (!state.canReceiveFlightCommands()) return@updateAircraft null
                if (!command.altitudeFeet.isFinite() || command.altitudeFeet !in 0.0..50_000.0) {
                    reject(state.id, CommandRejectionReason.INVALID_ALTITUDE, "Altitude must be between 0 and 50,000 feet", events)
                    return@updateAircraft state
                }
                state.copy(targetAltitudeFeet = command.altitudeFeet)
            }
            is PlayerCommand.SetTargetSpeed -> updateAircraft(command.aircraftId, events) { state ->
                if (!state.canReceiveFlightCommands()) return@updateAircraft null
                if (!command.speedKnots.isFinite() || command.speedKnots !in 0.0..state.type.maxSpeedKnots) {
                    reject(state.id, CommandRejectionReason.INVALID_SPEED, "Speed is outside this aircraft's performance envelope", events)
                    return@updateAircraft state
                }
                state.copy(targetSpeedKnots = command.speedKnots)
            }
            is PlayerCommand.ClearToLand -> clearToLand(command, events)
            is PlayerCommand.ClearForTakeoff -> clearForTakeoff(command, events)
            is PlayerCommand.AssignRunway -> assignRunway(command, events)
            is PlayerCommand.AssignApproach -> assignApproach(command, events)
            is PlayerCommand.CancelApproach -> cancelApproach(command.aircraftId, events)
            is PlayerCommand.LineUpAndWait -> lineUpAndWait(command, events)
            is PlayerCommand.CancelLandingClearance -> cancelLanding(command.aircraftId, events)
            is PlayerCommand.CancelTakeoffClearance -> cancelTakeoff(command.aircraftId, events)
            is PlayerCommand.GoAround -> playerGoAround(command, events)
        }
        lastEvents = events.toList()
        rememberEvents(lastEvents)
        return createSnapshot(lastEvents)
    }

    /**
     * Advances by wall-clock time. At 2x speed, one wall-clock second evaluates two simulation
     * seconds. A paused, ready, or terminal simulation does not accumulate time.
     */
    @Synchronized
    fun advance(realDeltaSeconds: Double): GameSnapshot {
        require(realDeltaSeconds >= 0.0 && realDeltaSeconds.isFinite()) {
            "Delta time must be finite and non-negative"
        }
        val events = mutableListOf<GameEvent>()
        if (status == GameStatus.RUNNING) {
            accumulatorSeconds += realDeltaSeconds * speedMultiplier
            while (accumulatorSeconds + EPSILON >= parameters.fixedStepSeconds && status == GameStatus.RUNNING) {
                stepOnce(events)
                accumulatorSeconds -= parameters.fixedStepSeconds
            }
            accumulatorSeconds = accumulatorSeconds.coerceAtLeast(0.0)
        }
        lastEvents = events.toList()
        rememberEvents(lastEvents)
        return createSnapshot(lastEvents)
    }

    /** Exact fixed-step advancement for tests, replays, and headless simulation. */
    @Synchronized
    fun advanceFixedSteps(count: Int = 1): GameSnapshot {
        require(count >= 0) { "Step count must be non-negative" }
        val events = mutableListOf<GameEvent>()
        repeat(count) {
            if (status == GameStatus.RUNNING) stepOnce(events)
        }
        lastEvents = events.toList()
        rememberEvents(lastEvents)
        return createSnapshot(lastEvents)
    }

    private fun start(events: MutableList<GameEvent>) {
        if (status != GameStatus.READY) {
            reject(null, CommandRejectionReason.SIMULATION_ALREADY_STARTED, "The simulation has already started", events)
            return
        }
        status = GameStatus.RUNNING
        spawnDueAircraft(events)
    }

    private fun setSpeed(multiplier: Double, events: MutableList<GameEvent>) {
        val allowed = SPEEDS.any { abs(it - multiplier) < EPSILON }
        if (!allowed) {
            reject(null, CommandRejectionReason.INVALID_SPEED, "Simulation speed must be 0.5x, 1x, or 2x", events)
            return
        }
        speedMultiplier = multiplier
    }

    private inline fun updateAircraft(
        aircraftId: String,
        events: MutableList<GameEvent>,
        transform: (AircraftState) -> AircraftState?,
    ) {
        if (!canAcceptFlightCommand(aircraftId, events)) return
        val current = aircraft.getValue(aircraftId)
        val updated = transform(current)
        if (updated == null) {
            reject(aircraftId, CommandRejectionReason.AIRCRAFT_STATE_INELIGIBLE, "Aircraft cannot receive that command in its current state", events)
            return
        }
        aircraft[aircraftId] = updated
        if (updated.route != current.route) events += GameEvent.RouteUpdated(aircraftId, elapsedSeconds)
    }

    private fun canAcceptFlightCommand(
        aircraftId: String,
        events: MutableList<GameEvent>,
    ): Boolean {
        if (status != GameStatus.RUNNING && status != GameStatus.PAUSED) {
            reject(aircraftId, CommandRejectionReason.SIMULATION_NOT_ACTIVE, "The simulation is not active", events)
            return false
        }
        if (aircraftId !in aircraft) {
            reject(aircraftId, CommandRejectionReason.AIRCRAFT_NOT_ACTIVE, "Aircraft is not in the active airspace", events)
            return false
        }
        return true
    }

    private fun clearToLand(command: PlayerCommand.ClearToLand, events: MutableList<GameEvent>) {
        if (!canAcceptFlightCommand(command.aircraftId, events)) return
        val state = aircraft.getValue(command.aircraftId)
        val runway = runways[command.runwayId]
        when {
            runway == null -> reject(state.id, CommandRejectionReason.UNKNOWN_RUNWAY, "Unknown runway ${command.runwayId}", events)
            !runway.active -> reject(state.id, CommandRejectionReason.RUNWAY_INACTIVE, "Runway ${runway.id} is not active", events)
            !runway.accepts(state.type) -> reject(state.id, CommandRejectionReason.RUNWAY_CLASS_INCOMPATIBLE, "Runway ${runway.id} is not suitable for this aircraft class", events)
            state.operation != FlightOperation.ARRIVAL ||
                state.status !in setOf(AircraftStatus.INBOUND, AircraftStatus.APPROACH, AircraftStatus.GO_AROUND) ->
                reject(state.id, CommandRejectionReason.ARRIVAL_REQUIRED, "Only an airborne arrival can be cleared to land", events)
            state.approachRunwayId != null && state.approachRunwayId != runway.id ->
                reject(state.id, CommandRejectionReason.RUNWAY_ASSIGNMENT_MISMATCH, "Aircraft is assigned an approach to runway ${state.approachRunwayId}", events)
            scenario.mechanicVersions.runwayProcedures > 0 &&
                physicalOccupant(runway) != null && physicalOccupant(runway) != state.id ->
                reject(state.id, CommandRejectionReason.RUNWAY_OCCUPIED, "Physical runway ${runway.physicalRunwayId} is occupied", events)
            scenario.mechanicVersions.runwayProcedures > 0 && hasReciprocalConflict(runway, state.id) ->
                reject(state.id, CommandRejectionReason.RUNWAY_RECIPROCAL_CONFLICT, "Reciprocal runway end is in use", events)
            else -> {
                val clearance = Clearance.Land(runway.id)
                aircraft[state.id] = state.copy(
                    clearance = clearance,
                    runwayId = runway.id,
                    approachRunwayId = state.approachRunwayId ?: runway.id,
                )
                events += GameEvent.ClearanceIssued(state.id, clearance, elapsedSeconds)
            }
        }
    }

    private fun clearForTakeoff(command: PlayerCommand.ClearForTakeoff, events: MutableList<GameEvent>) {
        if (!canAcceptFlightCommand(command.aircraftId, events)) return
        val state = aircraft.getValue(command.aircraftId)
        val runway = runways[command.runwayId]
        when {
            runway == null -> reject(state.id, CommandRejectionReason.UNKNOWN_RUNWAY, "Unknown runway ${command.runwayId}", events)
            !runway.active -> reject(state.id, CommandRejectionReason.RUNWAY_INACTIVE, "Runway ${runway.id} is not active", events)
            !runway.accepts(state.type) -> reject(state.id, CommandRejectionReason.RUNWAY_CLASS_INCOMPATIBLE, "Runway ${runway.id} is not suitable for this aircraft class", events)
            state.operation != FlightOperation.DEPARTURE || state.status !in setOf(AircraftStatus.HOLDING_SHORT, AircraftStatus.LINED_UP) ->
                reject(state.id, CommandRejectionReason.DEPARTURE_REQUIRED, "Aircraft is not holding short or lined up for departure", events)
            state.runwayId != null && state.runwayId != runway.id ->
                reject(state.id, CommandRejectionReason.RUNWAY_ASSIGNMENT_MISMATCH, "Aircraft is assigned to runway ${state.runwayId}", events)
            physicalOccupant(runway) != null && physicalOccupant(runway) != state.id ->
                reject(state.id, CommandRejectionReason.RUNWAY_OCCUPIED, "Runway ${runway.id} is occupied", events)
            else -> {
                val previous = lastRunwayMovement[runway.physicalRunwayId]
                if (scenario.mechanicVersions.wakeTurbulence > 0 && previous != null) {
                    val actual = elapsedSeconds - previous.elapsedSeconds
                    val required = WakeSeparationRules.requiredSeconds(
                        previous.aircraftType,
                        state.type,
                        scenario.mechanicVersions.wakeTurbulence,
                    )
                    if (actual + EPSILON < required) {
                        events += GameEvent.WakeWarning(
                            previous.aircraftId,
                            state.id,
                            required,
                            actual,
                            elapsedSeconds,
                        )
                        reject(
                            state.id,
                            CommandRejectionReason.WAKE_SPACING_INSUFFICIENT,
                            "Wake spacing requires ${required.roundToInt()} seconds; ${actual.roundToInt()} seconds are available",
                            events,
                        )
                        return
                    }
                }
                val clearance = Clearance.Takeoff(runway.id)
                occupyPhysicalRunway(runway, state.id, events)
                aircraft[state.id] = state.copy(
                    status = AircraftStatus.TAKEOFF_ROLL,
                    position = runway.threshold,
                    headingDegrees = Navigation.normalizeHeading(runway.headingDegrees),
                    speedKnots = 0.0,
                    clearance = clearance,
                    runwayId = runway.id,
                    statusElapsedSeconds = 0.0,
                )
                events += GameEvent.ClearanceIssued(state.id, clearance, elapsedSeconds)
            }
        }
    }

    private fun assignRunway(command: PlayerCommand.AssignRunway, events: MutableList<GameEvent>) {
        if (!requireRunwayProcedures(command.aircraftId, events)) return
        if (!canAcceptFlightCommand(command.aircraftId, events)) return
        val state = aircraft.getValue(command.aircraftId)
        val runway = validateRunwayFor(state, command.runwayId, events) ?: return
        if (state.status in setOf(AircraftStatus.TAKEOFF_ROLL, AircraftStatus.LANDING)) {
            reject(state.id, CommandRejectionReason.AIRCRAFT_STATE_INELIGIBLE, "Runway cannot be changed during a runway roll", events)
            return
        }
        aircraft[state.id] = state.copy(
            runwayId = runway.id,
            approachRunwayId = state.approachRunwayId?.takeIf { it == runway.id },
            clearance = if (state.clearance.referencesRunway(runway.id)) state.clearance else Clearance.None,
        )
        events += GameEvent.RunwayAssigned(state.id, runway.id, elapsedSeconds)
    }

    private fun assignApproach(command: PlayerCommand.AssignApproach, events: MutableList<GameEvent>) {
        if (!requireRunwayProcedures(command.aircraftId, events)) return
        if (!canAcceptFlightCommand(command.aircraftId, events)) return
        val state = aircraft.getValue(command.aircraftId)
        if (state.operation != FlightOperation.ARRIVAL) {
            reject(state.id, CommandRejectionReason.ARRIVAL_REQUIRED, "Only an arrival can be assigned an approach", events)
            return
        }
        val runway = validateRunwayFor(state, command.runwayId, events) ?: return
        val clearance = Clearance.Approach(runway.id)
        aircraft[state.id] = state.copy(
            runwayId = runway.id,
            approachRunwayId = runway.id,
            clearance = clearance,
        )
        events += GameEvent.ApproachAssigned(state.id, runway.id, elapsedSeconds)
        events += GameEvent.ClearanceIssued(state.id, clearance, elapsedSeconds)
    }

    private fun cancelApproach(aircraftId: String, events: MutableList<GameEvent>) {
        if (!requireRunwayProcedures(aircraftId, events)) return
        if (!canAcceptFlightCommand(aircraftId, events)) return
        val state = aircraft.getValue(aircraftId)
        if (state.operation != FlightOperation.ARRIVAL || state.approachRunwayId == null) {
            reject(state.id, CommandRejectionReason.APPROACH_NOT_ASSIGNED, "Aircraft has no assigned approach", events)
            return
        }
        aircraft[state.id] = state.copy(
            approachRunwayId = null,
            clearance = Clearance.None,
            status = if (state.status == AircraftStatus.APPROACH) AircraftStatus.INBOUND else state.status,
        )
        events += GameEvent.ApproachAssigned(state.id, null, elapsedSeconds)
        events += GameEvent.ClearanceCancelled(state.id, "APPROACH", elapsedSeconds)
    }

    private fun lineUpAndWait(command: PlayerCommand.LineUpAndWait, events: MutableList<GameEvent>) {
        if (!requireRunwayProcedures(command.aircraftId, events)) return
        if (!canAcceptFlightCommand(command.aircraftId, events)) return
        val state = aircraft.getValue(command.aircraftId)
        val runway = validateRunwayFor(state, command.runwayId, events) ?: return
        when {
            state.operation != FlightOperation.DEPARTURE || state.status != AircraftStatus.HOLDING_SHORT ->
                reject(state.id, CommandRejectionReason.DEPARTURE_REQUIRED, "Aircraft is not holding short", events)
            state.runwayId != null && state.runwayId != runway.id ->
                reject(state.id, CommandRejectionReason.RUNWAY_ASSIGNMENT_MISMATCH, "Aircraft is assigned to runway ${state.runwayId}", events)
            physicalOccupant(runway) != null ->
                reject(state.id, CommandRejectionReason.RUNWAY_OCCUPIED, "Runway ${runway.id} is occupied", events)
            else -> {
                val clearance = Clearance.LineUpAndWait(runway.id)
                occupyPhysicalRunway(runway, state.id, events)
                aircraft[state.id] = state.copy(
                    status = AircraftStatus.LINED_UP,
                    position = runway.threshold,
                    headingDegrees = Navigation.normalizeHeading(runway.headingDegrees),
                    runwayId = runway.id,
                    clearance = clearance,
                    statusElapsedSeconds = 0.0,
                )
                events += GameEvent.ClearanceIssued(state.id, clearance, elapsedSeconds)
            }
        }
    }

    private fun cancelLanding(aircraftId: String, events: MutableList<GameEvent>) {
        if (!requireRunwayProcedures(aircraftId, events)) return
        if (!canAcceptFlightCommand(aircraftId, events)) return
        val state = aircraft.getValue(aircraftId)
        if (state.clearance !is Clearance.Land) {
            reject(state.id, CommandRejectionReason.CLEARANCE_NOT_ACTIVE, "Aircraft has no landing clearance", events)
            return
        }
        aircraft[state.id] = state.copy(
            clearance = state.approachRunwayId?.let(Clearance::Approach) ?: Clearance.None,
        )
        events += GameEvent.ClearanceCancelled(state.id, "LANDING", elapsedSeconds)
    }

    private fun cancelTakeoff(aircraftId: String, events: MutableList<GameEvent>) {
        if (!requireRunwayProcedures(aircraftId, events)) return
        if (!canAcceptFlightCommand(aircraftId, events)) return
        val state = aircraft.getValue(aircraftId)
        if (state.status != AircraftStatus.LINED_UP && state.status != AircraftStatus.TAKEOFF_ROLL) {
            reject(state.id, CommandRejectionReason.CLEARANCE_NOT_ACTIVE, "Aircraft has no take-off clearance", events)
            return
        }
        val runway = state.runwayId?.let(runways::get)
        if (state.status == AircraftStatus.TAKEOFF_ROLL && state.speedKnots > state.type.takeoffSpeedKnots * 0.5) {
            reject(state.id, CommandRejectionReason.AIRCRAFT_STATE_INELIGIBLE, "Take-off roll is too advanced to cancel", events)
            return
        }
        aircraft[state.id] = state.copy(
            status = AircraftStatus.LINED_UP,
            speedKnots = 0.0,
            clearance = runway?.let { Clearance.LineUpAndWait(it.id) } ?: Clearance.None,
            statusElapsedSeconds = 0.0,
        )
        events += GameEvent.ClearanceCancelled(state.id, "TAKE_OFF", elapsedSeconds)
    }

    private fun validateRunwayFor(
        state: AircraftState,
        runwayId: String,
        events: MutableList<GameEvent>,
    ): RunwayState? {
        val runway = runways[runwayId]
        when {
            runway == null -> reject(state.id, CommandRejectionReason.UNKNOWN_RUNWAY, "Unknown runway $runwayId", events)
            !runway.active -> reject(state.id, CommandRejectionReason.RUNWAY_INACTIVE, "Runway $runwayId is not active", events)
            !runway.accepts(state.type) -> reject(state.id, CommandRejectionReason.RUNWAY_CLASS_INCOMPATIBLE, "Runway $runwayId is not suitable for this aircraft class", events)
            physicalOccupant(runway) != null && physicalOccupant(runway) != state.id ->
                reject(state.id, CommandRejectionReason.RUNWAY_OCCUPIED, "Physical runway ${runway.physicalRunwayId} is occupied", events)
            hasReciprocalConflict(runway, state.id) ->
                reject(state.id, CommandRejectionReason.RUNWAY_RECIPROCAL_CONFLICT, "Reciprocal runway end is in use", events)
            scenario.mechanicVersions.windDrift > 0 &&
                !WeatherOperations.isRunwaySuitable(scenario.weather, runway, state.type) ->
                reject(state.id, CommandRejectionReason.RUNWAY_WIND_UNSUITABLE, "Crosswind exceeds this aircraft class limit on runway $runwayId", events)
            else -> return runway
        }
        return null
    }

    private fun requireRunwayProcedures(
        aircraftId: String,
        events: MutableList<GameEvent>,
    ): Boolean {
        if (scenario.mechanicVersions.runwayProcedures > 0) return true
        reject(
            aircraftId,
            CommandRejectionReason.MECHANIC_NOT_ENABLED,
            "Explicit runway procedures are not enabled for this scenario",
            events,
        )
        return false
    }

    private fun playerGoAround(command: PlayerCommand.GoAround, events: MutableList<GameEvent>) {
        if (!canAcceptFlightCommand(command.aircraftId, events)) return
        val state = aircraft.getValue(command.aircraftId)
        if (state.operation != FlightOperation.ARRIVAL ||
            state.status !in setOf(AircraftStatus.INBOUND, AircraftStatus.APPROACH, AircraftStatus.GO_AROUND)
        ) {
            reject(state.id, CommandRejectionReason.ARRIVAL_REQUIRED, "Only an airborne arrival can go around", events)
            return
        }
        if (!command.targetAltitudeFeet.isFinite() || command.targetAltitudeFeet !in 1_000.0..10_000.0) {
            reject(state.id, CommandRejectionReason.INVALID_GO_AROUND_ALTITUDE, "Go-around altitude must be between 1,000 and 10,000 feet", events)
            return
        }
        performGoAround(state, command.targetAltitudeFeet, automatic = false, events = events)
    }

    private fun stepOnce(events: MutableList<GameEvent>) {
        tick += 1
        elapsedSeconds = tick * parameters.fixedStepSeconds
        spawnDueAircraft(events)

        for (id in aircraft.keys.toList()) {
            if (status != GameStatus.RUNNING) break
            aircraft[id]?.let { current -> aircraft[id] = simulateAircraft(current, events) }
        }

        if (status == GameStatus.RUNNING) evaluateWakeSpacing(events)
        if (status == GameStatus.RUNNING) evaluateConflicts(events)
        if (status == GameStatus.RUNNING) evaluateObjectivesAndTime(events)
    }

    private fun spawnDueAircraft(events: MutableList<GameEvent>) {
        while (nextSpawnIndex < resolvedTraffic.size &&
            resolvedTraffic[nextSpawnIndex].timeSeconds <= elapsedSeconds + EPSILON
        ) {
            val resolved = resolvedTraffic[nextSpawnIndex++]
            val state = resolved.aircraft.deepCopy()
            aircraft[state.id] = state
            spawnTimes[state.id] = elapsedSeconds
            minimumDistances[state.id] = minimumUsefulDistance(state)
            events += GameEvent.AircraftSpawned(state.id, elapsedSeconds)
        }
    }

    private fun simulateAircraft(
        original: AircraftState,
        events: MutableList<GameEvent>,
    ): AircraftState {
        if (!original.status.consumesFuel()) return original
        val remainingFuel = (original.fuelRemainingSeconds - parameters.fixedStepSeconds)
            .coerceAtLeast(0.0)
        val fueled = original.copy(fuelRemainingSeconds = remainingFuel)
        if (remainingFuel <= EPSILON) {
            fueled.runwayId?.let { clearRunwayIfOccupiedBy(it, fueled.id) }
            fail(FailureReason.FUEL_EXHAUSTED, events)
            return fueled.copy(
                status = AircraftStatus.CRASHED,
                speedKnots = 0.0,
                targetSpeedKnots = 0.0,
                clearance = Clearance.None,
            )
        }
        return when (fueled.status) {
            AircraftStatus.TAKEOFF_ROLL -> simulateTakeoffRoll(fueled, events)
            AircraftStatus.LANDING -> simulateLandingRoll(fueled, events)
            AircraftStatus.HOLDING_SHORT,
            AircraftStatus.LINED_UP -> fueled
            AircraftStatus.INBOUND,
            AircraftStatus.APPROACH,
            AircraftStatus.GO_AROUND,
            AircraftStatus.DEPARTING -> simulateAirborne(fueled, events)
            else -> fueled
        }
    }

    private fun simulateTakeoffRoll(
        state: AircraftState,
        events: MutableList<GameEvent>,
    ): AircraftState {
        val runway = state.runwayId?.let(runways::get) ?: return state
        val dt = parameters.fixedStepSeconds
        val speed = moveTowards(
            state.speedKnots,
            state.type.takeoffSpeedKnots,
            state.type.accelerationKnotsPerSecond * dt,
        )
        val distance = speed * dt / 3_600.0
        val position = Navigation.move(
            state.position,
            runway.headingDegrees,
            distance,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        )
        val elapsed = state.statusElapsedSeconds + dt
        if (elapsed + EPSILON < state.type.takeoffRollSeconds) {
            return state.copy(
                position = position,
                headingDegrees = Navigation.normalizeHeading(runway.headingDegrees),
                speedKnots = speed,
                distanceTravelledNm = state.distanceTravelledNm + distance,
                statusElapsedSeconds = elapsed,
            )
        }

        clearRunwayIfOccupiedBy(runway.id, state.id)
        events += GameEvent.Takeoff(state.id, runway.id, elapsedSeconds)
        lastRunwayMovement[runway.physicalRunwayId] = RunwayMovement(
            state.id,
            state.type,
            elapsedSeconds,
        )
        return state.copy(
            status = AircraftStatus.DEPARTING,
            position = position,
            headingDegrees = Navigation.normalizeHeading(runway.headingDegrees),
            altitudeFeet = 50.0,
            speedKnots = max(speed, state.type.takeoffSpeedKnots),
            targetAltitudeFeet = max(state.targetAltitudeFeet, 3_000.0),
            targetSpeedKnots = max(state.targetSpeedKnots, state.type.takeoffSpeedKnots + 40.0)
                .coerceAtMost(state.type.maxSpeedKnots),
            clearance = Clearance.None,
            distanceTravelledNm = state.distanceTravelledNm + distance,
            statusElapsedSeconds = 0.0,
        )
    }

    private fun simulateLandingRoll(
        state: AircraftState,
        events: MutableList<GameEvent>,
    ): AircraftState {
        val runway = state.runwayId?.let(runways::get) ?: return state
        val dt = parameters.fixedStepSeconds
        val speed = (state.speedKnots - state.type.accelerationKnotsPerSecond * 1.5 * dt).coerceAtLeast(0.0)
        val distance = speed * dt / 3_600.0
        val position = Navigation.move(
            state.position,
            runway.headingDegrees,
            distance,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        )
        val elapsed = state.statusElapsedSeconds + dt
        val occupancyMultiplier = if (scenario.mechanicVersions.reducedVisibility > 0) {
            WeatherOperations.runwayOccupancyMultiplier(scenario.weather.visibilityKm)
        } else {
            1.0
        }
        if (elapsed + EPSILON < state.type.landingRollSeconds * occupancyMultiplier) {
            return state.copy(
                position = position,
                headingDegrees = Navigation.normalizeHeading(runway.headingDegrees),
                altitudeFeet = 0.0,
                speedKnots = speed,
                distanceTravelledNm = state.distanceTravelledNm + distance,
                statusElapsedSeconds = elapsed,
            )
        }

        clearRunwayIfOccupiedBy(runway.id, state.id)
        awardArrival(state.copy(distanceTravelledNm = state.distanceTravelledNm + distance))
        events += GameEvent.Landed(state.id, runway.id, elapsedSeconds)
        return state.copy(
            status = AircraftStatus.LANDED,
            position = position,
            altitudeFeet = 0.0,
            speedKnots = 0.0,
            targetAltitudeFeet = 0.0,
            targetSpeedKnots = 0.0,
            clearance = Clearance.None,
            distanceTravelledNm = state.distanceTravelledNm + distance,
            statusElapsedSeconds = 0.0,
        )
    }

    private fun simulateAirborne(
        state: AircraftState,
        events: MutableList<GameEvent>,
    ): AircraftState {
        val dt = parameters.fixedStepSeconds
        val speed = moveTowards(
            state.speedKnots,
            state.targetSpeedKnots.coerceAtMost(state.type.maxSpeedKnots),
            state.type.accelerationKnotsPerSecond * dt,
        )
        val altitudeRate = if (state.targetAltitudeFeet >= state.altitudeFeet) {
            state.type.climbRateFeetPerMinute / 60.0
        } else {
            state.type.descentRateFeetPerMinute / 60.0
        }
        val altitude = moveTowards(state.altitudeFeet, state.targetAltitudeFeet, altitudeRate * dt)
        val navigated = navigateRoute(state, speed, dt)
        var updated = state.copy(
            position = navigated.position,
            headingDegrees = navigated.headingDegrees,
            altitudeFeet = altitude,
            speedKnots = speed,
            routeIndex = navigated.routeIndex,
            distanceTravelledNm = state.distanceTravelledNm + navigated.distanceTravelledNm,
            statusElapsedSeconds = state.statusElapsedSeconds + dt,
        )

        if (updated.operation == FlightOperation.ARRIVAL && updated.clearance is Clearance.Land) {
            updated = evaluateApproach(updated, events)
        }
        if (updated.operation == FlightOperation.DEPARTURE && updated.status == AircraftStatus.DEPARTING) {
            updated = evaluateDepartureExit(updated, events)
        } else if (updated.operation == FlightOperation.ARRIVAL && isLeavingMap(updated)) {
            events += GameEvent.AircraftLeftAirspace(updated.id, elapsedSeconds)
            updated = updated.copy(status = AircraftStatus.EXITED, clearance = Clearance.None)
            issueStrike(setOf(updated.id), events)
            fail(FailureReason.AIRCRAFT_LOST, events)
        }
        return updated
    }

    private fun navigateRoute(state: AircraftState, speedKnots: Double, dt: Double): NavigationResult {
        var position = state.position
        var heading = Navigation.normalizeHeading(state.headingDegrees)
        var routeIndex = state.routeIndex.coerceAtMost(state.route.waypoints.size)
        while (routeIndex < state.route.waypoints.size &&
            Navigation.distanceNm(
                position,
                state.route.waypoints[routeIndex],
                scenario.mapWidthNm,
                scenario.mapHeightNm,
            ) <= parameters.waypointCaptureNm
        ) {
            routeIndex++
        }
        if (routeIndex < state.route.waypoints.size) {
            val targetHeading = Navigation.bearingDegrees(
                position,
                state.route.waypoints[routeIndex],
                scenario.mapWidthNm,
                scenario.mapHeightNm,
            )
            heading = Navigation.turnTowards(
                heading,
                targetHeading,
                state.type.turnRateDegreesPerSecond * dt,
            )
        }
        val requestedDistance = speedKnots * dt / 3_600.0
        val movedPosition = Navigation.move(
            position,
            heading,
            requestedDistance,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        )
        val driftedPosition = if (scenario.mechanicVersions.windDrift > 0 && scenario.weather.windSpeedKnots > 0.0) {
            // Wind direction is the meteorological direction it comes from.
            Navigation.move(
                movedPosition,
                scenario.weather.windDirectionDegrees + 180.0,
                scenario.weather.windSpeedKnots * dt / 3_600.0,
                scenario.mapWidthNm,
                scenario.mapHeightNm,
            )
        } else {
            movedPosition
        }
        val actualDistance = Navigation.distanceNm(
            position,
            driftedPosition,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        )
        position = driftedPosition
        while (routeIndex < state.route.waypoints.size &&
            Navigation.distanceNm(
                position,
                state.route.waypoints[routeIndex],
                scenario.mapWidthNm,
                scenario.mapHeightNm,
            ) <= parameters.waypointCaptureNm
        ) {
            routeIndex++
        }
        return NavigationResult(position, heading, routeIndex, actualDistance)
    }

    private fun evaluateApproach(
        state: AircraftState,
        events: MutableList<GameEvent>,
    ): AircraftState {
        val clearance = state.clearance as Clearance.Land
        val runway = runways[clearance.runwayId] ?: return state
        val distanceToThreshold = Navigation.distanceNm(
            state.position,
            runway.threshold,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        )
        val lateralDistance = Navigation.distanceToRunwayCentrelineNm(
            state.position,
            runway,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        )
        val headingError = abs(
            Navigation.signedHeadingDifference(state.headingDegrees, runway.headingDegrees),
        )
        val onApproachSide = Navigation.signedDistanceAlongRunwayNm(
            state.position,
            runway,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        ) <= 0.0
        val movingTowardThreshold = Navigation.isMovingToward(
            position = state.position,
            target = runway.threshold,
            headingDegrees = state.headingDegrees,
            speedKnots = state.speedKnots,
            mapWidthNm = scenario.mapWidthNm,
            mapHeightNm = scenario.mapHeightNm,
        )
        val inCapture = distanceToThreshold <= parameters.approachCaptureDistanceNm &&
            lateralDistance <= parameters.approachLateralToleranceNm &&
            headingError <= parameters.approachHeadingToleranceDegrees &&
            onApproachSide &&
            movingTowardThreshold
        if (scenario.mechanicVersions.reducedVisibility > 0) {
            val gate = WeatherOperations.stabilizedApproachGateNm(scenario.weather.visibilityKm)
            val stabilized = inCapture &&
                state.speedKnots <= state.type.maxLandingSpeedKnots + 10.0 &&
                state.altitudeFeet <= max(parameters.maxTouchdownAltitudeFeet, distanceToThreshold * 900.0)
            if (distanceToThreshold <= gate && !stabilized) {
                return performGoAround(state, 3_000.0, automatic = true, events = events)
            }
        }
        var updated = if (inCapture) {
            state.copy(status = AircraftStatus.APPROACH)
        } else if (state.status == AircraftStatus.APPROACH) {
            state.copy(status = AircraftStatus.INBOUND)
        } else {
            state
        }

        if (distanceToThreshold > parameters.touchdownRadiusNm) return updated
        val safeToTouchDown = runway.active && inCapture &&
            state.altitudeFeet <= parameters.maxTouchdownAltitudeFeet &&
            state.speedKnots <= state.type.maxLandingSpeedKnots + 10.0
        if (!safeToTouchDown) {
            return performGoAround(updated, 3_000.0, automatic = true, events = events)
        }
        val physicalOccupant = physicalOccupant(runway)
        if (physicalOccupant != null && physicalOccupant != state.id) {
            val occupants = setOf(state.id, physicalOccupant)
            events += GameEvent.RunwayIncursion(runway.id, occupants, elapsedSeconds)
            aircraft[physicalOccupant]?.let { occupant ->
                aircraft[occupant.id] = occupant.copy(status = AircraftStatus.CRASHED)
            }
            fail(FailureReason.RUNWAY_INCURSION, events)
            return updated.copy(status = AircraftStatus.CRASHED)
        }

        occupyPhysicalRunway(runway, state.id, events)
        lastRunwayMovement[runway.physicalRunwayId] = RunwayMovement(
            state.id,
            state.type,
            elapsedSeconds,
        )
        events += GameEvent.Touchdown(state.id, runway.id, elapsedSeconds)
        return updated.copy(
            status = AircraftStatus.LANDING,
            position = runway.threshold,
            headingDegrees = Navigation.normalizeHeading(runway.headingDegrees),
            altitudeFeet = 0.0,
            targetAltitudeFeet = 0.0,
            runwayId = runway.id,
            statusElapsedSeconds = 0.0,
        )
    }

    private fun performGoAround(
        state: AircraftState,
        targetAltitudeFeet: Double,
        automatic: Boolean,
        events: MutableList<GameEvent>,
    ): AircraftState {
        val runwayHeading = state.runwayId?.let(runways::get)?.headingDegrees ?: state.headingDegrees
        val clearance = Clearance.GoAround(targetAltitudeFeet)
        val updated = state.copy(
            status = AircraftStatus.GO_AROUND,
            headingDegrees = Navigation.normalizeHeading(runwayHeading),
            targetAltitudeFeet = max(targetAltitudeFeet, state.altitudeFeet),
            targetSpeedKnots = max(state.targetSpeedKnots, state.type.maxLandingSpeedKnots + 30.0)
                .coerceAtMost(state.type.maxSpeedKnots),
            route = Route.EMPTY,
            routeIndex = 0,
            clearance = clearance,
            statusElapsedSeconds = 0.0,
        )
        aircraft[state.id] = updated
        val penalty = if (automatic) {
            scenario.scoring.automaticGoAroundPenaltyPoints
        } else {
            scenario.scoring.manualGoAroundPenaltyPoints
        }
        score = score.copy(penalties = saturatingAdd(score.penalties, penalty))
        score = score.copy(goAroundPenaltyPoints = saturatingAdd(score.goAroundPenaltyPoints, penalty))
        events += GameEvent.ClearanceIssued(state.id, clearance, elapsedSeconds)
        events += GameEvent.GoAround(state.id, automatic, elapsedSeconds)
        return updated
    }

    private fun evaluateDepartureExit(
        state: AircraftState,
        events: MutableList<GameEvent>,
    ): AircraftState {
        val desiredExit = state.exitPoint
        val atCorrectExit = desiredExit == null || Navigation.distanceNm(
            state.position,
            desiredExit,
            scenario.mapWidthNm,
            scenario.mapHeightNm,
        ) <= parameters.exitCaptureNm
        if (!atCorrectExit && !isLeavingMap(state)) return state
        val correct = atCorrectExit
        val exited = state.copy(status = AircraftStatus.EXITED, clearance = Clearance.None)
        awardDeparture(exited, correct)
        events += GameEvent.AircraftExited(state.id, correct, elapsedSeconds)
        return exited
    }

    private fun evaluateConflicts(events: MutableList<GameEvent>) {
        val airborne = aircraft.values.filter { it.isSubjectToSeparation() }.sortedBy { it.id }
        val conflicts = mutableListOf<Conflict>()
        val lossPairs = mutableSetOf<PairKey>()
        val predictedPairs = mutableSetOf<PairKey>()
        var collision: Pair<Conflict, PairKey>? = null

        for (firstIndex in 0 until airborne.lastIndex) {
            for (secondIndex in firstIndex + 1 until airborne.size) {
                val first = airborne[firstIndex]
                val second = airborne[secondIndex]
                val key = PairKey.of(first.id, second.id)
                val currentHorizontal = Navigation.distanceNm(
                    first.position,
                    second.position,
                    scenario.mapWidthNm,
                    scenario.mapHeightNm,
                )
                val currentVertical = abs(first.altitudeFeet - second.altitudeFeet)
                when {
                    currentHorizontal < parameters.collisionHorizontalNm &&
                        currentVertical < parameters.collisionVerticalFeet -> {
                        val conflict = Conflict(
                            key.first,
                            key.second,
                            ConflictKind.COLLISION,
                            currentHorizontal,
                            currentVertical,
                            0.0,
                        )
                        conflicts += conflict
                        if (collision == null) collision = conflict to key
                    }
                    currentHorizontal < parameters.horizontalSeparationNm &&
                        currentVertical < parameters.verticalSeparationFeet -> {
                        val conflict = Conflict(
                            key.first,
                            key.second,
                            ConflictKind.LOSS_OF_SEPARATION,
                            currentHorizontal,
                            currentVertical,
                            0.0,
                        )
                        conflicts += conflict
                        lossPairs += key
                        if (key !in previousLossPairs) {
                            events += GameEvent.SeparationLost(conflict, elapsedSeconds)
                            issueStrike(setOf(key.first, key.second), events)
                        }
                    }
                    else -> {
                        val predicted = predictConflict(first, second, key)
                        if (predicted != null) {
                            conflicts += predicted
                            predictedPairs += key
                            if (key !in previousPredictedPairs && key !in previousLossPairs) {
                                events += GameEvent.ConflictWarning(predicted, elapsedSeconds)
                            }
                        }
                    }
                }
            }
        }
        currentConflicts = conflicts.sortedWith(
            compareBy<Conflict> { it.timeToClosestApproachSeconds }
                .thenBy { it.firstAircraftId }
                .thenBy { it.secondAircraftId },
        )
        previousLossPairs = lossPairs
        previousPredictedPairs = predictedPairs

        collision?.let { (conflict, key) ->
            aircraft[key.first]?.let { aircraft[key.first] = it.copy(status = AircraftStatus.CRASHED) }
            aircraft[key.second]?.let { aircraft[key.second] = it.copy(status = AircraftStatus.CRASHED) }
            events += GameEvent.Collision(conflict, elapsedSeconds)
            fail(FailureReason.COLLISION, events)
        }
    }

    private fun evaluateWakeSpacing(events: MutableList<GameEvent>) {
        val spacing = currentWakeSpacing()
        val warnings = mutableSetOf<PairKey>()
        val violations = mutableSetOf<PairKey>()
        spacing.forEach { item ->
            val key = PairKey.of(item.leaderAircraftId, item.followerAircraftId)
            val follower = aircraft[item.followerAircraftId]
            val isFinalSequence = follower?.operation == FlightOperation.ARRIVAL
            when {
                item.violation && isFinalSequence -> {
                    violations += key
                    if (key !in previousWakeViolationPairs) {
                        events += GameEvent.WakeViolation(
                            item.leaderAircraftId,
                            item.followerAircraftId,
                            item.requiredSeconds,
                            item.actualSeconds,
                            elapsedSeconds,
                        )
                        val penalty = scenario.scoring.wakeViolationPenaltyPoints
                        score = score.copy(
                            penalties = saturatingAdd(score.penalties, penalty),
                            wakePenaltyPoints = saturatingAdd(score.wakePenaltyPoints, penalty),
                        )
                    }
                }
                item.actualSeconds < item.requiredSeconds + WAKE_WARNING_MARGIN_SECONDS -> {
                    warnings += key
                    if (key !in previousWakeWarningPairs && key !in previousWakeViolationPairs) {
                        events += GameEvent.WakeWarning(
                            item.leaderAircraftId,
                            item.followerAircraftId,
                            item.requiredSeconds,
                            item.actualSeconds,
                            elapsedSeconds,
                        )
                    }
                }
            }
        }
        previousWakeWarningPairs = warnings
        previousWakeViolationPairs = violations
    }

    private fun predictConflict(
        first: AircraftState,
        second: AircraftState,
        key: PairKey,
    ): Conflict? {
        val relativeX = (second.position.x - first.position.x) * scenario.mapWidthNm
        val relativeY = (second.position.y - first.position.y) * scenario.mapHeightNm
        val firstVelocity = Navigation.velocityNmPerSecond(first.headingDegrees, first.speedKnots)
        val secondVelocity = Navigation.velocityNmPerSecond(second.headingDegrees, second.speedKnots)
        val relativeVelocityX = secondVelocity.first - firstVelocity.first
        val relativeVelocityY = secondVelocity.second - firstVelocity.second
        val velocitySquared = relativeVelocityX * relativeVelocityX + relativeVelocityY * relativeVelocityY
        val horizontalInterval = horizontalConflictInterval(
            relativeX = relativeX,
            relativeY = relativeY,
            relativeVelocityX = relativeVelocityX,
            relativeVelocityY = relativeVelocityY,
        ) ?: return null
        val verticalIntervals = verticalConflictIntervals(first, second)
        val overlap = verticalIntervals.firstNotNullOfOrNull { verticalInterval ->
            horizontalInterval.intersection(verticalInterval)?.takeIf { it.end - it.start > EPSILON }
        } ?: return null

        val unconstrainedHorizontalClosestTime = if (velocitySquared <= EPSILON) {
            overlap.start
        } else {
            -(relativeX * relativeVelocityX + relativeY * relativeVelocityY) / velocitySquared
        }
        var conflictTime = unconstrainedHorizontalClosestTime.coerceIn(overlap.start, overlap.end)
        // The interval boundaries are the exact separation minima and are therefore safe. Keep
        // the reported point inside the open overlap, including for equal horizontal velocities.
        if (conflictTime <= overlap.start + EPSILON || conflictTime >= overlap.end - EPSILON) {
            conflictTime = (overlap.start + overlap.end) / 2.0
        }
        if (conflictTime <= EPSILON) return null

        val conflictHorizontal = hypot(
            relativeX + relativeVelocityX * conflictTime,
            relativeY + relativeVelocityY * conflictTime,
        )
        val conflictVertical = abs(
            projectAltitude(first, conflictTime) - projectAltitude(second, conflictTime),
        )
        if (conflictHorizontal >= parameters.horizontalSeparationNm ||
            conflictVertical >= parameters.verticalSeparationFeet
        ) return null
        return Conflict(
            key.first,
            key.second,
            ConflictKind.PREDICTED,
            conflictHorizontal,
            conflictVertical,
            conflictTime,
        )
    }

    private fun horizontalConflictInterval(
        relativeX: Double,
        relativeY: Double,
        relativeVelocityX: Double,
        relativeVelocityY: Double,
    ): TimeInterval? {
        val horizon = parameters.predictionHorizonSeconds
        val velocitySquared = relativeVelocityX * relativeVelocityX + relativeVelocityY * relativeVelocityY
        val separationSquared = parameters.horizontalSeparationNm * parameters.horizontalSeparationNm
        if (velocitySquared <= EPSILON) {
            val currentSquared = relativeX * relativeX + relativeY * relativeY
            return if (currentSquared < separationSquared) TimeInterval(0.0, horizon) else null
        }

        val linear = 2.0 * (relativeX * relativeVelocityX + relativeY * relativeVelocityY)
        val constant = relativeX * relativeX + relativeY * relativeY - separationSquared
        val discriminant = linear * linear - 4.0 * velocitySquared * constant
        if (discriminant <= 0.0) return null
        val root = sqrt(discriminant)
        val firstRoot = (-linear - root) / (2.0 * velocitySquared)
        val secondRoot = (-linear + root) / (2.0 * velocitySquared)
        val start = max(0.0, min(firstRoot, secondRoot))
        val end = min(horizon, max(firstRoot, secondRoot))
        return if (end - start > EPSILON) TimeInterval(start, end) else null
    }

    private fun verticalConflictIntervals(
        first: AircraftState,
        second: AircraftState,
    ): List<TimeInterval> {
        val horizon = parameters.predictionHorizonSeconds
        val breakpoints = buildList {
            add(0.0)
            add(horizon)
            first.secondsUntilTargetAltitude()?.takeIf { it > EPSILON && it < horizon }?.let(::add)
            second.secondsUntilTargetAltitude()?.takeIf { it > EPSILON && it < horizon }?.let(::add)
        }.distinct().sorted()

        return breakpoints.zipWithNext().mapNotNull { (segmentStart, segmentEnd) ->
            val startDelta = projectAltitude(second, segmentStart) - projectAltitude(first, segmentStart)
            val endDelta = projectAltitude(second, segmentEnd) - projectAltitude(first, segmentEnd)
            val slope = (endDelta - startDelta) / (segmentEnd - segmentStart)
            if (abs(slope) <= EPSILON) {
                if (abs(startDelta) < parameters.verticalSeparationFeet) {
                    TimeInterval(segmentStart, segmentEnd)
                } else {
                    null
                }
            } else {
                val negativeBoundary = segmentStart +
                    (-parameters.verticalSeparationFeet - startDelta) / slope
                val positiveBoundary = segmentStart +
                    (parameters.verticalSeparationFeet - startDelta) / slope
                val start = max(segmentStart, min(negativeBoundary, positiveBoundary))
                val end = min(segmentEnd, max(negativeBoundary, positiveBoundary))
                if (end - start > EPSILON) TimeInterval(start, end) else null
            }
        }
    }

    private fun AircraftState.secondsUntilTargetAltitude(): Double? {
        val difference = abs(targetAltitudeFeet - altitudeFeet)
        if (difference <= EPSILON) return null
        val rate = if (targetAltitudeFeet > altitudeFeet) {
            type.climbRateFeetPerMinute / 60.0
        } else {
            type.descentRateFeetPerMinute / 60.0
        }
        return difference / rate
    }

    private fun projectAltitude(state: AircraftState, seconds: Double): Double {
        val feetPerSecond = if (state.targetAltitudeFeet >= state.altitudeFeet) {
            state.type.climbRateFeetPerMinute / 60.0
        } else {
            state.type.descentRateFeetPerMinute / 60.0
        }
        return moveTowards(state.altitudeFeet, state.targetAltitudeFeet, feetPerSecond * seconds)
    }

    private fun issueStrike(aircraftIds: Set<String>, events: MutableList<GameEvent>) {
        if (status != GameStatus.RUNNING) return
        strikes += 1
        score = score.copy(
            penalties = saturatingAdd(score.penalties, scenario.scoring.conflictPenaltyPoints),
            separationPenaltyPoints = saturatingAdd(
                score.separationPenaltyPoints,
                scenario.scoring.conflictPenaltyPoints,
            ),
        )
        events += GameEvent.StrikeIssued(aircraftIds, elapsedSeconds)
        if (strikes >= scenario.maxStrikes || strikes > scenario.objectives.maximumStrikes) {
            fail(FailureReason.TOO_MANY_STRIKES, events)
        }
    }

    private fun awardArrival(state: AircraftState) {
        val efficiency = efficiencyPoints(state)
        val bonus = timeBonus(state)
        score = score.copy(
            safeArrivals = score.safeArrivals + 1,
            basePoints = saturatingAdd(score.basePoints, scenario.scoring.safeArrivalPoints),
            efficiencyPoints = saturatingAdd(score.efficiencyPoints, efficiency),
            timeBonusPoints = saturatingAdd(score.timeBonusPoints, bonus),
        )
    }

    private fun awardDeparture(state: AircraftState, correctExit: Boolean) {
        val efficiency = efficiencyPoints(state)
        val bonus = timeBonus(state)
        score = score.copy(
            safeDepartures = score.safeDepartures + 1,
            basePoints = saturatingAdd(score.basePoints, scenario.scoring.safeDeparturePoints),
            efficiencyPoints = saturatingAdd(score.efficiencyPoints, efficiency),
            timeBonusPoints = saturatingAdd(score.timeBonusPoints, bonus),
            penalties = saturatingAdd(score.penalties, if (correctExit) {
                0
            } else {
                scenario.scoring.missedExitPenaltyPoints
            }),
            missedExitPenaltyPoints = saturatingAdd(
                score.missedExitPenaltyPoints,
                if (correctExit) 0 else scenario.scoring.missedExitPenaltyPoints,
            ),
        )
    }

    private fun efficiencyPoints(state: AircraftState): Int {
        val extraDistance = (state.distanceTravelledNm - minimumDistances.getOrDefault(state.id, 0.0))
            .coerceAtLeast(0.0)
        return (
            scenario.scoring.maximumRouteEfficiencyBonusPoints -
                extraDistance * scenario.scoring.routeInefficiencyPenaltyPointsPerNm
        ).roundToInt().coerceAtLeast(0)
    }

    private fun timeBonus(state: AircraftState): Int {
        val duration = elapsedSeconds - spawnTimes.getOrDefault(state.id, elapsedSeconds)
        return (
            scenario.scoring.maximumTimeBonusPoints -
                duration * scenario.scoring.timeBonusDecayPointsPerSecond
        ).roundToInt().coerceAtLeast(0)
    }

    private fun evaluateObjectivesAndTime(events: MutableList<GameEvent>) {
        val objectives = scenario.objectives
        val countsMet = score.safeArrivals + score.safeDepartures >= objectives.safeMovementsToComplete &&
            score.safeArrivals >= objectives.arrivalsToLand &&
            score.safeDepartures >= objectives.departuresToExit
        val allTrafficResolved = nextSpawnIndex == resolvedTraffic.size &&
            aircraft.values.all { it.status.isTerminal() }
        val operationalObjectivesMet = countsMet &&
            strikes <= objectives.maximumStrikes &&
            (!objectives.completeWhenAllTrafficResolved || allTrafficResolved)
        if (operationalObjectivesMet &&
            score.completionBonusPoints != scenario.scoring.completionBonusPoints
        ) {
            score = score.copy(completionBonusPoints = scenario.scoring.completionBonusPoints)
        }
        if (operationalObjectivesMet && score.total >= objectives.minimumScore) {
            status = GameStatus.COMPLETED
            accumulatorSeconds = 0.0
            events += GameEvent.ScenarioCompleted(elapsedSeconds)
        } else if (elapsedSeconds + EPSILON >= scenario.maxDurationSeconds) {
            fail(FailureReason.TIME_EXPIRED, events)
        }
    }

    private fun fail(reason: FailureReason, events: MutableList<GameEvent>) {
        if (status == GameStatus.FAILED || status == GameStatus.COMPLETED) return
        status = GameStatus.FAILED
        failureReason = reason
        accumulatorSeconds = 0.0
        events += GameEvent.ScenarioFailed(reason, elapsedSeconds)
    }

    private fun reject(
        aircraftId: String?,
        code: CommandRejectionReason,
        reason: String,
        events: MutableList<GameEvent>,
    ) {
        events += GameEvent.CommandRejected(aircraftId, reason, elapsedSeconds, code)
    }

    private fun clearRunwayIfOccupiedBy(runwayId: String, aircraftId: String) {
        val runway = runways[runwayId] ?: return
        if (physicalOccupant(runway) == aircraftId) {
            runways.entries.forEach { (id, end) ->
                if (end.physicalRunwayId == runway.physicalRunwayId) {
                    runways[id] = end.copy(occupiedByAircraftId = null)
                }
            }
        }
    }

    private fun physicalOccupant(runway: RunwayState): String? = runways.values
        .firstOrNull { it.physicalRunwayId == runway.physicalRunwayId && it.occupiedByAircraftId != null }
        ?.occupiedByAircraftId

    private fun hasReciprocalConflict(runway: RunwayState, aircraftId: String): Boolean =
        aircraft.values.any { other ->
            if (other.id == aircraftId || other.runwayId == runway.id || other.clearance is Clearance.None) {
                return@any false
            }
            val otherRunway = other.runwayId?.let(runways::get) ?: return@any false
            otherRunway.physicalRunwayId == runway.physicalRunwayId &&
                other.status !in setOf(AircraftStatus.LANDED, AircraftStatus.EXITED, AircraftStatus.CRASHED)
        }

    private fun occupyPhysicalRunway(
        runway: RunwayState,
        aircraftId: String,
        events: MutableList<GameEvent>,
    ) {
        runways.entries.forEach { (id, end) ->
            if (end.physicalRunwayId == runway.physicalRunwayId) {
                runways[id] = end.copy(occupiedByAircraftId = aircraftId)
            }
        }
        events += GameEvent.RunwayOccupied(runway.physicalRunwayId, runway.id, aircraftId, elapsedSeconds)
    }

    private fun RunwayState.accepts(type: AircraftType): Boolean =
        compatibleAircraftTypes.isEmpty() || type in compatibleAircraftTypes

    private fun Clearance.referencesRunway(runwayId: String): Boolean = when (this) {
        is Clearance.Approach -> this.runwayId == runwayId
        is Clearance.LineUpAndWait -> this.runwayId == runwayId
        is Clearance.Land -> this.runwayId == runwayId
        is Clearance.Takeoff -> this.runwayId == runwayId
        is Clearance.GoAround, Clearance.None -> true
    }

    private fun rememberEvents(events: List<GameEvent>) {
        events.forEach { event ->
            eventHistory.addLast(event)
            while (eventHistory.size > EVENT_HISTORY_CAPACITY) eventHistory.removeFirst()
        }
    }

    private fun minimumUsefulDistance(state: AircraftState): Double = when (state.operation) {
        FlightOperation.ARRIVAL -> runways.values.minOfOrNull {
            Navigation.distanceNm(state.position, it.threshold, scenario.mapWidthNm, scenario.mapHeightNm)
        } ?: 0.0
        FlightOperation.DEPARTURE -> state.exitPoint?.let {
            Navigation.distanceNm(state.position, it, scenario.mapWidthNm, scenario.mapHeightNm)
        } ?: 0.0
    }

    private fun isLeavingMap(state: AircraftState): Boolean {
        val headingRadians = Math.toRadians(Navigation.normalizeHeading(state.headingDegrees))
        val east = sin(headingRadians)
        val south = -cos(headingRadians)
        return (state.position.x <= EPSILON && east < -EPSILON) ||
            (state.position.x >= 1.0 - EPSILON && east > EPSILON) ||
            (state.position.y <= EPSILON && south < -EPSILON) ||
            (state.position.y >= 1.0 - EPSILON && south > EPSILON)
    }

    private fun createSnapshot(events: List<GameEvent>): GameSnapshot = GameSnapshot(
        scenarioId = scenario.id,
        tick = tick,
        elapsedSeconds = elapsedSeconds,
        status = status,
        failureReason = failureReason,
        speedMultiplier = speedMultiplier,
        aircraft = aircraft.values.map { it.deepCopy() },
        runways = runways.values.toList(),
        conflicts = currentConflicts.toList(),
        strikes = strikes,
        score = score,
        stars = scenario.objectives.starScoreThresholds.count { score.total >= it },
        pendingAircraftCount = resolvedTraffic.size - nextSpawnIndex,
        events = events.toList(),
        eventHistory = eventHistory.toList(),
        upcomingAircraft = resolvedTraffic.drop(nextSpawnIndex).take(3).map { spawn ->
            UpcomingAircraft(
                aircraftId = spawn.aircraft.id,
                callsign = spawn.aircraft.callsign,
                operation = spawn.aircraft.operation,
                runwayId = spawn.aircraft.runwayId,
                spawnAtSeconds = spawn.timeSeconds,
            )
        },
        objectives = scenario.objectives.copy(
            starScoreThresholds = scenario.objectives.starScoreThresholds.toList(),
        ),
        maxDurationSeconds = scenario.maxDurationSeconds,
        weather = scenario.weather,
        mechanicVersions = scenario.mechanicVersions,
        wakeSpacing = currentWakeSpacing(),
    )

    private fun currentWakeSpacing(): List<WakeSpacing> {
        if (scenario.mechanicVersions.wakeTurbulence == 0) return emptyList()
        val result = mutableListOf<WakeSpacing>()
        aircraft.values
            .filter { it.operation == FlightOperation.DEPARTURE && it.status in setOf(AircraftStatus.HOLDING_SHORT, AircraftStatus.LINED_UP) }
            .forEach { follower ->
                val runway = follower.runwayId?.let(runways::get) ?: return@forEach
                val leader = lastRunwayMovement[runway.physicalRunwayId] ?: return@forEach
                if (leader.aircraftId == follower.id) return@forEach
                val actual = (elapsedSeconds - leader.elapsedSeconds).coerceAtLeast(0.0)
                val required = WakeSeparationRules.requiredSeconds(
                    leader.aircraftType,
                    follower.type,
                    scenario.mechanicVersions.wakeTurbulence,
                )
                result += WakeSpacing(
                    leader.aircraftId,
                    follower.id,
                    runway.id,
                    required,
                    actual,
                    actual + EPSILON < required,
                )
            }
        runways.values.forEach { runway ->
            val onFinal = aircraft.values.filter { state ->
                state.operation == FlightOperation.ARRIVAL &&
                    state.approachRunwayId == runway.id &&
                    state.status in setOf(AircraftStatus.INBOUND, AircraftStatus.APPROACH)
            }.map { state ->
                state to Navigation.distanceNm(
                    state.position,
                    runway.threshold,
                    scenario.mapWidthNm,
                    scenario.mapHeightNm,
                )
            }.sortedBy { it.second }
            onFinal.zipWithNext().forEach { (leaderPair, followerPair) ->
                val leader = leaderPair.first
                val follower = followerPair.first
                val leaderEta = leaderPair.second / leader.speedKnots.coerceAtLeast(1.0) * 3_600.0
                val followerEta = followerPair.second / follower.speedKnots.coerceAtLeast(1.0) * 3_600.0
                val actual = (followerEta - leaderEta).coerceAtLeast(0.0)
                val required = WakeSeparationRules.requiredSeconds(
                    leader.type,
                    follower.type,
                    scenario.mechanicVersions.wakeTurbulence,
                )
                result += WakeSpacing(
                    leader.id,
                    follower.id,
                    runway.id,
                    required,
                    actual,
                    actual + EPSILON < required,
                )
            }
        }
        return result.sortedWith(compareBy<WakeSpacing> { it.runwayId }.thenBy { it.followerAircraftId })
    }

    private fun ScenarioDefinition.deepCopy() = copy(
        runways = runways.toList(),
        traffic = traffic.map { it.copy(aircraft = it.aircraft.deepCopy()) },
        objectives = objectives.copy(starScoreThresholds = objectives.starScoreThresholds.toList()),
    )

    private fun AircraftState.deepCopy() = copy(route = Route(route.waypoints.toList()))

    private fun AircraftState.canReceiveFlightCommands() = status in setOf(
        AircraftStatus.INBOUND,
        AircraftStatus.APPROACH,
        AircraftStatus.GO_AROUND,
        AircraftStatus.HOLDING_SHORT,
        AircraftStatus.LINED_UP,
        AircraftStatus.DEPARTING,
    )

    private fun AircraftState.isSubjectToSeparation() = status in setOf(
        AircraftStatus.INBOUND,
        AircraftStatus.APPROACH,
        AircraftStatus.GO_AROUND,
        AircraftStatus.DEPARTING,
    )

    private fun AircraftStatus.consumesFuel() = this in setOf(
        AircraftStatus.INBOUND,
        AircraftStatus.APPROACH,
        AircraftStatus.GO_AROUND,
        AircraftStatus.HOLDING_SHORT,
        AircraftStatus.LINED_UP,
        AircraftStatus.TAKEOFF_ROLL,
        AircraftStatus.DEPARTING,
        AircraftStatus.LANDING,
    )

    private fun AircraftStatus.isTerminal() = this in setOf(
        AircraftStatus.LANDED,
        AircraftStatus.EXITED,
        AircraftStatus.CRASHED,
    )

    private data class ResolvedSpawn(
        val timeSeconds: Double,
        val originalOrder: Int,
        val aircraft: AircraftState,
    )

    private data class RunwayMovement(
        val aircraftId: String,
        val aircraftType: AircraftType,
        val elapsedSeconds: Double,
    )

    private data class NavigationResult(
        val position: Vec2,
        val headingDegrees: Double,
        val routeIndex: Int,
        val distanceTravelledNm: Double,
    )

    private data class TimeInterval(val start: Double, val end: Double) {
        fun intersection(other: TimeInterval): TimeInterval? {
            val overlapStart = max(start, other.start)
            val overlapEnd = min(end, other.end)
            return if (overlapEnd - overlapStart > EPSILON) {
                TimeInterval(overlapStart, overlapEnd)
            } else {
                null
            }
        }
    }

    private data class PairKey(val first: String, val second: String) {
        companion object {
            fun of(first: String, second: String) =
                if (first <= second) PairKey(first, second) else PairKey(second, first)
        }
    }

    /** Stable LCG so scenario timing does not change with Kotlin runtime implementations. */
    private class StableRandom(seed: Long) {
        private var state = seed

        fun nextDouble(): Double {
            state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            return (state ushr 11).toDouble() / (1L shl 53).toDouble()
        }
    }

    companion object {
        const val EPSILON = 1e-9
        const val EVENT_HISTORY_CAPACITY = 200
        const val MAX_ROUTE_WAYPOINTS = 64
        const val WAKE_WARNING_MARGIN_SECONDS = 30.0
        val SPEEDS = doubleArrayOf(0.5, 1.0, 2.0)

        fun moveTowards(current: Double, target: Double, maximumDelta: Double): Double = when {
            current < target -> min(current + maximumDelta, target)
            current > target -> max(current - maximumDelta, target)
            else -> current
        }

        fun saturatingAdd(first: Int, second: Int): Int =
            (first.toLong() + second).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
