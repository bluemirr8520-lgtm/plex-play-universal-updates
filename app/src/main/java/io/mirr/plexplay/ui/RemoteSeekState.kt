package io.mirr.plexplay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val RemoteSeekIdleMs = 300L

/** A transient target only: repeats must not seek the decoder or persist watch progress. */
internal class RemoteSeekState {
    var previewMs: Long? by mutableStateOf(null)
        private set
    var revision: Int by mutableIntStateOf(0)
        private set

    fun step(deltaMs: Long, positionMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0L) {
            cancel()
            return null
        }
        val base = (previewMs ?: positionMs).coerceIn(0L, durationMs)
        // Clamp before adding so even bad metadata or a huge delta cannot overflow.
        val target = if (deltaMs >= 0L) {
            base + minOf(deltaMs, durationMs - base)
        } else {
            if (deltaMs <= -base) 0L else base + deltaMs
        }
        previewMs = target
        // Repeated input at a boundary must still reset the idle timer.
        revision++
        return target
    }

    fun finish(durationMs: Long): Long? {
        val target = previewMs?.takeIf { durationMs > 0L }?.coerceIn(0L, durationMs)
        cancel()
        return target
    }

    fun cancel() { previewMs = null }
}
