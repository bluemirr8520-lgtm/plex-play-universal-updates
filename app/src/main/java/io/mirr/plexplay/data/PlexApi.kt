package io.mirr.plexplay.data

import io.mirr.plexplay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class PlexApi(
    private val connection: PlexConnection,
    private val clientIdentifier: String,
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

    suspend fun children(path: String): List<PlexItem> =
        request(
            path = path.ensurePath(),
            query = mapOf("includeMedia" to "1"),
        ) { PlexXmlParser.items(it) }

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
                "includeMedia" to "1",
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
        parse: (InputStream) -> T,
    ): T = withContext(Dispatchers.IO) {
        val queryString = query.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        val rawUrl = absoluteUrl(path) + if (queryString.isBlank()) "" else "?$queryString"
        val url = runCatching { URI(rawUrl).toURL() }
            .getOrElse { throw PlexException("서버 주소가 올바르지 않습니다.", it) }

        var lastError: Exception? = null
        for (attempt in 0..1) {
            val http = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                requestMethod = method
                useCaches = false
                setRequestProperty("Accept", "application/xml")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "close")
                setRequestProperty("X-Plex-Token", this@PlexApi.connection.token)
                setRequestProperty("X-Plex-Product", "Plex Play Universal")
                setRequestProperty("X-Plex-Version", BuildConfig.VERSION_NAME)
                setRequestProperty("X-Plex-Platform", "Android")
                setRequestProperty("X-Plex-Client-Identifier", clientIdentifier)
            }
            try {
                val status = http.responseCode
                if (status !in 200..299) {
                    val message = when (status) {
                        401 -> "Plex 계정 인증에 실패했습니다."
                        404 -> "요청한 Plex 콘텐츠를 찾지 못했습니다."
                        else -> "Plex 서버 응답 오류 ($status)"
                    }
                    throw PlexException(message)
                }
                return@withContext BufferedInputStream(http.inputStream).use(parse)
            } catch (error: PlexException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                if (attempt == 1) break
            } finally {
                http.disconnect()
            }
        }
        throw PlexException(
            "Plex 서버 응답이 중간에 끊겼습니다. 서버 주소와 네트워크를 확인해 주세요.",
            lastError,
        )
    }

    private fun String.ensurePath(): String = if (startsWith("/")) this else "/$this"
    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
