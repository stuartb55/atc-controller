package com.stuart.atccontroller.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainTest {
    @Test
    fun `normalized coordinates reject values outside map`() {
        assertThrows(IllegalArgumentException::class.java) { Vec2(-.01, .5) }
        assertThrows(IllegalArgumentException::class.java) { Vec2(.5, 1.01) }
    }

    @Test
    fun `aircraft factories establish correct operation state`() {
        val runway = runway()
        val arrival = arrival(id = "A")
        val departure = AircraftState.holdingShort(
            id = "D",
            callsign = "TEST2",
            type = AircraftType.REGIONAL,
            runway = runway,
            exitPoint = Vec2(.5, .05),
            holdingPosition = Vec2(.52, .62),
        )

        assertEquals(FlightOperation.ARRIVAL, arrival.operation)
        assertEquals(AircraftStatus.INBOUND, arrival.status)
        assertEquals(FlightOperation.DEPARTURE, departure.operation)
        assertEquals(AircraftStatus.HOLDING_SHORT, departure.status)
        assertEquals(runway.id, departure.runwayId)
    }

    @Test
    fun `score total subtracts penalties and never becomes negative`() {
        assertEquals(
            1_150,
            ScoreBreakdown(basePoints = 1_000, efficiencyPoints = 250, penalties = 100).total,
        )
        assertEquals(0, ScoreBreakdown(basePoints = 100, penalties = 500).total)
    }

    @Test
    fun `simulation parameters reject non-finite and inconsistent values`() {
        assertThrows(IllegalArgumentException::class.java) {
            SimulationParameters(fixedStepSeconds = Double.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SimulationParameters(waypointCaptureNm = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SimulationParameters(
                touchdownRadiusNm = 1.0,
                approachCaptureDistanceNm = .5,
            )
        }
    }

    @Test
    fun `scenario dimensions must be finite`() {
        assertThrows(IllegalArgumentException::class.java) {
            scenario(
                traffic = listOf(ScheduledAircraft(0.0, arrival("A"))),
            ).copy(mapWidthNm = Double.POSITIVE_INFINITY)
        }
    }
}
