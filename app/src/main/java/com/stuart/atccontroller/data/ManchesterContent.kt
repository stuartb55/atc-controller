package com.stuart.atccontroller.data

import com.stuart.atccontroller.simulation.Navigation
import com.stuart.atccontroller.simulation.Vec2

/**
 * Authored game content inspired by Manchester Airport. Coordinates, fixes, traffic and
 * procedures are deliberately fictionalized for playability and must not be used operationally.
 */
object ManchesterContent {
    const val AIRPORT_ID = "manchester_game"
    const val FIRST_MISSION_ID = "manchester_01_first_contact"

    private const val NORTH_RUNWAY_ID = "north_parallel"
    private const val SOUTH_RUNWAY_ID = "south_parallel"

    val airport = AirportDefinition(
        id = AIRPORT_ID,
        displayName = "Manchester",
        mapWidthNm = 32.0,
        mapHeightNm = 32.0,
        physicalRunways = listOf(
            PhysicalRunwayDefinition(
                id = NORTH_RUNWAY_ID,
                displayName = "05L / 23R",
                endIds = setOf("05L", "23R"),
            ),
            PhysicalRunwayDefinition(
                id = SOUTH_RUNWAY_ID,
                displayName = "05R / 23L",
                endIds = setOf("05R", "23L"),
            ),
        ),
        runwayEnds = listOf(
            RunwayEndDefinition(
                id = "05L",
                physicalRunwayId = NORTH_RUNWAY_ID,
                reciprocalEndId = "23R",
                threshold = NormalizedPoint(0.40, 0.59),
                approachGate = NormalizedPoint(0.16, 0.83),
                headingDegrees = 51,
            ),
            RunwayEndDefinition(
                id = "23R",
                physicalRunwayId = NORTH_RUNWAY_ID,
                reciprocalEndId = "05L",
                threshold = NormalizedPoint(0.61, 0.38),
                approachGate = NormalizedPoint(0.85, 0.14),
                headingDegrees = 231,
            ),
            RunwayEndDefinition(
                id = "05R",
                physicalRunwayId = SOUTH_RUNWAY_ID,
                reciprocalEndId = "23L",
                threshold = NormalizedPoint(0.36, 0.68),
                approachGate = NormalizedPoint(0.12, 0.92),
                headingDegrees = 51,
            ),
            RunwayEndDefinition(
                id = "23L",
                physicalRunwayId = SOUTH_RUNWAY_ID,
                reciprocalEndId = "05R",
                threshold = NormalizedPoint(0.57, 0.47),
                approachGate = NormalizedPoint(0.81, 0.23),
                headingDegrees = 231,
            ),
        ),
        fixes = listOf(
            fix("GAME_NORTHWEST", "North-west Gate", 0.05, 0.16),
            fix("GAME_NORTH", "North Gate", 0.45, 0.02),
            fix("GAME_NORTHEAST", "North-east Gate", 0.90, 0.09),
            fix("GAME_EAST", "East Gate", 0.98, 0.46),
            fix("GAME_SOUTHEAST", "South-east Gate", 0.91, 0.90),
            fix("GAME_SOUTH", "South Gate", 0.52, 0.98),
            fix("GAME_SOUTHWEST", "South-west Gate", 0.08, 0.90),
            fix("GAME_WEST", "West Gate", 0.02, 0.51),
        ),
        source = ContentSourceMetadata(
            sourceName = "UK AIP Manchester Aerodrome Chart",
            effectiveDateIso = "2026-04-16",
            sourceUrl = "https://www.aurora.nats.co.uk/htmlAIP/Publications/2026-04-16-AIRAC/graphics/480171.pdf",
            attribution = "Airport naming and runway designators referenced from the UK AIP published by NATS.",
            disclaimer = "Dated, deliberately simplified entertainment content. Not for navigation, operational use or air traffic control training. Manchester Airport and NATS do not endorse this game.",
        ),
    )

    val authoredMissions: List<ScenarioDefinition> = listOf(
        mission(
            number = 1,
            id = FIRST_MISSION_ID,
            title = "First Contact",
            briefing = "Select each arrival, set up its approach, then clear it to land while keeping traffic separated.",
            focus = TutorialFocus.SELECTION_AND_ROUTING,
            duration = 300,
            traffic = listOf(
                arrival("m01_a1", "NORTH 201", 0, "GAME_NORTHEAST", altitude = 4_000),
                arrival("m01_a2", "CLOUD 314", 55, "GAME_NORTH", altitude = 5_000),
                arrival("m01_a3", "VECTOR 122", 115, "GAME_EAST", altitude = 4_000),
            ),
            thresholds = StarThresholds(240, 330, 390),
        ),
        mission(
            number = 2,
            id = "manchester_02_level_headed",
            title = "Level Headed",
            briefing = "Use altitude assignments to safely cross converging arrivals.",
            focus = TutorialFocus.ALTITUDE,
            duration = 360,
            traffic = listOf(
                arrival("m02_a1", "EMBER 241", 5, "GAME_NORTHEAST", altitude = 5_000),
                arrival("m02_a2", "NORTH 418", 35, "GAME_NORTHWEST", altitude = 6_000),
                arrival("m02_a3", "CLOUD 515", 80, "GAME_EAST", altitude = 5_000),
                arrival("m02_a4", "VECTOR 206", 130, "GAME_NORTH", altitude = 6_000),
            ),
            thresholds = StarThresholds(320, 430, 510),
        ),
        mission(
            number = 3,
            id = "manchester_03_slow_and_steady",
            title = "Slow and Steady",
            briefing = "Build spacing with speed control before turning aircraft onto final.",
            focus = TutorialFocus.SPEED,
            duration = 420,
            traffic = listOf(
                arrival("m03_a1", "CLOUD 331", 5, "GAME_NORTHEAST", altitude = 5_000, speed = 230),
                arrival("m03_a2", "EMBER 724", 30, "GAME_EAST", altitude = 4_000, speed = 220),
                arrival("m03_a3", "NORTH 608", 65, "GAME_NORTH", altitude = 6_000, speed = 240),
                arrival("m03_a4", "VECTOR 412", 105, "GAME_NORTHWEST", altitude = 5_000, speed = 210),
                arrival("m03_a5", "CLOUD 905", 150, "GAME_EAST", altitude = 6_000, speed = 240),
            ),
            thresholds = StarThresholds(400, 530, 640),
        ),
        mission(
            number = 4,
            id = "manchester_04_cleared_to_land",
            title = "Cleared to Land",
            briefing = "Issue landing clearances only when the approach is stable and the runway is clear. Send an unsafe approach around.",
            focus = TutorialFocus.LANDING_AND_GO_AROUND,
            duration = 480,
            traffic = listOf(
                arrival("m04_a1", "VECTOR 551", 5, "GAME_NORTHEAST", altitude = 4_000),
                arrival("m04_a2", "NORTH 734", 40, "GAME_EAST", altitude = 5_000),
                arrival("m04_a3", "EMBER 316", 78, "GAME_NORTH", altitude = 6_000),
                arrival("m04_a4", "CLOUD 227", 118, "GAME_NORTHWEST", altitude = 5_000),
                arrival("m04_a5", "VECTOR 880", 160, "GAME_EAST", altitude = 5_000, performance = AircraftPerformanceClass.HEAVY),
                arrival("m04_a6", "EMBER 642", 205, "GAME_NORTHEAST", altitude = 6_000),
            ),
            thresholds = StarThresholds(440, 610, 760),
        ),
        mission(
            number = 5,
            id = "manchester_05_opening_the_flow",
            title = "Opening the Flow",
            briefing = "Clear departures for takeoff, then guide them to their assigned exit while arrivals continue.",
            focus = TutorialFocus.DEPARTURES,
            duration = 500,
            traffic = listOf(
                departure("m05_d1", "NORTH 821", 5, "GAME_NORTHWEST"),
                arrival("m05_a1", "CLOUD 417", 35, "GAME_NORTHEAST", altitude = 5_000),
                departure("m05_d2", "VECTOR 308", 80, "GAME_WEST"),
                arrival("m05_a2", "EMBER 772", 115, "GAME_EAST", altitude = 6_000),
                departure("m05_d3", "CLOUD 663", 155, "GAME_NORTH"),
                arrival("m05_a3", "NORTH 194", 195, "GAME_NORTHEAST", altitude = 5_000),
            ),
            thresholds = StarThresholds(440, 610, 700),
        ),
        mission(
            number = 6,
            id = "manchester_06_mind_the_runway",
            title = "Mind the Runway",
            briefing = "Sequence arrivals and departures without allowing their runway occupancy to overlap.",
            focus = TutorialFocus.RUNWAY_OCCUPANCY,
            duration = 580,
            traffic = listOf(
                departure("m06_d1", "EMBER 205", 5, "GAME_NORTHWEST", performance = AircraftPerformanceClass.LIGHT),
                arrival("m06_a1", "VECTOR 619", 20, "GAME_NORTHEAST", altitude = 4_000),
                departure("m06_d2", "CLOUD 430", 70, "GAME_WEST"),
                arrival("m06_a2", "NORTH 744", 90, "GAME_EAST", altitude = 5_000, performance = AircraftPerformanceClass.HEAVY),
                departure("m06_d3", "VECTOR 901", 140, "GAME_NORTH"),
                arrival("m06_a3", "EMBER 388", 168, "GAME_NORTHWEST", altitude = 6_000),
                departure("m06_d4", "NORTH 526", 220, "GAME_WEST"),
                arrival("m06_a4", "CLOUD 817", 245, "GAME_NORTHEAST", altitude = 5_000),
            ),
            thresholds = StarThresholds(600, 800, 930),
        ),
        mission(
            number = 7,
            id = "manchester_07_mixed_picture",
            title = "Mixed Picture",
            briefing = "Manage a denser mix of performance classes, arrivals and departures.",
            focus = TutorialFocus.MIXED_TRAFFIC,
            duration = 660,
            traffic = listOf(
                arrival("m07_a1", "NORTH 257", 5, "GAME_NORTHEAST", altitude = 6_000, performance = AircraftPerformanceClass.HEAVY),
                departure("m07_d1", "EMBER 620", 25, "GAME_WEST", performance = AircraftPerformanceClass.LIGHT),
                arrival("m07_a2", "CLOUD 813", 50, "GAME_EAST", altitude = 5_000),
                departure("m07_d2", "VECTOR 442", 85, "GAME_NORTHWEST"),
                arrival("m07_a3", "EMBER 179", 110, "GAME_NORTH", altitude = 6_000),
                arrival("m07_a4", "VECTOR 306", 145, "GAME_NORTHWEST", altitude = 5_000, performance = AircraftPerformanceClass.LIGHT),
                departure("m07_d3", "CLOUD 731", 180, "GAME_NORTH", performance = AircraftPerformanceClass.HEAVY),
                arrival("m07_a5", "NORTH 965", 215, "GAME_EAST", altitude = 6_000),
                departure("m07_d4", "EMBER 554", 255, "GAME_WEST"),
                arrival("m07_a6", "CLOUD 248", 290, "GAME_NORTHEAST", altitude = 5_000),
            ),
            thresholds = StarThresholds(750, 1_000, 1_180),
        ),
        mission(
            number = 8,
            id = "manchester_08_parallel_pressure",
            title = "Parallel Pressure",
            briefing = "Use both parallel runways to keep a busy final session moving safely.",
            focus = TutorialFocus.PARALLEL_RUNWAYS,
            duration = 780,
            configuration = RunwayConfigurationDefinition(
                arrivalEndIds = setOf("23R", "23L"),
                departureEndIds = setOf("23R", "23L"),
            ),
            traffic = listOf(
                arrival("m08_a1", "VECTOR 118", 5, "GAME_NORTHEAST", runway = "23R", altitude = 6_000, performance = AircraftPerformanceClass.HEAVY),
                departure("m08_d1", "CLOUD 529", 20, "GAME_WEST", runway = "23L"),
                arrival("m08_a2", "EMBER 703", 42, "GAME_EAST", runway = "23L", altitude = 5_000),
                departure("m08_d2", "NORTH 264", 70, "GAME_NORTHWEST", runway = "23R"),
                arrival("m08_a3", "CLOUD 831", 95, "GAME_NORTH", runway = "23R", altitude = 6_000),
                arrival("m08_a4", "NORTH 407", 120, "GAME_NORTHWEST", runway = "23L", altitude = 5_000, performance = AircraftPerformanceClass.LIGHT),
                departure("m08_d3", "VECTOR 685", 150, "GAME_WEST", runway = "23L", performance = AircraftPerformanceClass.HEAVY),
                arrival("m08_a5", "EMBER 952", 178, "GAME_EAST", runway = "23R", altitude = 6_000),
                departure("m08_d4", "CLOUD 346", 210, "GAME_NORTH", runway = "23R"),
                arrival("m08_a6", "VECTOR 270", 235, "GAME_NORTHEAST", runway = "23L", altitude = 5_000),
                departure("m08_d5", "EMBER 614", 270, "GAME_NORTHWEST", runway = "23L", performance = AircraftPerformanceClass.LIGHT),
                arrival("m08_a7", "NORTH 785", 295, "GAME_EAST", runway = "23R", altitude = 6_000, performance = AircraftPerformanceClass.HEAVY),
                departure("m08_d6", "VECTOR 433", 330, "GAME_WEST", runway = "23R"),
                arrival("m08_a8", "CLOUD 906", 355, "GAME_NORTH", runway = "23L", altitude = 5_000),
            ),
            thresholds = StarThresholds(1_000, 1_350, 1_650),
            mechanicVersions = MechanicVersionDefinition(runwayProcedures = 1, wakeTurbulence = 1),
        ),
        mission(
            number = 9,
            id = "manchester_09_heavy_spacing",
            title = "Heavy Spacing",
            briefing = "Sequence heavy leaders and light followers using the displayed simplified wake interval.",
            focus = TutorialFocus.WAKE_TURBULENCE,
            duration = 760,
            traffic = listOf(
                departure("m09_d1", "SUMMIT 901", 5, "GAME_WEST", performance = AircraftPerformanceClass.HEAVY),
                departure("m09_d2", "ORBIT 214", 35, "GAME_NORTH", performance = AircraftPerformanceClass.LIGHT),
                arrival("m09_a1", "VECTOR 778", 70, "GAME_NORTHEAST", altitude = 5_000, performance = AircraftPerformanceClass.HEAVY),
                arrival("m09_a2", "CLOUD 319", 112, "GAME_EAST", altitude = 6_000, performance = AircraftPerformanceClass.LIGHT),
                departure("m09_d3", "EMBER 640", 180, "GAME_NORTHWEST"),
                arrival("m09_a3", "NORTH 452", 225, "GAME_NORTH", altitude = 5_000),
            ),
            thresholds = StarThresholds(430, 610, 760),
            mechanicVersions = MechanicVersionDefinition(runwayProcedures = 1, wakeTurbulence = 1),
        ),
        mission(
            number = 10,
            id = "manchester_10_weather_window",
            title = "Weather Window",
            briefing = "Allow for deterministic wind drift and stabilize arrivals earlier in reduced visibility.",
            focus = TutorialFocus.WEATHER_OPERATIONS,
            duration = 760,
            weather = WeatherDefinition(285, 16, 5),
            configuration = RunwayConfigurationDefinition(
                arrivalEndIds = setOf("23R", "23L"),
                departureEndIds = setOf("23R", "23L"),
            ),
            traffic = listOf(
                arrival("m10_a1", "ORBIT 510", 5, "GAME_NORTHEAST", altitude = 5_000),
                arrival("m10_a2", "SUMMIT 332", 55, "GAME_EAST", altitude = 6_000, performance = AircraftPerformanceClass.LIGHT),
                departure("m10_d1", "CLOUD 745", 100, "GAME_WEST"),
                arrival("m10_a3", "EMBER 281", 150, "GAME_NORTH", altitude = 5_000, performance = AircraftPerformanceClass.HEAVY),
                departure("m10_d2", "VECTOR 604", 210, "GAME_NORTHWEST"),
                arrival("m10_a4", "NORTH 919", 265, "GAME_EAST", altitude = 6_000),
            ),
            thresholds = StarThresholds(430, 600, 740),
            mechanicVersions = MechanicVersionDefinition(
                runwayProcedures = 1,
                windDrift = 1,
                reducedVisibility = 1,
                weatherChanges = 1,
            ),
            weatherChanges = listOf(
                WeatherChangeDefinition(
                    id = "easterly_change",
                    effectiveSeconds = 300,
                    warningLeadSeconds = 60,
                    weather = WeatherDefinition(70, 12, 8),
                    activeRunwayEndIds = setOf("05L", "05R"),
                ),
            ),
        ),
        mission(
            number = 11,
            id = "manchester_11_hold_and_handoff",
            title = "Hold and Handoff",
            briefing = "Accept inbound handoffs, use fix holds to absorb delay, then clear and hand departures to the exit sector.",
            focus = TutorialFocus.PROCEDURAL_CONTROL,
            duration = 820,
            traffic = listOf(
                arrival("m11_a1", "SUMMIT 117", 5, "GAME_NORTHEAST", altitude = 6_000),
                departure("m11_d1", "ORBIT 482", 35, "GAME_WEST"),
                arrival("m11_a2", "CLOUD 630", 70, "GAME_EAST", altitude = 5_000),
                departure("m11_d2", "EMBER 905", 120, "GAME_NORTHWEST"),
                arrival("m11_a3", "VECTOR 241", 160, "GAME_NORTH", altitude = 6_000),
                departure("m11_d3", "NORTH 758", 220, "GAME_WEST"),
            ),
            thresholds = StarThresholds(430, 610, 760),
            mechanicVersions = MechanicVersionDefinition(
                runwayProcedures = 1,
                proceduralControl = 1,
            ),
        ),
        mission(
            number = 12,
            id = "manchester_12_recovery_shift",
            title = "Recovery Shift",
            briefing = "Respond to a deterministic schedule of priority traffic, a rejected takeoff, a closure, and a prediction outage.",
            focus = TutorialFocus.DYNAMIC_EVENTS,
            duration = 900,
            configuration = RunwayConfigurationDefinition(
                arrivalEndIds = setOf("23R", "23L"),
                departureEndIds = setOf("23R", "23L"),
            ),
            traffic = listOf(
                departure("m12_d1", "ORBIT 608", 5, "GAME_WEST", runway = "23L"),
                arrival("m12_a1", "SUMMIT 911", 10, "GAME_NORTHEAST", runway = "23R", altitude = 5_000, fuelSeconds = 300),
                departure("m12_d2", "VECTOR 337", 55, "GAME_NORTHWEST", runway = "23R"),
                arrival("m12_a2", "CLOUD 724", 80, "GAME_EAST", runway = "23L", altitude = 6_000),
                arrival("m12_a3", "EMBER 415", 130, "GAME_NORTH", runway = "23R", altitude = 5_000),
                departure("m12_d3", "NORTH 862", 190, "GAME_WEST", runway = "23L"),
            ),
            thresholds = StarThresholds(470, 680, 850),
            mechanicVersions = MechanicVersionDefinition(
                runwayProcedures = 1,
                wakeTurbulence = 1,
                windDrift = 1,
                dynamicEvents = 1,
            ),
            dynamicEvents = listOf(
                DynamicEventDefinition(
                    "rejected_takeoff",
                    DynamicEventTypeDefinition.REJECTED_TAKEOFF,
                    15,
                    10,
                    70,
                    DynamicRecoveryGoalDefinition.RESEQUENCE_DEPARTURE,
                    aircraftId = "m12_d1",
                ),
                DynamicEventDefinition(
                    "low_fuel_priority",
                    DynamicEventTypeDefinition.LOW_FUEL_PRIORITY,
                    45,
                    15,
                    150,
                    DynamicRecoveryGoalDefinition.LAND_PRIORITY_AIRCRAFT,
                    aircraftId = "m12_a1",
                ),
                DynamicEventDefinition(
                    "runway_closure",
                    DynamicEventTypeDefinition.RUNWAY_CLOSURE,
                    95,
                    20,
                    90,
                    DynamicRecoveryGoalDefinition.KEEP_RUNWAY_CLEAR,
                    runwayEndId = "23R",
                ),
                DynamicEventDefinition(
                    "prediction_outage",
                    DynamicEventTypeDefinition.EQUIPMENT_OUTAGE,
                    150,
                    15,
                    75,
                    DynamicRecoveryGoalDefinition.CONTROL_WITHOUT_PREDICTION,
                ),
                DynamicEventDefinition(
                    "priority_flight",
                    DynamicEventTypeDefinition.PRIORITY_FLIGHT,
                    210,
                    20,
                    150,
                    DynamicRecoveryGoalDefinition.EXPEDITE_PRIORITY_FLIGHT,
                    aircraftId = "m12_a2",
                ),
            ),
        ),
    )

    val missionIds: List<String> = authoredMissions.map(ScenarioDefinition::id)

    fun mission(id: String): ScenarioDefinition? = authoredMissions.firstOrNull { it.id == id }

    /** A short, flyable final; the decorative outer gate can add too much beginner traffic time. */
    fun finalApproachPoints(runwayEndId: String, interceptDistanceNm: Double = 2.5): List<NormalizedPoint> {
        require(interceptDistanceNm > 0.0 && interceptDistanceNm.isFinite())
        val runway = airport.runwayEnds.firstOrNull { it.id == runwayEndId }
            ?: error("Unknown runway end $runwayEndId")
        val intercept = Navigation.move(
            position = Vec2(runway.threshold.x, runway.threshold.y),
            headingDegrees = runway.headingDegrees + 180.0,
            distanceNm = interceptDistanceNm,
            mapWidthNm = airport.mapWidthNm,
            mapHeightNm = airport.mapHeightNm,
        )
        return listOf(
            NormalizedPoint(intercept.x, intercept.y),
            runway.threshold,
        )
    }

    fun nextMissionId(afterMissionId: String): String? {
        val index = missionIds.indexOf(afterMissionId)
        return if (index >= 0) missionIds.getOrNull(index + 1) else null
    }

    private fun fix(id: String, name: String, x: Double, y: Double) = NavigationFixDefinition(
        id = id,
        displayName = name,
        position = NormalizedPoint(x, y),
        use = FixUse.ENTRY_AND_EXIT,
        isFictional = true,
    )

    private fun mission(
        number: Int,
        id: String,
        title: String,
        briefing: String,
        focus: TutorialFocus,
        duration: Int,
        traffic: List<TrafficSpawnDefinition>,
        thresholds: StarThresholds,
        weather: WeatherDefinition? = null,
        mechanicVersions: MechanicVersionDefinition = MechanicVersionDefinition(
            runwayProcedures = if (number >= 6) 1 else 0,
        ),
        dynamicEvents: List<DynamicEventDefinition> = emptyList(),
        weatherChanges: List<WeatherChangeDefinition> = emptyList(),
        configuration: RunwayConfigurationDefinition = RunwayConfigurationDefinition(
            arrivalEndIds = setOf("23R"),
            departureEndIds = setOf("23R"),
        ),
    ): ScenarioDefinition {
        val arrivalCount = traffic.count { it.intent == TrafficIntent.ARRIVAL }
        val departureCount = traffic.size - arrivalCount
        return ScenarioDefinition(
            id = id,
            title = title,
            briefing = briefing,
            airportId = AIRPORT_ID,
            seed = 10_000L + number,
            difficulty = minOf(number, 10),
            // Leave at least six minutes after the final spawn. Edge fixes can be more than
            // twenty miles from touchdown and tutorial players need time to experiment.
            maxDurationSeconds = maxOf(
                duration,
                traffic.maxOfOrNull { it.spawnAtSeconds }?.plus(360) ?: duration,
            ),
            runwayConfiguration = configuration,
            weather = weather ?: WeatherDefinition(240, 8 + (number / 3), 20),
            traffic = traffic.sortedBy(TrafficSpawnDefinition::spawnAtSeconds),
            objectives = buildList {
                add(ObjectiveDefinition(ObjectiveType.COMPLETE_SAFE_MOVEMENTS, traffic.size, "Complete all ${traffic.size} movements"))
                if (arrivalCount > 0) add(ObjectiveDefinition(ObjectiveType.LAND_ARRIVALS, arrivalCount, "Land $arrivalCount arrivals"))
                if (departureCount > 0) add(ObjectiveDefinition(ObjectiveType.DEPART_AIRCRAFT, departureCount, "Depart $departureCount aircraft"))
                add(ObjectiveDefinition(ObjectiveType.LIMIT_STRIKES, 2, "Finish with fewer than three strikes"))
            },
            // Authored values are expressed in compact design points; the engine scores in tens.
            scoring = ScoringDefinition(
                thresholds = StarThresholds(
                    oneStar = thresholds.oneStar * 10,
                    twoStars = thresholds.twoStars * 10,
                    threeStars = thresholds.threeStars * 10,
                ),
            ),
            tutorialFocus = focus,
            mechanicVersions = mechanicVersions,
            dynamicEvents = dynamicEvents,
            weatherChanges = weatherChanges,
        )
    }

    private fun arrival(
        id: String,
        callsign: String,
        at: Int,
        entry: String,
        runway: String = "23R",
        altitude: Int,
        speed: Int = 220,
        performance: AircraftPerformanceClass = AircraftPerformanceClass.MEDIUM,
        fuelSeconds: Int = 720,
    ) = TrafficSpawnDefinition(
        id = id,
        callsign = callsign,
        intent = TrafficIntent.ARRIVAL,
        performanceClass = performance,
        spawnAtSeconds = at,
        entryFixId = entry,
        runwayEndId = runway,
        initialAltitudeFeet = altitude,
        initialSpeedKnots = speed,
        fuelSeconds = fuelSeconds,
    )

    private fun departure(
        id: String,
        callsign: String,
        at: Int,
        exit: String,
        runway: String = "23R",
        performance: AircraftPerformanceClass = AircraftPerformanceClass.MEDIUM,
    ) = TrafficSpawnDefinition(
        id = id,
        callsign = callsign,
        intent = TrafficIntent.DEPARTURE,
        performanceClass = performance,
        spawnAtSeconds = at,
        exitFixId = exit,
        runwayEndId = runway,
        initialAltitudeFeet = 0,
        initialSpeedKnots = 0,
        fuelSeconds = 900,
    )
}
