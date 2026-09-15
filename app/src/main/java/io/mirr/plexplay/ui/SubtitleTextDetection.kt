package io.mirr.plexplay.ui

import io.mirr.plexplay.data.PlaybackSubtitle
import java.util.Locale

/**
 * Decide whether the app may try reading a subtitle as styled text.
 * Servers often omit a codec or return application/octet-stream for sidecars.
 * The body parser still has to find timed cues before the overlay becomes ready.
 * Known image formats must never be advertised as font-customizable.
 */
internal fun PlaybackSubtitle.isManualTextSubtitle(): Boolean {
    val codecName = codec?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val mime = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
    val extension = url.substringBefore('#').substringBefore('?')
        .substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (codecName in BitmapSubtitleCodecs || BitmapSubtitleMimeTokens.any(mime::contains)) return false
    if (codecName in TextSubtitleCodecs || TextSubtitleMimeTokens.any(mime::contains)) return true
    if (!isEmbedded && extension in setOf("sup", "idx")) return false
    if (mime == "text/plain") return true
    if (isEmbedded) return false
    if (extension in setOf("srt", "ass", "ssa", "vtt", "ttml", "dfxp", "smi", "sami")) return true
    // A missing format is not evidence of an image subtitle. Probe the sidecar,
    // without opening the 4K movie or handing font rendering over to LibVLC.
    return codecName in setOf("", "unknown", "none") &&
        (mime.isBlank() || mime in setOf("application/octet-stream", "binary/octet-stream") || mime.startsWith("text/"))
}

private val TextSubtitleCodecs = setOf(
    "srt", "subrip", "ass", "ssa", "vtt", "webvtt", "ttml", "dfxp",
    "smi", "sami", "mov_text", "tx3g", "ttxt", "text",
)
private val TextSubtitleMimeTokens = setOf("subrip", "ssa", "vtt", "ttml", "sami", "smi", "x-ass")
private val BitmapSubtitleCodecs = setOf(
    "pgs", "pgssub", "hdmv_pgs_subtitle", "hdmv_pgs", "dvdsub",
    "dvd_subtitle", "vobsub", "dvbsub", "dvb_subtitle", "xsub",
)
private val BitmapSubtitleMimeTokens = setOf("pgs", "vobsub", "dvbsub", "dvbsubs", "dvb.subtitle", "dvd", "xsub", "image/")
