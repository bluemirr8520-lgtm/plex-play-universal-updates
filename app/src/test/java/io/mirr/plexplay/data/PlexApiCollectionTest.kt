package io.mirr.plexplay.data

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull

class PlexApiCollectionTest {
    @Test fun userDefinedCollectionNameIsEncodedAndVerifiedWithoutHardcodedTags() = runBlocking {
        val name = "시청 완료, A+B & C"
        val xml = metadata("시청 완료, A+B &amp; C")
        TestServer(metadata = xml).use { server ->
            server.api.replaceCollectionTag("7", "42", "movie", name, listOf("KILL", "123"))
            assertEquals(name, server.requests.first().query["collection[0].tag.tag"])
            assertEquals("KILL,123", server.requests.first().query["collection[].tag.tag-"])
        }
    }
    private fun seriesItem() = PlexItem("42", "/library/metadata/42", "show", "Series", librarySectionId = "7")

    private fun seriesMetadata(viewed: Int, sectionId: String = "7") =
        """<MediaContainer librarySectionID="$sectionId"><Directory ratingKey="42" key="/library/metadata/42" type="show" leafCount="2" viewedLeafCount="$viewed"/></MediaContainer>"""

    @Test fun seriesWatchedReadsBackEpisodeCountersRatherThanInventingViewCount() = runBlocking {
        TestServer(script = listOf(200 to "", 200 to seriesMetadata(2))).use { server ->
            val saved = server.api.setWatchedAndVerify(seriesItem(), true)
            assertTrue(saved.isWatched)
            assertEquals(0, saved.viewCount)
            assertEquals(2, saved.viewedLeafCount)
            assertEquals(listOf("/:/scrobble", "/library/metadata/42"), server.requests.map { it.path })
            assertEquals("42", server.requests.first().query["key"])
        }
    }

    @Test fun delayedCountersRetryOnlyReadbackNeverTheSeriesWrite() = runBlocking {
        TestServer(script = listOf(200 to "", 200 to seriesMetadata(0), 200 to seriesMetadata(2))).use { server ->
            assertTrue(server.api.setWatchedAndVerify(seriesItem(), true).isWatched)
            assertEquals(listOf("/:/scrobble", "/library/metadata/42", "/library/metadata/42"), server.requests.map { it.path })
        }
    }

    @Test fun unpersistedOrWrongLibraryCountersDoNotClaimWatchedSuccess() = runBlocking {
        for (metadata in listOf(seriesMetadata(1), seriesMetadata(2, "8"))) {
            TestServer(script = listOf(200 to "") + List(3) { 200 to metadata }).use { server ->
                expectPlexFailure { server.api.setWatchedAndVerify(seriesItem(), true) }
                assertEquals(1, server.requests.count { it.path == "/:/scrobble" })
                assertEquals(3, server.requests.count { it.path == "/library/metadata/42" })
            }
        }
    }

    @Test fun seriesUnwatchedAlsoUsesVerifiedServerCounters() = runBlocking {
        TestServer(script = listOf(200 to "", 200 to seriesMetadata(0))).use { server ->
            val saved = server.api.setWatchedAndVerify(seriesItem(), false)
            assertFalse(saved.isWatched)
            assertEquals(0, saved.viewedLeafCount)
            assertEquals("/:/unscrobble", server.requests.first().path)
        }
    }

    @Test fun seriesEpisodeApiFetchesEveryPageIncludingShortServerPages() = runBlocking {
        fun page(id: String) = """<MediaContainer librarySectionID="7"><Video ratingKey="$id" key="/library/metadata/$id" type="episode" grandparentRatingKey="42" viewCount="1"><Media><Part file="/TV/$id.mkv" key="/part/$id"/></Media></Video></MediaContainer>"""
        TestServer(script = listOf(200 to page("50"), 200 to page("51"), 200 to "<MediaContainer/>" )).use { server ->
            val episodes = server.api.seriesEpisodes("42")
            assertEquals(listOf("50", "51"), episodes.map { it.ratingKey })
            assertEquals(listOf("0", "1", "2"), server.requests.map { it.query["X-Plex-Container-Start"] })
            assertTrue(server.requests.all { it.path == "/library/metadata/42/allLeaves" && it.query["includeMedia"] == "1" })
            assertEquals(listOf("/TV/50.mkv"), episodes.first().mediaFilePaths)
        }
    }

    @Test
    fun replacementUsesOneDisjointPutAndVerifiesTheSameItem() = runBlocking {
        TestServer(metadata = metadata("KILL")).use { server ->
            server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("First", "KILL", "Second", "First"))

            val write = server.requests[0]
            assertEquals("PUT", write.method)
            assertEquals("/library/sections/7/all", write.path)
            assertEquals(
                mapOf(
                    "id" to "42", "type" to "1", "collection.locked" to "1",
                    "collection[0].tag.tag" to "KILL", "collection[].tag.tag-" to "First,Second",
                ),
                write.query,
            )
            assertEquals("test-token", write.token)
            assertFalse(write.rawQuery.contains("test-token"))
            assertEquals(2, server.requests.size)
            assertEquals("GET", server.requests[1].method)
            assertEquals("/library/metadata/42", server.requests[1].path)
        }
    }

    @Test
    fun removalQuotesEachNameBeforeEncodingTheCommaSeparatedQuery() = runBlocking {
        val names = listOf("One, Two", "A+B & C", "한글 / 기호*~")
        TestServer(metadata = metadata("123")).use { server ->
            server.api.replaceCollectionTag("7", "42", "movie", "123", names)
            val write = server.requests.first()
            assertEquals(
                "One%2C%20Two,A%2BB%20%26%20C,%ED%95%9C%EA%B8%80%20/%20%EA%B8%B0%ED%98%B8%2A~",
                write.query["collection[].tag.tag-"],
            )
            assertTrue(write.rawQuery.contains("One%252C%2520Two"))
            assertEquals(names, write.query.getValue("collection[].tag.tag-").split(',').map(::decode))
        }
    }

    @Test
    fun emptyExistingCollectionsDoesNotSendAnyClearOrRemovalParameter() = runBlocking {
        TestServer(metadata = metadata("KILL")).use { server ->
            server.api.replaceCollectionTag("7", "42", "movie", "KILL", emptyList())
            val query = server.requests.first().query
            assertEquals(setOf("id", "type", "collection.locked", "collection[0].tag.tag"), query.keys)
        }
    }

    @Test
    fun duplicateTargetNeverAppearsInTheRemovalSet() = runBlocking {
        TestServer(metadata = metadata("KILL")).use { server ->
            server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("KILL", "KILL", " "))
            assertFalse(server.requests.first().query.containsKey("collection[].tag.tag-"))
        }
    }

    @Test
    fun supportsPlayableVideoAndSeriesTypes() = runBlocking {
        for ((type, number) in listOf("movie" to "1", "show" to "2", "episode" to "4", "clip" to "12")) {
            TestServer(metadata = metadata("123", type = type)).use { server ->
                server.api.replaceCollectionTag("7", "42", type, "123", emptyList())
                assertEquals(number, server.requests.first().query["type"])
            }
        }
    }

    @Test
    fun rejectsUnknownTypeAndUnsafeIdentifiersBeforeConnecting() = runBlocking {
        TestServer(metadata = metadata("KILL")).use { server ->
            for (type in listOf("season", "track", "video", "unknown", "")) {
                expectPlexFailure { server.api.replaceCollectionTag("7", "42", type, "KILL", emptyList()) }
            }
            expectPlexFailure { server.api.replaceCollectionTag("7/all", "42", "movie", "KILL", emptyList()) }
            expectPlexFailure { server.api.replaceCollectionTag("7", "42,43", "movie", "KILL", emptyList()) }
            expectPlexFailure { server.api.replaceCollectionTag("7", "42", "movie", " ", emptyList()) }
            assertTrue(server.requests.isEmpty())
        }
    }

    @Test
    fun failsVerificationIfOldCollectionsRemainOrTheWrongItemIsReturned() = runBlocking {
        for (response in listOf(metadata("KILL", "Old"), metadata("Old"), metadata("KILL", ratingKey = "43"))) {
            TestServer(metadata = response).use { server ->
                val error = expectPlexFailure {
                    server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("Old"))
                }
                assertTrue(error.message.orEmpty().contains("변경 결과"))
                assertEquals(2, server.requests.size)
            }
        }
    }

    @Test
    fun authenticationAndPermissionFailuresDoNotRetryOrClaimSuccess() = runBlocking {
        for ((status, expectedMessage) in listOf(401 to "인증", 403 to "메타데이터 편집 권한")) {
            TestServer(metadata = metadata("KILL"), writeStatus = status).use { server ->
                val error = expectPlexFailure {
                    server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("Old"))
                }
                assertTrue(error.message.orEmpty().contains(expectedMessage))
                assertTrue(error.collectionPermissionDenied)
                assertNull(watchedCollectionFailureNotice(error))
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test fun alreadyCorrectCollectionIsReadBackWithoutAnotherPut() = runBlocking {
        TestServer(metadata = metadata("KILL"), writeExpected = false).use { server ->
            server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("KILL"))
            assertEquals(1, server.requests.size)
            assertEquals("GET", server.requests.single().method)
            assertEquals("/library/metadata/42", server.requests.single().path)
        }
    }

    @Test fun alreadyCorrectSnapshotIsNotTrustedIfServerHasChanged() = runBlocking {
        TestServer(metadata = metadata("Old"), writeExpected = false).use { server ->
            expectPlexFailure { server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("KILL")) }
            assertEquals(listOf("GET"), server.requests.map { it.method })
        }
    }

    @Test fun verificationRejectsAnotherLibraryEvenWithTheSameTag() = runBlocking {
        TestServer(metadata = metadata("KILL", sectionId = "8")).use { server ->
            expectPlexFailure { server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("Old")) }
            assertEquals(listOf("PUT", "GET"), server.requests.map { it.method })
        }
    }

    @Test fun serverFailureDoesNotRepeatCollectionWrites() = runBlocking {
        for (status in listOf(500, 503)) {
            TestServer(metadata = metadata("KILL"), writeStatus = status).use { server ->
                val error = expectPlexFailure { server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("Old")) }
                assertFalse(error.collectionPermissionDenied)
                assertNotNull(watchedCollectionFailureNotice(error))
                assertEquals(listOf("PUT"), server.requests.map { it.method })
            }
        }
    }

    @Test fun confirmedOwnerPermissionFailuresRemainVisible() = runBlocking {
        for (status in listOf(401, 403)) {
            TestServer(writeStatus = status, isServerOwner = true).use { server ->
                val error = expectPlexFailure { server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("Old")) }
                assertFalse(error.collectionPermissionDenied)
                assertNotNull(watchedCollectionFailureNotice(error))
                assertEquals(listOf("PUT"), server.requests.map { it.method })
            }
        }
    }

    @Test fun watchedAndMetadataPermissionErrorsAreNeverOptional() = runBlocking {
        for (status in listOf(401, 403)) {
            TestServer(script = listOf(status to "")).use { server ->
                val error = expectPlexFailure { server.api.setWatched("42", true) }
                assertFalse(error.collectionPermissionDenied)
                assertEquals("/:/scrobble", server.requests.single().path)
            }
            TestServer(script = listOf(200 to "", status to "")).use { server ->
                val error = expectPlexFailure { server.api.replaceCollectionTag("7", "42", "movie", "KILL", listOf("Old")) }
                assertFalse(error.collectionPermissionDenied)
                assertNotNull(watchedCollectionFailureNotice(error))
                assertEquals(listOf("PUT", "GET"), server.requests.map { it.method })
            }
        }
    }

    private suspend fun expectPlexFailure(block: suspend () -> Unit): PlexException {
        try {
            block()
            fail("Expected PlexException")
        } catch (error: PlexException) {
            return error
        }
        error("Expected failure")
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val rawQuery: String,
        val query: Map<String, String>,
        val token: String?,
    )

    private class TestServer(metadata: String = "", writeStatus: Int = 200, writeExpected: Boolean = true,
        script: List<Pair<Int, String>>? = null, isServerOwner: Boolean? = null) : AutoCloseable {
        val requests = CopyOnWriteArrayList<CapturedRequest>()
        private val responses = ConcurrentLinkedQueue(
            script ?: if (writeExpected) listOf(writeStatus to "", 200 to metadata) else listOf(200 to metadata),
        )
        private val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        @Volatile private var running = true
        @Volatile private var activeSocket: Socket? = null
        @Volatile private var serverFailure: Throwable? = null
        private val worker = thread(isDaemon = true, name = "plex-collection-http-fixture") {
            try {
                while (running) {
                    server.accept().use { socket ->
                        activeSocket = socket
                        if (!running) return@use
                        socket.soTimeout = 3_000
                        respond(socket)
                        activeSocket = null
                    }
                }
            } catch (error: Throwable) {
                if (running) serverFailure = error
            } finally {
                activeSocket?.close()
                activeSocket = null
            }
        }
        val api = PlexApi(PlexConnection("http://127.0.0.1:${server.localPort}", "test-token", isServerOwner), "collection-test")

        private fun respond(socket: Socket) {
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val requestLine = reader.readLine() ?: error("Missing HTTP request line")
                val requestParts = requestLine.split(' ', limit = 3)
                check(requestParts.size == 3) { "Malformed HTTP request line" }
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: error("Incomplete HTTP request headers")
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    check(colon > 0) { "Malformed HTTP request header" }
                    headers[line.substring(0, colon).lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
                }
                // The production API sends bodyless GET/PUT requests. Keep this
                // fixture strict so an unexpected transport change is visible.
                check(headers["content-length"].orEmpty().ifEmpty { "0" } == "0") { "Unexpected HTTP request body" }
                check("transfer-encoding" !in headers) { "Unexpected chunked HTTP request" }
                val target = requestParts[1]
                val raw = target.substringAfter('?', "")
                val query = raw.split('&').filter(String::isNotEmpty).associate { argument ->
                    val parts = argument.split('=', limit = 2)
                    decode(parts[0]) to decode(parts.getOrElse(1) { "" })
                }
                requests += CapturedRequest(
                    requestParts[0], target.substringBefore('?'), raw, query, headers["x-plex-token"],
                )
                val (status, body) = responses.poll() ?: (500 to "Unexpected request")
                val bytes = body.toByteArray(Charsets.UTF_8)
                val reason = when (status) {
                    200 -> "OK"
                    401 -> "Unauthorized"
                    403 -> "Forbidden"
                    else -> "Internal Server Error"
                }
                val responseHeaders = "HTTP/1.1 $status $reason\r\n" +
                    "Content-Type: application/xml; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                socket.getOutputStream().apply {
                    write(responseHeaders.toByteArray(Charsets.US_ASCII))
                    write(bytes)
                    flush()
                }
        }

        override fun close() {
            running = false
            try {
                server.close()
                activeSocket?.close()
            } finally {
                worker.join(2_000)
            }
            check(!worker.isAlive) { "HTTP fixture worker did not stop" }
            serverFailure?.let { throw AssertionError("HTTP fixture failed", it) }
        }
    }

    companion object {
        private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

        private fun metadata(vararg tags: String, ratingKey: String = "42", type: String = "movie", sectionId: String = "7"): String =
            """<MediaContainer size="1" librarySectionID="$sectionId"><Video ratingKey="$ratingKey" key="/library/metadata/$ratingKey" type="$type" title="Fixture">""" +
                tags.joinToString("") { """<Collection tag="$it"/>""" } +
                """<Genre tag="Unchanged Genre"/><Role tag="Unchanged Actor"/></Video></MediaContainer>"""
    }
}
