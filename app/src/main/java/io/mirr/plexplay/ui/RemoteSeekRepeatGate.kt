package io.mirr.plexplay.ui

internal const val RemoteSeekStepMs = 10_000L
internal const val RemoteSeekRepeatMs = 500L

/** Rate limit repeats, not short taps. Delayed batches never cause catch-up jumps. */
internal class RemoteSeekRepeatGate {
    private data class Press(val key: Int, val downTime: Long)
    private data class Event(val press: Press, val time: Long, val repeat: Int)
    private var press: Press? = null
    private var lastAcceptedTime = 0L
    private var lastEvent: Event? = null
    private var lastResult = false

    fun acceptDown(
        key: Int, downTime: Long, eventTime: Long, repeatCount: Int, nowMs: Long = eventTime,
    ): Boolean {
        val incoming = Press(key, downTime)
        val event = Event(incoming, eventTime, repeatCount)
        // Compose preview and the native view can observe the same event.
        if (event == lastEvent) return lastResult
        val accept = when {
            repeatCount == 0 -> { press = incoming; true }
            press != incoming -> false
            nowMs - lastAcceptedTime >= RemoteSeekRepeatMs -> true
            else -> false
        }
        if (accept) lastAcceptedTime = nowMs
        lastEvent = event
        lastResult = accept
        return accept
    }

    fun release(key: Int, downTime: Long) {
        if (press == Press(key, downTime)) reset()
    }

    fun reset() {
        press = null
        lastEvent = null
        lastResult = false
    }
}
