package io.mirr.plexplay.data

/** Reject late end events from a previous video or playback session. */
internal fun matchesPlaybackCompletion(active: PlaybackSource?, completed: PlaybackSource): Boolean =
    active != null && active.playbackId == completed.playbackId &&
        active.ratingKey == completed.ratingKey && active.url == completed.url &&
        active.token == completed.token

internal fun validatedPlaybackConnection(
    source: PlaybackSource,
    current: PlexConnection,
): PlexConnection? = current.takeIf {
    it.isConfigured && it.baseUrl == source.serverBaseUrl && it.token == source.token
}
