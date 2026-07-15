package com.stuart.atccontroller.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtcSimulationEngineTest {
    @Test
    fun `engine advances only complete fixed steps and applies time multiplier`() {
        val engine = engineWith(holdingDeparture("D"))
        engine.submit(PlayerCommand.Start)

        assertEquals(0L, engine.advance(.05).tick)
        assertEquals(1L, engine.advance(.05).tick)
        engine.submit(PlayerCommand.SetSimulationSpeed(2.0))
        assertEquals(11L, engine.advance(.5).tick)
        assertEquals(1.1, engine.snapshot.elapsedSeconds, 1e-9)
    }

    @Test
    fun `pause freezes simulation until resumed`() {
        val engine = engineWith(holdingDeparture("D"))
        engine.submit(PlayerCommand.Start)
        engine.advance(1.0)
        engine.submit(PlayerCommand.Pause)
        val pausedTick = engine.snapshot.tick

        engine.advance(20.0)
        assertEquals(pausedTick, engine.snapshot.tick)
        assertEquals(GameStatus.PAUSED, engine.snapshot.status)

        engine.submit(PlayerCommand.Resume)
        engine.advance(.1)
        assertEquals(pausedTick + 1, engine.snapshot.tick)
    }

    @Test
    fun `route altitude and speed commands obey aircraft performance`() {
        val aircraft = arrival(
            id = "A",
            position = Vec2(.5, .5),
            heading = 0.0,
            altitude = 5_000.0,
            speed = 100.0,
        )
        val engine = engineWith(aircraft)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.SetRoute("A", Route(listOf(Vec2(.8, .5)))))
        engine.submit(PlayerCommand.SetTargetAltitude("A", 6_000.0))
        engine.submit(PlayerCommand.SetTargetSpeed("A", 150.0))
        engine.advanceFixedSteps(10)
        val updated = engine.snapshot.aircraft.single()

        assertEquals(15.0, updated.headingDegrees, 1e-6)
        assertEquals(5_030.0, updated.altitudeFeet, 1e-6)
        assertEquals(112.0, updated.speedKnots, 1e-6)
        assertTrue(updated.position.x > .5)
        assertEquals(0, updated.routeIndex)
    }

    @Test
    fun `invalid command is rejected without changing target`() {
        val engine = engineWith(arrival("A"))
        engine.submit(PlayerCommand.Start)
        val before = engine.snapshot.aircraft.single()

        val result = engine.submit(PlayerCommand.SetTargetSpeed("A", 999.0))

        assertEquals(before.targetSpeedKnots, result.aircraft.single().targetSpeedKnots, 0.0)
        assertTrue(result.events.single() is GameEvent.CommandRejected)
    }

    @Test
    fun `safe cleared approach touches down rolls out scores and completes`() {
        val runway = runway()
        val arrival = arrival(
            id = "A",
            position = Vec2(.5, .62),
            heading = 0.0,
            altitude = 100.0,
            speed = 90.0,
            route = Route(listOf(runway.threshold)),
        ).copy(runwayId = runway.id)
        val engine = AtcSimulationEngine(
            scenario(
                traffic = listOf(ScheduledAircraft(0.0, arrival)),
                objectives = ScenarioObjectives(
                    arrivalsToLand = 1,
                    completeWhenAllTrafficResolved = true,
                    starScoreThresholds = listOf(500, 1_000, 1_400),
                ),
            ),
        )
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.ClearToLand("A", runway.id))

        var sawTouchdown = false
        repeat(200) {
            val result = engine.advanceFixedSteps()
            sawTouchdown = sawTouchdown || result.events.any { event -> event is GameEvent.Touchdown }
            if (result.status != GameStatus.RUNNING) return@repeat
        }
        val result = engine.snapshot

        assertTrue(sawTouchdown)
        assertEquals(AircraftStatus.LANDED, result.aircraft.single().status)
        assertEquals(GameStatus.COMPLETED, result.status)
        assertEquals(1, result.score.safeArrivals)
        assertTrue(result.score.total >= 1_400)
        assertEquals(3, result.stars)
        assertEquals(null, result.runways.single().occupiedByAircraftId)
    }

    @Test
    fun `unsafe approach automatically goes around with penalty`() {
        val runway = runway()
        val highArrival = arrival(
            id = "A",
            position = runway.threshold,
            heading = runway.headingDegrees,
            altitude = 1_000.0,
            speed = 90.0,
        ).copy(runwayId = runway.id)
        val engine = engineWith(highArrival)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.ClearToLand("A", runway.id))

        val result = engine.advanceFixedSteps()

        assertEquals(AircraftStatus.GO_AROUND, result.aircraft.single().status)
        assertTrue(result.aircraft.single().clearance is Clearance.GoAround)
        assertEquals(200, result.score.penalties)
        assertTrue(result.events.any { it is GameEvent.GoAround && it.automatic })
    }

    @Test
    fun `takeoff occupies runway then departure exits and scores`() {
        val departure = holdingDeparture("D", exitPoint = Vec2(.5, .40))
            // Trusted scenarios may place long holding queues well away from the threshold;
            // clearance abstracts line-up and must not make those aircraft impossible to launch.
            .copy(position = Vec2(.10, .10))
        val engine = AtcSimulationEngine(
            scenario(
                traffic = listOf(ScheduledAircraft(0.0, departure)),
                objectives = ScenarioObjectives(
                    departuresToExit = 1,
                    completeWhenAllTrafficResolved = true,
                ),
            ),
        )
        engine.submit(PlayerCommand.Start)
        val cleared = engine.submit(PlayerCommand.ClearForTakeoff("D", TEST_RUNWAY_ID))

        assertEquals("D", cleared.runways.single().occupiedByAircraftId)
        assertEquals(AircraftStatus.TAKEOFF_ROLL, cleared.aircraft.single().status)
        assertEquals(cleared.runways.single().threshold, cleared.aircraft.single().position)

        var sawTakeoff = false
        repeat(1_500) {
            val result = engine.advanceFixedSteps()
            sawTakeoff = sawTakeoff || result.events.any { event -> event is GameEvent.Takeoff }
            if (result.status != GameStatus.RUNNING) return@repeat
        }
        val result = engine.snapshot
        assertTrue(sawTakeoff)
        assertEquals(GameStatus.COMPLETED, result.status)
        assertEquals(AircraftStatus.EXITED, result.aircraft.single().status)
        assertEquals(1, result.score.safeDepartures)
        assertEquals(null, result.runways.single().occupiedByAircraftId)
    }

    @Test
    fun `conflict predictor reports converging traffic within thirty seconds`() {
        val first = arrival(
            id = "A",
            position = Vec2(.30, .5),
            heading = 90.0,
            altitude = 5_000.0,
            speed = 360.0,
            type = AircraftType.JET,
        )
        val second = arrival(
            id = "B",
            position = Vec2(.70, .5),
            heading = 270.0,
            altitude = 5_000.0,
            speed = 360.0,
            type = AircraftType.JET,
        )
        val engine = engineWith(first, second)
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps()

        assertEquals(ConflictKind.PREDICTED, result.conflicts.single().kind)
        assertTrue(result.conflicts.single().timeToClosestApproachSeconds in 0.0..30.0)
        assertTrue(result.events.single { it is GameEvent.ConflictWarning } is GameEvent.ConflictWarning)
        assertEquals(0, result.strikes)
    }

    @Test
    fun `conflict predictor warns when parallel traffic converges vertically`() {
        val first = arrival(
            id = "A",
            position = Vec2(.45, .5),
            heading = 0.0,
            altitude = 5_000.0,
            speed = 180.0,
        )
        val second = arrival(
            id = "B",
            position = Vec2(.55, .5),
            heading = 0.0,
            altitude = 6_200.0,
            speed = 180.0,
        )
        val engine = engineWith(first, second)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.SetTargetAltitude("A", 7_000.0))

        val result = engine.advanceFixedSteps()

        val predicted = result.conflicts.single()
        assertEquals(ConflictKind.PREDICTED, predicted.kind)
        assertTrue(predicted.timeToClosestApproachSeconds > 0.0)
        assertTrue(predicted.verticalDistanceFeet < 1_000.0)
    }

    @Test
    fun `conflict predictor finds overlap after horizontal closest approach`() {
        val first = arrival(
            id = "A",
            position = Vec2(.30, .5),
            heading = 90.0,
            altitude = 5_000.0,
            speed = 360.0,
            type = AircraftType.JET,
        )
        val second = arrival(
            id = "B",
            position = Vec2(.70, .5),
            heading = 270.0,
            altitude = 8_000.0,
            speed = 360.0,
            type = AircraftType.JET,
        )
        val engine = engineWith(first, second)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.SetTargetAltitude("A", 8_000.0))
        engine.submit(PlayerCommand.SetTargetAltitude("B", 5_000.0))

        val result = engine.advanceFixedSteps()

        val predicted = result.conflicts.single()
        assertEquals(ConflictKind.PREDICTED, predicted.kind)
        assertTrue(predicted.timeToClosestApproachSeconds > 20.0)
        assertTrue(predicted.horizontalDistanceNm < 3.0)
        assertTrue(predicted.verticalDistanceFeet < 1_000.0)
    }

    @Test
    fun `vertical separation of exactly one thousand feet is safe`() {
        val first = arrival("A", position = Vec2(.4, .5), altitude = 5_000.0)
        val second = arrival("B", position = Vec2(.4, .5), altitude = 6_000.0)
        val engine = engineWith(first, second)
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps()

        assertTrue(result.conflicts.isEmpty())
        assertEquals(0, result.strikes)
    }

    @Test
    fun `continuous loss of separation produces only one strike per pair`() {
        val first = arrival("A", position = Vec2(.45, .5), heading = 0.0, altitude = 5_000.0, speed = 100.0)
        val second = arrival("B", position = Vec2(.55, .5), heading = 0.0, altitude = 5_000.0, speed = 100.0)
        val engine = engineWith(first, second)
        engine.submit(PlayerCommand.Start)

        engine.advanceFixedSteps()
        engine.advanceFixedSteps(50)

        assertEquals(1, engine.snapshot.strikes)
        assertEquals(ConflictKind.LOSS_OF_SEPARATION, engine.snapshot.conflicts.single().kind)
    }

    @Test
    fun `three simultaneous pair losses fail mission at strike limit`() {
        val traffic = listOf(
            arrival("A", position = Vec2(.45, .5), heading = 0.0, altitude = 5_000.0, speed = 100.0),
            arrival("B", position = Vec2(.47, .5), heading = 0.0, altitude = 5_000.0, speed = 100.0),
            arrival("C", position = Vec2(.49, .5), heading = 0.0, altitude = 5_000.0, speed = 100.0),
        )
        val engine = engineWith(*traffic.toTypedArray())
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps()

        assertEquals(GameStatus.FAILED, result.status)
        assertEquals(FailureReason.TOO_MANY_STRIKES, result.failureReason)
        assertEquals(3, result.strikes)
    }

    @Test
    fun `collision fails immediately`() {
        val first = arrival("A", position = Vec2(.5, .5), heading = 90.0, altitude = 5_000.0)
        val second = arrival("B", position = Vec2(.5, .5), heading = 270.0, altitude = 5_000.0)
        val engine = engineWith(first, second)
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps()

        assertEquals(GameStatus.FAILED, result.status)
        assertEquals(FailureReason.COLLISION, result.failureReason)
        assertTrue(result.events.any { it is GameEvent.Collision })
        assertTrue(result.aircraft.all { it.status == AircraftStatus.CRASHED })
    }

    @Test
    fun `landing on occupied runway is immediate runway incursion failure`() {
        val runway = runway()
        val departure = holdingDeparture("D")
        val arrival = arrival(
            id = "A",
            position = Vec2(.5, .61),
            heading = runway.headingDegrees,
            altitude = 100.0,
            speed = 90.0,
        ).copy(runwayId = runway.id)
        val engine = engineWith(departure, arrival)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.ClearForTakeoff("D", runway.id))
        engine.submit(PlayerCommand.ClearToLand("A", runway.id))

        val result = engine.advanceFixedSteps()

        assertEquals(GameStatus.FAILED, result.status)
        assertEquals(FailureReason.RUNWAY_INCURSION, result.failureReason)
        assertTrue(result.events.any { it is GameEvent.RunwayIncursion })
    }

    @Test
    fun `unstable arrival goes around over occupied runway without an incursion`() {
        val runway = runway()
        val departure = holdingDeparture("D")
        val highArrival = arrival(
            id = "A",
            position = runway.threshold,
            heading = runway.headingDegrees,
            altitude = 1_000.0,
            speed = 90.0,
        ).copy(runwayId = runway.id)
        val engine = engineWith(departure, highArrival)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.ClearForTakeoff("D", runway.id))
        engine.submit(PlayerCommand.ClearToLand("A", runway.id))

        val result = engine.advanceFixedSteps()

        assertEquals(GameStatus.RUNNING, result.status)
        assertEquals(AircraftStatus.GO_AROUND, result.aircraft.single { it.id == "A" }.status)
        assertTrue(result.events.any { it is GameEvent.GoAround })
        assertFalse(result.events.any { it is GameEvent.RunwayIncursion })
    }

    @Test
    fun `scenario expires when objectives remain unmet`() {
        val engine = AtcSimulationEngine(
            scenario(
                traffic = listOf(ScheduledAircraft(0.0, holdingDeparture("D"))),
                objectives = ScenarioObjectives(departuresToExit = 1),
                maxDuration = .3,
            ),
        )
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps(3)

        assertEquals(GameStatus.FAILED, result.status)
        assertEquals(FailureReason.TIME_EXPIRED, result.failureReason)
    }

    @Test
    fun `seeded jitter yields identical spawn sequence and snapshots`() {
        val traffic = listOf(
            ScheduledAircraft(2.0, holdingDeparture("A"), spawnJitterSeconds = 1.5),
            ScheduledAircraft(2.0, holdingDeparture("B"), spawnJitterSeconds = 1.5),
            ScheduledAircraft(2.0, holdingDeparture("C"), spawnJitterSeconds = 1.5),
        )
        val definition = scenario(
            seed = 987_654_321L,
            traffic = traffic,
            objectives = ScenarioObjectives(departuresToExit = 3),
        )
        val first = AtcSimulationEngine(definition)
        val second = AtcSimulationEngine(definition)
        first.submit(PlayerCommand.Start)
        second.submit(PlayerCommand.Start)

        repeat(50) {
            assertEquals(first.advanceFixedSteps(), second.advanceFixedSteps())
        }
        assertNotEquals(3, first.snapshot.pendingAircraftCount)
    }

    @Test
    fun `wrong boundary exit still resolves departure but incurs penalty`() {
        val departure = AircraftState(
            id = "D",
            callsign = "TESTD",
            type = AircraftType.LIGHT,
            operation = FlightOperation.DEPARTURE,
            status = AircraftStatus.DEPARTING,
            position = Vec2(0.0, .5),
            headingDegrees = 270.0,
            altitudeFeet = 3_000.0,
            speedKnots = 150.0,
            exitPoint = Vec2(1.0, .5),
        )
        val engine = AtcSimulationEngine(
            scenario(
                traffic = listOf(ScheduledAircraft(0.0, departure)),
                objectives = ScenarioObjectives(departuresToExit = 1),
            ),
        )
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps()

        assertEquals(AircraftStatus.EXITED, result.aircraft.single().status)
        assertEquals(1, result.score.safeDepartures)
        assertEquals(250, result.score.penalties)
        assertFalse((result.events.single { it is GameEvent.AircraftExited } as GameEvent.AircraftExited).correctExit)
    }

    @Test
    fun `arrival leaving controlled airspace fails promptly`() {
        val escapedArrival = arrival(
            id = "A",
            position = Vec2(0.0, .5),
            heading = 270.0,
            altitude = 4_000.0,
            speed = 180.0,
        )
        val engine = engineWith(escapedArrival)
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps()

        assertEquals(GameStatus.FAILED, result.status)
        assertEquals(FailureReason.AIRCRAFT_LOST, result.failureReason)
        assertEquals(AircraftStatus.EXITED, result.aircraft.single().status)
        assertTrue(result.events.any { it is GameEvent.AircraftLeftAirspace })
    }

    @Test
    fun `fuel burns on fixed steps and exhaustion fails deterministically`() {
        val lowFuelArrival = arrival("A").copy(
            fuelCapacitySeconds = .2,
            fuelRemainingSeconds = .2,
        )
        val engine = engineWith(lowFuelArrival)
        engine.submit(PlayerCommand.Start)

        val firstStep = engine.advanceFixedSteps()
        val exhausted = engine.advanceFixedSteps()

        assertEquals(.1, firstStep.aircraft.single().fuelRemainingSeconds, 1e-9)
        assertEquals(GameStatus.FAILED, exhausted.status)
        assertEquals(FailureReason.FUEL_EXHAUSTED, exhausted.failureReason)
        assertEquals(0.0, exhausted.aircraft.single().fuelRemainingSeconds, 0.0)
        assertEquals(AircraftStatus.CRASHED, exhausted.aircraft.single().status)
    }

    @Test
    fun `close waypoint chains advance without teleporting or undercounting distance`() {
        val start = Vec2(.5, .5)
        val routed = arrival(
            id = "A",
            position = start,
            heading = 90.0,
            altitude = 5_000.0,
            speed = 180.0,
            route = Route(
                listOf(
                    Vec2(.501, .5),
                    Vec2(.507, .5),
                    Vec2(.513, .5),
                ),
            ),
        )
        val engine = engineWith(routed)
        engine.submit(PlayerCommand.Start)

        val updated = engine.advanceFixedSteps().aircraft.single()
        val geometricDistance = Navigation.distanceNm(start, updated.position, 10.0, 10.0)

        assertEquals(2, updated.routeIndex)
        assertEquals(.005, geometricDistance, 1e-9)
        assertEquals(geometricDistance, updated.distanceTravelledNm, 1e-9)
    }

    @Test
    fun `arrival beyond threshold cannot capture approach from the departure side`() {
        val runway = runway()
        val wrongSide = arrival(
            id = "A",
            position = Vec2(.5, .59),
            heading = runway.headingDegrees,
            altitude = 100.0,
            speed = 90.0,
        ).copy(runwayId = runway.id)
        val engine = engineWith(wrongSide)
        engine.submit(PlayerCommand.Start)
        engine.submit(PlayerCommand.ClearToLand("A", runway.id))

        val result = engine.advanceFixedSteps()

        assertEquals(AircraftStatus.GO_AROUND, result.aircraft.single().status)
        assertTrue(result.events.any { it is GameEvent.GoAround && it.automatic })
        assertFalse(result.events.any { it is GameEvent.Touchdown })
    }

    @Test
    fun `engine applies scenario scoring rules including completion bonus`() {
        val departure = AircraftState(
            id = "D",
            callsign = "TESTD",
            type = AircraftType.LIGHT,
            operation = FlightOperation.DEPARTURE,
            status = AircraftStatus.DEPARTING,
            position = Vec2(0.0, .5),
            headingDegrees = 270.0,
            altitudeFeet = 3_000.0,
            speedKnots = 150.0,
            exitPoint = Vec2(1.0, .5),
        )
        val engine = AtcSimulationEngine(
            scenario(
                traffic = listOf(ScheduledAircraft(0.0, departure)),
                objectives = ScenarioObjectives(departuresToExit = 1),
                scoring = ScenarioScoringRules(
                    safeDeparturePoints = 321,
                    maximumRouteEfficiencyBonusPoints = 0,
                    maximumTimeBonusPoints = 0,
                    completionBonusPoints = 11,
                    missedExitPenaltyPoints = 17,
                ),
            ),
        )
        engine.submit(PlayerCommand.Start)

        val result = engine.advanceFixedSteps()

        assertEquals(GameStatus.COMPLETED, result.status)
        assertEquals(321, result.score.basePoints)
        assertEquals(11, result.score.completionBonusPoints)
        assertEquals(17, result.score.penalties)
        assertEquals(315, result.score.total)
    }
}

internal const val TEST_RUNWAY_ID = "RWY"

internal fun runway() = RunwayState(
    id = TEST_RUNWAY_ID,
    threshold = Vec2(.5, .6),
    end = Vec2(.5, .4),
    headingDegrees = 0.0,
)

internal fun arrival(
    id: String,
    position: Vec2 = Vec2(.5, .8),
    heading: Double = 0.0,
    altitude: Double = 5_000.0,
    speed: Double = 180.0,
    type: AircraftType = AircraftType.LIGHT,
    route: Route = Route.EMPTY,
) = AircraftState.inbound(
    id = id,
    callsign = "TEST$id",
    type = type,
    position = position,
    headingDegrees = heading,
    altitudeFeet = altitude,
    speedKnots = speed,
    route = route,
)

internal fun holdingDeparture(id: String, exitPoint: Vec2 = Vec2(.5, .05)) =
    AircraftState.holdingShort(
        id = id,
        callsign = "TEST$id",
        type = AircraftType.LIGHT,
        runway = runway(),
        exitPoint = exitPoint,
        holdingPosition = Vec2(.52, .62),
    )

internal fun scenario(
    seed: Long = 42L,
    traffic: List<ScheduledAircraft>,
    objectives: ScenarioObjectives = ScenarioObjectives(
        arrivalsToLand = traffic.count { it.aircraft.operation == FlightOperation.ARRIVAL },
        departuresToExit = traffic.count { it.aircraft.operation == FlightOperation.DEPARTURE },
        completeWhenAllTrafficResolved = true,
    ),
    scoring: ScenarioScoringRules = ScenarioScoringRules(),
    maxDuration: Double = 600.0,
) = ScenarioDefinition(
    id = "test",
    title = "Test scenario",
    seed = seed,
    runways = listOf(runway()),
    traffic = traffic,
    objectives = objectives,
    scoring = scoring,
    mapWidthNm = 10.0,
    mapHeightNm = 10.0,
    maxDurationSeconds = maxDuration,
)

internal fun engineWith(vararg states: AircraftState) = AtcSimulationEngine(
    scenario(traffic = states.map { ScheduledAircraft(0.0, it) }),
)
