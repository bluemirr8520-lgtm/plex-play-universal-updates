package io.mirr.plexplay.ui

import android.view.View
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI/window regression checks; the fake AndroidView does not test either media decoder. */
@RunWith(AndroidJUnit4::class)
class PlaybackSettingsPanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openingClosingAndReopeningKeepsThePlayerViewAndWindow() {
        val visible = mutableStateOf(false)
        var factories = 0
        var releases = 0
        var detaches = 0
        var playerView: View? = null
        var panelHost: View? = null

        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            factories++
                            View(context).also { view ->
                                playerView = view
                                view.addOnAttachStateChangeListener(
                                    object : View.OnAttachStateChangeListener {
                                        override fun onViewAttachedToWindow(v: View) = Unit
                                        override fun onViewDetachedFromWindow(v: View) { detaches++ }
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { releases++ },
                    )
                    if (visible.value) {
                        PlaybackSettingsPanel(
                            onDismissRequest = { visible.value = false },
                            modifier = Modifier.testTag("panel"),
                            title = {
                                val host = LocalView.current
                                SideEffect { panelHost = host }
                                Text("Settings")
                            },
                            text = { Text("The player stays in this Activity window.") },
                            confirmButton = { Button(onClick = {}) { Text("Close") } },
                        )
                    }
                }
            }
        }

        repeat(2) {
            compose.runOnIdle { visible.value = true }
            compose.onNodeWithTag("panel").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(1, factories)
                assertEquals(0, releases)
                assertEquals(0, detaches)
                assertTrue(playerView!!.isAttachedToWindow)
                assertSame(compose.activity.window.decorView, panelHost!!.rootView)
                assertSame(playerView!!.rootView, panelHost!!.rootView)
            }
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals(1, factories)
            assertEquals(0, releases)
            assertEquals(0, detaches)
        }
    }

    @Test
    fun dpadAndTabStayInsideThePanelAtBothEnds() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = {}, modifier = Modifier.testTag("player")) { Text("Player") }
                    PlaybackSettingsPanel(
                        onDismissRequest = {},
                        title = { Text("Settings") },
                        text = {
                            Column {
                                Button(onClick = {}, modifier = Modifier.testTag("first")) { Text("First") }
                                Button(onClick = {}, modifier = Modifier.testTag("second")) { Text("Second") }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {}, modifier = Modifier.testTag("close")) { Text("Close") }
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag("first").performKeyInput {
            pressKey(Key.DirectionUp)
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("second").assertIsFocused()
        compose.onNodeWithTag("second").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("close").assertIsFocused()
        compose.onNodeWithTag("close").performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag("close").assertIsFocused()
    }

    @Test
    fun blankPanelAndScrimTapsNeverReachPlayer() {
        var playerClicks = 0
        var dismissals = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            View(context).apply { setOnClickListener { playerClicks++ } }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    PlaybackSettingsPanel(
                        onDismissRequest = { dismissals++ },
                        modifier = Modifier.testTag("panel"),
                        title = { Text("Settings") },
                        text = { Text("Options") },
                        confirmButton = { Button(onClick = {}) { Text("Close") } },
                    )
                }
            }
        }

        compose.onNodeWithTag("panel").performTouchInput { click(Offset(width / 2f, 10f)) }
        compose.runOnIdle {
            assertEquals(0, dismissals)
            assertEquals(0, playerClicks)
        }
        compose.onRoot().performTouchInput { click(Offset(2f, 2f)) }
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, playerClicks)
        }
    }

    @Test
    fun mediaKeysAreConsumedAndOnlyAPairedUncancelledEscapeDismisses() {
        var escapedKeyEvents = 0
        var dismissals = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().onKeyEvent { escapedKeyEvents++; false }) {
                    PlaybackSettingsPanel(
                        onDismissRequest = { dismissals++ },
                        title = { Text("Settings") },
                        text = { Text("Options") },
                        confirmButton = {
                            Button(onClick = {}, modifier = Modifier.testTag("close")) { Text("Close") }
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("close").assertIsFocused()
        compose.runOnIdle {
            // The player handled this key's down event when opening the panel.
            compose.activity.dispatchKeyEvent(
                AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_MENU),
            )
            assertEquals(0, dismissals)
            compose.activity.dispatchKeyEvent(
                AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_ESCAPE),
            )
            assertEquals(0, dismissals)
            compose.activity.dispatchKeyEvent(
                AndroidKeyEvent.changeFlags(
                    AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_ESCAPE),
                    AndroidKeyEvent.FLAG_CANCELED,
                ),
            )
            assertEquals(0, dismissals)
            assertEquals(0, escapedKeyEvents)
        }
        compose.onNodeWithTag("close").performKeyInput {
            pressKey(Key.MediaPlay)
            pressKey(Key.MediaPause)
            pressKey(Key.MediaPlayPause)
            pressKey(Key.MediaStop)
            pressKey(Key.Escape)
        }
        compose.runOnIdle {
            assertEquals(0, escapedKeyEvents)
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun longOptionsScrollWhileTheFooterRemainsVisible() {
        compose.setContent {
            MaterialTheme {
                PlaybackSettingsPanel(
                    onDismissRequest = {},
                    title = { Text("Settings") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            repeat(40) { index ->
                                Button(onClick = {}, modifier = Modifier.testTag("option_$index")) {
                                    Text("Option $index")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {}, modifier = Modifier.testTag("close")) { Text("Close") }
                    },
                )
            }
        }

        compose.onNodeWithTag("close").assertIsDisplayed()
        compose.onNodeWithTag("option_39").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("close").assertIsDisplayed()
    }
}
