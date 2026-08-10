package io.mirr.plexplay.ui

import io.mirr.plexplay.data.PlexItem
import io.mirr.plexplay.data.PlaybackQuality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCompatibilityTest {
    @Test
    fun originalUncertainMediaStartsWithUniversalCodec() {
        assertTrue(
            shouldPreferUniversalCodec(
                playbackQuality = PlaybackQuality.ORIGINAL,
                directPlaybackSupported = false,
            ),
        )
    }

    @Test
    fun supportedOriginalMediaKeepsDefaultPlayer() {
        assertFalse(
            shouldPreferUniversalCodec(
                playbackQuality = PlaybackQuality.ORIGINAL,
                directPlaybackSupported = true,
            ),
        )
    }

    @Test
    fun explicitQualityConversionKeepsTranscodePlayer() {
        assertFalse(
            shouldPreferUniversalCodec(
                playbackQuality = PlaybackQuality.HD_1080,
                directPlaybackSupported = false,
            ),
        )
    }

    @Test
    fun createsProminentMediaFeatureLabels() {
        val item = PlexItem(
            ratingKey = "42",
            key = "/library/metadata/42",
            type = "movie",
            title = "Demo",
            videoCodec = "av1",
            videoDynamicRange = "DOVI",
            dolbyVisionProfile = 8,
            audioCodec = "eac3",
            audioProfile = "atmos",
        )

        assertEquals(
            listOf("Dolby Vision", "AV1", "Dolby Atmos"),
            mediaFeatureLabels(item),
        )
    }

    @Test
    fun distinguishesHdr10PlusAndDolbyDigitalPlus() {
        val item = PlexItem(
            ratingKey = "7",
            key = "/library/metadata/7",
            type = "episode",
            title = "Demo",
            videoCodec = "hevc",
            videoDynamicRange = "HDR10+",
            audioCodec = "eac3",
        )

        assertEquals(
            listOf("HDR10+", "HEVC", "Dolby Digital+"),
            mediaFeatureLabels(item),
        )
    }
}
