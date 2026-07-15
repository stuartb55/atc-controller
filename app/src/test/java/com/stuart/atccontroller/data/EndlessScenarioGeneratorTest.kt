package com.stuart.atccontroller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndlessScenarioGeneratorTest {
    @Test
    fun sameSeedAndStageProduceIdenticalScenario() {
        val first = EndlessScenarioGenerator.generate(seed = 8675309L, stage = 6)
        val second = EndlessScenarioGenerator.generate(seed = 8675309L, stage = 6)

        assertEquals(first, second)
    }

    @Test
    fun differentSeedsProduceDifferentTraffic() {
        val first = EndlessScenarioGenerator.generate(seed = 11L, stage = 5)
        val second = EndlessScenarioGenerator.generate(seed = 12L, stage = 5)

        assertNotEquals(first.traffic, second.traffic)
    }

    @Test
    fun laterStagesIncreaseCountAndDensityAndEnableParallelRunways() {
        val early = EndlessScenarioGenerator.generate(seed = 42L, stage = 1)
        val later = EndlessScenarioGenerator.generate(seed = 42L, stage = 6)

        assertTrue(later.traffic.size > early.traffic.size)
        assertTrue(averageSpawnGap(later) < averageSpawnGap(early))
        assertEquals(2, later.runwayConfiguration.arrivalEndIds.size)
        assertTrue(later.traffic.any { it.intent == TrafficIntent.ARRIVAL })
        assertTrue(later.traffic.any { it.intent == TrafficIntent.DEPARTURE })
    }

    @Test
    fun runwayDirectionAlternatesBetweenStages() {
        val first = EndlessScenarioGenerator.generate(seed = 99L, stage = 1)
        val second = EndlessScenarioGenerator.generate(seed = 99L, stage = 2)

        val firstUses23 = first.runwayConfiguration.arrivalEndIds.any { it.startsWith("23") }
        val secondUses23 = second.runwayConfiguration.arrivalEndIds.any { it.startsWith("23") }
        assertNotEquals(firstUses23, secondUses23)
    }

    @Test
    fun representativeGeneratedStagesPassContentValidationAndEngineConversion() {
        listOf(1L, 7L, Long.MIN_VALUE, Long.MAX_VALUE).forEach { seed ->
            ((1..12).toList() + listOf(50, 86, 87, 100, 1_000)).forEach { stage ->
                val scenario = EndlessScenarioGenerator.generate(seed, stage)
                val validation = ScenarioValidator.validate(scenario)
                assertTrue("seed=$seed stage=$stage issues=${validation.issues}", validation.isValid)
                assertEquals(scenario.traffic.size, scenario.toSimulationScenario().traffic.size)
                assertTrue(
                    scenario.maxDurationSeconds - scenario.traffic.maxOf { it.spawnAtSeconds } >= 480,
                )
                scenario.traffic
                    .filter { it.intent == TrafficIntent.ARRIVAL }
                    .groupBy { it.entryFixId }
                    .values
                    .forEach { arrivalsAtFix ->
                        assertTrue(
                            arrivalsAtFix.zipWithNext().all { (first, second) ->
                                second.spawnAtSeconds - first.spawnAtSeconds >= 65
                            },
                        )
                    }
            }
        }
    }

    private fun averageSpawnGap(scenario: ScenarioDefinition): Double = scenario.traffic
        .zipWithNext { first, second -> second.spawnAtSeconds - first.spawnAtSeconds }
        .average()
}
