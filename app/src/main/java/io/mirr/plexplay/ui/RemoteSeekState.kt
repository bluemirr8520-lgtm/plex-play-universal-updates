package io.mirr.plexplay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val RemoteSeekIdleMs = 600L
internal const val RemoteSeekFrameMs = 50L
internal const val RemoteSeekSpeed = 20L

/** A transient target only: repeats must not seek the decoder or persist watch progress. */
internal class RemoteSeekState {
    var previewMs: Long? by mutableStateOf(null)
        private set
    var revision: Int by mutableIntStateOf(0)
        private set
    var isHolding: Boolean by mutableStateOf(false)
        private set
    private var holdDirection = 0
    private var lastTickMs = 0L
    private var lastSignalMs = 0L

    fun step(deltaMs: Long, positionMs: Long, durationMs: Long): Long? {
        isHolding = false
        val target = moveTarget(deltaMs, positionMs, durationMs) ?: return null
        revision++
        return target
    }

    private fun moveTarget(deltaMs: Long, positionMs: Long, durationMs: Long): Long? {
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
        return target
    }

    fun hold(deltaMs: Long, positionMs: Long, durationMs: Long, nowMs: Long): Long? {
        val direction = if (deltaMs >= 0) 1 else -1
        lastSignalMs = nowMs
        if (!isHolding || holdDirection != direction) {
            val target = step(direction * RemoteSeekStepMs, positionMs, durationMs) ?: return null
            holdDirection = direction
            lastTickMs = nowMs
            isHolding = true
            return target
        }
        return previewMs
    }

    fun holdExpired(nowMs: Long): Boolean = isHolding && nowMs - lastSignalMs >= RemoteSeekIdleMs

    fun advanceHold(nowMs: Long, durationMs: Long): Long? {
        if (!isHolding || holdExpired(nowMs)) return previewMs
        // No catch-up leap after a delayed frame, regardless of the keyboard's repeat rate.
        val elapsed = (nowMs - lastTickMs).coerceIn(0L, 100L)
        lastTickMs = maxOf(lastTickMs, nowMs)
        return moveTarget(holdDirection * elapsed * RemoteSeekSpeed, previewMs ?: 0L, durationMs)
    }

    fun finish(durationMs: Long): Long? {
        val target = previewMs?.takeIf { durationMs > 0L }?.coerceIn(0L, durationMs)
        cancel()
        return target
    }

    fun keepAlive(nowMs: Long? = null) {
        if (isHolding) {
            if (nowMs != null) lastSignalMs = nowMs
        } else if (previewMs != null) revision++
    }

    fun cancel() {
        previewMs = null
        isHolding = false
        holdDirection = 0
    }
}
