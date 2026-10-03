package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteSeekStateTest {
    @Test fun repeatedInputAccumulatesInsteadOfRestartingFromTheDecoderClock() {
        val state = RemoteSeekState()
        assertEquals(110_000L, state.step(10_000L, 100_000L, 600_000L))
        assertEquals(120_000L, state.step(10_000L, 100_020L, 600_000L))
        assertEquals(130_000L, state.step(10_000L, 100_050L, 600_000L))
    }

    @Test fun aSingleStepCanBeCommittedImmediately() {
        val state = RemoteSeekState()
        state.step(-10_000L, 100_000L, 600_000L)
        assertEquals(90_000L, state.finish(600_000L))
        assertNull(state.previewMs)
    }

    @Test fun manyRepeatsProduceOnlyOneFinalTarget() {
        val state = RemoteSeekState()
        repeat(20) { state.step(10_000L, 100_000L, 600_000L) }
        assertEquals(300_000L, state.finish(600_000L))
        assertNull(state.finish(600_000L))
    }

    @Test fun reversingDirectionUsesThePendingTarget() {
        val state = RemoteSeekState()
        state.step(10_000L, 100_000L, 600_000L)
        state.step(10_000L, 100_000L, 600_000L)
        assertEquals(110_000L, state.step(-10_000L, 100_000L, 600_000L))
    }

    @Test fun cancellationDiscardsThePreviewAndUsesAFreshClockNextTime() {
        val state = RemoteSeekState()
        state.step(10_000L, 100_000L, 600_000L)
        state.cancel()
        assertNull(state.finish(600_000L))
        assertEquals(210_000L, state.step(10_000L, 200_000L, 600_000L))
    }

    @Test fun targetsStayWithinTheVideoEvenWithOverflowSizedDeltas() {
        val state = RemoteSeekState()
        assertEquals(Long.MAX_VALUE, state.step(Long.MAX_VALUE, Long.MAX_VALUE - 10L, Long.MAX_VALUE))
        assertEquals(0L, state.step(Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0L, state.step(-10_000L, 0L, 600_000L))
    }

    @Test fun unknownOrInvalidDurationDoesNotKeepAnOldTarget() {
        val state = RemoteSeekState()
        state.step(10_000L, 100_000L, 600_000L)
        assertNull(state.step(10_000L, 100_000L, 0L))
        assertNull(state.finish(600_000L))
        assertNull(state.step(10_000L, 100_000L, -1L))
    }

    @Test fun theFinalTargetIsReclampedIfTheDurationChanges() {
        val state = RemoteSeekState()
        state.step(10_000L, 100_000L, 600_000L)
        assertEquals(80_000L, state.finish(80_000L))
        state.step(10_000L, 20_000L, 80_000L)
        assertNull(state.finish(0L))
        assertNull(state.previewMs)
    }

    @Test fun holdingAtTheBoundaryStillRenewsTheIdleTimer() {
        val state = RemoteSeekState()
        state.step(10_000L, 590_000L, 600_000L)
        val revision = state.revision
        state.step(10_000L, 590_000L, 600_000L)
        assertEquals(revision + 1, state.revision)
        assertEquals(600_000L, state.previewMs)
    }

    @Test fun pendingTargetsAreNotSharedAcrossPlayers() {
        val first = RemoteSeekState()
        first.step(10_000L, 100_000L, 600_000L)
        val replacement = RemoteSeekState()
        assertNull(replacement.finish(600_000L))
        assertEquals(60_000L, replacement.step(10_000L, 50_000L, 600_000L))
    }
}
