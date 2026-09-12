package io.mirr.plexplay.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompletionTest {
    @Test
    fun engineFallbackCopyBelongsToTheSamePlayback() {
        val source = source()
        assertTrue(matchesPlaybackCompletion(source, source.copy(resumePositionMs = 500)))
    }

    @Test
    fun oldEndEventCannotCompleteAnotherVideoOrReplay() {
        val source = source()
        assertFalse(matchesPlaybackCompletion(null, source))
        assertFalse(matchesPlaybackCompletion(source(), source))
        assertFalse(matchesPlaybackCompletion(source.copy(ratingKey = "2"), source))
        assertFalse(matchesPlaybackCompletion(source.copy(url = "https://example.invalid/other"), source))
        assertFalse(matchesPlaybackCompletion(source.copy(token = "other-test-token"), source))
    }

    @Test
    fun playbackWritesCannotFollowAnAccountOrServerSwitch() {
        val source = source().copy(serverBaseUrl = "https://example.invalid")
        val connection = PlexConnection(source.serverBaseUrl!!, source.token)
        assertEquals(connection, validatedPlaybackConnection(source, connection))
        assertNull(validatedPlaybackConnection(source, connection.copy(baseUrl = "https://other.invalid")))
        assertNull(validatedPlaybackConnection(source, connection.copy(token = "other-test-token")))
        assertNull(validatedPlaybackConnection(source, PlexConnection()))
        assertNull(validatedPlaybackConnection(source.copy(serverBaseUrl = null), connection))
    }

    private fun source() = PlaybackSource(
        url = "https://example.invalid/video",
        token = "test-token",
        title = "Video",
        subtitle = null,
        ratingKey = "1",
        durationMs = 1000,
        resumePositionMs = 0,
    )
}
