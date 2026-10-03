package io.mirr.plexplay.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Small, memory-only cache. A server/account change drops every previous entry. */
internal class BrowseLookupCache<K, V>(
    private val capacity: Int,
    private val ttlMs: Long,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)
    private var scope: PlexConnection? = null
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)

    @Synchronized fun get(connection: PlexConnection, key: K): V? {
        if (scope != connection) {
            scope = connection
            entries.clear()
        }
        val entry = entries[key] ?: return null
        if (clockMs() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized fun put(connection: PlexConnection, key: K, value: V) {
        // An old cancelled request cannot repopulate the new account's cache.
        if (scope != connection) return
        entries[key] = Entry(value, clockMs() + ttlMs)
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    @Synchronized fun clear() {
        entries.clear()
        scope = null
    }
}

internal fun formatLatestEpisodeLabel(episode: PlexItem): String {
    val number = when {
        episode.seasonNumber != null && episode.episodeNumber != null ->
            "S${episode.seasonNumber}:E${episode.episodeNumber}"
        episode.episodeNumber != null -> "${episode.episodeNumber}화"
        else -> null
    }
    return listOfNotNull("최신", number, episode.title.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** Each successful filter is shown immediately; a slow genre lookup cannot hide actor results. */
internal suspend fun loadRelatedContent(
    selected: PlexItem,
    tagged: PlexItem,
    permits: Semaphore,
    timeoutMs: Long = 6_000,
    fetch: suspend (String) -> List<PlexItem>,
    onUpdate: (PlexRelatedContent) -> Unit = {},
): PlexRelatedContent = supervisorScope {
    fun filters(tags: List<PlexTag>, name: String) = tags.mapNotNull { tag ->
        tag.filter?.takeIf { it.substringBefore('=') == name && it.substringAfter('=', "").isNotBlank() }
            ?: tag.id?.takeIf { it.isNotBlank() }?.let { "$name=$it" }
    }.distinct().take(3)
    val actors = filters(tagged.actors, "actor")
    val genres = filters(tagged.genres, "genre")
    val results = mutableMapOf<String, List<PlexItem>>()
    val resultMutex = Mutex()
    fun candidates(keys: List<String>) = keys.asSequence().flatMap { results[it].orEmpty().asSequence() }
        .filter { it.type in setOf("movie", "show") && it.ratingKey != selected.ratingKey && it.ratingKey != tagged.ratingKey }
        .distinctBy { "${it.type}-${it.ratingKey}" }.take(20).toList()
    fun snapshot() = PlexRelatedContent(candidates(actors), candidates(genres))
    (actors + genres).distinct().map { filter ->
        async {
            val items = permits.withPermit {
                withTimeoutOrNull(timeoutMs) {
                    try {
                        fetch(filter)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        emptyList()
                    }
                }.orEmpty()
            }
            resultMutex.withLock {
                results[filter] = items
                onUpdate(snapshot())
            }
        }
    }.awaitAll()
    snapshot()
}
