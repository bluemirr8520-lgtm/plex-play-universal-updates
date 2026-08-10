package io.mirr.plexplay.ui

import io.mirr.plexplay.data.PlaybackSource
import io.mirr.plexplay.data.PlaybackSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniversalSubtitleBridgeTest {
    @Test
    fun choosesDirectFallbackInsteadOfSubtitleFreeTranscode() {
        val source = PlaybackSource(
            url = "https://plex.invalid/video/:/transcode/universal/start.m3u8?subtitles=none",
            fallbackUrls = listOf("https://plex.invalid/library/parts/77/file.mkv"),
            token = "token",
            title = "영상",
            subtitle = null,
            ratingKey = "77",
            durationMs = 60_000,
            resumePositionMs = 0,
        )

        assertEquals(
            "https://plex.invalid/library/parts/77/file.mkv",
            source.originalSubtitleMediaUrlForBridge(),
        )
    }

    @Test
    fun refusesToMapBitmapTrackToTextMetadata() {
        val koreanText = embeddedText("10", "kor", "한국어 SRT", "application/x-subrip")

        assertNull(
            chooseEmbeddedMetadata(
                language = "kor",
                label = "한국어 PGS",
                mimeType = "application/pgs",
                remaining = listOf(koreanText),
            ),
        )
    }

    @Test
    fun doesNotArbitrarilyConsumeOneOfSeveralUnmatchedTextTracks() {
        val koreanText = embeddedText("10", "kor", "한국어", "application/x-subrip")
        val englishText = embeddedText("11", "eng", "English", "application/x-subrip")

        assertNull(
            chooseEmbeddedMetadata(
                language = null,
                label = null,
                mimeType = "application/x-media3-cues",
                remaining = listOf(koreanText, englishText),
            ),
        )
    }

    @Test
    fun recognizesExternalTrackIdPrefixedByMergingMediaSource() {
        assertEquals(
            "stream:22",
            media3ExternalSidecarStableId("1:plex-sidecar:stream:22"),
        )
    }

    private fun embeddedText(
        id: String,
        language: String,
        label: String,
        mimeType: String,
    ) = PlaybackSubtitle(
        url = "https://plex.invalid/video/:/transcode/universal/subtitles?id=$id",
        streamId = id,
        isEmbedded = true,
        language = language,
        label = label,
        mimeType = mimeType,
        codec = "srt",
        selected = false,
    )
}
