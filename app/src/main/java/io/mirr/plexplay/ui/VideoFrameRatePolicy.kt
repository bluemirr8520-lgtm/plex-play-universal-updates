package io.mirr.plexplay.ui

/** Preserve fractional source rates. Zero clears the hint for paused or unknown video. */
internal fun playbackFrameRate(sourceRate: Double?, speed: Float, playing: Boolean): Float {
    if (!playing || sourceRate == null || !sourceRate.isFinite() || sourceRate <= 0.0 ||
        !speed.isFinite() || speed <= 0f
    ) return 0f
    val rate = sourceRate * speed
    return rate.takeIf { it.isFinite() && it > 0.0 && it <= 1_000.0 }?.toFloat() ?: 0f
}
