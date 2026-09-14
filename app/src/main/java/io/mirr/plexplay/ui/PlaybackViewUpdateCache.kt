package io.mirr.plexplay.ui

/** Suppress decoder-surface work caused only by controls/settings recomposition. */
internal class PlaybackViewUpdateCache {
    private var appliedTarget: Any? = null
    private var appliedValue: Any? = null

    fun apply(target: Any, value: Any, update: () -> Unit): Boolean {
        if (appliedTarget === target && appliedValue == value) return false
        // An update can change part of a surface before it fails. Invalidate
        // the old stamp too, so returning to the old value really restores it.
        appliedTarget = null
        appliedValue = null
        update()
        appliedTarget = target
        appliedValue = value
        return true
    }
}
