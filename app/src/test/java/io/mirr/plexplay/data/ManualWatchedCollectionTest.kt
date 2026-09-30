package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManualWatchedCollectionTest {
    @Test fun sharedServerSavesWatchedWithoutReadingPathsOrEditingCollection() = runBlocking {
        val connection = PlexConnection("http://fixture", "fixture", isServerOwner = false)
        var saved = false
        var attempted = false
        val result = saveWatchedWithCollection(
            saveWatched = { saved = true },
            updateCollection = { attempted = true; error("Shared metadata may omit paths") },
            collectionUpdatesAllowed = connection.mayUpdateCollections,
        )
        assertTrue(saved)
        assertFalse(attempted)
        assertNull(result)
    }
    @Test fun sharedWatchedFailureStillPropagates() = runBlocking {
        val expected = PlexException("Watched permission denied")
        try {
            saveWatchedWithCollection({ throw expected }, { error("Must not edit") }, false)
            fail("Expected watched error")
        } catch (actual: PlexException) {
            assertSame(expected, actual)
        }
    }
    @Test fun legacyAndOwnerConnectionsKeepCollectionSupport() {
        assertTrue(PlexConnection().mayUpdateCollections)
        assertTrue(PlexConnection(isServerOwner = true).mayUpdateCollections)
        assertFalse(PlexConnection(isServerOwner = false).mayUpdateCollections)
    }
    @Test fun optionalPermissionDenialKeepsWatchedWithoutFailureOrFalseCollectionSuccess() = runBlocking {
        var saved = false
        val denied = PlexException("denied", collectionPermissionDenied = true)
        assertNull(saveWatchedWithCollection({ saved = true }, { throw denied }))
        assertTrue(saved)
        // The automatic-completion UI uses this same notice policy.
        assertNull(watchedCollectionFailureNotice(denied))
    }
    @Test fun unrelatedFailuresAreNotSilencedByTheirMessageText() {
        for (error in listOf(PlexException("Plex 계정 인증에 실패했습니다."),
            PlexException("컬렉션 변경에는 Plex 메타데이터 편집 권한이 필요합니다."),
            PlexException("Plex 서버 응답 오류 (500)"), IllegalStateException("network"))) {
            assertTrue(watchedCollectionFailureNotice(error)!!.contains(error.message!!))
        }
    }
    @Test fun automaticNoticePolicyPreservesCancellation() {
        val expected = CancellationException("cancel")
        try {
            watchedCollectionFailureNotice(expected)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
    @Test fun savesWatchedBeforeReplacingCollection() = runBlocking {
        for (tag in listOf("KILL", "123")) {
            val events = mutableListOf<String>()
            val result = saveWatchedWithCollection({ events.add("watched"); Unit }, {
                events.add("collection"); tag
            })
            assertEquals(listOf("watched", "collection"), events)
            assertTrue(result!!.contains("$tag 하나"))
        }
    }
    @Test fun unmanagedItemKeepsNormalNotice() = runBlocking {
        assertNull(saveWatchedWithCollection({}, { null }))
    }
    @Test fun failedWatchedDoesNotChangeCollection() = runBlocking {
        var collectionCalled = false
        try {
            saveWatchedWithCollection({ throw IllegalStateException("watched failed") }, {
                collectionCalled = true; "KILL"
            })
            fail("Expected watched failure")
        } catch (expected: IllegalStateException) {
            assertEquals("watched failed", expected.message)
        }
        assertFalse(collectionCalled)
    }
    @Test fun failedCollectionPreservesSuccessfulWatchedAndReportsPartialSuccess() = runBlocking {
        var saved = false
        val result = saveWatchedWithCollection({ saved = true }, { throw IllegalStateException("denied") })
        assertTrue(saved)
        assertTrue(result!!.contains("시청 완료는 저장됐지만"))
        assertTrue(result.contains("denied"))
    }
    @Test fun cancellationIsNotReportedAsSuccess() = runBlocking {
        try {
            saveWatchedWithCollection({}, { throw CancellationException("cancel") })
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("cancel", expected.message)
        }
    }
}
