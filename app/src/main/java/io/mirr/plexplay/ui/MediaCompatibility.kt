package io.mirr.plexplay.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import io.mirr.plexplay.data.MediaAudioCodec
import io.mirr.plexplay.data.MediaVideoCodec
import io.mirr.plexplay.data.PlexItem
import io.mirr.plexplay.data.PlaybackSource
import io.mirr.plexplay.data.PlaybackQuality
import io.mirr.plexplay.data.mediaAudioCodec
import io.mirr.plexplay.data.mediaVideoCodec
import io.mirr.plexplay.data.normalizeMediaCodec
import io.mirr.plexplay.data.requiresLocalAudioDecoding
import java.util.Locale

internal data class DevicePlaybackCompatibility(
    val directPlaybackSupported: Boolean,
    val label: String,
)

internal fun shouldPreferUniversalCodec(
    playbackQuality: PlaybackQuality,
    directPlaybackSupported: Boolean,
    audioCodec: String? = null,
): Boolean =
    playbackQuality == PlaybackQuality.ORIGINAL &&
        (!directPlaybackSupported || requiresLocalAudioDecoding(audioCodec))

private enum class VideoDynamicRange(val label: String) {
    DOLBY_VISION("Dolby Vision"),
    HDR10_PLUS("HDR10+"),
    HDR10("HDR10"),
    HLG("HLG"),
    HDR("HDR"),
    SDR("SDR"),
}

/** These are source metadata badges, not a promise of HDR or object-audio output. */
internal fun mediaFeatureLabels(item: PlexItem): List<String> = buildList {
    detectDynamicRange(
        videoCodec = item.videoCodec,
        dynamicRange = item.videoDynamicRange,
        videoProfile = item.videoProfile,
        colorPrimaries = item.videoColorPrimaries,
        colorTransfer = item.videoColorTransfer,
        dolbyVisionProfile = item.dolbyVisionProfile,
    ).takeUnless { it == VideoDynamicRange.SDR }?.let { add(it.label) }
    mediaVideoCodec(item.videoCodec)?.featureLabel?.let { add(it) }

    val audio = mediaAudioCodec(item.audioCodec)
    val signal = listOfNotNull(item.audioProfile, item.audioDisplayTitle)
        .joinToString(" ").lowercase(Locale.ROOT)
    val audioLabel = when {
        audio == MediaAudioCodec.EAC3_JOC ||
            Regex("(^|[^a-z0-9])(atmos|joc)([^a-z0-9]|$)").containsMatchIn(signal) -> "Dolby Atmos"
        Regex("(^|[^a-z0-9])dts[ :_-]?x([^a-z0-9]|$)").containsMatchIn(signal) -> "DTS:X"
        audio == MediaAudioCodec.DTS && (
            normalizeMediaCodec(item.audioProfile) in setOf("ma", "hra", "dtshdma", "dtshdhra") ||
                Regex("(^|[^a-z0-9])dts[ _-]?hd([^a-z0-9]|$)").containsMatchIn(signal)
            ) -> "DTS-HD"
        else -> audio?.featureLabel
    }
    audioLabel?.let { add(it) }
}.distinct()

internal fun detectDevicePlaybackCompatibility(
    context: Context,
    item: PlexItem,
): DevicePlaybackCompatibility = detectDevicePlaybackCompatibility(
    context = context,
    videoCodec = item.videoCodec,
    videoDynamicRange = item.videoDynamicRange,
    videoProfile = item.videoProfile,
    videoBitDepth = item.videoBitDepth,
    videoWidth = item.videoWidth,
    videoHeight = item.videoHeight,
    videoFrameRate = item.videoFrameRate,
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
    videoBitDepth = source.videoBitDepth,
    videoWidth = source.videoWidth,
    videoHeight = source.videoHeight,
    videoFrameRate = source.videoFrameRate,
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
    videoBitDepth: Int?,
    videoWidth: Int?,
    videoHeight: Int?,
    videoFrameRate: Double?,
    videoColorPrimaries: String?,
    videoColorTransfer: String?,
    dolbyVisionProfile: Int?,
    audioCodec: String?,
): DevicePlaybackCompatibility {
    val dynamicRange = detectDynamicRange(
        videoCodec, videoDynamicRange, videoProfile,
        videoColorPrimaries, videoColorTransfer, dolbyVisionProfile,
    )
    val sourceVideo = mediaVideoCodec(videoCodec)
    val decoderVideo = if (dynamicRange == VideoDynamicRange.DOLBY_VISION) {
        MediaVideoCodec.DOLBY_VISION
    } else {
        sourceVideo
    }
    val profiles = decoderVideo?.let {
        requiredVideoDecoderProfiles(
            it, videoProfile, videoBitDepth, dolbyVisionProfile,
            hdr10 = dynamicRange == VideoDynamicRange.HDR10,
            hdr10Plus = dynamicRange == VideoDynamicRange.HDR10_PLUS,
        )
    }
    return evaluateDevicePlaybackCompatibility(
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        videoDecoderSupported = decoderVideo != null &&
            hasSupportedVideoDecoder(decoderVideo, profiles, videoWidth, videoHeight, videoFrameRate),
        audioDecoderSupported = hasSupportedAudioDecoder(mediaAudioCodec(audioCodec)),
        dynamicRangeSupported = displaySupports(context, dynamicRange),
    )
}

/** Keep unknown metadata and failed Android capability queries distinguishable from support. */
internal fun evaluateDevicePlaybackCompatibility(
    videoCodec: String?,
    audioCodec: String?,
    videoDecoderSupported: Boolean,
    audioDecoderSupported: Boolean,
    dynamicRangeSupported: Boolean,
): DevicePlaybackCompatibility {
    val supported = mediaVideoCodec(videoCodec) != null && mediaAudioCodec(audioCodec) != null &&
        videoDecoderSupported && audioDecoderSupported && dynamicRangeSupported
    return DevicePlaybackCompatibility(
        supported,
        if (supported) {
            "기기 디코더 지원 확인 · 실제 재생은 파일과 출력 장치에 따라 달라집니다"
        } else {
            "이 기기에서 원본 재생 지원 여부 확인 필요"
        },
    )
}

private fun detectDynamicRange(
    videoCodec: String?,
    dynamicRange: String?,
    videoProfile: String?,
    colorPrimaries: String?,
    colorTransfer: String?,
    dolbyVisionProfile: Int?,
): VideoDynamicRange {
    val signal = listOfNotNull(dynamicRange, videoProfile, colorPrimaries, colorTransfer)
        .joinToString(" ").lowercase(Locale.ROOT)
    return when {
        mediaVideoCodec(videoCodec) == MediaVideoCodec.DOLBY_VISION ||
            dolbyVisionProfile != null || "dovi" in signal || "dolby vision" in signal ->
            VideoDynamicRange.DOLBY_VISION
        "hdr10+" in signal || "hdr10plus" in signal || "st 2094" in signal || "smpte2094" in signal ->
            VideoDynamicRange.HDR10_PLUS
        "hdr10" in signal || "smpte st 2084" in signal || "smpte2084" in signal ||
            Regex("(^|[^a-z])pq([^a-z]|$)").containsMatchIn(signal) -> VideoDynamicRange.HDR10
        "hlg" in signal || "arib-std-b67" in signal -> VideoDynamicRange.HLG
        "hdr" in signal -> VideoDynamicRange.HDR
        else -> VideoDynamicRange.SDR
    }
}

private fun displaySupports(context: Context, dynamicRange: VideoDynamicRange): Boolean {
    if (dynamicRange == VideoDynamicRange.SDR) return true
    val supportedTypes = runCatching {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        // An HDR-capable secondary display must not certify playback on an SDR phone screen.
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display }.getOrNull()
        } else {
            null
        }
        val display = current ?: manager.getDisplay(Display.DEFAULT_DISPLAY)
        display?.hdrCapabilities?.supportedHdrTypes?.toSet().orEmpty()
    }.getOrDefault(emptySet())
    return when (dynamicRange) {
        VideoDynamicRange.DOLBY_VISION -> Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in supportedTypes
        VideoDynamicRange.HDR10_PLUS -> Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in supportedTypes
        VideoDynamicRange.HDR10 -> Display.HdrCapabilities.HDR_TYPE_HDR10 in supportedTypes
        VideoDynamicRange.HLG -> Display.HdrCapabilities.HDR_TYPE_HLG in supportedTypes
        VideoDynamicRange.HDR -> supportedTypes.isNotEmpty()
        VideoDynamicRange.SDR -> true
    }
}

internal fun requiresHardwareVideoDecoder(width: Int?, height: Int?): Boolean =
    (width ?: 0) >= 3840 || (height ?: 0) >= 2160

internal fun isHardwareVideoDecoder(name: String, hardwareAccelerated: Boolean?): Boolean {
    val normalized = name.lowercase(Locale.ROOT)
    val knownSoftware = normalized.startsWith("omx.google.") ||
        normalized.startsWith("c2.android.") || normalized.startsWith("c2.google.") ||
        "ffmpeg" in normalized || ".sw." in normalized || normalized.endsWith(".sw")
    if (knownSoftware) return false
    return hardwareAccelerated ?: listOf(
        "omx.qcom.", "omx.exynos.", "omx.sec.", "omx.mtk.", "omx.amlogic.",
        "omx.nvidia.", "omx.hisi.", "omx.rk.", "omx.rockchip.", "omx.realtek.",
        "omx.brcm.", "omx.intel.",
    ).any { normalized.startsWith(it) }
}

internal fun safelyCheckMediaDecoder(query: () -> Boolean): Boolean =
    runCatching(query).getOrDefault(false)

private fun hasSupportedVideoDecoder(
    codec: MediaVideoCodec,
    profiles: Set<Int>?,
    width: Int?,
    height: Int?,
    frameRate: Double?,
): Boolean {
    if (profiles?.isEmpty() == true ||
        (width != null && width <= 0) || (height != null && height <= 0) ||
        (frameRate != null && (!frameRate.isFinite() || frameRate <= 0))
    ) return false
    return safelyCheckMediaDecoder {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            runCatching {
                if (info.isEncoder) return@runCatching false
                val mime = info.supportedTypes.firstOrNull { it.equals(codec.mimeType, ignoreCase = true) }
                    ?: return@runCatching false
                if (requiresHardwareVideoDecoder(width, height) &&
                    !isHardwareVideoDecoder(
                        info.name,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated else null,
                    )
                ) return@runCatching false
                val caps = info.getCapabilitiesForType(mime)
                if (profiles != null && caps.profileLevels.none { it.profile in profiles }) {
                    return@runCatching false
                }
                val video = caps.videoCapabilities ?: return@runCatching false
                if (width != null && height != null) {
                    if (!video.isSizeSupported(width, height)) return@runCatching false
                    if (frameRate != null && !video.areSizeAndRateSupported(width, height, frameRate)) {
                        return@runCatching false
                    }
                }
                true
            }.getOrDefault(false)
        }
    }
}

private fun hasSupportedAudioDecoder(codec: MediaAudioCodec?): Boolean {
    if (codec == null) return false
    if (codec == MediaAudioCodec.PCM_S16LE) return true // Raw 16-bit LE PCM is not a MediaCodec decoder.
    if (codec.prefersLocalDecoding) return false
    return safelyCheckMediaDecoder {
        val acceptedMimeTypes = if (codec == MediaAudioCodec.EAC3_JOC) {
            // An E-AC-3 decoder can decode its compatible bed; this does not promise Atmos output.
            setOf(codec.mimeType, MediaAudioCodec.EAC3.mimeType)
        } else {
            setOf(codec.mimeType)
        }
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            runCatching {
                !info.isEncoder && info.supportedTypes.any { type ->
                    acceptedMimeTypes.any { it.equals(type, ignoreCase = true) }
                }
            }.getOrDefault(false)
        }
    }
}

/** null = no known restriction; empty = profile/bit depth cannot safely be certified. */
internal fun requiredVideoDecoderProfiles(
    codec: MediaVideoCodec,
    profile: String?,
    bitDepth: Int?,
    dolbyVisionProfile: Int? = null,
    hdr10: Boolean = false,
    hdr10Plus: Boolean = false,
): Set<Int>? {
    val name = normalizeMediaCodec(profile)
    if (bitDepth != null && bitDepth !in setOf(8, 10, 12)) return emptySet()
    if (codec == MediaVideoCodec.DOLBY_VISION) {
        // Android's documented Dolby Vision profile constants map profiles 0..10 to these bits.
        return dolbyVisionProfile?.takeIf { it in 0..10 }?.let { setOf(1 shl it) } ?: emptySet()
    }
    if (codec in setOf(MediaVideoCodec.AVC, MediaVideoCodec.HEVC, MediaVideoCodec.AV1, MediaVideoCodec.VP9) && bitDepth == 12) {
        return emptySet() // A generic 10-bit profile is not proof of 12-bit output support.
    }
    return when (codec) {
        MediaVideoCodec.HEVC -> when {
            name !in setOf("", "main", "main10", "mainstillpicture") -> emptySet()
            bitDepth == 10 && name in setOf("main", "mainstillpicture") -> emptySet()
            hdr10Plus -> setOf(CodecProfileLevel.HEVCProfileMain10HDR10Plus)
            hdr10 -> setOf(CodecProfileLevel.HEVCProfileMain10HDR10, CodecProfileLevel.HEVCProfileMain10HDR10Plus)
            bitDepth == 10 || name == "main10" -> setOf(
                CodecProfileLevel.HEVCProfileMain10,
                CodecProfileLevel.HEVCProfileMain10HDR10,
                CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )
            name == "main" -> setOf(CodecProfileLevel.HEVCProfileMain)
            name == "mainstillpicture" -> setOf(CodecProfileLevel.HEVCProfileMainStill)
            name.isEmpty() -> null
            else -> emptySet()
        }
        MediaVideoCodec.AVC -> if (bitDepth == 10 && name !in setOf("high10", "high422", "high444", "high444predictive")) {
            emptySet()
        } else when (name) {
            "baseline" -> setOf(CodecProfileLevel.AVCProfileBaseline, CodecProfileLevel.AVCProfileConstrainedBaseline)
            "constrainedbaseline" -> setOf(CodecProfileLevel.AVCProfileConstrainedBaseline)
            "main" -> setOf(CodecProfileLevel.AVCProfileMain)
            "extended" -> setOf(CodecProfileLevel.AVCProfileExtended)
            "high" -> if (bitDepth == 10) emptySet() else setOf(CodecProfileLevel.AVCProfileHigh, CodecProfileLevel.AVCProfileConstrainedHigh)
            "constrainedhigh" -> setOf(CodecProfileLevel.AVCProfileConstrainedHigh)
            "high10" -> setOf(CodecProfileLevel.AVCProfileHigh10)
            "high422" -> setOf(CodecProfileLevel.AVCProfileHigh422)
            "high444", "high444predictive" -> setOf(CodecProfileLevel.AVCProfileHigh444)
            "" -> if (bitDepth == 10 || bitDepth == 12) emptySet() else null
            else -> emptySet()
        }
        MediaVideoCodec.AV1 -> when {
            name !in setOf("", "main", "main8", "main10", "0", "profile0") -> emptySet()
            bitDepth == 10 && name == "main8" -> emptySet()
            hdr10Plus -> setOf(CodecProfileLevel.AV1ProfileMain10HDR10Plus)
            hdr10 -> setOf(CodecProfileLevel.AV1ProfileMain10HDR10, CodecProfileLevel.AV1ProfileMain10HDR10Plus)
            bitDepth == 10 || name == "main10" -> setOf(
                CodecProfileLevel.AV1ProfileMain10,
                CodecProfileLevel.AV1ProfileMain10HDR10,
                CodecProfileLevel.AV1ProfileMain10HDR10Plus,
            )
            name.isNotEmpty() || bitDepth == 8 -> setOf(CodecProfileLevel.AV1ProfileMain8)
            else -> null
        }
        MediaVideoCodec.VP9 -> when {
            name !in setOf("", "0", "1", "2", "3", "profile0", "profile1", "profile2", "profile3") -> emptySet()
            bitDepth == 10 && name in setOf("0", "1", "profile0", "profile1") -> emptySet()
            name in setOf("3", "profile3") -> when {
                hdr10Plus -> setOf(CodecProfileLevel.VP9Profile3HDR10Plus)
                hdr10 -> setOf(CodecProfileLevel.VP9Profile3HDR, CodecProfileLevel.VP9Profile3HDR10Plus)
                else -> setOf(CodecProfileLevel.VP9Profile3, CodecProfileLevel.VP9Profile3HDR, CodecProfileLevel.VP9Profile3HDR10Plus)
            }
            hdr10Plus -> setOf(CodecProfileLevel.VP9Profile2HDR10Plus)
            hdr10 -> setOf(CodecProfileLevel.VP9Profile2HDR, CodecProfileLevel.VP9Profile2HDR10Plus)
            bitDepth == 10 || name in setOf("2", "profile2") -> setOf(
                CodecProfileLevel.VP9Profile2,
                CodecProfileLevel.VP9Profile2HDR,
                CodecProfileLevel.VP9Profile2HDR10Plus,
            )
            name in setOf("1", "profile1") -> setOf(CodecProfileLevel.VP9Profile1)
            name in setOf("0", "profile0") -> setOf(CodecProfileLevel.VP9Profile0)
            else -> null
        }
        else -> if (bitDepth != null && bitDepth > 8) emptySet() else null
    }
}
