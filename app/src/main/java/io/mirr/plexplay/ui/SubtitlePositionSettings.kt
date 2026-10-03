package io.mirr.plexplay.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val SubtitlePositionFocusColor = Color(0xFFFFD400)

@Composable
internal fun SubtitlePositionSettings(
    horizontalOffset: Int,
    verticalOffset: Int,
    verticalWriting: Boolean,
    onPositionChanged: (Int, Int) -> Unit,
    onVerticalWritingChanged: (Boolean) -> Unit,
) {
    val horizontalFocus = remember { FocusRequester() }
    val verticalFocus = remember { FocusRequester() }
    val resetFocus = remember { FocusRequester() }
    val writingFocus = remember { FocusRequester() }

    SubtitlePositionSlider(
        label = "가로 위치",
        hint = "− 왼쪽 · + 오른쪽",
        value = horizontalOffset,
        focus = horizontalFocus,
        down = verticalFocus,
        onChange = { onPositionChanged(it, verticalOffset) },
    )
    SubtitlePositionSlider(
        label = "세로 위치",
        hint = "− 위 · + 아래",
        value = verticalOffset,
        focus = verticalFocus,
        up = horizontalFocus,
        down = resetFocus,
        onChange = { onPositionChanged(horizontalOffset, it) },
    )
    SubtitlePositionAction(
        label = "자막 위치 가운데로",
        focus = resetFocus,
        up = verticalFocus,
        down = writingFocus,
        onClick = { onPositionChanged(0, 0) },
    )
    SubtitlePositionAction(
        label = if (verticalWriting) "자막 세로쓰기: 켬" else "자막 세로쓰기: 끔",
        focus = writingFocus,
        up = resetFocus,
        onClick = { onVerticalWritingChanged(!verticalWriting) },
    )
    Text(
        text = "세로쓰기를 켜면 오른쪽 기본 위치(0%, 0%)로 돌아갑니다. " +
            "영문·숫자는 한 글자씩 세우고, 어절 단위로 줄을 바꾸며 괄호 방향을 유지합니다.",
        color = Color.LightGray,
    )
    Text(
        text = "리모컨 좌우: 5% 조절 · 위아래: 항목 이동",
        color = Color.LightGray,
    )
}

@Composable
private fun SubtitlePositionSlider(
    label: String,
    hint: String,
    value: Int,
    focus: FocusRequester,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    onChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "$label  ${if (value > 0) "+" else ""}$value%",
            color = if (focused) SubtitlePositionFocusColor else Color.White,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        )
        Slider(
            value = value.coerceIn(-100, 100).toFloat(),
            valueRange = -100f..100f,
            steps = 39,
            onValueChange = { onChange((it / 5f).roundToInt() * 5) },
            colors = SliderDefaults.colors(
                thumbColor = if (focused) SubtitlePositionFocusColor else Color.Transparent,
                activeTrackColor = if (focused) SubtitlePositionFocusColor else Color.Gray,
                inactiveTrackColor = Color.DarkGray,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused || it.hasFocus }
                .onPreviewKeyEvent {
                    val event = it.nativeKeyEvent
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                        event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    ) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            val delta = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -5 else 5
                            onChange((value + delta).coerceIn(-100, 100))
                        }
                        true
                    } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            val movingUp = event.keyCode == KeyEvent.KEYCODE_DPAD_UP
                            val target = if (movingUp) up else down
                            if (target != null) {
                                target.requestFocus()
                            } else {
                                focusManager.moveFocus(
                                    if (movingUp) FocusDirection.Up else FocusDirection.Down,
                                )
                            }
                        }
                        // Consume both actions even at the edge: Slider's
                        // default Up/Down behavior changes its value.
                        true
                    } else {
                        moveSubtitlePositionFocus(event, up, down)
                    }
                },
        )
        Text(hint, color = Color.LightGray)
    }
}

@Composable
private fun SubtitlePositionAction(
    label: String,
    focus: FocusRequester,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { moveSubtitlePositionFocus(it.nativeKeyEvent, up, down) },
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.Gray),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (focused) SubtitlePositionFocusColor else Color.Black,
            contentColor = if (focused) Color.Black else Color.White,
        ),
    ) {
        Text(label, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun moveSubtitlePositionFocus(
    event: KeyEvent,
    up: FocusRequester?,
    down: FocusRequester?,
): Boolean {
    val target = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> up
        KeyEvent.KEYCODE_DPAD_DOWN -> down
        else -> null
    } ?: return false
    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) target.requestFocus()
    return true
}
