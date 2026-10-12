package io.mirr.plexplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBackPressTest {
    @Test fun aPressNavigatesExactlyOnceOnRelease() {
        val key = RemoteBackPress()
        key.down(100, 0)
        assertTrue(key.up(100, false))
        assertFalse(key.up(100, false))
    }

    @Test fun holdingBackDoesNotUnwindMultipleScreens() {
        val key = RemoteBackPress()
        key.down(100, 0)
        (1..100).forEach { key.down(100, it) }
        assertTrue(key.up(100, false))
        assertFalse(key.up(100, false))
    }

    @Test fun orphanRepeatAndReleaseCannotCloseANewScreen() {
        val key = RemoteBackPress()
        key.down(100, 10)
        assertFalse(key.up(100, false))
    }

    @Test fun cancellationDiscardsThePress() {
        val key = RemoteBackPress()
        key.down(100, 0)
        assertFalse(key.up(100, true))
        assertFalse(key.up(100, false))
    }

    @Test fun lostWindowFocusDiscardsThePress() {
        val key = RemoteBackPress()
        key.down(100, 0)
        key.reset()
        assertFalse(key.up(100, false))
    }

    @Test fun differentPressReleaseCannotNavigate() {
        val key = RemoteBackPress()
        key.down(100, 0)
        assertFalse(key.up(200, false))
    }

    @Test fun separateQuickPressesStillWork() {
        val key = RemoteBackPress()
        (1L..4L).forEach {
            key.down(it, 0)
            assertTrue(key.up(it, false))
        }
    }
}
