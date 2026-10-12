package io.mirr.plexplay.ui

import android.os.SystemClock
import android.view.KeyEvent

private val seekKeys = setOf(
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN,
)

internal class RemoteSeekKeyInput {
    private val repeats = RemoteSeekRepeatGate()

    fun consumeFastRepeat(
        event: KeyEvent, seek: RemoteSeekState, nowMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        if (event.keyCode !in seekKeys) return false
        if (event.action == KeyEvent.ACTION_UP) {
            repeats.release(event.keyCode, event.downTime)
        } else if (event.action == KeyEvent.ACTION_DOWN && !repeats.acceptDown(
                event.keyCode, event.downTime, event.eventTime, event.repeatCount, nowMs,
            )
        ) {
            seek.keepAlive()
            return true
        }
        return false
    }

    fun reset() = repeats.reset()
}
