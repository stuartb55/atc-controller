package com.stuart.atccontroller.data

/**
 * An original fictional crossing-runway sector used to contrast with Manchester's parallel layout.
 * It deliberately avoids representing a current real-world aerodrome.
 */
object CoastalContent {
    const val PACK_ID = "fictional_coastal_v1"
    const val AIRPORT_ID = "fictional_coastal"
    const val FIRST_MISSION_ID = "coastal_01_harbour_arrivals"

    val airport = AirportDefinition(
        id = AIRPORT_ID,
        displayName = "Harbour Approach (fictional)",
        mapWidthNm = 20.0,
        mapHeightNm = 20.0,
        physicalRunways = listOf(
            PhysicalRunwayDefinition("09-27", "09 / 27", setOf("09", "27")),
            PhysicalRunwayDefinition("18-36", "18 / 36", setOf("18", "36")),
        ),
        runwayEnds = listOf(
            RunwayEndDefinition("09", "09-27", "27", point(.25, .55), point(.05, .55), 90),
            RunwayEndDefinition("27", "09-27", "09", point(.75, .55), point(.95, .55), 270),
            RunwayEndDefinition("18", "18-36", "36", point(.52, .25), point(.52, .05), 180),
            RunwayEndDefinition("36", "18-36", "18", point(.52, .82), point(.52, .98), 0),
        ),
        fixes = listOf(
            fix("COAST_NORTH", "Coast North", .50, .03),
            fix("COAST_EAST", "Harbour East", .97, .48),
            fix("COAST_SOUTH", "Island South", .55, .97),
            fix("COAST_WEST", "Bay West", .03, .52),
            fix("COAST_NORTHEAST", "Cliff Gate", .88, .12),
        ),
        source = ContentSourceMetadata(
            sourceName = "Original fictional sector layout",
            effectiveDateIso = "2026-07-16",
            sourceUrl = "https://github.com/stuartb55/atc-controller",
            attribution = "Original fictional crossing-runway layout created for this entertainment game.",
            disclaimer = "Fictional and deliberately simplified. Not for navigation, operational use, or air traffic control training.",
        ),
    )

    val authoredMissions = listOf(
        mission(
            number = 1,
            id = FIRST_MISSION_ID,
            title = "Harbour Arrivals",
            briefing = "Guide three arrivals through the compact fictional coastal sector.",
            focus = TutorialFocus.SELECTION_AND_ROUTING,
            configuration = configuration("09"),
            traffic = listOf(
                arrival("c01_a1", "TIDAL 201", 0, "COAST_EAST", "09", 4_000),
                arrival("c01_a2", "BEACON 314", 55, "COAST_NORTH", "09", 5_000),
                arrival("c01_a3", "ISLAND 122", 115, "COAST_NORTHEAST", "09", 4_000),
            ),
            thresholds = StarThresholds(1_900, 2_700, 3_400),
        ),
        mission(
            number = 2,
            id = "coastal_02_crossing_flow",
            title = "Crossing Flow",
            briefing = "Coordinate the crossing 09 and 18 strips before line-up, crossing, landing, or takeoff.",
            focus = TutorialFocus.RUNWAY_OCCUPANCY,
            configuration = RunwayConfigurationDefinition(setOf("09", "18"), setOf("09", "18")),
            traffic = listOf(
                departure("c02_d1", "TIDAL 508", 5, "COAST_WEST", "18"),
                arrival("c02_a1", "BEACON 619", 25, "COAST_EAST", "09", 4_000),
                departure("c02_d2", "ISLAND 430", 85, "COAST_SOUTH", "09"),
                arrival("c02_a2", "CLIFF 744", 120, "COAST_NORTH", "18", 5_000),
            ),
            thresholds = StarThresholds(2_300, 3_300, 4_200),
            mechanics = MechanicVersionDefinition(runwayProcedures = 1),
        ),
        mission(
            number = 3,
            id = "coastal_03_bay_holds",
            title = "Bay Holds",
            briefing = "Use named-fix holds and explicit handoffs around the compact crossing-runway flow.",
            focus = TutorialFocus.PROCEDURAL_CONTROL,
            configuration = RunwayConfigurationDefinition(setOf("09", "18"), setOf("09", "18")),
            traffic = listOf(
                arrival("c03_a1", "TIDAL 117", 5, "COAST_EAST", "09", 5_000),
                departure("c03_d1", "BEACON 482", 35, "COAST_WEST", "18"),
                arrival("c03_a2", "ISLAND 630", 80, "COAST_NORTH", "18", 6_000),
                departure("c03_d2", "CLIFF 905", 140, "COAST_SOUTH", "09"),
                arrival("c03_a3", "TIDAL 241", 195, "COAST_NORTHEAST", "09", 5_000),
            ),
            thresholds = StarThresholds(3_000, 4_200, 5_300),
            mechanics = MechanicVersionDefinition(runwayProcedures = 1, proceduralControl = 1),
        ),
        mission(
            number = 4,
            id = "coastal_04_squall_recovery",
            title = "Squall Recovery",
            briefing = "Recover from a fictional closure and prediction outage while the active direction changes.",
            focus = TutorialFocus.DYNAMIC_EVENTS,
            configuration = RunwayConfigurationDefinition(setOf("09", "18"), setOf("09", "18")),
            traffic = listOf(
                arrival("c04_a1", "BEACON 911", 5, "COAST_EAST", "09", 5_000),
                departure("c04_d1", "TIDAL 608", 30, "COAST_WEST", "18"),
                arrival("c04_a2", "ISLAND 724", 85, "COAST_NORTH", "18", 6_000),
                departure("c04_d2", "CLIFF 337", 145, "COAST_SOUTH", "09"),
                arrival("c04_a3", "TIDAL 415", 210, "COAST_NORTHEAST", "09", 5_000),
            ),
            thresholds = StarThresholds(3_200, 4_600, 5_800),
            mechanics = MechanicVersionDefinition(
                runwayProcedures = 1,
                windDrift = 1,
                reducedVisibility = 1,
                dynamicEvents = 1,
                weatherChanges = 1,
            ),
            dynamicEvents = listOf(
                DynamicEventDefinition(
                    "coastal_closure",
                    DynamicEventTypeDefinition.RUNWAY_CLOSURE,
                    90,
                    20,
                    90,
                    DynamicRecoveryGoalDefinition.KEEP_RUNWAY_CLEAR,
                    runwayEndId = "09",
                ),
                DynamicEventDefinition(
                    "coastal_outage",
                    DynamicEventTypeDefinition.EQUIPMENT_OUTAGE,
                    170,
                    20,
                    80,
                    DynamicRecoveryGoalDefinition.CONTROL_WITHOUT_PREDICTION,
                ),
            ),
            weatherChanges = listOf(
                WeatherChangeDefinition(
                    "coastal_northerly",
                    280,
                    60,
                    WeatherDefinition(350, 15, 6),
                    setOf("27", "36"),
                ),
            ),
        ),
    )

    private fun mission(
        number: Int,
        id: String,
        title: String,
        briefing: String,
        focus: TutorialFocus,
        configuration: RunwayConfigurationDefinition,
        traffic: List<TrafficSpawnDefinition>,
        thresholds: StarThresholds,
        mechanics: MechanicVersionDefinition = MechanicVersionDefinition(),
        dynamicEvents: List<DynamicEventDefinition> = emptyList(),
        weatherChanges: List<WeatherChangeDefinition> = emptyList(),
    ) = ScenarioDefinition(
        id = id,
        title = title,
        briefing = briefing,
        airportId = AIRPORT_ID,
        seed = 20_000L + number,
        difficulty = 5 + number,
        maxDurationSeconds = maxOf(620, traffic.maxOf { it.spawnAtSeconds } + 360),
        runwayConfiguration = configuration,
        weather = WeatherDefinition(100, 8 + number, if (number == 4) 7 else 18),
        traffic = traffic,
        objectives = buildList {
            add(ObjectiveDefinition(ObjectiveType.COMPLETE_SAFE_MOVEMENTS, traffic.size, "Complete all ${traffic.size} movements"))
            val arrivals = traffic.count { it.intent == TrafficIntent.ARRIVAL }
            val departures = traffic.size - arrivals
            if (arrivals > 0) add(ObjectiveDefinition(ObjectiveType.LAND_ARRIVALS, arrivals, "Land $arrivals arrivals"))
            if (departures > 0) add(ObjectiveDefinition(ObjectiveType.DEPART_AIRCRAFT, departures, "Depart $departures aircraft"))
            add(ObjectiveDefinition(ObjectiveType.LIMIT_STRIKES, 2, "Finish with fewer than three strikes"))
        },
        scoring = ScoringDefinition(thresholds = thresholds),
        tutorialFocus = focus,
        mechanicVersions = mechanics,
        dynamicEvents = dynamicEvents,
        weatherChanges = weatherChanges,
    )

    private fun configuration(runway: String) = RunwayConfigurationDefinition(setOf(runway), setOf(runway))

    private fun arrival(
        id: String,
        callsign: String,
        at: Int,
        entry: String,
        runway: String,
        altitude: Int,
    ) = TrafficSpawnDefinition(
        id, callsign, TrafficIntent.ARRIVAL, AircraftPerformanceClass.MEDIUM, at,
        entryFixId = entry, runwayEndId = runway, initialAltitudeFeet = altitude,
        initialSpeedKnots = 210, fuelSeconds = 720,
    )

    private fun departure(
        id: String,
        callsign: String,
        at: Int,
        exit: String,
        runway: String,
    ) = TrafficSpawnDefinition(
        id, callsign, TrafficIntent.DEPARTURE, AircraftPerformanceClass.MEDIUM, at,
        exitFixId = exit, runwayEndId = runway, initialAltitudeFeet = 0,
        initialSpeedKnots = 0, fuelSeconds = 900,
    )

    private fun point(x: Double, y: Double) = NormalizedPoint(x, y)
    private fun fix(id: String, name: String, x: Double, y: Double) = NavigationFixDefinition(
        id,
        name,
        point(x, y),
        FixUse.ENTRY_AND_EXIT,
        isFictional = true,
    )
}
