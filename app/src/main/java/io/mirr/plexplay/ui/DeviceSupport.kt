package io.mirr.plexplay.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

internal fun Context.isTelevisionDevice(): Boolean {
    val uiModeIsTelevision =
        (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    return uiModeIsTelevision ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
}
