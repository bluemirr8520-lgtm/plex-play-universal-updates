package io.mirr.plexplay.data

import kotlinx.coroutines.delay

internal data class CompletedCollectionChange(val item: PlexItem, val tag: String)

/** Routes completion to a movie or a fully watched show, never an episode/season. */
internal class CompletedCollectionUpdater(
    private val metadata: suspend (String) -> PlexItem?,
    private val sections: suspend () -> List<PlexSection>,
    private val episodes: suspend (String) -> List<PlexItem>,
    private val replace: suspend (PlexItem, PlexSection, String) -> Unit,
    private val isCurrent: () -> Boolean,
    private val waitForReadback: suspend (Int) -> Unit = { delay(150L * it) },
) {
    suspend fun update(
        completed: PlexItem,
        settings: WatchedCollectionSettings,
        playedFilePath: String? = null,
        usePlayedFilePath: Boolean = false,
    ): CompletedCollectionChange? {
        if (!isCurrent()) return null
        return when (completed.type) {
            "show", "season", "episode" -> updateSeries(completed, settings)
            "movie", "video", "clip" -> {
                val section = sectionFor(completed)
                val tag = if (usePlayedFilePath) {
                    watchedCollectionTag(section.title, completed.type, playedFilePath, settings)
                } else {
                    manualWatchedCollectionTag(section.title, completed.type, completed.mediaFilePaths, settings)
                } ?: throw PlexException("파일 경로가 불명확하거나 여러 파일 버전의 규칙이 달라 컬렉션을 변경하지 않았습니다.")
                apply(completed, section, tag)
            }
            else -> null
        }
    }

    private suspend fun updateSeries(completed: PlexItem, settings: WatchedCollectionSettings): CompletedCollectionChange? {
        val seriesKey = when (completed.type) {
            "show" -> completed.ratingKey
            "season" -> completed.parentRatingKey
            else -> completed.grandparentRatingKey
        }?.takeIf { it.matches(Regex("[0-9]+")) } ?: return null
        if (completed.librarySectionId.isNullOrBlank()) return null

        var unresolvedCompleteSeries = false
        // PMS may update the episode before its parent counters. Retry reads only;
        // completing an episode must NEVER mark the rest of the show watched.
        repeat(3) { attempt ->
            if (attempt > 0) waitForReadback(attempt)
            if (!isCurrent()) return null
            val series = metadata(seriesKey)
                ?: throw PlexException("시리즈의 시청 완료 상태를 확인하지 못했습니다.")
            if (series.ratingKey != seriesKey || series.type != "show" ||
                series.librarySectionId != completed.librarySectionId
            ) throw PlexException("같은 라이브러리의 시리즈인지 확인할 수 없어 컬렉션을 변경하지 않았습니다.")

            unresolvedCompleteSeries = false
            if (!series.matchesSavedWatchedState(true)) return@repeat
            val allEpisodes = episodes(seriesKey)
            val includesCompletedItem = when (completed.type) {
                "episode" -> allEpisodes.any { it.ratingKey == completed.ratingKey }
                "season" -> allEpisodes.any { it.parentRatingKey == completed.ratingKey }
                else -> true
            }
            val section = sectionFor(series)
            val tag = seriesWatchedCollectionTag(section.title, series, allEpisodes, settings)
            if (!includesCompletedItem || tag == null) {
                unresolvedCompleteSeries = true
                return@repeat
            }
            return apply(series, section, tag)
        }
        if (unresolvedCompleteSeries) {
            throw PlexException("시리즈의 전체 에피소드·완료 상태·파일 경로 규칙을 확인할 수 없어 컬렉션을 변경하지 않았습니다.")
        }
        // A partially watched show is expected, not a collection failure.
        return null
    }

    private suspend fun sectionFor(item: PlexItem): PlexSection =
        sections().singleOrNull { it.key == item.librarySectionId }
            ?: throw PlexException("영상의 라이브러리를 확인하지 못했습니다.")

    private suspend fun apply(item: PlexItem, section: PlexSection, tag: String): CompletedCollectionChange? {
        check(item.type in setOf("movie", "video", "clip", "show"))
        if (!isCurrent()) return null
        replace(item, section, tag)
        return CompletedCollectionChange(item.copy(collections = listOf(tag)), tag)
    }
}
