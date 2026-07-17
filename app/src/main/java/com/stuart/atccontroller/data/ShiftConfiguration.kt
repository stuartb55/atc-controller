package com.stuart.atccontroller.data

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.roundToInt

enum class TrafficDensity(val stage: Int, val trafficLabel: Int) {
    LIGHT(1, 10),
    BALANCED(4, 16),
    BUSY(8, 24),
}

enum class RunwayDirection { WESTERLY, EASTERLY }
enum class WeatherPreset { CALM, WINDY, LOW_VISIBILITY }
enum class FuelPressure(val multiplier: Double) { RELAXED(1.25), STANDARD(1.0), TIGHT(.72) }

data class ShiftAssists(
    val routeSnapping: Boolean = true,
    val approachSetup: Boolean = true,
    val conflictPrediction: Boolean = true,
)

data class ShiftConfiguration(
    val generatorVersion: Int = CURRENT_GENERATOR_VERSION,
    val contentPackId: String = ContentRegistry.DEFAULT_PACK_ID,
    val seed: Long = 20_260_716L,
    val density: TrafficDensity = TrafficDensity.BALANCED,
    val arrivalPercent: Int = 60,
    val runwayDirection: RunwayDirection = RunwayDirection.WESTERLY,
    val weatherPreset: WeatherPreset = WeatherPreset.CALM,
    val fuelPressure: FuelPressure = FuelPressure.STANDARD,
    val strikeLimit: Int = 3,
    val assists: ShiftAssists = ShiftAssists(),
    val rankedPresetId: String? = null,
) {
    init {
        require(generatorVersion == CURRENT_GENERATOR_VERSION)
        require(contentPackId.isNotBlank())
        require(arrivalPercent in 20..80 && arrivalPercent % 10 == 0)
        require(strikeLimit in 1..5)
        require(rankedPresetId == null || rankedPresetId == STANDARD_RANKED_PRESET)
        if (rankedPresetId != null) {
            require(density == TrafficDensity.BALANCED && arrivalPercent == 60)
            require(runwayDirection == RunwayDirection.WESTERLY)
            require(weatherPreset == WeatherPreset.CALM && fuelPressure == FuelPressure.STANDARD)
            require(strikeLimit == 3 && assists == ShiftAssists())
        }
    }

    val isRanked: Boolean get() = rankedPresetId != null

    companion object {
        const val CURRENT_GENERATOR_VERSION = 1
        const val STANDARD_RANKED_PRESET = "standard_v1"

        fun rankedPreset(
            seed: Long,
            contentPackId: String = ContentRegistry.DEFAULT_PACK_ID,
        ) = ShiftConfiguration(
            contentPackId = contentPackId,
            seed = seed,
            density = TrafficDensity.BALANCED,
            arrivalPercent = 60,
            runwayDirection = RunwayDirection.WESTERLY,
            weatherPreset = WeatherPreset.CALM,
            fuelPressure = FuelPressure.STANDARD,
            strikeLimit = 3,
            assists = ShiftAssists(),
            rankedPresetId = STANDARD_RANKED_PRESET,
        )
    }
}

object ShiftConfigurationCodec {
    private const val LEGACY_PREFIX = "ATC1"
    private const val NAMESPACED_PREFIX = "ATC2"
    private const val MAX_CODE_LENGTH = 512

    fun encode(configuration: ShiftConfiguration): String {
        val legacyFields = listOf(
            configuration.generatorVersion,
            configuration.seed,
            configuration.density.name,
            configuration.arrivalPercent,
            configuration.runwayDirection.name,
            configuration.weatherPreset.name,
            configuration.fuelPressure.name,
            configuration.strikeLimit,
            if (configuration.assists.routeSnapping) 1 else 0,
            if (configuration.assists.approachSetup) 1 else 0,
            if (configuration.assists.conflictPrediction) 1 else 0,
            configuration.rankedPresetId.orEmpty(),
        )
        val namespaced = configuration.contentPackId != ContentRegistry.DEFAULT_PACK_ID
        val prefix = if (namespaced) NAMESPACED_PREFIX else LEGACY_PREFIX
        val payload = (if (namespaced) {
            listOf(configuration.contentPackId) + legacyFields
        } else {
            legacyFields
        }).joinToString("|")
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$prefix.$encoded.${checksum(encoded)}"
    }

    fun decode(encoded: String?): ShiftConfiguration? {
        if (encoded.isNullOrBlank() || encoded.length > MAX_CODE_LENGTH) {
            return null
        }
        return runCatching {
            val codeParts = encoded.split('.')
            require(codeParts.size == 3 && codeParts[0] in setOf(LEGACY_PREFIX, NAMESPACED_PREFIX))
            require(codeParts[2] == checksum(codeParts[1]))
            val payload = String(
                Base64.getUrlDecoder().decode(codeParts[1]),
                StandardCharsets.UTF_8,
            )
            val parts = payload.split('|')
            val namespaced = codeParts[0] == NAMESPACED_PREFIX
            require(parts.size == if (namespaced) 13 else 12)
            val offset = if (namespaced) 1 else 0
            val contentPackId = if (namespaced) parts[0] else ContentRegistry.DEFAULT_PACK_ID
            require(ContentRegistry.pack(contentPackId) != null)
            ShiftConfiguration(
                generatorVersion = parts[offset].toInt(),
                contentPackId = contentPackId,
                seed = parts[offset + 1].toLong(),
                density = TrafficDensity.valueOf(parts[offset + 2]),
                arrivalPercent = parts[offset + 3].toInt(),
                runwayDirection = RunwayDirection.valueOf(parts[offset + 4]),
                weatherPreset = WeatherPreset.valueOf(parts[offset + 5]),
                fuelPressure = FuelPressure.valueOf(parts[offset + 6]),
                strikeLimit = parts[offset + 7].toInt(),
                assists = ShiftAssists(
                    routeSnapping = parts[offset + 8].toStrictFlag(),
                    approachSetup = parts[offset + 9].toStrictFlag(),
                    conflictPrediction = parts[offset + 10].toStrictFlag(),
                ),
                rankedPresetId = parts[offset + 11].takeIf(String::isNotBlank),
            )
        }.getOrNull()
    }

    private fun String.toStrictFlag(): Boolean = when (this) {
        "0" -> false
        "1" -> true
        else -> error("Invalid flag")
    }

    private fun checksum(encodedPayload: String): String = CRC32().run {
        update(encodedPayload.toByteArray(StandardCharsets.UTF_8))
        value.toString(36).padStart(7, '0')
    }
}

object CustomShiftGenerator {
    fun generate(configuration: ShiftConfiguration): ScenarioDefinition {
        val pack = requireNotNull(ContentRegistry.pack(configuration.contentPackId))
        val airport = pack.airport
        val base = EndlessScenarioGenerator.generate(
            configuration.seed,
            configuration.density.stage,
            configuration.contentPackId,
        )
        val ends = pack.runwayEnds(configuration.runwayDirection)
        val boundaryFixes = airport.fixes.filter { it.use == FixUse.ENTRY_AND_EXIT }
        val traffic = base.traffic.mapIndexed { index, original ->
            val arrival = ((index * 37 + floorMod(configuration.seed, 100L).toInt()) % 100) <
                configuration.arrivalPercent
            val fix = original.entryFixId ?: original.exitFixId
                ?: boundaryFixes[index % boundaryFixes.size].id
            original.copy(
                id = "custom_${configuration.seed.toULong().toString(16)}_$index",
                intent = if (arrival) TrafficIntent.ARRIVAL else TrafficIntent.DEPARTURE,
                entryFixId = fix.takeIf { arrival },
                exitFixId = fix.takeIf { !arrival },
                runwayEndId = ends[index % ends.size],
                initialAltitudeFeet = if (arrival) maxOf(4_000, original.initialAltitudeFeet) else 0,
                initialSpeedKnots = if (arrival) maxOf(190, original.initialSpeedKnots) else 0,
                fuelSeconds = (original.fuelSeconds * configuration.fuelPressure.multiplier)
                    .roundToInt()
                    .coerceAtLeast(240),
            )
        }
        val arrivals = traffic.count { it.intent == TrafficIntent.ARRIVAL }
        val departures = traffic.size - arrivals
        val weather = when (configuration.weatherPreset) {
            WeatherPreset.CALM -> WeatherDefinition(
                airport.runwayEnds.first { it.id == ends.first() }.headingDegrees,
                6,
                20,
            )
            WeatherPreset.WINDY -> WeatherDefinition(
                Math.floorMod(
                    airport.runwayEnds.first { it.id == ends.first() }.headingDegrees + 40,
                    360,
                ),
                22,
                16,
            )
            WeatherPreset.LOW_VISIBILITY -> WeatherDefinition(
                airport.runwayEnds.first { it.id == ends.first() }.headingDegrees,
                10,
                5,
            )
        }
        val identity = ShiftConfigurationCodec.encode(configuration).substringAfter('.').take(16)
        return base.copy(
            id = if (configuration.contentPackId == ContentRegistry.DEFAULT_PACK_ID) {
                "manchester_custom_v${configuration.generatorVersion}_$identity"
            } else {
                "${configuration.contentPackId}_custom_v${configuration.generatorVersion}_$identity"
            },
            title = if (configuration.isRanked) "Ranked seeded shift" else "Custom practice shift",
            briefing = "A deterministic local configuration. Practice results do not change campaign or service records.",
            maxStrikes = configuration.strikeLimit,
            runwayConfiguration = RunwayConfigurationDefinition(ends.toSet(), ends.toSet()),
            weather = weather,
            traffic = traffic,
            objectives = buildList {
                add(ObjectiveDefinition(ObjectiveType.COMPLETE_SAFE_MOVEMENTS, traffic.size, "Complete ${traffic.size} movements"))
                if (arrivals > 0) add(ObjectiveDefinition(ObjectiveType.LAND_ARRIVALS, arrivals, "Land $arrivals arrivals"))
                if (departures > 0) add(ObjectiveDefinition(ObjectiveType.DEPART_AIRCRAFT, departures, "Depart $departures aircraft"))
                add(ObjectiveDefinition(ObjectiveType.LIMIT_STRIKES, configuration.strikeLimit - 1, "Stay below the strike limit"))
            },
            mechanicVersions = base.mechanicVersions.copy(
                runwayProcedures = 1,
                windDrift = if (configuration.weatherPreset == WeatherPreset.CALM) 0 else 1,
                reducedVisibility = if (configuration.weatherPreset == WeatherPreset.LOW_VISIBILITY) 1 else 0,
            ),
            isEndless = false,
        )
    }

    private fun floorMod(value: Long, divisor: Long): Long {
        val remainder = value % divisor
        return if (remainder < 0) remainder + divisor else remainder
    }
}

object DailyShift {
    private val challengeEpoch: LocalDate = LocalDate.of(2026, 1, 1)

    fun configurationFor(localDate: LocalDate): ShiftConfiguration {
        val day = localDate.toEpochDay() - challengeEpoch.toEpochDay()
        val mixed = mix64(day xor 0x4154434441494c59L)
        return ShiftConfiguration(
            seed = mixed,
            density = TrafficDensity.entries[floorMod(mixed, TrafficDensity.entries.size.toLong()).toInt()],
            arrivalPercent = 40 + floorMod(mixed ushr 8, 4).toInt() * 10,
            runwayDirection = if ((mixed and 1L) == 0L) {
                RunwayDirection.WESTERLY
            } else {
                RunwayDirection.EASTERLY
            },
            weatherPreset = WeatherPreset.entries[
                floorMod(mixed ushr 16, WeatherPreset.entries.size.toLong()).toInt()
            ],
            fuelPressure = FuelPressure.entries[
                floorMod(mixed ushr 24, FuelPressure.entries.size.toLong()).toInt()
            ],
            strikeLimit = 3,
            assists = ShiftAssists(),
        )
    }

    fun identityFor(localDate: LocalDate): String =
        "${localDate}|${ShiftConfigurationCodec.encode(configurationFor(localDate))}"

    private fun mix64(input: Long): Long {
        var value = input + -7046029254386353131L
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    private fun floorMod(value: Long, divisor: Long): Long {
        val remainder = value % divisor
        return if (remainder < 0) remainder + divisor else remainder
    }
}
