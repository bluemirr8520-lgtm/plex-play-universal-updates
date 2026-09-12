package io.mirr.plexplay.data

private val WatchedCollectionLibraries = setOf(
    "AV-모자이크제거",
    "AV-자막A",
    "AV-자막B",
    "AV1",
)

private val WatchedCollectionVideoTypes = setOf("movie", "episode", "clip", "video")

private val WatchedCollectionSpecialFolders = listOf(
    "posix:/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/NO_META",
    "posix:/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Uncensored/NO_META",
    "posix:/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/Western/NO_META",
    "posix:/mnt/GDS2/GDRIVE/VIDEO/AV/자막B/기타",
)

private val PlexApiFolders = listOf(
    "posix:/library/metadata",
    "posix:/library/streams",
    "posix:/video/:/transcode",
)

internal fun managesWatchedCollections(libraryTitle: String?, mediaType: String): Boolean =
    libraryTitle in WatchedCollectionLibraries && mediaType in WatchedCollectionVideoTypes

/** filePath must come from the played Part.file, not Part.key or a playback URL. */
internal fun watchedCollectionTag(
    libraryTitle: String?,
    mediaType: String,
    filePath: String?,
): String? {
    if (!managesWatchedCollections(libraryTitle, mediaType)) return null
    val folder = playbackFolderKey(filePath) ?: return null
    if (PlexApiFolders.any { folder == it || folder.startsWith("$it/") }) return null
    return if (WatchedCollectionSpecialFolders.any { folder == it || folder.startsWith("$it/") }) {
        "123"
    } else {
        "KILL"
    }
}
