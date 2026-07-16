package com.stuart.atccontroller.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftConfigurationTest {
    @Test
    fun configurationRoundTripPreservesEveryOutcomeAffectingField() {
        val configuration = ShiftConfiguration(
            seed = -8_812_334L,
            density = TrafficDensity.BUSY,
            arrivalPercent = 70,
            runwayDirection = RunwayDirection.EASTERLY,
            weatherPreset = WeatherPreset.LOW_VISIBILITY,
            fuelPressure = FuelPressure.TIGHT,
            strikeLimit = 2,
            assists = ShiftAssists(false, false, true),
        )

        assertEquals(
            configuration,
            ShiftConfigurationCodec.decode(ShiftConfigurationCodec.encode(configuration)),
        )
        assertNull(ShiftConfigurationCodec.decode("ATC1.not-base64%%%"))
        assertNull(ShiftConfigurationCodec.decode("x".repeat(513)))
    }

    @Test
    fun namespacedCoastalCodeRoundTripsAndNeverUsesManchesterContent() {
        val configuration = ShiftConfiguration(
            contentPackId = CoastalContent.PACK_ID,
            seed = 73,
            density = TrafficDensity.BUSY,
            runwayDirection = RunwayDirection.EASTERLY,
        )
        val code = ShiftConfigurationCodec.encode(configuration)
        val scenario = CustomShiftGenerator.generate(configuration)

        assertTrue(code.startsWith("ATC2."))
        assertEquals(configuration, ShiftConfigurationCodec.decode(code))
        assertEquals(CoastalContent.AIRPORT_ID, scenario.airportId)
        assertTrue(scenario.runwayConfiguration.arrivalEndIds.all { it in setOf("27", "36") })
        assertTrue(scenario.traffic.all { spawn ->
            spawn.entryFixId == null || spawn.entryFixId in CoastalContent.airport.fixes.map { it.id }
        })
        assertTrue(scenario.traffic.none { it.runwayEndId.startsWith("23") || it.runwayEndId.startsWith("05") })
    }

    @Test
    fun legacyManchesterCodesKeepTheirOriginalImplicitNamespace() {
        val configuration = ShiftConfiguration(seed = 8675309)
        val code = ShiftConfigurationCodec.encode(configuration)

        assertTrue(code.startsWith("ATC1."))
        assertEquals(ContentRegistry.DEFAULT_PACK_ID, ShiftConfigurationCodec.decode(code)?.contentPackId)
    }

    @Test
    fun sameVersionAndConfigurationGenerateIdenticalScenarios() {
        val configuration = ShiftConfiguration(
            seed = 42,
            density = TrafficDensity.BALANCED,
            arrivalPercent = 40,
            weatherPreset = WeatherPreset.WINDY,
        )

        val first = CustomShiftGenerator.generate(configuration)
        val second = CustomShiftGenerator.generate(configuration)
        val changed = CustomShiftGenerator.generate(configuration.copy(seed = 43))

        assertEquals(first, second)
        assertNotEquals(first.id, changed.id)
        assertEquals(configuration.density.trafficLabel, first.traffic.size)
        assertEquals(configuration.strikeLimit, first.maxStrikes)
        assertTrue(first.traffic.all { it.fuelSeconds >= 240 })
    }

    @Test
    fun rankedPresetLocksAllOptionsIntoItsIdentity() {
        val ranked = ShiftConfiguration.rankedPreset(99)

        assertTrue(ranked.isRanked)
        assertEquals(ranked, ShiftConfigurationCodec.decode(ShiftConfigurationCodec.encode(ranked)))
    }

    @Test
    fun dailyConfigurationDependsOnlyOnLocalDateAndGeneratorVersion() {
        val date = LocalDate.of(2026, 7, 16)

        assertEquals(DailyShift.configurationFor(date), DailyShift.configurationFor(date))
        assertEquals(DailyShift.identityFor(date), DailyShift.identityFor(date))
        assertNotEquals(DailyShift.identityFor(date), DailyShift.identityFor(date.plusDays(1)))
    }

    @Test
    fun checksumRejectsOtherwiseWellFormedMutations() {
        val code = ShiftConfigurationCodec.encode(ShiftConfiguration(seed = 123))
        val replacement = if (code[8] == 'A') 'B' else 'A'
        val mutated = code.substring(0, 8) + replacement + code.substring(9)

        assertNull(ShiftConfigurationCodec.decode(mutated))
    }
}
