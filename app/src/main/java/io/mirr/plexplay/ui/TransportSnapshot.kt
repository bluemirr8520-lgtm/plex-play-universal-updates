package io.mirr.plexplay.ui

internal data class TransportSnapshot(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

internal fun transportSeekPosition(positionMs: Long, durationMs: Long): Long =
    positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))

internal fun shouldAutoHideTransport(visible: Boolean, settings: Boolean, playing: Boolean, scrubbing: Boolean): Boolean =
    visible && !settings && playing && !scrubbing
