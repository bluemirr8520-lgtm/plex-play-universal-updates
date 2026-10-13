package io.mirr.plexplay.ui

import android.view.View
import android.widget.TextView
import androidx.media3.ui.TimeBar

/** Preview only: never asks the decoder/network to seek while the remote is held. */
internal fun updateRemoteSeekPreview(controller: View?, positionMs: Long, label: String) {
    (controller?.findViewById<View>(androidx.media3.ui.R.id.exo_progress) as? TimeBar)
        ?.setPosition(positionMs)
    controller?.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)?.let {
        if (it.text.toString() != label) it.text = label
    }
}
