package io.mirr.plexplay.data

import io.mirr.plexplay.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URLEncoder
import java.util.UUID

class PlexRepository(
    private val store: ConnectionStore,
) {
    private val collectionUpdateMutex = Mutex()
    private val relatedPermits = Semaphore(3)
    private val latestEpisodePermits = Semaphore(3)
    private val relatedCache = BrowseLookupCache<String, PlexRelatedContent>(48, 90_000)
    private data class LatestLabel(val text: String?)
    private val latestEpisodeCache = BrowseLookupCache<String, LatestLabel>(100, 60_000)

    fun connection(): PlexConnection = store.load()

    fun saveConnection(connection: PlexConnection) {
        clearBrowseCaches()
        store.save(connection)
    }

    fun logout() {
        clearBrowseCaches()
        store.logout()
    }

    fun clearBrowseCaches() {
        relatedCache.clear()
        latestEpisodeCache.clear()
    }

    fun libraryOrder(): List<String> = store.libraryOrder()

    fun saveLibraryOrder(keys: List<String>) = store.saveLibraryOrder(keys)

    fun playbackQuality(): PlaybackQuality = store.playbackQuality()

    fun savePlaybackQuality(quality: PlaybackQuality) =
        store.savePlaybackQuality(quality)

    fun autoPlayNext(): Boolean = store.autoPlayNext()

    fun watchedCollectionSettings(): WatchedCollectionSettings = store.watchedCollectionSettings()

    suspend fun saveWatchedCollectionSettings(settings: WatchedCollectionSettings) = withContext(Dispatchers.IO) {
        store.saveWatchedCollectionSettings(settings)
    }

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
                isServerOwner = endpoint.isOwned,
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
        api(backgroundLookup = true).recentlyAdded(section.key, section.type)

    suspend fun latestEpisodeLabel(show: PlexItem): String? {
        if (show.type != "show") return null
        val connection = store.load()
        latestEpisodeCache.get(connection, show.ratingKey)?.let { return it.text }
        val episode = latestEpisodePermits.withPermit {
            api(connection, backgroundLookup = true).latestEpisode(show)
        }
        val label = episode?.let(::formatLatestEpisodeLabel)
        if (store.load() == connection) latestEpisodeCache.put(connection, show.ratingKey, LatestLabel(label))
        return label
    }

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

    suspend fun relatedContent(
        item: PlexItem,
        onUpdate: (PlexRelatedContent) -> Unit = {},
    ): PlexRelatedContent {
        val connection = store.load()
        relatedCache.get(connection, item.ratingKey)?.let { onUpdate(it); return it }
        // showItemDetails already resolved this metadata. Do not fetch it twice.
        val resolved = item
        val api = api(connection, backgroundLookup = true)
        val taggedItem = if (
            resolved.actors.isEmpty() &&
            resolved.genres.isEmpty() &&
            !resolved.grandparentRatingKey.isNullOrBlank()
        ) {
            api.metadata(resolved.grandparentRatingKey).firstOrNull() ?: resolved
        } else {
            resolved
        }
        val sectionKey = taggedItem.librarySectionId
            ?: resolved.librarySectionId
            ?: item.librarySectionId
            ?: return PlexRelatedContent()
        val related = loadRelatedContent(item, taggedItem, relatedPermits,
            fetch = { filter -> api.filteredSectionItems(sectionKey, filter, limit = 24) },
            onUpdate = { if (store.load() == connection) onUpdate(it) })
        if (store.load() == connection &&
            (related.actorWorks.isNotEmpty() || related.similarGenreWorks.isNotEmpty())
        ) relatedCache.put(connection, item.ratingKey, related)
        return related
    }

    suspend fun watched(section: PlexSection): List<PlexItem> =
        api().watched(section.key, section.type)

    suspend fun setWatched(item: PlexItem, watched: Boolean) =
        api().setWatchedAndVerify(item, watched)

    suspend fun setWatched(ratingKey: String, watched: Boolean) =
        api().setWatched(ratingKey, watched)

    suspend fun setWatched(source: PlaybackSource, watched: Boolean): Boolean {
        val connection = validatedPlaybackConnection(source, store.load()) ?: return false
        api(connection).setWatched(source.ratingKey, watched)
        return true
    }

    suspend fun markWatchedWithCollection(item: PlexItem): WatchedActionResult {
        val connection = store.load()
        val collectionSettings = store.watchedCollectionSettings()
        val api = api(connection)
        var saved: PlexItem? = null
        var collectionChange: CompletedCollectionChange? = null
        val notice = saveWatchedWithCollection(
            collectionUpdatesAllowed = connection.mayUpdateCollections,
            saveWatched = { saved = api.setWatchedAndVerify(item, true) },
            updateCollection = {
                val resolved = checkNotNull(saved)
                val change = collectionUpdateMutex.withLock {
                    collectionUpdater(api, connection).update(resolved, collectionSettings)
                }
                collectionChange = change
                if (change?.item?.ratingKey == resolved.ratingKey) {
                    saved = change.item
                }
                change?.tag
            },
        )
        val finalNotice = collectionChange?.takeIf { it.item.type == "show" }?.let {
            "시리즈 전체 시청을 완료해 시리즈 컬렉션을 ${it.tag} 하나로 변경했습니다."
        } ?: notice
        return WatchedActionResult(checkNotNull(saved), finalNotice)
    }

    /** Real playback completion uses the played Part; manual completion uses fresh metadata. */
    internal suspend fun updateCompletedPlaybackCollection(
        source: PlaybackSource,
    ): CompletedCollectionChange? {
        val connection = validatedPlaybackConnection(source, store.load()) ?: return null
        if (!connection.mayUpdateCollections) return null
        val collectionSettings = store.watchedCollectionSettings()
        val api = api(connection)
        return collectionUpdateMutex.withLock {
            val item = api.metadata(source.ratingKey).singleOrNull { it.ratingKey == source.ratingKey }
                ?: throw PlexException("재생 완료 영상의 컬렉션 정보를 확인하지 못했습니다.")
            collectionUpdater(api, connection).update(item, collectionSettings,
                playedFilePath = source.filePath, usePlayedFilePath = true)
        }
    }

    private fun collectionUpdater(api: PlexApi, connection: PlexConnection) = CompletedCollectionUpdater(
        metadata = { key -> api.metadata(key).singleOrNull { it.ratingKey == key } },
        sections = { api.sections() },
        episodes = { api.seriesEpisodes(it) },
        isCurrent = { store.load() == connection },
        replace = { item, section, tag ->
            api.replaceCollectionTag(section.key, item.ratingKey,
                if (item.type == "video" && section.type == "movie") "movie" else item.type,
                tag, item.collections)
        },
    )

    suspend fun removeFromContinueWatching(item: PlexItem) =
        api().removeFromContinueWatching(item.ratingKey)

    suspend fun children(item: PlexItem): List<PlexItem> {
        return visibleSeriesChildren(item, api().children(plexChildrenPath(item)))
    }

    suspend fun hasChildren(item: PlexItem): Boolean = children(item).isNotEmpty()

    suspend fun episodeBrowseItems(item: PlexItem): List<PlexItem> {
        val connection = store.load()
        val api = api(connection)
        val items = episodeBrowseItems(item) { folder -> api.children(plexChildrenPath(folder)) }
        if (store.load() != connection) {
            throw PlexException("서버 연결이 변경되어 이전 목록 요청을 중지했습니다.")
        }
        return items
    }

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

    suspend fun folderPlaybackQueue(item: PlexItem, cachedLists: List<List<PlexItem>>): List<PlexItem> {
        val connection = store.load()
        val api = api(connection)
        val current = api.metadata(item.ratingKey).singleOrNull { it.ratingKey == item.ratingKey } ?: item
        val path = playbackCandidatePath(current)
        val remote = if (path == null || playbackFolderKey(current.filePath) == null) emptyList() else try {
            api.playbackCandidates(path, episodeSection = current.type == "episode")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        val result = discoverSameFolderPlaybackQueue(current, listOf(remote) + cachedLists) { candidate ->
            api.metadata(candidate.ratingKey).singleOrNull { it.ratingKey == candidate.ratingKey } ?: candidate
        }
        if (store.load() != connection) throw PlexException("서버 연결이 변경되어 이전 폴더 조회를 중지했습니다.")
        return result
    }

    suspend fun playback(item: PlexItem): PlaybackSource {
        val connection = store.load()
        // Keep manual playback's cached Part fallback, but an empty metadata
        // response cannot revalidate the physical folder for next/previous play.
        val resolved = api(connection).metadata(item.ratingKey).firstOrNull()
            ?: item.copy(filePath = null)
        if (store.load() != connection) {
            throw PlexException("서버 연결이 변경되어 이전 영상의 재생 요청을 중지했습니다.")
        }
        val part = resolved.partKey
            ?: throw PlexException("이 항목은 직접 재생할 수 없습니다.")
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
                    mediaIndex = resolved.selectedMediaIndex,
                    partIndex = resolved.selectedPartIndex,
                )
            }
        val originalVideo = playbackUrl == directUrl
        val video = resolved.videoStreamMetadata().forPlaybackOutput(originalVideo)
        return PlaybackSource(
            url = playbackUrl,
            serverBaseUrl = connection.baseUrl,
            fallbackUrls =
                if (playbackUrl != directUrl) {
                    listOf(directUrl)
                } else {
                    emptyList()
                },
            filePath = resolved.filePath,
            token = connection.token,
            title = item.title,
            subtitle = item.subtitle,
            ratingKey = item.ratingKey,
            durationMs = resolved.durationMs.takeIf { it > 0 } ?: item.durationMs,
            resumePositionMs = item.viewOffsetMs,
            videoCodec = resolved.videoCodec.takeIf { originalVideo },
            videoWidth = video.width,
            videoHeight = video.height,
            videoFrameRate = video.frameRate,
            videoBitDepth = video.bitDepth,
            videoDynamicRange = resolved.videoDynamicRange.takeIf { originalVideo },
            videoProfile = resolved.videoProfile.takeIf { originalVideo },
            videoColorPrimaries = resolved.videoColorPrimaries.takeIf { originalVideo },
            videoColorTransfer = resolved.videoColorTransfer.takeIf { originalVideo },
            dolbyVisionProfile = resolved.dolbyVisionProfile.takeIf { originalVideo },
            audioCodec = resolved.audioCodec.takeIf { originalVideo },
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
        val connection = validatedPlaybackConnection(source, store.load()) ?: return
        api(connection).timeline(
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

    private fun transcodeUrl(
        connection: PlexConnection,
        ratingKey: String,
        quality: PlaybackQuality,
        mediaIndex: Int,
        partIndex: Int,
    ): String {
        val sessionIdentifier = UUID.randomUUID().toString()
        val maxVideoBitrate =
            checkNotNull(quality.maxBitrateKbps).toString()
        val parameters = linkedMapOf(
            "path" to "/library/metadata/$ratingKey",
            "mediaIndex" to mediaIndex.toString(),
            "partIndex" to partIndex.toString(),
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

    private fun api(connection: PlexConnection = store.load(), backgroundLookup: Boolean = false): PlexApi {
        if (!connection.isConfigured) throw PlexException("Plex 연결 정보가 없습니다.")
        return PlexApi(connection, store.clientIdentifier(), backgroundLookup)
    }
}
