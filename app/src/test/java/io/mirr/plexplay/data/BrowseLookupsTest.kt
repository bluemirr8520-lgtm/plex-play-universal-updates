package io.mirr.plexplay.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class BrowseLookupsTest {
    private val account = PlexConnection("http://server", "test-only")
    private fun item(id: String, type: String = "movie") = PlexItem(id, "/library/metadata/$id", type, "Title $id")

    @Test fun cacheExpiresWithoutExtendingOnRead() {
        var time = 0L
        val cache = BrowseLookupCache<String, String>(2, 60) { time }
        assertNull(cache.get(account, "42"))
        cache.put(account, "42", "label")
        time = 59
        assertEquals("label", cache.get(account, "42"))
        time = 60
        assertNull(cache.get(account, "42"))
    }

    @Test fun cacheUsesLeastRecentlyUsedEviction() {
        val cache = BrowseLookupCache<String, String>(2, 60)
        cache.get(account, "a")
        cache.put(account, "a", "A")
        cache.put(account, "b", "B")
        cache.get(account, "a")
        cache.put(account, "c", "C")
        assertNull(cache.get(account, "b"))
        assertEquals("A", cache.get(account, "a"))
        assertEquals("C", cache.get(account, "c"))
    }

    @Test fun serverAndAccountChangesDropCacheAndRejectStaleWrites() {
        for (other in listOf(account.copy(token = "another-test-user"), account.copy(baseUrl = "http://another"))) {
            val cache = BrowseLookupCache<String, String>(2, 60)
            cache.get(account, "a")
            cache.put(account, "a", "old")
            assertNull(cache.get(other, "a"))
            cache.put(account, "a", "stale")
            assertNull(cache.get(other, "a"))
            cache.put(other, "a", "current")
            assertEquals("current", cache.get(other, "a"))
        }
    }

    @Test fun refreshAndWatchChangesCanExplicitlyClearCache() {
        val cache = BrowseLookupCache<String, String>(2, 60)
        cache.get(account, "a")
        cache.put(account, "a", "old")
        cache.clear()
        cache.put(account, "a", "late")
        assertNull(cache.get(account, "a"))
    }

    @Test fun episodeLabelShowsSeasonEpisodeAndTitleWithoutChangingSeriesIdentity() {
        val series = item("42", "show")
        val episode = item("50", "episode").copy(title = "마지막 이야기", seasonNumber = 2, episodeNumber = 8)
        val enriched = series.copy(latestEpisodeLabel = formatLatestEpisodeLabel(episode))
        assertEquals("최신 · S2:E8 · 마지막 이야기", enriched.latestEpisodeLabel)
        assertEquals(series.ratingKey, enriched.ratingKey)
        assertEquals(series.key, enriched.key)
        assertFalse(enriched.isPlayable)
    }

    @Test fun episodeLabelHandlesMissingNumbersAndSpecials() {
        assertEquals("최신 · Title 50", formatLatestEpisodeLabel(item("50", "episode")))
        assertEquals("최신 · 3화 · Title 50", formatLatestEpisodeLabel(item("50", "episode").copy(episodeNumber = 3)))
        assertEquals("최신 · S0:E1 · Title 50", formatLatestEpisodeLabel(item("50", "episode").copy(seasonNumber = 0, episodeNumber = 1)))
    }

    @Test fun parserReadsEpisodeNumbersEvenWithoutMediaStreams() {
        val xml = """<MediaContainer librarySectionID="7"><Video ratingKey="50" key="/library/metadata/50" type="episode" title="Episode" index="8" parentIndex="2" grandparentRatingKey="42"/></MediaContainer>"""
        val episode = PlexXmlParser.items(xml.byteInputStream()).single()
        assertEquals(8, episode.episodeNumber)
        assertEquals(2, episode.seasonNumber)
        assertEquals("7", episode.librarySectionId)
    }

    @Test fun actorResultsAppearBeforeSlowGenreLookupCompletes() = runBlocking {
        val tagged = item("42").copy(actors = listOf(PlexTag("Actor", "1")), genres = listOf(PlexTag("Genre", "2")))
        val genreRelease = CompletableDeferred<Unit>()
        val actorPublished = CompletableDeferred<Unit>()
        val job = async {
            loadRelatedContent(tagged, tagged, Semaphore(3), fetch = {
                if (it.startsWith("genre")) genreRelease.await()
                listOf(item(if (it.startsWith("actor")) "50" else "51"))
            }, onUpdate = { if (it.actorWorks.isNotEmpty()) actorPublished.complete(Unit) })
        }
        withTimeout(1_000) { actorPublished.await() }
        assertFalse(job.isCompleted)
        genreRelease.complete(Unit)
        assertEquals(listOf("50"), job.await().actorWorks.map { it.ratingKey })
    }

    @Test fun relatedQueriesAreDeduplicatedCappedAndExcludeCurrentParentAndEpisodes() = runBlocking {
        val tagged = item("42", "show").copy(actors = (1..5).map { PlexTag("Actor", "$it") } + PlexTag("Duplicate", "1"))
        val filters = mutableListOf<String>()
        val result = loadRelatedContent(item("49", "episode"), tagged, Semaphore(3), fetch = {
            filters += it
            listOf(item("42", "show"), item("49", "episode"), item("50"), item("50")) + (51..80).map { id -> item("$id") }
        })
        assertEquals(setOf("actor=1", "actor=2", "actor=3"), filters.toSet())
        assertEquals(3, filters.size)
        assertEquals(20, result.actorWorks.size)
        assertEquals("50", result.actorWorks.first().ratingKey)
        assertTrue(result.actorWorks.none { it.ratingKey in setOf("42", "49") })
    }

    @Test fun relatedRequestsNeverExceedThreeConcurrentLookups() = runBlocking {
        var active = 0
        var peak = 0
        val tagged = item("42").copy(actors = (1..3).map { PlexTag("A", "$it") }, genres = (1..3).map { PlexTag("G", "$it") })
        loadRelatedContent(tagged, tagged, Semaphore(3), fetch = {
            active++
            peak = maxOf(peak, active)
            delay(10)
            active--
            emptyList()
        })
        assertEquals(3, peak)
        assertEquals(0, active)
    }

    @Test fun timeoutOrFailureDoesNotDiscardOtherResults() = runBlocking {
        val tagged = item("42").copy(actors = listOf(PlexTag("A", "1")), genres = listOf(PlexTag("G", "2"), PlexTag("G", "3")))
        val result = loadRelatedContent(tagged, tagged, Semaphore(3), timeoutMs = 50, fetch = {
            when (it) {
                "genre=2" -> awaitCancellation()
                "genre=3" -> error("Synthetic optional lookup failure")
                else -> listOf(item("50"))
            }
        })
        assertEquals("50", result.actorWorks.single().ratingKey)
        assertTrue(result.similarGenreWorks.isEmpty())
    }

    @Test fun playPriorityCancellationReleasesEveryOptionalLookupPermit() = runBlocking {
        val permits = Semaphore(3)
        val started = CompletableDeferred<Unit>()
        var updates = 0
        val tagged = item("42").copy(actors = listOf(PlexTag("A", "1")))
        val lookup = launch {
            loadRelatedContent(tagged, tagged, permits, fetch = { started.complete(Unit); awaitCancellation() }, onUpdate = { updates++ })
        }
        started.await()
        lookup.cancelAndJoin()
        assertEquals(3, permits.availablePermits)
        assertEquals(0, updates)
        // Playback preparation is independent of the cancelled optional lookup.
        val playback = async { "ready" }
        assertEquals("ready", playback.await())
    }
}
