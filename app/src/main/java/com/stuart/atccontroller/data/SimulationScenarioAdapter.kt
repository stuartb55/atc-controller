package com.stuart.atccontroller.data

import com.stuart.atccontroller.simulation.AircraftState
import com.stuart.atccontroller.simulation.AircraftType
import com.stuart.atccontroller.simulation.DynamicEventDefinition as SimulationDynamicEventDefinition
import com.stuart.atccontroller.simulation.DynamicEventType
import com.stuart.atccontroller.simulation.DynamicRecoveryGoal
import com.stuart.atccontroller.simulation.Navigation
import com.stuart.atccontroller.simulation.MechanicVersions
import com.stuart.atccontroller.simulation.Route
import com.stuart.atccontroller.simulation.RunwayState
import com.stuart.atccontroller.simulation.ScenarioDefinition as SimulationScenarioDefinition
import com.stuart.atccontroller.simulation.ScenarioObjectives
import com.stuart.atccontroller.simulation.ScenarioScoringRules
import com.stuart.atccontroller.simulation.ScheduledAircraft
import com.stuart.atccontroller.simulation.Vec2
import com.stuart.atccontroller.simulation.WeatherState
import com.stuart.atccontroller.simulation.WeatherChangeDefinition as SimulationWeatherChangeDefinition

/** Converts authored content into the immutable contract consumed by the simulation engine. */
fun ScenarioDefinition.toSimulationScenario(
    airport: AirportDefinition = checkNotNull(ContentRegistry.airport(airportId)) {
        "Unknown content airport $airportId"
    },
): SimulationScenarioDefinition {
    ScenarioValidator.validate(this, airport).requireValid()
    require(airport.id == airportId) {
        "Scenario $id targets airport $airportId, not ${airport.id}"
    }

    val runwayDefinitions = airport.runwayEnds.associateBy(RunwayEndDefinition::id)
    val activeRunwayIds = runwayConfiguration.arrivalEndIds + runwayConfiguration.departureEndIds
    val scheduledRunwayIds = weatherChanges.flatMap { it.activeRunwayEndIds }.toSet()
    val runways = airport.runwayEnds
        .filter { it.id in activeRunwayIds || it.id in scheduledRunwayIds }
        .map { definition ->
            val reciprocal = checkNotNull(runwayDefinitions[definition.reciprocalEndId]) {
                "Missing reciprocal end ${definition.reciprocalEndId} for ${definition.id}"
            }
            RunwayState(
                id = definition.id,
                threshold = definition.threshold.toVec2(),
                end = reciprocal.threshold.toVec2(),
                headingDegrees = definition.headingDegrees.toDouble(),
                physicalRunwayId = definition.physicalRunwayId,
                active = definition.id in activeRunwayIds,
            )
        }
    val runwayStates = runways.associateBy(RunwayState::id)
    val fixes = airport.fixes.associateBy(NavigationFixDefinition::id)
    val departureQueueByRunway = mutableMapOf<String, Int>()

    val scheduledTraffic = traffic.map { spawn ->
        val runway = checkNotNull(runwayStates[spawn.runwayEndId]) {
            "Traffic ${spawn.id} references inactive runway ${spawn.runwayEndId}"
        }
        val type = spawn.performanceClass.toSimulationType()
        val aircraft = when (spawn.intent) {
            TrafficIntent.ARRIVAL -> {
                val entry = checkNotNull(spawn.entryFixId?.let(fixes::get)) {
                    "Arrival ${spawn.id} has no valid entry fix"
                }.position.toVec2()
                val centre = Vec2(0.5, 0.5)
                AircraftState.inbound(
                    id = spawn.id,
                    callsign = spawn.callsign,
                    type = type,
                    position = entry,
                    headingDegrees = Navigation.bearingDegrees(
                        entry,
                        centre,
                        airport.mapWidthNm,
                        airport.mapHeightNm,
                    ),
                    altitudeFeet = spawn.initialAltitudeFeet.toDouble(),
                    speedKnots = spawn.initialSpeedKnots.toDouble(),
                    route = Route.EMPTY,
                ).copy(
                    runwayId = runway.id,
                    fuelCapacitySeconds = spawn.fuelSeconds.toDouble(),
                    fuelRemainingSeconds = spawn.fuelSeconds.toDouble(),
                )
            }

            TrafficIntent.DEPARTURE -> {
                val exit = checkNotNull(spawn.exitFixId?.let(fixes::get)) {
                    "Departure ${spawn.id} has no valid exit fix"
                }.position.toVec2()
                val queueIndex = departureQueueByRunway.getOrDefault(runway.id, 0)
                departureQueueByRunway[runway.id] = queueIndex + 1
                AircraftState.holdingShort(
                    id = spawn.id,
                    callsign = spawn.callsign,
                    type = type,
                    runway = runway,
                    exitPoint = exit,
                    holdingPosition = holdingPoint(
                        runway = runway,
                        queueIndex = queueIndex,
                        mapWidthNm = airport.mapWidthNm,
                        mapHeightNm = airport.mapHeightNm,
                    ),
                ).copy(
                    fuelCapacitySeconds = spawn.fuelSeconds.toDouble(),
                    fuelRemainingSeconds = spawn.fuelSeconds.toDouble(),
                )
            }
        }

        ScheduledAircraft(
            spawnTimeSeconds = spawn.spawnAtSeconds.toDouble(),
            aircraft = aircraft,
        )
    }

    val completeMovementTarget = objectives.targetFor(ObjectiveType.COMPLETE_SAFE_MOVEMENTS)
    return SimulationScenarioDefinition(
        id = id,
        title = title,
        seed = seed,
        runways = runways,
        traffic = scheduledTraffic,
        objectives = ScenarioObjectives(
            safeMovementsToComplete = completeMovementTarget ?: 0,
            arrivalsToLand = objectives.targetFor(ObjectiveType.LAND_ARRIVALS) ?: 0,
            departuresToExit = objectives.targetFor(ObjectiveType.DEPART_AIRCRAFT) ?: 0,
            minimumScore = objectives.targetFor(ObjectiveType.SCORE_AT_LEAST) ?: 0,
            maximumStrikes = objectives.targetFor(ObjectiveType.LIMIT_STRIKES) ?: (maxStrikes - 1),
            completeWhenAllTrafficResolved = completeMovementTarget == traffic.size,
            starScoreThresholds = with(scoring.thresholds) { listOf(oneStar, twoStars, threeStars) },
        ),
        scoring = ScenarioScoringRules(
            safeArrivalPoints = scoring.safeArrivalPoints,
            safeDeparturePoints = scoring.safeDeparturePoints,
            maximumRouteEfficiencyBonusPoints = scoring.maximumRouteEfficiencyBonusPoints,
            routeInefficiencyPenaltyPointsPerNm = scoring.routeInefficiencyPenaltyPointsPerNm,
            maximumTimeBonusPoints = scoring.maximumTimeBonusPoints,
            timeBonusDecayPointsPerSecond = scoring.timeBonusDecayPointsPerSecond,
            completionBonusPoints = scoring.completionBonus,
            conflictPenaltyPoints = scoring.conflictPenalty,
            automaticGoAroundPenaltyPoints = scoring.avoidableGoAroundPenalty,
            manualGoAroundPenaltyPoints = scoring.manualGoAroundPenalty,
            missedExitPenaltyPoints = scoring.missedExitPenalty,
            runwayProcedurePenaltyPoints = scoring.runwayProcedurePenalty,
            wakeViolationPenaltyPoints = scoring.wakeViolationPenalty,
            proceduralControlPenaltyPoints = scoring.proceduralControlPenalty,
            dynamicEventSuccessBonusPoints = scoring.dynamicEventSuccessBonus,
            dynamicEventFailurePenaltyPoints = scoring.dynamicEventFailurePenalty,
        ),
        mapWidthNm = airport.mapWidthNm,
        mapHeightNm = airport.mapHeightNm,
        maxDurationSeconds = maxDurationSeconds.toDouble(),
        maxStrikes = maxStrikes,
        weather = WeatherState(
            windDirectionDegrees = weather.windDirectionDegrees.toDouble(),
            windSpeedKnots = weather.windSpeedKnots.toDouble(),
            visibilityKm = weather.visibilityKm.toDouble(),
        ),
        mechanicVersions = MechanicVersions(
            runwayProcedures = mechanicVersions.runwayProcedures,
            wakeTurbulence = mechanicVersions.wakeTurbulence,
            windDrift = mechanicVersions.windDrift,
            reducedVisibility = mechanicVersions.reducedVisibility,
            proceduralControl = mechanicVersions.proceduralControl,
            dynamicEvents = mechanicVersions.dynamicEvents,
            weatherChanges = mechanicVersions.weatherChanges,
        ),
        dynamicEvents = dynamicEvents.map { event ->
            SimulationDynamicEventDefinition(
                id = event.id,
                type = DynamicEventType.valueOf(event.type.name),
                triggerSeconds = event.triggerSeconds.toDouble(),
                warningLeadSeconds = event.warningLeadSeconds.toDouble(),
                recoveryWindowSeconds = event.recoveryWindowSeconds.toDouble(),
                recoveryGoal = DynamicRecoveryGoal.valueOf(event.recoveryGoal.name),
                aircraftId = event.aircraftId,
                runwayId = event.runwayEndId,
            )
        },
        weatherChanges = weatherChanges.map { change ->
            SimulationWeatherChangeDefinition(
                id = change.id,
                effectiveSeconds = change.effectiveSeconds.toDouble(),
                warningLeadSeconds = change.warningLeadSeconds.toDouble(),
                weather = WeatherState(
                    change.weather.windDirectionDegrees.toDouble(),
                    change.weather.windSpeedKnots.toDouble(),
                    change.weather.visibilityKm.toDouble(),
                ),
                activeRunwayIds = change.activeRunwayEndIds,
            )
        },
    )
}

private fun List<ObjectiveDefinition>.targetFor(type: ObjectiveType): Int? =
    firstOrNull { it.type == type }?.target

private fun NormalizedPoint.toVec2() = Vec2(x, y)

private fun holdingPoint(
    runway: RunwayState,
    queueIndex: Int,
    mapWidthNm: Double,
    mapHeightNm: Double,
): Vec2 {
    // A compact fictional holding queue: offset to the right of the threshold, with following
    // departures staggered behind it. Clear-for-takeoff snaps the lead aircraft onto centreline.
    val behind = Navigation.move(
        position = runway.threshold,
        headingDegrees = runway.headingDegrees + 180.0,
        distanceNm = 0.16 + queueIndex * 0.13,
        mapWidthNm = mapWidthNm,
        mapHeightNm = mapHeightNm,
    )
    return Navigation.move(
        position = behind,
        headingDegrees = runway.headingDegrees + 90.0,
        distanceNm = 0.18,
        mapWidthNm = mapWidthNm,
        mapHeightNm = mapHeightNm,
    )
}

private fun AircraftPerformanceClass.toSimulationType(): AircraftType = when (this) {
    AircraftPerformanceClass.LIGHT -> AircraftType.LIGHT
    AircraftPerformanceClass.MEDIUM -> AircraftType.JET
    AircraftPerformanceClass.HEAVY -> AircraftType.HEAVY
}
