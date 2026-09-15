package io.mirr.plexplay.ui

/**
 * Only one renderer may own a selected subtitle. External text stays app-owned
 * while loading and after an error, because silently falling back to native
 * VLC would discard the user's Typeface and may duplicate the app overlay.
 */
internal fun appOwnsVlcSubtitleSelection(
    disabled: Boolean,
    textSelected: Boolean,
    externalTextSelected: Boolean,
    directLoadFailed: Boolean,
    directOverlayActive: Boolean,
    bridgeEnabled: Boolean,
    bridgeActive: Boolean,
    bridgePreparing: Boolean,
    bridgeBitmapOnly: Boolean,
): Boolean {
    if (disabled) return false
    if (externalTextSelected) return true
    if (textSelected && !directLoadFailed) return true
    if (directOverlayActive) return true
    return bridgeEnabled && !bridgeBitmapOnly && (bridgeActive || bridgePreparing)
}
