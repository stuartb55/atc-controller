package com.stuart.atccontroller.ui

import com.stuart.atccontroller.data.ManchesterContent
import com.stuart.atccontroller.data.toSimulationScenario
import com.stuart.atccontroller.simulation.AircraftStatus
import com.stuart.atccontroller.simulation.AtcSimulationEngine
import com.stuart.atccontroller.simulation.GameStatus
import com.stuart.atccontroller.simulation.ScoreBreakdown
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionRestorePolicyTest {
    @Test
    fun `overtime checkpoint is restorable only while final arrival is rolling out`() {
        val scenario = ManchesterContent.authoredMissions.first().toSimulationScenario()
        val deadlineTick = ActiveSessionRestorePolicy.deadlineTick(scenario)
        val overtimeTick = deadlineTick + 1L
        val aircraft = scenario.traffic.mapIndexed { index, scheduled ->
            scheduled.aircraft.copy(
                status = if (index == scenario.traffic.lastIndex) {
                    AircraftStatus.LANDING
                } else {
                    AircraftStatus.LANDED
                },
                altitudeFeet = 0.0,
                targetAltitudeFeet = 0.0,
                speedKnots = if (index == scenario.traffic.lastIndex) 80.0 else 0.0,
                targetSpeedKnots = if (index == scenario.traffic.lastIndex) 80.0 else 0.0,
                statusElapsedSeconds = if (index == scenario.traffic.lastIndex) 1.0 else 0.0,
            )
        }
        val overtime = AtcSimulationEngine(scenario).snapshot.copy(
            tick = overtimeTick,
            elapsedSeconds = overtimeTick / 10.0,
            status = GameStatus.RUNNING,
            failureReason = null,
            aircraft = aircraft,
            strikes = 0,
            score = ScoreBreakdown(safeArrivals = scenario.objectives.arrivalsToLand - 1),
            pendingAircraftCount = 0,
            events = emptyList(),
            eventHistory = emptyList(),
            dynamicEventStates = emptyList(),
            weatherChangeStates = emptyList(),
        )

        assertTrue(ActiveSessionRestorePolicy.canRestore(overtime, overtimeTick, scenario))
        assertFalse(
            ActiveSessionRestorePolicy.canRestore(
                overtime.copy(
                    aircraft = overtime.aircraft.mapIndexed { index, state ->
                        if (index == overtime.aircraft.lastIndex) {
                            state.copy(status = AircraftStatus.APPROACH)
                        } else {
                            state
                        }
                    },
                ),
                overtimeTick,
                scenario,
            ),
        )
        val beyondGrace = ActiveSessionRestorePolicy.maximumRestorableTick(scenario) + 1L
        assertFalse(
            ActiveSessionRestorePolicy.canRestore(
                overtime.copy(tick = beyondGrace, elapsedSeconds = beyondGrace / 10.0),
                beyondGrace,
                scenario,
            ),
        )
    }
}
