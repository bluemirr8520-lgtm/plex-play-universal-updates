package io.mirr.plexplay.ui

internal fun showLibraryContinueSection(hasLibrary: Boolean, browsingChildren: Boolean, query: String): Boolean =
    hasLibrary && !browsingChildren && query.isBlank()
