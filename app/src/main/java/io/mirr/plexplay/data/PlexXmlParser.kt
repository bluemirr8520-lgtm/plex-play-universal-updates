package io.mirr.plexplay.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

internal object PlexXmlParser {
    fun server(input: InputStream): PlexServer {
        val parser = newParser(input)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "MediaContainer") {
                return PlexServer(
                    name = parser.attr("friendlyName").orEmpty().ifBlank { "Plex" },
                    version = parser.attr("version").orEmpty(),
                )
            }
        }
        throw PlexException("Plex 서버 정보를 읽을 수 없습니다.")
    }

    fun sections(input: InputStream): List<PlexSection> {
        val parser = newParser(input)
        val result = mutableListOf<PlexSection>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Directory") {
                val key = parser.attr("key") ?: continue
                result += PlexSection(
                    key = key,
                    title = parser.attr("title").orEmpty(),
                    type = parser.attr("type").orEmpty(),
                    thumb = parser.attr("thumb"),
                )
            }
        }
        return result
    }

    fun items(input: InputStream): List<PlexItem> {
        val parser = newParser(input)
        val result = mutableListOf<PlexItem>()
        var current: MutableItem? = null
        var currentMedia: MutableItem? = null
        var currentItemDepth = -1
        var ignoredItemDepth = -1
        var mediaIndex = -1
        var partIndex = -1
        var currentPartKey: String? = null
        var containerLibrarySectionId: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (ignoredItemDepth >= 0) {
                if (parser.eventType == XmlPullParser.END_TAG && parser.depth == ignoredItemDepth) {
                    ignoredItemDepth = -1
                }
                continue
            }
            if (current != null && parser.eventType == XmlPullParser.START_TAG &&
                parser.depth > currentItemDepth &&
                parser.name in setOf("Video", "Directory", "Track", "Photo")
            ) {
                // Related/extra child items are not the current playback item.
                // Ignore their entire subtree, including media and file paths.
                ignoredItemDepth = parser.depth
                continue
            }
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "MediaContainer" -> {
                        if (parser.depth == 1) {
                            containerLibrarySectionId = parser.attr("librarySectionID")
                                ?.takeIf { it.isNotBlank() }
                        }
                    }
                    "Video", "Directory", "Track", "Photo" -> {
                        if (current == null) {
                            current = MutableItem.from(parser, containerLibrarySectionId)
                            currentMedia = null
                            currentItemDepth = parser.depth
                            mediaIndex = -1
                            partIndex = -1
                            currentPartKey = null
                        }
                    }
                    "Media" -> {
                        mediaIndex++
                        partIndex = -1
                        currentPartKey = null
                        // A candidate Media must not overwrite the previously selected Part
                        // until this Media supplies an actual Part of its own.
                        currentMedia = current?.forMedia(parser)
                    }
                    "Part" -> {
                        val media = currentMedia
                        if (media != null) {
                            media.mediaFilePaths.add(parser.attr("file").orEmpty())
                            partIndex++
                            currentPartKey = parser.attr("key")
                            // Each Part starts from its Media's attributes, never a sibling's streams.
                            current = media.copy(
                                partKey = currentPartKey,
                                filePath = parser.attr("file"),
                                selectedMediaIndex = mediaIndex.coerceAtLeast(0),
                                selectedPartIndex = partIndex.coerceAtLeast(0),
                                container = parser.attr("container") ?: media.container,
                            )
                        }
                    }
                    "Stream" -> {
                        val streamItem = if (currentPartKey == null) currentMedia else current
                        if (streamItem != null && parser.attr("streamType") == "1" &&
                            (!streamItem.videoStreamCaptured || parser.attr("selected") == "1")
                        ) {
                            streamItem.videoCodec = parser.attr("codec") ?: currentMedia?.videoCodec
                            streamItem.videoProfile = parser.attr("profile") ?: currentMedia?.videoProfile
                            streamItem.videoWidth = parsePositiveVideoInt(parser.attr("width"), currentMedia?.videoWidth)
                            streamItem.videoHeight = parsePositiveVideoInt(parser.attr("height"), currentMedia?.videoHeight)
                            streamItem.videoFrameRate = parseVideoFrameRate(
                                parser.attr("frameRate") ?: parser.attr("videoFrameRate"), currentMedia?.videoFrameRate,
                            )
                            streamItem.videoBitDepth = parsePositiveVideoInt(parser.attr("bitDepth"), currentMedia?.videoBitDepth)
                            streamItem.videoDynamicRange = parser.videoDynamicRangeHint() ?: currentMedia?.videoDynamicRange
                            streamItem.videoColorPrimaries = parser.attr("colorPrimaries") ?: currentMedia?.videoColorPrimaries
                            streamItem.videoColorTransfer = parser.attr("colorTrc") ?: parser.attr("colorTransfer") ?: currentMedia?.videoColorTransfer
                            streamItem.dolbyVisionProfile = parser.attr("DOVIProfile")?.toIntOrNull() ?: currentMedia?.dolbyVisionProfile
                            streamItem.videoResolution = currentMedia?.videoResolution ?: when {
                                streamItem.videoWidth != null && streamItem.videoHeight != null -> "${streamItem.videoWidth}x${streamItem.videoHeight}"
                                streamItem.videoHeight != null -> "${streamItem.videoHeight}p"
                                else -> null
                            }
                            streamItem.videoStreamCaptured = true
                        }
                        if (streamItem != null && parser.attr("streamType") == "2") {
                            val selected = parser.attr("selected") == "1"
                            if (!streamItem.audioStreamCaptured || selected) {
                                streamItem.audioCodec =
                                    parser.attr("codec") ?: currentMedia?.audioCodec
                                streamItem.audioChannels =
                                    parser.attr("channels")?.toIntOrNull()
                                        ?: currentMedia?.audioChannels
                                streamItem.audioLanguage =
                                    parser.attr("language")
                                        ?: parser.attr("languageCode")
                                        ?: currentMedia?.audioLanguage
                                streamItem.audioProfile =
                                    parser.attr("profile") ?: currentMedia?.audioProfile
                                streamItem.audioDisplayTitle =
                                    parser.attr("extendedDisplayTitle")
                                        ?: parser.attr("displayTitle")
                                        ?: currentMedia?.audioDisplayTitle
                                streamItem.audioStreamCaptured = true
                            }
                        }
                        val subtitleKey = parser.attr("key")
                        val subtitleStreamId = parser.attr("id")
                        val subtitleIsEmbedded = when (parser.attr("external")) {
                            "1" -> false
                            "0" -> true
                            else -> subtitleKey.isNullOrBlank()
                        }
                        if (streamItem != null &&
                            parser.attr("streamType") == "3" &&
                            (!subtitleKey.isNullOrBlank() || !subtitleStreamId.isNullOrBlank())
                        ) {
                            streamItem.subtitles += PlexSubtitle(
                                key = subtitleKey,
                                streamId = subtitleStreamId,
                                isEmbedded = subtitleIsEmbedded,
                                partKey = currentPartKey,
                                mediaIndex = mediaIndex.coerceAtLeast(0),
                                partIndex = partIndex.coerceAtLeast(0),
                                language = parser.attr("languageCode")
                                    ?: parser.attr("language"),
                                title = parser.attr("displayTitle")
                                    ?: parser.attr("title")
                                    ?: parser.attr("language"),
                                codec = parser.attr("codec"),
                                selected = parser.attr("selected") == "1",
                            )
                        }
                    }
                    "Role" -> current?.actors?.add(
                        PlexTag(
                            tag = parser.attr("tag").orEmpty(),
                            id = parser.attr("id"),
                            filter = parser.attr("filter"),
                        ),
                    )
                    "Genre" -> current?.genres?.add(
                        PlexTag(
                            tag = parser.attr("tag").orEmpty(),
                            id = parser.attr("id"),
                            filter = parser.attr("filter"),
                        ),
                    )
                    "Collection" -> parser.attr("tag")?.let { tag ->
                        if (parser.depth == currentItemDepth + 1) {
                            current?.collections?.add(tag)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (current != null && parser.depth == currentItemDepth &&
                        parser.name in setOf("Video", "Directory", "Track", "Photo")
                    ) {
                        result += (if (current.partKey == null) currentMedia ?: current else current).toItem()
                        current = null
                        currentItemDepth = -1
                    }
                }
            }
        }
        return result
    }

    private fun newParser(input: InputStream): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }

    private data class MutableItem(
        val ratingKey: String,
        val key: String,
        val type: String,
        val title: String,
        val subtitle: String?,
        val summary: String?,
        val year: Int?,
        val durationMs: Long,
        val viewOffsetMs: Long,
        val viewCount: Int,
        val thumb: String?,
        val art: String?,
        val parentRatingKey: String?,
        val parentKey: String?,
        val grandparentRatingKey: String?,
        val librarySectionId: String?,
        val leafCount: Int,
        val viewedLeafCount: Int,
        val childCount: Int,
        val episodeNumber: Int?,
        val seasonNumber: Int?,
        val addedAtSeconds: Long?,
        val grandparentTitle: String?,
        val grandparentThumb: String?,
        val grandparentArt: String?,
        var partKey: String? = null,
        var filePath: String? = null,
        var selectedMediaIndex: Int = 0,
        var selectedPartIndex: Int = 0,
        var container: String? = null,
        var videoCodec: String? = null,
        var videoResolution: String? = null,
        var videoWidth: Int? = null,
        var videoHeight: Int? = null,
        var videoFrameRate: Double? = null,
        var videoProfile: String? = null,
        var videoBitDepth: Int? = null,
        var videoDynamicRange: String? = null,
        var videoColorPrimaries: String? = null,
        var videoColorTransfer: String? = null,
        var dolbyVisionProfile: Int? = null,
        var audioCodec: String? = null,
        var audioChannels: Int? = null,
        var audioLanguage: String? = null,
        var audioProfile: String? = null,
        var audioDisplayTitle: String? = null,
        var audioStreamCaptured: Boolean = false,
        var videoStreamCaptured: Boolean = false,
        val actors: MutableList<PlexTag> = mutableListOf(),
        val genres: MutableList<PlexTag> = mutableListOf(),
        val collections: MutableList<String> = mutableListOf(),
        val subtitles: MutableList<PlexSubtitle> = mutableListOf(),
        val mediaFilePaths: MutableList<String> = mutableListOf(),
    ) {
        fun forMedia(parser: XmlPullParser) = copy(
            partKey = null,
            filePath = null,
            container = parser.attr("container"),
            videoCodec = parser.attr("videoCodec"),
            videoResolution = parser.attr("videoResolution"),
            videoWidth = parsePositiveVideoInt(parser.attr("width")),
            videoHeight = parsePositiveVideoInt(parser.attr("height")),
            videoFrameRate = parseVideoFrameRate(parser.attr("frameRate") ?: parser.attr("videoFrameRate")),
            videoProfile = parser.attr("videoProfile"),
            videoBitDepth = parsePositiveVideoInt(parser.attr("videoBitDepth")),
            videoDynamicRange = parser.attr("videoDynamicRange"),
            videoColorPrimaries = parser.attr("colorPrimaries"),
            videoColorTransfer = parser.attr("colorTrc") ?: parser.attr("colorTransfer"),
            dolbyVisionProfile = parser.attr("DOVIProfile")?.toIntOrNull(),
            audioCodec = parser.attr("audioCodec"),
            audioChannels = parsePositiveVideoInt(parser.attr("audioChannels")),
            audioLanguage = null,
            audioProfile = parser.attr("audioProfile"),
            audioDisplayTitle = null,
            audioStreamCaptured = false,
            videoStreamCaptured = false,
        )

        fun toItem() = PlexItem(
            ratingKey = ratingKey,
            key = key,
            type = type,
            title = title,
            subtitle = subtitle,
            summary = summary,
            year = year,
            durationMs = durationMs,
            viewOffsetMs = viewOffsetMs,
            viewCount = viewCount,
            thumb = thumb,
            art = art,
            partKey = partKey,
            filePath = filePath,
            selectedMediaIndex = selectedMediaIndex,
            selectedPartIndex = selectedPartIndex,
            container = container,
            videoCodec = videoCodec,
            videoResolution = videoResolution,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            videoFrameRate = videoFrameRate,
            videoProfile = videoProfile,
            videoBitDepth = videoBitDepth,
            videoDynamicRange = videoDynamicRange,
            videoColorPrimaries = videoColorPrimaries,
            videoColorTransfer = videoColorTransfer,
            dolbyVisionProfile = dolbyVisionProfile,
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            audioLanguage = audioLanguage,
            audioProfile = audioProfile,
            audioDisplayTitle = audioDisplayTitle,
            parentRatingKey = parentRatingKey,
            parentKey = parentKey,
            grandparentRatingKey = grandparentRatingKey,
            librarySectionId = librarySectionId,
            actors = actors.filter { it.tag.isNotBlank() }.distinctBy { it.id ?: it.tag },
            genres = genres.filter { it.tag.isNotBlank() }.distinctBy { it.id ?: it.tag },
            collections = collections.filter { it.isNotBlank() }.distinct(),
            subtitles = subtitles.toList(),
            leafCount = leafCount,
            viewedLeafCount = viewedLeafCount,
            childCount = childCount,
            mediaFilePaths = mediaFilePaths.toList(),
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            addedAtSeconds = addedAtSeconds,
            grandparentTitle = grandparentTitle,
            grandparentThumb = grandparentThumb,
            grandparentArt = grandparentArt,
        )

        companion object {
            fun from(parser: XmlPullParser, containerLibrarySectionId: String?): MutableItem {
                val grandparent = parser.attr("grandparentTitle")
                val parent = parser.attr("parentTitle")
                val subtitle = when {
                    grandparent != null && parent != null -> "$grandparent · $parent"
                    grandparent != null -> grandparent
                    parent != null -> parent
                    else -> parser.attr("tagline")
                }
                val key = parser.attr("key").orEmpty()
                return MutableItem(
                    ratingKey = parser.attr("ratingKey") ?: key,
                    key = key,
                    type = parser.attr("type").orEmpty().ifBlank {
                        parser.name.lowercase()
                    },
                    title = parser.attr("title")
                        ?: parser.attr("name")
                        ?: "제목 없음",
                    subtitle = subtitle,
                    summary = parser.attr("summary"),
                    year = parser.attr("year")?.toIntOrNull(),
                    durationMs = parser.attr("duration")?.toLongOrNull() ?: 0,
                    viewOffsetMs = parser.attr("viewOffset")?.toLongOrNull() ?: 0,
                    viewCount = parser.attr("viewCount")?.toIntOrNull() ?: 0,
                    thumb = parser.attr("thumb"),
                    art = parser.attr("art"),
                    parentRatingKey = parser.attr("parentRatingKey"),
                    parentKey = parser.attr("parentKey"),
                    grandparentRatingKey = parser.attr("grandparentRatingKey"),
                    librarySectionId = parser.attr("librarySectionID")
                        ?.takeIf { it.isNotBlank() }
                        ?: containerLibrarySectionId,
                    leafCount = parser.attr("leafCount")?.toIntOrNull() ?: 0,
                    viewedLeafCount =
                        parser.attr("viewedLeafCount")?.toIntOrNull() ?: 0,
                    childCount = parser.attr("childCount")?.toIntOrNull() ?: 0,
                    episodeNumber = parser.attr("index")?.toIntOrNull(),
                    seasonNumber = parser.attr("parentIndex")?.toIntOrNull(),
                    addedAtSeconds = parser.attr("addedAt")?.toLongOrNull()?.takeIf { it >= 0 },
                    grandparentTitle = grandparent,
                    grandparentThumb = parser.attr("grandparentThumb"),
                    grandparentArt = parser.attr("grandparentArt"),
                )
            }
        }
    }
}

private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

private fun XmlPullParser.videoDynamicRangeHint(): String? {
    attr("videoDynamicRange")?.let { return it }
    attr("dynamicRange")?.let { return it }
    if (attr("DOVIPresent") == "1") return "DOVI"
    if (
        attr("HDR10PlusPresent") == "1" ||
        attr("HDR10PlusMetadataPresent") == "1"
    ) {
        return "HDR10+"
    }
    val displaySignal = listOfNotNull(
        attr("extendedDisplayTitle"),
        attr("displayTitle"),
    ).joinToString(" ").lowercase()
    return when {
        "dovi" in displaySignal || "dolby vision" in displaySignal -> "DOVI"
        "hdr10+" in displaySignal || "hdr10plus" in displaySignal -> "HDR10+"
        "hdr10" in displaySignal -> "HDR10"
        "hlg" in displaySignal -> "HLG"
        else -> null
    }
}
