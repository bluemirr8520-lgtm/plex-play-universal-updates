package io.mirr.plexplay.ui

/** Saved preference; fallback never changes the user's preferred mode. */
internal enum class VlcDecoderMode(val storage: String, val label: String) {
    AUTO("auto", "자동 · 하드웨어 우선"),
    SOFTWARE("software", "소프트웨어 · 호환 재생");

    companion object {
        fun fromStorage(value: String?): VlcDecoderMode =
            entries.firstOrNull { it.storage == value } ?: AUTO
    }
}

internal data class VlcDecoderConfiguration(
    val useHardware: Boolean,
    val mediaOptions: List<String>,
)

internal fun vlcDecoderConfiguration(
    mode: VlcDecoderMode,
    softwareFallbackUsed: Boolean,
): VlcDecoderConfiguration {
    val software = mode == VlcDecoderMode.SOFTWARE || softwareFallbackUsed
    // setHWDecoderEnabled(false, false) marks the codec choice as explicit and
    // uses VLC's normal module list, not forced MediaCodec. Do not restrict
    // :codec to avcodec/dav1d: that can exclude audio and subtitle modules.
    return VlcDecoderConfiguration(
        useHardware = !software,
        mediaOptions = if (software) listOf(":avcodec-hw=none") else emptyList(),
    )
}

/** Generic VLC errors cannot establish a hardware failure. Recover the connection first. */
internal fun shouldRetryVlcWithSoftware(
    mode: VlcDecoderMode,
    softwareFallbackUsed: Boolean,
    connectionRecoveryExhausted: Boolean,
    playbackHasProgressed: Boolean,
    videoWidth: Int?,
    videoHeight: Int?,
): Boolean = mode == VlcDecoderMode.AUTO && !softwareFallbackUsed &&
    connectionRecoveryExhausted && !playbackHasProgressed &&
    // Unknown dimensions may also be UHD. Software remains a manual option for any file.
    (videoWidth ?: 0) > 0 && (videoHeight ?: 0) > 0 &&
    !isVlcUhdVideo(videoWidth, videoHeight)
