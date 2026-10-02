package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun playbackCandidatePath(item: PlexItem): String? {
    if (item.type == "episode") {
        item.grandparentRatingKey?.takeIf(String::isNotBlank)?.let {
            return "/library/metadata/$it/allLeaves"
        }
        val parent = item.parentKey ?: item.parentRatingKey?.let { "/library/metadata/$it" }
        if (!parent.isNullOrBlank()) return if (parent.endsWith("/children")) parent else "$parent/children"
    }
    return item.librarySectionId?.takeIf(String::isNotBlank)?.let { "/library/sections/$it/all" }
}

/** Paging must not stop at a server-imposed page size smaller than the requested size. */
internal suspend fun loadPlaybackCandidatePages(loadPage: suspend (Int) -> List<PlexItem>): List<PlexItem> {
    val result = mutableListOf<PlexItem>()
    val seen = mutableSetOf<String>()
    var offset = 0
    while (true) {
        val page = loadPage(offset)
        if (page.isEmpty()) return result
        val fresh = page.filter { it.ratingKey.isNotBlank() && seen.add(it.ratingKey) }
        if (fresh.isEmpty()) return result // Some servers ignore the paging offset.
        result += fresh
        offset += page.size
    }
}

/** Merge complete lists before filtered/on-deck rows, and resolve missing Part.file evidence. */
internal suspend fun discoverSameFolderPlaybackQueue(
    current: PlexItem,
    candidateLists: List<List<PlexItem>>,
    loadDetails: suspend (PlexItem) -> PlexItem,
): List<PlexItem> {
    if (playbackFolderKey(current.filePath) == null) return listOf(current)
    val candidates = linkedMapOf<String, PlexItem>()
    for (list in candidateLists) {
        // A folder/section response not containing the active item is not its navigation list.
        if (list.none { it.ratingKey == current.ratingKey }) continue
        for (item in list.filter { it.isPlayable }) {
            val previous = candidates[item.ratingKey]
            if (previous == null || (playbackFolderKey(previous.filePath) == null && playbackFolderKey(item.filePath) != null)) {
                candidates[item.ratingKey] = item
            }
        }
    }
    if (candidates.isEmpty()) return listOf(current)
    val resolved = mutableListOf<PlexItem>()
    // Bounded concurrency and batches: avoid one coroutine/request per whole library at once.
    for (batch in candidates.values.chunked(4)) {
        resolved += coroutineScope {
            batch.map { candidate -> async {
                when {
                    candidate.ratingKey == current.ratingKey -> current
                    playbackFolderKey(candidate.filePath) != null -> candidate
                    else -> try {
                        loadDetails(candidate).takeIf { detail ->
                            detail.ratingKey == candidate.ratingKey &&
                                (candidate.librarySectionId == null || detail.librarySectionId == null ||
                                    candidate.librarySectionId == detail.librarySectionId)
                        } ?: candidate
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        candidate // Unknown folders never acquire a guessed file path.
                    }
                }
            } }.awaitAll()
        }
    }
    return sameFolderPlaybackQueue(current, resolved)
}
