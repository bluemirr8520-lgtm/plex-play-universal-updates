package io.mirr.plexplay.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Long press near the currently displayed subtitle, then drag in either direction. */
@Composable
internal fun Modifier.subtitleLongPressDrag(
    enabled: Boolean,
    appearance: SubtitleAppearance,
    onStart: () -> Unit,
    onMove: (Float, Float, Int, Int) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val currentAppearance by rememberUpdatedState(appearance)
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val finish by rememberUpdatedState(onFinish)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val style = currentAppearance
            val centerX = if (style.verticalWriting) size.width - 80.dp.toPx() else size.width / 2f
            val centerY = if (style.verticalWriting) size.height / 2f else size.height - 72.dp.toPx()
            val x = centerX + size.width * style.horizontalOffsetPercent / 100f
            val y = centerY + size.height * style.verticalOffsetPercent / 100f
            val halfWidth = size.width * if (style.verticalWriting) .22f else .44f
            val halfHeight = size.height * if (style.verticalWriting) .44f else .22f
            if (abs(down.position.x - x) > halfWidth || abs(down.position.y - y) > halfHeight) return@awaitEachGesture
            val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            pressed.consume()
            start()
            var previous = pressed.position
            var delta = Offset.Zero
            var finished = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pressed.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        finished = true
                        break
                    }
                    delta += change.position - previous
                    previous = change.position
                    change.consume()
                    move(delta.x, delta.y, size.width, size.height)
                }
            } finally {
                if (finished) finish() else cancel()
            }
        }
    }
}
