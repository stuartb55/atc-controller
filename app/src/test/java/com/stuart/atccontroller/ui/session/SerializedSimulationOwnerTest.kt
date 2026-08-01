package com.stuart.atccontroller.ui.session

import com.stuart.atccontroller.simulation.GameEvent
import com.stuart.atccontroller.simulation.AtcSimulationEngine
import com.stuart.atccontroller.simulation.GameStatus
import com.stuart.atccontroller.simulation.PlayerCommand
import com.stuart.atccontroller.simulation.Route
import com.stuart.atccontroller.simulation.Vec2
import com.stuart.atccontroller.simulation.arrival
import com.stuart.atccontroller.simulation.engineWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializedSimulationOwnerTest {
    @Test
    fun `clock policy is dormant without active work`() {
        assertFalse(
            SimulationClockPolicy.shouldRun(
                hasOwnedSession = false,
                isGameScreenVisible = true,
                liveStatus = GameStatus.RUNNING,
                hasReplay = false,
                replayIsPlaying = false,
            ),
        )
        assertFalse(
            SimulationClockPolicy.shouldRun(
                hasOwnedSession = true,
                isGameScreenVisible = false,
                liveStatus = GameStatus.RUNNING,
                hasReplay = false,
                replayIsPlaying = false,
            ),
        )
        assertFalse(
            SimulationClockPolicy.shouldRun(
                hasOwnedSession = true,
                isGameScreenVisible = true,
                liveStatus = GameStatus.PAUSED,
                hasReplay = false,
                replayIsPlaying = false,
            ),
        )
        assertFalse(
            SimulationClockPolicy.shouldRun(
                hasOwnedSession = true,
                isGameScreenVisible = true,
                liveStatus = GameStatus.RUNNING,
                hasReplay = true,
                replayIsPlaying = false,
            ),
        )
        assertTrue(
            SimulationClockPolicy.shouldRun(
                hasOwnedSession = true,
                isGameScreenVisible = true,
                liveStatus = GameStatus.RUNNING,
                hasReplay = true,
                replayIsPlaying = true,
            ),
        )
    }

    @Test
    fun `commands and ticks execute in stable fifo order off the caller thread`() = runBlocking {
        repeat(10) {
            val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val owner = SerializedSimulationOwner(ownerScope)
                val owned = owner.create(initialCommands = listOf(PlayerCommand.Start)) {
                    engineWith(arrival("A"))
                }
                val callerThread = Thread.currentThread().name
                val ownerThread = owner.inspect(owned.sessionId) { _, _ ->
                    Thread.currentThread().name
                }
                assertNotEquals(callerThread, ownerThread)

                // UNDISPATCHED starts each producer through its channel send before the next one,
                // while all three remain concurrently suspended awaiting the actor result.
                val firstTick = async(start = CoroutineStart.UNDISPATCHED) {
                    owner.advanceFixedSteps(owned.sessionId)
                }
                val commands = async(start = CoroutineStart.UNDISPATCHED) {
                    owner.submitAll(
                        owned.sessionId,
                        listOf(
                            PlayerCommand.SetTargetHeading("A", 90.0),
                            PlayerCommand.SetTargetHeading("A", 180.0),
                            PlayerCommand.SetTargetHeading("A", 270.0),
                        ),
                    )
                }
                val secondTick = async(start = CoroutineStart.UNDISPATCHED) {
                    owner.advanceFixedSteps(owned.sessionId)
                }

                val firstTickResult = checkNotNull(firstTick.await())
                val commandResults = commands.await()
                val secondTickResult = checkNotNull(secondTick.await())
                assertEquals(
                    listOf(2L, 3L, 4L, 5L, 6L),
                    listOf(firstTickResult.operationSequence) +
                        commandResults.map(SimulationTransition::operationSequence) +
                        secondTickResult.operationSequence,
                )
                val finalHeading = owner.inspect(owned.sessionId) { _, snapshot ->
                    snapshot.aircraft.single().assignedHeadingDegrees
                }
                assertEquals(270.0, checkNotNull(finalHeading), 0.0)
                assertEquals(2L, secondTickResult.frame.snapshot.tick)
            } finally {
                ownerScope.cancel()
            }
        }
    }

    @Test
    fun `presentation frames deliver deltas without copying retained history`() {
        val engine = engineWith(arrival("A"))
        val start = engine.submitForPresentation(PlayerCommand.Start)
        assertTrue(start.snapshot.eventHistory.isEmpty())
        assertTrue(start.snapshot.events.any { it is GameEvent.AircraftSpawned })
        assertEquals(0L, start.eventSequenceStart)

        var latest = start
        repeat(AtcSimulationEngine.EVENT_HISTORY_CAPACITY + 25) { index ->
            latest = engine.submitForPresentation(
                PlayerCommand.SetRoute(
                    aircraftId = "A",
                    route = Route(listOf(Vec2(0.1 + (index % 5) * 0.01, 0.2))),
                ),
            )
            assertTrue(latest.snapshot.eventHistory.isEmpty())
        }

        val retained = engine.snapshot
        assertEquals(AtcSimulationEngine.EVENT_HISTORY_CAPACITY, retained.eventHistory.size)
        assertEquals(
            retained.eventHistoryStartSequence + retained.eventHistory.size - 1L,
            latest.eventSequenceStart,
        )
    }

    @Test
    fun `earlier presentation aircraft remain immutable after later steps`() {
        val engine = engineWith(arrival("A"))
        val first = engine.submitForPresentation(PlayerCommand.Start)
        val originalAircraft = first.snapshot.aircraft.single()

        engine.advanceFixedStepsForPresentation(5)

        assertEquals(originalAircraft, first.snapshot.aircraft.single())
        assertNotEquals(originalAircraft.position, engine.presentationFrame.snapshot.aircraft.single().position)
    }
}
