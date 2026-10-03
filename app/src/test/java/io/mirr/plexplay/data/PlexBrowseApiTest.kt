package io.mirr.plexplay.data

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PlexBrowseApiTest {
    private fun show() = PlexItem("42", "/library/metadata/42", "show", "Series", librarySectionId = "7")
    private fun episode(parent: String = "42", section: String = "7") =
        """<MediaContainer librarySectionID="$section"><Video ratingKey="50" key="/library/metadata/50" type="episode" title="Latest" grandparentRatingKey="$parent" parentIndex="2" index="8"/></MediaContainer>"""

    @Test fun latestEpisodeFetchIsOneSmallReadOnlySortedRequest() = runBlocking {
        Fixture(episode()).use { server ->
            val result = server.api.latestEpisode(show())!!
            assertEquals("최신 · S2:E8 · Latest", formatLatestEpisodeLabel(result))
            val request = server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/library/metadata/42/allLeaves", request.path)
            assertEquals("addedAt:desc", request.query["sort"])
            assertEquals("1", request.query["X-Plex-Container-Size"])
            assertEquals("0", request.query["includeMedia"])
        }
    }

    @Test fun latestEpisodeDoesNotLeakAnotherSeriesOrLibrary() = runBlocking {
        for (xml in listOf(episode("43"), episode(section = "8"), "<MediaContainer/>")) {
            Fixture(xml).use { assertNull(it.api.latestEpisode(show())) }
        }
    }

    @Test fun movieOrInvalidSeriesDoesNotStartEpisodeLookup() = runBlocking {
        Fixture(episode()).use {
            assertNull(it.api.latestEpisode(show().copy(type = "movie")))
            assertNull(it.api.latestEpisode(show().copy(ratingKey = "42/../43")))
            assertTrue(it.requests.isEmpty())
        }
    }

    @Test fun relatedCardsUseSmallPagesWithoutPlaybackMediaAndKeepFilterEncoding() = runBlocking {
        Fixture("<MediaContainer/>").use {
            it.api.filteredSectionItems("7", "actor=12", 24)
            val request = it.requests.single()
            assertEquals("/library/sections/7/all", request.path)
            assertEquals("12", request.query["actor"])
            assertEquals("24", request.query["X-Plex-Container-Size"])
            assertEquals("0", request.query["includeMedia"])
            assertEquals("GET", request.method)
        }
    }

    @Test fun cancellingRelatedHttpDoesNotWaitForReadTimeoutOrRetry() = runBlocking {
        Fixture("<MediaContainer/>", stall = true).use { server ->
            val lookup = async { server.api.filteredSectionItems("7", "actor=12") }
            withTimeout(2_000) { server.stalled.await() }
            val started = System.nanoTime()
            withTimeout(2_000) { lookup.cancelAndJoin() }
            assertTrue("Cancellation took longer than 1.5 seconds", (System.nanoTime() - started) / 1_000_000 < 1_500)
            assertEquals(1, server.requests.size)
        }
    }

    @Test fun playbackMetadataRemainsAvailableWhileBackgroundLookupIsStalled() = runBlocking {
        Fixture(episode(), stall = true).use { server ->
            val lookup = async { server.api.filteredSectionItems("7", "actor=12") }
            withTimeout(2_000) { server.stalled.await() }
            val metadata = withTimeout(2_000) { server.api.metadata("50") }
            assertEquals("50", metadata.single().ratingKey)
            assertFalse(lookup.isCompleted)
            lookup.cancelAndJoin()
            assertEquals(2, server.requests.size)
        }
    }

    @Test fun fullOptionalRequestQueueDoesNotBlockPlaybackMetadata() = runBlocking {
        Fixture(episode(), stall = true).use { server ->
            val lookups = (1..4).map { id -> async { server.api.filteredSectionItems("7", "actor=$id") } }
            try {
                withTimeout(2_000) { while (server.requests.size < 4) delay(5) }
                val metadata = withTimeout(2_000) { server.playbackApi.metadata("50") }
                assertEquals("50", metadata.single().ratingKey)
                assertTrue(lookups.all { !it.isCompleted })
            } finally {
                lookups.forEach { it.cancelAndJoin() }
            }
            assertEquals(5, server.requests.size)
        }
    }

    private data class Request(val method: String, val path: String, val query: Map<String, String>)

    private class Fixture(private val body: String, private val stall: Boolean = false) : AutoCloseable {
        private val listener = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val requests = CopyOnWriteArrayList<Request>()
        val stalled = CompletableDeferred<Unit>()
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val workers = CopyOnWriteArrayList<Thread>()
        @Volatile private var running = true
        val api = PlexApi(PlexConnection("http://127.0.0.1:${listener.localPort}", "fixture-only"), "browse-test", backgroundLookup = true)
        val playbackApi = PlexApi(PlexConnection("http://127.0.0.1:${listener.localPort}", "fixture-only"), "playback-test")
        private val acceptor = thread(isDaemon = true, name = "plex-browse-fixture") {
            while (running) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                sockets += socket
                workers += thread(isDaemon = true) {
                    try { socket.use { respond(it) } } catch (_: Exception) { /* A cancelled client closes its socket. */ }
                    finally { sockets.remove(socket) }
                }
            }
        }

        private fun respond(socket: Socket) {
            socket.soTimeout = 3_000
            val reader = socket.getInputStream().bufferedReader()
            val parts = reader.readLine().split(' ')
            while (reader.readLine().orEmpty().isNotEmpty()) { }
            val target = parts[1]
            val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
                val keyValue = it.split('=', limit = 2)
                URLDecoder.decode(keyValue[0], "UTF-8") to URLDecoder.decode(keyValue.getOrElse(1) { "" }, "UTF-8")
            }
            val request = Request(parts[0], target.substringBefore('?'), query)
            requests += request
            val bytes = body.toByteArray(Charsets.UTF_8)
            val output = socket.getOutputStream()
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
            if (stall && request.path.endsWith("/all")) {
                output.flush()
                stalled.complete(Unit)
                // No response body until the client disconnects: unlike a sleep,
                // this fixture proves cancellation really closes the connection.
                socket.getInputStream().read()
            } else {
                output.write(bytes)
                output.flush()
            }
        }

        override fun close() {
            running = false
            listener.close()
            sockets.forEach { runCatching { it.close() } }
            acceptor.join(1_000)
            workers.forEach { it.join(1_000) }
            check(!acceptor.isAlive && workers.none { it.isAlive })
        }
    }
}
