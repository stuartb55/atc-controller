package com.stuart.atccontroller.ui

import com.stuart.atccontroller.simulation.AircraftStatus
import com.stuart.atccontroller.simulation.DynamicEventLifecycle
import com.stuart.atccontroller.simulation.FlightOperation
import com.stuart.atccontroller.simulation.GameSnapshot
import com.stuart.atccontroller.simulation.GameStatus
import com.stuart.atccontroller.simulation.ScenarioDefinition
import com.stuart.atccontroller.simulation.WeatherChangeLifecycle
import kotlin.math.ceil

/** Pure validation rules for deterministic active-session reconstruction. */
internal object ActiveSessionRestorePolicy {
    private const val TICKS_PER_SECOND = 10
    private const val MAXIMUM_RUNWAY_OCCUPANCY_MULTIPLIER = 1.5

    fun deadlineTick(scenario: ScenarioDefinition): Long =
        ceil(scenario.maxDurationSeconds * TICKS_PER_SECOND).toLong()

    fun maximumRestorableTick(scenario: ScenarioDefinition): Long {
        val maximumLandingRollSeconds = scenario.traffic
            .asSequence()
            .filter { it.aircraft.operation == FlightOperation.ARRIVAL }
            .maxOfOrNull { it.aircraft.type.landingRollSeconds }
            ?: 0.0
        val maximumOccupancyMultiplier = if (scenario.mechanicVersions.reducedVisibility > 0) {
            MAXIMUM_RUNWAY_OCCUPANCY_MULTIPLIER
        } else {
            1.0
        }
        val graceTicks = ceil(
            maximumLandingRollSeconds * maximumOccupancyMultiplier * TICKS_PER_SECOND,
        ).toLong()
        return deadlineTick(scenario) + graceTicks + 1L
    }

    fun canRestore(
        snapshot: GameSnapshot,
        savedTick: Long,
        scenario: ScenarioDefinition,
    ): Boolean {
        if (savedTick !in 0..maximumRestorableTick(scenario)) return false
        if (snapshot.tick != savedTick || snapshot.status != GameStatus.RUNNING) return false
        return savedTick <= deadlineTick(scenario) || snapshot.hasCompletableLandingRollsOnly()
    }

    /** Mirrors the engine's deliberately narrow deadline grace for an overtime checkpoint. */
    private fun GameSnapshot.hasCompletableLandingRollsOnly(): Boolean {
        if (pendingAircraftCount != 0 || strikes > objectives.maximumStrikes) return false
        if (dynamicEventStates.any {
                it.lifecycle !in setOf(DynamicEventLifecycle.RESOLVED, DynamicEventLifecycle.FAILED)
            }
        ) return false
        if (weatherChangeStates.any { it.lifecycle != WeatherChangeLifecycle.APPLIED }) return false

        val unresolved = aircraft.filterNot { state ->
            state.status in setOf(
                AircraftStatus.LANDED,
                AircraftStatus.EXITED,
                AircraftStatus.CRASHED,
            )
        }
        if (unresolved.isEmpty() || unresolved.any { state ->
                state.operation != FlightOperation.ARRIVAL || state.status != AircraftStatus.LANDING
            }
        ) return false

        val projectedArrivals = score.safeArrivals + unresolved.size
        return projectedArrivals + score.safeDepartures >= objectives.safeMovementsToComplete &&
            projectedArrivals >= objectives.arrivalsToLand &&
            score.safeDepartures >= objectives.departuresToExit
    }
}
