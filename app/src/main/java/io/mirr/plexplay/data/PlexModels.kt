package io.mirr.plexplay.data

data class PlexConnection(
    val baseUrl: String = "",
    val token: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

data class PlexServer(
    val name: String,
    val version: String,
)

data class PlexSection(
    val key: String,
    val title: String,
    val type: String,
    val thumb: String? = null,
)

data class PlexItem(
    val ratingKey: String,
    val key: String,
    val type: String,
    val title: String,
    val subtitle: String? = null,
    val summary: String? = null,
    val year: Int? = null,
    val durationMs: Long = 0,
    val viewOffsetMs: Long = 0,
    val viewCount: Int = 0,
    val thumb: String? = null,
    val art: String? = null,
    val partKey: String? = null,
    val filePath: String? = null,
    val container: String? = null,
    val videoCodec: String? = null,
    val videoResolution: String? = null,
    val videoProfile: String? = null,
    val videoBitDepth: Int? = null,
    val videoDynamicRange: String? = null,
    val videoColorPrimaries: String? = null,
    val videoColorTransfer: String? = null,
    val dolbyVisionProfile: Int? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioLanguage: String? = null,
    val audioProfile: String? = null,
    val audioDisplayTitle: String? = null,
    val parentRatingKey: String? = null,
    val parentKey: String? = null,
    val grandparentRatingKey: String? = null,
    val librarySectionId: String? = null,
    val actors: List<PlexTag> = emptyList(),
    val genres: List<PlexTag> = emptyList(),
    val subtitles: List<PlexSubtitle> = emptyList(),
    val leafCount: Int = 0,
    val viewedLeafCount: Int = 0,
    val childCount: Int = 0,
) {
    val isPlayable: Boolean
        get() = type in setOf("movie", "episode", "clip", "video", "track") || partKey != null

    val progress: Float
        get() = if (durationMs > 0) (viewOffsetMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isWatched: Boolean
        get() = viewCount > 0 || progress >= .95f

    val unwatchedEpisodeCount: Int
        get() = if (type == "show" || type == "season") {
            (leafCount - viewedLeafCount).coerceAtLeast(0)
        } else {
            0
        }
}

data class PlexTag(
    val tag: String,
    val id: String? = null,
    val filter: String? = null,
)

data class PlexRelatedContent(
    val actorWorks: List<PlexItem> = emptyList(),
    val similarGenreWorks: List<PlexItem> = emptyList(),
)

data class PlexSubtitle(
    val key: String?,
    val streamId: String? = null,
    val isEmbedded: Boolean = false,
    val partKey: String? = null,
    val mediaIndex: Int = 0,
    val partIndex: Int = 0,
    val language: String?,
    val title: String?,
    val codec: String?,
    val selected: Boolean,
)

data class PlaybackSubtitle(
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
    val streamId: String? = null,
    val isEmbedded: Boolean = false,
    val language: String?,
    val label: String,
    val mimeType: String,
    val codec: String? = null,
    val selected: Boolean,
) {
    val stableId: String
        get() = streamId?.let { "stream:$it" } ?: url
}

data class PlaybackSource(
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
    val filePath: String? = null,
    val token: String,
    val title: String,
    val subtitle: String?,
    val ratingKey: String,
    val durationMs: Long,
    val resumePositionMs: Long,
    val subtitles: List<PlaybackSubtitle> = emptyList(),
    val compatibilitySubtitles: List<PlaybackSubtitle> = emptyList(),
    val videoCodec: String? = null,
    val videoDynamicRange: String? = null,
    val videoProfile: String? = null,
    val videoColorPrimaries: String? = null,
    val videoColorTransfer: String? = null,
    val dolbyVisionProfile: Int? = null,
    val audioCodec: String? = null,
)

enum class PlaybackQuality(
    val label: String,
    val resolution: String?,
    val maxBitrateKbps: Int?,
) {
    ORIGINAL("원본 화질", null, null),
    HD_1080("1080p", "1920x1080", 12_000),
    HD_720("720p", "1280x720", 4_000),
    SD_480("480p", "720x480", 2_000),
}

class PlexException(message: String, cause: Throwable? = null) : Exception(message, cause)
