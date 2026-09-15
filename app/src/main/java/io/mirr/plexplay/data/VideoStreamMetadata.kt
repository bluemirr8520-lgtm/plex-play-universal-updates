package io.mirr.plexplay.data

/** Measured stream properties only; a display label such as 4K or 24p is not a measurement. */
internal data class VideoStreamMetadata(
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val bitDepth: Int? = null,
) {
    fun forPlaybackOutput(originalVideo: Boolean): VideoStreamMetadata =
        if (originalVideo) this else VideoStreamMetadata()
}

internal fun parsePositiveVideoInt(value: String?, missingValue: Int? = null): Int? =
    if (value == null) missingValue?.takeIf { it > 0 } else value.trim().toIntOrNull()?.takeIf { it > 0 }

internal fun parseVideoFrameRate(value: String?, missingValue: Double? = null): Double? {
    if (value == null) return missingValue?.takeIf { it.isFinite() && it > 0 }
    val pieces = value.trim().split('/')
    val rate = when (pieces.size) {
        1 -> pieces[0].toDoubleOrNull()
        2 -> {
            val numerator = pieces[0].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
            val denominator = pieces[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
            numerator / denominator
        }
        else -> null
    }
    return rate?.takeIf { it.isFinite() && it > 0 }
}

internal fun PlexItem.videoStreamMetadata(): VideoStreamMetadata = VideoStreamMetadata(
    width = videoWidth?.takeIf { it > 0 },
    height = videoHeight?.takeIf { it > 0 },
    frameRate = videoFrameRate?.takeIf { it.isFinite() && it > 0 },
    bitDepth = videoBitDepth?.takeIf { it > 0 },
)
