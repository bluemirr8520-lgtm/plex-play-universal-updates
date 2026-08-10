package io.mirr.plexplay.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.mirr.plexplay.BuildConfig
import io.mirr.plexplay.data.PlaybackSource
import io.mirr.plexplay.data.PlaybackSubtitle
import kotlinx.coroutines.delay
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import java.util.Locale
import kotlin.math.abs

/** Current lifecycle state of the subtitle-only Media3 companion player. */
internal enum class UniversalSubtitleBridgeStatus {
    DISABLED,
    PREPARING,
    READY,
    NO_TEXT_TRACKS,
    NO_SELECTED_TRACK,
    ERROR,
}

/** A selectable text track discovered from the original media or a Plex sidecar. */
internal data class UniversalSubtitleBridgeTrack(
    val key: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    val isExternal: Boolean,
    val selected: Boolean,
    val bitmapBased: Boolean,
)

/**
 * Compose-facing snapshot from the subtitle bridge.
 *
 * [text] intentionally contains plain cue text. The caller renders it with the
 * same app-side subtitle overlay used by the normal Media3 player, so embedded
 * ASS/SSA styling cannot override the user's font, color or position settings.
 */
internal data class UniversalSubtitleBridgeState(
    val text: String = "",
    val tracks: List<UniversalSubtitleBridgeTrack> = emptyList(),
    val selectedTrackKey: String? = null,
    val active: Boolean = false,
    val ready: Boolean = false,
    val bitmapOnly: Boolean = false,
    val status: UniversalSubtitleBridgeStatus = UniversalSubtitleBridgeStatus.DISABLED,
    val errorMessage: String? = null,
) {
    companion object {
        val Disabled = UniversalSubtitleBridgeState()
    }
}

/**
 * Runs a subtitle-only Media3 player beside LibVLC.
 *
 * The companion always opens the original/direct Plex media URL, never the HLS
 * compatibility transcode that deliberately omits subtitle tracks. Audio and
 * video track types are disabled before prepare, while embedded text tracks and
 * Plex external sidecars remain available. Playback position, pause state and
 * speed follow [vlcPlayer] without sharing its decoder or output surface.
 *
 * [selectedPlexSubtitleId] accepts [PlaybackSubtitle.stableId]. Embedded tracks
 * are associated with Plex metadata where possible. [preferredNativeTrackName]
 * is the fallback for a track selected from LibVLC's native track list.
 */
@Composable
@UnstableApi
internal fun rememberUniversalSubtitleBridge(
    source: PlaybackSource,
    enabled: Boolean,
    selectedPlexSubtitleId: String?,
    preferredNativeTrackName: String?,
    automatic: Boolean,
    vlcPlayer: VlcMediaPlayer?,
    playbackPositionMs: Long,
    mainPlayerIsPlaying: Boolean,
    playbackSpeed: Float,
): UniversalSubtitleBridgeState {
    if (!enabled) return UniversalSubtitleBridgeState.Disabled

    val context = LocalContext.current.applicationContext
    val originalUrl = remember(source.url, source.fallbackUrls) {
        source.originalSubtitleMediaUrlForBridge()
    }
    val controllerResult = remember(
        source.ratingKey,
        originalUrl,
        source.token,
        source.subtitles,
        source.compatibilitySubtitles,
        selectedPlexSubtitleId,
        preferredNativeTrackName,
        automatic,
    ) {
        runCatching {
            UniversalSubtitleBridgeController(
                context = context,
                source = source,
                originalUrl = originalUrl,
                selectedPlexSubtitleId = selectedPlexSubtitleId,
                preferredNativeTrackName = preferredNativeTrackName,
                automatic = automatic,
            )
        }
    }
    val controller = controllerResult.getOrNull()
        ?: return UniversalSubtitleBridgeState(
            status = UniversalSubtitleBridgeStatus.ERROR,
            errorMessage = controllerResult.exceptionOrNull()?.message
                ?: "자막 보조 플레이어를 만들 수 없습니다.",
        )

    DisposableEffect(controller) {
        onDispose(controller::release)
    }

    val latestPlaybackPositionMs by rememberUpdatedState(playbackPositionMs)
    val latestMainPlayerIsPlaying by rememberUpdatedState(mainPlayerIsPlaying)
    val latestPlaybackSpeed by rememberUpdatedState(playbackSpeed)

    LaunchedEffect(
        controller,
        selectedPlexSubtitleId,
        preferredNativeTrackName,
        automatic,
    ) {
        controller.updateSelectionRequest(
            selectedPlexSubtitleId = selectedPlexSubtitleId,
            preferredNativeTrackName = preferredNativeTrackName,
            automatic = automatic,
        )
    }

    LaunchedEffect(controller, vlcPlayer) {
        while (true) {
            val nativePositionMs = runCatching { vlcPlayer?.time }
                .getOrNull()
                ?.takeIf { it > 0L }
            val reliablePositionMs = latestPlaybackPositionMs
                .takeIf { it >= 0L }
                ?: nativePositionMs
            if (reliablePositionMs != null) {
                controller.synchronize(
                    positionMs = reliablePositionMs,
                    playing = latestMainPlayerIsPlaying,
                    speed = latestPlaybackSpeed,
                )
            } else {
                controller.pauseUntilMainPlayerIsReady(latestPlaybackSpeed)
            }
            delay(SubtitleBridgeSyncIntervalMs)
        }
    }

    LaunchedEffect(controller) {
        delay(SubtitleBridgePrepareTimeoutMs)
        controller.markPreparingTimedOut()
    }

    return controller.state
}

@Stable
@UnstableApi
private class UniversalSubtitleBridgeController(
    context: Context,
    private val source: PlaybackSource,
    originalUrl: String,
    selectedPlexSubtitleId: String?,
    preferredNativeTrackName: String?,
    automatic: Boolean,
) {
    var state by mutableStateOf(
        UniversalSubtitleBridgeState(
            status = UniversalSubtitleBridgeStatus.PREPARING,
        ),
    )
        private set

    private var released = false
    private var terminalFallback = false
    private var requestedPlexSubtitleId = selectedPlexSubtitleId
    private var requestedNativeTrackName = preferredNativeTrackName
    private var requestedAutomatic = automatic
    private var bindings = emptyList<SubtitleTrackBinding>()
    private var requestedTargetKey: String? = null
    private var cueHasBitmap = false
    private var cueHasText = false

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(60_000)
        .setUserAgent("PlexPlay/${BuildConfig.VERSION_NAME} Android")
        .setDefaultRequestProperties(
            buildMap {
                if (source.token.isNotBlank()) {
                    put("X-Plex-Token", source.token)
                }
                put("Cache-Control", "no-cache")
                put("Pragma", "no-cache")
                put("Accept-Encoding", "identity")
            },
        )

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(httpFactory),
        )
        .build()

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            rebuildTrackBindings(tracks)
            applyRequestedSelection()
            publishPlaybackState()
        }

        override fun onCues(cueGroup: CueGroup) {
            cueHasBitmap = cueHasBitmap || cueGroup.cues.any { it.bitmap != null }
            val cueTexts = cueGroup.cues
                .mapNotNull { cue -> cue.text?.toString()?.takeIf(String::isNotBlank) }
            cueHasText = cueHasText || cueTexts.isNotEmpty()
            state = state.copy(text = cueTexts.joinToString("\n\n"))
            publishPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            terminalFallback = true
            state = state.copy(
                text = "",
                active = false,
                ready = false,
                status = UniversalSubtitleBridgeStatus.ERROR,
                errorMessage = error.message
                    ?: "Media3 자막 보조 플레이어 오류 (${error.errorCode})",
            )
            runCatching { player.stop() }
        }
    }

    init {
        try {
            player.addListener(listener)
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguages("ko", "kor")
                .setSelectUndeterminedTextLanguage(true)
                .build()
            val requestedSubtitle = selectedPlexSubtitleId?.let { stableId ->
                (source.subtitles + source.compatibilitySubtitles)
                    .firstOrNull { it.stableId == stableId }
            }
            if (requestedSubtitle != null && !requestedSubtitle.isEmbedded) {
                // Use the same MediaItem subtitle configuration path as the
                // normal player. Direct SingleSampleMediaSource injection uses
                // Media3's legacy subtitle decoder path and can produce no cues
                // with the current TextRenderer. A/V renderers stay disabled, so
                // VLC remains the only video/audio decoder.
                val subtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(
                    Uri.parse(
                        requestedSubtitle.url.withPlexSubtitleTokenQuery(source.token),
                    ),
                )
                    .setId(ExternalSidecarIdPrefix + requestedSubtitle.stableId)
                    .setMimeType(requestedSubtitle.mimeType)
                    .setLanguage(requestedSubtitle.language)
                    .setLabel(requestedSubtitle.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(originalUrl)
                        .setSubtitleConfigurations(listOf(subtitleConfiguration))
                        .build(),
                )
            } else {
                // Only embedded/native selection needs a second demux of the
                // original container. The caller starts this bridge after Plex's
                // lightweight extraction path has already failed.
                player.setMediaItem(MediaItem.fromUri(originalUrl))
            }
            if (source.resumePositionMs > 0L) {
                player.seekTo(source.resumePositionMs)
            }
            player.playWhenReady = false
            player.prepare()
        } catch (error: Throwable) {
            player.removeListener(listener)
            runCatching { player.release() }
            throw error
        }
    }

    fun updateSelectionRequest(
        selectedPlexSubtitleId: String?,
        preferredNativeTrackName: String?,
        automatic: Boolean,
    ) {
        if (released) return
        val changed = requestedPlexSubtitleId != selectedPlexSubtitleId ||
            requestedNativeTrackName != preferredNativeTrackName ||
            requestedAutomatic != automatic
        requestedPlexSubtitleId = selectedPlexSubtitleId
        requestedNativeTrackName = preferredNativeTrackName
        requestedAutomatic = automatic
        if (changed) {
            state = state.copy(text = "")
            cueHasBitmap = false
            cueHasText = false
            applyRequestedSelection()
        }
    }

    fun synchronize(positionMs: Long, playing: Boolean, speed: Float) {
        if (released || terminalFallback) return
        val safeSpeed = speed.coerceIn(.25f, 4f)
        if (abs(player.playbackParameters.speed - safeSpeed) > .001f) {
            player.setPlaybackSpeed(safeSpeed)
        }
        val driftLimit = if (playing) SubtitleBridgePlayingDriftMs else SubtitleBridgePausedDriftMs
        if (
            (
                player.playbackState == Player.STATE_READY ||
                    player.playbackState == Player.STATE_ENDED
                ) &&
            abs(player.currentPosition - positionMs) > driftLimit
        ) {
            player.seekTo(positionMs.coerceAtLeast(0L))
        }
        if (player.playWhenReady != playing) {
            player.playWhenReady = playing
        }
    }

    fun pauseUntilMainPlayerIsReady(speed: Float) {
        if (released || terminalFallback) return
        val safeSpeed = speed.coerceIn(.25f, 4f)
        if (abs(player.playbackParameters.speed - safeSpeed) > .001f) {
            player.setPlaybackSpeed(safeSpeed)
        }
        player.playWhenReady = false
    }

    fun markPreparingTimedOut() {
        if (
            released ||
            terminalFallback ||
            state.status != UniversalSubtitleBridgeStatus.PREPARING
        ) {
            return
        }
        terminalFallback = true
        state = state.copy(
            text = "",
            active = false,
            ready = false,
            status = UniversalSubtitleBridgeStatus.ERROR,
            errorMessage = "자막 트랙 준비 시간이 초과되었습니다.",
        )
        player.playWhenReady = false
        runCatching { player.stop() }
    }

    fun release() {
        if (released) return
        released = true
        player.removeListener(listener)
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    private fun rebuildTrackBindings(tracks: Tracks) {
        val remainingEmbeddedMetadata = source.compatibilitySubtitles.toMutableList()
        val usedKeys = mutableSetOf<String>()
        val rebuilt = buildList {
            tracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    val formatIsBitmap = format.isBitmapSubtitleFormat()
                    // MergingMediaSource can prefix child Format IDs (for
                    // example "1:plex-external:<stableId>"). Find our marker
                    // anywhere in the ID so the selected external track is not
                    // mistaken for an embedded track.
                    val sidecarId = media3ExternalSidecarStableId(format.id)
                    val metadata = if (sidecarId == null && !formatIsBitmap) {
                        chooseEmbeddedMetadata(
                            language = format.language,
                            label = format.label,
                            mimeType = format.sampleMimeType,
                            remaining = remainingEmbeddedMetadata,
                        )
                    } else {
                        source.subtitles.firstOrNull { it.stableId == sidecarId }
                    }
                    if (sidecarId == null && metadata != null) {
                        remainingEmbeddedMetadata.remove(metadata)
                    }
                    val proposedKey = sidecarId
                        ?: metadata?.stableId
                        ?: buildString {
                            append("media3:")
                            append(groupIndex)
                            append(':')
                            append(trackIndex)
                            format.id?.let { append(':').append(it) }
                        }
                    val key = if (usedKeys.add(proposedKey)) {
                        proposedKey
                    } else {
                        "$proposedKey#$groupIndex:$trackIndex"
                    }
                    val label = format.label
                        ?.takeIf { it.isNotBlank() }
                        ?: metadata?.label
                        ?: format.language?.takeIf { it.isNotBlank() }
                        ?: "자막 ${size + 1}"
                    val mimeType = format.sampleMimeType ?: metadata?.mimeType
                    add(
                        SubtitleTrackBinding(
                            publicTrack = UniversalSubtitleBridgeTrack(
                                key = key,
                                label = label,
                                language = format.language ?: metadata?.language,
                                mimeType = mimeType,
                                isExternal = sidecarId != null,
                                selected = group.isTrackSelected(trackIndex),
                                bitmapBased = formatIsBitmap ||
                                    metadata?.mimeType.isBitmapSubtitleMimeType(),
                            ),
                            group = group.mediaTrackGroup,
                            trackIndex = trackIndex,
                        ),
                    )
                }
            }
        }
        bindings = rebuilt
        state = state.copy(
            tracks = rebuilt.map(SubtitleTrackBinding::publicTrack),
            selectedTrackKey = rebuilt.firstOrNull { it.publicTrack.selected }
                ?.publicTrack
                ?.key,
        )
    }

    private fun applyRequestedSelection() {
        if (released || bindings.isEmpty()) return

        val parameters = player.trackSelectionParameters
        val requestedMetadata = requestedPlexSubtitleId?.let { stableId ->
            (source.subtitles + source.compatibilitySubtitles)
                .firstOrNull { it.stableId == stableId }
        }
        val target = requestedPlexSubtitleId?.let { stableId ->
            bindings.firstOrNull { it.publicTrack.key == stableId }
        } ?: requestedMetadata?.let { metadata ->
            bindings.bestMatch(
                language = metadata.language,
                label = metadata.label,
                mimeType = metadata.mimeType,
            )
        } ?: requestedNativeTrackName
            ?.takeIf { it.isNotBlank() }
            ?.let { nativeName -> bindings.bestNameMatch(nativeName) }
        ?: if (requestedAutomatic) {
            bindings.firstOrNull { track ->
                isKoreanSubtitleTrack(
                    language = track.publicTrack.language,
                    label = track.publicTrack.label,
                )
            } ?: bindings.firstOrNull { it.publicTrack.selected }
                ?: bindings.firstOrNull()
        } else {
            null
        }

        if (target == null) {
            requestedTargetKey = null
            if (C.TRACK_TYPE_TEXT !in parameters.disabledTrackTypes) {
                player.trackSelectionParameters = parameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            return
        }
        requestedTargetKey = target.publicTrack.key
        if (target.publicTrack.selected) return

        state = state.copy(
            text = "",
            active = false,
            ready = false,
            status = UniversalSubtitleBridgeStatus.PREPARING,
            errorMessage = null,
        )

        player.trackSelectionParameters = parameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                TrackSelectionOverride(target.group, target.trackIndex),
            )
            .build()
    }

    private fun publishPlaybackState() {
        if (released || terminalFallback) return
        val selectedTrack = state.tracks.firstOrNull { it.selected }
        val ready = player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_ENDED
        val selectionMatched = selectedTrack != null &&
            requestedTargetKey != null &&
            selectedTrack.key == requestedTargetKey
        val bitmapOnly = selectedTrack?.bitmapBased == true ||
            (cueHasBitmap && !cueHasText)
        val status = when {
            ready && state.tracks.isEmpty() -> UniversalSubtitleBridgeStatus.NO_TEXT_TRACKS
            ready && requestedTargetKey == null -> UniversalSubtitleBridgeStatus.NO_SELECTED_TRACK
            ready && !selectionMatched -> UniversalSubtitleBridgeStatus.PREPARING
            ready -> UniversalSubtitleBridgeStatus.READY
            else -> UniversalSubtitleBridgeStatus.PREPARING
        }
        state = state.copy(
            selectedTrackKey = selectedTrack?.key?.takeIf { selectionMatched },
            active = ready && selectionMatched && !bitmapOnly,
            ready = ready,
            bitmapOnly = bitmapOnly,
            status = status,
            errorMessage = if (status == UniversalSubtitleBridgeStatus.NO_SELECTED_TRACK) {
                "선택한 자막 트랙을 원본 파일에서 찾지 못했습니다."
            } else {
                null
            },
        )
        if (
            bitmapOnly ||
            status == UniversalSubtitleBridgeStatus.NO_TEXT_TRACKS ||
            status == UniversalSubtitleBridgeStatus.NO_SELECTED_TRACK
        ) {
            terminalFallback = true
            player.playWhenReady = false
            runCatching { player.stop() }
        }
    }
}

private data class SubtitleTrackBinding(
    val publicTrack: UniversalSubtitleBridgeTrack,
    val group: TrackGroup,
    val trackIndex: Int,
)

internal fun PlaybackSource.originalSubtitleMediaUrlForBridge(): String {
    val candidates = (listOf(url) + fallbackUrls).distinct()
    return candidates.firstOrNull { !it.isPlexCompatibilityTranscodeUrl() }
        ?: candidates.first()
}

internal fun media3ExternalSidecarStableId(formatId: String?): String? {
    val value = formatId ?: return null
    val markerIndex = value.indexOf(ExternalSidecarIdPrefix)
    if (markerIndex < 0) return null
    return value.substring(markerIndex + ExternalSidecarIdPrefix.length)
        .takeIf(String::isNotBlank)
}

private fun String.isPlexCompatibilityTranscodeUrl(): Boolean =
    contains("/video/:/transcode/", ignoreCase = true)

internal fun chooseEmbeddedMetadata(
    language: String?,
    label: String?,
    mimeType: String?,
    remaining: List<PlaybackSubtitle>,
): PlaybackSubtitle? {
    if (mimeType.isBitmapSubtitleMimeType()) return null
    val best = remaining.maxByOrNull { subtitle ->
        subtitleMatchScore(
            trackLanguage = language,
            trackLabel = label,
            trackMimeType = mimeType,
            subtitle = subtitle,
        )
    } ?: return null
    val score = subtitleMatchScore(language, label, mimeType, best)
    return when {
        score > 0 -> best
        remaining.size == 1 && !remaining.single().mimeType.isBitmapSubtitleMimeType() -> best
        else -> null
    }
}

private fun List<SubtitleTrackBinding>.bestMatch(
    language: String?,
    label: String?,
    mimeType: String?,
): SubtitleTrackBinding? {
    fun score(binding: SubtitleTrackBinding): Int {
        val track = binding.publicTrack
        var value = 0
        if (sameLanguage(track.language, language)) value += 8
        val normalizedTrackLabel = normalizedTrackName(track.label)
        val normalizedRequestedLabel = normalizedTrackName(label)
        if (
            normalizedTrackLabel.isNotBlank() &&
            normalizedTrackLabel == normalizedRequestedLabel
        ) {
            value += 6
        }
        if (
            !mimeType.isNullOrBlank() &&
            track.mimeType.equals(mimeType, ignoreCase = true)
        ) {
            value += 4
        }
        return value
    }
    val best = maxByOrNull(::score) ?: return null
    return best.takeIf { score(it) > 0 }
}

private fun List<SubtitleTrackBinding>.bestNameMatch(
    name: String,
): SubtitleTrackBinding? {
    val normalizedName = normalizedTrackName(name)
    return firstOrNull {
        normalizedTrackName(it.publicTrack.label) == normalizedName
    } ?: firstOrNull {
        val candidate = normalizedTrackName(it.publicTrack.label)
        candidate.isNotBlank() &&
            (candidate.contains(normalizedName) || normalizedName.contains(candidate))
    } ?: if (isKoreanSubtitleTrack(null, name)) {
        firstOrNull {
            isKoreanSubtitleTrack(it.publicTrack.language, it.publicTrack.label)
        }
    } else {
        null
    }
}

private fun subtitleMatchScore(
    trackLanguage: String?,
    trackLabel: String?,
    trackMimeType: String?,
    subtitle: PlaybackSubtitle,
): Int {
    var score = 0
    if (sameLanguage(trackLanguage, subtitle.language)) score += 8
    val normalizedTrackLabel = normalizedTrackName(trackLabel)
    val normalizedSubtitleLabel = normalizedTrackName(subtitle.label)
    if (
        normalizedTrackLabel.isNotBlank() &&
        normalizedTrackLabel == normalizedSubtitleLabel
    ) {
        score += 6
    }
    if (
        !trackMimeType.isNullOrBlank() &&
        trackMimeType.equals(subtitle.mimeType, ignoreCase = true)
    ) {
        score += 4
    }
    return score
}

private fun sameLanguage(first: String?, second: String?): Boolean {
    val firstTag = first?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')
    val secondTag = second?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')
    if (firstTag.isNullOrBlank() || secondTag.isNullOrBlank()) return false
    val normalizedFirst = if (firstTag == "kor") "ko" else firstTag
    val normalizedSecond = if (secondTag == "kor") "ko" else secondTag
    return normalizedFirst == normalizedSecond
}

private fun normalizedTrackName(value: String?): String = value
    .orEmpty()
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun isKoreanSubtitleTrack(language: String?, label: String?): Boolean {
    val languageTag = language.orEmpty().lowercase(Locale.ROOT)
    val displayLabel = label.orEmpty().lowercase(Locale.ROOT)
    return languageTag == "ko" ||
        languageTag == "kor" ||
        languageTag.startsWith("ko-") ||
        displayLabel.contains("한국") ||
        displayLabel.contains("korean") ||
        displayLabel.contains("kor")
}

private fun String?.isBitmapSubtitleMimeType(): Boolean {
    val normalized = orEmpty().lowercase(Locale.ROOT)
    return normalized.contains("pgs") ||
        normalized.contains("vobsub") ||
        normalized.contains("dvbsub") ||
        normalized.contains("dvd_subtitle")
}

private fun androidx.media3.common.Format.isBitmapSubtitleFormat(): Boolean =
    sampleMimeType.isBitmapSubtitleMimeType() ||
        containerMimeType.isBitmapSubtitleMimeType() ||
        codecs.isBitmapSubtitleMimeType() ||
        label.isBitmapSubtitleMimeType()

private const val ExternalSidecarIdPrefix = "plex-sidecar:"
private const val SubtitleBridgeSyncIntervalMs = 250L
private const val SubtitleBridgePlayingDriftMs = 600L
private const val SubtitleBridgePausedDriftMs = 150L
private const val SubtitleBridgePrepareTimeoutMs = 15_000L
