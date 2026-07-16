package com.stuart.atccontroller.ui

import com.stuart.atccontroller.simulation.Conflict
import com.stuart.atccontroller.simulation.ConflictKind
import com.stuart.atccontroller.simulation.FailureReason
import com.stuart.atccontroller.simulation.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseZeroPresentationTest {
    @Test
    fun conflictPairKeyIsIndependentOfEngineAircraftOrder() {
        val forward = conflict("a1", "a2", "NORTH 201", "CLOUD 314")
        val reverse = conflict("a2", "a1", "CLOUD 314", "NORTH 201")

        assertEquals(forward.canonicalPairKey(), reverse.canonicalPairKey())
    }

    @Test
    fun activeConflictPairSurvivesCountdownAndListReordering() {
        val first = conflict("a1", "a2", "NORTH 201", "CLOUD 314")
        val selected = conflict("b1", "b2", "VECTOR 122", "EMBER 241")
        val updatedSelected = selected.copy(secondsToConflict = 7, isLossOfSeparation = true)

        val retained = retainedConflictIndex(
            previousPairKey = selected.canonicalPairKey(),
            conflicts = listOf(updatedSelected, first.copy(secondsToConflict = 4)),
        )

        assertEquals(0, retained)
        assertEquals(0, retainedConflictIndex("missing", listOf(first)))
    }

    @Test
    fun conflictPresentationUsesCallsignsAndCanonicalOrdering() {
        val mapped = conflictsToUiModels(
            conflicts = listOf(
                engineConflict("z2", "z1", ConflictKind.LOSS_OF_SEPARATION, 0.0),
                engineConflict("a2", "a1", ConflictKind.PREDICTED, 7.2),
            ),
            callsigns = mapOf(
                "a1" to "NORTH 201",
                "a2" to "CLOUD 314",
                "z1" to "VECTOR 122",
                "z2" to "EMBER 241",
            ),
        )

        assertEquals("a1\u0000a2", mapped[0].canonicalPairKey())
        assertEquals("NORTH 201", mapped[0].firstAircraftCallsign)
        assertEquals("CLOUD 314", mapped[0].secondAircraftCallsign)
        assertEquals(8, mapped[0].secondsToConflict)
        assertEquals(true, mapped[1].isLossOfSeparation)
    }

    @Test
    fun headingsAreNormalizedForRadarLabels() {
        assertEquals(0, normalizedHeading(360f))
        assertEquals(359, normalizedHeading(-1f))
        assertEquals(232, normalizedHeading(231.6f))
    }

    @Test
    fun reciprocalRunwayLabelsSwapParallelSides() {
        assertEquals("05L", reciprocalRunwayId("23R"))
        assertEquals("23L", reciprocalRunwayId("05R"))
        assertEquals("27", reciprocalRunwayId("09"))
    }

    @Test
    fun starForecastMovesBackwardWhenPenaltiesReduceTheScore() {
        val thresholds = listOf(200, 300, 400)

        assertEquals(StarForecastUiModel(1, 50), starForecastFor(250, thresholds))
        assertEquals(StarForecastUiModel(0, 20), starForecastFor(180, thresholds))
        assertEquals(StarForecastUiModel(3, null), starForecastFor(450, thresholds))
    }

    @Test
    fun trafficCountdownUsesDeterministicCeilingAndNeverBecomesNegative() {
        assertEquals(6, secondsUntilEntry(spawnAtSeconds = 10.0, elapsedSeconds = 4.1))
        assertEquals(0, secondsUntilEntry(spawnAtSeconds = 10.0, elapsedSeconds = 10.0))
        assertEquals(0, secondsUntilEntry(spawnAtSeconds = 10.0, elapsedSeconds = 11.0))
    }

    @Test
    fun missionClockDistinguishesActiveOverdueAndFailedStates() {
        assertEquals(
            MissionClockState.ACTIVE,
            missionClockState(GameStatus.RUNNING, null, 299.9, 300.0),
        )
        assertEquals(
            MissionClockState.OVERDUE,
            missionClockState(GameStatus.RUNNING, null, 300.0, 300.0),
        )
        assertEquals(
            MissionClockState.FAILED,
            missionClockState(GameStatus.FAILED, FailureReason.TIME_EXPIRED, 300.0, 300.0),
        )
    }

    private fun conflict(
        firstId: String,
        secondId: String,
        firstCallsign: String,
        secondCallsign: String,
    ) = ConflictUiModel(
        firstAircraftId = firstId,
        secondAircraftId = secondId,
        firstAircraftCallsign = firstCallsign,
        secondAircraftCallsign = secondCallsign,
        secondsToConflict = 15,
    )

    private fun engineConflict(
        firstId: String,
        secondId: String,
        kind: ConflictKind,
        seconds: Double,
    ) = Conflict(
        firstAircraftId = firstId,
        secondAircraftId = secondId,
        kind = kind,
        horizontalDistanceNm = 2.5,
        verticalDistanceFeet = 500.0,
        timeToClosestApproachSeconds = seconds,
    )
}
