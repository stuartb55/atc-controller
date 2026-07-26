package com.stuart.atccontroller.data

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@JvmInline
value class PersistenceOperationId(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= MAX_ID_LENGTH) {
            "Persistence operation id must contain 1..$MAX_ID_LENGTH characters"
        }
    }

    companion object {
        private const val MAX_ID_LENGTH = 160

        /**
         * Produces a stable, log-safe ID without retaining a raw attempt/player identifier.
         */
        fun forStableKey(kind: PersistenceOperationKind, stableKey: String): PersistenceOperationId {
            require(stableKey.isNotBlank()) { "Stable persistence key must not be blank" }
            val digest = sha256(stableKey.toByteArray(StandardCharsets.UTF_8)).take(32)
            return PersistenceOperationId("${kind.name.lowercase(Locale.ROOT)}:$digest")
        }
    }
}

enum class PersistenceOperationKind {
    SETTINGS,
    TRAINING,
    AUTHORED_RESULT,
    DAILY_RESULT,
    PRACTICE_RESULT,
    ENDLESS_MILESTONE,
    ENDLESS_PAYOUT,
    COMPLETED_REPLAY,
    ACTIVE_SESSION,
    OTHER,
}

enum class DurableWriteState {
    SAVING,
    SAVED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
}

enum class PersistenceRecoverability {
    NOT_APPLICABLE,
    RETRYABLE,
    PERMANENT,
}

enum class PersistenceFailureReason {
    IO,
    PERMISSION,
    INVALID_DATA,
    CANCELLED,
    UNKNOWN,
}

data class PersistenceOperationStatus(
    val operationId: PersistenceOperationId,
    val kind: PersistenceOperationKind,
    val state: DurableWriteState,
    val attempt: Int,
    val recoverability: PersistenceRecoverability,
    val failureReason: PersistenceFailureReason? = null,
)

data class PersistenceOperationResult(
    val operationId: PersistenceOperationId,
    val kind: PersistenceOperationKind,
    val durableState: DurableWriteState,
    val recoverability: PersistenceRecoverability,
    val attempts: Int,
    val failureReason: PersistenceFailureReason? = null,
    /** True when a duplicate in the coordinator's bounded idempotency ledger was skipped. */
    val alreadyCommitted: Boolean = false,
) {
    val isSaved: Boolean get() = durableState == DurableWriteState.SAVED
    val canRetry: Boolean get() = durableState == DurableWriteState.FAILED_RETRYABLE
}

data class PersistenceRetryPolicy(
    val maxAttempts: Int,
    val initialDelayMillis: Long,
    val maxDelayMillis: Long,
    val multiplier: Double = 2.0,
) {
    init {
        require(maxAttempts >= 1)
        require(initialDelayMillis >= 0 && maxDelayMillis >= initialDelayMillis)
        require(multiplier >= 1.0)
    }

    internal fun delayBeforeAttempt(completedAttempts: Int): Long {
        if (completedAttempts <= 0 || initialDelayMillis == 0L) return 0L
        var value = initialDelayMillis.toDouble()
        repeat((completedAttempts - 1).coerceAtLeast(0)) {
            value = (value * multiplier).coerceAtMost(maxDelayMillis.toDouble())
        }
        return value.toLong().coerceAtMost(maxDelayMillis)
    }

    companion object {
        val USER_RETRY = PersistenceRetryPolicy(
            maxAttempts = 1,
            initialDelayMillis = 0,
            maxDelayMillis = 0,
        )
        val BOUNDED_BACKGROUND_RETRY = PersistenceRetryPolicy(
            maxAttempts = 3,
            initialDelayMillis = 100,
            maxDelayMillis = 1_000,
        )
    }
}

/**
 * One durable operation. [write] must itself be idempotent at the repository boundary because the
 * in-memory ledger intentionally does not pretend to survive process death.
 */
class PersistenceOperation(
    val id: PersistenceOperationId,
    val kind: PersistenceOperationKind,
    val retryPolicy: PersistenceRetryPolicy = PersistenceRetryPolicy.USER_RETRY,
    val write: suspend () -> Unit,
)

/**
 * Serializes all player-data writes and makes every terminal state observable. Retrying the same
 * operation object/ID is safe: successful IDs are skipped in memory, and repository writes use
 * stable attempt/result IDs for process-recreation idempotency.
 */
class PersistenceCoordinator(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val classifyFailure: (Throwable) -> PersistenceFailureClassification =
        ::defaultPersistenceFailureClassification,
) {
    private val writeMutex = Mutex()
    private val mutableStatuses =
        MutableStateFlow<Map<PersistenceOperationId, PersistenceOperationStatus>>(emptyMap())
    private val completed = LinkedHashMap<PersistenceOperationId, PersistenceOperationResult>()

    val statuses: StateFlow<Map<PersistenceOperationId, PersistenceOperationStatus>> =
        mutableStatuses.asStateFlow()

    suspend fun execute(operation: PersistenceOperation): PersistenceOperationResult =
        writeMutex.withLock { executeLocked(operation) }

    private suspend fun executeLocked(
        operation: PersistenceOperation,
    ): PersistenceOperationResult {
        completed[operation.id]?.let { committed ->
            return committed.copy(alreadyCommitted = true)
        }

        var attempt = 0
        while (true) {
                attempt += 1
                publish(
                    PersistenceOperationStatus(
                        operationId = operation.id,
                        kind = operation.kind,
                        state = DurableWriteState.SAVING,
                        attempt = attempt,
                        recoverability = PersistenceRecoverability.NOT_APPLICABLE,
                    ),
                )
                try {
                    withContext(dispatcher) { operation.write() }
                    val saved = PersistenceOperationResult(
                        operationId = operation.id,
                        kind = operation.kind,
                        durableState = DurableWriteState.SAVED,
                        recoverability = PersistenceRecoverability.NOT_APPLICABLE,
                        attempts = attempt,
                    )
                    rememberCompleted(saved)
                    publish(saved.asStatus())
                    return saved
                } catch (cancelled: CancellationException) {
                    publish(
                        PersistenceOperationStatus(
                            operationId = operation.id,
                            kind = operation.kind,
                            state = DurableWriteState.FAILED_RETRYABLE,
                            attempt = attempt,
                            recoverability = PersistenceRecoverability.RETRYABLE,
                            failureReason = PersistenceFailureReason.CANCELLED,
                        ),
                    )
                    throw cancelled
                } catch (failure: Exception) {
                    val classification = classifyFailure(failure)
                    val mayRetryAutomatically =
                        classification.recoverability == PersistenceRecoverability.RETRYABLE &&
                            attempt < operation.retryPolicy.maxAttempts
                    if (mayRetryAutomatically) {
                        publish(
                            PersistenceOperationStatus(
                                operationId = operation.id,
                                kind = operation.kind,
                                state = DurableWriteState.FAILED_RETRYABLE,
                                attempt = attempt,
                                recoverability = PersistenceRecoverability.RETRYABLE,
                                failureReason = classification.reason,
                            ),
                        )
                        delay(operation.retryPolicy.delayBeforeAttempt(attempt))
                        continue
                    }

                    val durableState =
                        if (classification.recoverability == PersistenceRecoverability.RETRYABLE) {
                            DurableWriteState.FAILED_RETRYABLE
                        } else {
                            DurableWriteState.FAILED_PERMANENT
                        }
                    val result = PersistenceOperationResult(
                        operationId = operation.id,
                        kind = operation.kind,
                        durableState = durableState,
                        recoverability = classification.recoverability,
                        attempts = attempt,
                        failureReason = classification.reason,
                    )
                    publish(result.asStatus())
                    return result
                }
            }
        error("Unreachable persistence state")
    }

    /** Explicit player Retry uses the same stable operation ID and idempotent repository write. */
    suspend fun retry(operation: PersistenceOperation): PersistenceOperationResult = execute(operation)

    fun status(operationId: PersistenceOperationId): PersistenceOperationStatus? =
        statuses.value[operationId]

    private fun rememberCompleted(result: PersistenceOperationResult) {
        completed[result.operationId] = result
        while (completed.size > MAX_COMPLETED_OPERATION_IDS) {
            completed.remove(completed.keys.first())
        }
    }

    private fun publish(status: PersistenceOperationStatus) {
        val updated = LinkedHashMap(mutableStatuses.value)
        updated.remove(status.operationId)
        updated[status.operationId] = status
        while (updated.size > MAX_VISIBLE_OPERATION_STATES) {
            updated.remove(updated.keys.first())
        }
        mutableStatuses.value = updated
    }

    private fun PersistenceOperationResult.asStatus(): PersistenceOperationStatus =
        PersistenceOperationStatus(
            operationId = operationId,
            kind = kind,
            state = durableState,
            attempt = attempts,
            recoverability = recoverability,
            failureReason = failureReason,
        )

    private companion object {
        const val MAX_COMPLETED_OPERATION_IDS = 200
        const val MAX_VISIBLE_OPERATION_STATES = 100
    }
}

data class PersistenceFailureClassification(
    val recoverability: PersistenceRecoverability,
    val reason: PersistenceFailureReason,
)

fun defaultPersistenceFailureClassification(failure: Throwable): PersistenceFailureClassification =
    when (failure) {
        is IOException -> PersistenceFailureClassification(
            PersistenceRecoverability.RETRYABLE,
            PersistenceFailureReason.IO,
        )
        is SecurityException -> PersistenceFailureClassification(
            PersistenceRecoverability.PERMANENT,
            PersistenceFailureReason.PERMISSION,
        )
        is IllegalArgumentException -> PersistenceFailureClassification(
            PersistenceRecoverability.PERMANENT,
            PersistenceFailureReason.INVALID_DATA,
        )
        else -> PersistenceFailureClassification(
            PersistenceRecoverability.PERMANENT,
            PersistenceFailureReason.UNKNOWN,
        )
    }

/**
 * Operation factories keep stable IDs and retry semantics out of the ViewModel.
 */
class PlayerPersistenceOperations(
    private val repository: PlayerPreferencesRepository,
) {
    fun settings(stableRevision: String, settings: PlayerSettings) = PersistenceOperation(
        id = PersistenceOperationId.forStableKey(PersistenceOperationKind.SETTINGS, stableRevision),
        kind = PersistenceOperationKind.SETTINGS,
        retryPolicy = PersistenceRetryPolicy.BOUNDED_BACKGROUND_RETRY,
        write = { repository.setSettings(settings) },
    )

    fun training(stableRevision: String, state: TrainingState) = PersistenceOperation(
        id = PersistenceOperationId.forStableKey(PersistenceOperationKind.TRAINING, stableRevision),
        kind = PersistenceOperationKind.TRAINING,
        retryPolicy = PersistenceRetryPolicy.BOUNDED_BACKGROUND_RETRY,
        write = { repository.saveTrainingState(state) },
    )

    fun authoredResult(result: ValidatedMissionResult) = PersistenceOperation(
        id = PersistenceOperationId.forStableKey(
            PersistenceOperationKind.AUTHORED_RESULT,
            result.resultId,
        ),
        kind = PersistenceOperationKind.AUTHORED_RESULT,
        write = { repository.commitValidatedMissionResult(result) },
    )

    fun dailyResult(
        localDate: java.time.LocalDate,
        configurationIdentity: String,
        resultId: String,
        score: Int,
    ) = PersistenceOperation(
        id = PersistenceOperationId.forStableKey(PersistenceOperationKind.DAILY_RESULT, resultId),
        kind = PersistenceOperationKind.DAILY_RESULT,
        write = { repository.commitDailyResult(localDate, configurationIdentity, resultId, score) },
    )

    fun practiceResult(result: PracticeResultRecord) = PersistenceOperation(
        id = PersistenceOperationId.forStableKey(
            PersistenceOperationKind.PRACTICE_RESULT,
            result.resultId,
        ),
        kind = PersistenceOperationKind.PRACTICE_RESULT,
        write = { repository.commitPracticeResult(result) },
    )

    fun endlessMilestone(stableAttemptId: String, milestone: EndlessMilestoneRecord) =
        PersistenceOperation(
            id = PersistenceOperationId.forStableKey(
                PersistenceOperationKind.ENDLESS_MILESTONE,
                stableAttemptId,
            ),
            kind = PersistenceOperationKind.ENDLESS_MILESTONE,
            write = { repository.saveEndlessMilestone(milestone) },
        )

    fun endlessPayout(
        stableAttemptId: String,
        contentPackId: String,
        score: Int,
    ) = PersistenceOperation(
        id = PersistenceOperationId.forStableKey(
            PersistenceOperationKind.ENDLESS_PAYOUT,
            stableAttemptId,
        ),
        kind = PersistenceOperationKind.ENDLESS_PAYOUT,
        write = { repository.commitEndlessPayout(contentPackId, score) },
    )

    fun completedReplay(replay: CompletedReplayRecord) = PersistenceOperation(
        id = PersistenceOperationId.forStableKey(
            PersistenceOperationKind.COMPLETED_REPLAY,
            replay.id,
        ),
        kind = PersistenceOperationKind.COMPLETED_REPLAY,
        write = { repository.saveCompletedReplay(replay) },
    )
}
