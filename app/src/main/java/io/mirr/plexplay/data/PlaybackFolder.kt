package io.mirr.plexplay.data

import java.util.Locale

/** The actual server file's immediate folder, never a Plex metadata/stream URL. */
internal fun playbackFolderKey(path: String?): String? {
    if (path.isNullOrBlank() || path.contains('\u0000')) return null
    val windowsDrive = Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(path)
    val unc = path.startsWith("\\\\") ||
        (path.startsWith("//") && !path.startsWith("///"))
    // A backslash in a POSIX filename is literal, not a directory separator.
    val normalized = if (windowsDrive || unc) path.replace('\\', '/') else path
    if (normalized.contains("://") || normalized.endsWith('/')) return null
    val absolutePosix = normalized.startsWith('/') && !unc
    if (!windowsDrive && !unc && !absolutePosix) return null
    val body = when {
        windowsDrive -> normalized.substring(3)
        unc -> normalized.substring(2)
        else -> normalized.substring(1)
    }
    val components = body.split('/').filter { it.isNotEmpty() }
    if (components.isEmpty() || components.last() in setOf(".", "..")) return null
    if (unc && (components.size < 3 || components.take(2).any { it in setOf(".", "..") })) {
        return null
    }
    val minimumDepth = if (unc) 2 else 0
    val folder = mutableListOf<String>()
    for (component in components.dropLast(1)) {
        when (component) {
            "." -> Unit
            ".." -> {
                if (folder.size <= minimumDepth) return null
                folder.removeAt(folder.lastIndex)
            }
            else -> folder.add(component)
        }
    }
    val parent = folder.joinToString("/")
    return when {
        windowsDrive -> "windows:${normalized.take(2)}/$parent".lowercase(Locale.ROOT)
        unc -> "unc://$parent".lowercase(Locale.ROOT)
        // Part.key is a playback endpoint, not evidence of the original file's folder.
        parent == "library/parts" || parent.startsWith("library/parts/") -> null
        else -> "posix:/$parent"
    }
}

internal fun sharesPlaybackFolder(current: PlexItem, candidate: PlexItem): Boolean {
    if (!current.librarySectionId.isNullOrBlank() &&
        !candidate.librarySectionId.isNullOrBlank() &&
        current.librarySectionId != candidate.librarySectionId
    ) return false
    val folder = playbackFolderKey(current.filePath) ?: return false
    return folder == playbackFolderKey(candidate.filePath)
}

/** A queue cannot outlive the verified file path of the active playback source. */
internal fun validatedPlaybackNeighbor(
    current: PlexItem?,
    source: PlaybackSource?,
    candidate: PlexItem?,
): PlexItem? {
    if (current == null || source == null || candidate == null) return null
    val sourceFolder = playbackFolderKey(source.filePath) ?: return null
    if (source.ratingKey != current.ratingKey ||
        sourceFolder != playbackFolderKey(current.filePath)
    ) return null
    return candidate.takeIf { it.isPlayable && sharesPlaybackFolder(current, it) }
}

/** Keep the existing episode/list order without borrowing entries from another folder. */
internal fun sameFolderPlaybackQueue(
    current: PlexItem,
    candidates: List<PlexItem>,
): List<PlexItem> {
    if (playbackFolderKey(current.filePath) == null ||
        candidates.none { it.ratingKey == current.ratingKey }
    ) return listOf(current)
    return candidates
        .map { if (it.ratingKey == current.ratingKey) current else it }
        .filter { it.isPlayable && sharesPlaybackFolder(current, it) }
        .distinctBy { it.ratingKey }
        .ifEmpty { listOf(current) }
}
