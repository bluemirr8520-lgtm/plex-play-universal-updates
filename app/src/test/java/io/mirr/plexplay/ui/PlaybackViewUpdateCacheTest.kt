package io.mirr.plexplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackViewUpdateCacheTest {
    @Test fun openingAndClosingSettingsDoesNotReapplyUnchangedVideoValues() {
        val cache = PlaybackViewUpdateCache()
        val surface = Any()
        var updates = 0
        assertTrue(cache.apply(surface, "fit") { updates++ })
        repeat(20) { assertFalse(cache.apply(surface, "fit") { updates++ }) }
        assertEquals(1, updates)
    }

    @Test fun actualSettingChangesStillApplyImmediatelyIncludingReset() {
        val cache = PlaybackViewUpdateCache()
        val surface = Any()
        val applied = mutableListOf<Int>()
        listOf(0, 5, 10, 0).forEach { value ->
            assertTrue(cache.apply(surface, value) { applied += value })
        }
        assertEquals(listOf(0, 5, 10, 0), applied)
    }

    @Test fun replacementSurfaceReceivesExistingSettings() {
        val cache = PlaybackViewUpdateCache()
        var updates = 0
        repeat(2) { assertTrue(cache.apply(Any(), "zoom") { updates++ }) }
        assertEquals(2, updates)
    }

    @Test fun targetIdentityIsNotValueEquality() {
        data class Surface(val id: Int)
        val cache = PlaybackViewUpdateCache()
        assertTrue(cache.apply(Surface(1), 0) {})
        assertTrue(cache.apply(Surface(1), 0) {})
    }

    @Test fun failedApplicationCanBeRetried() {
        val cache = PlaybackViewUpdateCache()
        val surface = Any()
        runCatching { cache.apply(surface, 25) { error("surface not ready") } }
        assertTrue(cache.apply(surface, 25) {})
        assertFalse(cache.apply(surface, 25) {})
    }

    @Test fun scaleAndColorCachesDoNotInterfere() {
        val scale = PlaybackViewUpdateCache()
        val color = PlaybackViewUpdateCache()
        val surface = Any()
        assertTrue(scale.apply(surface, 0) {})
        assertTrue(color.apply(surface, 0) {})
        assertTrue(color.apply(surface, 20) {})
        assertFalse(scale.apply(surface, 0) {})
    }

    @Test fun resetRestoresOldValueAfterAPartiallyFailedChange() {
        val cache = PlaybackViewUpdateCache()
        val surface = Any()
        var actual = 0
        cache.apply(surface, 0) { actual = 0 }
        runCatching {
            cache.apply(surface, 10) {
                actual = 10
                error("partial surface update")
            }
        }
        assertTrue(cache.apply(surface, 0) { actual = 0 })
        assertEquals(0, actual)
    }
}
