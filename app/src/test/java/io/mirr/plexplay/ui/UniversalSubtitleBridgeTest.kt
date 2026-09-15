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

    @Test
    fun selectsSidecarWithoutDependingOnUnsupported4kVideoUrl() {
        val external = embeddedText("22", "kor", "한국어 ASS", "text/x-ssa")
            .copy(
                url = "https://media.invalid/subtitle/22?ticket=a%2Fb%2Bc",
                isEmbedded = false,
                codec = "ass",
            )
        val source = bridgeSource().copy(subtitles = listOf(external))

        assertEquals(external, source.requestedExternalSubtitleForBridge(external.stableId))
        assertEquals(
            "https://media.invalid/subtitle/22?ticket=a%2Fb%2Bc",
            source.requestedExternalSubtitleForBridge(external.stableId)?.url,
        )
    }

    @Test
    fun findsSidecarInCompatibilityMetadataWithoutChangingItsUrl() {
        val external = embeddedText("23", "kor", "한국어 SRT", "application/x-subrip")
            .copy(isEmbedded = false)
        val source = bridgeSource().copy(compatibilitySubtitles = listOf(external))

        assertEquals(external, source.requestedExternalSubtitleForBridge(external.stableId))
    }

    @Test
    fun embeddedSubtitleStillUsesOriginalContainerBridge() {
        val embedded = embeddedText("24", "kor", "한국어 ASS", "text/x-ssa")
        val source = bridgeSource().copy(compatibilitySubtitles = listOf(embedded))

        assertNull(source.requestedExternalSubtitleForBridge(embedded.stableId))
    }

    @Test
    fun noExplicitSelectionDoesNotOpenAnArbitraryExternalSidecar() {
        val external = embeddedText("25", "kor", "한국어", "application/x-subrip")
            .copy(isEmbedded = false)
        val source = bridgeSource().copy(subtitles = listOf(external))

        assertNull(source.requestedExternalSubtitleForBridge(null))
        assertNull(source.requestedExternalSubtitleForBridge("missing-track"))
    }

    @Test
    fun sidecarTimelineUsesFullMovieDurationNotLastCue() {
        assertEquals(7_200_000_000L, subtitleBridgeTimelineDurationUs(7_200_000L, 600_000L))
    }

    @Test
    fun unknownDurationKeepsLateSubtitlesAndResumeAvailable() {
        assertEquals(90_000_000_000L, subtitleBridgeTimelineDurationUs(0L, 3_600_000L))
        assertEquals(86_400_000_000L, subtitleBridgeTimelineDurationUs(-1L, 0L))
    }

    @Test
    fun staleDurationDoesNotPlaceResumedSidecarAtEndOfTimeline() {
        assertEquals(93_600_000_000L, subtitleBridgeTimelineDurationUs(3_600_000L, 7_200_000L))
    }

    @Test
    fun timelineMicrosecondConversionDoesNotOverflow() {
        assertEquals(
            (Long.MAX_VALUE / 1_000L) * 1_000L,
            subtitleBridgeTimelineDurationUs(Long.MAX_VALUE, Long.MAX_VALUE),
        )
    }

    private fun bridgeSource() = PlaybackSource(
        url = "https://media.invalid/unsupported-container/4k-video",
        token = "",
        title = "영상",
        subtitle = null,
        ratingKey = "77",
        durationMs = 7_200_000L,
        resumePositionMs = 600_000L,
    )
}
