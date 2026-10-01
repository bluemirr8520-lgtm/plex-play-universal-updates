package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CompletedCollectionUpdaterTest {
    private fun show(viewed: Int = 2, total: Int = 2) = PlexItem("10", "/library/metadata/10", "show", "Series",
        librarySectionId = "7", leafCount = total, viewedLeafCount = viewed, collections = listOf("Old series"))
    private fun episode(id: String = "20", path: String = "/TV/Series/$id.mkv") = PlexItem(id,
        "/library/metadata/$id", "episode", "Episode", librarySectionId = "7", parentRatingKey = "11",
        grandparentRatingKey = "10", viewCount = 1, mediaFilePaths = listOf(path), collections = listOf("Old episode"))
    private fun season() = PlexItem("11", "/library/metadata/11", "season", "Season", librarySectionId = "7",
        parentRatingKey = "10", leafCount = 2, viewedLeafCount = 2)

    private inner class Fixture {
        var parent: PlexItem? = show()
        var leaves = listOf(episode(), episode("21"))
        var current = true
        var metadataReads = 0
        var episodeReads = 0
        val waits = mutableListOf<Int>()
        val writes = mutableListOf<CompletedCollectionChange>()
        var afterWait: (Int) -> Unit = {}
        var afterEpisodes: () -> Unit = {}
        var writeError: Exception? = null
        val updater = CompletedCollectionUpdater(
            metadata = { key -> assertEquals("10", key); metadataReads++; parent },
            sections = { listOf(PlexSection("7", "VOD 드라마", "show")) },
            episodes = { key -> assertEquals("10", key); episodeReads++; leaves.also { afterEpisodes() } },
            replace = { item, section, tag ->
                assertEquals("7", section.key)
                assertFalse(item.type in setOf("episode", "season"))
                writeError?.let { throw it }
                writes += CompletedCollectionChange(item, tag)
            },
            isCurrent = { current },
            waitForReadback = { attempt -> waits += attempt; afterWait(attempt) },
        )
        suspend fun update(item: PlexItem = episode(), settings: WatchedCollectionSettings = WatchedCollectionSettings(),
            played: String? = null, automatic: Boolean = false) = updater.update(item, settings, played, automatic)
    }

    @Test fun partialSeriesDoesNotWriteEpisodeOrShowCollections() = runBlocking {
        val f = Fixture().apply { parent = show(viewed = 1) }
        assertNull(f.update())
        assertTrue(f.writes.isEmpty())
        assertEquals(3, f.metadataReads)
        assertEquals(0, f.episodeReads)
        assertEquals(listOf(1, 2), f.waits)
    }

    @Test fun lastEpisodeAutomaticCompletionChangesOnlyItsShow() = runBlocking {
        val f = Fixture()
        val completed = episode()
        val change = f.update(completed, played = "/other/played.mkv", automatic = true)!!
        assertEquals("10", change.item.ratingKey)
        assertEquals("show", change.item.type)
        assertEquals("KILL", change.tag)
        assertEquals(listOf("KILL"), change.item.collections)
        assertEquals(listOf("Old episode"), completed.collections)
        assertEquals(listOf("10"), f.writes.map { it.item.ratingKey })
    }

    @Test fun manualEpisodeCompletionAlsoTargetsOnlyTheFullyWatchedShow() = runBlocking {
        val f = Fixture()
        assertEquals("show", f.update()!!.item.type)
        assertEquals(listOf("10"), f.writes.map { it.item.ratingKey })
    }

    @Test fun manualShowCompletionKeepsShowTarget() = runBlocking {
        val f = Fixture()
        assertEquals("10", f.update(show())!!.item.ratingKey)
        assertEquals(1, f.writes.size)
    }

    @Test fun seasonCompletionOnlyChangesShowWhenEverySeasonIsComplete() = runBlocking {
        val complete = Fixture()
        assertEquals("10", complete.update(season())!!.item.ratingKey)
        val partial = Fixture().apply { parent = show(viewed = 1) }
        assertNull(partial.update(season()))
        assertTrue(partial.writes.isEmpty())
    }

    @Test fun parentCountersMayLagWithoutRepeatingWatchedWrites() = runBlocking {
        val f = Fixture().apply {
            parent = show(viewed = 1)
            afterWait = { if (it == 2) parent = show() }
        }
        assertEquals("KILL", f.update()!!.tag)
        assertEquals(3, f.metadataReads)
        assertEquals(1, f.episodeReads)
        assertEquals(1, f.writes.size)
    }

    @Test fun leafReadbackMayLagBehindParentCounters() = runBlocking {
        val f = Fixture().apply {
            leaves = listOf(episode(), episode("21").copy(viewCount = 0))
            afterWait = { leaves = listOf(episode(), episode("21")) }
        }
        assertNotNull(f.update())
        assertEquals(2, f.episodeReads)
        assertEquals(1, f.writes.size)
    }

    @Test fun progressOnlyEpisodeDoesNotCountAsWatchedForSeries() = runBlocking {
        val f = Fixture().apply { leaves = listOf(episode(), episode("21").copy(viewCount = 0, durationMs = 100, viewOffsetMs = 99)) }
        expectFailure { f.update() }
        assertTrue(f.writes.isEmpty())
    }

    @Test fun missingOrInvalidParentIdentityNeverWritesAnEpisode() = runBlocking {
        for (key in listOf(null, "", "10,20", "../10")) {
            val f = Fixture()
            assertNull(f.update(episode().copy(grandparentRatingKey = key)))
            assertTrue(f.writes.isEmpty())
            assertEquals(0, f.metadataReads)
        }
    }

    @Test fun foreignOrNonShowMetadataCannotBeChanged() = runBlocking {
        for (parent in listOf(show().copy(ratingKey = "99"), show().copy(librarySectionId = "8"), show().copy(type = "season"))) {
            val f = Fixture().apply { this.parent = parent }
            expectFailure { f.update() }
            assertTrue(f.writes.isEmpty())
        }
    }

    @Test fun allEpisodeMembershipAndPathsMustBeVerified() = runBlocking {
        val badLists = listOf(
            listOf(episode()), listOf(episode(), episode()),
            listOf(episode(), episode("21").copy(grandparentRatingKey = "99")),
            listOf(episode(), episode("21").copy(librarySectionId = "8")),
            listOf(episode(), episode("21").copy(mediaFilePaths = emptyList())),
            listOf(episode(), episode("21", "/data/GDRIVE/VIDEO/AV/자막B/NO_META/b.mkv")),
            listOf(episode("22"), episode("23")),
        )
        for (leaves in badLists) {
            val f = Fixture().apply { this.leaves = leaves }
            expectFailure { f.update() }
            assertTrue(f.writes.isEmpty())
        }
    }

    @Test fun completedSeasonMustActuallyBelongToTheReturnedEpisodes() = runBlocking {
        val f = Fixture().apply { leaves = leaves.map { it.copy(parentRatingKey = "12") } }
        expectFailure { f.update(season()) }
        assertTrue(f.writes.isEmpty())
    }

    @Test fun specialRulesUseAllSeriesPathsAndUserDefinedNames() = runBlocking {
        val f = Fixture().apply {
            leaves = listOf(episode("20", "/mnt/GDS9/GDRIVE/VIDEO/AV/자막B/X/NO_META/a.mkv"),
                episode("21", "/other/GDRIVE/VIDEO/AV/자막B/기타/b.mkv"))
        }
        assertEquals("예외 보관", f.update(settings = WatchedCollectionSettings("본 시리즈", "예외 보관"),
            played = "/normal/only-the-last-file.mkv", automatic = true)!!.tag)
        assertEquals("show", f.writes.single().item.type)
    }

    @Test fun matchingCustomNamesAllowMixedRulesButNotMissingPaths() = runBlocking {
        val f = Fixture().apply { leaves = listOf(episode(), episode("21", "/data/GDRIVE/VIDEO/AV/자막B/NO_META/b.mkv")) }
        assertEquals("보관", f.update(settings = WatchedCollectionSettings("보관", "보관"))!!.tag)
    }

    @Test fun newUnwatchedEpisodesPreventReapplyingShowCollection() = runBlocking {
        val f = Fixture().apply { parent = show(viewed = 2, total = 3) }
        assertNull(f.update(show()))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun changingAccountDuringReadbackPreventsAnyCollectionWrite() = runBlocking {
        val f = Fixture().apply { afterEpisodes = { current = false } }
        assertNull(f.update())
        assertTrue(f.writes.isEmpty())
        val alreadyChanged = Fixture().apply { current = false }
        assertNull(alreadyChanged.update())
        assertEquals(0, alreadyChanged.metadataReads)
    }

    @Test fun moviesKeepAutomaticPlayedPartAndManualAllVersionsPolicy() = runBlocking {
        val movie = episode().copy(type = "movie", mediaFilePaths = listOf("/movies/a.mkv"))
        val auto = Fixture()
        assertEquals("123", auto.update(movie, played = "/data/GDRIVE/VIDEO/AV/자막B/NO_META/a.mkv", automatic = true)!!.tag)
        assertEquals("movie", auto.writes.single().item.type)
        assertEquals(0, auto.metadataReads)
        val manual = Fixture()
        assertEquals("KILL", manual.update(movie)!!.tag)
        assertEquals(0, manual.episodeReads)
    }

    @Test fun unsupportedItemsDoNotLoadOrChangeCollections() = runBlocking {
        for (type in listOf("track", "photo", "album", "collection", "directory")) {
            val f = Fixture()
            assertNull(f.update(episode().copy(type = type)))
            assertTrue(f.writes.isEmpty())
            assertEquals(0, f.metadataReads)
        }
    }

    @Test fun collectionFailureDoesNotUndoManualWatchedSave() = runBlocking {
        val f = Fixture().apply { writeError = PlexException("test failure") }
        var saved = false
        val notice = saveWatchedWithCollection(saveWatched = { saved = true }, updateCollection = { f.update()?.tag })
        assertTrue(saved)
        assertTrue(notice!!.contains("시청 완료는 저장됐지만"))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun sharedServerSkipsTheEntireCollectionFlow() = runBlocking {
        val f = Fixture()
        var saved = false
        assertNull(saveWatchedWithCollection(saveWatched = { saved = true }, updateCollection = { f.update()?.tag },
            collectionUpdatesAllowed = false))
        assertTrue(saved)
        assertEquals(0, f.metadataReads)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun cancellationRemainsCancellation() = runBlocking {
        val f = Fixture().apply { writeError = CancellationException("cancelled") }
        try { f.update(); fail("Must cancel") } catch (_: CancellationException) { }
        assertTrue(f.writes.isEmpty())
    }

    private suspend fun expectFailure(block: suspend () -> Any?) {
        try { block(); fail("Unsafe collection write must fail") } catch (_: PlexException) { }
    }
}
