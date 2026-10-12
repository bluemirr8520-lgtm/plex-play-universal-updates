package io.mirr.plexplay

/** A held TV Back key must navigate once, on its matching release. */
internal class RemoteBackPress {
    private var downTime: Long? = null

    fun down(time: Long, repeatCount: Int) {
        if (repeatCount == 0) downTime = time
    }

    fun up(time: Long, canceled: Boolean): Boolean {
        val matched = downTime == time
        reset()
        return matched && !canceled
    }

    fun reset() { downTime = null }
}
