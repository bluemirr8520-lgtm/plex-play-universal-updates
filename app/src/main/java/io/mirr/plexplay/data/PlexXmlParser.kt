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
        var mediaIndex = -1
        var partIndex = -1
        var currentPartKey: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Video", "Directory", "Track", "Photo" -> {
                        if (current == null) {
                            current = MutableItem.from(parser)
                            mediaIndex = -1
                            partIndex = -1
                            currentPartKey = null
                        }
                    }
                    "Media" -> current?.apply {
                        mediaIndex++
                        partIndex = -1
                        currentPartKey = null
                        container = parser.attr("container") ?: container
                        videoCodec = parser.attr("videoCodec") ?: videoCodec
                        videoResolution =
                            parser.attr("videoResolution") ?: videoResolution
                        videoProfile = parser.attr("videoProfile") ?: videoProfile
                        videoBitDepth =
                            parser.attr("videoBitDepth")?.toIntOrNull() ?: videoBitDepth
                        videoDynamicRange =
                            parser.attr("videoDynamicRange") ?: videoDynamicRange
                        audioCodec = parser.attr("audioCodec") ?: audioCodec
                        audioChannels =
                            parser.attr("audioChannels")?.toIntOrNull() ?: audioChannels
                        audioProfile = parser.attr("audioProfile") ?: audioProfile
                    }
                    "Part" -> current?.apply {
                        partIndex++
                        currentPartKey = parser.attr("key")
                        partKey = currentPartKey
                        filePath = parser.attr("file") ?: filePath
                        container = parser.attr("container") ?: container
                    }
                    "Stream" -> {
                        if (current != null && parser.attr("streamType") == "1") {
                            current.videoCodec = parser.attr("codec") ?: current.videoCodec
                            current.videoProfile =
                                parser.attr("profile") ?: current.videoProfile
                            current.videoBitDepth =
                                parser.attr("bitDepth")?.toIntOrNull()
                                    ?: current.videoBitDepth
                            current.videoDynamicRange =
                                parser.videoDynamicRangeHint()
                                    ?: current.videoDynamicRange
                            current.videoColorPrimaries =
                                parser.attr("colorPrimaries")
                                    ?: current.videoColorPrimaries
                            current.videoColorTransfer =
                                parser.attr("colorTrc")
                                    ?: parser.attr("colorTransfer")
                                    ?: current.videoColorTransfer
                            current.dolbyVisionProfile =
                                parser.attr("DOVIProfile")?.toIntOrNull()
                                    ?: current.dolbyVisionProfile
                            if (current.videoResolution.isNullOrBlank()) {
                                val width = parser.attr("width")?.toIntOrNull()
                                val height = parser.attr("height")?.toIntOrNull()
                                current.videoResolution = when {
                                    width != null && height != null -> "${width}x$height"
                                    height != null -> "${height}p"
                                    else -> null
                                }
                            }
                        }
                        if (current != null && parser.attr("streamType") == "2") {
                            val selected = parser.attr("selected") == "1"
                            if (!current.audioStreamCaptured || selected) {
                                current.audioCodec =
                                    parser.attr("codec") ?: current.audioCodec
                                current.audioChannels =
                                    parser.attr("channels")?.toIntOrNull()
                                        ?: current.audioChannels
                                current.audioLanguage =
                                    parser.attr("language")
                                        ?: parser.attr("languageCode")
                                        ?: current.audioLanguage
                                current.audioProfile =
                                    parser.attr("profile") ?: current.audioProfile
                                current.audioDisplayTitle =
                                    parser.attr("extendedDisplayTitle")
                                        ?: parser.attr("displayTitle")
                                        ?: current.audioDisplayTitle
                                current.audioStreamCaptured = true
                            }
                        }
                        val subtitleKey = parser.attr("key")
                        val subtitleStreamId = parser.attr("id")
                        val subtitleIsEmbedded = when (parser.attr("external")) {
                            "1" -> false
                            "0" -> true
                            else -> subtitleKey.isNullOrBlank()
                        }
                        if (current != null &&
                            parser.attr("streamType") == "3" &&
                            (!subtitleKey.isNullOrBlank() || !subtitleStreamId.isNullOrBlank())
                        ) {
                            current.subtitles += PlexSubtitle(
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
                }
                XmlPullParser.END_TAG -> {
                    if (current != null && parser.name in setOf("Video", "Directory", "Track", "Photo")) {
                        result += current.toItem()
                        current = null
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
        var partKey: String? = null,
        var filePath: String? = null,
        var container: String? = null,
        var videoCodec: String? = null,
        var videoResolution: String? = null,
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
        val actors: MutableList<PlexTag> = mutableListOf(),
        val genres: MutableList<PlexTag> = mutableListOf(),
        val subtitles: MutableList<PlexSubtitle> = mutableListOf(),
    ) {
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
            container = container,
            videoCodec = videoCodec,
            videoResolution = videoResolution,
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
            subtitles = subtitles.toList(),
            leafCount = leafCount,
            viewedLeafCount = viewedLeafCount,
            childCount = childCount,
        )

        companion object {
            fun from(parser: XmlPullParser): MutableItem {
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
                    librarySectionId = parser.attr("librarySectionID"),
                    leafCount = parser.attr("leafCount")?.toIntOrNull() ?: 0,
                    viewedLeafCount =
                        parser.attr("viewedLeafCount")?.toIntOrNull() ?: 0,
                    childCount = parser.attr("childCount")?.toIntOrNull() ?: 0,
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
