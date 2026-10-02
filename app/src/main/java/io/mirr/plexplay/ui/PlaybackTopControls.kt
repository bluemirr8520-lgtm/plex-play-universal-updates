package io.mirr.plexplay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Identical title, close/size/settings actions in both playback engines. */
@Composable
internal fun PlaybackTopControls(
    title: String,
    subtitle: String?,
    previousTitle: String?,
    nextTitle: String?,
    videoSize: String,
    onVideoSize: (String) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    settingsModifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxWidth().statusBarsPadding()) {
        PlaybackChromeButton(
            icon = Icons.Rounded.Close, label = "재생 화면 닫기", onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VideoSizeControls(videoSize, onVideoSize)
            PlaybackChromeButton(Icons.Rounded.Settings, "재생 설정", onSettings, settingsModifier)
        }
        Surface(
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(start = 72.dp, end = 232.dp, top = 14.dp, bottom = 14.dp),
            shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = .68f),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = Color.White.copy(alpha = .78f), textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                previousTitle?.takeIf(String::isNotBlank)?.let {
                    Text("이전: $it", color = Color.White.copy(alpha = .72f), textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                nextTitle?.takeIf(String::isNotBlank)?.let {
                    Text("다음: $it", color = Color(0xFFFFD400), textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PlaybackChromeButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.scale(if (focused) 1.12f else 1f).onFocusChanged { focused = it.hasFocus },
        shape = CircleShape,
        color = if (focused) Color.White else Color.Black.copy(alpha = .68f),
        border = if (focused) BorderStroke(3.dp, Color(0xFFE5A00D)) else null,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = if (focused) Color.Black else Color.White)
        }
    }
}
