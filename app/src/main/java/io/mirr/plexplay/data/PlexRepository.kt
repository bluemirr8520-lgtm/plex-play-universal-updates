package io.mirr.plexplay.data

import io.mirr.plexplay.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.net.URLEncoder
import java.util.UUID

class PlexRepository(
    private val store: ConnectionStore,
) {
    fun connection(): PlexConnection = store.load()

    fun saveConnection(connection: PlexConnection) = store.save(connection)

    fun logout() = store.logout()

    fun libraryOrder(): List<String> = store.libraryOrder()

    fun saveLibraryOrder(keys: List<String>) = store.saveLibraryOrder(keys)

    fun playbackQuality(): PlaybackQuality = store.playbackQuality()

    fun savePlaybackQuality(quality: PlaybackQuality) =
        store.savePlaybackQuality(quality)

    fun autoPlayNext(): Boolean = store.autoPlayNext()

    fun saveAutoPlayNext(enabled: Boolean) =
        store.saveAutoPlayNext(enabled)

    suspend fun connect(connection: PlexConnection = store.load()): PlexServer =
        api(connection).server()

    suspend fun signInAndConnect(
        username: String,
        password: String,
    ): Pair<PlexConnection, PlexServer> {
        val clientIdentifier = store.clientIdentifier()
        val token = PlexAuthApi(clientIdentifier).signIn(
            username = username,
            password = password,
        )
        val discovered = PlexResourcesApi(
            accountToken = token,
            clientIdentifier = clientIdentifier,
        ).serverConnections()
        var lastError: Throwable? = null
        for (endpoint in discovered) {
            val connection = PlexConnection(
                baseUrl = endpoint.uri,
                token = endpoint.token,
            )
            try {
                return connection to connect(connection)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw PlexException(
            "계정에서 연결 가능한 Plex Media Server를 찾지 못했습니다.",
            lastError,
        )
    }

    suspend fun sections(): List<PlexSection> = api().sections()

    suspend fun sectionItems(sectionKey: String): List<PlexItem> =
        api().sectionItems(sectionKey)

    suspend fun recentlyAdded(section: PlexSection): List<PlexItem> =
        api().recentlyAdded(section.key, section.type)

    suspend fun onDeck(): List<PlexItem> = api().onDeck()

    suspend fun search(term: String): List<PlexItem> {
        val normalized = term.trim()
        if (normalized.isBlank()) return emptyList()
        return try {
            api().search(normalized)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            val allItems = mutableListOf<PlexItem>()
            for (section in sections()) {
                allItems += sectionItems(section.key)
            }
            allItems
                .filter { item ->
                    item.title.contains(normalized, ignoreCase = true) ||
                        item.subtitle.orEmpty().contains(normalized, ignoreCase = true)
                }
                .distinctBy { "${it.type}-${it.ratingKey}-${it.key}" }
        }
    }

    suspend fun sectionOnDeck(sectionKey: String): List<PlexItem> =
        api().sectionOnDeck(sectionKey)

    suspend fun itemDetails(item: PlexItem): PlexItem =
        api().metadata(item.ratingKey).firstOrNull() ?: item

    suspend fun relatedContent(item: PlexItem): PlexRelatedContent {
        val resolved = api().metadata(item.ratingKey).firstOrNull() ?: item
        val taggedItem = if (
            resolved.actors.isEmpty() &&
            resolved.genres.isEmpty() &&
            !resolved.grandparentRatingKey.isNullOrBlank()
        ) {
            api().metadata(resolved.grandparentRatingKey).firstOrNull() ?: resolved
        } else {
            resolved
        }
        val sectionKey = taggedItem.librarySectionId
            ?: resolved.librarySectionId
            ?: item.librarySectionId
            ?: return PlexRelatedContent()
        val actorFilters = taggedItem.actors.mapNotNull { it.queryFilter("actor") }
            .distinct()
            .take(3)
        val genreFilters = taggedItem.genres.mapNotNull { it.queryFilter("genre") }
            .distinct()
            .take(3)

        return supervisorScope {
            val actorWorks = actorFilters.map { filter ->
                async {
                    try {
                        api().filteredSectionItems(sectionKey, filter)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            }
            val genreWorks = genreFilters.map { filter ->
                async {
                    try {
                        api().filteredSectionItems(sectionKey, filter)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
            }
            PlexRelatedContent(
                actorWorks = actorWorks.map { it.await() }
                    .flatten()
                    .relatedCandidatesExcluding(item, taggedItem),
                similarGenreWorks = genreWorks.map { it.await() }
                    .flatten()
                    .relatedCandidatesExcluding(item, taggedItem),
            )
        }
    }

    suspend fun watched(section: PlexSection): List<PlexItem> =
        api().watched(section.key, section.type)

    suspend fun setWatched(item: PlexItem, watched: Boolean) =
        setWatched(item.ratingKey, watched)

    suspend fun setWatched(ratingKey: String, watched: Boolean) =
        api().setWatched(ratingKey, watched)

    suspend fun removeFromContinueWatching(item: PlexItem) =
        api().removeFromContinueWatching(item.ratingKey)

    suspend fun children(item: PlexItem): List<PlexItem> {
        val path = when {
            item.key.endsWith("/children") -> item.key
            item.key.isNotBlank() -> "${item.key}/children"
            else -> "/library/metadata/${item.ratingKey}/children"
        }
        return api().children(path)
    }

    suspend fun hasChildren(item: PlexItem): Boolean = children(item).isNotEmpty()

    suspend fun seasonSiblings(item: PlexItem): List<PlexItem> {
        val resolved = api().metadata(item.ratingKey).firstOrNull() ?: item
        if (item.type != "episode" && resolved.type != "episode") return emptyList()
        val parentPath = resolved.parentKey
            ?: item.parentKey
            ?: resolved.parentRatingKey?.let { "/library/metadata/$it" }
            ?: item.parentRatingKey?.let { "/library/metadata/$it" }
            ?: return emptyList()
        val childrenPath = if (parentPath.endsWith("/children")) {
            parentPath
        } else {
            "$parentPath/children"
        }
        return api().children(childrenPath)
    }

    suspend fun playback(item: PlexItem): PlaybackSource {
        val resolved = api().metadata(item.ratingKey).firstOrNull() ?: item
        val part = resolved.partKey
            ?: throw PlexException("이 항목은 직접 재생할 수 없습니다.")
        val connection = store.load()
        val quality = store.playbackQuality()
        val directUrl = api(connection).absoluteUrl(part)
        val playbackUrl =
            if (
                quality == PlaybackQuality.ORIGINAL ||
                item.type == "track"
            ) {
                directUrl
            } else {
                transcodeUrl(
                    connection = connection,
                    ratingKey = item.ratingKey,
                    quality = quality,
                )
            }
        return PlaybackSource(
            url = playbackUrl,
            fallbackUrls =
                if (playbackUrl != directUrl) {
                    listOf(directUrl)
                } else {
                    emptyList()
                },
            filePath = resolved.filePath ?: part,
            token = connection.token,
            title = item.title,
            subtitle = item.subtitle,
            ratingKey = item.ratingKey,
            durationMs = resolved.durationMs.takeIf { it > 0 } ?: item.durationMs,
            resumePositionMs = item.viewOffsetMs,
            videoCodec = resolved.videoCodec,
            videoDynamicRange = resolved.videoDynamicRange,
            videoProfile = resolved.videoProfile,
            videoColorPrimaries = resolved.videoColorPrimaries,
            videoColorTransfer = resolved.videoColorTransfer,
            dolbyVisionProfile = resolved.dolbyVisionProfile,
            audioCodec = resolved.audioCodec,
            subtitles = resolved.subtitles
                .forSelectedPart(resolved.partKey)
                .mapNotNull { subtitle ->
                if (subtitle.isEmbedded) return@mapNotNull null
                val streamPath = subtitle.key?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                PlaybackSubtitle(
                    url = api(connection).absoluteUrl(streamPath),
                    streamId = subtitle.streamId,
                    isEmbedded = false,
                    language = subtitle.language,
                    label = subtitle.title
                        ?: subtitle.language
                        ?: "자막",
                    mimeType = subtitleMimeType(subtitle.codec),
                    codec = subtitle.codec,
                    selected = subtitle.selected,
                )
            },
            compatibilitySubtitles = resolved.subtitles
                .forSelectedPart(resolved.partKey)
                .mapNotNull { subtitle ->
                    if (!subtitle.isEmbedded || !subtitle.isExtractableTextSubtitle()) {
                        return@mapNotNull null
                    }
                    val streamId = subtitle.streamId?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val directStreamUrl = subtitle.key
                        ?.takeIf { it.isNotBlank() }
                        ?.let { api(connection).absoluteUrl(it) }
                    val extractionUrl = embeddedSubtitleUrl(
                        connection = connection,
                        ratingKey = item.ratingKey,
                        subtitle = subtitle,
                    )
                    val candidates = listOfNotNull(directStreamUrl, extractionUrl).distinct()
                    PlaybackSubtitle(
                        url = candidates.first(),
                        fallbackUrls = candidates.drop(1),
                        streamId = streamId,
                        isEmbedded = true,
                        language = subtitle.language,
                        label = subtitle.title
                            ?: subtitle.language
                            ?: "내장 텍스트 자막",
                        mimeType = subtitleMimeType(subtitle.codec),
                        codec = subtitle.codec,
                        selected = subtitle.selected,
                    )
                },
        )
    }

    suspend fun timeline(source: PlaybackSource, state: String, positionMs: Long) {
        api().timeline(
            ratingKey = source.ratingKey,
            key = "/library/metadata/${source.ratingKey}",
            state = state,
            timeMs = positionMs,
            durationMs = source.durationMs,
        )
    }

    fun imageUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { api().absoluteUrl(it) }

    fun token(): String = store.load().token

    private fun PlexTag.queryFilter(defaultName: String): String? {
        val raw = filter?.takeIf { it.contains('=') }
        if (raw != null) return raw
        return id?.takeIf { it.isNotBlank() }?.let { "$defaultName=$it" }
    }

    private fun List<PlexItem>.relatedCandidatesExcluding(
        selected: PlexItem,
        taggedItem: PlexItem,
    ): List<PlexItem> = asSequence()
        .filter { it.type in setOf("movie", "show") }
        .filter { candidate ->
            candidate.ratingKey != selected.ratingKey &&
                candidate.ratingKey != taggedItem.ratingKey
        }
        .distinctBy { "${it.type}-${it.ratingKey}" }
        .take(20)
        .toList()

    private fun transcodeUrl(
        connection: PlexConnection,
        ratingKey: String,
        quality: PlaybackQuality,
    ): String {
        val sessionIdentifier = UUID.randomUUID().toString()
        val maxVideoBitrate =
            checkNotNull(quality.maxBitrateKbps).toString()
        val parameters = linkedMapOf(
            "path" to "/library/metadata/$ratingKey",
            "mediaIndex" to "0",
            "partIndex" to "0",
            "protocol" to "hls",
            "fastSeek" to "1",
            "hasMDE" to "1",
            "includeCodecs" to "1",
            "directPlay" to "0",
            "directStream" to "0",
            "directStreamAudio" to "0",
            "videoCodec" to "h264",
            "audioCodec" to "aac",
            "audioBoost" to "100",
            "autoAdjustQuality" to "0",
            "mediaBufferSize" to "74944",
            "subtitles" to "none",
            "skipSubtitles" to "1",
            "subtitleStreamID" to "0",
            "advancedSubtitles" to "text",
            "autoAdjustSubtitle" to "0",
            "subtitleSize" to "100",
            "videoQuality" to "100",
            "videoResolution" to checkNotNull(quality.resolution),
            "videoBitrate" to maxVideoBitrate,
            "maxVideoBitrate" to maxVideoBitrate,
            "session" to sessionIdentifier,
            "X-Plex-Session-Identifier" to sessionIdentifier,
            "X-Plex-Product" to "Plex Play Universal",
            "X-Plex-Version" to BuildConfig.VERSION_NAME,
            "X-Plex-Platform" to "Android",
            "X-Plex-Client-Platform" to "Android",
            "X-Plex-Device" to "Android",
            "X-Plex-Client-Identifier" to store.clientIdentifier(),
            "X-Plex-Language" to "ko",
            "X-Plex-Token" to connection.token,
        )
        val query = parameters.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        return connection.baseUrl.trimEnd('/') +
            "/video/:/transcode/universal/start.m3u8?$query"
    }

    private fun embeddedSubtitleUrl(
        connection: PlexConnection,
        ratingKey: String,
        subtitle: PlexSubtitle,
    ): String {
        val sessionIdentifier = UUID.randomUUID().toString()
        val parameters = linkedMapOf(
            "path" to "/library/metadata/$ratingKey",
            "mediaIndex" to subtitle.mediaIndex.toString(),
            "partIndex" to subtitle.partIndex.toString(),
            "subtitleStreamID" to checkNotNull(subtitle.streamId),
            "protocol" to "http",
            "directPlay" to "0",
            "directStream" to "1",
            "fastSeek" to "1",
            "hasMDE" to "1",
            "subtitles" to "sidecar",
            "advancedSubtitles" to "text",
            "offset" to "0",
            "session" to sessionIdentifier,
            "X-Plex-Product" to "Plex Play Universal",
            "X-Plex-Version" to BuildConfig.VERSION_NAME,
            "X-Plex-Platform" to "Android",
            "X-Plex-Client-Identifier" to store.clientIdentifier(),
            "X-Plex-Language" to "ko",
            "X-Plex-Client-Profile-Extra" to
                "add-transcode-target(type=subtitleProfile&protocol=http&context=all&" +
                "subtitleCodec=srt&container=srt)",
        )
        val query = parameters.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        return connection.baseUrl.trimEnd('/') +
            "/video/:/transcode/universal/subtitles?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun subtitleMimeType(codec: String?): String = when (codec?.lowercase()) {
        "srt", "subrip" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt", "webvtt" -> "text/vtt"
        "ttml" -> "application/ttml+xml"
        "pgs", "hdmv_pgs_subtitle" -> "application/pgs"
        "vobsub", "dvd_subtitle" -> "application/vobsub"
        "dvbsub", "dvb_subtitle", "dvb_subtitle_teletext" ->
            "application/dvbsubs"
        "smi", "sami" -> "application/x-subrip"
        else -> "application/x-subrip"
    }

    private fun PlexSubtitle.isExtractableTextSubtitle(): Boolean =
        codec?.lowercase() in setOf(
            "srt",
            "subrip",
            "ass",
            "ssa",
            "vtt",
            "webvtt",
            "ttml",
            "smi",
            "sami",
            "mov_text",
            "tx3g",
            "ttxt",
            "text",
        )

    private fun List<PlexSubtitle>.forSelectedPart(partKey: String?): List<PlexSubtitle> {
        if (partKey.isNullOrBlank()) return this
        val matching = filter { it.partKey == partKey }
        return matching.ifEmpty { this }
    }

    private fun api(connection: PlexConnection = store.load()): PlexApi {
        if (!connection.isConfigured) throw PlexException("Plex 연결 정보가 없습니다.")
        return PlexApi(connection, store.clientIdentifier())
    }
}
