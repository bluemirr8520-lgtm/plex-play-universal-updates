package io.mirr.plexplay.data

import java.util.Locale

/** Source codec families, not a claim that the active device/output supports every profile. */
internal enum class MediaVideoCodec(val mimeType: String, val featureLabel: String?) {
    AVC("video/avc", null),
    HEVC("video/hevc", "HEVC"),
    AV1("video/av01", "AV1"),
    VP9("video/x-vnd.on2.vp9", "VP9"),
    VP8("video/x-vnd.on2.vp8", null),
    MPEG2("video/mpeg2", null),
    MPEG4("video/mp4v-es", null),
    VC1("video/wvc1", null),
    WMV3("video/x-ms-wmv", null),
    DOLBY_VISION("video/dolby-vision", null),
}

internal enum class MediaAudioCodec(
    val mimeType: String,
    val featureLabel: String?,
    val prefersLocalDecoding: Boolean = false,
) {
    TRUEHD("audio/true-hd", "Dolby TrueHD", true),
    MLP("audio/mlp", "MLP", true),
    DTS("audio/vnd.dts", "DTS", true),
    DTS_HD("audio/vnd.dts.hd", "DTS-HD", true),
    AC3("audio/ac3", "Dolby Digital"),
    EAC3("audio/eac3", "Dolby Digital+"),
    EAC3_JOC("audio/eac3-joc", "Dolby Atmos"),
    AAC("audio/mp4a-latm", null),
    FLAC("audio/flac", "FLAC"),
    PCM_S16LE("audio/raw", "PCM"),
    PCM("audio/raw", "PCM", true),
    MP3("audio/mpeg", null),
    MP2("audio/mpeg-L2", null, true),
    OPUS("audio/opus", null),
    VORBIS("audio/vorbis", null),
    ALAC("audio/alac", "ALAC", true),
}

internal fun normalizeMediaCodec(codec: String?): String =
    codec.orEmpty().trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

internal fun mediaVideoCodec(codec: String?): MediaVideoCodec? =
    when (normalizeMediaCodec(codec)) {
        "h264", "avc", "avc1", "avc3", "x264", "vmpeg4isoavc", "videoavc" -> MediaVideoCodec.AVC
        "h265", "hevc", "hev1", "hvc1", "x265", "vmpeghisohevc", "videohevc" -> MediaVideoCodec.HEVC
        "av1", "av01", "vav1", "videoav01" -> MediaVideoCodec.AV1
        "vp9", "vp09", "vvp9", "videoxvndon2vp9" -> MediaVideoCodec.VP9
        "vp8", "vp08", "vvp8", "videoxvndon2vp8" -> MediaVideoCodec.VP8
        "mpeg2", "mpeg2video", "mp2v", "vmpeg2", "videompeg2" -> MediaVideoCodec.MPEG2
        "mpeg4", "mp4v", "mp4ves", "vmpeg4isoasp", "videomp4ves" -> MediaVideoCodec.MPEG4
        "vc1", "wvc1", "videowvc1" -> MediaVideoCodec.VC1
        "wmv3", "wmv9", "videoxmswmv" -> MediaVideoCodec.WMV3
        "dovi", "dvhe", "dvh1", "dvav", "dva1", "videodolbyvision" -> MediaVideoCodec.DOLBY_VISION
        else -> null
    }

internal fun mediaAudioCodec(codec: String?): MediaAudioCodec? =
    when (normalizeMediaCodec(codec)) {
        "truehd", "dolbytruehd", "trhd", "mlpfb", "atruehd", "audiotruehd",
        "audiovnddolbymlp" -> MediaAudioCodec.TRUEHD
        "mlp", "amlp", "audiomlp" -> MediaAudioCodec.MLP
        "dts", "dca", "adts", "audiovnddts" -> MediaAudioCodec.DTS
        "dtshd", "dtshdma", "dtshdhra", "dtsma", "dtshighresolution", "dtsmasteraudio",
        "dtsl", "dtsh", "adtslossless", "adtsexpress", "audiovnddtshd" -> MediaAudioCodec.DTS_HD
        "ac3", "a52", "aac3", "audioac3" -> MediaAudioCodec.AC3
        "eac3", "ec3", "aeac3", "audioeac3" -> MediaAudioCodec.EAC3
        "eac3joc", "ec3joc", "aeac3joc", "audioeac3joc" -> MediaAudioCodec.EAC3_JOC
        "aac", "aaclc", "heaac", "heaacv2", "aaclatm", "mp4a", "mp4a402", "mp4a405", "mp4a4029", "aaac",
        "aaacmpeg4lc", "aaacmpeg4he", "audiomp4alatm" -> MediaAudioCodec.AAC
        "flac", "aflac", "audioflac" -> MediaAudioCodec.FLAC
        "pcms16le" -> MediaAudioCodec.PCM_S16LE
        "pcm", "lpcm", "pcmbluray", "pcmdvd", "pcms16be", "pcms24le", "pcms24be",
        "pcms32le", "pcms32be", "pcmf32le", "pcmf32be", "pcmf64le", "pcmf64be",
        "pcmu8", "pcms8", "apcmintlit", "apcmintbig", "apcmfloatieee", "audioraw" -> MediaAudioCodec.PCM
        "mp3", "ampegl3", "audiompeg" -> MediaAudioCodec.MP3
        "mp2", "ampegl2", "audiompegl2" -> MediaAudioCodec.MP2
        "opus", "aopus", "audioopus" -> MediaAudioCodec.OPUS
        "vorbis", "avorbis", "audiovorbis" -> MediaAudioCodec.VORBIS
        "alac", "aalac", "audioalac" -> MediaAudioCodec.ALAC
        else -> null
    }

internal fun mediaVideoMimeType(codec: String?): String? = mediaVideoCodec(codec)?.mimeType
internal fun mediaAudioMimeType(codec: String?): String? = mediaAudioCodec(codec)?.mimeType
internal fun requiresLocalAudioDecoding(codec: String?): Boolean =
    mediaAudioCodec(codec)?.prefersLocalDecoding == true
