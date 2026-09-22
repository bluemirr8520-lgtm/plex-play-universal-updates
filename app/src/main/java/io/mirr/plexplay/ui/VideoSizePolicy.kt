package io.mirr.plexplay.ui

/** Manual +/- returns from fill/stretch to a predictable, aspect-preserving size. */
internal fun steppedVideoSize(current: String, direction: Int): String {
    val steps = listOf("small", "fit", "zoom", "zoom_large")
    val index = steps.indexOf(current).takeIf { it >= 0 } ?: 1
    return steps[(index + direction.coerceIn(-1, 1)).coerceIn(0, steps.lastIndex)]
}
