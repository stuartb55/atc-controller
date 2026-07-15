package com.stuart.atccontroller.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stuart.atccontroller.MainActivity
import com.stuart.atccontroller.data.PlayerPreferencesRepository
import com.stuart.atccontroller.data.PlayerSettings
import com.stuart.atccontroller.data.atcControllerDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository by lazy { PlayerPreferencesRepository(context.atcControllerDataStore) }

    @Before
    fun seedPersistedSettings() = runBlocking {
        repository.setSettings(
            PlayerSettings(
                musicVolume = 0f,
                effectsVolume = 0f,
                hapticsEnabled = false,
                reducedMotion = true,
                highContrast = true,
            ),
        )
    }

    @After
    fun restoreDefaults() = runBlocking {
        repository.setSettings(PlayerSettings())
        repository.clearActiveSession()
    }

    @Test
    fun persistedSettingsAreAppliedByMainActivityAndSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("SETTINGS").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("SETTINGS").performClick()

            composeRule.onNodeWithText("Controller settings").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Music volume").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Unmute Music").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("High contrast").assertIsOn()

            scenario.recreate()

            composeRule.onNodeWithText("Controller settings").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Music volume").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Unmute Music").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("High contrast").assertIsOn()
        }
    }
}
