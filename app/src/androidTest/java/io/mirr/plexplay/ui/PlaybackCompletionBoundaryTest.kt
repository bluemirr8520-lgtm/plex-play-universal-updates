package io.mirr.plexplay.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic player lifetime checks, without connecting to Plex or changing watched history. */
@RunWith(AndroidJUnit4::class)
class PlaybackCompletionBoundaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun endedPlayerIsDisposedBeforeTheLibraryCallbackAndOnlyOnce() {
        val events = mutableListOf<String>()
        lateinit var end: (Long) -> Unit
        compose.setContent {
            PlaybackCompletionBoundary(onCompleted = { events += "library:$it" }, onClose = {}) { complete ->
                end = complete
                DisposableEffect(Unit) { onDispose { events += "released" } }
            }
        }
        compose.runOnIdle { end(100); end(200) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("released", "library:100"), events)
            assertFalse(compose.activity.isFinishing)
        }
    }

    @Test fun oldSessionCannotCompleteTheNextVideo() {
        val session = mutableStateOf("first")
        val ends = mutableMapOf<String, (Long) -> Unit>()
        val completed = mutableListOf<String>()
        compose.setContent {
            val id = session.value
            key(id) {
                PlaybackCompletionBoundary(onCompleted = { completed += id }, onClose = {}) { end -> ends[id] = end }
            }
        }
        compose.runOnIdle { session.value = "next" }
        compose.waitForIdle()
        compose.runOnIdle { ends.getValue("first")(100) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(completed.isEmpty()); ends.getValue("next")(200) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("next"), completed) }
    }

    @Test fun failedHandoffReturnsToListWithoutFinishingActivity() {
        lateinit var end: (Long) -> Unit
        var closes = 0
        compose.setContent {
            PlaybackCompletionBoundary(onCompleted = { throw IllegalStateException("Synthetic test") },
                onClose = { closes++ }) { end = it }
        }
        compose.runOnIdle { end(100) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, closes); assertFalse(compose.activity.isFinishing) }
    }
}
