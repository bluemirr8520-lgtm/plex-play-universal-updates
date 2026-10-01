package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EpisodeBrowsingTest {
    private val show = PlexItem("10", "/library/metadata/10/children", "show", "시리즈")
    private val season = PlexItem("11", "/library/metadata/11/children", "season", "시즌", parentRatingKey = "10")
    private val aggregate = PlexItem("/library/metadata/10/allLeaves", "/library/metadata/10/allLeaves", "directory", "에피소드")
    private val episodes = listOf(
        PlexItem("21", "/library/metadata/21", "episode", "1화"),
        PlexItem("22", "/library/metadata/22", "episode", "2화"),
    )

    private fun resolve(parent: PlexItem, first: List<PlexItem>, second: List<PlexItem> = episodes): Pair<List<PlexItem>, List<PlexItem>> = runBlocking {
        val calls = mutableListOf<PlexItem>()
        val result = episodeBrowseItems(parent) { calls += it; if (calls.size == 1) first else second }
        result to calls
    }

    @Test fun containersOpenListsAndVideosKeepTheirDetails() {
        listOf("show", "season", "directory").forEach { assertTrue(show.copy(type = it).opensEpisodeList) }
        listOf("episode", "movie", "clip", "track", "artist", "collection").forEach {
            assertFalse(show.copy(type = it).opensEpisodeList)
        }
    }

    @Test fun playableDirectoryNeverBypassesPlaybackDetails() {
        assertFalse(season.copy(type = "directory", partKey = "/parts/1").opensEpisodeList)
    }

    @Test fun singleSeasonWithAggregateOpensEpisodesInServerOrder() {
        val (result, calls) = resolve(show, listOf(aggregate, season), episodes.reversed())
        assertEquals(episodes.reversed(), result)
        assertEquals(listOf(show, season), calls)
    }

    @Test fun multipleSeasonsRemainSelectableWithoutAggregate() {
        val other = season.copy(ratingKey = "12", key = "/library/metadata/12/children")
        val (result, calls) = resolve(show, listOf(aggregate, season, other))
        assertEquals(listOf(season, other), result)
        assertEquals(listOf(show), calls)
    }

    @Test fun directEpisodeListDoesNotReadEpisodeChildrenOrAutoplay() {
        val (result, calls) = resolve(season, episodes.take(1))
        assertEquals(episodes.take(1), result)
        assertEquals(listOf(season), calls)
    }

    @Test fun skipsExactlyOneFolderNotRecursiveDescendants() {
        val nested = season.copy(ratingKey = "12", key = "/library/metadata/12/children", parentRatingKey = "11")
        val (result, calls) = resolve(show, listOf(season), listOf(nested))
        assertEquals(listOf(nested), result)
        assertEquals(2, calls.size)
    }

    @Test fun emptySecondListKeepsOriginalFolderChoice() {
        val first = listOf(aggregate, season)
        assertEquals(first, resolve(show, first, emptyList()).first)
    }

    @Test fun optionalReadFailureKeepsOriginalFolderChoice() = runBlocking {
        val result = episodeBrowseItems(show) { if (it == show) listOf(season) else throw IllegalStateException("offline") }
        assertEquals(listOf(season), result)
    }

    @Test(expected = IllegalStateException::class)
    fun firstReadFailureIsNotHidden() { runBlocking { episodeBrowseItems(show) { throw IllegalStateException("offline") } } }

    @Test(expected = CancellationException::class)
    fun optionalReadCancellationIsNotSwallowed() { runBlocking {
        episodeBrowseItems(show) { if (it == show) listOf(season) else throw CancellationException("back") }
    } }

    @Test fun selfReferenceIsNotFollowedEvenWithDifferentKeyShape() {
        val self = season.copy(ratingKey = "10", key = "/library/metadata/10")
        assertEquals(listOf(show), resolve(show, listOf(self)).second)
    }

    @Test fun explicitlyDifferentParentIsNotSkipped() {
        assertEquals(listOf(show), resolve(show, listOf(season.copy(parentRatingKey = "99"))).second)
    }

    @Test fun mixedEpisodeAndFolderListsAreNotFlattened() {
        val first = listOf(season, episodes.first())
        assertEquals(first to listOf(show), resolve(show, first))
    }

    @Test fun foreignAggregateIsNotDiscardedToForceSingleFolder() {
        val first = listOf(season, aggregate.copy(key = "/library/metadata/99/allLeaves"))
        assertEquals(first to listOf(show), resolve(show, first))
    }

    @Test fun aggregateOnlyListRemainsAvailableAndOpensDirectly() {
        assertEquals(listOf(aggregate) to listOf(show), resolve(show, listOf(aggregate)))
        assertEquals(episodes to listOf(aggregate), resolve(aggregate, episodes))
    }

    @Test fun unknownOrUnaddressableFolderIsNotSkipped() {
        assertEquals(listOf(show), resolve(show, listOf(season.copy(ratingKey = "", key = ""))).second)
        assertEquals(listOf(show), resolve(show, listOf(season.copy(type = "collection"))).second)
    }

    @Test fun blankKeysWithDistinctNumericIdsCanStillSkipOneLayer() {
        val parent = show.copy(key = "")
        val folder = season.copy(key = "")
        assertEquals(episodes to listOf(parent, folder), resolve(parent, listOf(folder)))
    }

    @Test fun duplicateFolderRowsAreNotSilentlyDropped() {
        val first = listOf(season, season.copy(key = "/library/metadata/11"))
        assertEquals(first to listOf(show), resolve(show, first))
    }

    @Test fun genericFolderCanSkipOneChildButMusicCollectionsCannot() {
        val parent = show.copy(type = "directory")
        assertEquals(episodes to listOf(parent, season), resolve(parent, listOf(season)))
        val artist = show.copy(type = "artist")
        assertEquals(listOf(season) to listOf(artist), resolve(artist, listOf(season)))
    }

    @Test fun emptyFirstListRemainsEmptyForInlineHeader() {
        assertEquals(emptyList<PlexItem>() to listOf(show), resolve(show, emptyList()))
    }
}
