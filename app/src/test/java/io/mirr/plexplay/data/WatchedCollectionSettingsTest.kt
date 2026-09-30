package io.mirr.plexplay.data

import org.junit.Assert.*
import org.junit.Test

class WatchedCollectionSettingsTest {
    private val specialPath = "/new-mount/GDRIVE/VIDEO/AV/자막B/NO_META/a.mkv"

    @Test fun absentPreferencesKeepPreviousDefaultNames() {
        assertEquals(WatchedCollectionSettings("KILL", "123"), readWatchedCollectionSettings { null })
    }

    @Test fun bothSavedNamesAreRestoredTogether() {
        val preferences = mapOf(DefaultCollectionNameKey to "시청 완료", SpecialCollectionNameKey to "보관, A+B & C")
        assertEquals(WatchedCollectionSettings("시청 완료", "보관, A+B & C"), readWatchedCollectionSettings { preferences[it] })
    }

    @Test fun invalidStoredValueOnlyFallsBackForThatName() {
        val preferences = mapOf(DefaultCollectionNameKey to " ", SpecialCollectionNameKey to "내 예외 목록")
        assertEquals(WatchedCollectionSettings("KILL", "내 예외 목록"), readWatchedCollectionSettings { preferences[it] })
        assertEquals(WatchedCollectionSettings("KILL", "123"), readWatchedCollectionSettings { "bad\nname" })
    }

    @Test fun namesTrimSurroundingSpacesButKeepUnicodeAndPunctuation() {
        assertEquals(WatchedCollectionSettings("완료 🎬", "보관, A+B & C"),
            WatchedCollectionSettings("  완료 🎬  ", " 보관, A+B & C ").normalizedOrNull())
        assertNotNull(WatchedCollectionSettings("🎬".repeat(100), "123").normalizedOrNull())
        assertNull(WatchedCollectionSettings("🎬".repeat(101), "123").normalizedOrNull())
    }

    @Test fun emptyOverlongAndControlNamesCannotBeSavedOrUsedForWrites() {
        for (invalid in listOf("", "  ", "a".repeat(101), "a\nb", "a\rb", "a\tb", "a\u0000b", "a\u007fb")) {
            for (settings in listOf(WatchedCollectionSettings(invalid, "123"), WatchedCollectionSettings("KILL", invalid))) {
                assertNull(settings.normalizedOrNull())
                assertNull(watchedCollectionTag("Movies", "movie", "/video/a.mkv", settings))
                assertNull(watchedCollectionTag("Movies", "movie", specialPath, settings))
            }
        }
    }

    @Test fun automaticAndManualCompletionUseCustomNames() {
        val settings = WatchedCollectionSettings("본 영상", "따로 보관")
        for ((path, name) in listOf("/video/a.mkv" to "본 영상", specialPath to "따로 보관")) {
            assertEquals(name, watchedCollectionTag("TV", "episode", path, settings))
            assertEquals(name, manualWatchedCollectionTag("TV", "episode", listOf(path), settings))
        }
        assertNull(manualWatchedCollectionTag("TV", "episode", listOf("/video/a.mkv", specialPath), settings))
    }

    @Test fun bothRulesMayIntentionallyUseTheSameNameButMissingPathsStillFail() {
        val settings = WatchedCollectionSettings("완료", "완료")
        assertEquals("완료", manualWatchedCollectionTag("Movies", "movie", listOf("/video/a.mkv", specialPath), settings))
        assertNull(manualWatchedCollectionTag("Movies", "movie", listOf("", specialPath), settings))
    }
}
