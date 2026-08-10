package io.mirr.plexplay.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import io.mirr.plexplay.data.PlexItem
import io.mirr.plexplay.data.PlaybackSource
import io.mirr.plexplay.data.PlaybackQuality

internal data class DevicePlaybackCompatibility(
    val directPlaybackSupported: Boolean,
    val label: String,
)

internal fun shouldPreferUniversalCodec(
    playbackQuality: PlaybackQuality,
    directPlaybackSupported: Boolean,
): Boolean =
    playbackQuality == PlaybackQuality.ORIGINAL && !directPlaybackSupported

private enum class VideoDynamicRange(
    val label: String,
) {
    DOLBY_VISION("Dolby Vision"),
    HDR10_PLUS("HDR10+"),
    HDR10("HDR10"),
    HLG("HLG"),
    HDR("HDR"),
    SDR("SDR"),
}

internal fun mediaFeatureLabels(item: PlexItem): List<String> = buildList {
    detectDynamicRange(
        dynamicRange = item.videoDynamicRange,
        videoProfile = item.videoProfile,
        colorPrimaries = item.videoColorPrimaries,
        colorTransfer = item.videoColorTransfer,
        dolbyVisionProfile = item.dolbyVisionProfile,
    ).takeUnless { it == VideoDynamicRange.SDR }?.let { add(it.label) }

    when (item.videoCodec.normalizedCodec()) {
        "av1", "av01" -> add("AV1")
        "hevc", "h265", "hev1", "hvc1" -> add("HEVC")
    }

    val audioSignal = listOfNotNull(
        item.audioCodec,
        item.audioProfile,
        item.audioDisplayTitle,
    ).joinToString(" ").lowercase()
    when {
        "atmos" in audioSignal || "joc" in audioSignal -> add("Dolby Atmos")
        "truehd" in audioSignal || "true-hd" in audioSignal -> add("Dolby TrueHD")
        "eac3" in audioSignal || "e-ac-3" in audioSignal -> add("Dolby Digital+")
        Regex("(^|[^e])ac3").containsMatchIn(audioSignal) ||
            "ac-3" in audioSignal -> add("Dolby Digital")
        "dts:x" in audioSignal || "dtsx" in audioSignal -> add("DTS:X")
        "dtshd" in audioSignal || "dts-hd" in audioSignal -> add("DTS-HD")
    }
}.distinct()

internal fun detectDevicePlaybackCompatibility(
    context: Context,
    item: PlexItem,
): DevicePlaybackCompatibility = detectDevicePlaybackCompatibility(
    context = context,
    videoCodec = item.videoCodec,
    videoDynamicRange = item.videoDynamicRange,
    videoProfile = item.videoProfile,
    videoColorPrimaries = item.videoColorPrimaries,
    videoColorTransfer = item.videoColorTransfer,
    dolbyVisionProfile = item.dolbyVisionProfile,
    audioCodec = item.audioCodec,
)

internal fun detectDevicePlaybackCompatibility(
    context: Context,
    source: PlaybackSource,
): DevicePlaybackCompatibility = detectDevicePlaybackCompatibility(
    context = context,
    videoCodec = source.videoCodec,
    videoDynamicRange = source.videoDynamicRange,
    videoProfile = source.videoProfile,
    videoColorPrimaries = source.videoColorPrimaries,
    videoColorTransfer = source.videoColorTransfer,
    dolbyVisionProfile = source.dolbyVisionProfile,
    audioCodec = source.audioCodec,
)

private fun detectDevicePlaybackCompatibility(
    context: Context,
    videoCodec: String?,
    videoDynamicRange: String?,
    videoProfile: String?,
    videoColorPrimaries: String?,
    videoColorTransfer: String?,
    dolbyVisionProfile: Int?,
    audioCodec: String?,
): DevicePlaybackCompatibility {
    val dynamicRange = detectDynamicRange(
        dynamicRange = videoDynamicRange,
        videoProfile = videoProfile,
        colorPrimaries = videoColorPrimaries,
        colorTransfer = videoColorTransfer,
        dolbyVisionProfile = dolbyVisionProfile,
    )
    val videoMimeType = when {
        dynamicRange == VideoDynamicRange.DOLBY_VISION -> "video/dolby-vision"
        else -> videoCodec.toVideoMimeType()
    }
    val audioMimeType = audioCodec.toAudioMimeType()
    val missingVideoDecoder = videoMimeType != null && !hasDecoder(videoMimeType)
    val missingAudioDecoder =
        audioMimeType != null &&
            !hasDecoder(audioMimeType) &&
            !supportsDirectAudioPlayback(audioCodec)
    val unsupportedDynamicRange = !displaySupports(context, dynamicRange)
    val directPlaybackSupported =
        !missingVideoDecoder && !missingAudioDecoder && !unsupportedDynamicRange
    return DevicePlaybackCompatibility(
        directPlaybackSupported = directPlaybackSupported,
        label = if (directPlaybackSupported) {
            "이 기기에서 원본 재생 지원"
        } else {
            "이 기기에서 원본 재생 지원 여부 확인 필요"
        },
    )
}

private fun detectDynamicRange(
    dynamicRange: String?,
    videoProfile: String?,
    colorPrimaries: String?,
    colorTransfer: String?,
    dolbyVisionProfile: Int?,
): VideoDynamicRange {
    val signal = listOfNotNull(
        dynamicRange,
        videoProfile,
        colorPrimaries,
        colorTransfer,
    ).joinToString(" ").lowercase()
    return when {
        dolbyVisionProfile != null ||
            "dovi" in signal ||
            "dolby vision" in signal -> VideoDynamicRange.DOLBY_VISION
        "hdr10+" in signal ||
            "hdr10plus" in signal ||
            "st 2094" in signal ||
            "smpte2094" in signal -> VideoDynamicRange.HDR10_PLUS
        "hdr10" in signal ||
            "smpte st 2084" in signal ||
            "smpte2084" in signal ||
            "smpte2084-10" in signal ||
            Regex("(^|[^a-z])pq([^a-z]|$)").containsMatchIn(signal) ->
            VideoDynamicRange.HDR10
        "hlg" in signal || "arib-std-b67" in signal -> VideoDynamicRange.HLG
        "hdr" in signal || "bt2020" in signal -> VideoDynamicRange.HDR
        else -> VideoDynamicRange.SDR
    }
}

private fun displaySupports(
    context: Context,
    dynamicRange: VideoDynamicRange,
): Boolean {
    if (dynamicRange == VideoDynamicRange.SDR) return true
    val supportedTypes = runCatching {
        val displayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.displays
            .flatMap { display -> display.hdrCapabilities.supportedHdrTypes.asIterable() }
            .toSet()
    }.getOrDefault(emptySet())
    if (supportedTypes.isEmpty()) return false
    return when (dynamicRange) {
        VideoDynamicRange.DOLBY_VISION ->
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in supportedTypes
        VideoDynamicRange.HDR10_PLUS ->
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in supportedTypes
        VideoDynamicRange.HDR10 ->
            Display.HdrCapabilities.HDR_TYPE_HDR10 in supportedTypes
        VideoDynamicRange.HLG ->
            Display.HdrCapabilities.HDR_TYPE_HLG in supportedTypes
        VideoDynamicRange.HDR -> supportedTypes.isNotEmpty()
        VideoDynamicRange.SDR -> true
    }
}

private fun hasDecoder(mimeType: String): Boolean = runCatching {
    MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codecInfo ->
        !codecInfo.isEncoder && codecInfo.supportedTypes.any {
            it.equals(mimeType, ignoreCase = true)
        }
    }
}.getOrDefault(true)

private fun supportsDirectAudioPlayback(codec: String?): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val encoding = when (codec.normalizedCodec()) {
        "ac3" -> AudioFormat.ENCODING_AC3
        "eac3", "e-ac-3" -> AudioFormat.ENCODING_E_AC3
        "truehd", "true-hd" -> AudioFormat.ENCODING_DOLBY_TRUEHD
        "dca", "dts" -> AudioFormat.ENCODING_DTS
        "dtshd", "dts-hd", "dts_hd" -> AudioFormat.ENCODING_DTS_HD
        else -> return false
    }
    return runCatching {
        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(48_000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        AudioTrack.isDirectPlaybackSupported(format, attributes)
    }.getOrDefault(false)
}

private fun String?.normalizedCodec(): String = this?.trim()?.lowercase().orEmpty()

private fun String?.toVideoMimeType(): String? = when (normalizedCodec()) {
    "h264", "avc", "avc1" -> "video/avc"
    "hevc", "h265", "hev1", "hvc1" -> "video/hevc"
    "av1", "av01" -> "video/av01"
    "vp9", "vp09" -> "video/x-vnd.on2.vp9"
    "vp8", "vp08" -> "video/x-vnd.on2.vp8"
    "mpeg2", "mpeg2video" -> "video/mpeg2"
    "mpeg4", "mp4v" -> "video/mp4v-es"
    "vc1", "vc-1", "wmv3" -> "video/wvc1"
    else -> null
}

private fun String?.toAudioMimeType(): String? = when (normalizedCodec()) {
    "aac", "aac_latm" -> "audio/mp4a-latm"
    "ac3" -> "audio/ac3"
    "eac3", "e-ac-3" -> "audio/eac3"
    "truehd", "true-hd" -> "audio/true-hd"
    "dca", "dts" -> "audio/vnd.dts"
    "dtshd", "dts-hd", "dts_hd" -> "audio/vnd.dts.hd"
    "flac" -> "audio/flac"
    "opus" -> "audio/opus"
    "vorbis" -> "audio/vorbis"
    else -> null
}
