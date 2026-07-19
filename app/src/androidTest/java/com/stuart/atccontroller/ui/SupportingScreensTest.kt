package com.stuart.atccontroller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SupportingScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactHomeMakesOnboardingAndPrimaryPathsDiscoverable() {
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    HomeScreen(shellTestState(), onAction = {})
                }
            }
        }

        composeRule.onNodeWithText("CHOOSE A SHIFT").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("CONTINUE").assertIsDisplayed()
        composeRule.onNodeWithText("New to air traffic control?").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("OPEN CONTROLLER GUIDE").assertHasClickAction()
    }

    @Test
    fun firstMissionOffersGuidanceBeforeUnguidedStart() {
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 360.dp, height = 800.dp)) {
                    MissionSelectScreen(shellTestState(), onAction = {})
                }
            }
        }

        composeRule.onNodeWithText("PLAY GUIDED LESSON").performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("START WITHOUT GUIDANCE").assertHasClickAction()
    }

    @Test
    fun compactMissionSelectionUsesOneScrollContainerAndReachesTheLastMission() {
        val actions = mutableListOf<GameAction>()
        val template = shellTestState().missions.single()
        val missions = (1..12).map { number ->
            template.copy(
                id = "mission_$number",
                number = number,
                title = "Mission $number",
                trainingAvailable = false,
            )
        }
        val state = shellTestState().copy(
            selectedMissionId = missions.first().id,
            missions = missions,
        )

        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    MissionSelectScreen(state, onAction = actions::add)
                }
            }
        }

        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(1)
        composeRule.onNodeWithText("Mission 12")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf(GameAction.SelectMission("mission_12")), actions)
    }

    @Test
    fun customShiftExposesAllTrainingAssistsAndNamedCycleControls() {
        composeRule.setContent {
            AtcControllerTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    CustomShiftScreen(CustomShiftUiModel(), onAction = {})
                }
            }
        }

        composeRule.onNodeWithContentDescription("Next Sector pack option")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Route snapping"))
        composeRule.onNodeWithContentDescription("Route snapping")
            .assertIsDisplayed()
    }
}

private fun shellTestState() = GameUiState(
    settingsLoaded = true,
    selectedMissionId = "first_contact",
    missions = listOf(
        MissionUiModel(
            id = "first_contact",
            number = 1,
            title = "First Contact",
            subtitle = "Selection & approach",
            briefing = "Select each arrival, prepare its approach and clear it to land.",
            trafficCount = 3,
            campaignName = "Manchester",
            airportName = "EGCC",
            runwayLabel = "23R",
            wind = "240°/08 kt",
            windShort = "240/08",
            visibilityKm = 20,
            durationSeconds = 480,
            objectives = listOf("Land all three arrivals"),
            trainingAvailable = true,
        ),
    ),
)
