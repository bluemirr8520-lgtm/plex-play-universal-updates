package io.mirr.plexplay.ui

import android.view.View

internal fun View.updatePlaybackView(
    cacheId: Int,
    target: View,
    value: Any,
    update: () -> Unit,
) {
    val cache = getTag(cacheId) as? PlaybackViewUpdateCache
        ?: PlaybackViewUpdateCache().also { setTag(cacheId, it) }
    cache.apply(target, value, update)
}
