package io.mirr.plexplay.ui

import io.mirr.plexplay.data.PlaybackSubtitle
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class ExternalSubtitleTextTest {
    private fun sidecar(
        codec: String? = null,
        mime: String = "application/octet-stream",
        url: String = "https://example.invalid/subtitles/track?ticket=fixture",
        embedded: Boolean = false,
    ) = PlaybackSubtitle(url = url, codec = codec, mimeType = mime, isEmbedded = embedded,
        language = "kor", label = "외부 한국어", selected = true)

    @Test fun missingSidecarFormatStillAttemptsStyledText() {
        assertTrue(sidecar().isManualTextSubtitle())
    }

    @Test fun plainTextMimeWithParametersIsRecognized() {
        assertTrue(sidecar(mime = " Text/Plain; charset=EUC-KR ").isManualTextSubtitle())
    }

    @Test fun samiMimeWithoutCodecIsRecognized() {
        assertTrue(sidecar(mime = "application/x-sami").isManualTextSubtitle())
        assertTrue(sidecar(mime = "text/sami").isManualTextSubtitle())
    }

    @Test fun assMimeWithoutCodecIsRecognized() {
        assertTrue(sidecar(mime = "text/x-ass").isManualTextSubtitle())
    }

    @Test fun signedSubtitleFilePathsRecognizeTextExtensions() {
        listOf("SRT", "ASS", "SSA", "VTT", "TTML", "DFXP", "SMI", "SAMI").forEach {
            assertTrue(sidecar(mime = "application/download",
                url = "https://example.invalid/episode.$it?ticket=fixture#fragment").isManualTextSubtitle())
        }
    }

    @Test fun queryParametersDoNotMasqueradeAsFileExtensions() {
        assertFalse(sidecar(codec = "unsupported", url = "https://example.invalid/data?filename=subtitle.srt").isManualTextSubtitle())
    }

    @Test fun missingEmbeddedFormatDoesNotEnableTextCompanionByGuessing() {
        assertFalse(sidecar(embedded = true).isManualTextSubtitle())
    }

    @Test fun knownBitmapCodecsDoNotClaimCustomFontSupport() {
        listOf("pgs", "hdmv_pgs_subtitle", "dvd_subtitle", "vobsub", "dvb_subtitle", "xsub").forEach {
            assertFalse(sidecar(codec = it, mime = "text/plain").isManualTextSubtitle())
        }
    }

    @Test fun bitmapMimeAndFileExtensionsDoNotClaimCustomFontSupport() {
        assertFalse(sidecar(mime = "application/pgs").isManualTextSubtitle())
        assertFalse(sidecar(mime = "image/png").isManualTextSubtitle())
        assertFalse(sidecar(url = "https://example.invalid/a.sup?ticket=fixture").isManualTextSubtitle())
        assertFalse(sidecar(url = "https://example.invalid/a.idx").isManualTextSubtitle())
    }

    @Test fun unknownExternalSrtBodyUsesAppCuesWithoutOriginalVideo() {
        val subtitle = sidecar()
        assertTrue(subtitle.isManualTextSubtitle())
        val cues = parseManualSubtitleCues("1\n00:00:01,000 --> 00:00:03,000\n사용자 글꼴 자막\n", subtitle)
        assertEquals("사용자 글꼴 자막", cues.textAt(1_500))
        assertEquals("", cues.textAt(3_500))
    }

    @Test fun assFontOverridesBecomePlainTextForUserTypeface() {
        val body = """
            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Some Other Font,72
            [Events]
            Format: Layer, Start, End, Style, Text
            Dialogue: 0,0:00:01.00,0:00:03.00,Default,{\fnOtherFont\fs72}첫 줄\N둘째 줄
        """.trimIndent()
        val cues = parseManualSubtitleCues(body, sidecar(codec = "ass"))
        assertEquals("첫 줄\n둘째 줄", cues.single().text)
    }

    @Test fun koreanSamiEncodingAndFontTagsUsePlainAppText() {
        val body = "<SAMI><BODY><SYNC Start=1000><P Class=KRCC><FONT FACE='Other'>한국어 자막</FONT><SYNC Start=3000><P Class=KRCC>다음 줄</BODY></SAMI>"
        val decoded = decodeSubtitleBytes(body.toByteArray(Charset.forName("MS949")))
        val cues = parseManualSubtitleCues(decoded, sidecar(mime = "text/sami"))
        assertEquals("한국어 자막", cues.textAt(1_500))
    }

    @Test fun invalidUnknownSidecarDoesNotManufactureSubtitleCues() {
        assertTrue(sidecar().isManualTextSubtitle())
        assertTrue(parseManualSubtitleCues("<html>login required</html>", sidecar()).isEmpty())
    }
}
