package com.stuart.atccontroller.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationTest {
    @Test
    fun homeDoesNotInterceptSystemBack() {
        assertNull(appBackAction(GameUiState(screen = AppScreen.HOME)))
    }

    @Test
    fun customShiftReturnsToMissionSelection() {
        assertEquals(
            GameAction.Navigate(AppScreen.MISSIONS),
            appBackAction(GameUiState(screen = AppScreen.CUSTOM_SHIFT)),
        )
    }

    @Test
    fun activeGameBackUsesSafeAbandonFlow() {
        assertEquals(
            GameAction.RequestAbandonment,
            appBackAction(GameUiState(screen = AppScreen.GAME)),
        )
        assertEquals(
            GameAction.CancelAbandonment,
            appBackAction(
                GameUiState(
                    screen = AppScreen.GAME,
                    abandonConfirmationVisible = true,
                ),
            ),
        )
    }

    @Test
    fun replayBackReturnsHomeWithoutRequestingAbandonment() {
        assertEquals(
            GameAction.Navigate(AppScreen.HOME),
            appBackAction(
                GameUiState(
                    screen = AppScreen.GAME,
                    replay = ReplayUiModel(),
                ),
            ),
        )
    }
}
