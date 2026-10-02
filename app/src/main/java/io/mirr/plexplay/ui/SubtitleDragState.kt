package io.mirr.plexplay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

internal data class SubtitleDragPosition(val horizontal: Int, val vertical: Int)

/** Transient rendering state only. Persist the result of finish(), never a move. */
internal class SubtitleDragState {
    var preview: SubtitleDragPosition? by mutableStateOf(null)
        private set

    private var origin: SubtitleDragPosition? = null

    fun start(horizontal: Int, vertical: Int) {
        cancel()
        origin = SubtitleDragPosition(horizontal.coerceIn(-100, 100), vertical.coerceIn(-100, 100))
    }

    fun move(totalX: Float, totalY: Float, width: Int, height: Int): SubtitleDragPosition? {
        val start = origin ?: return null
        if (width <= 0 || height <= 0 || !totalX.isFinite() || !totalY.isFinite()) return preview
        return SubtitleDragPosition(
            (start.horizontal + totalX / width * 100f).coerceIn(-100f, 100f).roundToInt(),
            (start.vertical + totalY / height * 100f).coerceIn(-100f, 100f).roundToInt(),
        ).also { preview = it }
    }

    fun finish(): SubtitleDragPosition? = preview.also { cancel() }

    fun cancel() {
        preview = null
        origin = null
    }
}
