package com.stuart.atccontroller.data

import kotlin.math.max
import kotlin.math.min

/**
 * Produces deterministic endless-mode waves. A stage is finite so it can be resumed and scored;
 * completing it advances to the next, denser stage with the same player-visible seed.
 */
object EndlessScenarioGenerator {
    private const val MIN_ARRIVAL_FIX_REUSE_SECONDS = 65
    private val callsignPrefixes = listOf("NORTH", "CLOUD", "VECTOR", "EMBER", "ORBIT", "SUMMIT")

    fun generate(
        seed: Long,
        stage: Int,
        contentPackId: String = ContentRegistry.DEFAULT_PACK_ID,
    ): ScenarioDefinition {
        require(stage >= 1) { "Endless stage must be at least one" }
        val pack = requireNotNull(ContentRegistry.pack(contentPackId)) {
            "Unknown content pack $contentPackId"
        }
        val entryFixIds = pack.airport.fixes
            .filter { it.use == FixUse.ENTRY_AND_EXIT }
            .map { it.id }

        val intensity = min(stage, 100)
        val scenarioSeed = seed xor (stage.toLong() * -7046029254386353131L)
        val random = DeterministicRandom(scenarioSeed)
        // Alternate direction between stages while allowing the player seed to choose the first.
        val usesRunway23 = ((seed xor stage.toLong()) and 1L) == 0L
        val dualRunways = intensity >= 4
        val direction = if (usesRunway23) RunwayDirection.WESTERLY else RunwayDirection.EASTERLY
        val directionalEnds = pack.runwayEnds(direction)
        val activeEnds = if (dualRunways) directionalEnds else directionalEnds.take(1)

        val trafficCount = min(36, 8 + intensity * 2)
        val baseSpacingSeconds = max(18, 54 - intensity * 3)
        var spawnTime = 4
        val lastArrivalAtFix = mutableMapOf<String, Int>()
        val traffic = List(trafficCount) { index ->
            if (index > 0) {
                val jitter = random.nextInt(9) - 4
                spawnTime += max(12, baseSpacingSeconds + jitter)
            }

            val intent = when (index) {
                0 -> TrafficIntent.ARRIVAL
                1 -> TrafficIntent.DEPARTURE
                else -> if (random.nextInt(100) < min(48, 31 + intensity * 2)) {
                    TrafficIntent.DEPARTURE
                } else {
                    TrafficIntent.ARRIVAL
                }
            }
            val performanceClass = performanceFor(intensity, random)
            val runway = activeEnds[random.nextInt(activeEnds.size)]
            val fix = if (intent == TrafficIntent.ARRIVAL) {
                // Do not materialize an arrival already inside separation from unseen traffic at
                // the same gate. Even at the slowest arrival speed, 65 seconds provides >3 NM.
                val choices = entryFixIds.filter { candidate ->
                    spawnTime - lastArrivalAtFix.getOrDefault(candidate, Int.MIN_VALUE / 2) >=
                        MIN_ARRIVAL_FIX_REUSE_SECONDS
                }.ifEmpty { entryFixIds }
                choices[random.nextInt(choices.size)].also { selected ->
                    lastArrivalAtFix[selected] = spawnTime
                }
            } else {
                entryFixIds[random.nextInt(entryFixIds.size)]
            }
            val flightNumber = 100 + floorMod(
                floorMod(seed, 900L) + index * 47L + stage.toLong() * 19L,
                900L,
            ).toInt()

            TrafficSpawnDefinition(
                id = generatedAircraftId(pack.id, seed, stage, index),
                callsign = "${callsignPrefixes[index % callsignPrefixes.size]} $flightNumber",
                intent = intent,
                performanceClass = performanceClass,
                spawnAtSeconds = spawnTime,
                entryFixId = fix.takeIf { intent == TrafficIntent.ARRIVAL },
                exitFixId = fix.takeIf { intent == TrafficIntent.DEPARTURE },
                runwayEndId = runway,
                initialAltitudeFeet = if (intent == TrafficIntent.ARRIVAL) {
                    4_000 + random.nextInt(4) * 1_000
                } else {
                    0
                },
                initialSpeedKnots = if (intent == TrafficIntent.ARRIVAL) {
                    arrivalSpeed(performanceClass) + random.nextInt(3) * 10
                } else {
                    0
                },
                fuelSeconds = if (intent == TrafficIntent.ARRIVAL) 660 + random.nextInt(5) * 60 else 900,
            )
        }

        val scoring = ScoringDefinition(thresholds = StarThresholds(1, 2, 3))
        val maximumScore = traffic.sumOf { movement ->
            val safeMovementPoints = when (movement.intent) {
                TrafficIntent.ARRIVAL -> scoring.safeArrivalPoints
                TrafficIntent.DEPARTURE -> scoring.safeDeparturePoints
            }
            safeMovementPoints +
                scoring.maximumRouteEfficiencyBonusPoints +
                scoring.maximumTimeBonusPoints
        }
        val thresholds = StarThresholds(
            oneStar = (maximumScore * 55) / 100,
            twoStars = (maximumScore * 72) / 100,
            threeStars = (maximumScore * 88) / 100,
        )
        val windDirection = pack.airport.runwayEnds
            .first { it.id == activeEnds.first() }
            .headingDegrees

        return ScenarioDefinition(
            id = generatedScenarioId(pack.id, seed, stage),
            title = "Endless — Stage $stage",
            briefing = "Handle an escalating, seeded traffic wave. Fictional routes and simplified rules apply.",
            airportId = pack.airport.id,
            seed = scenarioSeed,
            difficulty = min(10, 3 + intensity),
            maxDurationSeconds = spawnTime + 480,
            runwayConfiguration = RunwayConfigurationDefinition(
                arrivalEndIds = activeEnds.toSet(),
                departureEndIds = activeEnds.toSet(),
            ),
            weather = WeatherDefinition(
                windDirectionDegrees = Math.floorMod(windDirection + random.nextInt(21) - 10, 360),
                windSpeedKnots = min(24, 6 + intensity + random.nextInt(5)),
                visibilityKm = max(5, 20 - intensity / 2),
            ),
            traffic = traffic,
            objectives = listOf(
                ObjectiveDefinition(
                    ObjectiveType.COMPLETE_SAFE_MOVEMENTS,
                    trafficCount,
                    "Complete the stage's $trafficCount movements",
                ),
                ObjectiveDefinition(ObjectiveType.LIMIT_STRIKES, 2, "Finish with fewer than three strikes"),
            ),
            scoring = scoring.copy(thresholds = thresholds),
            tutorialFocus = TutorialFocus.NONE,
            isEndless = true,
        )
    }

    private fun generatedScenarioId(packId: String, seed: Long, stage: Int): String =
        if (packId == ContentRegistry.DEFAULT_PACK_ID) {
            "manchester_endless_${seed.toULong().toString(16)}_$stage"
        } else {
            "${packId}_endless_${seed.toULong().toString(16)}_$stage"
        }

    private fun generatedAircraftId(packId: String, seed: Long, stage: Int, index: Int): String =
        if (packId == ContentRegistry.DEFAULT_PACK_ID) {
            "endless_${seed.toULong().toString(16)}_${stage}_$index"
        } else {
            "${packId}_endless_${seed.toULong().toString(16)}_${stage}_$index"
        }

    private fun performanceFor(stage: Int, random: DeterministicRandom): AircraftPerformanceClass {
        if (stage == 1) return AircraftPerformanceClass.MEDIUM
        return when (random.nextInt(100)) {
            in 0 until min(25, 8 + stage * 2) -> AircraftPerformanceClass.HEAVY
            in 75..99 -> AircraftPerformanceClass.LIGHT
            else -> AircraftPerformanceClass.MEDIUM
        }
    }

    private fun arrivalSpeed(performanceClass: AircraftPerformanceClass): Int = when (performanceClass) {
        AircraftPerformanceClass.LIGHT -> 180
        AircraftPerformanceClass.MEDIUM -> 210
        AircraftPerformanceClass.HEAVY -> 220
    }

    private fun floorMod(value: Long, divisor: Long): Long {
        val remainder = value % divisor
        return if (remainder < 0) remainder + divisor else remainder
    }
}

/** A stable SplitMix64-based PRNG so saved seeds do not change behavior across library updates. */
private class DeterministicRandom(seed: Long) {
    private var state = seed

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive" }
        return ((nextLong().ushr(1) % bound.toLong())).toInt()
    }

    private fun nextLong(): Long {
        state += -7046029254386353131L
        var value = state
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
}
