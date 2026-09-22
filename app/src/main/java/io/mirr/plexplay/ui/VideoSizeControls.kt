package io.mirr.plexplay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun VideoSizeControls(current: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SizeButton(Icons.Rounded.ZoomOut, "화면 축소", false) {
            onChange(steppedVideoSize(current, -1))
        }
        SizeButton(Icons.Rounded.ZoomIn, "화면 확대", false) {
            onChange(steppedVideoSize(current, 1))
        }
        SizeButton(
            if (current == "fill") Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            if (current == "fill") "화면 맞춤으로 복원" else "화면 꽉 채우기 · 가장자리 잘림",
            current == "fill",
        ) { onChange(if (current == "fill") "fit" else "fill") }
    }
}

@Composable
private fun SizeButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.onFocusChanged { focused = it.hasFocus },
        shape = CircleShape,
        color = if (focused) Color.White else Color.Black.copy(alpha = .72f),
        border = if (focused) BorderStroke(3.dp, Color(0xFFE5A00D))
            else if (selected) BorderStroke(2.dp, Color(0xFFE5A00D)) else null,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = if (focused) Color.Black else Color.White)
        }
    }
}
