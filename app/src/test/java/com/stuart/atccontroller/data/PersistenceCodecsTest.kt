package com.stuart.atccontroller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceCodecsTest {
    @Test
    fun missionStarsRoundTripIdentifiersAndSortDeterministically() {
        val stars = mapOf(
            "mission:two/β" to 2,
            "mission one" to 3,
        )

        val encoded = MissionStarsCodec.encode(stars)

        assertEquals(stars, MissionStarsCodec.decode(encoded))
        assertEquals(encoded, MissionStarsCodec.encode(stars.toList().reversed().toMap()))
    }

    @Test
    fun missionStarsSkipMalformedEntriesAndKeepBestDuplicate() {
        val validId = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("valid".toByteArray())
        val decoded = MissionStarsCodec.decode("bad;$validId:1;$validId:3;$validId:99;%%%:2")

        assertEquals(mapOf("valid" to 3), decoded)
    }

    @Test
    fun missionResultsRoundTripUnicodeAndSortDeterministically() {
        val results = mapOf(
            "mission:two/β" to MissionResultRecord(stars = 2, bestScore = 12_450),
            "mission one" to MissionResultRecord(stars = 0, bestScore = 0),
            "legacy" to MissionResultRecord(stars = 3, bestScore = null),
        )

        val encoded = MissionResultsCodec.encode(results)

        assertEquals(results, MissionResultsCodec.decode(encoded))
        assertEquals(encoded, MissionResultsCodec.encode(results.toList().reversed().toMap()))
    }

    @Test
    fun missionResultsSkipMalformedEntriesAndMergeIndependentBests() {
        val validId = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("valid".toByteArray())
        val decoded = MissionResultsCodec.decode(
            "$validId:1:900;$validId:3:;$validId:2:1200;bad;%%%:2:500;$validId:9:9999",
        )

        assertEquals(
            mapOf("valid" to MissionResultRecord(stars = 3, bestScore = 1_200)),
            decoded,
        )
    }

    @Test
    fun versionTwoResultsMergeWithLegacyStarsWithoutDroppingZeroStarCompletion() {
        val first = ManchesterContent.FIRST_MISSION_ID
        val second = ManchesterContent.missionIds[1]
        val merged = mergeMissionResults(
            legacyStars = mapOf(first to 0, second to 3, "unknown" to 3),
            results = mapOf(
                first to MissionResultRecord(stars = 2, bestScore = 4_200),
                second to MissionResultRecord(stars = 1, bestScore = null),
            ),
        )

        assertEquals(mapOf(first to 2, second to 3), merged.stars)
        assertEquals(mapOf(first to 4_200), merged.bestScores)
    }

    @Test
    fun activeSessionRoundTripsOpaqueUnicodePayload() {
        val session = ActiveSessionRecord(
            schemaVersion = 2,
            scenarioId = "manchester:endless/42",
            savedAtEpochMillis = 1_752_579_000_000L,
            payload = "aircraft=3:route=α→β\npaused=true",
        )

        assertEquals(session, SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(session)))
    }

    @Test
    fun malformedOrUnsupportedSessionEncodingRecoversAsNoSession() {
        assertNull(SessionSnapshotCodec.decode(null))
        assertNull(SessionSnapshotCodec.decode(""))
        assertNull(SessionSnapshotCodec.decode("not:a:session"))
        assertNull(SessionSnapshotCodec.decode("0:bWlzc2lvbg:123:cGF5bG9hZA"))
        assertNull(SessionSnapshotCodec.decode("1:%%%:123:cGF5bG9hZA"))
    }

    @Test
    fun progressTotalsStars() {
        val progress = PlayerProgress(missionStars = mapOf("one" to 1, "two" to 3))
        assertEquals(4, progress.totalStars)
        assertTrue(ManchesterContent.FIRST_MISSION_ID in progress.unlockedMissionIds)
    }

    @Test
    fun zeroStarMissionStillCountsAsCompleted() {
        val first = ManchesterContent.FIRST_MISSION_ID
        val progress = PlayerProgress(missionStars = mapOf(first to 0))

        assertEquals(0, progress.totalStars)
        assertEquals(1, progress.completedMissionCount)
    }

    @Test
    fun completedMissionUnlocksNextEvenWhenResultHasZeroStars() {
        val first = ManchesterContent.FIRST_MISSION_ID
        val second = ManchesterContent.nextMissionId(first)!!

        val unlocked = unlockedAfterMissionCompletion(emptySet(), first)

        assertEquals(setOf(first, second), unlocked)
    }
}
