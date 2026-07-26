package com.stuart.atccontroller.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceCoordinatorTest {
    @Test
    fun writesAreSerializedInSubmissionOrder() = runBlocking {
        val coordinator = PersistenceCoordinator(Dispatchers.Default)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val order = mutableListOf<String>()

        fun operation(name: String) = PersistenceOperation(
            id = PersistenceOperationId(name),
            kind = PersistenceOperationKind.OTHER,
            write = {
                val concurrent = active.incrementAndGet()
                peak.updateAndGet { current -> maxOf(current, concurrent) }
                order += "$name-start"
                delay(20)
                order += "$name-end"
                active.decrementAndGet()
            },
        )

        val first = async { coordinator.execute(operation("first")) }
        delay(5)
        val second = async { coordinator.execute(operation("second")) }

        assertTrue(first.await().isSaved)
        assertTrue(second.await().isSaved)
        assertEquals(1, peak.get())
        assertEquals(
            listOf("first-start", "first-end", "second-start", "second-end"),
            order,
        )
    }

    @Test
    fun retryableIoUsesBoundedBackoffAndEventuallyCommits() = runBlocking {
        val coordinator = PersistenceCoordinator(Dispatchers.Unconfined)
        var writes = 0
        val operation = PersistenceOperation(
            id = PersistenceOperationId("retrying"),
            kind = PersistenceOperationKind.SETTINGS,
            retryPolicy = PersistenceRetryPolicy(
                maxAttempts = 3,
                initialDelayMillis = 0,
                maxDelayMillis = 0,
            ),
            write = {
                writes += 1
                if (writes < 3) throw IOException("injected")
            },
        )

        val result = coordinator.execute(operation)

        assertTrue(result.isSaved)
        assertEquals(3, result.attempts)
        assertEquals(3, writes)
        assertEquals(DurableWriteState.SAVED, coordinator.status(operation.id)?.state)
    }

    @Test
    fun exhaustedRetryReturnsTypedFailureAndExplicitRetryIsIdempotent() = runBlocking {
        val coordinator = PersistenceCoordinator(Dispatchers.Unconfined)
        var shouldFail = true
        var writes = 0
        val operation = PersistenceOperation(
            id = PersistenceOperationId("terminal-result"),
            kind = PersistenceOperationKind.DAILY_RESULT,
            write = {
                writes += 1
                if (shouldFail) throw IOException("injected")
            },
        )

        val failed = coordinator.execute(operation)
        assertEquals(DurableWriteState.FAILED_RETRYABLE, failed.durableState)
        assertEquals(PersistenceRecoverability.RETRYABLE, failed.recoverability)
        assertTrue(failed.canRetry)
        assertFalse(failed.isSaved)

        shouldFail = false
        val saved = coordinator.retry(operation)
        val duplicate = coordinator.retry(operation)

        assertTrue(saved.isSaved)
        assertTrue(duplicate.isSaved)
        assertTrue(duplicate.alreadyCommitted)
        assertEquals(2, writes)
    }

    @Test
    fun invalidDataIsPermanentAndIsNotAutomaticallyRetried() = runBlocking {
        val coordinator = PersistenceCoordinator(Dispatchers.Unconfined)
        var writes = 0
        val operation = PersistenceOperation(
            id = PersistenceOperationId("invalid"),
            kind = PersistenceOperationKind.COMPLETED_REPLAY,
            retryPolicy = PersistenceRetryPolicy(
                maxAttempts = 5,
                initialDelayMillis = 0,
                maxDelayMillis = 0,
            ),
            write = {
                writes += 1
                throw IllegalArgumentException("injected")
            },
        )

        val result = coordinator.execute(operation)

        assertEquals(DurableWriteState.FAILED_PERMANENT, result.durableState)
        assertEquals(PersistenceRecoverability.PERMANENT, result.recoverability)
        assertEquals(PersistenceFailureReason.INVALID_DATA, result.failureReason)
        assertEquals(1, result.attempts)
        assertEquals(1, writes)
    }

    @Test
    fun stableIdsDoNotExposeRawAttemptIdentifiers() {
        val raw = "player@example.test/session/attempt-123"

        val first = PersistenceOperationId.forStableKey(
            PersistenceOperationKind.AUTHORED_RESULT,
            raw,
        )
        val second = PersistenceOperationId.forStableKey(
            PersistenceOperationKind.AUTHORED_RESULT,
            raw,
        )

        assertEquals(first, second)
        assertFalse(first.value.contains(raw))
        assertTrue(first.value.startsWith("authored_result:"))
    }
}
