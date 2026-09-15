package io.mirr.plexplay.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcSubtitleOwnershipTest {
    @Test
    fun externalTextIsReservedBeforeDownloadFinishes() {
        assertTrue(owns(textSelected = true, externalTextSelected = true))
    }

    @Test
    fun failedExternalTextNeverSilentlyChangesToNativeFont() {
        assertTrue(
            owns(textSelected = true, externalTextSelected = true, directLoadFailed = true),
        )
    }

    @Test
    fun externalTextRemainsOwnedWhileCompanionLoads() {
        assertTrue(
            owns(
                textSelected = true, externalTextSelected = true, directLoadFailed = true,
                bridgeEnabled = true, bridgePreparing = true,
            ),
        )
    }

    @Test
    fun offWinsOverLoadedDirectAndCompanionText() {
        assertFalse(
            owns(
                disabled = true, textSelected = true, externalTextSelected = true,
                directOverlayActive = true, bridgeEnabled = true, bridgeActive = true,
            ),
        )
    }

    @Test
    fun embeddedExtractionAlsoReservesSelectionWhilePending() {
        assertTrue(owns(textSelected = true))
    }

    @Test
    fun failedEmbeddedExtractionCanUseNativeFallback() {
        assertFalse(owns(textSelected = true, directLoadFailed = true))
    }

    @Test
    fun successfulDirectTextSuppressesNativeRenderer() {
        assertTrue(owns(directOverlayActive = true))
    }

    @Test
    fun companionPreparationSuppressesNativeRenderer() {
        assertTrue(owns(bridgeEnabled = true, bridgePreparing = true))
    }

    @Test
    fun companionTextSuppressesNativeRenderer() {
        assertTrue(owns(bridgeEnabled = true, bridgeActive = true))
    }

    @Test
    fun bitmapCompanionLeavesNativeRendererAvailable() {
        assertFalse(
            owns(bridgeEnabled = true, bridgeActive = true, bridgeBitmapOnly = true),
        )
    }

    @Test
    fun disabledCompanionCannotKeepStaleOwnership() {
        assertFalse(owns(bridgeActive = true, bridgePreparing = true))
    }

    private fun owns(
        disabled: Boolean = false,
        textSelected: Boolean = false,
        externalTextSelected: Boolean = false,
        directLoadFailed: Boolean = false,
        directOverlayActive: Boolean = false,
        bridgeEnabled: Boolean = false,
        bridgeActive: Boolean = false,
        bridgePreparing: Boolean = false,
        bridgeBitmapOnly: Boolean = false,
    ): Boolean = appOwnsVlcSubtitleSelection(
        disabled, textSelected, externalTextSelected, directLoadFailed, directOverlayActive,
        bridgeEnabled, bridgeActive, bridgePreparing, bridgeBitmapOnly,
    )
}
