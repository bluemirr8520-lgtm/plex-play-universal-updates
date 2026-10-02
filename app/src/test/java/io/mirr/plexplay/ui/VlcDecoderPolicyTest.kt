package io.mirr.plexplay.ui

import org.junit.Assert.*
import org.junit.Test

class VlcDecoderPolicyTest {
    @Test fun defaultsToAutomaticAndRestoresSoftware() {
        assertEquals(VlcDecoderMode.AUTO, VlcDecoderMode.fromStorage(null))
        assertEquals(VlcDecoderMode.AUTO, VlcDecoderMode.fromStorage("unknown"))
        assertEquals(VlcDecoderMode.SOFTWARE, VlcDecoderMode.fromStorage("software"))
    }

    @Test fun automaticKeepsHardwareAndNormalAudioSubtitleModules() {
        val result = vlcDecoderConfiguration(VlcDecoderMode.AUTO, false)
        assertTrue(result.useHardware)
        assertTrue(result.mediaOptions.isEmpty())
    }

    @Test fun softwareOptOutDoesNotRestrictCodecModules() {
        for (fallback in listOf(false, true)) {
            val result = vlcDecoderConfiguration(VlcDecoderMode.SOFTWARE, fallback)
            assertFalse(result.useHardware)
            assertEquals(listOf(":avcodec-hw=none"), result.mediaOptions)
            assertFalse(result.mediaOptions.any { it.startsWith(":codec=") })
        }
    }

    @Test fun automaticFallbackUsesSameSoftwareConfiguration() {
        assertEquals(
            vlcDecoderConfiguration(VlcDecoderMode.SOFTWARE, false),
            vlcDecoderConfiguration(VlcDecoderMode.AUTO, true),
        )
    }

    @Test fun fallbackCanOnlyBeTakenOnceInAutomaticMode() {
        assertTrue(retry(VlcDecoderMode.AUTO, false))
        assertFalse(retry(VlcDecoderMode.AUTO, true))
        assertFalse(retry(VlcDecoderMode.SOFTWARE, false))
        assertFalse(retry(VlcDecoderMode.SOFTWARE, true))
    }

    @Test fun transientConnectionErrorsKeepDecoder() {
        assertFalse(retry(connectionRecoveryExhausted = false))
    }

    @Test fun runtimeErrorsDoNotForceSoftwareEvenAfterReconnects() {
        assertFalse(retry(playbackHasProgressed = true))
    }

    @Test fun uhdIncludingLetterboxAndPortraitNeverForcesSoftware() {
        for ((width, height) in listOf(3840 to 2160, 3840 to 1600, 1600 to 3840, 7680 to 4320)) {
            assertFalse(retry(width = width, height = height))
        }
    }

    @Test fun missingOrInvalidDimensionsDoNotForceSoftware() {
        for ((width, height) in listOf(null to null, 3840 to null, 0 to 1080, 1920 to -1)) {
            assertFalse(retry(width = width, height = height))
        }
    }

    private fun retry(
        mode: VlcDecoderMode = VlcDecoderMode.AUTO,
        fallbackUsed: Boolean = false,
        connectionRecoveryExhausted: Boolean = true,
        playbackHasProgressed: Boolean = false,
        width: Int? = 1920,
        height: Int? = 1080,
    ) = shouldRetryVlcWithSoftware(
        mode, fallbackUsed, connectionRecoveryExhausted, playbackHasProgressed, width, height,
    )
}
