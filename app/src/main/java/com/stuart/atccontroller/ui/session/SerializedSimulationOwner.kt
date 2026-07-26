package com.stuart.atccontroller.ui.session

import com.stuart.atccontroller.simulation.AtcSimulationEngine
import com.stuart.atccontroller.simulation.GameSnapshot
import com.stuart.atccontroller.simulation.GameStatus
import com.stuart.atccontroller.simulation.PlayerCommand
import com.stuart.atccontroller.simulation.SimulationFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * FIFO actor that is the production owner of the mutable simulation engine.
 *
 * Callers may enqueue work from any thread. Engine creation, snapshots, commands, and fixed-step
 * advancement are all executed by one background coroutine in channel order. A session token
 * prevents a late result from an abandoned/replaced game from mutating the new session.
 */
internal class SerializedSimulationOwner(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val requests = Channel<OwnerRequest>(Channel.UNLIMITED)
    private var activeEngine: AtcSimulationEngine? = null
    private var activeFrame: SimulationFrame? = null
    private var activeSessionId = 0L
    private var operationSequence = 0L

    init {
        scope.launch(dispatcher + CoroutineName("atc-simulation-owner")) {
            for (request in requests) request.execute()
        }
    }

    suspend fun create(
        initialCommands: List<PlayerCommand> = emptyList(),
        factory: () -> AtcSimulationEngine,
    ): OwnedSimulation = request {
        val created = factory()
        activeSessionId += 1L
        activeEngine = created
        var frame = created.presentationFrame
        initialCommands.forEach { command ->
            frame = created.submitForPresentation(command)
        }
        activeFrame = frame
        OwnedSimulation(
            sessionId = activeSessionId,
            operationSequence = nextOperationSequence(),
            engine = created,
            frame = frame,
        )
    }

    suspend fun createInitialized(
        factory: () -> AtcSimulationEngine,
        initialize: (AtcSimulationEngine) -> SimulationFrame,
    ): OwnedSimulation = request {
        val created = factory()
        activeSessionId += 1L
        activeEngine = created
        val frame = initialize(created)
        activeFrame = frame
        OwnedSimulation(
            sessionId = activeSessionId,
            operationSequence = nextOperationSequence(),
            engine = created,
            frame = frame,
        )
    }

    suspend fun adopt(
        engine: AtcSimulationEngine,
        snapshot: GameSnapshot,
    ): OwnedSimulation = request {
        activeSessionId += 1L
        activeEngine = engine
        val frame = SimulationFrame(
            snapshot = snapshot,
            eventSequenceStart = snapshot.eventHistoryStartSequence,
        )
        activeFrame = frame
        OwnedSimulation(
            sessionId = activeSessionId,
            operationSequence = nextOperationSequence(),
            engine = engine,
            frame = frame,
        )
    }

    suspend fun clear(): Long = request {
        activeSessionId += 1L
        activeEngine = null
        activeFrame = null
        nextOperationSequence()
        activeSessionId
    }

    suspend fun submit(
        sessionId: Long,
        command: PlayerCommand,
    ): SimulationTransition? = request {
        val engine = activeEngine
        val before = activeFrame
        if (sessionId != activeSessionId || engine == null || before == null) {
            null
        } else {
            val after = engine.submitForPresentation(command)
            activeFrame = after
            SimulationTransition(
                sessionId = sessionId,
                operationSequence = nextOperationSequence(),
                before = before.snapshot,
                frame = after,
            )
        }
    }

    /** Enqueues a controller-authored command group without allowing a clock tick between items. */
    suspend fun submitAll(
        sessionId: Long,
        commands: List<PlayerCommand>,
    ): List<SimulationTransition> = request {
        val engine = activeEngine
        var before = activeFrame
        if (sessionId != activeSessionId || engine == null || before == null) {
            emptyList()
        } else {
            buildList(commands.size) {
                commands.forEach { command ->
                    val after = engine.submitForPresentation(command)
                    add(
                        SimulationTransition(
                            sessionId = sessionId,
                            operationSequence = nextOperationSequence(),
                            before = before!!.snapshot,
                            frame = after,
                        ),
                    )
                    before = after
                    activeFrame = after
                }
            }
        }
    }

    suspend fun advance(
        sessionId: Long,
        realDeltaSeconds: Double,
    ): SimulationTransition? = request {
        val engine = activeEngine
        val before = activeFrame
        if (sessionId != activeSessionId || engine == null || before == null) {
            null
        } else {
            val after = engine.advanceForPresentation(realDeltaSeconds)
            activeFrame = after
            SimulationTransition(
                sessionId = sessionId,
                operationSequence = nextOperationSequence(),
                before = before.snapshot,
                frame = after,
            )
        }
    }

    suspend fun advanceFixedSteps(
        sessionId: Long,
        count: Int = 1,
    ): SimulationTransition? = request {
        val engine = activeEngine
        val before = activeFrame
        if (sessionId != activeSessionId || engine == null || before == null) {
            null
        } else {
            val after = engine.advanceFixedStepsForPresentation(count)
            activeFrame = after
            SimulationTransition(
                sessionId = sessionId,
                operationSequence = nextOperationSequence(),
                before = before.snapshot,
                frame = after,
            )
        }
    }

    /**
     * Runs a multi-step mutation as one actor operation. This is used for replay batches so live
     * ticks cannot interleave with their scheduled commands.
     */
    suspend fun <T> mutate(
        sessionId: Long,
        block: (AtcSimulationEngine, SimulationFrame) -> EngineMutation<T>,
    ): SimulationMutationTransition<T>? = request {
        val engine = activeEngine
        val before = activeFrame
        if (sessionId != activeSessionId || engine == null || before == null) {
            null
        } else {
            val mutation = block(engine, before)
            activeFrame = mutation.frame
            SimulationMutationTransition(
                sessionId = sessionId,
                operationSequence = nextOperationSequence(),
                before = before.snapshot,
                frame = mutation.frame,
                value = mutation.value,
            )
        }
    }

    /** Test/diagnostic hook; the block still executes inside the serialized owner. */
    suspend fun <T> inspect(
        sessionId: Long,
        block: (AtcSimulationEngine, GameSnapshot) -> T,
    ): T? = request {
        val engine = activeEngine
        val frame = activeFrame
        if (sessionId != activeSessionId || engine == null || frame == null) {
            null
        } else {
            block(engine, frame.snapshot)
        }
    }

    private fun nextOperationSequence(): Long {
        operationSequence += 1L
        return operationSequence
    }

    private suspend fun <T> request(block: () -> T): T {
        val result = CompletableDeferred<T>()
        requests.send(
            OwnerRequest {
                runCatching(block)
                    .onSuccess(result::complete)
                    .onFailure(result::completeExceptionally)
            },
        )
        return result.await()
    }

    private fun interface OwnerRequest {
        fun execute()
    }
}

internal data class OwnedSimulation(
    val sessionId: Long,
    val operationSequence: Long,
    /**
     * Read-only compatibility handle for diagnostics and existing instrumentation. Production
     * mutations must go through [SerializedSimulationOwner].
     */
    val engine: AtcSimulationEngine,
    val frame: SimulationFrame,
)

internal data class SimulationTransition(
    val sessionId: Long,
    val operationSequence: Long,
    val before: GameSnapshot,
    val frame: SimulationFrame,
)

internal data class EngineMutation<T>(
    val frame: SimulationFrame,
    val value: T,
)

internal data class SimulationMutationTransition<T>(
    val sessionId: Long,
    val operationSequence: Long,
    val before: GameSnapshot,
    val frame: SimulationFrame,
    val value: T,
)

internal object SimulationClockPolicy {
    fun shouldRun(
        hasOwnedSession: Boolean,
        isGameScreenVisible: Boolean,
        liveStatus: GameStatus?,
        hasReplay: Boolean,
        replayIsPlaying: Boolean,
    ): Boolean {
        if (!hasOwnedSession || !isGameScreenVisible) return false
        return if (hasReplay) {
            replayIsPlaying && liveStatus?.isTerminal() != true
        } else {
            liveStatus == GameStatus.RUNNING
        }
    }

    private fun GameStatus.isTerminal(): Boolean =
        this == GameStatus.COMPLETED || this == GameStatus.FAILED
}
