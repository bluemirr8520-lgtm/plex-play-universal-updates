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
        assertTrue(shouldRetryVlcWithSoftware(VlcDecoderMode.AUTO, false))
        assertFalse(shouldRetryVlcWithSoftware(VlcDecoderMode.AUTO, true))
        assertFalse(shouldRetryVlcWithSoftware(VlcDecoderMode.SOFTWARE, false))
        assertFalse(shouldRetryVlcWithSoftware(VlcDecoderMode.SOFTWARE, true))
    }
}
