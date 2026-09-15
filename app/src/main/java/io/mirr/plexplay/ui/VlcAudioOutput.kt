package io.mirr.plexplay.ui

import android.content.Context
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import java.util.Locale

private const val VlcAudioOutputPreference = "vlc_audio_output_mode"

internal enum class VlcAudioOutputMode(
    val preferenceKey: String,
    val label: String,
    val detail: String,
) {
    AUTO("auto", "자동", "현재 HDMI 출력이 지원하면 패스스루, 확인되지 않으면 PCM 디코딩"),
    PCM("pcm", "호환 PCM", "VLC가 오디오를 디코딩합니다. Atmos 객체 정보 출력은 보장하지 않습니다."),
    PASSTHROUGH(
        "passthrough",
        "HDMI 패스스루",
        "현재 HDMI 장치의 해당 형식 지원이 확인될 때만 사용하며, 그 외에는 PCM으로 재생합니다.",
    );

    companion object {
        fun fromPreferenceKey(value: String?): VlcAudioOutputMode =
            entries.firstOrNull { it.preferenceKey == value } ?: AUTO
    }
}

internal fun loadVlcAudioOutputMode(context: Context): VlcAudioOutputMode =
    VlcAudioOutputMode.fromPreferenceKey(
        context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
            .getString(VlcAudioOutputPreference, null),
    )

internal fun saveVlcAudioOutputMode(context: Context, mode: VlcAudioOutputMode) {
    context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        .edit()
        .putString(VlcAudioOutputPreference, mode.preferenceKey)
        .apply()
}

internal data class VlcAudioOutputDecision(
    val passthrough: Boolean,
    val label: String,
    val reason: String,
)

internal data class VlcDirectAudioSupport(
    val supported: Boolean,
    val reason: String,
)

/** Facts kept separate from Android calls so route ambiguity is covered by local JVM tests. */
internal data class VlcAudioRouteEvidence(
    val sdkInt: Int,
    val currentRouteIsHdmi: Boolean = false,
    val hdmiOutputCount: Int = 0,
    val nonHdmiOutputCount: Int = 0,
    val hdmiPlugged: Boolean = false,
    val hdmiProfileSupported: Boolean = false,
    val directBitstreamSupported: Boolean = false,
)

internal fun evaluateVlcDirectAudioSupport(
    evidence: VlcAudioRouteEvidence,
): VlcDirectAudioSupport = with(evidence) {
    when {
        sdkInt >= 33 && !currentRouteIsHdmi ->
            VlcDirectAudioSupport(false, "현재 미디어 출력이 HDMI로 확인되지 않아 PCM으로 재생합니다.")
        sdkInt >= 33 && directBitstreamSupported ->
            VlcDirectAudioSupport(true, "현재 HDMI 출력에서 이 오디오 형식의 비트스트림 지원이 확인되었습니다.")
        sdkInt >= 33 ->
            VlcDirectAudioSupport(false, "현재 HDMI 출력에서 이 오디오 형식의 패스스루 지원이 확인되지 않았습니다.")
        sdkInt < 29 ->
            VlcDirectAudioSupport(false, "이 Android 버전에서는 정확한 HDMI 형식 지원을 확인할 수 없어 PCM으로 재생합니다.")
        hdmiOutputCount != 1 || nonHdmiOutputCount != 0 || !hdmiPlugged ->
            VlcDirectAudioSupport(false, "현재 HDMI 경로를 확실히 확인할 수 없어 PCM으로 재생합니다.")
        !hdmiProfileSupported || !directBitstreamSupported ->
            VlcDirectAudioSupport(false, "HDMI 장치의 코덱·채널·샘플레이트 지원이 확인되지 않아 PCM으로 재생합니다.")
        else ->
            VlcDirectAudioSupport(true, "HDMI 단독 출력에서 이 오디오 형식의 직접 재생 지원이 확인되었습니다.")
    }
}

/** Does not infer TrueHD from an Atmos label or generic MLP (which can also be DVD-Audio). */
internal fun isVlcTrueHdAudioCodec(codec: String?): Boolean =
    normalizedVlcAudioCodec(codec) in setOf(
        "truehd", "dolbytruehd", "mlpfb", "atruehd", "audiotruehd", "audiovnddolbymlp",
    )

internal fun decideVlcAudioOutput(
    mode: VlcAudioOutputMode,
    audioCodec: String?,
    audioChannels: Int? = null,
    audioSampleRate: Int? = null,
    directSupport: VlcDirectAudioSupport,
): VlcAudioOutputDecision {
    val pcmReason = when {
        mode == VlcAudioOutputMode.PCM ->
            "호환 PCM 모드: VLC가 오디오를 디코딩합니다."
        vlcAudioEncoding(audioCodec) == null ->
            "오디오 형식의 HDMI 패스스루 지원을 확인할 수 없어 PCM으로 재생합니다."
        vlcAudioChannelMask(audioChannels) == null || audioSampleRate == null || audioSampleRate <= 0 ->
            "오디오 채널·샘플레이트를 확인할 때까지 PCM으로 재생합니다."
        !directSupport.supported -> directSupport.reason
        else -> null
    }
    return if (pcmReason != null) {
        VlcAudioOutputDecision(false, "PCM 디코딩", pcmReason)
    } else {
        VlcAudioOutputDecision(
            true,
            "HDMI 패스스루",
            directSupport.reason + " Atmos 출력은 소스와 수신 장치에 따라 달라집니다.",
        )
    }
}

/**
 * Resolve this again for selected-track and output-route changes, not only at player creation.
 * Unknown format/route information must never enable VLC's global encoded-output switch.
 */
internal fun detectVlcAudioOutput(
    context: Context,
    mode: VlcAudioOutputMode,
    audioCodec: String?,
    audioChannels: Int? = null,
    audioSampleRate: Int? = null,
): VlcAudioOutputDecision {
    val encoding = vlcAudioEncoding(audioCodec)
    val channelMask = vlcAudioChannelMask(audioChannels)
    val directSupport = if (
        mode == VlcAudioOutputMode.PCM || encoding == null ||
        channelMask == null || audioSampleRate == null || audioSampleRate <= 0
    ) {
        VlcDirectAudioSupport(false, "오디오 출력 형식이 확인되지 않았습니다.")
    } else {
        inspectVlcDirectAudioSupport(
            context = context,
            encoding = encoding,
            channelMask = channelMask,
            channels = requireNotNull(audioChannels),
            sampleRate = audioSampleRate,
        )
    }
    return decideVlcAudioOutput(mode, audioCodec, audioChannels, audioSampleRate, directSupport)
}

private fun inspectVlcDirectAudioSupport(
    context: Context,
    encoding: Int,
    channelMask: Int,
    channels: Int,
    sampleRate: Int,
): VlcDirectAudioSupport = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return@runCatching evaluateVlcDirectAudioSupport(VlcAudioRouteEvidence(Build.VERSION.SDK_INT))
    }
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return@runCatching VlcDirectAudioSupport(false, "오디오 출력 정보를 확인할 수 없습니다.")
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()
    val format = AudioFormat.Builder()
        .setEncoding(encoding)
        .setSampleRate(sampleRate)
        .setChannelMask(channelMask)
        .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val routes = manager.getAudioDevicesForAttributes(attributes)
        val currentRouteIsHdmi = routes.isNotEmpty() && routes.all { it.isSink && it.isVlcHdmiOutput() }
        val supported = currentRouteIsHdmi &&
            (AudioManager.getDirectPlaybackSupport(format, attributes) and
                AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED) != 0
        return@runCatching evaluateVlcDirectAudioSupport(
            VlcAudioRouteEvidence(
                sdkInt = Build.VERSION.SDK_INT,
                currentRouteIsHdmi = currentRouteIsHdmi,
                directBitstreamSupported = supported,
            ),
        )
    }

    // Before API 33, isDirectPlaybackSupported checks ALL outputs, not the active route.
    // Accept only a single HDMI sink with a live plug announcement and no competing sink,
    // including built-in speakers. Ambiguous older TVs deliberately use PCM.
    val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
    val hdmiOutputs = outputs.filter { it.isVlcHdmiOutput() }
    val plug = context.registerReceiver(null, IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG))
    val plugged = plug?.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1
    val advertisedEncoding = plug?.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS)?.contains(encoding) == true
    val advertisedChannels = (plug?.getIntExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, 0) ?: 0) >= channels
    val unambiguous = hdmiOutputs.size == 1 && outputs.size == 1 && plugged
    val profileSupported = unambiguous && advertisedEncoding && advertisedChannels &&
        hdmiOutputs.single().supportsVlcAudioProfile(encoding, channelMask, channels, sampleRate)
    val supported = profileSupported && AudioTrack.isDirectPlaybackSupported(format, attributes)
    evaluateVlcDirectAudioSupport(
        VlcAudioRouteEvidence(
            sdkInt = Build.VERSION.SDK_INT,
            hdmiOutputCount = hdmiOutputs.size,
            nonHdmiOutputCount = outputs.size - hdmiOutputs.size,
            hdmiPlugged = plugged,
            hdmiProfileSupported = profileSupported,
            directBitstreamSupported = supported,
        ),
    )
}.getOrElse {
    VlcDirectAudioSupport(false, "이 기기의 현재 HDMI 출력 지원을 확인할 수 없어 PCM으로 재생합니다.")
}

private fun AudioDeviceInfo.isVlcHdmiOutput(): Boolean =
    type == AudioDeviceInfo.TYPE_HDMI ||
        type == AudioDeviceInfo.TYPE_HDMI_ARC ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_HDMI_EARC)

private fun AudioDeviceInfo.supportsVlcAudioProfile(
    encoding: Int,
    channelMask: Int,
    channels: Int,
    sampleRate: Int,
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Do not combine the sample rate of one codec with the channel mask of another.
        return audioProfiles.any { profile ->
            profile.format == encoding &&
                sampleRate in profile.sampleRates &&
                (channelMask in profile.channelMasks ||
                    profile.channelIndexMasks.any { Integer.bitCount(it) == channels })
        }
    }
    // API 29/30 only expose aggregate arrays; the exact-format direct query above is
    // additionally required, with all non-HDMI devices excluded first.
    return encoding in encodings && channels in channelCounts && sampleRate in sampleRates
}

private fun normalizedVlcAudioCodec(codec: String?): String =
    codec.orEmpty().trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private fun vlcAudioEncoding(codec: String?): Int? {
    if (isVlcTrueHdAudioCodec(codec)) return AudioFormat.ENCODING_DOLBY_TRUEHD
    return when (normalizedVlcAudioCodec(codec)) {
        "ac3", "a52", "aac3", "audioac3" -> AudioFormat.ENCODING_AC3
        "eac3", "ec3", "aeac3", "audioeac3" -> AudioFormat.ENCODING_E_AC3
        "eac3joc", "audioeac3joc" -> AudioFormat.ENCODING_E_AC3_JOC
        "dts", "dca", "adts", "audiovnddts" -> AudioFormat.ENCODING_DTS
        "dtshd", "dtsma", "adtslossless", "adtsexpress", "audiovnddtshd" -> AudioFormat.ENCODING_DTS_HD
        else -> null
    }
}

// A channel count alone cannot establish arbitrary layouts. Only the common movie layouts
// below are queried; unusual or unknown layouts keep the decoder's compatible PCM path.
private fun vlcAudioChannelMask(channels: Int?): Int? = when (channels) {
    1 -> AudioFormat.CHANNEL_OUT_MONO
    2 -> AudioFormat.CHANNEL_OUT_STEREO
    6 -> AudioFormat.CHANNEL_OUT_5POINT1
    8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
    else -> null
}

