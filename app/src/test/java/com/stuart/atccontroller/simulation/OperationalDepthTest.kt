package com.stuart.atccontroller.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalDepthTest {
    @Test
    fun `route edit commands are explicit and deterministic`() {
        val engine = engineWith(arrival("A"))
        engine.submit(PlayerCommand.Start)

        engine.submit(PlayerCommand.DirectTo("A", Vec2(.2, .2)))
        engine.submit(PlayerCommand.AppendWaypoint("A", Vec2(.3, .3)))
        assertEquals(listOf(Vec2(.2, .2), Vec2(.3, .3)), engine.snapshot.aircraft.single().route.waypoints)

        engine.submit(PlayerCommand.UndoWaypoint("A"))
        assertEquals(listOf(Vec2(.2, .2)), engine.snapshot.aircraft.single().route.waypoints)

        engine.submit(PlayerCommand.ClearRoute("A"))
        assertTrue(engine.snapshot.aircraft.single().route.waypoints.isEmpty())
    }

    @Test
    fun `command rejections expose stable reason codes`() {
        val engine = engineWith(arrival("A"))
        engine.submit(PlayerCommand.Start)

        val rejected = engine.submit(PlayerCommand.SetTargetSpeed("A", 999.0))
            .events.single() as GameEvent.CommandRejected

        assertEquals(CommandRejectionReason.INVALID_SPEED, rejected.reasonCode)
    }

    @Test
    fun `directional ends share physical runway occupancy`() {
        val firstEnd = runway().copy(id = "09", physicalRunwayId = "09-27")
        val secondEnd = runway().copy(
            id = "27",
            physicalRunwayId = "09-27",
            headingDegrees = 180.0,
            threshold = runway().end,
            end = runway().threshold,
        )
        val first = holdingDeparture("A").copy(runwayId = "09")
        val second = holdingDeparture("B").copy(runwayId = "27")
        val scenario = scenario(
            traffic = listOf(ScheduledAircraft(0.0, first), ScheduledAircraft(0.0, second)),
        ).copy(
            runways = listOf(firstEnd, secondEnd),
            mechanicVersions = MechanicVersions(runwayProcedures = 1),
        )
        val engine = AtcSimulationEngine(scenario)
        engine.submit(PlayerCommand.Start)

        engine.submit(PlayerCommand.LineUpAndWait("A", "09"))
        val rejected = engine.submit(PlayerCommand.LineUpAndWait("B", "27"))

        assertTrue(rejected.runways.all { it.occupiedByAircraftId == "A" })
        assertEquals(
            CommandRejectionReason.RUNWAY_OCCUPIED,
            (rejected.events.last() as GameEvent.CommandRejected).reasonCode,
        )
    }

    @Test
    fun `wake table gives heavy leading light the largest minimum`() {
        val heavyLight = WakeSeparationRules.requiredSeconds(AircraftType.HEAVY, AircraftType.LIGHT)
        assertEquals(180.0, heavyLight, 0.0)
        assertTrue(heavyLight > WakeSeparationRules.requiredSeconds(AircraftType.HEAVY, AircraftType.HEAVY))
        assertEquals(60.0, WakeSeparationRules.requiredSeconds(AircraftType.LIGHT, AircraftType.HEAVY), 0.0)
    }

    @Test
    fun `wake violations remain distinct from ordinary separation`() {
        val leader = arrival(
            "H",
            position = Vec2(.5, .66),
            altitude = 1_000.0,
            speed = 180.0,
            type = AircraftType.HEAVY,
        ).copy(runwayId = TEST_RUNWAY_ID, approachRunwayId = TEST_RUNWAY_ID)
        val follower = arrival(
            "L",
            position = Vec2(.5, .72),
            altitude = 3_000.0,
            speed = 180.0,
            type = AircraftType.LIGHT,
        ).copy(runwayId = TEST_RUNWAY_ID, approachRunwayId = TEST_RUNWAY_ID)
        val engine = AtcSimulationEngine(
            scenario(traffic = listOf(ScheduledAircraft(0.0, leader), ScheduledAircraft(0.0, follower))).copy(
                mechanicVersions = MechanicVersions(wakeTurbulence = 1),
            ),
        )
        engine.submit(PlayerCommand.Start)

        val snapshot = engine.advanceFixedSteps()

        assertTrue(snapshot.events.any { it is GameEvent.WakeViolation })
        assertTrue(snapshot.events.none { it is GameEvent.SeparationLost })
        assertEquals(300, snapshot.score.wakePenaltyPoints)
    }

    @Test
    fun `authored wind creates fixed step ground drift only when enabled`() {
        val traffic = listOf(ScheduledAircraft(0.0, arrival("A", position = Vec2(.5, .8))))
        val still = AtcSimulationEngine(scenario(traffic = traffic))
        val windy = AtcSimulationEngine(
            scenario(traffic = traffic).copy(
                weather = WeatherState(270.0, 30.0, 10.0),
                mechanicVersions = MechanicVersions(windDrift = 1),
            ),
        )
        still.submit(PlayerCommand.Start)
        windy.submit(PlayerCommand.Start)

        val stillPosition = still.advanceFixedSteps().aircraft.single().position
        val windyPosition = windy.advanceFixedSteps().aircraft.single().position

        assertNotEquals(stillPosition, windyPosition)
        assertTrue(windyPosition.x > stillPosition.x)
    }

    @Test
    fun `event history is bounded to two hundred entries`() {
        val engine = engineWith(arrival("A"))
        engine.submit(PlayerCommand.Start)
        repeat(220) { index ->
            engine.submit(
                PlayerCommand.SetRoute(
                    "A",
                    Route(listOf(Vec2(if (index % 2 == 0) .2 else .3, .2))),
                ),
            )
        }

        assertEquals(AtcSimulationEngine.EVENT_HISTORY_CAPACITY, engine.snapshot.eventHistory.size)
    }
}
