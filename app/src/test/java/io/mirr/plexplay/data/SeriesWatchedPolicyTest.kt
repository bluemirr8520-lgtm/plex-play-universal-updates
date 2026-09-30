package io.mirr.plexplay.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SeriesWatchedPolicyTest {
    private fun series(type: String = "show", viewed: Int = 2, total: Int = 2, views: Int = 0) =
        PlexItem("10", "/library/metadata/10", type, "Series", librarySectionId = "7",
            leafCount = total, viewedLeafCount = viewed, viewCount = views)

    private fun episode(id: String, path: String = "/TV/Series/$id.mkv") =
        PlexItem(id, "/library/metadata/$id", "episode", "Episode", librarySectionId = "7",
            grandparentRatingKey = "10", mediaFilePaths = listOf(path), viewCount = 1)

    @Test fun completedSeriesAndSeasonUseEpisodeCountersAfterReload() {
        for (type in listOf("show", "season")) {
            assertTrue(series(type).isWatched)
            assertTrue(series(type).matchesSavedWatchedState(true))
            assertFalse(series(type).matchesSavedWatchedState(false))
            assertEquals(0, series(type).unwatchedEpisodeCount)
        }
    }

    @Test fun incompleteAndEmptyContainersDoNotUseSyntheticViewCountOrProgress() {
        for (type in listOf("show", "season")) {
            val partial = series(type, viewed = 1, views = 1).copy(durationMs = 100, viewOffsetMs = 100)
            assertFalse(partial.isWatched)
            assertFalse(partial.matchesSavedWatchedState(true))
            assertEquals(1, partial.unwatchedEpisodeCount)
            assertFalse(series(type, viewed = 0, total = 0, views = 1).isWatched)
            assertFalse(series(type, viewed = 0, total = 0).matchesSavedWatchedState(true))
        }
    }

    @Test fun markingSeriesUnwatchedRequiresAllEpisodeCountersCleared() {
        assertTrue(series(viewed = 0).matchesSavedWatchedState(false))
        assertFalse(series(viewed = 1).matchesSavedWatchedState(false))
    }

    @Test fun individualVideoDisplayAndPersistedVerificationRemainDistinct() {
        val movie = episode("20").copy(type = "movie", viewCount = 0, durationMs = 100, viewOffsetMs = 95)
        assertTrue(movie.isWatched)
        assertFalse(movie.matchesSavedWatchedState(true))
        assertFalse(movie.matchesSavedWatchedState(false))
        assertTrue(movie.copy(viewOffsetMs = 0).matchesSavedWatchedState(false))
        assertTrue(movie.copy(viewCount = 1).matchesSavedWatchedState(true))
    }

    @Test fun ordinarySeriesGetsKillAndSpecialSeriesGets123() {
        val roots = listOf("/TV/Series" to "KILL", "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META" to "123",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Uncensored/NO_META" to "123",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Western/NO_META" to "123",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/기타" to "123")
        for ((root, tag) in roots) {
            assertEquals(tag, seriesWatchedCollectionTag("VOD 드라마", series(),
                listOf(episode("20", "$root/Season 1/20.mkv"), episode("21", "$root/Season 2/21.mkv"))))
        }
    }

    @Test fun missingMixedOrUnrelatedEpisodePathsNeverReplaceSeriesCollections() {
        val a = episode("20")
        val b = episode("21")
        val invalidLists = listOf(emptyList(), listOf(a), listOf(a, a),
            listOf(a, b.copy(librarySectionId = "8")), listOf(a, b.copy(grandparentRatingKey = "11")),
            listOf(a, b.copy(type = "movie")), listOf(a, b.copy(mediaFilePaths = emptyList())),
            listOf(a, b.copy(viewCount = 0)),
            listOf(a, b.copy(mediaFilePaths = listOf(""))),
            listOf(a, b.copy(mediaFilePaths = listOf("/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META/b.mkv"))))
        for (episodes in invalidLists) assertNull(seriesWatchedCollectionTag("TV", series(), episodes))
        assertNull(seriesWatchedCollectionTag("TV", series(viewed = 1), listOf(a, b)))
        assertNull(seriesWatchedCollectionTag("TV", series(type = "season"), listOf(a, b)))
        assertNull(seriesWatchedCollectionTag("", series(), listOf(a, b)))
    }

    @Test fun everyFileVersionMustAgreeForSeriesToo() {
        val versions = episode("20").copy(mediaFilePaths = listOf("/TV/a.mkv", "/TV/a.mp4"))
        assertEquals("KILL", seriesWatchedCollectionTag("TV", series(), listOf(versions, episode("21"))))
        assertNull(seriesWatchedCollectionTag("TV", series(), listOf(versions.copy(
            mediaFilePaths = versions.mediaFilePaths + ""), episode("21"))))
    }

    @Test fun seriesCompletionUsesSavedNamesAndDifferentMountPrefixes() {
        val settings = WatchedCollectionSettings("본 시리즈", "예외 보관")
        assertEquals("본 시리즈", seriesWatchedCollectionTag("TV", series(), listOf(episode("20"), episode("21")), settings))
        val episodes = listOf(episode("20", "/data/GDRIVE/VIDEO/AV/자막B/New/Group/NO_META/20.mkv"),
            episode("21", "/mnt/GDS9/GDRIVE/VIDEO/AV/자막B/Another/NO_META/Season/21.mkv"))
        assertEquals("예외 보관", seriesWatchedCollectionTag("TV", series(), episodes, settings))
    }

    @Test fun paginationReadsBeyondShortPagesUntilAnEmptyPage() = runBlocking {
        val starts = mutableListOf<Int>()
        val result = loadAllSeriesEpisodes { start ->
            starts += start
            when (start) { 0 -> listOf(episode("20")); 1 -> listOf(episode("21")); else -> emptyList() }
        }
        assertEquals(listOf(0, 1, 2), starts)
        assertEquals(listOf("20", "21"), result.map { it.ratingKey })
    }

    @Test fun paginationRefusesRepeatedPagesInsteadOfLoopingOrTaggingPartialResults() = runBlocking {
        var calls = 0
        try {
            loadAllSeriesEpisodes { calls++; listOf(episode("20")) }
            fail("Repeated page must fail")
        } catch (_: PlexException) { assertEquals(2, calls) }
    }
}
