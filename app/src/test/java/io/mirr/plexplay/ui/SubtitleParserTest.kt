package io.mirr.plexplay.ui

import io.mirr.plexplay.data.PlaybackSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.io.InputStream

class SubtitleParserTest {
    @Test
    fun keepsExternalSubtitleBytesWhenPlexStreamEndsUnexpectedly() {
        val body = "1\n00:00:01,000 --> 00:00:02,000\n외부 자막\n"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val interrupted = object : InputStream() {
            private var delivered = false

            override fun read(): Int = throw UnsupportedOperationException()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (delivered) throw IOException("unexpected end of stream")
                delivered = true
                bytes.copyInto(
                    destination = buffer,
                    destinationOffset = offset,
                    startIndex = 0,
                    endIndex = bytes.size,
                )
                return bytes.size
            }
        }

        assertEquals(body, readSubtitleBytes(interrupted).toString(Charsets.UTF_8))
    }

    @Test
    fun decodesUtf16LittleEndianSubtitle() {
        val body = "1\r\n00:00:01,000 --> 00:00:02,000\r\n안녕하세요\r\n"
        val encoded = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            body.toByteArray(Charsets.UTF_16LE)

        assertEquals(body, decodeSubtitleBytes(encoded))
    }

    @Test
    fun parsesTtmlSubtitleParagraphs() {
        val subtitle = PlaybackSubtitle(
            url = "https://example.invalid/subtitle.ttml",
            language = "kor",
            label = "한국어",
            mimeType = "application/ttml+xml",
            codec = "ttml",
            selected = true,
        )
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.250" end="00:00:03.000">첫째 줄<br/>둘째 줄</p>
                <p begin="4s" dur="1.5s">다음 자막</p>
              </div></body>
            </tt>
        """.trimIndent()

        val cues = parseManualSubtitleCues(body, subtitle)

        assertEquals(2, cues.size)
        assertEquals(1_250L, cues[0].startMs)
        assertEquals(3_000L, cues[0].endMs)
        assertEquals("첫째 줄\n둘째 줄", cues[0].text)
        assertEquals(4_000L, cues[1].startMs)
        assertEquals(5_500L, cues[1].endMs)
    }

    @Test
    fun parsesServerConvertedSrtUsingBodyInsteadOfOriginalCodec() {
        val subtitle = PlaybackSubtitle(
            url = "https://example.invalid/universal/subtitles",
            streamId = "701",
            isEmbedded = true,
            language = "kor",
            label = "한국어 내장 ASS",
            mimeType = "text/x-ssa",
            codec = "ass",
            selected = true,
        )
        val convertedSrt = """
            1
            00:00:02,000 --> 00:00:04,000
            서버에서 변환된 자막
        """.trimIndent()

        val cues = parseManualSubtitleCues(convertedSrt, subtitle)

        assertEquals(1, cues.size)
        assertEquals("서버에서 변환된 자막", cues.single().text)
    }

    @Test
    fun parsesExternalSrtWithoutBlankLinesBetweenCues() {
        val subtitle = PlaybackSubtitle(
            url = "https://example.invalid/subtitle.srt",
            language = "kor",
            label = "한국어 외부 자막",
            mimeType = "application/x-subrip",
            codec = "srt",
            selected = true,
        )
        val body = """
            1
            00:00:01,000 --> 00:00:02,000
            첫 번째
            2
            00:00:03,000 --> 00:00:04,000
            두 번째
        """.trimIndent()

        val cues = parseManualSubtitleCues(body, subtitle)

        assertEquals(2, cues.size)
        assertEquals("첫 번째", cues[0].text)
        assertEquals("두 번째", cues[1].text)
    }
}
