package com.stuart.atccontroller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RadarResponsiveAccessibilityTest(
    @Suppress("unused") private val viewportName: String,
    private val widthDp: Int,
    private val heightDp: Int,
    private val fontScale: Float,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun criticalGameplayRegionsRemainVisibleAndIndependentlyReachable() {
        val actions = mutableListOf<GameAction>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                AtcControllerTheme {
                    Box(Modifier.size(widthDp.dp, heightDp.dp)) {
                        GameScreen(accessibilityGameState(), actions::add)
                    }
                }
            }
        }

        if (widthDp >= 650 && fontScale < 1.3f) {
            composeRule.onNodeWithText("Approach sector • 23R").assertIsDisplayed()
            composeRule.onNodeWithText("00:24").assertIsDisplayed()
            composeRule.onNodeWithText("120").assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("RWY 23R · 00:24 · 120 pts").assertIsDisplayed()
        }
        composeRule.onNodeWithText("Safe movements").assertIsDisplayed()
        composeRule.onNodeWithText("0/2").assertIsDisplayed()
        composeRule
            .onNodeWithText("Select NORTH 201, then set up its approach.")
            .assertIsDisplayed()

        val radar = composeRule.onNodeWithTag("radar_display").assertIsDisplayed()
        val radarBounds = radar.fetchSemanticsNode().boundsInRoot
        assertTrue("Radar must retain a usable width at $viewportName/$fontScale", radarBounds.width >= 96f)
        assertTrue("Radar must retain a usable height at $viewportName/$fontScale", radarBounds.height >= 96f)
        val renderedRadar = radar.captureToImage()
        assertTrue(renderedRadar.width > 0)
        assertTrue(renderedRadar.height > 0)

        // Selecting an aircraft remains available as a single accessibility action even while
        // guidance, objectives, and the independently scrolling command deck are present.
        val selectAircraft = composeRule
            .onNodeWithContentDescription("Terminal radar", substring = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .single { action -> action.label == "Select NORTH 201" }
        composeRule.runOnIdle {
            assertTrue(selectAircraft.action())
        }
        composeRule.runOnIdle {
            assertTrue(actions.contains(GameAction.SelectAircraft("arrival-1")))
        }

        composeRule.onNodeWithText("SET UP APPROACH")
            .performScrollTo()
            .assertIsDisplayed()
        val headingSlider = composeRule.onNodeWithContentDescription("Assigned heading")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        composeRule.onNodeWithContentDescription("Pause simulation").assertIsDisplayed()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, fontScale={3}")
        fun viewports(): List<Array<Any>> = listOf(
            arrayOf("portrait", 400, 860, 1.0f),
            arrayOf("portrait", 400, 860, 1.3f),
            arrayOf("portrait", 400, 860, 2.0f),
            arrayOf("landscape", 860, 400, 1.0f),
            arrayOf("landscape", 860, 400, 1.3f),
            arrayOf("landscape", 860, 400, 2.0f),
        )
    }
}

class CommandAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directVectorControlsExposeAdjustableValuesAndCommitAccessibleChanges() {
        val actions = mutableListOf<GameAction>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AtcControllerTheme {
                    DirectVectorAssignmentControls(
                        aircraft = accessibilityGameState().selectedAircraft!!,
                        onAction = actions::add,
                    )
                }
            }
        }

        val heading = composeRule.onNodeWithContentDescription("Assigned heading")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "090°",
                ),
            )
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        assertTrue(heading.fetchSemanticsNode().touchBoundsInRoot.height >= 48f)

        heading.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(180f))
        }

        composeRule.onNodeWithContentDescription("Assigned heading")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "180°",
                ),
            )
        composeRule.runOnIdle {
            assertTrue(
                "An accessibility adjustment must issue the same command as touch input",
                actions.contains(GameAction.SetTargetHeading(180)),
            )
        }
    }

    @Test
    fun acceptedAndRejectedReadbacksHaveSpokenPriorityAndStayBesideControls() {
        val accepted = accessibilityGameState().copy(
            commandReadback = CommandReadbackUiModel(
                sequence = 9,
                aircraftId = "arrival-1",
                callsign = "NORTH 201",
                command = "heading 180°",
                status = CommandReadbackStatus.ACCEPTED,
            ),
        )
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 400.dp, height = 860.dp)) {
                    GameScreen(accepted) {}
                }
            }
        }

        composeRule.onNodeWithText("NORTH 201, heading 180° accepted")
            .performScrollTo()
            .assertIsDisplayed()
        val acceptedMessage = "NORTH 201, heading 180° accepted"
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ) and hasAnyDescendant(hasText(acceptedMessage)),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription("Assigned heading")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun rejectedReadbackIsAssertiveAndExplainsWhyTheCommandDidNotApply() {
        composeRule.setContent {
            AtcControllerTheme {
                CommandReadbackCard(
                    CommandReadbackUiModel(
                        sequence = 10,
                        aircraftId = "arrival-1",
                        callsign = "NORTH 201",
                        command = "clear to land runway 23R",
                        status = CommandReadbackStatus.REJECTED,
                        detail = "Aircraft is not established on the approach",
                    ),
                )
            }
        }

        composeRule.onNode(
            hasText(
                "NORTH 201, clear to land runway 23R rejected: " +
                    "Aircraft is not established on the approach",
            ),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive,
            ),
            useUnmergedTree = true,
        ).assertExists()
    }
}

private fun accessibilityGameState(): GameUiState {
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
