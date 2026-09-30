package io.mirr.plexplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedCollectionPolicyTest {
    private val libraries = listOf(
        "AV-모자이크제거", "AV-자막A", "AV-자막B", "AV1",
        "Movies", "VOD 드라마", "TV Shows", "애니메이션", "Home Videos", "새 라이브러리 2026",
    )
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
    fun managementEligibilityExcludesMissingLibrariesAndNonVideoItems() {
        listOf(null, "", " ", "\t").forEach { library ->
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
    fun commonPosixFolderNamesRemainCaseSensitive() {
        listOf(
            "/mnt/GDS2/gdrive/VIDEO/AV/자막B/NO_META/movie.mkv",
            "/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/no_meta/movie.mkv",
            "/mnt/GDS2/GDRIVE/video/AV/자막B/NO_META/movie.mkv",
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

    @Test fun manualCompletionKeepsTheIndividualVideoScopeInAllLibraries() {
        libraries.forEach { library ->
            assertEquals("KILL", manualWatchedCollectionTag(library, "movie", listOf("/video/a.mkv")))
            assertNull(manualWatchedCollectionTag(library, "show", listOf("/video/a.mkv")))
            assertNull(manualWatchedCollectionTag(library, "season", listOf("/video/a.mkv")))
            assertNull(manualWatchedCollectionTag(library, "track", listOf("/video/a.mkv")))
        }
    }

    @Test fun controlCharactersAreRejectedForCollectionDecisions() {
        for (char in listOf('\n', '\r', '\t', '\u0000')) {
            assertNull(watchedCollectionTag("AV1", "movie", "/video/${char}a.mkv"))
        }
    }

    @Test
    fun unknownLibraryIsStillRejectedEvenForSpecialFiles() {
        listOf(null, "", " ", "\t").forEach { library ->
            assertNull(watchedCollectionTag(library, "movie", "${specialRoots.first()}/movie.mkv"))
            assertNull(watchedCollectionTag(library, "movie", "/video/movie.mkv"))
        }
    }

    @Test fun newlyAddedOrRenamedLibraryDoesNotRequireAnAppUpdate() {
        listOf("AV", "AV2", "av1", "AV1 ", " AV1", "AV-자막B2", "영화 / UHD", "개인 보관함 🎬").forEach { library ->
            assertEquals("KILL", watchedCollectionTag(library, "movie", "/video/movie.mkv"))
            assertEquals("123", watchedCollectionTag(library, "movie", "${specialRoots.first()}/movie.mkv"))
        }
    }

    @Test fun manualAndPlaybackCompletionShareRulesForEveryLibraryAndVideoType() {
        libraries.forEach { library ->
            listOf("movie", "episode", "clip", "video").forEach { type ->
                (listOf("/video/movie.mkv") + specialRoots.map { "$it/movie.mkv" }).forEach { path ->
                    assertEquals(watchedCollectionTag(library, type, path), manualWatchedCollectionTag(library, type, listOf(path)))
                }
                assertNull(manualWatchedCollectionTag(library, type, listOf("/video/movie.mkv", "${specialRoots.first()}/movie.mkv")))
            }
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
    fun unrelatedWindowsAndUncFoldersRemainOrdinary() {
        listOf(
            """C:\Videos\movie.mkv""",
            """\\NAS\Media\NO_META\movie.mkv""",
        ).forEach { file ->
            assertEquals(file, "KILL", watchedCollectionTag("AV-자막B", "movie", file))
        }
    }

    @Test fun allFourRulesMatchDifferentMountPrefixesAndDescendants() {
        val suffixes = specialRoots.map { it.substringAfter("/mnt/GDS2/") }
        for (prefix in listOf("/mnt/GDS2/", "/mnt/gds2/", "/mnt/GDS9/", "/data/storage/", "/volume1/", "/")) {
            for (suffix in suffixes) {
                assertEquals("123", watchedCollectionTag("Movies", "movie", "$prefix$suffix/movie.mkv"))
                assertEquals("123", watchedCollectionTag("TV", "episode", "$prefix$suffix/nested/movie.mkv"))
            }
        }
    }

    @Test fun windowsAndUncCommonPhysicalFoldersAlsoMatch() {
        for (file in listOf(
            "D:/GDRIVE/VIDEO/AV/자막B/NO_META/movie.mkv",
            """Z:\Media\gdrive\video\av\자막B\western\no_meta\movie.mkv""",
            "//NAS/Media/GDRIVE/VIDEO/AV/자막B/기타/movie.mkv",
            "//mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META/movie.mkv",
        )) assertEquals(file, "123", watchedCollectionTag("TV", "episode", file))
    }

    @Test fun commonRuleRequiresAllComponentsAndNeverMatchesPartialNamesOrUncHosts() {
        for (file in listOf(
            "/mnt/AV/자막B/NO_META/a.mkv",
            "/mnt/OTHERGDRIVE/VIDEO/AV/자막B/NO_META/a.mkv",
            "/mnt/GDRIVE/VIDEO2/AV/자막B/NO_META/a.mkv",
            "/mnt/GDRIVE/VIDEO/AV/자막B2/NO_META/a.mkv",
            "/mnt/GDRIVE/VIDEO/AV/자막B/NO_META2/a.mkv",
            "/mnt/GDRIVE/VIDEO/AV/자막B/NO_META.mkv",
            "//GDRIVE/VIDEO/AV/자막B/NO_META/a.mkv",
            "/mnt/GDRIVE%2FVIDEO/AV/자막B/NO_META/a.mkv",
        )) assertEquals(file, "KILL", watchedCollectionTag("TV", "episode", file))
    }

    @Test fun noMetaAtAnyDepthBelowCommonBaseUsesSpecialRule() {
        for (prefix in listOf("/mnt/GDS2", "/mnt/GDS9", "/data/backup")) {
            for (tail in listOf("NO_META", "uncensored/NO_META", "NewFolder/NO_META",
                "NewFolder/2026/NO_META", "A/B/C/NO_META/Season 1", "NO_META/A/NO_META/B")) {
                assertEquals("123", watchedCollectionTag("TV", "episode", "$prefix/GDRIVE/VIDEO/AV/자막B/$tail/a.mkv"))
            }
        }
        assertEquals("123", watchedCollectionTag("TV", "episode",
            "D:/mount/GDRIVE/VIDEO/AV/자막B/any/depth/no_meta/season/a.mkv"))
    }

    @Test fun noMetaMustBeAWholeFolderBelowTheCorrectBase() {
        for (path in listOf(
            "/NO_META/GDRIVE/VIDEO/AV/자막B/Normal/a.mkv",
            "/data/GDRIVE/VIDEO/AV/자막B/A/B/NO_META2/a.mkv",
            "/data/GDRIVE/VIDEO/AV/자막B/A/B/MY_NO_META/a.mkv",
            "/data/GDRIVE/VIDEO/AV/자막B/A/B/NO_META.mkv",
            "/data/GDRIVE/VIDEO/AV/자막A/A/B/NO_META/a.mkv",
            "/data/GDRIVE/VIDEO/AV/자막B/A/기타/a.mkv",
        )) assertEquals(path, "KILL", watchedCollectionTag("TV", "episode", path))
    }

    @Test fun deepNoMetaAndExistingOtherFolderAgreeForManualCompletionWithCustomNames() {
        val paths = listOf("/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/New/A/NO_META/1.mkv",
            "/data/GDRIVE/VIDEO/AV/자막B/New/B/NO_META/2.mkv",
            "/data/GDRIVE/VIDEO/AV/자막B/기타/Sub/3.mkv")
        val settings = WatchedCollectionSettings("완료", "예외 영상")
        assertEquals("예외 영상", manualWatchedCollectionTag("TV", "episode", paths, settings))
        assertNull(manualWatchedCollectionTag("TV", "episode", paths + "/data/normal/a.mkv", settings))
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
