package io.mirr.plexplay.data

data class WatchedActionResult(val item: PlexItem?, val notice: String? = null)

/** Verify persisted server counters, not the local progress-based display shortcut. */
internal fun PlexItem.matchesSavedWatchedState(watched: Boolean): Boolean =
    if (isSeriesContainer) {
        leafCount > 0 && if (watched) viewedLeafCount >= leafCount else viewedLeafCount == 0
    } else if (watched) {
        viewCount > 0
    } else {
        viewCount == 0 && viewOffsetMs == 0L
    }

/** A series gets one collection only when every original episode path agrees. */
internal fun seriesWatchedCollectionTag(
    libraryTitle: String,
    series: PlexItem,
    episodes: List<PlexItem>,
): String? {
    if (series.type != "show" || !series.isWatched || series.librarySectionId.isNullOrBlank() ||
        episodes.isEmpty() || episodes.size != series.leafCount ||
        episodes.map { it.ratingKey }.distinct().size != episodes.size ||
        episodes.any {
            it.type != "episode" || it.ratingKey.isBlank() ||
                it.grandparentRatingKey != series.ratingKey ||
                it.librarySectionId != series.librarySectionId || it.viewCount <= 0 || it.mediaFilePaths.isEmpty()
        }
    ) return null
    return manualWatchedCollectionTag(libraryTitle, "episode", episodes.flatMap { it.mediaFilePaths })
}

/** Do not accept a truncated first page, including servers with a smaller page cap. */
internal suspend fun loadAllSeriesEpisodes(loadPage: suspend (start: Int) -> List<PlexItem>): List<PlexItem> {
    val episodes = mutableListOf<PlexItem>()
    val seen = mutableSetOf<String>()
    while (true) {
        val page = loadPage(episodes.size)
        if (page.isEmpty()) return episodes
        if (page.any { it.ratingKey.isBlank() || !seen.add(it.ratingKey) }) {
            throw PlexException("시리즈 에피소드 목록이 반복되거나 불명확하여 컬렉션 변경을 중지했습니다.")
        }
        episodes += page
    }
}
