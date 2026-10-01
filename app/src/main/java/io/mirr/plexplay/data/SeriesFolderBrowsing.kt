package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException

/** Plex's synthetic All Episodes row is an allLeaves route, not a named season. */
private val MetadataNodePath = Regex("^/library/metadata/([0-9]+)(?:/children)?$")
private val AllEpisodesPath = Regex("^/library/metadata/([0-9]+)/allLeaves$")

private fun String.browsePath(): String =
    substringBefore('#').substringBefore('?').trimEnd('/').let { if (it.startsWith('/')) it else "/$it" }

private fun PlexItem.metadataFolderId(): String? =
    ratingKey.takeIf { it.matches(Regex("[0-9]+")) }
        ?: MetadataNodePath.matchEntire(key.browsePath())?.groupValues?.get(1)
        ?: MetadataNodePath.matchEntire(ratingKey.browsePath())?.groupValues?.get(1)

private fun PlexItem.allEpisodesShowId(): String? =
    key.takeIf { it.isNotBlank() }?.browsePath()
        ?.let { AllEpisodesPath.matchEntire(it)?.groupValues?.get(1) }

/** Playable episodes still open their details; only containers go straight to a list. */
internal val PlexItem.opensEpisodeList: Boolean
    get() = !isPlayable && type in setOf("show", "season", "directory")

/** Skip at most one unambiguous folder, never flatten multiple seasons or play a video. */
internal suspend fun episodeBrowseItems(
    parent: PlexItem,
    readChildren: suspend (PlexItem) -> List<PlexItem>,
): List<PlexItem> {
    val children = visibleSeriesChildren(parent, readChildren(parent))
    if (!parent.opensEpisodeList || parent.allEpisodesShowId() != null) return children
    val parentId = parent.metadataFolderId()
    val realChildren = children.filterNot {
        parent.type == "show" && parentId != null && !it.isPlayable && it.allEpisodesShowId() == parentId
    }
    // Keep mixed lists and duplicate/ambiguous rows intact rather than guessing.
    val folder = realChildren.singleOrNull()?.takeIf {
        !it.isPlayable && it.type in setOf("season", "directory") &&
            it.allEpisodesShowId() == null &&
            (it.key.isBlank() || parent.key.isBlank() || it.key.browsePath() != parent.key.browsePath()) &&
            (it.metadataFolderId() != null || it.key.isNotBlank()) &&
            (parentId == null || it.metadataFolderId() != parentId) &&
            (parentId == null || it.parentRatingKey.isNullOrBlank() || it.parentRatingKey == parentId)
    } ?: return children
    return try {
        visibleSeriesChildren(folder, readChildren(folder)).ifEmpty { children }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // The original folder is still usable if the optional second read fails.
        children
    }
}

/**
 * Count distinct folder entries returned for this show, not episode counters.
 * Keep their order and leave every real season/episode untouched.
 */
internal fun visibleSeriesChildren(parent: PlexItem, children: List<PlexItem>): List<PlexItem> {
    if (parent.type != "show" || parent.isPlayable) return children
    val showId = parent.metadataFolderId() ?: return children
    val folderCount = children.asSequence()
        .filter { !it.isPlayable && it.type in setOf("season", "directory") && it.allEpisodesShowId() == null }
        .mapNotNull { child ->
            child.metadataFolderId()?.let { "metadata:$it" }
                ?: child.key.takeIf { it.isNotBlank() }?.browsePath()?.let { "path:$it" }
        }
        .distinct()
        .take(2)
        .count()
    if (folderCount < 2) return children
    return children.filterNot { !it.isPlayable && it.allEpisodesShowId() == showId }
}

/** Both children and allLeaves are already list endpoints. */
internal fun plexChildrenPath(item: PlexItem): String {
    if (item.key.isBlank()) return "/library/metadata/${item.ratingKey}/children"
    val path = item.key.browsePath()
    if (path.endsWith("/children") || AllEpisodesPath.matches(path)) return item.key
    val queryStart = item.key.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: item.key.length
    return item.key.substring(0, queryStart).trimEnd('/') + "/children" + item.key.substring(queryStart)
}
