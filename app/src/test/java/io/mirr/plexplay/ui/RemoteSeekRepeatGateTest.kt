package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSeekRepeatGateTest {
    @Test fun initialPressWorksAndRepeatsAreLimitedToHalfASecond() {
        val gate = RemoteSeekRepeatGate()
        assertTrue(gate.acceptDown(22, 100, 100, 0))
        assertFalse(gate.acceptDown(22, 100, 599, 1))
        assertTrue(gate.acceptDown(22, 100, 600, 2))
        assertFalse(gate.acceptDown(22, 100, 601, 3))
        assertTrue(gate.acceptDown(22, 100, 1100, 4))
    }

    @Test fun fiftyRepeatsPerSecondCannotCauseHugeJumps() {
        val gate = RemoteSeekRepeatGate()
        val seek = RemoteSeekState()
        gate.acceptDown(22, 0, 0, 0)
        (1..250).forEach { repeat ->
            if (gate.acceptDown(22, 0, repeat * 20L, repeat)) {
                seek.step(RemoteSeekStepMs, 100_000, 7_200_000)
            } else seek.keepAlive()
        }
        assertEquals(200_000L, seek.finish(7_200_000))
    }

    @Test fun delayedBatchNeverCatchesUpAllMissingRepeats() {
        val gate = RemoteSeekRepeatGate()
        gate.acceptDown(22, 0, 0, 0)
        assertTrue(gate.acceptDown(22, 0, 5000, 100))
        (101..150).forEach { assertFalse(gate.acceptDown(22, 0, 5000, it)) }
    }

    @Test fun queuedOldEventsAfterAStallCannotJumpMinutesAtOnce() {
        val gate = RemoteSeekRepeatGate()
        gate.acceptDown(22, 0, 0, 0, nowMs = 0)
        // Five seconds of queued input arrive together after a UI stall.
        assertTrue(gate.acceptDown(22, 0, 500, 1, nowMs = 5000))
        (2..10).forEach {
            assertFalse(gate.acceptDown(22, 0, it * 500L, it, nowMs = 5000L + it))
        }
        assertTrue(gate.acceptDown(22, 0, 5500, 11, nowMs = 5500))
    }

    @Test fun duplicateObservationDoesNotChangeNativePassThrough() {
        val gate = RemoteSeekRepeatGate()
        repeat(2) { assertTrue(gate.acceptDown(22, 0, 0, 0)) }
        repeat(2) { assertFalse(gate.acceptDown(22, 0, 20, 1)) }
        repeat(2) { assertTrue(gate.acceptDown(22, 0, 500, 2)) }
        assertFalse(gate.acceptDown(22, 0, 520, 3))
    }

    @Test fun quickShortTapsAreNotThrottled() {
        val gate = RemoteSeekRepeatGate()
        (0L..10L).forEach {
            assertTrue(gate.acceptDown(22, it, it, 0))
            gate.release(22, it)
        }
    }

    @Test fun changingDirectionStartsAFreshPress() {
        val gate = RemoteSeekRepeatGate()
        assertTrue(gate.acceptDown(22, 100, 100, 0))
        assertTrue(gate.acceptDown(21, 110, 110, 0))
        gate.release(22, 100)
        assertTrue(gate.acceptDown(21, 110, 610, 1))
    }

    @Test fun orphanRepeatsAreIgnoredAfterReleaseOrNewPlayer() {
        val gate = RemoteSeekRepeatGate()
        assertFalse(gate.acceptDown(22, 0, 500, 1))
        gate.acceptDown(22, 600, 600, 0)
        gate.release(22, 600)
        assertFalse(gate.acceptDown(22, 600, 1100, 2))
    }

    @Test fun canceledInputCannotResumeSeeking() {
        val gate = RemoteSeekRepeatGate()
        gate.acceptDown(22, 0, 0, 0)
        gate.reset()
        assertFalse(gate.acceptDown(22, 0, 500, 1))
    }

    @Test fun staleTimestampsAndOtherPressRepeatsAreIgnored() {
        val gate = RemoteSeekRepeatGate()
        gate.acceptDown(22, 1000, 1000, 0)
        assertFalse(gate.acceptDown(22, 1000, 999, 1))
        assertFalse(gate.acceptDown(22, 0, 1500, 2))
        assertFalse(gate.acceptDown(21, 1000, 1500, 2))
        assertTrue(gate.acceptDown(22, 1000, 1500, 3))
    }

    @Test fun filteredRepeatsRenewIdleWithoutAddingDistance() {
        val seek = RemoteSeekState()
        seek.step(RemoteSeekStepMs, 100_000, 7_200_000)
        val revision = seek.revision
        seek.keepAlive()
        assertEquals(revision + 1, seek.revision)
        assertEquals(110_000L, seek.previewMs)
        assertTrue(RemoteSeekIdleMs > RemoteSeekRepeatMs)
    }

    @Test fun idleRenewalCannotCreateASeekAfterCancel() {
        val seek = RemoteSeekState()
        val revision = seek.revision
        seek.keepAlive()
        assertEquals(revision, seek.revision)
        assertEquals(null, seek.previewMs)
    }
}
