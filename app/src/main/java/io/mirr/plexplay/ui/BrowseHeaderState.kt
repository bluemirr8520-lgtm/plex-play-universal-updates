package io.mirr.plexplay.ui

import io.mirr.plexplay.data.PlexConnection
import io.mirr.plexplay.data.PlexItem

/** Do not let an older read replace a new folder, account, or confirmed watched state. */
internal fun PlexUiState.withBrowseDetails(
    requested: PlexItem,
    detailed: PlexItem,
    expectedConnection: PlexConnection,
): PlexUiState {
    if (isHome || connection != expectedConnection || browsingItem != requested ||
        detailed.ratingKey != requested.ratingKey || detailed.isPlayable
    ) return this
    return copy(browsingItem = detailed)
}

/** Refresh badges in place after a series action without resetting search or navigation. */
internal fun PlexUiState.withBrowseItems(
    requested: PlexItem,
    refreshed: List<PlexItem>,
    expectedConnection: PlexConnection,
): PlexUiState {
    if (isHome || connection != expectedConnection || browsingItem != requested) return this
    return copy(items = refreshed)
}
