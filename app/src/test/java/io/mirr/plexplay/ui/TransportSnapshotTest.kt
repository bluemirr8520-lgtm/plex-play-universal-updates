package io.mirr.plexplay.ui

import org.junit.Assert.*
import org.junit.Test

class TransportSnapshotTest {
    @Test fun playingControlsCanAutoHide() {
        assertTrue(shouldAutoHideTransport(true, false, true, false))
    }

    @Test fun scrubbingPauseAndSettingsPreventAutoHide() {
        assertFalse(shouldAutoHideTransport(true, false, true, true))
        assertFalse(shouldAutoHideTransport(true, false, false, false))
        assertFalse(shouldAutoHideTransport(true, true, true, false))
        assertFalse(shouldAutoHideTransport(false, false, true, false))
    }

    @Test fun seeksStayInsideMediaDuration() {
        assertEquals(0L, transportSeekPosition(-1_000, 30_000))
        assertEquals(30_000L, transportSeekPosition(50_000, 30_000))
        assertEquals(12_000L, transportSeekPosition(12_000, 30_000))
    }

    @Test fun unknownDurationDoesNotSeekToNegativeOrUnsetTime() {
        assertEquals(0L, transportSeekPosition(Long.MIN_VALUE + 1, -1))
        assertEquals(0L, transportSeekPosition(12_000, 0))
    }

    @Test fun clockOnlyChangesCanReuseNativeTimeline() {
        val first = TransportSnapshot(1_000, 30_000, true, false, true, true)
        val tick = first.copy(positionMs = 1_500)
        assertEquals(first, tick.copy(positionMs = first.positionMs))
        assertNotEquals(first, tick.copy(positionMs = first.positionMs, hasNext = false))
    }
}
