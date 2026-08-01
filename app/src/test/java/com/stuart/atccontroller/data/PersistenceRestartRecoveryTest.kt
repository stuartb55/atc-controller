package com.stuart.atccontroller.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the durable half of retry semantics. Each reconstruction creates a fresh repository,
 * operation factory, and coordinator over only the last committed Preferences snapshot, matching
 * the state available after process death.
 */
class PersistenceRestartRecoveryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun authoredResultFailureRestoresRecoveryAndRetryAfterRestartAwardsOnce() = runBlocking {
        val initialStore = RestartablePreferencesDataStore()
        val initialRepository = PlayerPreferencesRepository(initialStore)
        val session = activeSession("authored-session")
        val result = authoredResult("authored-result")
        initialRepository.saveActiveSession(session)
        initialStore.failNextUpdates(1)

        val failed = PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(initialRepository).authoredResult(result),
        )

        assertEquals(DurableWriteState.FAILED_RETRYABLE, failed.durableState)
        assertEquals(session, initialRepository.playerData.first().activeSession)
        assertTrue(initialRepository.playerData.first().serviceRecord.processedResultIds.isEmpty())

        val retriedStore = initialStore.restart()
        val retriedRepository = PlayerPreferencesRepository(retriedStore)
        val retried = PersistenceCoordinator(Dispatchers.Unconfined).retry(
            PlayerPersistenceOperations(retriedRepository).authoredResult(result),
        )

        assertTrue(retried.isSaved)
        assertNull(retriedRepository.playerData.first().activeSession)

        // A second process has no in-memory coordinator ledger. Repository-level result IDs must
        // still make replaying the terminal operation harmless.
        val duplicateStore = retriedStore.restart()
        val duplicateRepository = PlayerPreferencesRepository(duplicateStore)
        val duplicate = PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(duplicateRepository).authoredResult(result),
        )
        val committed = duplicateRepository.playerData.first()

        assertTrue(duplicate.isSaved)
        assertFalse(duplicate.alreadyCommitted)
        assertEquals(3, committed.progress.missionStars[result.missionId])
        assertEquals(4_200, committed.progress.missionBestScores[result.missionId])
        assertEquals(result.safeMovements, committed.serviceRecord.totalSafeMovements)
        assertEquals(listOf(result.resultId), committed.serviceRecord.processedResultIds)
    }

    @Test
    fun dailyResultFailureAndRepeatedPostRestartRetryCountOneAttempt() = runBlocking {
        val date = LocalDate.of(2026, 7, 26)
        val identity = DailyShift.identityFor(date)
        val resultId = "daily-result"
        val initialStore = RestartablePreferencesDataStore()
        val initialRepository = PlayerPreferencesRepository(initialStore)
        val session = activeSession("daily-session")
        initialRepository.saveActiveSession(session)
        initialStore.failNextUpdates(1)

        val failed = PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(initialRepository).dailyResult(
                localDate = date,
                configurationIdentity = identity,
                resultId = resultId,
                score = 3_000,
            ),
        )

        assertEquals(DurableWriteState.FAILED_RETRYABLE, failed.durableState)
        assertEquals(session, initialRepository.playerData.first().activeSession)
        assertTrue(initialRepository.playerData.first().dailyRecord.entries.isEmpty())

        val retriedStore = initialStore.restart()
        val retriedRepository = PlayerPreferencesRepository(retriedStore)
        val operation = PlayerPersistenceOperations(retriedRepository).dailyResult(
            localDate = date,
            configurationIdentity = identity,
            resultId = resultId,
            score = 3_000,
        )
        assertTrue(PersistenceCoordinator(Dispatchers.Unconfined).retry(operation).isSaved)

        val duplicateStore = retriedStore.restart()
        val duplicateRepository = PlayerPreferencesRepository(duplicateStore)
        PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(duplicateRepository).dailyResult(
                localDate = date,
                configurationIdentity = identity,
                resultId = resultId,
                score = 3_000,
            ),
        )
        val committed = duplicateRepository.playerData.first()
        val entry = committed.dailyRecord.entries.getValue(date.toString())

        assertNull(committed.activeSession)
        assertEquals(1, entry.completedAttempts)
        assertEquals(3_000, entry.bestScore)
        assertEquals(listOf(resultId), committed.dailyRecord.processedResultIds)
    }

    @Test
    fun endlessMilestoneAndPayoutRemainRecoverableAcrossBothFailurePoints() = runBlocking {
        val milestoneAttemptId = "endless-stage-1"
        val payoutAttemptId = "endless-payout"
        val milestone = EndlessMilestoneRecord(
            seed = 42,
            completedStage = 1,
            stageScore = 1_000,
            cumulativeScore = 1_000,
        )
        val initialStore = RestartablePreferencesDataStore()
        val initialRepository = PlayerPreferencesRepository(initialStore)
        val session = activeSession("endless-session")
        initialRepository.saveActiveSession(session)
        initialStore.failNextUpdates(1)

        val failedMilestone = PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(initialRepository).endlessMilestone(
                stableAttemptId = milestoneAttemptId,
                milestone = milestone,
            ),
        )

        assertEquals(DurableWriteState.FAILED_RETRYABLE, failedMilestone.durableState)
        assertEquals(session, initialRepository.playerData.first().activeSession)
        assertNull(initialRepository.playerData.first().endlessMilestone)

        val milestoneStore = initialStore.restart()
        val milestoneRepository = PlayerPreferencesRepository(milestoneStore)
        assertTrue(
            PersistenceCoordinator(Dispatchers.Unconfined).retry(
                PlayerPersistenceOperations(milestoneRepository).endlessMilestone(
                    stableAttemptId = milestoneAttemptId,
                    milestone = milestone,
                ),
            ).isSaved,
        )
        assertNull(milestoneRepository.playerData.first().activeSession)
        assertEquals(milestone, milestoneRepository.playerData.first().endlessMilestone)

        milestoneStore.failNextUpdates(1)
        val failedPayout = PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(milestoneRepository).endlessPayout(
                stableAttemptId = payoutAttemptId,
                contentPackId = ContentRegistry.DEFAULT_PACK_ID,
                score = milestone.cumulativeScore,
            ),
        )

        assertEquals(DurableWriteState.FAILED_RETRYABLE, failedPayout.durableState)
        assertEquals(milestone, milestoneRepository.playerData.first().endlessMilestone)
        assertEquals(0, milestoneRepository.playerData.first().progress.endlessHighScore)

        val payoutStore = milestoneStore.restart()
        val payoutRepository = PlayerPreferencesRepository(payoutStore)
        assertTrue(
            PersistenceCoordinator(Dispatchers.Unconfined).retry(
                PlayerPersistenceOperations(payoutRepository).endlessPayout(
                    stableAttemptId = payoutAttemptId,
                    contentPackId = ContentRegistry.DEFAULT_PACK_ID,
                    score = milestone.cumulativeScore,
                ),
            ).isSaved,
        )

        val duplicateStore = payoutStore.restart()
        val duplicateRepository = PlayerPreferencesRepository(duplicateStore)
        PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(duplicateRepository).endlessPayout(
                stableAttemptId = payoutAttemptId,
                contentPackId = ContentRegistry.DEFAULT_PACK_ID,
                score = milestone.cumulativeScore,
            ),
        )
        val committed = duplicateRepository.playerData.first()

        assertNull(committed.endlessMilestone)
        assertEquals(
            milestone.cumulativeScore,
            committed.progress.endlessHighScoreFor(ContentRegistry.DEFAULT_PACK_ID),
        )
    }

    @Test
    fun replayMetadataFailureLeavesOnlyOrphanThenRestartCleansAndRetries() = runBlocking {
        val replayDirectory = temporaryFolder.newFolder("restart-replay")
        val initialStore = RestartablePreferencesDataStore().apply {
            // saveCompletedReplay first prepares storage, then commits the metadata pointer.
            failUpdateCall(2)
        }
        val initialRepository = PlayerPreferencesRepository(
            initialStore,
            FileReplayPayloadStore(replayDirectory),
        )
        val replay = completedReplay("restart-replay")

        val failed = PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(initialRepository).completedReplay(replay),
        )

        assertEquals(DurableWriteState.FAILED_RETRYABLE, failed.durableState)
        // Do not collect playerData here: its startup maintenance intentionally removes orphans.
        // The complete but unreferenced file is exactly what a killed process can leave behind.
        assertEquals(1, replayDirectory.replayFiles().size)

        val retriedStore = initialStore.restart()
        val retriedRepository = PlayerPreferencesRepository(
            retriedStore,
            FileReplayPayloadStore(replayDirectory),
        )
        val maintenance = retriedRepository.prepareReplayStorage()
        assertEquals(1, maintenance.removedOrphanFiles)
        assertTrue(replayDirectory.replayFiles().isEmpty())

        assertTrue(
            PersistenceCoordinator(Dispatchers.Unconfined).retry(
                PlayerPersistenceOperations(retriedRepository).completedReplay(replay),
            ).isSaved,
        )

        val duplicateStore = retriedStore.restart()
        val duplicateRepository = PlayerPreferencesRepository(
            duplicateStore,
            FileReplayPayloadStore(replayDirectory),
        )
        PersistenceCoordinator(Dispatchers.Unconfined).execute(
            PlayerPersistenceOperations(duplicateRepository).completedReplay(replay),
        )
        val summaries = duplicateRepository.playerData.first().completedReplays
        val loaded = duplicateRepository.loadCompletedReplayResult(replay.id)

        assertEquals(1, summaries.size)
        assertEquals(replay.id, summaries.single().id)
        assertEquals(1, replayDirectory.replayFiles().size)
        assertEquals(replay.payload, (loaded as ReplayLoadResult.Loaded).replay.payload)
    }

    private fun authoredResult(resultId: String) = ValidatedMissionResult(
        resultId = resultId,
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

    private fun activeSession(id: String) = ActiveSessionRecord(
        schemaVersion = ReplayPolicy.MAX_SUPPORTED_SCHEMA,
        scenarioId = id,
        savedAtEpochMillis = 1,
        payload = "session-payload",
    )

    private fun completedReplay(id: String) = CompletedReplayRecord(
        schemaVersion = ReplayPolicy.MAX_SUPPORTED_SCHEMA,
        id = id,
        scenarioId = "scenario",
        savedAtEpochMillis = 10,
        terminalTick = 100,
        finalScore = 500,
        terminalHash = "terminal-hash",
        payload = "replay-v5\nD|A|mission\nS|0|1.0|true||||",
    )

    private fun File.replayFiles(): List<File> =
        listFiles().orEmpty().filter { it.name.endsWith(".replay") }
}

private class RestartablePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)
    private val failingCalls = mutableSetOf<Int>()
    private var updateCalls = 0

    override val data: Flow<Preferences> = state

    fun failNextUpdates(count: Int) {
        require(count >= 1)
        repeat(count) { offset -> failingCalls += updateCalls + offset + 1 }
    }

    fun failUpdateCall(call: Int) {
        require(call > updateCalls)
        failingCalls += call
    }

    fun restart() = RestartablePreferencesDataStore(state.value)

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        updateCalls += 1
        if (failingCalls.remove(updateCalls)) {
            throw IOException("injected DataStore failure at update $updateCalls")
        }
        transform(state.value).also { state.value = it }
    }
}
