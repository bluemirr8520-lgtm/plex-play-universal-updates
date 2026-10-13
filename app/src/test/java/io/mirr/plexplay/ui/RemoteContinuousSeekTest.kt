package io.mirr.plexplay.ui

import org.junit.Assert.*
import org.junit.Test

class RemoteContinuousSeekTest {
    @Test fun holdAdvancesInSmallTimeBasedSteps() {
        val seek = RemoteSeekState()
        assertEquals(110_000L, seek.hold(10_000, 100_000, 600_000, 0))
        (1..20).forEach {
            val now = it * 50L
            seek.keepAlive(now)
            assertEquals(110_000L + it * 1000L, seek.advanceHold(now, 600_000))
        }
        assertEquals(130_000L, seek.finish(600_000))
        assertFalse(seek.isHolding)
    }

    @Test fun repeatPacketsDoNotAddTenSecondsEveryTime() {
        val seek = RemoteSeekState()
        seek.hold(10_000, 100_000, 600_000, 0)
        (1..20).forEach { seek.hold(10_000, 100_000, 600_000, it * 10L) }
        assertEquals(110_000L, seek.previewMs)
    }

    @Test fun fastAndSlowRemoteRepeatRatesProduceTheSameDistance() {
        fun target(signalInterval: Int): Long? {
            val seek = RemoteSeekState()
            seek.hold(10_000, 100_000, 600_000, 0)
            (1..100).forEach {
                val now = it * 50L
                if (it % signalInterval == 0) seek.keepAlive(now)
                seek.advanceHold(now, 600_000)
            }
            return seek.finish(600_000)
        }
        assertEquals(210_000L, target(1))
        assertEquals(target(1), target(5))
    }

    @Test fun aStalledFrameDoesNotCatchUpByJumpingMinutes() {
        val seek = RemoteSeekState()
        seek.hold(10_000, 100_000, 600_000, 0)
        seek.keepAlive(10_000)
        assertEquals(112_000L, seek.advanceHold(10_000, 600_000))
    }

    @Test fun missingKeyUpStopsAtTheLastPreviewAfterTimeout() {
        val seek = RemoteSeekState()
        seek.hold(10_000, 100_000, 600_000, 0)
        assertFalse(seek.holdExpired(599))
        assertTrue(seek.holdExpired(600))
        assertEquals(110_000L, seek.advanceHold(600, 600_000))
        assertEquals(110_000L, seek.finish(600_000))
        assertNull(seek.finish(600_000))
    }

    @Test fun reversingUsesThePreviewNotTheOldDecoderPosition() {
        val seek = RemoteSeekState()
        seek.hold(10_000, 100_000, 600_000, 0)
        seek.advanceHold(100, 600_000)
        assertEquals(102_000L, seek.hold(-10_000, 100_000, 600_000, 100))
        assertEquals(101_000L, seek.advanceHold(150, 600_000))
    }

    @Test fun bothVideoBoundariesRemainClamped() {
        val seek = RemoteSeekState()
        seek.hold(10_000, 595_000, 600_000, 0)
        assertEquals(600_000L, seek.advanceHold(50, 600_000))
        seek.cancel()
        seek.hold(-10_000, 5000, 600_000, 0)
        assertEquals(0L, seek.advanceHold(50, 600_000))
    }

    @Test fun canceledHoldCannotCommitOrResumeFromLaterTicks() {
        val seek = RemoteSeekState()
        seek.hold(10_000, 100_000, 600_000, 0)
        seek.cancel()
        seek.keepAlive(50)
        assertNull(seek.advanceHold(50, 600_000))
        assertNull(seek.finish(600_000))
    }

    @Test fun invalidDurationCancelsTheHold() {
        val seek = RemoteSeekState()
        assertNull(seek.hold(10_000, 100_000, 0, 0))
        assertFalse(seek.isHolding)
        seek.hold(10_000, 100_000, 600_000, 0)
        assertNull(seek.advanceHold(50, 0))
        assertFalse(seek.isHolding)
    }

    @Test fun repeatHeartbeatsDoNotTriggerRecompositionEachTime() {
        val seek = RemoteSeekState()
        seek.hold(10_000, 100_000, 600_000, 0)
        val revision = seek.revision
        (1L..100L).forEach { seek.keepAlive(it) }
        assertEquals(revision, seek.revision)
    }
}
