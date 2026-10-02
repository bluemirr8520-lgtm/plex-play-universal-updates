package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameRatePolicyTest {
    @Test fun fractionalMovieAndTelevisionRatesAreNotRounded() {
        assertEquals((24000.0 / 1001.0).toFloat(), playbackFrameRate(24000.0 / 1001.0, 1f, true), 0f)
        assertEquals(59.94f, playbackFrameRate(59.94, 1f, true), 0f)
    }

    @Test fun displayHintFollowsPlaybackSpeed() {
        assertEquals(48f, playbackFrameRate(24.0, 2f, true), 0f)
        assertEquals(12f, playbackFrameRate(24.0, .5f, true), 0f)
    }

    @Test fun pauseClearsTheHintRegardlessOfSourceRate() {
        assertEquals(0f, playbackFrameRate(60.0, 1f, false), 0f)
    }

    @Test fun unknownOrInvalidMetadataDoesNotRequestARefreshRate() {
        for (rate in listOf(null, 0.0, -24.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertEquals(0f, playbackFrameRate(rate, 1f, true), 0f)
        }
    }

    @Test fun invalidSpeedsAndOverflowDoNotReachThePlatformApi() {
        for (speed in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0f, playbackFrameRate(24.0, speed, true), 0f)
        }
        assertEquals(0f, playbackFrameRate(240.0, Float.MAX_VALUE, true), 0f)
    }
}
