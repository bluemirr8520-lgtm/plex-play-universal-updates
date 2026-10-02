package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FolderPlaybackDiscoveryTest {
    @Test fun seriesScopeDoesNotLoseAnotherVirtualSeasonInSamePhysicalFolder() {
        assertEquals("/library/metadata/50/allLeaves", playbackCandidatePath(
            item("1").copy(parentRatingKey = "20", grandparentRatingKey = "50")))
    }

    @Test fun seasonScopeRemainsFallbackWhenSeriesIsUnknown() {
        assertEquals("/library/metadata/20/children", playbackCandidatePath(item("1").copy(parentRatingKey = "20")))
        assertEquals("/library/metadata/20/children", playbackCandidatePath(item("1").copy(parentKey = "/library/metadata/20/children")))
    }

    @Test fun moviesAndOrphanEpisodesCanUseTheirLibrary() {
        assertEquals("/library/sections/1/all", playbackCandidatePath(item("1").copy(type = "movie")))
        assertEquals("/library/sections/1/all", playbackCandidatePath(item("1")))
    }

    @Test fun missingScopeDoesNotSelectAnUnrelatedLibrary() {
        assertNull(playbackCandidatePath(item("1").copy(librarySectionId = null)))
    }

    private fun item(id: String, path: String? = "/video/Show/$id.mkv") = PlexItem(
        ratingKey = id, key = "/library/metadata/$id", type = "episode", title = id,
        partKey = "/library/parts/$id/file.mkv", filePath = path, librarySectionId = "1",
    )

    @Test fun filteredSingleResultCannotHideCompleteFolder() = runBlocking {
        val current = item("2")
        val queue = discoverSameFolderPlaybackQueue(current,
            listOf(listOf(item("1"), current, item("3")), listOf(current))) { it }
        assertEquals(listOf("1", "2", "3"), queue.map { it.ratingKey })
    }

    @Test fun homeOnDeckFindsOtherMoviesFromServerLibrary() = runBlocking {
        val current = item("2").copy(type = "movie")
        val queue = discoverSameFolderPlaybackQueue(current,
            listOf(listOf(item("1").copy(type = "movie"), current, item("3").copy(type = "movie")), listOf(current))) { it }
        assertEquals(3, queue.size)
        assertEquals("3", queue[queue.indexOf(current) + 1].ratingKey)
    }

    @Test fun sparseChildMetadataIsHydratedBeforeFiltering() = runBlocking {
        val current = item("1")
        val calls = mutableListOf<String>()
        val queue = discoverSameFolderPlaybackQueue(current, listOf(listOf(current, item("2", null)))) {
            calls += it.ratingKey
            item(it.ratingKey)
        }
        assertEquals(listOf("1", "2"), queue.map { it.ratingKey })
        assertEquals(listOf("2"), calls)
    }

    @Test fun hydrationDoesNotBorrowAParentSeasonAsFolderEvidence() = runBlocking {
        val current = item("1")
        val queue = discoverSameFolderPlaybackQueue(current, listOf(listOf(current, item("2", null)))) {
            it.copy(parentRatingKey = "same-season")
        }
        assertEquals(listOf(current), queue)
    }

    @Test fun differentFoldersAndLibrariesStayExcluded() = runBlocking {
        val current = item("1")
        val queue = discoverSameFolderPlaybackQueue(current, listOf(listOf(current,
            item("2", "/video/Other/2.mkv"), item("3").copy(librarySectionId = "2"),
            item("4", "/video/Show/extras/4.mkv")))) { it }
        assertEquals(listOf(current), queue)
    }

    @Test fun failedDetailsKeepOnlyVerifiedNeighbors() = runBlocking {
        val current = item("1")
        val queue = discoverSameFolderPlaybackQueue(current, listOf(listOf(current, item("2", null), item("3")))) {
            throw IllegalStateException("network unavailable")
        }
        assertEquals(listOf("1", "3"), queue.map { it.ratingKey })
    }

    @Test fun wrongMetadataIdentityIsNotAccepted() = runBlocking {
        val current = item("1")
        val queue = discoverSameFolderPlaybackQueue(current, listOf(listOf(current, item("2", null)))) { item("wrong") }
        assertEquals(listOf(current), queue)
    }

    @Test fun unknownCurrentFolderDoesNotTriggerDetailStorm() = runBlocking {
        val current = item("1", null)
        assertEquals(listOf(current), discoverSameFolderPlaybackQueue(current, listOf(listOf(current, item("2")))) {
            fail("Must not load unverifiable queue")
            it
        })
    }

    @Test fun unrelatedListsAreNotMerged() = runBlocking {
        val current = item("1")
        assertEquals(listOf(current), discoverSameFolderPlaybackQueue(current, listOf(listOf(item("2")))) { it })
    }

    @Test fun knownCachedPartCanFillSparseServerEntryWithoutLosingOrder() = runBlocking {
        val current = item("2")
        val queue = discoverSameFolderPlaybackQueue(current,
            listOf(listOf(item("1", null), current, item("3")), listOf(current, item("1")))) {
            fail("Already has known path")
            it
        }
        assertEquals(listOf("1", "2", "3"), queue.map { it.ratingKey })
    }

    @Test fun metadataConcurrencyIsBoundedToFour() = runBlocking {
        var active = 0
        var maximum = 0
        val current = item("0")
        val queue = discoverSameFolderPlaybackQueue(current,
            listOf(listOf(current) + (1..19).map { item("$it", null) })) {
            active++
            maximum = maxOf(maximum, active)
            delay(2)
            active--
            item(it.ratingKey)
        }
        assertEquals(20, queue.size)
        assertEquals(4, maximum)
    }

    @Test fun cancellationStopsDiscovery() {
        try {
            runBlocking { discoverSameFolderPlaybackQueue(item("1"), listOf(listOf(item("1"), item("2", null)))) {
                throw CancellationException("left player")
            } }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun pagesContinuePastShortServerPage() = runBlocking {
        val offsets = mutableListOf<Int>()
        val queue = loadPlaybackCandidatePages { start ->
            offsets += start
            when (start) { 0 -> listOf(item("1"), item("2")); 2 -> listOf(item("3")); else -> emptyList() }
        }
        assertEquals(listOf(0, 2, 3), offsets)
        assertEquals(listOf("1", "2", "3"), queue.map { it.ratingKey })
    }

    @Test fun ignoredPaginationStopsWithoutLoopingForever() = runBlocking {
        var calls = 0
        val queue = loadPlaybackCandidatePages { calls++; listOf(item("1"), item("2")) }
        assertEquals(2, calls)
        assertEquals(2, queue.size)
    }

    @Test fun overlappingPagesDeduplicateWithoutSkippingOffset() = runBlocking {
        val offsets = mutableListOf<Int>()
        val queue = loadPlaybackCandidatePages { start ->
            offsets += start
            when (start) { 0 -> listOf(item("1"), item("2")); 2 -> listOf(item("2"), item("3")); else -> emptyList() }
        }
        assertEquals(listOf(0, 2, 4), offsets)
        assertEquals(listOf("1", "2", "3"), queue.map { it.ratingKey })
    }

    @Test fun lastVideoNeverWrapsOrCrossesFolder() = runBlocking {
        val current = item("2")
        val queue = discoverSameFolderPlaybackQueue(current,
            listOf(listOf(item("1"), current, item("3", "/video/Else/3.mkv")))) { it }
        assertNull(queue.getOrNull(queue.indexOf(current) + 1))
    }
}
