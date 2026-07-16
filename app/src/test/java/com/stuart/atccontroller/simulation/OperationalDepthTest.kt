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
    fun `route commands reject malformed out of bounds and oversized waypoints`() {
        val engine = engineWith(arrival("A"))
        engine.submit(PlayerCommand.Start)
        listOf(
            { Vec2(Double.NaN, .2) },
            { Vec2(-.01, .2) },
            { Vec2(.2, Double.POSITIVE_INFINITY) },
        ).forEach { invalidCoordinate ->
            assertTrue(runCatching(invalidCoordinate).isFailure)
        }

        val result = engine.submit(
            PlayerCommand.SetRoute(
                "A",
                Route(List(AtcSimulationEngine.MAX_ROUTE_WAYPOINTS + 1) { Vec2(.2, .2) }),
            ),
        )

        assertEquals(
            CommandRejectionReason.INVALID_ROUTE,
            result.events.filterIsInstance<GameEvent.CommandRejected>().single().reasonCode,
        )
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
    fun `runway occupancy history records labelled occupancy and vacation events`() {
        val scenario = scenario(
            traffic = listOf(ScheduledAircraft(0.0, holdingDeparture("A"))),
        ).copy(mechanicVersions = MechanicVersions(runwayProcedures = 1))
        val engine = AtcSimulationEngine(scenario)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.LineUpAndWait("A", TEST_RUNWAY_ID))
        engine.submit(PlayerCommand.ClearForTakeoff("A", TEST_RUNWAY_ID))
        engine.advanceFixedSteps(200)

        assertEquals(
            1,
            engine.snapshot.eventHistory.filterIsInstance<GameEvent.RunwayOccupied>()
                .count { it.runwayEndId == TEST_RUNWAY_ID },
        )
        assertTrue(
            engine.snapshot.eventHistory.any {
                it is GameEvent.RunwayVacated && it.runwayEndId == TEST_RUNWAY_ID
            },
        )
    }

    @Test
    fun `runway crossing occupies and clears the physical strip without changing assignment`() {
        val departureRunway = runway().copy(id = "09", physicalRunwayId = "09-27")
        val crossingRunway = runway().copy(
            id = "18",
            physicalRunwayId = "18-36",
            threshold = Vec2(.3, .4),
            end = Vec2(.7, .4),
            headingDegrees = 90.0,
        )
        val departure = holdingDeparture("A").copy(runwayId = departureRunway.id)
        val engine = AtcSimulationEngine(
            scenario(traffic = listOf(ScheduledAircraft(0.0, departure))).copy(
                runways = listOf(departureRunway, crossingRunway),
                mechanicVersions = MechanicVersions(runwayProcedures = 1),
            ),
        )
        engine.submit(PlayerCommand.Start)

        val crossing = engine.submit(PlayerCommand.CrossRunway("A", "18"))
        assertEquals(AircraftStatus.CROSSING_RUNWAY, crossing.aircraft.single().status)
        assertEquals("A", crossing.runways.first { it.id == "18" }.occupiedByAircraftId)
        assertTrue(crossing.events.any { it is GameEvent.RunwayCrossingStarted })

        val clear = engine.advanceFixedSteps(120)
        assertEquals(AircraftStatus.HOLDING_SHORT, clear.aircraft.single().status)
        assertEquals("09", clear.aircraft.single().runwayId)
        assertTrue(clear.runways.all { it.occupiedByAircraftId == null })
        assertTrue(clear.events.any { it is GameEvent.RunwayCrossingCompleted })
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
    fun `weather change warns then switches reciprocal directions at a fixed tick`() {
        fun run(): GameSnapshot {
            val west = runway().copy(id = "23", physicalRunwayId = "05-23", active = true)
            val east = runway().copy(
                id = "05",
                physicalRunwayId = "05-23",
                active = false,
                threshold = west.end,
                end = west.threshold,
                headingDegrees = 180.0,
            )
            val change = WeatherChangeDefinition(
                id = "easterly",
                effectiveSeconds = 20.0,
                warningLeadSeconds = 15.0,
                weather = WeatherState(60.0, 14.0, 6.0),
                activeRunwayIds = setOf("05"),
            )
            val engine = AtcSimulationEngine(
                scenario(traffic = listOf(ScheduledAircraft(0.0, arrival("A")))).copy(
                    runways = listOf(west, east),
                    mechanicVersions = MechanicVersions(windDrift = 1, weatherChanges = 1),
                    weatherChanges = listOf(change),
                ),
            )
            engine.submit(PlayerCommand.Start)
            val warning = engine.advanceFixedSteps(50)
            assertTrue(warning.events.any { it is GameEvent.WeatherChangeWarning })
            return engine.advanceFixedSteps(150)
        }

        val first = run()
        val second = run()

        assertEquals(first.weather, second.weather)
        assertEquals(first.runways, second.runways)
        assertEquals(first.eventHistory, second.eventHistory)
        assertEquals(setOf("05"), first.runways.filter { it.active }.map { it.id }.toSet())
        assertEquals(WeatherState(60.0, 14.0, 6.0), first.weather)
        assertEquals(WeatherChangeLifecycle.APPLIED, first.weatherChangeStates.single().lifecycle)
    }

    @Test
    fun `fix holds are explicit cancellable and fixed step deterministic`() {
        fun run(): GameSnapshot {
            val starting = arrival("A", position = Vec2(.35, .35)).copy(
                fuelCapacitySeconds = 1_200.0,
                fuelRemainingSeconds = 1_200.0,
            )
            val engine = AtcSimulationEngine(
                scenario(traffic = listOf(ScheduledAircraft(0.0, starting))).copy(
                    mechanicVersions = MechanicVersions(proceduralControl = 1),
                ),
            )
            engine.submit(PlayerCommand.Start)
            engine.submit(PlayerCommand.AcknowledgeInboundHandoff("A"))
            val assigned = engine.submit(
                PlayerCommand.AssignHold(
                    aircraftId = "A",
                    fix = Vec2(.35, .35),
                    inboundCourseDegrees = 180.0,
                    altitudeFeet = 4_000.0,
                    turnDirection = HoldTurnDirection.RIGHT,
                    legSeconds = 30.0,
                ),
            )
            assertTrue(assigned.events.any { it is GameEvent.HoldAssigned })
            return engine.advanceFixedSteps(400)
        }

        val first = run()
        val second = run()

        assertEquals(first.aircraft.single(), second.aircraft.single())
        assertEquals(first.eventHistory, second.eventHistory)
        assertEquals(AircraftStatus.HOLDING, first.aircraft.single().status)
        assertTrue(first.aircraft.single().holdAccumulatedSeconds > 30.0)

        val cancelEngine = AtcSimulationEngine(
            scenario(traffic = listOf(ScheduledAircraft(0.0, arrival("A", position = Vec2(.35, .35))))).copy(
                mechanicVersions = MechanicVersions(proceduralControl = 1),
            ),
        )
        cancelEngine.submit(PlayerCommand.Start)
        cancelEngine.submit(PlayerCommand.AcknowledgeInboundHandoff("A"))
        cancelEngine.submit(PlayerCommand.AssignHold("A", Vec2(.35, .35), 180.0, 4_000.0))
        cancelEngine.advanceFixedSteps(20)
        val cancelled = cancelEngine.submit(PlayerCommand.CancelHold("A"))
        assertTrue(cancelled.events.any { it is GameEvent.HoldCancelled })
        assertEquals(null, cancelled.aircraft.single().hold)
    }

    @Test
    fun `procedural handoffs expose offer request acknowledgement and completion states`() {
        val inboundEngine = AtcSimulationEngine(
            scenario(traffic = listOf(ScheduledAircraft(0.0, arrival("A")))).copy(
                mechanicVersions = MechanicVersions(proceduralControl = 1),
            ),
        )
        val offered = inboundEngine.submit(PlayerCommand.Start)
        assertEquals(HandoffStatus.OFFERED, offered.aircraft.single().handoff!!.status)
        val accepted = inboundEngine.submit(PlayerCommand.AcknowledgeInboundHandoff("A"))
        assertEquals(HandoffStatus.ACKNOWLEDGED, accepted.aircraft.single().handoff!!.status)

        val departure = holdingDeparture("D").copy(
            fuelCapacitySeconds = 1_200.0,
            fuelRemainingSeconds = 1_200.0,
        )
        val outboundEngine = AtcSimulationEngine(
            scenario(traffic = listOf(ScheduledAircraft(0.0, departure))).copy(
                mechanicVersions = MechanicVersions(proceduralControl = 1),
            ),
        )
        outboundEngine.submit(PlayerCommand.Start)
        outboundEngine.submit(PlayerCommand.ClearForTakeoff("D", TEST_RUNWAY_ID))
        outboundEngine.advanceFixedSteps(200)
        assertEquals(AircraftStatus.DEPARTING, outboundEngine.snapshot.aircraft.single().status)
        outboundEngine.submit(PlayerCommand.IssueExitClearance("D"))
        val requested = outboundEngine.submit(PlayerCommand.InitiateOutboundHandoff("D"))
        assertEquals(HandoffStatus.REQUESTED, requested.aircraft.single().handoff!!.status)
        val acknowledged = outboundEngine.advanceFixedSteps(50)
        assertEquals(HandoffStatus.ACKNOWLEDGED, acknowledged.aircraft.single().handoff!!.status)
        assertTrue(
            acknowledged.events.any {
                it is GameEvent.HandoffChanged && it.status == HandoffStatus.ACKNOWLEDGED
            },
        )
    }

    @Test
    fun `dynamic warnings activation recovery and scoring are deterministic`() {
        fun run(): GameSnapshot {
            val definitions = listOf(
                DynamicEventDefinition(
                    id = "closure",
                    type = DynamicEventType.RUNWAY_CLOSURE,
                    triggerSeconds = 5.0,
                    warningLeadSeconds = 5.0,
                    recoveryWindowSeconds = 40.0,
                    recoveryGoal = DynamicRecoveryGoal.KEEP_RUNWAY_CLEAR,
                    runwayId = TEST_RUNWAY_ID,
                ),
                DynamicEventDefinition(
                    id = "outage",
                    type = DynamicEventType.EQUIPMENT_OUTAGE,
                    triggerSeconds = 5.0,
                    warningLeadSeconds = 5.0,
                    recoveryWindowSeconds = 40.0,
                    recoveryGoal = DynamicRecoveryGoal.CONTROL_WITHOUT_PREDICTION,
                ),
            )
            val engine = AtcSimulationEngine(
                scenario(traffic = listOf(ScheduledAircraft(0.0, arrival("A")))).copy(
                    mechanicVersions = MechanicVersions(dynamicEvents = 1),
                    dynamicEvents = definitions,
                ),
            )
            engine.submit(PlayerCommand.Start)
            val warned = engine.advanceFixedSteps()
            assertEquals(2, warned.events.filterIsInstance<GameEvent.DynamicEventChanged>().size)
            engine.advanceFixedSteps(49)
            assertTrue(engine.snapshot.runways.none { it.active })
            engine.submit(PlayerCommand.AcknowledgeDynamicEvent("closure"))
            engine.submit(PlayerCommand.AcknowledgeDynamicEvent("outage"))
            return engine.advanceFixedSteps(200)
        }

        val first = run()
        val second = run()

        assertEquals(first.dynamicEventStates, second.dynamicEventStates)
        assertEquals(first.eventHistory, second.eventHistory)
        assertTrue(first.dynamicEventStates.all { it.lifecycle == DynamicEventLifecycle.RESOLVED })
        assertTrue(first.runways.all { it.active })
        assertEquals(500, first.score.dynamicEventBonusPoints)
    }

    @Test
    fun `unanswered priority event fails once with a categorized penalty`() {
        val event = DynamicEventDefinition(
            id = "fuel",
            type = DynamicEventType.LOW_FUEL_PRIORITY,
            triggerSeconds = 5.0,
            warningLeadSeconds = 5.0,
            recoveryWindowSeconds = 15.0,
            recoveryGoal = DynamicRecoveryGoal.LAND_PRIORITY_AIRCRAFT,
            aircraftId = "A",
        )
        val engine = AtcSimulationEngine(
            scenario(traffic = listOf(ScheduledAircraft(0.0, arrival("A")))).copy(
                mechanicVersions = MechanicVersions(dynamicEvents = 1),
                dynamicEvents = listOf(event),
            ),
        )
        engine.submit(PlayerCommand.Start)

        val failed = engine.advanceFixedSteps(200)
        val repeated = engine.advanceFixedSteps(20)

        assertEquals(DynamicEventLifecycle.FAILED, failed.dynamicEventStates.single().lifecycle)
        assertEquals(300, failed.score.dynamicEventPenaltyPoints)
        assertEquals(300, repeated.score.dynamicEventPenaltyPoints)
        assertEquals(
            1,
            repeated.eventHistory.filterIsInstance<GameEvent.DynamicEventChanged>()
                .count { it.lifecycle == DynamicEventLifecycle.FAILED },
        )
    }

    @Test
    fun `terminal flight reports and route heatmaps are fixed step deterministic`() {
        fun run(): GameSnapshot {
            val definition = scenario(
                traffic = listOf(ScheduledAircraft(0.0, arrival("A", position = Vec2(.5, .8)))),
            ).copy(maxDurationSeconds = 1.0)
            val engine = AtcSimulationEngine(definition)
            engine.submit(PlayerCommand.Start)
            return engine.advanceFixedSteps(10)
        }

        val first = run()
        val second = run()

        assertEquals(GameStatus.FAILED, first.status)
        assertEquals(first.flightPerformances, second.flightPerformances)
        assertEquals(first.routeHistory, second.routeHistory)
        assertEquals("A", first.flightPerformances.single().aircraftId)
        assertTrue(first.routeHistory.getValue("A").size >= 2)
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

    @Test
    fun `bounded event history retains stable sequence numbers after eviction`() {
        val engine = engineWith(arrival("A"))
        engine.submit(PlayerCommand.Start)
        repeat(AtcSimulationEngine.EVENT_HISTORY_CAPACITY + 20) { index ->
            engine.submit(
                PlayerCommand.SetRoute(
                    "A",
                    Route(listOf(Vec2(if (index % 2 == 0) .2 else .3, .2))),
                ),
            )
        }

        val snapshot = engine.snapshot
        val lastSequence = snapshot.eventHistoryStartSequence + snapshot.eventHistory.lastIndex

        assertEquals(21L, snapshot.eventHistoryStartSequence)
        assertEquals(220L, lastSequence)
    }

    @Test
    fun `structured event history is identical after deterministic reconstruction`() {
        fun run(): GameSnapshot {
            val engine = engineWith(arrival("A"))
            engine.submit(PlayerCommand.Start)
            engine.submit(PlayerCommand.SetRoute("A", Route(listOf(Vec2(.4, .4)))))
            engine.submit(PlayerCommand.SetTargetAltitude("A", 4_000.0))
            engine.submit(PlayerCommand.SetTargetSpeed("A", 180.0))
            return engine.advanceFixedSteps(25)
        }

        val original = run()
        val reconstructed = run()

        assertEquals(original.eventHistoryStartSequence, reconstructed.eventHistoryStartSequence)
        assertEquals(original.eventHistory, reconstructed.eventHistory)
    }
}
