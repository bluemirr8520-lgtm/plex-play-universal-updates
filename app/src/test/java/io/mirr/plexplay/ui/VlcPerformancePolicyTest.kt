package io.mirr.plexplay.ui

import org.junit.Assert.*
import org.junit.Test

class VlcPerformancePolicyTest {
    @Test fun savedModesStayCompatible() {
        for (mode in VlcOptimizationMode.entries) {
            assertEquals(mode, VlcOptimizationMode.fromStorage(mode.storage))
        }
        assertEquals(VlcOptimizationMode.AUTO, VlcOptimizationMode.fromStorage(null))
        assertEquals(VlcOptimizationMode.AUTO, VlcOptimizationMode.fromStorage("unknown"))
    }

    @Test fun automaticReservesMoreNetworkBufferForUhd() {
        assertEquals(5_000, profile().networkMs)
        assertEquals(1_500, profile(width = 1920, height = 1080).networkMs)
    }

    @Test fun letterboxedAndPortraitUhdAlsoReceiveBuffer() {
        assertEquals(5_000, profile(height = 1600).networkMs)
        assertEquals(5_000, profile(width = 1600, height = 3840).networkMs)
        assertFalse(isVlcUhdVideo(1080, 1920))
    }

    @Test fun highFrameRateHdAlsoReceivesBuffer() {
        assertEquals(5_000, profile(width = 1920, height = 1080, fps = 59.94).networkMs)
        assertEquals(5_000, profile(width = 1920, height = 1080, fps = 50.0).networkMs)
    }

    @Test fun invalidFrameRatesAndUnknownDimensionsHaveSafeDefaults() {
        for (fps in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -60.0)) {
            assertEquals(1_500, profile(width = null, height = null, fps = fps).networkMs)
        }
    }

    @Test fun stabilityHasLongerBufferThanAutomatic() {
        assertEquals(8_000, profile(mode = VlcOptimizationMode.STABILITY).networkMs)
        assertEquals(3_000, profile(mode = VlcOptimizationMode.STABILITY, width = 1920, height = 1080).networkMs)
    }

    @Test fun lowMemoryCapsNetworkTimeInEveryMode() {
        for (mode in VlcOptimizationMode.entries) {
            val result = profile(mode, lowMemory = true)
            assertTrue(result.networkMs in 800..3_000)
        }
    }

    @Test fun quickStartDoesNotClaimToBeBestForUhd() {
        assertEquals(1_500, profile(mode = VlcOptimizationMode.PERFORMANCE).networkMs)
        assertEquals(800, profile(mode = VlcOptimizationMode.PERFORMANCE, width = 1920, height = 1080).networkMs)
    }

    @Test fun localFilesDoNotReceiveLongNetworkDelay() {
        assertEquals(1_000, profile().fileMs)
        assertEquals(1_500, profile(mode = VlcOptimizationMode.STABILITY).fileMs)
        assertEquals(3_000, profile(mode = VlcOptimizationMode.BALANCED).networkMs)
    }

    private fun profile(
        mode: VlcOptimizationMode = VlcOptimizationMode.AUTO,
        width: Int? = 3840,
        height: Int? = 2160,
        fps: Double? = 24.0,
        lowMemory: Boolean = false,
    ) = vlcBufferProfile(mode, width, height, fps, lowMemory)
}
