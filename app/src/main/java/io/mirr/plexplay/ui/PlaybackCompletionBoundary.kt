package io.mirr.plexplay.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException

/** Remove the finished player before navigating or creating the next decoder. */
@Composable
internal fun PlaybackCompletionBoundary(
    onCompleted: (Long) -> Unit,
    onClose: () -> Unit,
    content: @Composable ((Long) -> Unit) -> Unit,
) {
    var completedPosition by remember { mutableStateOf<Long?>(null) }
    val latestCompleted by rememberUpdatedState(onCompleted)
    val latestClose by rememberUpdatedState(onClose)
    BackHandler(enabled = completedPosition != null) { latestClose() }
    val position = completedPosition
    if (position == null) {
        content { endedAt ->
            if (completedPosition == null) completedPosition = endedAt.coerceAtLeast(0L)
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("재생을 마쳤습니다", color = Color.White)
        }
    }
    LaunchedEffect(position) {
        if (position != null) {
            // onDispose / AndroidView removal must complete before library focus or next playback.
            withFrameNanos { }
            try {
                latestCompleted(position)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("PlexPlayUniversal", "Playback completion handoff failed", error)
                latestClose()
            }
        }
    }
}
