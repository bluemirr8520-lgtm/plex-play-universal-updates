package io.mirr.plexplay.ui

/** A device-specific cleanup failure must not skip the remaining release/restoration steps. */
internal fun cleanupPlayback(
    onFailure: (String, Exception) -> Unit,
    vararg actions: Pair<String, () -> Unit>,
) {
    actions.forEach { (name, action) ->
        try { action() } catch (error: Exception) { onFailure(name, error) }
    }
}
