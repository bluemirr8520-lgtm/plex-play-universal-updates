package io.mirr.plexplay.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic navigation only; does not load Plex or modify watched history. */
@RunWith(AndroidJUnit4::class)
class AppBackHandlerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun rootBackAsksBeforeExitingAndCancelKeepsActivityOpen() {
        var exits = 0
        compose.setContent { MaterialTheme { AppBackHandler({ false }, { exits++ }) } }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("앱을 종료할까요?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, exits); assertFalse(compose.activity.isFinishing) }
        compose.onNodeWithText("계속 사용").performClick()
        compose.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, exits); assertFalse(compose.activity.isFinishing) }
    }

    @Test fun onlyTheExplicitExitButtonInvokesExit() {
        var exits = 0
        compose.setContent { MaterialTheme { AppBackHandler({ false }, { exits++ }) } }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("종료").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun playerAndSettingsBackHandlersTakePriorityOverRoot() {
        val page = mutableStateOf(2)
        var exits = 0
        compose.setContent {
            MaterialTheme {
                AppBackHandler(onBack = { if (page.value > 0) { page.value--; true } else false },
                    onExit = { exits++ })
                BackHandler(enabled = page.value == 2) { page.value = 1 }
            }
        }
        repeat(2) {
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals(0, page.value); assertEquals(0, exits) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("앱을 종료할까요?").assertIsDisplayed()
    }
}
