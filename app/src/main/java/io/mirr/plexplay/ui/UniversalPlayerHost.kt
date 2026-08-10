package io.mirr.plexplay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.mirr.plexplay.data.PlaybackQuality
import io.mirr.plexplay.data.PlaybackSource

private enum class UniversalPlaybackEngine {
    MEDIA3,
    VLC,
}

/**
 * Keeps the original Plex Play experience and changes engines only when the
 * Android platform decoder cannot open a stream. VLC carries its own codec
 * stack, so unsupported containers/codecs can still be played locally.
 */
@Composable
fun UniversalPlayerHost(
    source: PlaybackSource,
    playbackQuality: PlaybackQuality,
    hasPreviousPlayback: Boolean,
    previousPlaybackTitle: String?,
    hasNextPlayback: Boolean,
    nextPlaybackTitle: String?,
    autoPlayNext: Boolean,
    onPlaybackQualityChanged: (PlaybackQuality, Long) -> Unit,
    onAutoPlayNextChanged: (Boolean) -> Unit,
    onPlayPrevious: (Long) -> Unit,
    onPlayNext: (Long) -> Unit,
    onClose: () -> Unit,
    onPlaybackCompleted: (Long) -> Unit,
    onProgress: (PlaybackSource, Long, String) -> Unit,
) {
    val context = LocalContext.current
    val preferUniversalCodec = remember(context, source, playbackQuality) {
        shouldPreferUniversalCodec(
            playbackQuality = playbackQuality,
            directPlaybackSupported = detectDevicePlaybackCompatibility(
                context = context,
                source = source,
            ).directPlaybackSupported,
        )
    }
    var engine by remember(source.url, playbackQuality) {
        mutableStateOf(
            if (preferUniversalCodec) {
                UniversalPlaybackEngine.VLC
            } else {
                UniversalPlaybackEngine.MEDIA3
            },
        )
    }
    var fallbackPositionMs by remember(source.ratingKey) {
        mutableLongStateOf(source.resumePositionMs)
    }

    LaunchedEffect(source.url, playbackQuality, preferUniversalCodec) {
        engine =
            if (preferUniversalCodec) {
                UniversalPlaybackEngine.VLC
            } else {
                UniversalPlaybackEngine.MEDIA3
            }
        fallbackPositionMs = source.resumePositionMs
    }

    when (engine) {
        UniversalPlaybackEngine.MEDIA3 -> PlayerScreen(
            source = source,
            playbackQuality = playbackQuality,
            hasPreviousPlayback = hasPreviousPlayback,
            previousPlaybackTitle = previousPlaybackTitle,
            hasNextPlayback = hasNextPlayback,
            nextPlaybackTitle = nextPlaybackTitle,
            autoPlayNext = autoPlayNext,
            onPlaybackQualityChanged = onPlaybackQualityChanged,
            onAutoPlayNextChanged = onAutoPlayNextChanged,
            onPlayPrevious = onPlayPrevious,
            onPlayNext = onPlayNext,
            onClose = onClose,
            onPlaybackCompleted = onPlaybackCompleted,
            onProgress = onProgress,
            onPlaybackUnavailable = { positionMs ->
                fallbackPositionMs = positionMs
                engine = UniversalPlaybackEngine.VLC
            },
        )

        UniversalPlaybackEngine.VLC -> VlcPlayerScreen(
            source = source.copy(resumePositionMs = fallbackPositionMs),
            playbackQuality = playbackQuality,
            hasPreviousPlayback = hasPreviousPlayback,
            previousPlaybackTitle = previousPlaybackTitle,
            hasNextPlayback = hasNextPlayback,
            nextPlaybackTitle = nextPlaybackTitle,
            autoPlayNext = autoPlayNext,
            onPlaybackQualityChanged = onPlaybackQualityChanged,
            onAutoPlayNextChanged = onAutoPlayNextChanged,
            onPlayPrevious = onPlayPrevious,
            onPlayNext = onPlayNext,
            onClose = onClose,
            onPlaybackCompleted = onPlaybackCompleted,
            onProgress = onProgress,
        )
    }
}
