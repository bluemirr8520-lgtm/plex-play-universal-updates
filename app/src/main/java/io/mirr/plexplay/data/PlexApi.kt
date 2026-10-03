package io.mirr.plexplay.data

import io.mirr.plexplay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlexApi(
    private val connection: PlexConnection,
    private val clientIdentifier: String,
    private val backgroundLookup: Boolean = false,
) {
    suspend fun server(): PlexServer = request("/") { PlexXmlParser.server(it) }

    suspend fun sections(): List<PlexSection> =
        request("/library/sections") { PlexXmlParser.sections(it) }

    suspend fun sectionItems(sectionKey: String): List<PlexItem> {
        val pageSize = 500
        val result = mutableListOf<PlexItem>()
        var start = 0
        while (true) {
            val page = request(
                path = "/library/sections/$sectionKey/all",
                query = mapOf(
                    "sort" to "titleSort:asc",
                    "includeMedia" to "1",
                    "X-Plex-Container-Start" to start.toString(),
                    "X-Plex-Container-Size" to pageSize.toString(),
                ),
            ) { PlexXmlParser.items(it) }
            if (page.isEmpty()) break
            result += page
            if (page.size < pageSize) break
            start += page.size
        }
        return result.distinctBy { "${it.type}-${it.ratingKey}-${it.key}" }
    }

    suspend fun recentlyAdded(
        sectionKey: String,
        sectionType: String,
    ): List<PlexItem> =
        request(
            path = if (sectionType == "show") {
                "/library/sections/$sectionKey/all"
            } else {
                "/library/sections/$sectionKey/recentlyAdded"
            },
            query = buildMap {
                put("includeMedia", "1")
                put("X-Plex-Container-Start", "0")
                put("X-Plex-Container-Size", "20")
                if (sectionType == "show") {
                    put("type", "2")
                    put("sort", "addedAt:desc")
                }
            },
        ) { PlexXmlParser.items(it) }

    suspend fun onDeck(): List<PlexItem> =
        request(
            path = "/library/onDeck",
            query = mapOf(
                "includeMedia" to "1",
                "X-Plex-Container-Start" to "0",
                "X-Plex-Container-Size" to "30",
            ),
        ) { PlexXmlParser.items(it) }

    /** Latest added episode, not the highest episode number or the next unwatched one. */
    suspend fun latestEpisode(show: PlexItem): PlexItem? {
        if (show.type != "show" || !show.ratingKey.matches(Regex("[0-9]+"))) return null
        return request(
            path = "/library/metadata/${show.ratingKey}/allLeaves",
            query = mapOf(
                "sort" to "addedAt:desc",
                "includeMedia" to "0",
                "X-Plex-Container-Start" to "0",
                "X-Plex-Container-Size" to "1",
            ),
        ) { PlexXmlParser.items(it) }.firstOrNull {
            it.type == "episode" && it.grandparentRatingKey == show.ratingKey &&
                (it.librarySectionId == null || show.librarySectionId == null ||
                    it.librarySectionId == show.librarySectionId)
        }
    }

    suspend fun search(term: String): List<PlexItem> =
        request(
            path = "/hubs/search",
            query = mapOf(
                "query" to term,
                "limit" to "100",
                "includeMedia" to "1",
            ),
        ) { PlexXmlParser.items(it) }
            .filter { item ->
                item.ratingKey.isNotBlank() &&
                    item.key.isNotBlank() &&
                    (
                        item.isPlayable ||
                            item.type in setOf(
                                "show",
                                "season",
                                "artist",
                                "album",
                                "photoalbum",
                                "collection",
                            )
                        )
            }
            .distinctBy { "${it.type}-${it.ratingKey}-${it.key}" }

    suspend fun sectionOnDeck(sectionKey: String): List<PlexItem> =
        request(
            path = "/library/sections/$sectionKey/onDeck",
            query = mapOf(
                "includeMedia" to "1",
                "X-Plex-Container-Start" to "0",
                "X-Plex-Container-Size" to "30",
            ),
        ) { PlexXmlParser.items(it) }

    suspend fun watched(
        sectionKey: String,
        sectionType: String,
    ): List<PlexItem> =
        request(
            path = "/library/sections/$sectionKey/all",
            query = buildMap {
                put("unwatched", "0")
                put("sort", "lastViewedAt:desc")
                put("includeMedia", "1")
                put("X-Plex-Container-Start", "0")
                put("X-Plex-Container-Size", "20")
                if (sectionType == "show") put("type", "2")
            },
        ) { PlexXmlParser.items(it) }

    suspend fun setWatched(ratingKey: String, watched: Boolean) {
        request<Unit>(
            path = if (watched) "/:/scrobble" else "/:/unscrobble",
            query = mapOf(
                "identifier" to "com.plexapp.plugins.library",
                "key" to ratingKey,
            ),
        ) { }
    }

    suspend fun removeFromContinueWatching(ratingKey: String) {
        request<Unit>(
            path = "/actions/removeFromContinueWatching",
            query = mapOf("ratingKey" to ratingKey),
            method = "PUT",
        ) { }
    }

    suspend fun setWatchedAndVerify(item: PlexItem, watched: Boolean): PlexItem {
        setWatched(item.ratingKey, watched)
        // Retry only the readback: never repeatedly mark a whole series watched.
        repeat(3) { attempt ->
            if (attempt > 0) delay(150L * attempt)
            val saved = metadata(item.ratingKey).singleOrNull { it.ratingKey == item.ratingKey }
            if (saved != null &&
                (item.librarySectionId == null || saved.librarySectionId == item.librarySectionId) &&
                saved.matchesSavedWatchedState(watched)
            ) return saved
        }
        throw PlexException("Plex 서버의 시청 상태 저장 결과를 확인하지 못했습니다. 새로고침 후 다시 확인해 주세요.")
    }

    suspend fun seriesEpisodes(ratingKey: String): List<PlexItem> {
        if (!ratingKey.matches(Regex("[0-9]+"))) throw PlexException("시리즈 식별자가 올바르지 않습니다.")
        return loadAllSeriesEpisodes { start ->
            request(
                path = "/library/metadata/$ratingKey/allLeaves",
                query = mapOf(
                    "includeMedia" to "1",
                    "X-Plex-Container-Start" to start.toString(),
                    "X-Plex-Container-Size" to "200",
                ),
            ) { PlexXmlParser.items(it) }
        }
    }

    suspend fun replaceCollectionTag(
        sectionId: String,
        ratingKey: String,
        mediaType: String,
        tag: String,
        existingCollections: List<String>,
    ) {
        val plexType = when (mediaType) {
            "movie" -> "1"
            "show" -> "2"
            "clip" -> "12"
            else -> throw PlexException("이 영상 유형에는 컬렉션 태그를 적용할 수 없습니다.")
        }
        if (!sectionId.matches(Regex("[0-9]+")) || !ratingKey.matches(Regex("[0-9]+"))) {
            throw PlexException("컬렉션을 변경할 라이브러리 또는 영상 식별자가 올바르지 않습니다.")
        }
        if (tag.isBlank()) throw PlexException("적용할 컬렉션 태그가 비어 있습니다.")

        // Indexed tags append in PMS. Remove every other existing collection
        // explicitly; disjoint add/remove names make processing order irrelevant.
        val removedTags = existingCollections.filter { it.isNotBlank() && it != tag }.distinct()
        if (existingCollections.toSet() != setOf(tag)) request<Unit>(
            path = "/library/sections/$sectionId/all",
            query = buildMap {
                put("id", ratingKey)
                put("type", plexType)
                put("collection.locked", "1")
                put("collection[0].tag.tag", tag)
                if (removedTags.isNotEmpty()) {
                    // PMS expects separately quoted tag names inside its comma
                    // list, before the whole query parameter is URL-encoded.
                    put("collection[].tag.tag-", removedTags.joinToString(",") { quoteCollectionTag(it) })
                }
            },
            method = "PUT",
            forbiddenMessage = "컬렉션 변경에는 Plex 메타데이터 편집 권한이 필요합니다. 서버 소유자 계정을 확인해 주세요.",
            collectionEdit = true,
        ) { }

        val updated = metadata(ratingKey).singleOrNull { it.ratingKey == ratingKey }
        if (updated == null || updated.librarySectionId != sectionId || updated.collections.toSet() != setOf(tag)) {
            throw PlexException("Plex 컬렉션 변경 결과를 확인하지 못했습니다. 지정한 컬렉션만 적용되지 않았습니다.")
        }
    }

    suspend fun children(path: String): List<PlexItem> =
        request(
            path = path.ensurePath(),
            query = mapOf("includeMedia" to "1"),
        ) { PlexXmlParser.items(it) }

    suspend fun playbackCandidates(path: String, episodeSection: Boolean = false): List<PlexItem> =
        loadPlaybackCandidatePages { start ->
            request(
                path = path.ensurePath(),
                query = buildMap {
                    put("includeMedia", "1")
                    put("X-Plex-Container-Start", start.toString())
                    put("X-Plex-Container-Size", "200")
                    if (path.startsWith("/library/sections/")) {
                        put("sort", "titleSort:asc")
                        if (episodeSection) put("type", "4")
                    }
                },
            ) { PlexXmlParser.items(it) }
        }

    suspend fun metadata(ratingKey: String): List<PlexItem> =
        request(
            path = "/library/metadata/$ratingKey",
            query = mapOf(
                "includeMedia" to "1",
                "includeExternalMedia" to "1",
                "includeStreams" to "1",
            ),
        ) { PlexXmlParser.items(it) }

    suspend fun filteredSectionItems(
        sectionKey: String,
        filter: String,
        limit: Int = 40,
    ): List<PlexItem> {
        val separator = filter.indexOf('=')
        if (separator <= 0 || separator == filter.lastIndex) return emptyList()
        val filterName = filter.substring(0, separator)
        val filterValue = filter.substring(separator + 1)
        return request(
            path = "/library/sections/$sectionKey/all",
            query = mapOf(
                filterName to filterValue,
                "sort" to "titleSort:asc",
                // Related cards fetch fresh Part/Stream metadata only when played.
                "includeMedia" to "0",
                "X-Plex-Container-Start" to "0",
                "X-Plex-Container-Size" to limit.toString(),
            ),
        ) { PlexXmlParser.items(it) }
    }

    suspend fun timeline(
        ratingKey: String,
        key: String,
        state: String,
        timeMs: Long,
        durationMs: Long,
    ) {
        request<Unit>(
            path = "/:/timeline",
            query = mapOf(
                "ratingKey" to ratingKey,
                "key" to key,
                "state" to state,
                "time" to timeMs.toString(),
                "duration" to durationMs.toString(),
            ),
        ) { }
    }

    fun absoluteUrl(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            connection.baseUrl.trimEnd('/') + path.ensurePath()
        }

    private suspend fun <T> request(
        path: String,
        query: Map<String, String> = emptyMap(),
        method: String = "GET",
        forbiddenMessage: String? = null,
        collectionEdit: Boolean = false,
        parse: (InputStream) -> T,
    ): T = withContext(Dispatchers.IO) {
        val queryString = query.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        val rawUrl = absoluteUrl(path) + if (queryString.isBlank()) "" else "?$queryString"
        val url = runCatching { URI(rawUrl).toURL() }
            .getOrElse { throw PlexException("서버 주소가 올바르지 않습니다.", it) }

        var lastError: Exception? = null
        // A PUT may have succeeded even if its response was lost. Never replay
        // metadata changes automatically; let the caller report partial success.
        val attempts = if (method == "GET" && !backgroundLookup) 2 else 1
        for (attempt in 0 until attempts) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(url)
                .method(method, if (method == "GET") null else ByteArray(0).toRequestBody())
                .header("Accept", "application/xml")
                .header("Accept-Encoding", "identity")
                .header("Connection", "close")
                .header("X-Plex-Token", connection.token)
                .header("X-Plex-Product", "Plex Play Universal")
                .header("X-Plex-Version", BuildConfig.VERSION_NAME)
                .header("X-Plex-Platform", "Android")
                .header("X-Plex-Client-Identifier", clientIdentifier)
                .build()
            val call = (if (backgroundLookup) backgroundClient else metadataClient).newCall(request)
            try {
                return@withContext suspendCancellableCoroutine<T> { continuation ->
                    // cancel() closes the socket without taking a blocking
                    // HttpURLConnection read lock on the UI/cancellation thread.
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, error: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val result = response.use {
                                    if (!continuation.isActive) return
                                    val status = it.code
                                    if (status !in 200..299) {
                                        val message = when (status) {
                                            401 -> "Plex 계정 인증에 실패했습니다."
                                            403 -> forbiddenMessage ?: "Plex 서버 접근 권한이 없습니다."
                                            404 -> "요청한 Plex 콘텐츠를 찾지 못했습니다."
                                            else -> "Plex 서버 응답 오류 ($status)"
                                        }
                                        throw PlexException(message,
                                            collectionPermissionDenied = collectionEdit &&
                                                connection.isServerOwner != true && status in setOf(401, 403))
                                    }
                                    val body = it.body ?: throw IOException("Empty metadata response")
                                    BufferedInputStream(body.byteStream()).use(parse)
                                }
                                if (continuation.isActive) continuation.resume(result)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: PlexException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                if (attempt == attempts - 1) break
            }
        }
        throw PlexException(
            "Plex 서버 응답이 중간에 끊겼습니다. 서버 주소와 네트워크를 확인해 주세요.",
            lastError,
        )
    }

    private fun String.ensurePath(): String = if (startsWith("/")) this else "/$this"
    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun quoteCollectionTag(value: String): String =
        encode(value).replace("+", "%20").replace("%7E", "~").replace("*", "%2A").replace("%2F", "/")

    companion object {
        private val metadataClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).build()
        // A separate queue ensures optional discovery never occupies playback
        // metadata's per-host dispatch slots. Existing GET/PUT retry policy stays above.
        private val backgroundClient = metadataClient.newBuilder()
            .dispatcher(Dispatcher().apply { maxRequests = 8; maxRequestsPerHost = 4 })
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS).build()
    }
}
