package io.mirr.plexplay.data

data class WatchedCollectionSettings(
    val defaultName: String = "KILL",
    val specialName: String = "123",
) {
    fun normalizedOrNull(): WatchedCollectionSettings? {
        val normal = normalizeCollectionName(defaultName) ?: return null
        val special = normalizeCollectionName(specialName) ?: return null
        return copy(defaultName = normal, specialName = special)
    }
}

internal const val DefaultCollectionNameKey = "watched_collection_default_name"
internal const val SpecialCollectionNameKey = "watched_collection_special_name"

internal fun normalizeCollectionName(value: String): String? {
    if (value.any { it.isISOControl() }) return null
    return value.trim().takeIf { it.isNotEmpty() && it.codePointCount(0, it.length) <= 100 }
}

internal fun readWatchedCollectionSettings(read: (String) -> String?): WatchedCollectionSettings {
    val defaults = WatchedCollectionSettings()
    return WatchedCollectionSettings(
        defaultName = read(DefaultCollectionNameKey)?.let(::normalizeCollectionName) ?: defaults.defaultName,
        specialName = read(SpecialCollectionNameKey)?.let(::normalizeCollectionName) ?: defaults.specialName,
    )
}
