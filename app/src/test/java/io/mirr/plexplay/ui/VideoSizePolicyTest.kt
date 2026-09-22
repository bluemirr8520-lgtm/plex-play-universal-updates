package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSizePolicyTest {
    @Test fun zoomStepsUpAndDown() {
        assertEquals("zoom", steppedVideoSize("fit", 1))
        assertEquals("zoom_large", steppedVideoSize("zoom", 1))
        assertEquals("fit", steppedVideoSize("zoom", -1))
        assertEquals("small", steppedVideoSize("fit", -1))
    }
    @Test fun limitsDoNotWrapAround() {
        assertEquals("small", steppedVideoSize("small", -1))
        assertEquals("zoom_large", steppedVideoSize("zoom_large", 1))
    }
    @Test fun fillAndStretchReturnToPredictableZoomSteps() {
        for (mode in listOf("fill", "stretch", "unknown")) {
            assertEquals("small", steppedVideoSize(mode, -1))
            assertEquals("zoom", steppedVideoSize(mode, 1))
        }
    }
}
