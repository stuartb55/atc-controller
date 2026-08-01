package com.stuart.atccontroller.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReplayPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sharedPolicyAcceptsByteAndCommandLimitsAndRejectsLimitPlusOne() {
        val limitMinusOne = replay(payload = "x".repeat(ReplayPolicy.MAX_ENCODED_BYTES - 1))
        val limit = replay(payload = "x".repeat(ReplayPolicy.MAX_ENCODED_BYTES))
        val limitPlusOne = replay(payload = "x".repeat(ReplayPolicy.MAX_ENCODED_BYTES + 1))

        assertNull(ReplayPolicy.validate(limitMinusOne))
        assertNull(ReplayPolicy.validate(limit))
        assertEquals(
            ReplayPolicyViolation.PAYLOAD_TOO_LARGE,
            ReplayPolicy.validate(limitPlusOne),
        )
        // Character count alone is insufficient: beta occupies two UTF-8 bytes.
        assertEquals(
            ReplayPolicyViolation.PAYLOAD_TOO_LARGE,
            ReplayPolicy.validate(replay(payload = "β".repeat(250_001))),
        )

        val commandLimit = replay(
            payload = buildReplayPayload(ReplayPolicy.MAX_COMMAND_COUNT),
        )
        val commandLimitPlusOne = replay(
            payload = buildReplayPayload(ReplayPolicy.MAX_COMMAND_COUNT + 1),
        )
        assertNull(ReplayPolicy.validate(commandLimit))
        assertEquals(
            ReplayPolicyViolation.TOO_MANY_COMMANDS,
            ReplayPolicy.validate(commandLimitPlusOne),
        )
    }

    @Test
    fun interruptedAtomicReplacementKeepsPreviousCompletePayload() = runBlocking {
        val directory = temporaryFolder.newFolder("atomic")
        val fileId = "a".repeat(64)
        val working = FileReplayPayloadStore(directory)
        working.writeAtomically(fileId, "previous".toByteArray())

        val interrupted = FileReplayPayloadStore(directory) { _, _ ->
            throw IOException("injected before rename")
        }
        val failure = runCatching {
            interrupted.writeAtomically(fileId, "replacement".toByteArray())
        }

        assertTrue(failure.exceptionOrNull() is IOException)
        assertArrayEquals("previous".toByteArray(), working.read(fileId))
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun metadataIsBoundedToFiveNewestRecordsAndContainsNoPayload() {
        val metadata = (0..5).map { index ->
            replay(
                id = "replay-$index",
                savedAt = index.toLong(),
                payload = "payload-$index",
            ).let(::replayMetadataFor)
        }

        val decoded = CompletedReplayMetadataCodec.decode(
            CompletedReplayMetadataCodec.encode(metadata),
        )

        assertEquals(ReplayPolicy.MAX_REPLAY_COUNT, decoded.size)
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), decoded.map { it.savedAtEpochMillis })
        assertTrue(decoded.map(CompletedReplayMetadata::asSummary).all { it.payload.isEmpty() })
    }

    @Test
    fun repositoryRetainsFiveFilesAndLoadsOnlyTheRequestedPayload() = runBlocking {
        val directory = temporaryFolder.newFolder("repository")
        val dataStore = TestPreferencesDataStore()
        val repository = PlayerPreferencesRepository(
            dataStore,
            FileReplayPayloadStore(directory),
        )
        (0..5).forEach { index ->
            repository.saveCompletedReplay(
                replay(
                    id = "replay-$index",
                    savedAt = index.toLong(),
                    payload = "payload-$index",
                ),
            )
        }

        val summaries = repository.playerData.first().completedReplays
        val loaded = repository.loadCompletedReplayResult("replay-5")

        assertEquals(ReplayPolicy.MAX_REPLAY_COUNT, summaries.size)
        assertTrue(summaries.all { it.payload.isEmpty() && !it.isPayloadLoaded })
        assertTrue(summaries.none { it.id == "replay-0" })
        assertEquals("payload-5", (loaded as ReplayLoadResult.Loaded).replay.payload)
        assertEquals(ReplayPolicy.MAX_REPLAY_COUNT, directory.payloadFiles().size)
    }

    @Test
    fun corruptPayloadIsReportedAndItsMetadataIsQuarantined() = runBlocking {
        val directory = temporaryFolder.newFolder("corrupt")
        val dataStore = TestPreferencesDataStore()
        val repository = PlayerPreferencesRepository(
            dataStore,
            FileReplayPayloadStore(directory),
        )
        repository.saveCompletedReplay(replay(id = "corrupt-me", payload = "valid"))
        val summary = repository.playerData.first().completedReplays.single()
        File(directory, "${summary.payloadFileId}.replay").writeText("tampered")

        val result = repository.loadCompletedReplayResult(summary.id)

        assertEquals(
            ReplayLoadResult.Corrupt(summary.id, ReplayCorruptionReason.INVALID_SIZE),
            result,
        )
        assertTrue(repository.playerData.first().completedReplays.isEmpty())
        assertTrue(directory.payloadFiles().isEmpty())
    }

    @Test
    fun legacyMigrationSurvivesFailureAndRetryWithoutDanglingMetadata() = runBlocking {
        val legacyReplay = replay(id = "legacy", payload = "legacy-payload")
        val legacyKey = stringPreferencesKey("completed_replays_v1")
        val metadataKey = stringPreferencesKey("completed_replays_v2")
        val dataStore = TestPreferencesDataStore(
            mutablePreferencesOf(legacyKey to CompletedReplayCodec.encode(listOf(legacyReplay))),
        )
        val store = FailingReplayPayloadStore()
        val repository = PlayerPreferencesRepository(dataStore, store)

        val failed = repository.prepareReplayStorage()

        assertTrue(failed.migrationPending)
        assertEquals(ReplayStorageFailure.IO_RETRYABLE, failed.failure)
        assertTrue(dataStore.data.first()[legacyKey]!!.isNotBlank())
        assertNull(dataStore.data.first()[metadataKey])
        assertTrue(store.payloads.isEmpty())

        store.failWrites = false
        val retried = repository.prepareReplayStorage()
        val loaded = repository.loadCompletedReplayResult(legacyReplay.id)

        assertFalse(retried.migrationPending)
        assertEquals(1, retried.migratedLegacyRecords)
        assertNull(dataStore.data.first()[legacyKey])
        assertTrue(dataStore.data.first()[metadataKey]!!.isNotBlank())
        val loadedReplay = (loaded as ReplayLoadResult.Loaded).replay
        assertEquals(legacyReplay.id, loadedReplay.id)
        assertEquals(legacyReplay.scenarioId, loadedReplay.scenarioId)
        assertEquals(legacyReplay.payload, loadedReplay.payload)
        assertEquals(legacyReplay.terminalHash, loadedReplay.terminalHash)
        assertTrue(loadedReplay.payloadFileId?.length == 64)
    }

    @Test
    fun maintenanceRemovesOrphansAndMissingMetadataWithoutReadingPayloads() = runBlocking {
        val directory = temporaryFolder.newFolder("maintenance")
        val dataStore = TestPreferencesDataStore()
        val storage = CountingReplayPayloadStore(FileReplayPayloadStore(directory))
        val repository = PlayerPreferencesRepository(dataStore, storage)
        repository.saveCompletedReplay(replay(id = "kept", payload = "kept"))
        val summary = repository.playerData.first().completedReplays.single()
        val orphanId = "b".repeat(64)
        storage.writeAtomically(orphanId, "orphan".toByteArray())
        File(directory, "${summary.payloadFileId}.replay").delete()
        storage.readCount = 0

        val maintenance = repository.prepareReplayStorage()

        assertEquals(1, maintenance.removedMissingRecords)
        assertEquals(1, maintenance.removedOrphanFiles)
        assertEquals(0, storage.readCount)
        assertTrue(repository.playerData.first().completedReplays.isEmpty())
        assertTrue(directory.payloadFiles().isEmpty())
    }

    @Test
    fun settingsReadDoesNotInspectReplayStorageOrDecodeLegacyPayload() = runBlocking {
        val legacyKey = stringPreferencesKey("completed_replays_v1")
        val dataStore = TestPreferencesDataStore(
            mutablePreferencesOf(
                legacyKey to CompletedReplayCodec.encode(
                    listOf(replay(payload = "x".repeat(100_000))),
                ),
            ),
        )
        val store = CountingReplayPayloadStore(
            FileReplayPayloadStore(temporaryFolder.newFolder("settings")),
        )
        val repository = PlayerPreferencesRepository(dataStore, store)

        assertEquals(PlayerSettings(), repository.settings.first())
        assertEquals(0, store.existingFileIdsCount)
        assertEquals(0, store.readCount)
    }

    @Test
    fun authoredTerminalCommitIsAtomicAndRetryingStableResultDoesNotDuplicate() = runBlocking {
        val dataStore = TestPreferencesDataStore()
        val repository = PlayerPreferencesRepository(dataStore)
        val session = activeSession()
        val result = ValidatedMissionResult(
            resultId = "authored-attempt",
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
        repository.saveActiveSession(session)
        dataStore.failNextUpdate()

        assertTrue(runCatching { repository.commitValidatedMissionResult(result) }.isFailure)
        val failed = repository.playerData.first()
        assertEquals(session, failed.activeSession)
        assertTrue(failed.progress.missionStars.isEmpty())
        assertEquals(ControllerServiceRecord(), failed.serviceRecord)

        repository.commitValidatedMissionResult(result)
        repository.commitValidatedMissionResult(result)
        val committed = repository.playerData.first()
        assertNull(committed.activeSession)
        assertEquals(3, committed.progress.missionStars[result.missionId])
        assertEquals(3, committed.serviceRecord.totalSafeMovements)
        assertEquals(listOf(result.resultId), committed.serviceRecord.processedResultIds)
    }

    @Test
    fun dailyAndPracticeTerminalCommitsRetainRecoveryUntilTheirTransactionSucceeds() = runBlocking {
        val dataStore = TestPreferencesDataStore()
        val repository = PlayerPreferencesRepository(dataStore)
        val date = LocalDate.of(2026, 7, 26)
        val dailyIdentity = DailyShift.identityFor(date)
        val practice = PracticeResultRecord(
            resultId = "practice-attempt",
            configurationIdentity = ShiftConfigurationCodec.encode(ShiftConfiguration(seed = 7)),
            score = 2_500,
            stars = 2,
            completedAtEpochMillis = 10,
            rankedPreset = false,
        )

        repository.saveActiveSession(activeSession())
        dataStore.failNextUpdate()
        assertTrue(
            runCatching {
                repository.commitDailyResult(date, dailyIdentity, "daily-attempt", 3_000)
            }.isFailure,
        )
        assertTrue(repository.playerData.first().dailyRecord.entries.isEmpty())
        assertEquals(activeSession(), repository.playerData.first().activeSession)

        repository.commitDailyResult(date, dailyIdentity, "daily-attempt", 3_000)
        repository.commitDailyResult(date, dailyIdentity, "daily-attempt", 3_000)
        assertEquals(
            1,
            repository.playerData.first().dailyRecord.entries.getValue(date.toString())
                .completedAttempts,
        )

        repository.saveActiveSession(activeSession())
        dataStore.failNextUpdate()
        assertTrue(runCatching { repository.commitPracticeResult(practice) }.isFailure)
        assertTrue(repository.playerData.first().practiceResults.isEmpty())
        assertEquals(activeSession(), repository.playerData.first().activeSession)

        repository.commitPracticeResult(practice)
        repository.commitPracticeResult(practice)
        assertNull(repository.playerData.first().activeSession)
        assertEquals(listOf(practice), repository.playerData.first().practiceResults)
    }

    @Test
    fun endlessMilestoneAndPayoutDoNotAdvanceAcrossFailedTransactions() = runBlocking {
        val dataStore = TestPreferencesDataStore()
        val repository = PlayerPreferencesRepository(dataStore)
        val milestone = EndlessMilestoneRecord(
            seed = 42,
            completedStage = 1,
            stageScore = 1_000,
            cumulativeScore = 1_000,
        )
        repository.saveActiveSession(activeSession())
        dataStore.failNextUpdate()

        assertTrue(runCatching { repository.saveEndlessMilestone(milestone) }.isFailure)
        assertEquals(activeSession(), repository.playerData.first().activeSession)
        assertNull(repository.playerData.first().endlessMilestone)

        repository.saveEndlessMilestone(milestone)
        assertNull(repository.playerData.first().activeSession)
        assertEquals(milestone, repository.playerData.first().endlessMilestone)

        dataStore.failNextUpdate()
        assertTrue(
            runCatching {
                repository.commitEndlessPayout(ContentRegistry.DEFAULT_PACK_ID, 1_000)
            }.isFailure,
        )
        assertEquals(milestone, repository.playerData.first().endlessMilestone)
        assertEquals(0, repository.playerData.first().progress.endlessHighScore)

        repository.commitEndlessPayout(ContentRegistry.DEFAULT_PACK_ID, 1_000)
        repository.commitEndlessPayout(ContentRegistry.DEFAULT_PACK_ID, 1_000)
        assertNull(repository.playerData.first().endlessMilestone)
        assertEquals(1_000, repository.playerData.first().progress.endlessHighScore)
    }

    @Test
    fun settingsAndTrainingFailuresKeepLastCommitUntilRetry() = runBlocking {
        val dataStore = TestPreferencesDataStore()
        val repository = PlayerPreferencesRepository(dataStore)
        val desiredSettings = PlayerSettings(musicVolume = 0.2f, highContrast = true)
        val desiredTraining = TrainingState(
            activeLessonId = "ALTITUDE",
            activeStep = 2,
            completedLessonIds = setOf("SELECTION"),
        )

        dataStore.failNextUpdate()
        assertTrue(runCatching { repository.setSettings(desiredSettings) }.isFailure)
        assertEquals(PlayerSettings(), repository.settings.first())
        repository.setSettings(desiredSettings)
        assertEquals(desiredSettings, repository.settings.first())

        dataStore.failNextUpdate()
        assertTrue(runCatching { repository.saveTrainingState(desiredTraining) }.isFailure)
        assertEquals(TrainingState(), repository.playerData.first().trainingState)
        repository.saveTrainingState(desiredTraining)
        assertEquals(desiredTraining, repository.playerData.first().trainingState)
    }

    private fun replay(
        id: String = "replay",
        savedAt: Long = 1L,
        payload: String = "payload",
    ) = CompletedReplayRecord(
        schemaVersion = ReplayPolicy.MAX_SUPPORTED_SCHEMA,
        id = id,
        scenarioId = "scenario",
        savedAtEpochMillis = savedAt,
        terminalTick = 100L,
        finalScore = 500,
        terminalHash = "hash",
        payload = payload,
    )

    private fun buildReplayPayload(commandCount: Int): String = buildString {
        append("replay-v5\nD|A|mission\nS|0|1.0|true||||")
        repeat(commandCount) { append("\nC|$it|command") }
    }

    private fun File.payloadFiles(): List<File> =
        listFiles().orEmpty().filter { it.name.endsWith(".replay") }

    private fun activeSession() = ActiveSessionRecord(
        schemaVersion = ReplayPolicy.MAX_SUPPORTED_SCHEMA,
        scenarioId = "scenario",
        savedAtEpochMillis = 1,
        payload = "session",
    )
}

private class TestPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)
    private var updatesToFail = 0

    override val data: Flow<Preferences> = state

    fun failNextUpdate() {
        updatesToFail += 1
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        if (updatesToFail > 0) {
            updatesToFail -= 1
            throw IOException("injected DataStore failure")
        }
        transform(state.value).also { state.value = it }
    }
}

private class FailingReplayPayloadStore : ReplayPayloadStore {
    val payloads = mutableMapOf<String, ByteArray>()
    var failWrites = true

    override suspend fun writeAtomically(fileId: String, bytes: ByteArray) {
        if (failWrites) throw IOException("injected")
        payloads[fileId] = bytes.copyOf()
    }

    override suspend fun read(fileId: String): ByteArray? = payloads[fileId]?.copyOf()

    override suspend fun delete(fileId: String): Boolean {
        payloads.remove(fileId)
        return true
    }

    override suspend fun existingFileIds(): Set<String> = payloads.keys.toSet()
}

private class CountingReplayPayloadStore(
    private val delegate: ReplayPayloadStore,
) : ReplayPayloadStore {
    var readCount = 0
    var existingFileIdsCount = 0

    override suspend fun writeAtomically(fileId: String, bytes: ByteArray) =
        delegate.writeAtomically(fileId, bytes)

    override suspend fun read(fileId: String): ByteArray? {
        readCount += 1
        return delegate.read(fileId)
    }

    override suspend fun delete(fileId: String): Boolean = delegate.delete(fileId)

    override suspend fun existingFileIds(): Set<String> {
        existingFileIdsCount += 1
        return delegate.existingFileIds()
    }
}
