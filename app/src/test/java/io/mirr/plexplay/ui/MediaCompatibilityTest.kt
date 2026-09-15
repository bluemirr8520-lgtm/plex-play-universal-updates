package io.mirr.plexplay.ui

import android.media.MediaCodecInfo.CodecProfileLevel
import io.mirr.plexplay.data.MediaVideoCodec
import io.mirr.plexplay.data.PlexItem
import io.mirr.plexplay.data.PlaybackQuality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCompatibilityTest {

    @Test
    fun discAudioFamiliesPreferLocalVlcOnlyForOriginalQuality() {
        listOf("DTS", "DTS-HD MA", "A_DTS/LOSSLESS", "pcm_bluray", "pcm_s24le", "mlp", "alac").forEach { codec ->
            assertTrue(codec, shouldPreferUniversalCodec(PlaybackQuality.ORIGINAL, true, codec))
            PlaybackQuality.entries.filter { it != PlaybackQuality.ORIGINAL }.forEach { quality ->
                assertFalse(codec, shouldPreferUniversalCodec(quality, false, codec))
            }
        }
    }

    @Test
    fun ordinarySupportedAudioDoesNotMoveHardwareCapableVideoToVlc() {
        listOf("aac", "eac3", "eac3-joc", "flac", "pcm_s16le", "opus").forEach {
            assertFalse(it, shouldPreferUniversalCodec(PlaybackQuality.ORIGINAL, true, it))
        }
    }

    @Test
    fun unknownCodecCannotBeCertifiedEvenIfInjectedDecoderFactsArePositive() {
        listOf(null, "", "newvideo").forEach {
            assertFalse(evaluateDevicePlaybackCompatibility(it, "aac", true, true, true).directPlaybackSupported)
        }
        listOf(null, "", "newaudio").forEach {
            assertFalse(evaluateDevicePlaybackCompatibility("hevc", it, true, true, true).directPlaybackSupported)
        }
    }

    @Test
    fun eachMissingCapabilityKeepsPlaybackUnconfirmed() {
        assertFalse(evaluateDevicePlaybackCompatibility("hevc", "aac", false, true, true).directPlaybackSupported)
        assertFalse(evaluateDevicePlaybackCompatibility("hevc", "aac", true, false, true).directPlaybackSupported)
        assertFalse(evaluateDevicePlaybackCompatibility("hevc", "aac", true, true, false).directPlaybackSupported)
        assertTrue(evaluateDevicePlaybackCompatibility("hevc", "aac", true, true, true).directPlaybackSupported)
    }

    @Test
    fun codecServiceFailureIsNeverPositiveEvidence() {
        assertFalse(safelyCheckMediaDecoder { throw IllegalStateException("codec service unavailable") })
        assertFalse(safelyCheckMediaDecoder { false })
        assertTrue(safelyCheckMediaDecoder { true })
    }

    @Test
    fun knownUhdDimensionsRequireHardwareEvidence() {
        assertTrue(requiresHardwareVideoDecoder(3840, 2160))
        assertTrue(requiresHardwareVideoDecoder(4096, 2160))
        assertTrue(requiresHardwareVideoDecoder(7680, 4320))
        assertFalse(requiresHardwareVideoDecoder(1920, 1080))
        assertFalse(requiresHardwareVideoDecoder(null, null))
    }

    @Test
    fun knownSoftwareDecodersAreNotFourKHardwareProof() {
        listOf("OMX.google.hevc.decoder", "c2.android.hevc.decoder", "c2.google.av1.decoder", "OMX.ffmpeg.hevc.decoder").forEach {
            assertFalse(it, isHardwareVideoDecoder(it, null))
            assertFalse(it, isHardwareVideoDecoder(it, true))
        }
        assertFalse(isHardwareVideoDecoder("c2.vendor.av1.decoder", false))
        assertTrue(isHardwareVideoDecoder("c2.vendor.av1.decoder", true))
        assertTrue(isHardwareVideoDecoder("OMX.qcom.video.decoder.hevc", null))
        assertFalse(isHardwareVideoDecoder("OMX.unknown.video.decoder.hevc", null))
    }

    @Test
    fun hevcMain10DoesNotAcceptAnEightBitMainOnlyDecoder() {
        val profiles = requiredVideoDecoderProfiles(MediaVideoCodec.HEVC, "Main 10", 10).orEmpty()
        assertTrue(CodecProfileLevel.HEVCProfileMain10 in profiles)
        assertFalse(CodecProfileLevel.HEVCProfileMain in profiles)
    }

    @Test
    fun hdr10PlusRequiresTheCorrespondingDecoderProfile() {
        val profiles = requiredVideoDecoderProfiles(MediaVideoCodec.HEVC, "Main 10", 10, hdr10Plus = true)
        assertEquals(setOf(CodecProfileLevel.HEVCProfileMain10HDR10Plus), profiles)
        assertFalse(CodecProfileLevel.HEVCProfileMain10HDR10 in profiles.orEmpty())
    }

    @Test
    fun knownUnsupportedVideoProfilesAreNotDowngradedToOrdinaryMain() {
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.HEVC, "Main 12", 12))
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.AV1, "Professional", 10))
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.AVC, "High", 10))
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.HEVC, "Main 4:2:2 10", 10))
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.VP9, "Profile 0", 10))
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.AV1, "Main 8", 10))
    }

    @Test
    fun tenBitAv1AndVp9RequireTenBitDecoderProfiles() {
        val av1 = requiredVideoDecoderProfiles(MediaVideoCodec.AV1, "Main", 10).orEmpty()
        assertTrue(CodecProfileLevel.AV1ProfileMain10 in av1)
        assertFalse(CodecProfileLevel.AV1ProfileMain8 in av1)
        val vp9 = requiredVideoDecoderProfiles(MediaVideoCodec.VP9, "Profile 2", 10).orEmpty()
        assertTrue(CodecProfileLevel.VP9Profile2 in vp9)
        assertFalse(CodecProfileLevel.VP9Profile0 in vp9)
    }

    @Test
    fun dolbyVisionNeedsAnExplicitKnownProfile() {
        assertEquals(
            setOf(CodecProfileLevel.DolbyVisionProfileDvheSt),
            requiredVideoDecoderProfiles(MediaVideoCodec.DOLBY_VISION, null, 10, dolbyVisionProfile = 8),
        )
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.DOLBY_VISION, null, 10))
        assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(MediaVideoCodec.DOLBY_VISION, null, 10, 99))
    }

    @Test
    fun bitDepthAloneDoesNotCertifyTwelveBitVideo() {
        listOf(MediaVideoCodec.AVC, MediaVideoCodec.HEVC, MediaVideoCodec.VP9, MediaVideoCodec.AV1).forEach {
            assertEquals(emptySet<Int>(), requiredVideoDecoderProfiles(it, null, 12))
        }
    }

    @Test
    fun sourceBadgesRecognizeDiscAudioWithoutPromisingAtmos() {
        val base = PlexItem("1", "/library/metadata/1", "movie", "Demo", videoCodec = "h.265")
        assertEquals(listOf("HEVC", "Dolby TrueHD"), mediaFeatureLabels(base.copy(audioCodec = "A_TRUEHD")))
        assertEquals(listOf("HEVC", "DTS-HD"), mediaFeatureLabels(base.copy(audioCodec = "DTS-HD MA")))
        assertEquals(listOf("HEVC", "PCM"), mediaFeatureLabels(base.copy(audioCodec = "pcm_bluray")))
        assertEquals(listOf("HEVC", "FLAC"), mediaFeatureLabels(base.copy(audioCodec = "flac")))
    }

    @Test
    fun atmosBadgeRequiresAnExactMetadataTokenOrJocCodec() {
        val base = PlexItem("1", "/library/metadata/1", "movie", "Demo", audioCodec = "eac3")
        assertEquals(listOf("Dolby Digital+"), mediaFeatureLabels(base.copy(audioDisplayTitle = "Atmospheric soundtrack")))
        assertEquals(listOf("Dolby Atmos"), mediaFeatureLabels(base.copy(audioCodec = "eac3-joc")))
        assertEquals(listOf("Dolby Atmos"), mediaFeatureLabels(base.copy(audioProfile = "Dolby Atmos")))
    }

    @Test
    fun dtsXBadgeIsNotHiddenByDtsHdFamily() {
        val item = PlexItem("1", "/library/metadata/1", "movie", "Demo", audioCodec = "dts-hd", audioDisplayTitle = "DTS:X 7.1")
        assertEquals(listOf("DTS:X"), mediaFeatureLabels(item))
    }

    @Test
    fun wideColorPrimariesAloneDoNotInventHdrMetadata() {
        val item = PlexItem(
            "1", "/library/metadata/1", "movie", "Demo",
            videoCodec = "hevc", videoColorPrimaries = "bt2020", videoColorTransfer = "bt709",
        )
        assertEquals(listOf("HEVC"), mediaFeatureLabels(item))
    }
    @Test
    fun trueHdUsesLocalVlcEvenWhenPlatformAdvertisesDirectOutput() {
        listOf("truehd", "True-HD", "A_TRUEHD", "mlpfb", "audio/true-hd").forEach {
            assertTrue(shouldPreferUniversalCodec(PlaybackQuality.ORIGINAL, true, it))
        }
    }

    @Test
    fun trueHdDoesNotOverrideUserSelectedQualityConversion() {
        assertFalse(shouldPreferUniversalCodec(PlaybackQuality.HD_1080, true, "truehd"))
        assertFalse(shouldPreferUniversalCodec(PlaybackQuality.ORIGINAL, true, "eac3"))
    }

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
