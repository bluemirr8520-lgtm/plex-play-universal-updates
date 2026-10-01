package io.mirr.plexplay.data

private val WatchedCollectionVideoTypes = setOf("movie", "episode", "clip", "video")

private val WatchedCollectionCommonBase = "GDRIVE/VIDEO/AV/자막B".split('/')

/** Match complete physical directory components, never a filename or partial folder name. */
private fun isSpecialCollectionFolder(folder: String): Boolean {
    val ignoreCase = folder.startsWith("windows:") || folder.startsWith("unc:")
    val components = when {
        folder.startsWith("posix:/") -> folder.removePrefix("posix:/").split('/')
        folder.startsWith("windows:") -> folder.removePrefix("windows:").split('/').drop(1)
        // The UNC host and share are not physical folders within the share.
        folder.startsWith("unc://") -> folder.removePrefix("unc://").split('/').drop(2)
        else -> return false
    }
    return components.windowed(WatchedCollectionCommonBase.size).withIndex().any { (index, candidate) ->
        val matchesBase = candidate.zip(WatchedCollectionCommonBase)
            .all { (actual, expected) -> actual.equals(expected, ignoreCase) }
        val belowBase = components.drop(index + WatchedCollectionCommonBase.size)
        matchesBase && (
            belowBase.any { it.equals("NO_META", ignoreCase) } ||
                belowBase.firstOrNull() == "기타"
            )
    }
}

private val PlexApiFolders = listOf(
    "posix:/library/metadata",
    "posix:/library/streams",
    "posix:/video/:/transcode",
)

// The repository resolves the item's actual registered section before calling
// this path policy. Episode paths are used to classify their parent show only;
// they are not permission to write episode collections. The API rejects that type.
// New or renamed libraries must not require an app allowlist update.
internal fun managesWatchedCollections(libraryTitle: String?, mediaType: String): Boolean =
    !libraryTitle.isNullOrBlank() && mediaType in WatchedCollectionVideoTypes

/** filePath must come from the played Part.file, not Part.key or a playback URL. */
internal fun watchedCollectionTag(
    libraryTitle: String?,
    mediaType: String,
    filePath: String?,
    settings: WatchedCollectionSettings = WatchedCollectionSettings(),
): String? {
    if (!managesWatchedCollections(libraryTitle, mediaType)) return null
    val names = settings.normalizedOrNull() ?: return null
    // Unusual physical paths are ambiguous for a destructive tag replacement.
    // This guard does not change playback-folder navigation's normalization.
    if (filePath == null || filePath.any { it.code < 32 } ||
        filePath.replace('\\', '/').split('/').any { it == ".." }
    ) return null
    val folder = playbackFolderKey(filePath) ?: return null
    if (PlexApiFolders.any { folder == it || folder.startsWith("$it/") }) return null
    return if (isSpecialCollectionFolder(folder)) {
        names.specialName
    } else {
        names.defaultName
    }
}

/** Manual completion has no played Part: every version must agree on one rule. */
internal fun manualWatchedCollectionTag(
    libraryTitle: String?,
    mediaType: String,
    filePaths: List<String>,
    settings: WatchedCollectionSettings = WatchedCollectionSettings(),
): String? {
    if (filePaths.isEmpty()) return null
    val tags = filePaths.map { watchedCollectionTag(libraryTitle, mediaType, it, settings) }
    if (tags.any { it == null }) return null
    return tags.distinct().singleOrNull()
}
