package io.mirr.plexplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedCollectionPolicyTest {
    private val libraries = listOf("AV-모자이크제거", "AV-자막A", "AV-자막B", "AV1")
    private val specialRoots = listOf(
        "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META",
        "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Uncensored/NO_META",
        "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Western/NO_META",
        "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/기타",
    )

    @Test
    fun managementEligibilityDoesNotDependOnHavingAResolvedFilePath() {
        libraries.forEach { library ->
            listOf("movie", "episode", "clip", "video").forEach { type ->
                assertTrue(managesWatchedCollections(library, type))
                assertNull(watchedCollectionTag(library, type, null))
            }
        }
    }

    @Test
    fun managementEligibilityExcludesOtherLibrariesAndNonVideoItems() {
        listOf(null, "", "Movies", "AV2", "av1", "AV1 ").forEach { library ->
            assertFalse(managesWatchedCollections(library, "movie"))
        }
        listOf("track", "album", "show", "season", "collection", "photo", "", "Movie").forEach { type ->
            assertFalse(managesWatchedCollections("AV1", type))
        }
    }

    @Test
    fun eligibleLibrariesUseKillForOrdinaryFiles() {
        libraries.forEach { library ->
            assertEquals("KILL", watchedCollectionTag(library, "movie", "/video/ordinary/movie.mkv"))
        }
    }

    @Test
    fun everySupportedVideoTypeIsEligible() {
        listOf("movie", "episode", "clip", "video").forEach { type ->
            assertEquals("KILL", watchedCollectionTag("AV1", type, "/video/movie.mkv"))
            assertEquals("123", watchedCollectionTag("AV1", type, "${specialRoots.first()}/movie.mkv"))
        }
    }

    @Test
    fun allFourSpecialRootsOverrideKillAcrossEligibleLibraries() {
        libraries.forEach { library ->
            specialRoots.forEach { root ->
                assertEquals(root, "123", watchedCollectionTag(library, "movie", "$root/movie.mkv"))
            }
        }
    }

    @Test
    fun filesInsideNestedSpecialSubfoldersUse123() {
        specialRoots.forEach { root ->
            assertEquals("123", watchedCollectionTag("AV-자막B", "episode", "$root/2026/September/movie.mkv"))
        }
    }

    @Test
    fun adjacentFolderPrefixesAndParentFoldersAreNotSpecial() {
        listOf(
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META2/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Uncensored/NO_META2/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Western/NO_META2/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/기타2/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Uncensored/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Western/movie.mkv",
            "/video/NO_META/movie.mkv",
        ).forEach { file ->
            assertEquals(file, "KILL", watchedCollectionTag("AV-자막B", "movie", file))
        }
    }

    @Test
    fun onlyExactCaseSensitivePosixSpecialRootsUse123() {
        listOf(
            "/mnt/gds2/GDRIVE/VIDEO/AV/자막B/NO_META/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/no_meta/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/uncensored/NO_META/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막A/NO_META/movie.mkv",
        ).forEach { file ->
            assertEquals(file, "KILL", watchedCollectionTag("AV-자막B", "movie", file))
        }
    }

    @Test
    fun parentTraversalIsRejectedInsteadOfGuessingTheReplacementTag() {
        val root = specialRoots.first()
        assertEquals("123", watchedCollectionTag("AV-자막B", "movie", "$root/./movie.mkv"))
        assertNull(watchedCollectionTag("AV-자막B", "movie", "$root/./nested/../movie.mkv"))
        assertNull(watchedCollectionTag("AV-자막B", "movie", "$root/../movie.mkv"))
        assertNull(watchedCollectionTag("AV-자막B", "movie", "/../../movie.mkv"))
    }

    @Test fun manualCompletionRequiresEveryVersionToAgree() {
        assertEquals("KILL", manualWatchedCollectionTag("AV1", "movie", listOf("/video/a.mkv", "/video/b.mkv")))
        assertEquals("123", manualWatchedCollectionTag("AV1", "movie", specialRoots.map { "$it/movie.mkv" }))
        assertNull(manualWatchedCollectionTag("AV1", "movie", listOf("/video/a.mkv", "${specialRoots.first()}/a.mkv")))
    }

    @Test fun manualCompletionDoesNotIgnoreAnyMissingVersionPath() {
        assertNull(manualWatchedCollectionTag("AV1", "movie", emptyList()))
        for (missing in listOf("", " ", "relative.mkv", "https://server/video")) {
            assertNull(manualWatchedCollectionTag("AV1", "movie", listOf("/video/a.mkv", missing)))
        }
    }

    @Test fun manualCompletionNeverExpandsLibraryOrMediaScope() {
        assertNull(manualWatchedCollectionTag("Movies", "movie", listOf("/video/a.mkv")))
        assertNull(manualWatchedCollectionTag("AV1", "show", listOf("/video/a.mkv")))
        assertNull(manualWatchedCollectionTag("AV1", "season", listOf("/video/a.mkv")))
    }

    @Test fun controlCharactersAreRejectedForCollectionDecisions() {
        for (char in listOf('\n', '\r', '\t', '\u0000')) {
            assertNull(watchedCollectionTag("AV1", "movie", "/video/${char}a.mkv"))
        }
    }

    @Test
    fun libraryNamesMustMatchExactlyEvenForSpecialFiles() {
        listOf(null, "", "AV", "AV2", "Movies", "av1", "AV1 ", " AV1", "AV-자막B2").forEach { library ->
            assertNull(watchedCollectionTag(library, "movie", "${specialRoots.first()}/movie.mkv"))
            assertNull(watchedCollectionTag(library, "movie", "/video/movie.mkv"))
        }
    }

    @Test
    fun audioContainersAndOtherMediaTypesAreExcluded() {
        listOf("track", "album", "artist", "show", "season", "collection", "photo", "photoalbum", "directory", "", "Movie")
            .forEach { type ->
                assertNull(watchedCollectionTag("AV-자막B", type, "${specialRoots.first()}/movie.mkv"))
                assertNull(watchedCollectionTag("AV1", type, "/video/movie.mkv"))
            }
    }

    @Test
    fun unknownRelativeDirectoryAndUrlPathsAreExcluded() {
        listOf(
            null,
            "",
            "  ",
            "movie.mkv",
            "NO_META/movie.mkv",
            "../movie.mkv",
            "C:movie.mkv",
            "https://server.example/video/movie.mkv",
            "file://${specialRoots.first()}/movie.mkv",
            "smb://server/share/movie.mkv",
            "${specialRoots.first()}/",
            "/video/\u0000movie.mkv",
        ).forEach { file ->
            assertNull(file, watchedCollectionTag("AV-자막B", "movie", file))
        }
    }

    @Test
    fun plexMetadataPartsAndStreamEndpointsAreNotPhysicalFileEvidence() {
        listOf(
            "/library/metadata/42",
            "/library/metadata/42/children",
            "/library/parts/42/file.mkv",
            "/library/streams/123",
            "/video/:/transcode/universal/start.m3u8",
        ).forEach { file ->
            assertNull(file, watchedCollectionTag("AV-자막B", "movie", file))
        }
    }

    @Test
    fun windowsAndUncFilesAreValidButDoNotMatchThePosixExceptions() {
        listOf(
            """C:\Videos\movie.mkv""",
            "C:/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META/movie.mkv",
            """\\NAS\Media\NO_META\movie.mkv""",
            "//mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META/movie.mkv",
        ).forEach { file ->
            assertEquals(file, "KILL", watchedCollectionTag("AV-자막B", "movie", file))
        }
    }

    @Test
    fun physicalPercentEncodedFolderNamesAreNotDecodedIntoSpecialRoots() {
        assertEquals(
            "KILL",
            watchedCollectionTag(
                "AV-자막B",
                "movie",
                "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Uncensored%2FNO_META/movie.mkv",
            ),
        )
    }
}
