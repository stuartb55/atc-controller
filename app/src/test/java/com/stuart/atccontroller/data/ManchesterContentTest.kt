package com.stuart.atccontroller.data

import com.stuart.atccontroller.simulation.FlightOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManchesterContentTest {
    @Test
    fun airportContainsTwoParallelRunwaysAndDatedEntertainmentMetadata() {
        val airport = ManchesterContent.airport

        assertEquals(setOf("05L", "23R", "05R", "23L"), airport.runwayEnds.map { it.id }.toSet())
        assertEquals(2, airport.physicalRunways.size)
        assertEquals("2026-04-16", airport.source.effectiveDateIso)
        assertTrue(airport.source.sourceUrl.contains("aurora.nats.co.uk"))
        assertTrue(airport.source.disclaimer.contains("Not for navigation"))
        assertTrue(airport.fixes.all { it.isFictional })
        assertTrue(ScenarioValidator.validateAirport(airport).isEmpty())
    }

    @Test
    fun allEightAuthoredMissionsAreValidAndProgressive() {
        val missions = ManchesterContent.authoredMissions

        assertEquals(8, missions.size)
        assertEquals((1..8).toList(), missions.map { it.difficulty })
        assertEquals(TutorialFocus.SELECTION_AND_ROUTING, missions.first().tutorialFocus)
        assertEquals(TutorialFocus.PARALLEL_RUNWAYS, missions.last().tutorialFocus)
        assertTrue(missions.zipWithNext().all { (first, second) -> first.traffic.size <= second.traffic.size })
        missions.forEach { mission ->
            val result = ScenarioValidator.validate(mission)
            assertTrue("${mission.id}: ${result.issues}", result.isValid)
            assertTrue(
                "${mission.id} does not leave enough time after its last spawn",
                mission.maxDurationSeconds - mission.traffic.maxOf { it.spawnAtSeconds } >= 360,
            )
        }
    }

    @Test
    fun missionOrderUnlockLookupStopsAfterFinalMission() {
        val first = ManchesterContent.authoredMissions.first()
        val second = ManchesterContent.authoredMissions[1]
        val last = ManchesterContent.authoredMissions.last()

        assertEquals(second.id, ManchesterContent.nextMissionId(first.id))
        assertEquals(null, ManchesterContent.nextMissionId(last.id))
        assertEquals(null, ManchesterContent.nextMissionId("unknown"))
        assertNotNull(ManchesterContent.mission(first.id))
    }

    @Test
    fun adapterBuildsEngineTrafficObjectivesAndActiveRunways() {
        val authored = ManchesterContent.authoredMissions.last()
        val simulation = authored.toSimulationScenario()

        assertEquals(authored.id, simulation.id)
        assertEquals(authored.traffic.size, simulation.traffic.size)
        assertEquals(setOf("23R", "23L"), simulation.runways.map { it.id }.toSet())
        assertEquals(
            authored.traffic.count { it.intent == TrafficIntent.ARRIVAL },
            simulation.objectives.arrivalsToLand,
        )
        assertEquals(
            authored.traffic.count { it.intent == TrafficIntent.DEPARTURE },
            simulation.objectives.departuresToExit,
        )
        assertTrue(simulation.traffic.any { it.aircraft.operation == FlightOperation.ARRIVAL })
        assertTrue(simulation.traffic.any { it.aircraft.operation == FlightOperation.DEPARTURE })
        assertEquals(authored.traffic.size, simulation.objectives.safeMovementsToComplete)
        assertEquals(2, simulation.objectives.maximumStrikes)
        assertEquals(authored.scoring.safeArrivalPoints, simulation.scoring.safeArrivalPoints)
        assertEquals(authored.scoring.safeDeparturePoints, simulation.scoring.safeDeparturePoints)
        assertEquals(
            authored.scoring.maximumRouteEfficiencyBonusPoints,
            simulation.scoring.maximumRouteEfficiencyBonusPoints,
        )
        assertEquals(authored.scoring.conflictPenalty, simulation.scoring.conflictPenaltyPoints)
        authored.traffic.zip(simulation.traffic).forEach { (content, scheduled) ->
            assertEquals(content.fuelSeconds.toDouble(), scheduled.aircraft.fuelCapacitySeconds, 0.0)
            assertEquals(content.fuelSeconds.toDouble(), scheduled.aircraft.fuelRemainingSeconds, 0.0)
        }
    }

    @Test
    fun queuedDeparturesUseDistinctOffRunwayHoldingPositions() {
        val simulation = ManchesterContent.authoredMissions[5].toSimulationScenario()
        val departures = simulation.traffic
            .map { it.aircraft }
            .filter { it.operation == FlightOperation.DEPARTURE }
        val thresholds = simulation.runways.associate { it.id to it.threshold }

        assertEquals(departures.size, departures.map { it.position }.distinct().size)
        assertTrue(departures.all { it.position != thresholds[it.runwayId] })
    }

    @Test
    fun airportValidatorRejectsMisalignedApproachGate() {
        val airport = ManchesterContent.airport
        val malformed = airport.copy(
            runwayEnds = airport.runwayEnds.mapIndexed { index, end ->
                if (index == 0) end.copy(approachGate = NormalizedPoint(.60, .39)) else end
            },
        )

        assertTrue(
            ScenarioValidator.validateAirport(malformed).any { it.code == "approach_heading_mismatch" },
        )
    }

    @Test
    fun airportValidatorRejectsNonFiniteDimensionsAndIdenticalRunwayEndpoints() {
        val airport = ManchesterContent.airport
        val firstEnd = airport.runwayEnds.first()
        val malformedEnds = airport.runwayEnds.map { end ->
            if (end.id == firstEnd.reciprocalEndId) end.copy(threshold = firstEnd.threshold) else end
        }
        val malformed = airport.copy(
            mapWidthNm = Double.NaN,
            runwayEnds = malformedEnds,
        )

        val codes = ScenarioValidator.validateAirport(malformed).map { it.code }.toSet()

        assertTrue("issues=$codes", "airport_map_size" in codes)
        assertTrue("issues=$codes", "identical_runway_endpoints" in codes)
    }

    @Test
    fun validatorUsesOperationSpecificExecutableScoringMaximum() {
        val valid = ManchesterContent.authoredMissions[4]
        val invalid = valid.copy(
            scoring = valid.scoring.copy(
                thresholds = StarThresholds(100, 200, 8_800),
            ),
        )

        val result = ScenarioValidator.validate(invalid)

        assertTrue(result.issues.any { it.code == "unachievable_star_threshold" })
        assertThrows(IllegalArgumentException::class.java) { invalid.toSimulationScenario() }
    }

    @Test
    fun validatorReportsConcreteProblemsWithoutThrowing() {
        val valid = ManchesterContent.authoredMissions.first()
        val invalidTraffic = valid.traffic.first().copy(
            entryFixId = "MISSING",
            initialAltitudeFeet = -1,
        )
        val invalid = valid.copy(
            traffic = listOf(invalidTraffic, invalidTraffic),
            scoring = valid.scoring.copy(thresholds = StarThresholds(500, 400, 300)),
        )

        val result = ScenarioValidator.validate(invalid)
        val codes = result.issues.map { it.code }.toSet()

        assertFalse(result.isValid)
        assertTrue("issues=$codes", "duplicate_traffic_id" in codes)
        assertTrue("issues=$codes", "invalid_entry_fix" in codes)
        assertTrue("issues=$codes", "arrival_altitude_range" in codes)
        assertTrue("issues=$codes", "star_threshold_order" in codes)
    }
}
