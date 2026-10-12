package io.mirr.plexplay.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteTimeBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tenSecondStepIsIndependentOfMovieLength() {
        compose.runOnUiThread {
            for (duration in listOf(600_000L, 7_200_000L, 18_000_000L)) {
                var target = 0L
                val bar = DefaultTimeBar(compose.activity).apply {
                    setDuration(duration)
                    setPosition(100_000L)
                    setKeyTimeIncrement(RemoteSeekStepMs)
                    addListener(object : TimeBar.OnScrubListener {
                        override fun onScrubStart(timeBar: TimeBar, position: Long) { target = position }
                        override fun onScrubMove(timeBar: TimeBar, position: Long) { target = position }
                        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) = Unit
                    })
                }
                bar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                assertEquals(110_000L, target)
                bar.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                assertEquals(100_000L, target)
                bar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            }
        }
    }

    @Test fun timeBarRepeatFilteringKeepsTheCurrentPressWhenScrubbingStarts() {
        compose.runOnUiThread {
            val input = RemoteSeekKeyInput()
            val pending = RemoteSeekState()
            var target = 0L
            val bar = DefaultTimeBar(compose.activity).apply {
                setDuration(7_200_000L)
                setPosition(100_000L)
                setKeyTimeIncrement(RemoteSeekStepMs)
                addListener(object : TimeBar.OnScrubListener {
                    override fun onScrubStart(timeBar: TimeBar, position: Long) {
                        pending.cancel() // Mirrors the native scrub callback; do not reset input.
                        target = position
                    }
                    override fun onScrubMove(timeBar: TimeBar, position: Long) { target = position }
                    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) = Unit
                })
            }
            (0..100).forEach { repeat ->
                val event = KeyEvent(100, 100 + repeat * 20L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat)
                if (!input.consumeFastRepeat(event, pending, nowMs = event.eventTime)) {
                    bar.onKeyDown(event.keyCode, event)
                }
            }
            // One initial 10s step, then four repeats in 2 seconds (not 101 steps).
            assertEquals(150_000L, target)
            bar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        }
    }
}
