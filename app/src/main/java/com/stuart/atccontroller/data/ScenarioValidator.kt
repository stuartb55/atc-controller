package com.stuart.atccontroller.data

import kotlin.math.abs
import kotlin.math.atan2

data class ScenarioValidationIssue(
    val code: String,
    val path: String,
    val message: String,
)

data class ScenarioValidationResult(
    val issues: List<ScenarioValidationIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()

    fun requireValid() {
        require(isValid) {
            issues.joinToString(prefix = "Invalid scenario: ", separator = "; ") {
                "${it.path}: ${it.message}"
            }
        }
    }
}

/** Pure content validation suitable for unit tests and build-time content checks. */
object ScenarioValidator {
    fun validate(
        scenario: ScenarioDefinition,
        airport: AirportDefinition = ManchesterContent.airport,
    ): ScenarioValidationResult {
        val issues = validateAirport(airport).toMutableList()
        fun issue(code: String, path: String, message: String) {
            issues += ScenarioValidationIssue(code, path, message)
        }

        if (scenario.id.isBlank()) issue("blank_id", "scenario.id", "must not be blank")
        if (scenario.title.isBlank()) issue("blank_title", "scenario.title", "must not be blank")
        if (scenario.briefing.isBlank()) issue("blank_briefing", "scenario.briefing", "must not be blank")
        if (scenario.airportId != airport.id) {
            issue("airport_mismatch", "scenario.airportId", "does not match ${airport.id}")
        }
        if (scenario.difficulty !in 1..10) {
            issue("difficulty_range", "scenario.difficulty", "must be between 1 and 10")
        }
        if (scenario.maxDurationSeconds <= 0) {
            issue("duration_range", "scenario.maxDurationSeconds", "must be positive")
        }
        if (scenario.maxStrikes <= 0) {
            issue("strikes_range", "scenario.maxStrikes", "must be positive")
        }

        val runwayEnds = airport.runwayEnds.associateBy(RunwayEndDefinition::id)
        val arrivalRunways = scenario.runwayConfiguration.arrivalEndIds
        val departureRunways = scenario.runwayConfiguration.departureEndIds
        if (arrivalRunways.isEmpty() && scenario.traffic.any { it.intent == TrafficIntent.ARRIVAL }) {
            issue("missing_arrival_runway", "scenario.runwayConfiguration", "arrivals require an active arrival runway")
        }
        if (departureRunways.isEmpty() && scenario.traffic.any { it.intent == TrafficIntent.DEPARTURE }) {
            issue("missing_departure_runway", "scenario.runwayConfiguration", "departures require an active departure runway")
        }
        (arrivalRunways + departureRunways).forEach { runwayId ->
            if (runwayId !in runwayEnds) {
                issue("unknown_runway", "scenario.runwayConfiguration[$runwayId]", "does not exist at the airport")
            }
        }
        val activeByPhysicalRunway = (arrivalRunways + departureRunways)
            .mapNotNull(runwayEnds::get)
            .groupBy(RunwayEndDefinition::physicalRunwayId)
        activeByPhysicalRunway.forEach { (physicalId, ends) ->
            if (ends.map(RunwayEndDefinition::id).distinct().size > 1) {
                issue(
                    "reciprocal_ends_active",
                    "scenario.runwayConfiguration",
                    "physical runway $physicalId cannot use both directions at once",
                )
            }
        }

        if (scenario.weather.windDirectionDegrees !in 0..359) {
            issue("wind_direction_range", "scenario.weather.windDirectionDegrees", "must be from 0 to 359")
        }
        if (scenario.weather.windSpeedKnots !in 0..60) {
            issue("wind_speed_range", "scenario.weather.windSpeedKnots", "must be from 0 to 60")
        }
        if (scenario.weather.visibilityKm !in 1..100) {
            issue("visibility_range", "scenario.weather.visibilityKm", "must be from 1 to 100")
        }

        if (scenario.traffic.isEmpty()) issue("empty_traffic", "scenario.traffic", "must contain at least one movement")
        duplicateValues(scenario.traffic.map(TrafficSpawnDefinition::id)).forEach { duplicate ->
            issue("duplicate_traffic_id", "scenario.traffic", "duplicate id $duplicate")
        }
        duplicateValues(scenario.traffic.map(TrafficSpawnDefinition::callsign)).forEach { duplicate ->
            issue("duplicate_callsign", "scenario.traffic", "duplicate callsign $duplicate")
        }
        if (scenario.traffic.zipWithNext().any { (first, second) -> first.spawnAtSeconds > second.spawnAtSeconds }) {
            issue("traffic_not_ordered", "scenario.traffic", "must be ordered by spawn time")
        }
        val fixes = airport.fixes.associateBy(NavigationFixDefinition::id)
        scenario.traffic.forEachIndexed { index, traffic ->
            val path = "scenario.traffic[$index]"
            if (traffic.id.isBlank()) issue("blank_traffic_id", "$path.id", "must not be blank")
            if (traffic.callsign.isBlank()) issue("blank_callsign", "$path.callsign", "must not be blank")
            if (traffic.spawnAtSeconds < 0 || traffic.spawnAtSeconds >= scenario.maxDurationSeconds) {
                issue("spawn_time_range", "$path.spawnAtSeconds", "must occur within the scenario")
            }
            if (traffic.fuelSeconds <= 0) issue("fuel_range", "$path.fuelSeconds", "must be positive")

            when (traffic.intent) {
                TrafficIntent.ARRIVAL -> {
                    val entryFix = traffic.entryFixId?.let(fixes::get)
                    if (entryFix == null) {
                        issue("invalid_entry_fix", "$path.entryFixId", "must reference an airport entry fix")
                    } else if (entryFix.use == FixUse.EXIT) {
                        issue("invalid_entry_fix_use", "$path.entryFixId", "fix is exit-only")
                    }
                    if (traffic.exitFixId != null) {
                        issue("arrival_has_exit", "$path.exitFixId", "must be null for an arrival")
                    }
                    if (traffic.runwayEndId !in arrivalRunways) {
                        issue("inactive_arrival_runway", "$path.runwayEndId", "must be an active arrival runway")
                    }
                    if (traffic.initialAltitudeFeet !in 1_000..15_000) {
                        issue("arrival_altitude_range", "$path.initialAltitudeFeet", "must be between 1,000 and 15,000 feet")
                    }
                    if (traffic.initialSpeedKnots !in 100..350) {
                        issue("arrival_speed_range", "$path.initialSpeedKnots", "must be between 100 and 350 knots")
                    }
                }

                TrafficIntent.DEPARTURE -> {
                    val exitFix = traffic.exitFixId?.let(fixes::get)
                    if (exitFix == null) {
                        issue("invalid_exit_fix", "$path.exitFixId", "must reference an airport exit fix")
                    } else if (exitFix.use == FixUse.ENTRY) {
                        issue("invalid_exit_fix_use", "$path.exitFixId", "fix is entry-only")
                    }
                    if (traffic.entryFixId != null) {
                        issue("departure_has_entry", "$path.entryFixId", "must be null for a departure")
                    }
                    if (traffic.runwayEndId !in departureRunways) {
                        issue("inactive_departure_runway", "$path.runwayEndId", "must be an active departure runway")
                    }
                    if (traffic.initialAltitudeFeet != 0 || traffic.initialSpeedKnots != 0) {
                        issue("departure_initial_state", path, "holding departures must start stopped at ground level")
                    }
                }
            }
        }
        scenario.traffic.lastOrNull()?.let { last ->
            if (scenario.maxDurationSeconds - last.spawnAtSeconds < 30) {
                issue("insufficient_completion_time", "scenario.maxDurationSeconds", "must allow at least 30 seconds after the final spawn")
            }
        }

        val arrivalCount = scenario.traffic.count { it.intent == TrafficIntent.ARRIVAL }
        val departureCount = scenario.traffic.size - arrivalCount
        duplicateValues(scenario.objectives.map { it.type.name }).forEach { duplicate ->
            issue("duplicate_objective", "scenario.objectives", "duplicate objective $duplicate")
        }
        scenario.objectives.forEachIndexed { index, objective ->
            val path = "scenario.objectives[$index]"
            if (objective.description.isBlank()) issue("blank_objective", "$path.description", "must not be blank")
            if (objective.target < 0) issue("objective_target_range", "$path.target", "must not be negative")
            val maximum = when (objective.type) {
                ObjectiveType.COMPLETE_SAFE_MOVEMENTS -> scenario.traffic.size
                ObjectiveType.LAND_ARRIVALS -> arrivalCount
                ObjectiveType.DEPART_AIRCRAFT -> departureCount
                ObjectiveType.LIMIT_STRIKES -> scenario.maxStrikes - 1
                ObjectiveType.SCORE_AT_LEAST -> maximumPossibleScore(scenario)
            }
            if (objective.target.toLong() > maximum.toLong()) {
                issue("unachievable_objective", "$path.target", "exceeds the available maximum of $maximum")
            }
        }

        val scoring = scenario.scoring
        with(scenario.mechanicVersions) {
            if (listOf(runwayProcedures, wakeTurbulence, windDrift, reducedVisibility).any { it < 0 }) {
                issue("invalid_mechanic_version", "scenario.mechanicVersions", "versions must not be negative")
            }
            if (wakeTurbulence !in 0..1) {
                issue("unknown_wake_version", "scenario.mechanicVersions.wakeTurbulence", "only version 1 is supported")
            }
        }
        val scoringValues = listOf(
            scoring.safeArrivalPoints,
            scoring.safeDeparturePoints,
            scoring.maximumRouteEfficiencyBonusPoints,
            scoring.maximumTimeBonusPoints,
            scoring.completionBonus,
            scoring.conflictPenalty,
            scoring.avoidableGoAroundPenalty,
            scoring.manualGoAroundPenalty,
            scoring.missedExitPenalty,
            scoring.runwayProcedurePenalty,
            scoring.wakeViolationPenalty,
        )
        if (scoringValues.any { it < 0 }) {
            issue("negative_scoring_value", "scenario.scoring", "point and penalty values must not be negative")
        }
        if (!scoring.routeInefficiencyPenaltyPointsPerNm.isFinite() ||
            scoring.routeInefficiencyPenaltyPointsPerNm < 0.0 ||
            !scoring.timeBonusDecayPointsPerSecond.isFinite() ||
            scoring.timeBonusDecayPointsPerSecond < 0.0
        ) {
            issue(
                "invalid_scoring_rate",
                "scenario.scoring",
                "scoring rates must be finite and non-negative",
            )
        }
        val stars = with(scoring.thresholds) { listOf(oneStar, twoStars, threeStars) }
        if (stars.any { it <= 0 } || stars != stars.sorted() || stars.distinct().size != 3) {
            issue("star_threshold_order", "scenario.scoring.thresholds", "must contain three positive, strictly ascending values")
        }
        val maximumScore = maximumPossibleScore(scenario)
        if (stars.lastOrNull()?.let { it.toLong() > maximumScore } == true) {
            issue("unachievable_star_threshold", "scenario.scoring.thresholds.threeStar", "exceeds estimated maximum score $maximumScore")
        }

        return ScenarioValidationResult(issues)
    }

    fun validateAirport(airport: AirportDefinition): List<ScenarioValidationIssue> {
        val issues = mutableListOf<ScenarioValidationIssue>()
        fun issue(code: String, path: String, message: String) {
            issues += ScenarioValidationIssue(code, path, message)
        }

        if (airport.id.isBlank()) issue("blank_airport_id", "airport.id", "must not be blank")
        if (!airport.mapWidthNm.isFinite() || !airport.mapHeightNm.isFinite() ||
            airport.mapWidthNm <= 0.0 || airport.mapHeightNm <= 0.0
        ) {
            issue("airport_map_size", "airport", "map dimensions must be finite and positive")
        }
        duplicateValues(airport.physicalRunways.map(PhysicalRunwayDefinition::id)).forEach {
            issue("duplicate_physical_runway", "airport.physicalRunways", "duplicate id $it")
        }
        duplicateValues(airport.runwayEnds.map(RunwayEndDefinition::id)).forEach {
            issue("duplicate_runway_end", "airport.runwayEnds", "duplicate id $it")
        }
        duplicateValues(airport.fixes.map(NavigationFixDefinition::id)).forEach {
            issue("duplicate_fix", "airport.fixes", "duplicate id $it")
        }
        val physicalRunways = airport.physicalRunways.associateBy(PhysicalRunwayDefinition::id)
        val runwayEnds = airport.runwayEnds.associateBy(RunwayEndDefinition::id)
        airport.physicalRunways.forEachIndexed { index, runway ->
            if (runway.endIds.size != 2) {
                issue("runway_end_count", "airport.physicalRunways[$index].endIds", "must contain exactly two ends")
            }
            runway.endIds.forEach { endId ->
                if (runwayEnds[endId]?.physicalRunwayId != runway.id) {
                    issue("runway_end_membership", "airport.physicalRunways[$index].endIds", "$endId is missing or belongs to another runway")
                }
            }
        }
        airport.runwayEnds.forEachIndexed { index, end ->
            val path = "airport.runwayEnds[$index]"
            if (end.physicalRunwayId !in physicalRunways) {
                issue("unknown_physical_runway", "$path.physicalRunwayId", "does not exist")
            }
            val reciprocal = runwayEnds[end.reciprocalEndId]
            if (reciprocal == null || reciprocal.reciprocalEndId != end.id || reciprocal.physicalRunwayId != end.physicalRunwayId) {
                issue("invalid_reciprocal", "$path.reciprocalEndId", "must reference the reciprocal end on the same runway")
            }
            if (reciprocal != null && end.id < reciprocal.id && end.threshold == reciprocal.threshold) {
                issue(
                    "identical_runway_endpoints",
                    "$path.threshold",
                    "must differ from reciprocal end ${reciprocal.id}",
                )
            }
            if (end.headingDegrees !in 0..359) {
                issue("runway_heading_range", "$path.headingDegrees", "must be from 0 to 359")
            }
            validatePoint(end.threshold, "$path.threshold", issues)
            validatePoint(end.approachGate, "$path.approachGate", issues)
            if (airport.mapWidthNm.isFinite() && airport.mapHeightNm.isFinite() &&
                airport.mapWidthNm > 0.0 && airport.mapHeightNm > 0.0 &&
                end.threshold.isNormalized() && end.approachGate.isNormalized()
            ) {
                val bearing = approachBearing(end, airport)
                if (headingDifference(bearing, end.headingDegrees.toDouble()) > 18.0) {
                    issue(
                        "approach_heading_mismatch",
                        "$path.approachGate",
                        "does not align with runway heading ${end.headingDegrees}° within 18°",
                    )
                }
            }
        }
        airport.fixes.forEachIndexed { index, fix ->
            if (fix.id.isBlank()) issue("blank_fix_id", "airport.fixes[$index].id", "must not be blank")
            validatePoint(fix.position, "airport.fixes[$index].position", issues)
        }
        if (airport.source.effectiveDateIso.matches(Regex("\\d{4}-\\d{2}-\\d{2}")).not()) {
            issue("source_date_format", "airport.source.effectiveDateIso", "must use YYYY-MM-DD")
        }
        if (!airport.source.sourceUrl.startsWith("https://")) {
            issue("source_url", "airport.source.sourceUrl", "must be an HTTPS URL")
        }
        if (airport.source.disclaimer.isBlank()) {
            issue("missing_disclaimer", "airport.source.disclaimer", "must explain the entertainment-only simplification")
        }
        return issues
    }

    private fun maximumPossibleScore(scenario: ScenarioDefinition): Long = with(scenario.scoring) {
        scenario.traffic.sumOf { traffic ->
            val movementPoints = when (traffic.intent) {
                TrafficIntent.ARRIVAL -> safeArrivalPoints
                TrafficIntent.DEPARTURE -> safeDeparturePoints
            }
            movementPoints.toLong() +
                maximumRouteEfficiencyBonusPoints +
                maximumTimeBonusPoints
        } + completionBonus
    }

    private fun validatePoint(
        point: NormalizedPoint,
        path: String,
        issues: MutableList<ScenarioValidationIssue>,
    ) {
        if (!point.x.isFinite() || !point.y.isFinite() || point.x !in 0.0..1.0 || point.y !in 0.0..1.0) {
            issues += ScenarioValidationIssue("coordinate_range", path, "must be finite and normalized from 0 to 1")
        }
    }

    private fun NormalizedPoint.isNormalized() =
        x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0

    private fun approachBearing(end: RunwayEndDefinition, airport: AirportDefinition): Double {
        val eastNm = (end.threshold.x - end.approachGate.x) * airport.mapWidthNm
        val northNm = (end.approachGate.y - end.threshold.y) * airport.mapHeightNm
        return ((Math.toDegrees(atan2(eastNm, northNm)) % 360.0) + 360.0) % 360.0
    }

    private fun headingDifference(first: Double, second: Double): Double =
        abs(((first - second + 540.0) % 360.0) - 180.0)

    private fun duplicateValues(values: List<String>): Set<String> = values
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
}
