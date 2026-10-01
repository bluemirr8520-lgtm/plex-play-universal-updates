package io.mirr.plexplay.ui

import io.mirr.plexplay.data.PlexConnection
import io.mirr.plexplay.data.PlexItem
import org.junit.Assert.*
import org.junit.Test

class BrowseHeaderStateTest {
    private val connection = PlexConnection("http://example.invalid", "test-token")
    private val series = PlexItem("10", "/library/metadata/10/children", "show", "시리즈", leafCount = 2)
    private val episode = PlexItem("21", "/library/metadata/21", "episode", "1화")
    private val detail = series.copy(summary = "상세 줄거리", genres = emptyList())
    private fun state() = PlexUiState(connection = connection, isHome = false, browsingItem = series,
        items = listOf(episode), title = "시리즈", query = "1화", canNavigateBack = true)

    @Test fun metadataAppearsInlineWithoutOpeningDetailsOrReplacingList() {
        val before = state()
        val after = before.withBrowseDetails(series, detail, connection)
        assertEquals(detail, after.browsingItem)
        assertEquals(before.items, after.items)
        assertEquals(before.query, after.query)
        assertEquals(before.title, after.title)
        assertTrue(after.canNavigateBack)
        assertNull(after.selectedItem)
    }

    @Test fun switchingFoldersRejectsOlderHeaderRead() {
        val current = state().copy(browsingItem = series.copy(ratingKey = "11"))
        assertSame(current, current.withBrowseDetails(series, detail, connection))
    }

    @Test fun switchingAccountsRejectsOlderHeaderRead() {
        val current = state().copy(connection = connection.copy(token = "another-account"))
        assertSame(current, current.withBrowseDetails(series, detail, connection))
    }

    @Test fun goingHomeDoesNotRestoreOldHeader() {
        val current = state().copy(isHome = true, browsingItem = null)
        assertSame(current, current.withBrowseDetails(series, detail, connection))
    }

    @Test fun confirmedWatchedStateCannotBeOverwrittenByEarlierMetadata() {
        val current = state().copy(browsingItem = series.copy(viewedLeafCount = 2))
        assertSame(current, current.withBrowseDetails(series, detail, connection))
        assertTrue(current.browsingItem!!.isWatched)
    }

    @Test fun unrelatedMetadataCannotReplaceTheSeries() {
        val current = state()
        assertSame(current, current.withBrowseDetails(series, detail.copy(ratingKey = "99"), connection))
    }

    @Test fun playableMetadataCannotTurnHeaderIntoAnEpisodeAction() {
        val current = state()
        assertSame(current, current.withBrowseDetails(series, detail.copy(type = "episode"), connection))
        assertSame(current, current.withBrowseDetails(series, detail.copy(partKey = "/parts/1"), connection))
    }

    @Test fun seriesActionRefreshesEpisodeBadgesWithoutChangingPageOrSearch() {
        val current = state()
        val watchedEpisode = episode.copy(viewCount = 1)
        val result = current.withBrowseItems(series, listOf(watchedEpisode), connection)
        assertEquals(listOf(watchedEpisode), result.items)
        assertEquals(series, result.browsingItem)
        assertEquals(current.query, result.query)
        assertTrue(result.canNavigateBack)
        assertNull(result.selectedItem)
    }

    @Test fun staleListRefreshDoesNotReplaceAnotherFolderOrAccount() {
        val otherFolder = state().copy(browsingItem = series.copy(ratingKey = "12"))
        assertSame(otherFolder, otherFolder.withBrowseItems(series, emptyList(), connection))
        val otherAccount = state().copy(connection = connection.copy(token = "another-account"))
        assertSame(otherAccount, otherAccount.withBrowseItems(series, emptyList(), connection))
    }

    @Test fun emptyListRefreshKeepsHeaderButNeverReopensItAtHome() {
        val current = state()
        val empty = current.withBrowseItems(series, emptyList(), connection)
        assertEquals(series, empty.browsingItem)
        assertTrue(empty.items.isEmpty())
        val home = current.copy(isHome = true)
        assertSame(home, home.withBrowseItems(series, emptyList(), connection))
    }
}
