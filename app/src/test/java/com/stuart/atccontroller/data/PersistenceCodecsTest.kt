package com.stuart.atccontroller.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceCodecsTest {
    @Test
    fun trainingStateCodecRetainsLessonProgressAndRejectsUnknownVersion() {
        val state = TrainingState(
            activeLessonId = "PARALLEL_RUNWAYS",
            activeStep = 3,
            completedLessonIds = setOf("ALTITUDE", "SPEED"),
        )

        assertEquals(state, TrainingStateCodec.decode(TrainingStateCodec.encode(state)))
        assertEquals(TrainingState(), TrainingStateCodec.decode("99:bad:2:"))
    }

    @Test
    fun completedReplayCodecIsBoundedAndIgnoresMalformedRecords() {
        val records = (0..7).map { index ->
            CompletedReplayRecord(
                schemaVersion = 2,
                id = "replay-$index",
                scenarioId = "scenario",
                savedAtEpochMillis = index.toLong(),
                terminalTick = 100L + index,
                finalScore = index,
                terminalHash = "hash-$index",
                payload = "payload-$index",
            )
        }

        val decoded = CompletedReplayCodec.decode(CompletedReplayCodec.encode(records) + "\nmalformed")

        assertEquals(5, decoded.size)
        assertEquals(records.take(5), decoded)
    }

    @Test
    fun serviceRecordUpdatesEligibleResultsOnceAndRoundTripsHonestly() {
        val first = ValidatedMissionResult(
            resultId = "attempt-1",
            missionId = ManchesterContent.FIRST_MISSION_ID,
            focus = TutorialFocus.SELECTION_AND_ROUTING,
            stars = 3,
            score = 4_200,
            completionSeconds = 180,
            safeMovements = 3,
            strikes = 0,
            departures = 1,
            missedExits = 0,
            routeEfficiencyPercent = 90,
        )
        val once = ControllerServiceRecord().updatedWith(first)
        val duplicate = once.updatedWith(first)
        val decoded = ControllerServiceRecordCodec.decode(
            ControllerServiceRecordCodec.encode(duplicate),
        )

        assertEquals(once, duplicate)
        assertEquals(once, decoded)
        assertEquals(3, decoded.totalSafeMovements)
        assertEquals(3, decoded.currentSafeStreak)
        assertEquals(180, decoded.bestCompletionSecondsByMission[first.missionId])
        assertEquals(1, decoded.masteryByFocus.getValue(first.focus).attempts)
        assertEquals(ServiceAchievements.all, decoded.achievementIds)
    }

    @Test
    fun practiceResultsHaveTheirOwnBoundedConfigurationNamespace() {
        val configuration = ShiftConfiguration(seed = 7)
        val records = (0..24).map { index ->
            PracticeResultRecord(
                resultId = "practice-$index",
                configurationIdentity = ShiftConfigurationCodec.encode(configuration.copy(seed = index.toLong())),
                score = index * 100,
                stars = index % 4,
                completedAtEpochMillis = index.toLong(),
                rankedPreset = false,
            )
        }

        val decoded = PracticeResultCodec.decode(PracticeResultCodec.encode(records))

        assertEquals(records.take(20), decoded)
        assertEquals(emptyList<PracticeResultRecord>(), PracticeResultCodec.decode("malformed"))
    }

    @Test
    fun dailyRecordCountsEachDateOnceAndKeepsRetryHistoryExplainable() {
        val firstDate = LocalDate.of(2026, 7, 15)
        val secondDate = firstDate.plusDays(1)
        val firstIdentity = DailyShift.identityFor(firstDate)
        val secondIdentity = DailyShift.identityFor(secondDate)
        val record = DailyServiceRecord()
            .updatedWith(firstDate, firstIdentity, "daily-1", 2_000)
            .updatedWith(firstDate, firstIdentity, "daily-2", 2_500)
            .updatedWith(firstDate, firstIdentity, "daily-2", 9_999)
            .updatedWith(secondDate, secondIdentity, "daily-3", 3_000)
        val decoded = DailyServiceRecordCodec.decode(DailyServiceRecordCodec.encode(record))

        assertEquals(record, decoded)
        assertEquals(2, decoded.entries.size)
        assertEquals(2, decoded.entries.getValue(firstDate.toString()).completedAttempts)
        assertEquals(2_500, decoded.entries.getValue(firstDate.toString()).bestScore)
        assertEquals(2, decoded.currentStreak)
        assertEquals(2, decoded.bestStreak)
    }

    @Test
    fun endlessMilestoneRoundTripsPendingChoicesAndRejectsInvalidBoundaries() {
        EndlessMilestoneChoice.entries.forEach { choice ->
            val milestone = EndlessMilestoneRecord(
                seed = -8_675_309L,
                completedStage = 7,
                stageScore = 4_200,
                cumulativeScore = 18_900,
                choice = choice,
            )

            assertEquals(
                milestone,
                EndlessMilestoneCodec.decode(EndlessMilestoneCodec.encode(milestone)),
            )
        }

        assertNull(EndlessMilestoneCodec.decode(null))
        assertNull(EndlessMilestoneCodec.decode("1|42|0|200|200|AWAITING"))
        assertNull(EndlessMilestoneCodec.decode("1|42|2|300|200|AWAITING"))
        assertNull(EndlessMilestoneCodec.decode("1|42|2|100|200|UNKNOWN"))
        assertNull(EndlessMilestoneCodec.decode("2|42|2|100|200|AWAITING"))
    }

    @Test
    fun endlessPersistenceMigratesLegacyManchesterAndKeepsPackScoresSeparate() {
        val legacyMilestone = EndlessMilestoneCodec.decode("1|42|2|100|200|AWAITING")!!
        val scores = EndlessHighScoreCodec.decode(
            EndlessHighScoreCodec.encode(
                mapOf(
                    ContentRegistry.DEFAULT_PACK_ID to 5_000,
                    CoastalContent.PACK_ID to 2_500,
                ),
            ),
            legacyManchesterScore = 6_000,
        )

        assertEquals(ContentRegistry.DEFAULT_PACK_ID, legacyMilestone.contentPackId)
        assertEquals(6_000, scores[ContentRegistry.DEFAULT_PACK_ID])
        assertEquals(2_500, scores[CoastalContent.PACK_ID])
        assertEquals(0, PlayerProgress(endlessHighScores = scores).endlessHighScoreFor("unknown"))
    }

    @Test
    fun unsafeResultResetsCurrentStreakWithoutErasingTheBestOrFabricatingHistory() {
        val safe = ValidatedMissionResult(
            "safe",
            ManchesterContent.FIRST_MISSION_ID,
            TutorialFocus.SELECTION_AND_ROUTING,
            2,
            3_000,
            200,
            3,
            0,
            0,
            0,
            70,
        )
        val unsafe = safe.copy(resultId = "unsafe", strikes = 1, safeMovements = 2)
        val record = ControllerServiceRecord().updatedWith(safe).updatedWith(unsafe)

        assertEquals(5, record.totalSafeMovements)
        assertEquals(0, record.currentSafeStreak)
        assertEquals(3, record.bestSafeStreak)
        assertEquals(ControllerServiceRecord(), ControllerServiceRecordCodec.decode(null))
        assertEquals(ControllerServiceRecord(), ControllerServiceRecordCodec.decode("malformed"))
    }

    @Test
    fun recordedCompletionRepairsMissingNextMissionUnlock() {
        val first = ManchesterContent.FIRST_MISSION_ID
        val next = ManchesterContent.nextMissionId(first)!!
        val repaired = reconciledMissionUnlocks(emptySet(), setOf(first))
        assertTrue(first in repaired)
        assertTrue(next in repaired)
    }
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

        assertEquals(ContentRegistry.firstMissionIds + second, unlocked)
    }
}
