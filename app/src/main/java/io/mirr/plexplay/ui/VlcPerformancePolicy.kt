package io.mirr.plexplay.ui

internal enum class VlcOptimizationMode(
    val storage: String,
    val label: String,
    val description: String,
) {
    AUTO("auto", "자동 최적화 · 권장", "4K·고프레임 영상은 네트워크 버퍼를 더 확보합니다."),
    STABILITY("stability", "재생 안정성 우선", "더 긴 버퍼를 사용하고 늦은 프레임을 정리합니다."),
    BALANCED("balanced", "균형", "시작 대기와 재생 안정성의 균형을 맞춥니다."),
    PERFORMANCE("performance", "빠른 시작", "짧은 버퍼를 사용합니다. 4K 끊김에는 자동 모드를 권장합니다."),
    ;

    companion object {
        fun fromStorage(value: String?): VlcOptimizationMode =
            entries.firstOrNull { it.storage == value } ?: AUTO
    }
}

internal data class VlcBufferProfile(val networkMs: Int, val fileMs: Int)

/** Includes letterboxed/portrait UHD, without classifying ordinary portrait HD as 4K. */
internal fun isVlcUhdVideo(width: Int?, height: Int?): Boolean =
    maxOf(width ?: 0, height ?: 0) >= 3_000

internal fun vlcBufferProfile(
    mode: VlcOptimizationMode,
    width: Int?,
    height: Int?,
    frameRate: Double?,
    lowMemoryDevice: Boolean,
): VlcBufferProfile {
    val demanding = isVlcUhdVideo(width, height) ||
        (frameRate != null && frameRate.isFinite() && frameRate >= 50.0)
    val requestedNetworkMs = when (mode) {
        VlcOptimizationMode.AUTO -> if (demanding) 6_000 else 3_000
        VlcOptimizationMode.STABILITY -> if (demanding) 8_000 else 3_000
        VlcOptimizationMode.BALANCED -> if (demanding) 3_000 else 1_500
        VlcOptimizationMode.PERFORMANCE -> if (demanding) 1_500 else 800
    }
    // This limits buffered time, not an exact byte budget. Bitrate is server dependent.
    val networkMs = if (lowMemoryDevice) requestedNetworkMs.coerceAtMost(3_000) else requestedNetworkMs
    val fileMs = if (mode == VlcOptimizationMode.STABILITY) 1_500 else 1_000
    return VlcBufferProfile(networkMs, fileMs)
}
