package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCleanupTest {
    @Test fun cleanupKeepsTheRequiredOrder() {
        val calls = mutableListOf<String>()
        cleanupPlayback({ _, _ -> error("Unexpected failure") },
            "detach" to { calls += "detach" }, "release" to { calls += "release" },
            "window" to { calls += "window" })
        assertEquals(listOf("detach", "release", "window"), calls)
    }

    @Test fun progressFailureDoesNotLeakTheDecoder() {
        val calls = mutableListOf<String>()
        cleanupPlayback({ name, _ -> calls += "failed:$name" },
            "progress" to { throw IllegalStateException("Synthetic test") },
            "release" to { calls += "released" })
        assertEquals(listOf("failed:progress", "released"), calls)
    }

    @Test fun decoderFailureStillRestoresTheWindow() {
        val calls = mutableListOf<String>()
        cleanupPlayback({ name, _ -> calls += "failed:$name" },
            "release" to { throw IllegalStateException("Synthetic test") },
            "brightness" to { calls += "brightness" }, "orientation" to { calls += "orientation" })
        assertEquals(listOf("failed:release", "brightness", "orientation"), calls)
    }

    @Test fun multipleDeviceFailuresAreIsolated() {
        val calls = mutableListOf<String>()
        cleanupPlayback({ name, _ -> calls += name },
            "brightness" to { throw IllegalArgumentException() },
            "bars" to { throw IllegalStateException() },
            "orientation" to { calls += "restored" })
        assertEquals(listOf("brightness", "bars", "restored"), calls)
    }
}
