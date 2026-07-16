package com.stuart.atccontroller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRegistryTest {
    @Test
    fun registryValidatesTwoNamespacedOfflinePacks() {
        assertEquals(2, ContentRegistry.packs.size)
        assertTrue(ContentRegistry.validate().isEmpty())
        assertEquals(
            ContentRegistry.authoredMissions.size,
            ContentRegistry.missionIds.distinct().size,
        )
        ContentRegistry.packs.forEach { pack ->
            assertTrue(ScenarioValidator.validateAirport(pack.airport).isEmpty())
            pack.authoredMissions.forEach { mission ->
                assertEquals(pack.airport.id, mission.airportId)
                assertNotNull(ContentRegistry.mission(mission.id))
                assertTrue(ScenarioValidator.validate(mission, pack.airport).isValid)
            }
        }
    }

    @Test
    fun adaptersResolveTheOwningAirportWithoutMixingRunwaysOrTraffic() {
        val manchester = ContentRegistry.pack(ContentRegistry.DEFAULT_PACK_ID)!!
            .authoredMissions.first().toSimulationScenario()
        val coastal = ContentRegistry.pack(CoastalContent.PACK_ID)!!
            .authoredMissions.first().toSimulationScenario()

        assertTrue(manchester.runways.all { it.id in setOf("23R", "23L", "05L", "05R") })
        assertTrue(coastal.runways.all { it.id in setOf("09", "27", "18", "36") })
        assertTrue(manchester.traffic.all { it.aircraft.id.startsWith("m") })
        assertTrue(coastal.traffic.all { it.aircraft.id.startsWith("c") })
    }

    @Test
    fun eachCampaignUnlocksIndependentlyAndLegacyManchesterIdsRemainStable() {
        val manchesterFirst = ManchesterContent.FIRST_MISSION_ID
        val coastalFirst = CoastalContent.FIRST_MISSION_ID
        val defaults = PlayerProgress().unlockedMissionIds

        assertEquals(setOf(manchesterFirst, coastalFirst), defaults)
        assertTrue(
            ManchesterContent.nextMissionId(manchesterFirst) in
                unlockedAfterMissionCompletion(defaults, manchesterFirst),
        )
        assertTrue(
            ContentRegistry.nextMissionId(coastalFirst) in
                unlockedAfterMissionCompletion(defaults, coastalFirst),
        )
    }
}
