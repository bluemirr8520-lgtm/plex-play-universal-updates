package io.mirr.plexplay.ui

import org.junit.Assert.*
import org.junit.Test

class SubtitleDragStateTest {
    @Test fun everyMoveUpdatesPreviewBeforeFingerIsReleased() {
        val state = SubtitleDragState()
        state.start(0, 0)
        assertNull(state.preview)
        state.move(100f, 50f, 1000, 500)
        assertEquals(SubtitleDragPosition(10, 10), state.preview)
        state.move(200f, 125f, 1000, 500)
        assertEquals(SubtitleDragPosition(20, 25), state.preview)
        state.move(-50f, -25f, 1000, 500)
        assertEquals(SubtitleDragPosition(-5, -5), state.preview)
    }

    @Test fun finishReturnsLastVisiblePositionOnceAndClearsPreview() {
        val state = SubtitleDragState()
        state.start(20, -30)
        state.move(100f, 100f, 1000, 500)
        assertEquals(SubtitleDragPosition(30, -10), state.finish())
        assertNull(state.preview)
        assertNull(state.finish())
        assertNull(state.move(200f, 200f, 1000, 500))
    }

    @Test fun cancellationRestoresSavedPositionWithoutACommit() {
        val state = SubtitleDragState()
        state.start(20, -30)
        state.move(200f, 200f, 1000, 500)
        state.cancel()
        assertNull(state.preview)
        assertNull(state.finish())
    }

    @Test fun nextDragStartsAtNewSavedPositionWithoutOldDeltas() {
        val state = SubtitleDragState()
        state.start(0, 0)
        state.move(100f, 100f, 1000, 500)
        val saved = requireNotNull(state.finish())
        state.start(saved.horizontal, saved.vertical)
        assertNull(state.preview)
        state.move(50f, -50f, 1000, 500)
        assertEquals(SubtitleDragPosition(15, 10), state.preview)
    }

    @Test fun newGestureDiscardsUnfinishedPreview() {
        val state = SubtitleDragState()
        state.start(0, 0)
        state.move(100f, 100f, 1000, 500)
        state.start(-10, 30)
        assertNull(state.preview)
        state.move(0f, 50f, 1000, 500)
        assertEquals(SubtitleDragPosition(-10, 40), state.preview)
    }

    @Test fun gestureWithoutSubtitleMovementHasNothingToSave() {
        val state = SubtitleDragState()
        assertNull(state.move(100f, 100f, 1000, 500))
        state.start(10, 20)
        assertNull(state.finish())
    }

    @Test fun offsetsRespectExistingRangeAndDoNotAccumulateRoundingDrift() {
        val state = SubtitleDragState()
        state.start(90, -90)
        state.move(500f, -500f, 1000, 500)
        assertEquals(SubtitleDragPosition(100, -100), state.preview)
        state.move(10f, 5f, 1000, 500)
        assertEquals(SubtitleDragPosition(91, -89), state.preview)
        state.move(10f, 5f, 1000, 500)
        assertEquals(SubtitleDragPosition(91, -89), state.preview)
    }

    @Test fun invalidViewportAndNonFiniteInputLeaveLastPreviewUnchanged() {
        val state = SubtitleDragState()
        state.start(0, 0)
        assertNull(state.move(100f, 50f, 0, 500))
        state.move(100f, 50f, 1000, 500)
        val expected = state.preview
        state.move(200f, 50f, 1000, 0)
        state.move(Float.NaN, 50f, 1000, 500)
        state.move(200f, Float.POSITIVE_INFINITY, 1000, 500)
        assertEquals(expected, state.preview)
    }
}
