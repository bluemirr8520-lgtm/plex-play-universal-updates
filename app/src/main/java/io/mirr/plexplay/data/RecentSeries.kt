package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Build Show cards from episode addition dates, never from series registration dates. */
internal suspend fun loadRecentSeries(
    sectionId: String,
    limit: Int = 20,
    pageSize: Int = 100,
    maxPages: Int = 3,
    loadEpisodePage: suspend (start: Int, size: Int) -> List<PlexItem>,
    loadShowMetadata: suspend (ids: List<String>) -> List<PlexItem>,
): List<PlexItem> {
    require(limit > 0 && pageSize > 0 && maxPages > 0)
    val numericId = Regex("[0-9]+")
    val episodes = LinkedHashMap<String, PlexItem>()
    val seenEpisodes = mutableSetOf<String>()
    var start = 0
    for (pageIndex in 0 until maxPages) {
        currentCoroutineContext().ensureActive()
        val page = try {
            loadEpisodePage(start, pageSize)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A later optional page failing must not hide the rows already loaded.
            if (episodes.isEmpty()) throw error
            break
        }
        if (page.isEmpty()) break
        var newEpisodes = 0
        for (episode in page) {
            if (!seenEpisodes.add("${episode.type}-${episode.ratingKey}-${episode.key}")) continue
            newEpisodes++
            val parent = episode.grandparentRatingKey ?: continue
            if (episode.type != "episode" || !numericId.matches(parent) ||
                (episode.librarySectionId != null && episode.librarySectionId != sectionId)) continue
            val previous = episodes[parent]
            if (previous == null || (episode.addedAtSeconds ?: Long.MIN_VALUE) >
                (previous.addedAtSeconds ?: Long.MIN_VALUE)) episodes[parent] = episode
        }
        if (newEpisodes == 0 || episodes.size >= limit) break
        // PMS may cap a requested page: a short page is not proof of end-of-list.
        start += page.size
    }
    val recent = episodes.entries.sortedByDescending { it.value.addedAtSeconds ?: Long.MIN_VALUE }.take(limit)
    if (recent.isEmpty()) return emptyList()
    val ids = recent.map { it.key }
    val shows = try {
        loadShowMetadata(ids)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }
    currentCoroutineContext().ensureActive()
    val metadata = shows.filter {
        it.type == "show" && it.ratingKey in ids &&
            (it.librarySectionId == null || it.librarySectionId == sectionId)
    }.associateBy { it.ratingKey }
    return recent.mapNotNull { (id, episode) ->
        val series = metadata[id] ?: episode.grandparentTitle?.takeIf { it.isNotBlank() }?.let { title ->
            PlexItem(id, "/library/metadata/$id/children", "show", title,
                thumb = episode.grandparentThumb, art = episode.grandparentArt, librarySectionId = sectionId)
        } ?: return@mapNotNull null
        series.copy(latestEpisodeLabel = formatLatestEpisodeLabel(episode), librarySectionId = sectionId)
    }
}
