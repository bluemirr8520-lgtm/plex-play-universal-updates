package io.mirr.plexplay.ui

import android.view.View
import androidx.media3.common.Player
import androidx.media3.ui.PlayerControlView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic control contract only: no VLC decoder, network, or Plex watched history. */
@RunWith(AndroidJUnit4::class)
class VlcTransportPlayerTest {
    @Test fun media3TransportControlsVlcCallbacksWithoutAnotherDecoder() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var playing = false
            var seek = -1L
            var previous = 0
            var next = 0
            val adapter = VlcTransportPlayer({ playing = it }, { seek = it }, { previous++ }, { next++ })
            adapter.update(TransportSnapshot(20_000, 100_000, true, false, true, true))
            assertEquals(100_000L, adapter.duration)
            assertEquals(20_000L, adapter.currentPosition)
            adapter.pause()
            assertFalse(playing)
            adapter.play()
            assertTrue(playing)
            adapter.seekForward()
            assertEquals(30_000L, seek)
            val controls = PlayerControlView(InstrumentationRegistry.getInstrumentation().targetContext)
            controls.player = adapter
            configureEpisodeNavigationButtons(controls, true, true, { previous++ }, { next++ })
            controls.findViewById<View>(androidx.media3.ui.R.id.exo_prev).performClick()
            controls.findViewById<View>(androidx.media3.ui.R.id.exo_next).performClick()
            assertEquals(1, previous)
            assertEquals(1, next)
            controls.player = null
            adapter.release()
        }
    }

    @Test fun missingNeighborIsDisabledAndClockRefreshDoesNotReplaceTimeline() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val adapter = VlcTransportPlayer({}, {}, {}, {})
            adapter.update(TransportSnapshot(1_000, 100_000, true, false, false, false))
            assertFalse(adapter.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
            val timeline = adapter.currentTimeline
            adapter.update(TransportSnapshot(2_000, 100_000, true, false, false, false))
            assertSame(timeline, adapter.currentTimeline)
            assertEquals(2_000L, adapter.currentPosition)
            adapter.release()
        }
    }
}
