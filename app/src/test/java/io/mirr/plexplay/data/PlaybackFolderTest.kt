package io.mirr.plexplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFolderTest {
    @Test
    fun refusesMissingRelativeAndStreamPaths() {
        listOf(
            null,
            "",
            "   ",
            "movie.mkv",
            "Season 1/movie.mkv",
            "../Season 1/movie.mkv",
            "C:Season 1/movie.mkv",
            "https://server.example/videos/movie.mkv",
            "smb://server/share/movie.mkv",
            "/library/parts/42/file.mkv",
        ).forEach { path ->
            assertNull("Not a physical absolute file path: $path", playbackFolderKey(path))
        }
    }

    @Test
    fun allowsOnlyTheExactParentFolder() {
        val current = item("1", "/video/Show/Season 1/01.mkv")

        assertTrue(sharesPlaybackFolder(current, item("2", "/video/Show/Season 1/02.mp4")))
        assertFalse(sharesPlaybackFolder(current, item("3", "/video/Show/Season 1/Extras/03.mkv")))
        assertFalse(sharesPlaybackFolder(current, item("4", "/video/Show/Season 10/04.mkv")))
        assertFalse(sharesPlaybackFolder(current, item("5", "/video/Other/Season 1/05.mkv")))
    }

    @Test
    fun preservesPosixFolderCase() {
        val current = item("1", "/video/Show/01.mkv")

        assertTrue(sharesPlaybackFolder(current, item("2", "/video/Show/02.mkv")))
        assertFalse(sharesPlaybackFolder(current, item("3", "/video/show/03.mkv")))
    }

    @Test
    fun preservesLiteralBackslashesInPosixFolders() {
        val current = item("1", "/video/Show\\Season/01.mkv")
        assertTrue(sharesPlaybackFolder(current, item("2", "/video/Show\\Season/02.mkv")))
        assertFalse(sharesPlaybackFolder(current, item("3", "/video/Show/Season/03.mkv")))
    }

    @Test
    fun lastSameFolderItemHasNoSuccessorFromAnotherFolder() {
        val current = item("2", "/video/Show/02.mkv")
        val queue = sameFolderPlaybackQueue(
            current,
            listOf(item("1", "/video/Show/01.mkv"), current, item("3", "/video/Other/03.mkv")),
        )
        val currentIndex = queue.indexOfFirst { it.ratingKey == current.ratingKey }
        assertNull(queue.getOrNull(currentIndex + 1))
    }

    @Test
    fun normalizesWindowsDriveCaseAndSeparators() {
        val current = item("1", """C:\Videos\Show\01.mkv""")

        assertNotNull(playbackFolderKey(current.filePath))
        assertTrue(sharesPlaybackFolder(current, item("2", "c:/videos/show/02.mkv")))
        assertFalse(sharesPlaybackFolder(current, item("3", "D:/Videos/Show/03.mkv")))
    }

    @Test
    fun normalizesUncPathsWithoutCrossingServersOrShares() {
        val current = item("1", """\\NAS\Media\Show\01.mkv""")

        assertNotNull(playbackFolderKey(current.filePath))
        assertTrue(sharesPlaybackFolder(current, item("2", "//nas/media/show/02.mkv")))
        assertFalse(sharesPlaybackFolder(current, item("3", "//other/media/show/03.mkv")))
        assertFalse(sharesPlaybackFolder(current, item("4", "//nas/other/show/04.mkv")))
    }

    @Test
    fun normalizesDotSegmentsWithinTheSameRoot() {
        val current = item("1", "/video/Show/Season 1/01.mkv")

        assertTrue(
            sharesPlaybackFolder(
                current,
                item("2", "/video/Show/./Season 1/temporary/../02.mkv"),
            ),
        )
        assertTrue(
            sharesPlaybackFolder(
                item("3", "C:/Videos/Show/03.mkv"),
                item("4", "C:/Videos/./Show/temporary/../04.mkv"),
            ),
        )
    }

    @Test
    fun refusesDotSegmentsThatEscapeTheFilesystemRoot() {
        listOf(
            "/../movie.mkv",
            "/video/../../movie.mkv",
            "C:/../movie.mkv",
            "//nas/media/../movie.mkv",
        ).forEach { path ->
            assertNull("Path escapes its root: $path", playbackFolderKey(path))
        }
    }

    @Test
    fun doesNotDecodePhysicalFolderNamesAsUrls() {
        assertFalse(
            sharesPlaybackFolder(
                item("1", "/video/Show%2FSeason/01.mkv"),
                item("2", "/video/Show/Season/02.mkv"),
            ),
        )
        assertFalse(
            sharesPlaybackFolder(
                item("3", "/video/Show+Season/03.mkv"),
                item("4", "/video/Show Season/04.mkv"),
            ),
        )
    }

    @Test
    fun plexSeasonMetadataDoesNotSubstituteForAFileFolder() {
        val current = item("1", "/video/Season 1/01.mkv")
            .copy(parentRatingKey = "season", parentKey = "/library/metadata/season")
        val sameSeasonDifferentFolder = item("2", "/video/Other/02.mkv")
            .copy(parentRatingKey = "season", parentKey = "/library/metadata/season")
        val sameSeasonUnknownFolder = item("3", null)
            .copy(parentRatingKey = "season", parentKey = "/library/metadata/season")

        assertFalse(sharesPlaybackFolder(current, sameSeasonDifferentFolder))
        assertFalse(sharesPlaybackFolder(current, sameSeasonUnknownFolder))
        assertFalse(sharesPlaybackFolder(item("4", null), item("5", null)))
    }

    @Test
    fun excludesDifferentKnownLibrarySectionsEvenWithTheSamePath() {
        val current = item("1", "/video/Show/01.mkv", section = "10")
        val candidate = item("2", "/video/Show/02.mkv", section = "20")

        assertFalse(sharesPlaybackFolder(current, candidate))
    }

    @Test
    fun permitsMissingSectionMetadataWhenThePhysicalFolderMatches() {
        val known = item("1", "/video/Show/01.mkv", section = "10")
        val unknown = item("2", "/video/Show/02.mkv", section = null)

        assertTrue(sharesPlaybackFolder(known, unknown))
        assertTrue(sharesPlaybackFolder(unknown, known))
    }

    @Test
    fun queueKeepsOrderDeduplicatesAndUsesTheResolvedCurrentItem() {
        val current = item("2", "/video/Show/02.mkv").copy(title = "Resolved current")
        val previous = item("1", "/video/Show/01.mkv")
        val staleCurrent = current.copy(filePath = null, title = "Stale current")
        val next = item("3", "/video/Show/03.mkv")
        val otherFolder = item("4", "/video/Other/04.mkv")
        val otherSection = item("5", "/video/Show/05.mkv", section = "20")

        val queue = sameFolderPlaybackQueue(
            current,
            listOf(previous, otherFolder, staleCurrent, next, next.copy(title = "Duplicate"), otherSection),
        )

        assertEquals(listOf("1", "2", "3"), queue.map { it.ratingKey })
        assertSame(current, queue[1])
        assertSame(next, queue[2])
    }

    @Test
    fun queueDoesNotAttachAnUnrelatedListWhenCurrentIsAbsent() {
        val current = item("1", "/video/Show/01.mkv")

        assertEquals(
            listOf(current),
            sameFolderPlaybackQueue(current, listOf(item("2", "/video/Show/02.mkv"))),
        )
        assertEquals(listOf(current), sameFolderPlaybackQueue(current, emptyList()))
    }

    @Test
    fun queueWithUnknownCurrentFolderContainsOnlyCurrent() {
        val current = item("1", null)

        assertEquals(
            listOf(current),
            sameFolderPlaybackQueue(current, listOf(current, item("2", "/video/Show/02.mkv"))),
        )
    }

    @Test
    fun qualityChangeCannotUseAStaleQueueWhenSourceFolderIsUnknownOrChanged() {
        val current = item("1", "/video/Show/01.mkv")
        val next = item("2", "/video/Show/02.mkv")
        val source = PlaybackSource(
            url = "https://example.invalid/stream.m3u8",
            filePath = current.filePath,
            token = "",
            title = current.title,
            subtitle = null,
            ratingKey = current.ratingKey,
            durationMs = 0,
            resumePositionMs = 0,
        )
        assertSame(next, validatedPlaybackNeighbor(current, source, next))
        assertNull(validatedPlaybackNeighbor(current, source.copy(filePath = null), next))
        assertNull(validatedPlaybackNeighbor(current, source.copy(filePath = "/video/Other/01.mkv"), next))
        assertNull(validatedPlaybackNeighbor(current, source.copy(ratingKey = "another"), next))
        assertNull(validatedPlaybackNeighbor(current, source, item("3", "/video/Other/03.mkv")))
    }

    private fun item(id: String, file: String?, section: String? = "10") = PlexItem(
        ratingKey = id,
        key = "/library/metadata/$id",
        type = "episode",
        title = "Episode $id",
        partKey = "/library/parts/$id/file.mkv",
        filePath = file,
        librarySectionId = section,
    )
}
