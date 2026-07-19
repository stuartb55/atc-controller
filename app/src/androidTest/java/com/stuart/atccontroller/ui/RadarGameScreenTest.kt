package com.stuart.atccontroller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RadarGameScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun portraitGuidanceDoesNotHidePrimaryArrivalCommands() {
        val actions = mutableListOf<GameAction>()
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 400.dp, height = 860.dp)) {
                    GameScreen(firstMissionState(), actions::add)
                }
            }
        }

        composeRule.onNodeWithText("SET UP APPROACH").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("CLEAR TO LAND").assertIsDisplayed()
        composeRule.onNodeWithText("Select NORTH 201, then set up its approach.").assertIsDisplayed()

        assertTrue(actions.contains(GameAction.PrepareApproach))
    }

    @Test
    fun vectorAssignmentsUseDiscoverableTouchControls() {
        val actions = mutableListOf<GameAction>()
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 400.dp, height = 860.dp)) {
                    GameScreen(firstMissionState(), actions::add)
                }
            }
        }

        composeRule.onNodeWithText("090°").assertIsDisplayed()
        composeRule.onNodeWithText("FL30").assertIsDisplayed()
        composeRule.onNodeWithText("160 kt").assertIsDisplayed()
    }

    @Test
    fun portraitGuidanceHasDedicatedSpaceInsteadOfCoveringRadar() {
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 400.dp, height = 860.dp)) {
                    GameScreen(firstMissionState()) {}
                }
            }
        }

        val radarBounds = composeRule.onNodeWithTag("radar_display")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val guidanceBounds = composeRule.onNodeWithTag("command_guidance")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue(guidanceBounds.bottom <= radarBounds.top)
    }

    @Test
    fun compactHudKeepsTimeScoreAndSpeedAccessible() {
        val actions = mutableListOf<GameAction>()
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 400.dp, height = 860.dp)) {
                    GameScreen(firstMissionState(), actions::add)
                }
            }
        }

        composeRule.onNodeWithText("RWY 23R · 00:24 · 120 pts").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Simulation speed 1×. Tap to change to 2×")
            .assertIsDisplayed()
            .performClick()

        assertTrue(actions.contains(GameAction.SetTimeScale(2)))
    }

    @Test
    fun portraitReplayControlsStayCompactAndBackReturnsHome() {
        val actions = mutableListOf<GameAction>()
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 400.dp, height = 860.dp)) {
                    GameScreen(
                        firstMissionState().copy(
                            training = null,
                            replay = ReplayUiModel(
                                tick = 2_717,
                                terminalTick = 4_750,
                            ),
                        ),
                        actions::add,
                    )
                }
            }
        }

        val progressBounds = composeRule.onNodeWithText("Replay 2717 / 4750")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val replayBounds = composeRule.onNodeWithTag("replay_controls")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val radarBounds = composeRule.onNodeWithTag("radar_display")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue(progressBounds.width > progressBounds.height)
        assertTrue(replayBounds.height < replayBounds.width)
        assertTrue(radarBounds.height > 0f)

        composeRule.onNodeWithText("Play replay").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(actions.contains(GameAction.ReplayTogglePlay))
        assertTrue(actions.contains(GameAction.Navigate(AppScreen.HOME)))
    }

    private fun firstMissionState(): GameUiState {
        val aircraft = AircraftUiModel(
            id = "arrival-1",
            callsign = "NORTH 201",
            type = "A320",
            position = NormalizedPoint(.28f, .25f),
            headingDegrees = 90f,
            targetHeadingDegrees = 90,
            altitudeFeet = 3_000,
            targetAltitudeFeet = 3_000,
            speedKnots = 160,
            targetSpeedKnots = 160,
            phase = FlightPhase.ARRIVAL,
            clearance = "Inbound",
            assignedRunway = "23R",
            fuelPercent = 72,
        )
        val runway = RunwayUiModel(
            id = "23R",
            label = "Manchester 23R",
            center = NormalizedPoint(.55f, .58f),
            headingDegrees = 230f,
            wind = "240/08",
        )
        return GameUiState(
            screen = AppScreen.GAME,
            aircraft = listOf(aircraft),
            selectedAircraftId = aircraft.id,
            runway = runway,
            runways = listOf(runway),
            score = 120,
            elapsedSeconds = 24,
            missionTimeRemainingSeconds = 276,
            movementsRemaining = 2,
            objectiveProgress = listOf(
                ObjectiveProgressUiModel(
                    id = "safe",
                    kind = ObjectiveProgressKind.SAFE_MOVEMENTS,
                    current = 0,
                    target = 2,
                    passed = false,
                ),
            ),
            starForecast = StarForecastUiModel(securedStars = 0, pointsToNextStar = 80),
            approachSetupAssistEnabled = true,
            training = TrainingUiModel(
                lessonId = "first-contact",
                title = "First contact",
                stepIndex = 0,
                stepCount = 3,
                prompt = "Select NORTH 201, then set up its approach.",
                actionGate = "Complete this action in the command deck.",
                canAdvance = false,
            ),
        )
    }
}
