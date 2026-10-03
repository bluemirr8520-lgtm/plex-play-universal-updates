package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecentSeriesTest {
    private fun episode(id: Int, parent: String = "42", added: Long? = id.toLong()) =
        PlexItem("$id", "/library/metadata/$id", "episode", "Episode $id",
            grandparentRatingKey = parent, librarySectionId = "7", addedAtSeconds = added,
            grandparentTitle = "Series $parent", grandparentThumb = "/poster/$parent",
            grandparentArt = "/art/$parent", seasonNumber = 2, episodeNumber = id)
    private fun show(id: String) = PlexItem(id, "/library/metadata/$id/children", "show", "Series $id",
        librarySectionId = "7", thumb = "/saved/$id", leafCount = 12, viewedLeafCount = 4)
    private suspend fun rows(items: List<PlexItem>, shows: List<PlexItem> = emptyList()) =
        loadRecentSeries("7", loadEpisodePage = { start, _ -> if (start == 0) items else emptyList() },
            loadShowMetadata = { shows })

    @Test fun oldSeriesWithNewEpisodeOutranksNewlyRegisteredSeries() = runBlocking {
        val result = rows(listOf(episode(10, "42", 300), episode(11, "43", 100)),
            listOf(show("42").copy(addedAtSeconds = 1), show("43").copy(addedAtSeconds = 500)))
        assertEquals(listOf("42", "43"), result.map { it.ratingKey })
        assertEquals("S2:E10 · Episode 10", result.first().latestEpisodeLabel)
    }

    @Test fun additionDateNotHighestEpisodeOrSeasonNumberSelectsLabel() = runBlocking {
        val result = rows(listOf(episode(99, added = 1).copy(seasonNumber = 5), episode(1, added = 20)))
        assertEquals("S2:E1 · Episode 1", result.single().latestEpisodeLabel)
    }

    @Test fun metadataKeepsSeriesPosterIdentityAndWatchedCounts() = runBlocking {
        val original = show("42")
        val row = rows(listOf(episode(10)), listOf(original)).single()
        assertEquals(original.copy(latestEpisodeLabel = "S2:E10 · Episode 10"), row)
        assertFalse(row.isPlayable)
    }

    @Test fun moviesWrongLibrariesAndInvalidParentsNeverBecomeShows() = runBlocking {
        val result = rows(listOf(episode(1).copy(type = "movie"), episode(2).copy(librarySectionId = "8"),
            episode(3, "42/../43"), episode(4).copy(grandparentRatingKey = null), episode(5, "45")))
        assertEquals(listOf("45"), result.map { it.ratingKey })
    }

    @Test fun shortCappedPagesContinueAndDeduplicateSeriesAcrossSeasons() = runBlocking {
        val starts = mutableListOf<Int>()
        val result = loadRecentSeries("7", loadEpisodePage = { start, _ ->
            starts += start
            when (start) { 0 -> listOf(episode(1, "42", 40)); 1 -> listOf(episode(2, "42", 30), episode(3, "43", 20)); else -> emptyList() }
        }, loadShowMetadata = { emptyList() })
        assertEquals(listOf(0, 1, 3), starts)
        assertEquals(listOf("42", "43"), result.map { it.ratingKey })
        assertEquals("S2:E1 · Episode 1", result.first().latestEpisodeLabel)
    }

    @Test fun repeatedServerPageStopsAndDoesNotLoopForever() = runBlocking {
        var calls = 0
        val result = loadRecentSeries("7", maxPages = 20, loadEpisodePage = { _, _ -> calls++; listOf(episode(1)) },
            loadShowMetadata = { emptyList() })
        assertEquals(2, calls)
        assertEquals(1, result.size)
    }

    @Test fun optionalLookupHasPageAndUniqueSeriesLimits() = runBlocking {
        var calls = 0
        val result = loadRecentSeries("7", limit = 2, loadEpisodePage = { _, _ ->
            calls++; (1..5).map { episode(it, "${40 + it}", it.toLong()) }
        }, loadShowMetadata = { ids -> assertEquals(listOf("45", "44"), ids); emptyList() })
        assertEquals(1, calls)
        assertEquals(2, result.size)
        calls = 0
        loadRecentSeries("7", maxPages = 3, loadEpisodePage = { _, _ -> calls++; listOf(episode(calls)) }, loadShowMetadata = { emptyList() })
        assertEquals(3, calls)
    }

    @Test fun oneBatchAndPartialMetadataUseSeriesArtworkFallbackOnly() = runBlocking {
        var batches = 0
        val result = loadRecentSeries("7", loadEpisodePage = { start, _ -> if (start == 0) listOf(episode(2, "43"), episode(1)) else emptyList() },
            loadShowMetadata = { ids -> batches++; assertEquals(listOf("43", "42"), ids); listOf(show("42")) })
        assertEquals(1, batches)
        assertEquals("/poster/43", result.first().thumb)
        assertEquals("/art/43", result.first().art)
        assertEquals("/library/metadata/43/children", result.first().key)
        assertEquals("/saved/42", result.last().thumb)
    }

    @Test fun failedMetadataDoesNotLoseShowCardsAndMissingTitleIsNotAnEpisodeCard() = runBlocking {
        val result = loadRecentSeries("7", loadEpisodePage = { start, _ -> if (start == 0) listOf(episode(1), episode(2, "43").copy(grandparentTitle = null)) else emptyList() },
            loadShowMetadata = { throw IllegalStateException("fixture-only") })
        assertEquals(listOf("42"), result.map { it.ratingKey })
        assertTrue(result.all { it.type == "show" && !it.isPlayable })
    }

    @Test fun metadataFromWrongLibraryOrMovieCannotReplaceSeriesIdentity() = runBlocking {
        val result = rows(listOf(episode(1), episode(2, "43")),
            listOf(show("42").copy(librarySectionId = "8"), show("43").copy(type = "movie")))
        assertTrue(result.all { it.type == "show" && it.librarySectionId == "7" })
        assertTrue(result.all { it.thumb!!.startsWith("/poster/") })
    }

    @Test fun missingDatesKeepServerOrderAndLaterPageFailureKeepsLoadedRows() = runBlocking {
        val result = loadRecentSeries("7", loadEpisodePage = { start, _ ->
            if (start == 0) listOf(episode(1, "42", null), episode(2, "43", null)) else throw IllegalStateException("fixture-only")
        }, loadShowMetadata = { emptyList() })
        assertEquals(listOf("42", "43"), result.map { it.ratingKey })
    }

    @Test fun cancellationPropagatesInsteadOfPublishingFallbackRows() = runBlocking {
        for (cancelMetadata in listOf(false, true)) {
            try {
                loadRecentSeries("7", loadEpisodePage = { start, _ ->
                    if (!cancelMetadata) throw CancellationException("fixture-only")
                    if (start == 0) listOf(episode(1)) else emptyList()
                }, loadShowMetadata = { throw CancellationException("fixture-only") })
                fail("Cancellation was swallowed")
            } catch (_: CancellationException) { }
        }
    }
}
