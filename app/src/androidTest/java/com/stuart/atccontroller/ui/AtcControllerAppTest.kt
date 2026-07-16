package com.stuart.atccontroller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class AtcControllerAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeOpensMissionSelection() {
        val viewModel = TestGameController()
        composeRule.setContent {
            AtcControllerTheme {
                AtcControllerApp(viewModel.uiState, viewModel::onAction)
            }
        }

        composeRule.onNodeWithText("CHOOSE A SHIFT").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Select your shift").assertIsDisplayed()
        composeRule.onNode(hasText("First Contact") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun homeUsesDynamicCareerAndSelectedScenarioValues() {
        val viewModel = TestGameController()
        composeRule.setContent {
            AtcControllerTheme {
                AtcControllerApp(viewModel.uiState, viewModel::onAction)
            }
        }

        composeRule.onNodeWithText("2 of 8 shifts cleared").assertIsDisplayed()
        composeRule.onNodeWithText("5 / 24 ★").assertIsDisplayed()
        composeRule.onNodeWithText("FIRST CONTACT PREVIEW").assertIsDisplayed()
        composeRule.onNodeWithText("240/08").assertIsDisplayed()
    }

    @Test
    fun gameExposesAccessibleAircraftAndPauseControls() {
        val viewModel = TestGameController()
        viewModel.onAction(GameAction.StartSelectedMission)
        viewModel.onAction(GameAction.DismissTutorial)
        composeRule.setContent {
            AtcControllerTheme {
                AtcControllerApp(viewModel.uiState, viewModel::onAction)
            }
        }

        val exsLabel = hasContentDescription("EXS72M, heading", substring = true)
        composeRule.onNode(exsLabel).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals("EXS72M", viewModel.uiState.selectedAircraftId) }
        composeRule.onNode(exsLabel).assertIsSelected()
        composeRule
            .onNode(hasText("DIRECT", substring = true, ignoreCase = true) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause simulation").performClick()
        composeRule.onNodeWithText("SECTOR PAUSED").assertIsDisplayed()
        composeRule.onNodeWithText("RESUME CONTROL").assertIsDisplayed()
    }

    @Test
    fun compactPortraitSettingsKeepEveryControlReachable() {
        val viewModel = TestGameController()
        viewModel.onAction(GameAction.Navigate(AppScreen.SETTINGS))
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    AtcControllerApp(viewModel.uiState, viewModel::onAction)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Music volume").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("RADAR & ACCESSIBILITY").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("LABEL SIZE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("High contrast").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Declutter labels").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause on focus loss").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun conflictBannerCyclesPairsAndSelectsByCallsign() {
        val viewModel = TestGameController()
        viewModel.onAction(GameAction.StartSelectedMission)
        viewModel.onAction(GameAction.DismissTutorial)
        composeRule.setContent {
            AtcControllerTheme {
                AtcControllerApp(viewModel.uiState, viewModel::onAction)
            }
        }

        composeRule.onNodeWithText("PREDICTED CONFLICT").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next conflict pair").performClick()
        composeRule.onNodeWithText("LOSS OF SEPARATION").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Select aircraft NORTH 201").performClick()
        composeRule.runOnIdle { assertEquals("NORTH201", viewModel.uiState.selectedAircraftId) }
    }

    @Test
    fun highContrastThemeProvidesTheCustomCanvasPalette() {
        var standard: AtcPalette? = null
        var highContrast: AtcPalette? = null
        composeRule.setContent {
            AtcControllerTheme(highContrast = false) {
                val palette = MaterialTheme.atcColors
                SideEffect { standard = palette }
            }
            AtcControllerTheme(highContrast = true) {
                val palette = MaterialTheme.atcColors
                SideEffect { highContrast = palette }
            }
        }

        composeRule.runOnIdle {
            assertNotEquals(standard, highContrast)
            assertEquals(Color.Black, highContrast?.night)
            assertNotEquals(standard?.line, highContrast?.line)
        }
    }
}

private class TestGameController {
    var uiState by mutableStateOf(testGameUiState())
        private set

    fun onAction(action: GameAction) {
        uiState = when (action) {
            is GameAction.Navigate -> uiState.copy(screen = action.screen)
            GameAction.StartSelectedMission -> uiState.copy(
                screen = AppScreen.GAME,
                tutorialStep = 0,
            )
            GameAction.DismissTutorial -> uiState.copy(tutorialStep = null)
            is GameAction.SelectAircraft -> uiState.copy(selectedAircraftId = action.id)
            GameAction.TogglePause -> uiState.copy(isPaused = !uiState.isPaused)
            is GameAction.CycleConflict -> uiState.copy(
                activeConflictIndex = Math.floorMod(
                    uiState.activeConflictIndex + action.offset,
                    uiState.conflicts.size.coerceAtLeast(1),
                ),
            )
            else -> uiState
        }
    }
}

private fun testGameUiState() = GameUiState(
    settingsLoaded = true,
    selectedMissionId = "vector_basics",
    career = CareerUiState(
        completedShifts = 2,
        totalShifts = 8,
        earnedStars = 5,
        availableStars = 24,
    ),
    missions = listOf(
        MissionUiModel(
            id = "vector_basics",
            number = 1,
            title = "First Contact",
            subtitle = "Vectors & selection",
            briefing = "Guide an arrival onto the localizer.",
            trafficCount = 1,
            bestStars = 0,
            bestScore = 1_234,
            runwayLabel = "23R",
            wind = "240°/08kt",
            windShort = "240/08",
            visibilityKm = 20,
            durationSeconds = 475,
            objectives = listOf("Complete all movements"),
            previewPositions = listOf(NormalizedPoint(.34f, .38f)),
        ),
    ),
    aircraft = listOf(
        AircraftUiModel(
            id = "EXS72M",
            callsign = "EXS72M",
            type = "B738",
            position = NormalizedPoint(.34f, .38f),
            headingDegrees = 132f,
            altitudeFeet = 4_500,
            targetAltitudeFeet = 3_000,
            speedKnots = 210,
            targetSpeedKnots = 190,
            phase = FlightPhase.ARRIVAL,
            clearance = "DESCEND",
            fuelPercent = 62,
            route = listOf(NormalizedPoint(.45f, .46f)),
        ),
        AircraftUiModel(
            id = "CLOUD314",
            callsign = "CLOUD 314",
            type = "A320",
            position = NormalizedPoint(.54f, .48f),
            headingDegrees = 210f,
            altitudeFeet = 5_000,
            targetAltitudeFeet = 4_000,
            speedKnots = 220,
            targetSpeedKnots = 200,
            phase = FlightPhase.ARRIVAL,
            clearance = "RADAR CONTACT",
            fuelPercent = 70,
            conflictLevel = ConflictLevel.PREDICTED,
        ),
        AircraftUiModel(
            id = "NORTH201",
            callsign = "NORTH 201",
            type = "E145",
            position = NormalizedPoint(.62f, .42f),
            headingDegrees = 180f,
            altitudeFeet = 5_000,
            targetAltitudeFeet = 5_000,
            speedKnots = 210,
            targetSpeedKnots = 210,
            phase = FlightPhase.ARRIVAL,
            clearance = "RADAR CONTACT",
            fuelPercent = 74,
            conflictLevel = ConflictLevel.LOSS,
        ),
    ),
    runway = RunwayUiModel(id = "23R", label = "23R", wind = "240° / 08 kt"),
    runways = listOf(RunwayUiModel(id = "23R", label = "23R", wind = "240° / 08 kt")),
    conflicts = listOf(
        ConflictUiModel(
            firstAircraftId = "EXS72M",
            secondAircraftId = "CLOUD314",
            firstAircraftCallsign = "EXS72M",
            secondAircraftCallsign = "CLOUD 314",
            secondsToConflict = 18,
        ),
        ConflictUiModel(
            firstAircraftId = "CLOUD314",
            secondAircraftId = "NORTH201",
            firstAircraftCallsign = "CLOUD 314",
            secondAircraftCallsign = "NORTH 201",
            secondsToConflict = 0,
            isLossOfSeparation = true,
        ),
    ),
    fixes = listOf(
        FixUiModel("MIRSI", NormalizedPoint(.10f, .18f)),
        FixUiModel("I-23R", NormalizedPoint(.73f, .73f), FixKind.APPROACH),
    ),
)
