package io.mirr.plexplay.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media3.ui.CaptionStyleCompat
import io.mirr.plexplay.R
import io.mirr.plexplay.data.PlaybackQuality
import io.mirr.plexplay.data.PlaybackSource
import io.mirr.plexplay.data.PlaybackSubtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private data class VlcTrack(
    val id: Int,
    val name: String,
)

private enum class VlcSettingsPage(
    val title: String,
) {
    MAIN("재생 설정"),
    SUBTITLE("자막 설정"),
    SUBTITLE_STYLE("자막 사용자 설정"),
    SPEED("재생 속도"),
    DISPLAY("화면 설정"),
    DISPLAY_ADVANCED("고급 화면 설정"),
    AUDIO("오디오"),
    QUALITY("재생 품질 변환"),
    OPTIMIZATION("기기 성능 최적화"),
    STORAGE("저장공간"),
}

private enum class VlcGestureMode {
    VOLUME,
    BRIGHTNESS,
    SEEK,
    SCALE,
    SUBTITLE_POSITION,
}

private enum class VlcPictureMode(
    val storage: String,
    val label: String,
    val description: String,
    val brightness: Float,
    val contrast: Float,
    val blackLevel: Float,
    val colorDepth: Float,
    val colorTemperature: Float,
) {
    STANDARD("standard", "표준 화면", "원본에 가까운 균형 잡힌 화면", 0f, 0f, 0f, 0f, 0f),
    VIVID("vivid", "선명한 화면", "밝고 색감이 풍부한 화면", 10f, 12f, 0f, 12f, 3f),
    CINEMA("cinema", "영화 화면", "어두운 공간에서 편안한 색감", 3f, -4f, 0f, -3f, -8f),
    BRIGHT_ROOM("bright_room", "밝은 공간", "낮이나 조명이 밝은 공간용", 14f, 6f, 2f, 5f, 0f),
    EYE_COMFORT("eye_comfort", "눈이 편안한 화면", "밝기와 푸른빛을 낮춘 화면", -8f, -6f, 2f, -8f, -18f),
    CUSTOM("custom", "사용자 설정", "고급 화면 설정에서 직접 조정", 0f, 0f, 0f, 0f, 0f),
    ;

    companion object {
        fun fromStorage(value: String?): VlcPictureMode =
            entries.firstOrNull { it.storage == value } ?: STANDARD
    }
}

private data class VlcVideoSettings(
    val screenBrightness: Float = 100f,
    val pictureMode: VlcPictureMode = VlcPictureMode.STANDARD,
    val pictureBrightness: Float = 0f,
    val pictureContrast: Float = 0f,
    val pictureBlackLevel: Float = 0f,
    val pictureColorDepth: Float = 0f,
    val pictureColorTemperature: Float = 0f,
) {
    fun applyPictureMode(mode: VlcPictureMode): VlcVideoSettings = copy(
        pictureMode = mode,
        pictureBrightness = mode.brightness,
        pictureContrast = mode.contrast,
        pictureBlackLevel = mode.blackLevel,
        pictureColorDepth = mode.colorDepth,
        pictureColorTemperature = mode.colorTemperature,
    )
}

private enum class VlcOptimizationMode(
    val storage: String,
    val label: String,
    val description: String,
) {
    AUTO("auto", "자동 최적화 · 권장", "기기 성능과 재생 형식에 맞춰 자동 조절합니다."),
    STABILITY("stability", "재생 안정성 우선", "버퍼를 늘리고 늦은 프레임을 정리합니다."),
    BALANCED("balanced", "균형", "화질과 재생 안정성을 균형 있게 유지합니다."),
    PERFORMANCE("performance", "고성능·고해상도 기기", "짧은 버퍼와 하드웨어 디코딩을 우선합니다."),
    ;

    companion object {
        fun fromStorage(value: String?): VlcOptimizationMode =
            entries.firstOrNull { it.storage == value } ?: AUTO
    }
}

private enum class VlcVideoScale(
    val storage: String,
    val label: String,
    val scaleX: Float,
    val scaleY: Float,
) {
    FIT("fit", "화면 맞춤 · 100%", 1f, 1f),
    ZOOM("zoom", "화면 확대 · 115%", 1.15f, 1.15f),
    ZOOM_LARGE("zoom_large", "화면 확대 · 130%", 1.3f, 1.3f),
    STRETCH("stretch", "가로 늘이기 · 112%", 1.12f, 1f),
    ;

    companion object {
        fun fromStorage(value: String?): VlcVideoScale =
            entries.firstOrNull { it.storage == value } ?: FIT
    }
}

private enum class VlcSubtitleFont(
    val storage: String,
    val label: String,
) {
    SYSTEM("system", "시스템 기본"),
    ASIA_B("asia_cinema_b", "a시네마B"),
    ASIA_M("asia_cinema_m", "a시네마M"),
    ASIA_L("asia_cinema_l", "a시네마L"),
    CUSTOM("custom", "사용자 폰트"),
    GOWUN("gowun", "고운바탕"),
    GOTHIC("gothic", "고딕체"),
    SERIF("serif", "명조체"),
    ROUNDED("rounded", "둥근 고딕"),
    ;

    companion object {
        fun fromStorage(value: String?): VlcSubtitleFont =
            when (value) {
                "cinema" -> ASIA_B
                else -> entries.firstOrNull { it.storage == value } ?: SYSTEM
            }
    }
}

private enum class VlcSubtitleEdge(
    val storedValue: Int,
    val label: String,
) {
    NONE(0, "없음"),
    OUTLINE(1, "외곽선"),
    SHADOW(2, "그림자"),
    ;

    companion object {
        fun fromStoredValue(value: Int): VlcSubtitleEdge =
            entries.firstOrNull { it.storedValue == value } ?: OUTLINE
    }
}

private data class VlcSubtitleStyle(
    val font: VlcSubtitleFont = VlcSubtitleFont.SYSTEM,
    val sizePercent: Int = 100,
    val color: Int = AndroidColor.WHITE,
    val background: Int = AndroidColor.TRANSPARENT,
    val edgeColor: Int = AndroidColor.BLACK,
    val edge: VlcSubtitleEdge = VlcSubtitleEdge.OUTLINE,
    val horizontalOffsetPercent: Int = 0,
    val verticalOffsetPercent: Int = 0,
    val verticalWriting: Boolean = false,
)

private val VlcSubtitleSizes = listOf(50, 75, 100, 125, 150, 200)
private val VlcSubtitleColors = listOf(
    "흰색" to AndroidColor.WHITE,
    "노란색" to AndroidColor.YELLOW,
    "하늘색" to AndroidColor.CYAN,
    "연두색" to AndroidColor.GREEN,
)
private val VlcSubtitleBackgrounds = listOf(
    "투명" to AndroidColor.TRANSPARENT,
    "반투명" to 0x99000000.toInt(),
    "검정" to AndroidColor.BLACK,
)
private val VlcSubtitleEdgeColors = listOf(
    "검정" to AndroidColor.BLACK,
    "흰색" to AndroidColor.WHITE,
    "파랑" to AndroidColor.BLUE,
)

@Composable
fun VlcPlayerScreen(
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val preferences = remember {
        context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
    }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var subtitleStyle by remember {
        val loaded = preferences.loadVlcSubtitleStyle()
        val storedCustomFont = File(context.filesDir, "custom_subtitle_font")
        mutableStateOf(
            if (
                loaded.font == VlcSubtitleFont.CUSTOM &&
                (!storedCustomFont.isFile || storedCustomFont.length() == 0L)
            ) {
                loaded.copy(font = VlcSubtitleFont.GOWUN)
            } else {
                loaded
            },
        )
    }
    var draftSubtitleStyle by remember { mutableStateOf(subtitleStyle) }
    var rendererRevision by remember { mutableIntStateOf(0) }
    var nativeSubtitleStyleApplyRevision by remember { mutableIntStateOf(0) }
    var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(VlcSettingsPage.MAIN) }
    var playbackSpeed by remember {
        mutableFloatStateOf(preferences.getFloat("playback_speed", 1f))
    }
    var videoScale by remember {
        mutableStateOf(VlcVideoScale.fromStorage(preferences.getString("video_scale_mode", null)))
    }
    var videoSettings by remember {
        mutableStateOf(preferences.loadVlcVideoSettings())
    }
    var optimizationMode by remember {
        mutableStateOf(
            VlcOptimizationMode.fromStorage(
                preferences.getString("device_optimization_mode", null),
            ),
        )
    }
    var selectedVideoPreset by remember { mutableIntStateOf(0) }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }
    var gestureFeedbackRevision by remember { mutableIntStateOf(0) }
    var controlsInteractionRevision by remember { mutableIntStateOf(0) }
    var leftLongPressSeeking by remember { mutableStateOf(false) }
    var rightLongPressSeeking by remember { mutableStateOf(false) }
    var confirmLongPressConsumed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs by remember(source.ratingKey) {
        mutableLongStateOf(source.resumePositionMs)
    }
    var durationMs by remember(source.ratingKey) {
        mutableLongStateOf(source.durationMs.coerceAtLeast(1L))
    }
    var seekPreview by remember(source.ratingKey) {
        mutableFloatStateOf(source.resumePositionMs.toFloat())
    }
    var draggingProgress by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf(emptyList<VlcTrack>()) }
    var subtitleTracks by remember { mutableStateOf(emptyList<VlcTrack>()) }
    val plexSubtitleChoices = remember(source.subtitles, source.compatibilitySubtitles) {
        (source.subtitles + source.compatibilitySubtitles).distinctBy { it.stableId }
    }
    var selectedAudioTrack by remember { mutableIntStateOf(-1) }
    var defaultAudioTrack by remember(source.ratingKey) { mutableIntStateOf(-1) }
    var automaticAudioSelection by remember(source.ratingKey) { mutableStateOf(true) }
    var preferredAudioTrack by remember(source.ratingKey) { mutableStateOf<Int?>(null) }
    var selectedSubtitleTrack by remember { mutableIntStateOf(-1) }
    var subtitlesDisabled by remember(source.ratingKey) { mutableStateOf(false) }
    var preferredSubtitleTrack by remember(source.ratingKey) { mutableStateOf<Int?>(null) }
    var selectedPlexSubtitleId by remember(source.ratingKey) {
        mutableStateOf(
            plexSubtitleChoices.firstOrNull { it.isPreferredKoreanTextSubtitle() }?.stableId
                ?: plexSubtitleChoices.firstOrNull { it.selected }?.stableId,
        )
    }
    val selectedPlexSubtitle = plexSubtitleChoices.firstOrNull {
        it.stableId == selectedPlexSubtitleId
    }
    val selectedExternalSubtitleUrl = selectedPlexSubtitle?.url
    var restorePositionMs by remember(source.ratingKey) {
        mutableLongStateOf(source.resumePositionMs)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val controlsFocusRequester = remember { FocusRequester() }
    val previousButtonFocusRequester = remember { FocusRequester() }
    val rewindButtonFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }
    val forwardButtonFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }
    val latestAutoPlayNext by rememberUpdatedState(autoPlayNext)
    val latestHasNextPlayback by rememberUpdatedState(hasNextPlayback)
    val latestOnPlaybackCompleted by rememberUpdatedState(onPlaybackCompleted)
    val latestOnProgress by rememberUpdatedState(onProgress)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val customFontFile = remember { File(context.filesDir, "custom_subtitle_font") }
    var customFontRevision by remember { mutableIntStateOf(0) }
    var styledSubtitleCues by remember(source.ratingKey) {
        mutableStateOf<List<ManualSubtitleCue>?>(null)
    }
    var styledSubtitleSourceUrl by remember(source.ratingKey) { mutableStateOf<String?>(null) }
    var styledSubtitleText by remember(source.ratingKey) { mutableStateOf("") }
    var styledSubtitleLoading by remember(source.ratingKey) { mutableStateOf(false) }
    var styledSubtitleLoadFailed by remember(source.ratingKey) { mutableStateOf(false) }
    val selectedAppTextSubtitle = selectedPlexSubtitle?.takeIf { it.isManualTextSubtitle() }
    val useStyledSubtitleOverlay =
        selectedAppTextSubtitle != null &&
            styledSubtitleSourceUrl == selectedAppTextSubtitle.url &&
            !styledSubtitleCues.isNullOrEmpty()
    val automaticSubtitleSelection = !subtitlesDisabled &&
        preferredSubtitleTrack == null &&
        selectedExternalSubtitleUrl == null
    val preferredNativeTrackName = preferredSubtitleTrack?.let { preferredId ->
        subtitleTracks.firstOrNull { it.id == preferredId }?.name
    }
    // Keep Media3 as a subtitle-only companion while the direct text loader is
    // pending/failed or when a LibVLC-discovered track was selected. Video and
    // audio renderers are disabled inside the bridge, so VLC remains the sole
    // A/V decoder.
    val subtitleBridgeEnabled = !subtitlesDisabled &&
        !useStyledSubtitleOverlay &&
        (
            (selectedAppTextSubtitle != null && styledSubtitleLoadFailed) ||
                selectedPlexSubtitle == null
            )
    val subtitleBridge = rememberUniversalSubtitleBridge(
        source = source,
        enabled = subtitleBridgeEnabled,
        selectedPlexSubtitleId = selectedPlexSubtitleId,
        preferredNativeTrackName = preferredNativeTrackName,
        automatic = automaticSubtitleSelection,
        vlcPlayer = mediaPlayer,
        playbackPositionMs = positionMs,
        mainPlayerIsPlaying = isPlaying,
        playbackSpeed = playbackSpeed,
    )
    val useCompanionSubtitleOverlay = subtitleBridgeEnabled && subtitleBridge.active
    val useAppSubtitleOverlay = useStyledSubtitleOverlay || useCompanionSubtitleOverlay
    val appSubtitleOwnsSelection = useStyledSubtitleOverlay ||
        (
            subtitleBridgeEnabled &&
                (
                    subtitleBridge.active ||
                        subtitleBridge.status == UniversalSubtitleBridgeStatus.PREPARING
                    ) &&
                !subtitleBridge.bitmapOnly
            )
    val appSubtitleText = if (useStyledSubtitleOverlay) {
        styledSubtitleText
    } else {
        subtitleBridge.text
    }
    val subtitleRendererStatus = when {
        subtitlesDisabled -> null
        useStyledSubtitleOverlay -> "사용자 자막 적용됨 · 자막 파일 직접 읽기"
        useCompanionSubtitleOverlay -> "사용자 자막 적용됨 · 원본 텍스트 트랙"
        subtitleBridgeEnabled &&
            subtitleBridge.status == UniversalSubtitleBridgeStatus.PREPARING ->
            "사용자 자막 준비 중…"
        subtitleBridge.bitmapOnly -> "이미지 자막 · 글꼴/색상 변경 불가"
        subtitleBridgeEnabled &&
            subtitleBridge.status in setOf(
                UniversalSubtitleBridgeStatus.NO_TEXT_TRACKS,
                UniversalSubtitleBridgeStatus.NO_SELECTED_TRACK,
                UniversalSubtitleBridgeStatus.ERROR,
            ) -> "VLC 원본 자막 · 사용자 설정 제한"
        selectedPlexSubtitle != null && !selectedPlexSubtitle.isManualTextSubtitle() ->
            "이미지 또는 원본 형식 자막 · 사용자 설정 제한"
        else -> null
    }
    val subtitleRendererLimited = subtitleRendererStatus?.contains("제한") == true ||
        subtitleRendererStatus?.contains("변경 불가") == true
    val latestSubtitlesDisabled by rememberUpdatedState(subtitlesDisabled)
    val latestSelectedExternalSubtitleUrl by rememberUpdatedState(selectedExternalSubtitleUrl)
    val latestSelectedAppTextSubtitle by rememberUpdatedState(selectedAppTextSubtitle)
    val latestPreferredSubtitleTrack by rememberUpdatedState(preferredSubtitleTrack)
    val latestAppSubtitleOwnsSelection by rememberUpdatedState(appSubtitleOwnsSelection)
    val styledSubtitleTypeface = remember(subtitleStyle.font, customFontRevision) {
        resolveVlcSubtitleTypeface(context, subtitleStyle.font, customFontFile)
    }
    val sharedSubtitleAppearance = remember(subtitleStyle) {
        subtitleStyle.toSharedSubtitleAppearance()
    }

    fun applySubtitleStyleChange(updated: VlcSubtitleStyle) {
        if (updated == subtitleStyle && updated == draftSubtitleStyle) return
        draftSubtitleStyle = updated
        subtitleStyle = updated
        preferences.saveVlcSubtitleStyle(updated)
        // The app-side text overlay is recomposed from subtitleStyle, so it is
        // already updated without touching playback. Native VLC subtitle
        // options require a new renderer; request a debounced restart instead
        // of restarting for every intermediate slider position.
        if (!appSubtitleOwnsSelection && !subtitlesDisabled) {
            nativeSubtitleStyleApplyRevision++
        }
    }

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val pendingFontFile = File(context.cacheDir, "pending_vlc_subtitle_font")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    pendingFontFile.outputStream().use(input::copyTo)
                }
                require(pendingFontFile.length() in 1..32_000_000)
                android.graphics.Typeface.createFromFile(pendingFontFile)
                pendingFontFile.copyTo(customFontFile, overwrite = true)
                pendingFontFile.delete()
                val displayName = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                preferences.edit()
                    .putString("custom_subtitle_font_name", displayName ?: "사용자 폰트")
                    .apply()
                customFontRevision++
                applySubtitleStyleChange(
                    subtitleStyle.copy(font = VlcSubtitleFont.CUSTOM),
                )
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "사용자 글꼴을 불러왔습니다." else "글꼴 파일을 열 수 없습니다.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun updateTrackLists(player: MediaPlayer) {
        audioTracks = player.audioTracks.orEmpty().map { VlcTrack(it.id, it.name) }
        subtitleTracks = player.spuTracks.orEmpty().map { VlcTrack(it.id, it.name) }
        selectedAudioTrack = player.audioTrack
        if (defaultAudioTrack < 0 && player.audioTrack >= 0) defaultAudioTrack = player.audioTrack
        selectedSubtitleTrack = player.spuTrack
    }

    fun showGestureFeedback(message: String) {
        controlsInteractionRevision++
        gestureFeedback = message
        gestureFeedbackRevision++
    }

    fun openSettings(page: VlcSettingsPage = VlcSettingsPage.MAIN) {
        controlsVisible = false
        settingsPage = page
        settingsVisible = true
    }

    fun navigateBackFromSettings() {
        when {
            settingsPage == VlcSettingsPage.DISPLAY_ADVANCED -> {
                settingsPage = VlcSettingsPage.DISPLAY
            }
            settingsPage == VlcSettingsPage.SUBTITLE_STYLE -> {
                settingsPage = VlcSettingsPage.SUBTITLE
            }
            settingsPage != VlcSettingsPage.MAIN -> {
                settingsPage = VlcSettingsPage.MAIN
            }
            else -> settingsVisible = false
        }
    }

    fun updateVideoSettings(value: VlcVideoSettings) {
        selectedVideoPreset = 0
        videoSettings = value
        preferences.saveVlcVideoSettings(value)
        applyVlcWindowBrightness(activity, value.screenBrightness)
        videoLayout?.let { applyVlcVideoAppearance(it, value) }
    }

    fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        val target = (player.time + deltaMs).coerceIn(0L, player.length.coerceAtLeast(0L))
        player.setTime(target, true)
        positionMs = target
        seekPreview = target.toFloat()
        controlsVisible = true
        showGestureFeedback(
            if (deltaMs >= 0) "+${formatVlcTime(deltaMs)}" else "−${formatVlcTime(-deltaMs)}",
        )
    }

    fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
        controlsVisible = true
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation
        val previousScreenBrightness = window?.attributes?.screenBrightness
        val insetsController = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            applyVlcWindowBrightness(activity, videoSettings.screenBrightness)
        }
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
            if (window != null && previousScreenBrightness != null) {
                window.attributes = window.attributes.apply {
                    screenBrightness = previousScreenBrightness
                }
            }
        }
    }

    DisposableEffect(source.ratingKey, source.url, videoLayout, rendererRevision) {
        val layout = videoLayout
        if (layout == null) {
            onDispose { }
        } else {
            val libVlc = try {
                val args = buildVlcArguments(
                    context,
                    subtitleStyle,
                    customFontFile,
                    optimizationMode,
                )
                LibVLC(context.applicationContext, args)
            } catch (error: Throwable) {
                Log.e("PlexPlayUniversal", "LibVLC initialization failed", error)
                mainHandler.post {
                    isPlaying = false
                    isBuffering = false
                    controlsVisible = true
                    errorMessage = "호환 재생 엔진을 시작할 수 없습니다. 앱을 다시 실행해 주세요."
                }
                return@DisposableEffect onDispose { }
            }
            val player = try {
                MediaPlayer(libVlc)
            } catch (error: Throwable) {
                Log.e("PlexPlayUniversal", "VLC player creation failed", error)
                runCatching { libVlc.release() }
                mainHandler.post {
                    isPlaying = false
                    isBuffering = false
                    controlsVisible = true
                    errorMessage = "호환 재생기를 만들 수 없습니다."
                }
                return@DisposableEffect onDispose { }
            }
            var pendingSeek = restorePositionMs.coerceAtLeast(0L)
            var completed = false
            val playbackUrls = (listOf(source.url) + source.fallbackUrls)
                .filter(String::isNotBlank)
                .distinct()
                .ifEmpty { listOf(source.url) }
            var playbackUrlIndex = 0
            var reconnectAttempts = 0
            var restartingPlayback = false
            var restartGeneration = 0
            var externalSubtitleAttached = false
            var recoveryAnchorMs = pendingSeek
            var playbackObservedPlaying = false
            var playbackObservedProgress = false
            var playbackStartPositionMs = pendingSeek
            var disposed = false

            fun startPlaybackUrl(url: String, resumeAtMs: Long) {
                pendingSeek = resumeAtMs.coerceAtLeast(0L)
                recoveryAnchorMs = pendingSeek
                playbackStartPositionMs = pendingSeek
                playbackObservedPlaying = false
                playbackObservedProgress = false
                externalSubtitleAttached = false
                try {
                    val media = Media(libVlc, Uri.parse(url.withPlexToken(source.token)))
                    // Respect the device decoder blacklist and allow LibVLC to
                    // fall back to software decoding instead of crashing.
                    media.setHWDecoderEnabled(true, false)
                    media.addOption(":network-caching=${optimizationMode.cachingMs()}")
                    media.addOption(":file-caching=${optimizationMode.cachingMs()}")
                    media.addOption(":http-user-agent=Plex Play Universal/1.0")
                    player.media = media
                    media.release()
                    player.play()
                } catch (error: Throwable) {
                    Log.e("PlexPlayUniversal", "VLC media start failed", error)
                    restartingPlayback = false
                    isPlaying = false
                    isBuffering = false
                    controlsVisible = true
                    errorMessage = "호환 재생 스트림을 열 수 없습니다."
                }
            }

            fun schedulePlaybackUrl(url: String, resumeAtMs: Long) {
                restartingPlayback = true
                val scheduledGeneration = ++restartGeneration
                // Stop first, then give the native decoder time to tear down.
                // Starting another Media from an error callback without this
                // gap can crash some OTT MediaCodec implementations.
                runCatching { player.stop() }
                mainHandler.postDelayed(
                    {
                        if (disposed || scheduledGeneration != restartGeneration) {
                            return@postDelayed
                        }
                        startPlaybackUrl(url, resumeAtMs)
                    },
                    450L,
                )
            }

            fun recoverPlayback(resumeAtMs: Long): Boolean {
                if (restartingPlayback) return true
                return when {
                    reconnectAttempts < 2 -> {
                        reconnectAttempts++
                        isBuffering = true
                        controlsVisible = true
                        errorMessage = "재생 연결 복구 중 $reconnectAttempts/2"
                        schedulePlaybackUrl(playbackUrls[playbackUrlIndex], resumeAtMs)
                        true
                    }
                    playbackUrlIndex < playbackUrls.lastIndex -> {
                        playbackUrlIndex++
                        reconnectAttempts = 0
                        isBuffering = true
                        controlsVisible = true
                        errorMessage = "대체 재생 경로로 다시 연결하는 중입니다."
                        schedulePlaybackUrl(playbackUrls[playbackUrlIndex], resumeAtMs)
                        true
                    }
                    else -> false
                }
            }
            fun applyPreferredSubtitleSelection() {
                when {
                    latestSubtitlesDisabled || latestAppSubtitleOwnsSelection -> {
                        if (player.spuTrack >= 0) player.setSpuTrack(-1)
                    }
                    latestSelectedExternalSubtitleUrl != null -> {
                        if (latestSelectedAppTextSubtitle?.isEmbedded == true && player.spuTrack < 0) {
                            player.spuTracks.orEmpty()
                                .firstOrNull { track -> track.name.isKoreanSubtitleName() }
                                ?.let { player.setSpuTrack(it.id) }
                        }
                    }
                    latestPreferredSubtitleTrack != null -> {
                        val preferredId = latestPreferredSubtitleTrack ?: -1
                        if (
                            preferredId >= 0 &&
                            player.spuTracks.orEmpty().any { it.id == preferredId } &&
                            player.spuTrack != preferredId
                        ) {
                            player.setSpuTrack(preferredId)
                        }
                    }
                    player.spuTrack < 0 -> player.spuTracks.orEmpty()
                        .firstOrNull { track -> track.name.isKoreanSubtitleName() }
                        ?.let { player.setSpuTrack(it.id) }
                }
            }
            val attachResult = runCatching {
                // false keeps the broadly compatible SurfaceView output.
                player.attachViews(layout, null, true, false)
            }
            if (attachResult.isFailure) {
                Log.e("PlexPlayUniversal", "VLC output attachment failed", attachResult.exceptionOrNull())
                runCatching { player.release() }
                runCatching { libVlc.release() }
                mainHandler.post {
                    isPlaying = false
                    isBuffering = false
                    controlsVisible = true
                    errorMessage = "호환 재생 화면을 준비할 수 없습니다."
                }
                return@DisposableEffect onDispose { }
            }
            layout.post {
                if (!disposed) {
                    applyVlcVideoScale(layout, videoScale)
                    applyVlcVideoAppearance(layout, videoSettings)
                }
            }
            player.setEventListener { event ->
                if (disposed) return@setEventListener
                mainHandler.post eventDispatch@{
                    if (disposed) return@eventDispatch
                    when (event.type) {
                        MediaPlayer.Event.Opening -> {
                            isBuffering = true
                            errorMessage = null
                        }
                        MediaPlayer.Event.Buffering -> isBuffering = event.buffering < 100f
                        MediaPlayer.Event.Playing -> {
                            restartingPlayback = false
                            playbackObservedPlaying = true
                            isPlaying = true
                            isBuffering = false
                            player.rate = playbackSpeed
                            if (pendingSeek > 0L) {
                                player.setTime(pendingSeek, true)
                                pendingSeek = 0L
                            }
                            updateTrackLists(player)
                            latestSelectedExternalSubtitleUrl
                                ?.takeIf { latestSelectedAppTextSubtitle?.isEmbedded != true }
                                ?.takeUnless { latestAppSubtitleOwnsSelection }
                                ?.let { subtitleUrl ->
                                if (!externalSubtitleAttached) {
                                    externalSubtitleAttached = player.addSlave(
                                        IMedia.Slave.Type.Subtitle,
                                        Uri.parse(subtitleUrl.withPlexToken(source.token)),
                                        true,
                                    )
                                }
                            }
                            if (!automaticAudioSelection) {
                                preferredAudioTrack?.let { player.setAudioTrack(it) }
                            }
                            applyPreferredSubtitleSelection()
                            updateTrackLists(player)
                        }
                        MediaPlayer.Event.Paused -> isPlaying = false
                        MediaPlayer.Event.TimeChanged -> {
                            positionMs = event.timeChanged.coerceAtLeast(0L)
                            if (
                                playbackObservedPlaying &&
                                positionMs >= playbackStartPositionMs + 250L
                            ) {
                                playbackObservedProgress = true
                            }
                            if (
                                recoveryAnchorMs != Long.MAX_VALUE &&
                                positionMs - recoveryAnchorMs >= 30_000L
                            ) {
                                reconnectAttempts = 0
                                recoveryAnchorMs = Long.MAX_VALUE
                            }
                            if (!draggingProgress) seekPreview = positionMs.toFloat()
                        }
                        MediaPlayer.Event.LengthChanged -> {
                            durationMs = event.lengthChanged.coerceAtLeast(1L)
                        }
                        MediaPlayer.Event.ESAdded,
                        MediaPlayer.Event.ESDeleted,
                        MediaPlayer.Event.ESSelected,
                        -> {
                            updateTrackLists(player)
                            // LibVLC may asynchronously re-select a native SPU
                            // after an external/app-rendered subtitle was chosen.
                            // Re-assert the logical selection for every ES change,
                            // including ESSelected, to prevent the native renderer
                            // from covering the styled app overlay.
                            applyPreferredSubtitleSelection()
                            updateTrackLists(player)
                        }
                        MediaPlayer.Event.EndReached -> {
                            if (!completed) {
                                val actualPosition = player.time
                                    .coerceAtLeast(positionMs)
                                    .coerceAtLeast(0L)
                                val expectedDuration = maxOf(
                                    player.length.coerceAtLeast(0L),
                                    source.durationMs.coerceAtLeast(0L),
                                )
                                val minimumValidProgress = minOf(
                                    10_000L,
                                    (expectedDuration / 2L).coerceAtLeast(1L),
                                )
                                val endedNormally = !restartingPlayback &&
                                    playbackObservedPlaying &&
                                    playbackObservedProgress &&
                                    expectedDuration > 0L &&
                                    actualPosition >= minimumValidProgress &&
                                    (
                                        actualPosition + 5_000L >= expectedDuration ||
                                            actualPosition.toDouble() / expectedDuration >= .98
                                        )
                                if (endedNormally) {
                                    completed = true
                                    latestOnProgress(source, actualPosition, "stopped")
                                    latestOnPlaybackCompleted(actualPosition)
                                } else if (!recoverPlayback(actualPosition)) {
                                    isPlaying = false
                                    isBuffering = false
                                    controlsVisible = true
                                    errorMessage = "재생이 중간에 종료되었습니다. 서버 원본 경로를 확인해 주세요."
                                }
                            }
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            isPlaying = false
                            if (!restartingPlayback) {
                                val resumeAt = player.time.coerceAtLeast(positionMs).coerceAtLeast(0L)
                                if (!recoverPlayback(resumeAt)) {
                                    restartingPlayback = false
                                    isBuffering = false
                                    controlsVisible = true
                                    errorMessage = "이 파일을 범용 재생 엔진에서도 열 수 없습니다. 파일 또는 서버 연결을 확인해 주세요."
                                }
                            }
                        }
                    }
                }
            }
            mediaPlayer = player
            startPlaybackUrl(playbackUrls.firstOrNull() ?: source.url, pendingSeek)

            onDispose {
                disposed = true
                restartGeneration++
                runCatching { player.setEventListener(null) }
                restorePositionMs = runCatching { player.time }
                    .getOrDefault(positionMs)
                    .coerceAtLeast(positionMs)
                latestOnProgress(source, restorePositionMs, "stopped")
                mediaPlayer = null
                runCatching { player.stop() }
                runCatching { player.detachViews() }
                runCatching { player.release() }
                runCatching { libVlc.release() }
            }
        }
    }

    LaunchedEffect(source.ratingKey, selectedAppTextSubtitle?.url) {
        styledSubtitleText = ""
        styledSubtitleCues = null
        styledSubtitleSourceUrl = null
        styledSubtitleLoadFailed = false
        val subtitle = selectedAppTextSubtitle ?: run {
            styledSubtitleLoading = false
            return@LaunchedEffect
        }
        styledSubtitleLoading = true
        val cues = try {
            loadManualSubtitleCues(subtitle, source.token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }
        if (cues.isNotEmpty()) {
            styledSubtitleSourceUrl = subtitle.url
            styledSubtitleCues = cues
            // Keep playback running. If VLC attached the same subtitle while
            // it was loading, hide that native track and let the shared styled
            // overlay become the single subtitle renderer.
            mediaPlayer?.setSpuTrack(-1)
            selectedSubtitleTrack = -1
            styledSubtitleLoading = false
            Toast.makeText(
                context,
                "자막 사용자 설정을 적용했습니다.",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            styledSubtitleCues = emptyList()
            styledSubtitleLoading = false
            styledSubtitleLoadFailed = true
            // Do not silently switch to VLC here. The subtitle-only Media3
            // bridge now gets a chance to read the same sidecar or demux the
            // original container. Native VLC is used only after that path has
            // explicitly reported that no styled text track is available.
        }
    }

    LaunchedEffect(
        subtitleBridge.status,
        subtitleBridge.bitmapOnly,
        subtitleBridgeEnabled,
        selectedPlexSubtitleId,
        preferredSubtitleTrack,
        mediaPlayer,
    ) {
        if (!subtitleBridgeEnabled || subtitlesDisabled) return@LaunchedEffect
        val requiresNativeFallback = subtitleBridge.bitmapOnly ||
            subtitleBridge.status == UniversalSubtitleBridgeStatus.NO_TEXT_TRACKS ||
            subtitleBridge.status == UniversalSubtitleBridgeStatus.NO_SELECTED_TRACK ||
            (subtitleBridge.status == UniversalSubtitleBridgeStatus.READY && !subtitleBridge.active) ||
            subtitleBridge.status == UniversalSubtitleBridgeStatus.ERROR
        if (!requiresNativeFallback) return@LaunchedEffect

        val subtitle = selectedPlexSubtitle
        if (subtitle != null && !subtitle.isEmbedded) {
            val attached = mediaPlayer?.addSlave(
                IMedia.Slave.Type.Subtitle,
                Uri.parse(subtitle.url.withPlexToken(source.token)),
                true,
            ) == true
            if (!attached) {
                Toast.makeText(context, "자막 파일을 읽지 못했습니다.", Toast.LENGTH_LONG).show()
            }
        } else {
            val fallback = preferredSubtitleTrack
                ?.let { id -> subtitleTracks.firstOrNull { it.id == id } }
                ?: subtitleTracks.bestNativeSubtitleMatch(subtitle)
                ?: subtitleTracks.firstOrNull { it.name.isKoreanSubtitleName() }
                ?: subtitleTracks.firstOrNull { it.id >= 0 }
            fallback?.let {
                mediaPlayer?.setSpuTrack(it.id)
                selectedSubtitleTrack = it.id
            }
        }
    }

    LaunchedEffect(mediaPlayer, appSubtitleOwnsSelection, subtitlesDisabled) {
        if (subtitlesDisabled) {
            mediaPlayer?.setSpuTrack(-1)
            selectedSubtitleTrack = -1
        } else if (appSubtitleOwnsSelection) {
            mediaPlayer?.setSpuTrack(-1)
        }
    }

    LaunchedEffect(nativeSubtitleStyleApplyRevision) {
        if (nativeSubtitleStyleApplyRevision <= 0) return@LaunchedEffect
        // Slider/key repeats can emit many values. Saving and app-side overlay
        // updates remain immediate, while native VLC is recreated only once
        // after the latest change settles.
        delay(500)
        if (!latestAppSubtitleOwnsSelection && !latestSubtitlesDisabled) {
            restorePositionMs = mediaPlayer?.time?.coerceAtLeast(0L) ?: positionMs
            rendererRevision++
        }
    }

    LaunchedEffect(mediaPlayer, styledSubtitleCues) {
        val cues = styledSubtitleCues ?: return@LaunchedEffect
        while (true) {
            // TimeChanged is the reliable clock on HLS and several OTT VLC
            // builds. MediaPlayer.time can remain -1/0 even while playback is
            // advancing, which previously kept the app subtitle permanently
            // on the first (usually empty) cue.
            styledSubtitleText = cues.textAt(positionMs.coerceAtLeast(0L))
            delay(120)
        }
    }

    LaunchedEffect(mediaPlayer, source.ratingKey) {
        while (mediaPlayer != null) {
            delay(10_000)
            mediaPlayer?.let { player ->
                latestOnProgress(
                    source,
                    player.time.coerceAtLeast(0L),
                    if (player.isPlaying) "playing" else "paused",
                )
            }
        }
    }

    LaunchedEffect(controlsVisible, settingsVisible, isPlaying, controlsInteractionRevision) {
        if (controlsVisible && !settingsVisible && isPlaying) {
            delay(7_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, settingsVisible) {
        if (settingsVisible) return@LaunchedEffect
        if (controlsVisible) {
            delay(60)
            runCatching { playButtonFocusRequester.requestFocus() }
        } else {
            runCatching { controlsFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(videoSettings, videoLayout) {
        applyVlcWindowBrightness(activity, videoSettings.screenBrightness)
        videoLayout?.let { layout ->
            layout.post { applyVlcVideoAppearance(layout, videoSettings) }
        }
    }

    LaunchedEffect(gestureFeedbackRevision) {
        if (gestureFeedbackRevision == 0) return@LaunchedEffect
        delay(900)
        gestureFeedback = null
    }

    BackHandler {
        if (settingsVisible) navigateBackFromSettings() else onClose()
    }

    fun handleRemoteKey(nativeEvent: AndroidKeyEvent): Boolean {
        if (settingsVisible) return true
        val isDown = nativeEvent.action == AndroidKeyEvent.ACTION_DOWN
        val isUp = nativeEvent.action == AndroidKeyEvent.ACTION_UP
        if (!isDown && !isUp) return false
        if (isDown) controlsInteractionRevision++

        val controllerNavigationKey = nativeEvent.keyCode in listOf(
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A,
            AndroidKeyEvent.KEYCODE_BUTTON_SELECT,
            AndroidKeyEvent.KEYCODE_BUTTON_START,
            AndroidKeyEvent.KEYCODE_SPACE,
            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeyEvent.KEYCODE_DPAD_UP,
            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
        )
        if (controlsVisible && controllerNavigationKey && nativeEvent.repeatCount == 0) {
            if (isUp) {
                when (nativeEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (leftLongPressSeeking) {
                        leftLongPressSeeking = false
                        return true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (rightLongPressSeeking) {
                        rightLongPressSeeking = false
                        return true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_BUTTON_A,
                    AndroidKeyEvent.KEYCODE_BUTTON_SELECT,
                    AndroidKeyEvent.KEYCODE_BUTTON_START,
                    AndroidKeyEvent.KEYCODE_SPACE,
                    -> if (confirmLongPressConsumed) {
                        confirmLongPressConsumed = false
                        return true
                    }
                }
            }
            return false
        }

        return when (nativeEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A,
            AndroidKeyEvent.KEYCODE_BUTTON_SELECT,
            AndroidKeyEvent.KEYCODE_BUTTON_START,
            AndroidKeyEvent.KEYCODE_SPACE,
            -> {
                if (isDown && nativeEvent.repeatCount > 0) {
                    confirmLongPressConsumed = true
                } else if (isUp) {
                    if (confirmLongPressConsumed) {
                        confirmLongPressConsumed = false
                    } else {
                        controlsVisible = true
                    }
                }
                true
            }

            AndroidKeyEvent.KEYCODE_HEADSETHOOK,
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> {
                if (isDown && nativeEvent.repeatCount == 0) togglePlayback()
                true
            }

            AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (isDown && nativeEvent.repeatCount == 0) mediaPlayer?.play()
                true
            }

            AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (isDown && nativeEvent.repeatCount == 0) mediaPlayer?.pause()
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isDown && nativeEvent.repeatCount > 0) {
                    leftLongPressSeeking = true
                    seekBy(-10_000L)
                } else if (isUp) {
                    if (leftLongPressSeeking) leftLongPressSeeking = false else seekBy(-10_000L)
                }
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isDown && nativeEvent.repeatCount > 0) {
                    rightLongPressSeeking = true
                    seekBy(10_000L)
                } else if (isUp) {
                    if (rightLongPressSeeking) rightLongPressSeeking = false else seekBy(10_000L)
                }
                true
            }

            AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
            AndroidKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            AndroidKeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            AndroidKeyEvent.KEYCODE_BUTTON_L1,
            -> {
                if (isDown) seekBy(-10_000L)
                true
            }

            AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            AndroidKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            AndroidKeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            AndroidKeyEvent.KEYCODE_BUTTON_R1,
            -> {
                if (isDown) seekBy(10_000L)
                true
            }

            AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
            -> {
                if (isDown && nativeEvent.repeatCount == 0 && hasPreviousPlayback) {
                    onPlayPrevious(positionMs)
                    showGestureFeedback("이전화")
                } else if (isDown) {
                    seekBy(-10_000L)
                }
                true
            }

            AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
            AndroidKeyEvent.KEYCODE_CHANNEL_UP,
            -> {
                if (isDown && nativeEvent.repeatCount == 0) {
                    if (hasNextPlayback) {
                        onPlayNext(positionMs)
                        showGestureFeedback("다음화")
                    } else {
                        showGestureFeedback("다음 영상이 없습니다")
                    }
                }
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_UP,
            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                if (isDown && nativeEvent.repeatCount > 0) openSettings()
                else if (isDown) controlsVisible = true
                true
            }

            AndroidKeyEvent.KEYCODE_MENU,
            AndroidKeyEvent.KEYCODE_SETTINGS,
            AndroidKeyEvent.KEYCODE_INFO,
            AndroidKeyEvent.KEYCODE_GUIDE,
            AndroidKeyEvent.KEYCODE_BUTTON_MODE,
            -> {
                if (isDown && nativeEvent.repeatCount == 0) openSettings()
                true
            }

            AndroidKeyEvent.KEYCODE_CAPTIONS -> {
                if (isDown && nativeEvent.repeatCount == 0) openSettings(VlcSettingsPage.SUBTITLE)
                true
            }

            AndroidKeyEvent.KEYCODE_LANGUAGE_SWITCH -> {
                if (isDown && nativeEvent.repeatCount == 0) openSettings(VlcSettingsPage.AUDIO)
                true
            }

            AndroidKeyEvent.KEYCODE_MEDIA_STOP -> {
                if (isDown && nativeEvent.repeatCount == 0) {
                    mediaPlayer?.pause()
                    onProgress(source, positionMs, "stopped")
                    onClose()
                }
                true
            }

            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event -> handleRemoteKey(event.nativeKeyEvent) }
            .pointerInput(videoScale) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var pinchStarted = false
                    var zoomAmount = 1f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinchStarted = true
                            val eventZoom = event.calculateZoom()
                            if (eventZoom.isFinite() && eventZoom > 0f) zoomAmount *= eventZoom
                        }
                        if (pinchStarted) event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (pinchStarted) {
                        val zoomModes = listOf(
                            VlcVideoScale.FIT,
                            VlcVideoScale.ZOOM,
                            VlcVideoScale.ZOOM_LARGE,
                        )
                        val targetScale = (videoScale.scaleX * zoomAmount).coerceIn(1f, 1.3f)
                        val selected = zoomModes.minBy { abs(it.scaleX - targetScale) }
                        selectedVideoPreset = 0
                        videoScale = selected
                        preferences.edit().putString("video_scale_mode", selected.storage).apply()
                        showGestureFeedback(selected.label)
                    }
                }
            }
            .pointerInput(
                source.ratingKey,
                source.url,
                activity,
                audioManager,
                videoScale,
                subtitleStyle,
                useAppSubtitleOverlay,
            ) {
                var startX = 0f
                var startY = 0f
                var totalX = 0f
                var totalY = 0f
                var mode: VlcGestureMode? = null
                var startVolume = 0
                var startBrightness = .5f
                var startPosition = 0L
                var seekTarget = 0L
                var startSubtitleHorizontal = 0
                var startSubtitleVertical = 0
                var subtitleHorizontalTarget = 0
                var subtitleVerticalTarget = 0

                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        startY = offset.y
                        totalX = 0f
                        totalY = 0f
                        mode = null
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        startBrightness = activity?.window?.attributes?.screenBrightness
                            ?.takeIf { it >= 0f }
                            ?: (videoSettings.screenBrightness / 100f)
                        startPosition = mediaPlayer?.time?.coerceAtLeast(0L) ?: positionMs
                        seekTarget = startPosition
                        startSubtitleHorizontal = subtitleStyle.horizontalOffsetPercent
                        startSubtitleVertical = subtitleStyle.verticalOffsetPercent
                        subtitleHorizontalTarget = startSubtitleHorizontal
                        subtitleVerticalTarget = startSubtitleVertical
                    },
                    onDragCancel = { mode = null },
                    onDragEnd = {
                        when (mode) {
                            VlcGestureMode.SEEK -> {
                                mediaPlayer?.setTime(seekTarget, true)
                                positionMs = seekTarget
                                seekPreview = seekTarget.toFloat()
                                onProgress(
                                    source,
                                    seekTarget,
                                    if (mediaPlayer?.isPlaying == true) "playing" else "paused",
                                )
                            }
                            VlcGestureMode.SCALE -> {
                                if (abs(totalX) >= size.width * .12f) {
                                    val modes = VlcVideoScale.entries
                                    val currentIndex = modes.indexOf(videoScale)
                                    val direction = if (totalX > 0f) 1 else -1
                                    val selected = modes[
                                        (currentIndex + direction).coerceIn(0, modes.lastIndex)
                                    ]
                                    selectedVideoPreset = 0
                                    videoScale = selected
                                    preferences.edit()
                                        .putString("video_scale_mode", selected.storage)
                                        .apply()
                                    showGestureFeedback(selected.label)
                                }
                            }
                            VlcGestureMode.SUBTITLE_POSITION -> {
                                val updated = subtitleStyle.copy(
                                    horizontalOffsetPercent = subtitleHorizontalTarget,
                                    verticalOffsetPercent = subtitleVerticalTarget,
                                )
                                applySubtitleStyleChange(updated)
                            }
                            else -> Unit
                        }
                        mode = null
                    },
                ) { change, dragAmount ->
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    if (mode == null) {
                        val subtitleGestureZone =
                            (
                                selectedSubtitleTrack >= 0 ||
                                    selectedExternalSubtitleUrl != null ||
                                    useAppSubtitleOverlay ||
                                    appSubtitleOwnsSelection
                                ) &&
                                startY >= size.height * .48f &&
                                startY <= size.height * .74f &&
                                startX >= size.width * .16f &&
                                startX <= size.width * .84f
                        mode = when {
                            subtitleGestureZone -> VlcGestureMode.SUBTITLE_POSITION
                            startY >= size.height * .78f && abs(totalX) > abs(totalY) ->
                                VlcGestureMode.SEEK
                            startY <= size.height * .28f && abs(totalX) > abs(totalY) ->
                                VlcGestureMode.SCALE
                            abs(totalY) > abs(totalX) && startX >= size.width * .88f ->
                                VlcGestureMode.VOLUME
                            abs(totalY) > abs(totalX) && startX <= size.width * .12f ->
                                VlcGestureMode.BRIGHTNESS
                            else -> null
                        }
                    }
                    when (mode) {
                        VlcGestureMode.VOLUME -> {
                            change.consume()
                            val maxVolume = audioManager
                                .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                .coerceAtLeast(1)
                            val volume = (
                                startVolume - totalY / size.height * maxVolume * 1.5f
                                ).roundToInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                            showGestureFeedback("볼륨 ${volume * 100 / maxVolume}%")
                        }
                        VlcGestureMode.BRIGHTNESS -> {
                            change.consume()
                            val brightness = (
                                startBrightness - totalY / size.height * 1.5f
                                ).coerceIn(.01f, 1f)
                            val updated = videoSettings.copy(screenBrightness = brightness * 100f)
                            updateVideoSettings(updated)
                            showGestureFeedback("밝기 ${(brightness * 100f).roundToInt()}%")
                        }
                        VlcGestureMode.SEEK -> {
                            change.consume()
                            val seekDelta = (totalX / size.width * 600_000f).toLong()
                            seekTarget = (startPosition + seekDelta)
                                .coerceIn(0L, durationMs.coerceAtLeast(0L))
                            val sign = if (seekDelta >= 0) "+" else "−"
                            showGestureFeedback(
                                "$sign${formatVlcTime(abs(seekDelta))}  ${formatVlcTime(seekTarget)}",
                            )
                        }
                        VlcGestureMode.SCALE -> {
                            change.consume()
                            showGestureFeedback(
                                if (totalX >= 0f) "화면 크기 확대 방향" else "화면 크기 축소 방향",
                            )
                        }
                        VlcGestureMode.SUBTITLE_POSITION -> {
                            change.consume()
                            subtitleHorizontalTarget = (
                                startSubtitleHorizontal + totalX / size.width * 100f
                                ).roundToInt().coerceIn(-100, 100)
                            subtitleVerticalTarget = (
                                startSubtitleVertical + totalY / size.height * 100f
                                ).roundToInt().coerceIn(-100, 100)
                            showGestureFeedback(
                                "자막 위치 가로 $subtitleHorizontalTarget  세로 $subtitleVerticalTarget",
                            )
                        }
                        null -> Unit
                    }
                }
            }
            .focusRequester(controlsFocusRequester)
            .focusable()
            .clickable { controlsVisible = !controlsVisible },
    ) {
        AndroidView(
            factory = { ctx -> VLCVideoLayout(ctx).also { videoLayout = it } },
            update = { layout ->
                applyVlcVideoScale(layout, videoScale)
                applyVlcVideoAppearance(layout, videoSettings)
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (useAppSubtitleOverlay) {
            HorizontalSubtitleOverlay(
                text = appSubtitleText,
                typeface = styledSubtitleTypeface,
                appearance = sharedSubtitleAppearance,
                modifier = Modifier.fillMaxSize(),
            )
            VerticalSubtitleOverlay(
                text = appSubtitleText,
                typeface = styledSubtitleTypeface,
                appearance = sharedSubtitleAppearance,
                modifier = Modifier.fillMaxSize(),
            )
        }

        gestureFeedback?.let { feedback ->
            Surface(
                color = Color.Black.copy(alpha = .78f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp),
            ) {
                Text(
                    feedback,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, "재생 화면 닫기", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            source.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(source.subtitle ?: "범용 코덱 재생", color = Color(0xFFE5A00D))
                        Text(
                            "이전화 · ${previousPlaybackTitle ?: "없음"}   |   " +
                                "다음화 · ${nextPlaybackTitle ?: "없음"}",
                            color = Color.White.copy(alpha = .78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = {
                        settingsPage = VlcSettingsPage.MAIN
                        settingsVisible = true
                    }) {
                        Icon(Icons.Rounded.Settings, "재생 설정", tint = Color.White)
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VlcPlayerControlButton(
                        enabled = hasPreviousPlayback,
                        onClick = { onPlayPrevious(positionMs) },
                        modifier = Modifier
                            .size(56.dp)
                            .focusRequester(previousButtonFocusRequester)
                            .focusProperties { right = rewindButtonFocusRequester },
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = previousPlaybackTitle ?: "이전화",
                    )
                    VlcPlayerControlButton(
                        onClick = { seekBy(-10_000L) },
                        modifier = Modifier
                            .size(56.dp)
                            .focusRequester(rewindButtonFocusRequester)
                            .focusProperties {
                                left = previousButtonFocusRequester
                                right = playButtonFocusRequester
                            },
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = "10초 뒤로",
                    )
                    VlcPlayerControlButton(
                        onClick = ::togglePlayback,
                        modifier = Modifier
                            .size(76.dp)
                            .focusRequester(playButtonFocusRequester)
                            .focusProperties {
                                left = rewindButtonFocusRequester
                                right = forwardButtonFocusRequester
                            },
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "일시정지" else "재생",
                        iconSize = 58,
                    )
                    VlcPlayerControlButton(
                        onClick = { seekBy(10_000L) },
                        modifier = Modifier
                            .size(56.dp)
                            .focusRequester(forwardButtonFocusRequester)
                            .focusProperties {
                                left = playButtonFocusRequester
                                right = nextButtonFocusRequester
                            },
                        icon = Icons.Rounded.FastForward,
                        contentDescription = "10초 앞으로",
                    )
                    VlcPlayerControlButton(
                        enabled = hasNextPlayback,
                        onClick = { onPlayNext(positionMs) },
                        modifier = Modifier
                            .size(56.dp)
                            .focusRequester(nextButtonFocusRequester)
                            .focusProperties { left = forwardButtonFocusRequester },
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = nextPlaybackTitle ?: "다음화",
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                ) {
                    errorMessage?.let {
                        Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (isBuffering) {
                        Text("범용 재생 준비 중…", color = Color.White)
                    }
                    Slider(
                        value = seekPreview.coerceIn(0f, durationMs.toFloat()),
                        onValueChange = {
                            draggingProgress = true
                            seekPreview = it
                        },
                        onValueChangeFinished = {
                            val target = seekPreview.roundToLong()
                            mediaPlayer?.setTime(target, true)
                            positionMs = target
                            draggingProgress = false
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatVlcTime(positionMs), color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text(formatVlcTime(durationMs), color = Color.White)
                    }
                }
            }
        }
    }

    if (settingsVisible) {
        VlcSettingsDialog(
            player = mediaPlayer,
            page = settingsPage,
            source = source,
            playbackQuality = playbackQuality,
            playbackSpeed = playbackSpeed,
            videoScale = videoScale,
            videoSettings = videoSettings,
            optimizationMode = optimizationMode,
            selectedVideoPreset = selectedVideoPreset,
            audioTracks = audioTracks,
            selectedAudioTrack = selectedAudioTrack,
            automaticAudioSelection = automaticAudioSelection,
            subtitleTracks = subtitleTracks.filterNot { track ->
                plexSubtitleChoices.bestPlexTextSubtitleMatch(track.name) != null
            },
            selectedSubtitleTrack = selectedSubtitleTrack,
            subtitlesDisabled = subtitlesDisabled,
            automaticSubtitleSelection = automaticSubtitleSelection,
            selectedExternalSubtitleUrl = selectedExternalSubtitleUrl,
            plexSubtitleChoices = plexSubtitleChoices,
            styledSubtitleLoading = styledSubtitleLoading,
            styledSubtitleLoadFailed = styledSubtitleLoadFailed,
            subtitleRendererStatus = subtitleRendererStatus,
            subtitleRendererLimited = subtitleRendererLimited,
            autoPlayNext = autoPlayNext,
            draftStyle = draftSubtitleStyle,
            customFontName = preferences.getString("custom_subtitle_font_name", null),
            onPageChanged = { settingsPage = it },
            onAudioTrack = { id ->
                automaticAudioSelection = false
                preferredAudioTrack = id
                mediaPlayer?.setAudioTrack(id)
                selectedAudioTrack = id
            },
            onAudioAuto = {
                automaticAudioSelection = true
                preferredAudioTrack = null
                val target = defaultAudioTrack.takeIf { it >= 0 }
                    ?: audioTracks.firstOrNull()?.id
                if (target != null) {
                    mediaPlayer?.setAudioTrack(target)
                    selectedAudioTrack = target
                }
            },
            onSubtitleOff = {
                subtitlesDisabled = true
                preferredSubtitleTrack = -1
                selectedPlexSubtitleId = null
                mediaPlayer?.setSpuTrack(-1)
                selectedSubtitleTrack = -1
            },
            onSubtitleAuto = {
                subtitlesDisabled = false
                preferredSubtitleTrack = null
                val appTextTarget = plexSubtitleChoices
                    .firstOrNull { it.isPreferredKoreanTextSubtitle() }
                if (appTextTarget != null) {
                    selectedPlexSubtitleId = appTextTarget.stableId
                    mediaPlayer?.setSpuTrack(-1)
                    selectedSubtitleTrack = -1
                } else {
                    selectedPlexSubtitleId = null
                    val target = subtitleTracks.firstOrNull { it.name.isKoreanSubtitleName() }
                        ?: subtitleTracks.firstOrNull { it.id >= 0 }
                    mediaPlayer?.setSpuTrack(target?.id ?: -1)
                    selectedSubtitleTrack = target?.id ?: -1
                }
            },
            onSubtitleTrack = { id ->
                subtitlesDisabled = false
                val nativeName = subtitleTracks.firstOrNull { it.id == id }?.name
                val appTextMatch = plexSubtitleChoices.bestPlexTextSubtitleMatch(nativeName)
                if (appTextMatch != null) {
                    preferredSubtitleTrack = null
                    selectedPlexSubtitleId = appTextMatch.stableId
                    mediaPlayer?.setSpuTrack(-1)
                    selectedSubtitleTrack = -1
                } else {
                    preferredSubtitleTrack = id
                    selectedPlexSubtitleId = null
                    mediaPlayer?.setSpuTrack(id)
                    selectedSubtitleTrack = id
                }
            },
            onExternalSubtitle = { subtitle ->
                subtitlesDisabled = false
                preferredSubtitleTrack = null
                selectedPlexSubtitleId = subtitle.stableId
                if (subtitle.isManualTextSubtitle()) {
                    mediaPlayer?.setSpuTrack(-1)
                    selectedSubtitleTrack = -1
                } else {
                    val attached = mediaPlayer?.addSlave(
                        IMedia.Slave.Type.Subtitle,
                        Uri.parse(subtitle.url.withPlexToken(source.token)),
                        true,
                    ) == true
                    if (!attached) {
                        restorePositionMs = mediaPlayer?.time?.coerceAtLeast(0L) ?: positionMs
                        rendererRevision++
                    }
                }
            },
            onPlaybackSpeedChanged = { speed ->
                playbackSpeed = speed
                mediaPlayer?.rate = speed
                preferences.edit().putFloat("playback_speed", speed).apply()
            },
            onPlaybackQualityChanged = { quality ->
                val resumeAt = mediaPlayer?.time?.coerceAtLeast(0L) ?: positionMs
                settingsVisible = false
                settingsPage = VlcSettingsPage.MAIN
                onPlaybackQualityChanged(quality, resumeAt)
            },
            onVideoScaleChanged = { scale ->
                selectedVideoPreset = 0
                videoScale = scale
                preferences.edit().putString("video_scale_mode", scale.storage).apply()
            },
            onVideoSettingsChanged = ::updateVideoSettings,
            onResetVideoSettings = {
                selectedVideoPreset = 0
                updateVideoSettings(VlcVideoSettings())
                videoScale = VlcVideoScale.FIT
                preferences.edit().putString("video_scale_mode", VlcVideoScale.FIT.storage).apply()
            },
            isVideoPresetAvailable = { slot -> preferences.isVlcVideoPresetAvailable(slot) },
            onSaveVideoPreset = { slot ->
                val saved = preferences.saveVlcVideoPreset(slot, videoSettings, videoScale)
                if (saved) selectedVideoPreset = slot
                Toast.makeText(
                    context,
                    if (saved) "화면 설정 $slot 번에 저장했습니다." else "화면 설정을 저장하지 못했습니다.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onLoadVideoPreset = { slot ->
                val preset = preferences.loadVlcVideoPreset(slot)
                if (preset != null) {
                    updateVideoSettings(preset.first)
                    videoScale = preset.second
                    preferences.edit().putString("video_scale_mode", preset.second.storage).apply()
                    selectedVideoPreset = slot
                    Toast.makeText(context, "화면 설정 $slot 번을 불러왔습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onOptimizationModeChanged = { mode ->
                if (optimizationMode != mode) {
                    restorePositionMs = mediaPlayer?.time?.coerceAtLeast(0L) ?: positionMs
                    optimizationMode = mode
                    preferences.edit().putString("device_optimization_mode", mode.storage).apply()
                    rendererRevision++
                }
            },
            onAutoPlayNextChanged = onAutoPlayNextChanged,
            onDraftStyleChanged = ::applySubtitleStyleChange,
            onPickFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
            onApplyStyle = {
                settingsPage = VlcSettingsPage.SUBTITLE
            },
            onClearCache = {
                val cleared = runCatching {
                    context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
                }.isSuccess
                Toast.makeText(
                    context,
                    if (cleared) "캐시를 삭제했습니다." else "캐시를 삭제하지 못했습니다.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onNavigateBack = ::navigateBackFromSettings,
        )
    }
}

@Composable
private fun VlcSettingsDialog(
    player: MediaPlayer?,
    page: VlcSettingsPage,
    source: PlaybackSource,
    playbackQuality: PlaybackQuality,
    playbackSpeed: Float,
    videoScale: VlcVideoScale,
    videoSettings: VlcVideoSettings,
    optimizationMode: VlcOptimizationMode,
    selectedVideoPreset: Int,
    audioTracks: List<VlcTrack>,
    selectedAudioTrack: Int,
    automaticAudioSelection: Boolean,
    subtitleTracks: List<VlcTrack>,
    selectedSubtitleTrack: Int,
    subtitlesDisabled: Boolean,
    automaticSubtitleSelection: Boolean,
    selectedExternalSubtitleUrl: String?,
    plexSubtitleChoices: List<io.mirr.plexplay.data.PlaybackSubtitle>,
    styledSubtitleLoading: Boolean,
    styledSubtitleLoadFailed: Boolean,
    subtitleRendererStatus: String?,
    subtitleRendererLimited: Boolean,
    autoPlayNext: Boolean,
    draftStyle: VlcSubtitleStyle,
    customFontName: String?,
    onPageChanged: (VlcSettingsPage) -> Unit,
    onAudioTrack: (Int) -> Unit,
    onAudioAuto: () -> Unit,
    onSubtitleOff: () -> Unit,
    onSubtitleAuto: () -> Unit,
    onSubtitleTrack: (Int) -> Unit,
    onExternalSubtitle: (io.mirr.plexplay.data.PlaybackSubtitle) -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onPlaybackQualityChanged: (PlaybackQuality) -> Unit,
    onVideoScaleChanged: (VlcVideoScale) -> Unit,
    onVideoSettingsChanged: (VlcVideoSettings) -> Unit,
    onResetVideoSettings: () -> Unit,
    isVideoPresetAvailable: (Int) -> Boolean,
    onSaveVideoPreset: (Int) -> Unit,
    onLoadVideoPreset: (Int) -> Unit,
    onOptimizationModeChanged: (VlcOptimizationMode) -> Unit,
    onAutoPlayNextChanged: (Boolean) -> Unit,
    onDraftStyleChanged: (VlcSubtitleStyle) -> Unit,
    onPickFont: () -> Unit,
    onApplyStyle: () -> Unit,
    onClearCache: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val subtitleHorizontalFocusRequester = remember { FocusRequester() }
    val subtitleVerticalFocusRequester = remember { FocusRequester() }
    val subtitlePositionResetFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onNavigateBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
            decorFitsSystemWindows = false,
        ),
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(page.title, fontWeight = FontWeight.Bold)
        },
        text = {
            val focusManager = LocalFocusManager.current
            val pageEntryFocusRequester = remember(page) { FocusRequester() }
            var pageEntryPending by remember(page) { mutableStateOf(true) }
            LaunchedEffect(page) {
                delay(80)
                runCatching { pageEntryFocusRequester.requestFocus() }
                delay(20)
                if (focusManager.moveFocus(FocusDirection.Next)) {
                    pageEntryPending = false
                }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (pageEntryPending) {
                    Spacer(
                        Modifier
                            .size(1.dp)
                            .focusRequester(pageEntryFocusRequester)
                            .focusable(),
                    )
                }
                when (page) {
                    VlcSettingsPage.MAIN -> {
                        Surface(
                            color = Color(0xFF171717),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("재생 파일 경로", color = Color(0xFFE5A00D), fontWeight = FontWeight.Bold)
                                Text(
                                    source.filePath ?: "파일 경로 정보 없음",
                                    color = Color.LightGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (player?.isPlaying == true) "범용 코덱 · 재생 중" else "범용 코덱 · VLC 로컬 재생",
                                    color = Color(0xFFE5A00D),
                                )
                            }
                        }
                        VlcSelectionRow(
                            label = "다음 영상 자동 재생",
                            selected = autoPlayNext,
                            onClick = { onAutoPlayNextChanged(!autoPlayNext) },
                        )
                        listOf(
                            VlcSettingsPage.SUBTITLE,
                            VlcSettingsPage.SPEED,
                            VlcSettingsPage.DISPLAY,
                            VlcSettingsPage.AUDIO,
                            VlcSettingsPage.QUALITY,
                            VlcSettingsPage.OPTIMIZATION,
                            VlcSettingsPage.STORAGE,
                        ).forEach { destination ->
                            VlcSelectionRow(
                                label = destination.title,
                                selected = false,
                                onClick = { onPageChanged(destination) },
                            )
                        }
                    }

                    VlcSettingsPage.SUBTITLE -> {
                        Text("자막 트랙", color = Color(0xFFE5A00D), fontWeight = FontWeight.Bold)
                        subtitleRendererStatus?.let { status ->
                            Surface(
                                color = if (subtitleRendererLimited) {
                                    Color(0xFF5A3100)
                                } else {
                                    Color(0xFF143D27)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = status,
                                    color = if (subtitleRendererLimited) {
                                        Color(0xFFFFB74D)
                                    } else {
                                        Color(0xFF8FE3AE)
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                )
                            }
                        }
                        VlcSelectionRow("자막 끔", subtitlesDisabled, onSubtitleOff)
                        VlcSelectionRow(
                            label = "기본 자막(자동) · 한국어 우선",
                            selected = automaticSubtitleSelection,
                            onClick = onSubtitleAuto,
                        )
                        if (
                            subtitleTracks.none { it.id >= 0 } &&
                            plexSubtitleChoices.none { it.isEmbedded }
                        ) {
                            Text("내장 자막을 불러오는 중이거나 이 영상에 내장 자막이 없습니다.", color = Color.LightGray)
                        }
                        subtitleTracks.filter { it.id >= 0 }.forEach { track ->
                            VlcSelectionRow(
                                label = "내장 · ${track.name}",
                                selected = selectedExternalSubtitleUrl == null &&
                                    !automaticSubtitleSelection &&
                                    selectedSubtitleTrack == track.id,
                                onClick = { onSubtitleTrack(track.id) },
                            )
                        }
                        plexSubtitleChoices.forEach { subtitle ->
                            VlcSelectionRow(
                                label = when {
                                    subtitle.isEmbedded && subtitle.isManualTextSubtitle() ->
                                        "내장 텍스트 · ${subtitle.label}"
                                    !subtitle.isEmbedded && subtitle.isManualTextSubtitle() ->
                                        "외부 텍스트 · ${subtitle.label}"
                                    subtitle.isEmbedded ->
                                        "내장 원본 · ${subtitle.label} · 사용자 글꼴 제한"
                                    else ->
                                        "외부 이미지/원본 · ${subtitle.label} · 사용자 글꼴 제한"
                                },
                                selected = selectedExternalSubtitleUrl == subtitle.url,
                                onClick = { onExternalSubtitle(subtitle) },
                            )
                        }
                        if (
                            styledSubtitleLoadFailed &&
                            selectedExternalSubtitleUrl != null &&
                            subtitleRendererLimited
                        ) {
                            Text(
                                "텍스트 추출과 보조 자막 읽기에 실패해 VLC 원본 표시로 전환했습니다.",
                                color = Color(0xFFFFB74D),
                            )
                        } else if (styledSubtitleLoading && subtitleRendererStatus == null) {
                            Text("선택한 텍스트 자막을 불러오는 중입니다…", color = Color(0xFFE5A00D))
                        }
                        VlcSelectionRow(
                            label = "자막 사용자 설정",
                            selected = false,
                            onClick = { onPageChanged(VlcSettingsPage.SUBTITLE_STYLE) },
                        )
                        Text(
                            "SRT·ASS·SSA·VTT·TTML·SMI 및 읽을 수 있는 내장 텍스트 자막은 기본 재생과 같은 " +
                                "글꼴·크기·색상·배경·외곽선·자유 위치·세로쓰기를 적용합니다. " +
                                "서버가 추출하지 못한 내장 ASS와 PGS/VobSub 이미지 자막은 원본 형식상 일부 모양을 " +
                                "바꿀 수 없습니다.",
                            color = Color.LightGray,
                        )
                    }

                    VlcSettingsPage.SUBTITLE_STYLE -> {
                        SettingsTitle("글꼴")
                        VlcSubtitleFont.entries.forEach { font ->
                            val label = if (font == VlcSubtitleFont.CUSTOM && !customFontName.isNullOrBlank()) {
                                "사용자 폰트 · $customFontName"
                            } else {
                                font.label
                            }
                            VlcSelectionRow(
                                label = label,
                                selected = draftStyle.font == font,
                                onClick = {
                                    if (font == VlcSubtitleFont.CUSTOM && customFontName.isNullOrBlank()) {
                                        onPickFont()
                                    } else {
                                        onDraftStyleChanged(draftStyle.copy(font = font))
                                    }
                                },
                            )
                        }
                        VlcActionButton(
                            label = "내 폰트 파일 선택",
                            onClick = onPickFont,
                        )

                        SettingsTitle("자막 크기")
                        VlcSubtitleSizes.chunked(3).forEach { sizes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                sizes.forEach { size ->
                                    VlcChoiceChip(
                                        label = "$size%",
                                        selected = draftStyle.sizePercent == size,
                                        onClick = { onDraftStyleChanged(draftStyle.copy(sizePercent = size)) },
                                    )
                                }
                            }
                        }

                        SettingsTitle("글자 색")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VlcSubtitleColors.forEach { (label, color) ->
                                VlcChoiceChip(
                                    label = label,
                                    selected = draftStyle.color == color,
                                    onClick = { onDraftStyleChanged(draftStyle.copy(color = color)) },
                                )
                            }
                        }

                        SettingsTitle("배경")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VlcSubtitleBackgrounds.forEach { (label, color) ->
                                VlcChoiceChip(
                                    label = label,
                                    selected = draftStyle.background == color,
                                    onClick = { onDraftStyleChanged(draftStyle.copy(background = color)) },
                                )
                            }
                        }
                        SettingsTitle("외곽선")
                        VlcSubtitleEdge.entries.forEach { edge ->
                            VlcSelectionRow(
                                label = edge.label,
                                selected = draftStyle.edge == edge,
                                onClick = { onDraftStyleChanged(draftStyle.copy(edge = edge)) },
                            )
                        }
                        SettingsTitle("외곽선 색")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VlcSubtitleEdgeColors.forEach { (label, color) ->
                                VlcChoiceChip(
                                    label = label,
                                    selected = draftStyle.edgeColor == color,
                                    onClick = { onDraftStyleChanged(draftStyle.copy(edgeColor = color)) },
                                )
                            }
                        }
                        SettingsTitle("자막 위치")
                        VlcValueSlider(
                            label = "가로",
                            value = draftStyle.horizontalOffsetPercent.toFloat(),
                            range = -100f..100f,
                            focusRequester = subtitleHorizontalFocusRequester,
                            down = subtitleVerticalFocusRequester,
                            onValueChange = {
                                onDraftStyleChanged(
                                    draftStyle.copy(horizontalOffsetPercent = it.roundToInt()),
                                )
                            },
                        )
                        VlcValueSlider(
                            label = "세로",
                            value = draftStyle.verticalOffsetPercent.toFloat(),
                            range = -100f..100f,
                            focusRequester = subtitleVerticalFocusRequester,
                            up = subtitleHorizontalFocusRequester,
                            down = subtitlePositionResetFocusRequester,
                            onValueChange = {
                                onDraftStyleChanged(
                                    draftStyle.copy(verticalOffsetPercent = it.roundToInt()),
                                )
                            },
                        )
                        VlcActionButton(
                            label = "자막 위치 가운데",
                            modifier = Modifier.focusRequester(subtitlePositionResetFocusRequester),
                            onClick = {
                                onDraftStyleChanged(
                                    draftStyle.copy(
                                        horizontalOffsetPercent = 0,
                                        verticalOffsetPercent = 0,
                                    ),
                                )
                            },
                        )
                        Text(
                            "리모컨 좌우: 위치 조절 · 위아래: 항목 이동",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                        )
                        SettingsTitle("쓰기 방향")
                        VlcSelectionRow(
                            label = "가로쓰기",
                            selected = !draftStyle.verticalWriting,
                            onClick = {
                                onDraftStyleChanged(draftStyle.copy(verticalWriting = false))
                            },
                        )
                        VlcSelectionRow(
                            label = "세로쓰기",
                            selected = draftStyle.verticalWriting,
                            onClick = {
                                onDraftStyleChanged(draftStyle.copy(verticalWriting = true))
                            },
                        )
                        VlcActionButton(
                            label = "완료",
                            onClick = onApplyStyle,
                            primary = true,
                        )
                    }

                    VlcSettingsPage.SPEED -> {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                            VlcSelectionRow(
                                label = "${speed}배",
                                selected = playbackSpeed == speed,
                                onClick = { onPlaybackSpeedChanged(speed) },
                            )
                        }
                    }

                    VlcSettingsPage.DISPLAY -> {
                        SettingsTitle("화면 모드")
                        VlcPictureMode.entries.forEach { mode ->
                            VlcSelectionRow(
                                label = "${mode.label} · ${mode.description}",
                                selected = videoSettings.pictureMode == mode,
                                onClick = {
                                    onVideoSettingsChanged(videoSettings.applyPictureMode(mode))
                                },
                            )
                        }
                        SettingsTitle("화면 크기")
                        VlcVideoScale.entries.forEach { scale ->
                            VlcSelectionRow(
                                label = scale.label,
                                selected = videoScale == scale,
                                onClick = { onVideoScaleChanged(scale) },
                            )
                        }
                        VlcSelectionRow(
                            label = "고급 화면 설정",
                            selected = false,
                            onClick = { onPageChanged(VlcSettingsPage.DISPLAY_ADVANCED) },
                        )
                        SettingsTitle("화면 설정 저장·불러오기")
                        (1..3).forEach { slot ->
                            val available = isVideoPresetAvailable(slot)
                            VlcSelectionRow(
                                label = "설정 $slot 불러오기 · ${if (available) "저장됨" else "비어 있음"}",
                                selected = selectedVideoPreset == slot,
                                onClick = { if (available) onLoadVideoPreset(slot) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..3).forEach { slot ->
                                VlcActionButton(
                                    label = "$slot 저장",
                                    onClick = { onSaveVideoPreset(slot) },
                                )
                            }
                        }
                        VlcActionButton(
                            label = "화면 설정 기본값",
                            onClick = onResetVideoSettings,
                        )
                        Text("변화를 보면서 조절할 수 있으며 다음 재생에도 저장됩니다.", color = Color.LightGray)
                    }

                    VlcSettingsPage.DISPLAY_ADVANCED -> {
                        VlcValueSlider(
                            label = "화면 밝기",
                            value = videoSettings.screenBrightness,
                            range = 1f..100f,
                            onValueChange = {
                                onVideoSettingsChanged(videoSettings.copy(screenBrightness = it))
                            },
                        )
                        VlcValueSlider(
                            label = "명암",
                            value = videoSettings.pictureContrast,
                            range = -50f..50f,
                            onValueChange = {
                                onVideoSettingsChanged(
                                    videoSettings.copy(
                                        pictureMode = VlcPictureMode.CUSTOM,
                                        pictureContrast = it,
                                    ),
                                )
                            },
                        )
                        VlcValueSlider(
                            label = "밝기",
                            value = videoSettings.pictureBrightness,
                            range = -50f..50f,
                            onValueChange = {
                                onVideoSettingsChanged(
                                    videoSettings.copy(
                                        pictureMode = VlcPictureMode.CUSTOM,
                                        pictureBrightness = it,
                                    ),
                                )
                            },
                        )
                        VlcValueSlider(
                            label = "블랙 레벨",
                            value = videoSettings.pictureBlackLevel,
                            range = -50f..50f,
                            onValueChange = {
                                onVideoSettingsChanged(
                                    videoSettings.copy(
                                        pictureMode = VlcPictureMode.CUSTOM,
                                        pictureBlackLevel = it,
                                    ),
                                )
                            },
                        )
                        VlcValueSlider(
                            label = "색 농도",
                            value = videoSettings.pictureColorDepth,
                            range = -50f..50f,
                            onValueChange = {
                                onVideoSettingsChanged(
                                    videoSettings.copy(
                                        pictureMode = VlcPictureMode.CUSTOM,
                                        pictureColorDepth = it,
                                    ),
                                )
                            },
                        )
                        VlcValueSlider(
                            label = "색 온도",
                            value = videoSettings.pictureColorTemperature,
                            range = -50f..50f,
                            onValueChange = {
                                onVideoSettingsChanged(
                                    videoSettings.copy(
                                        pictureMode = VlcPictureMode.CUSTOM,
                                        pictureColorTemperature = it,
                                    ),
                                )
                            },
                        )
                        VlcActionButton(
                            label = "고급 설정 초기화",
                            onClick = {
                                onVideoSettingsChanged(
                                    videoSettings.applyPictureMode(VlcPictureMode.STANDARD),
                                )
                            },
                        )
                    }

                    VlcSettingsPage.AUDIO -> {
                        VlcSelectionRow(
                            label = "자동 · 원본 기본 오디오",
                            selected = automaticAudioSelection,
                            onClick = onAudioAuto,
                        )
                        if (audioTracks.isEmpty()) {
                            Text("오디오 트랙을 불러오는 중입니다.", color = Color.LightGray)
                        }
                        audioTracks.forEach { track ->
                            VlcSelectionRow(
                                label = track.name,
                                selected = !automaticAudioSelection && selectedAudioTrack == track.id,
                                onClick = { onAudioTrack(track.id) },
                            )
                        }
                    }

                    VlcSettingsPage.QUALITY -> {
                        PlaybackQuality.entries.forEach { quality ->
                            VlcSelectionRow(
                                label = quality.maxBitrateKbps?.let { bitrate ->
                                    "${quality.label} · 최대 ${bitrate / 1_000} Mbps"
                                } ?: "${quality.label} · 원본 직접 재생",
                                selected = playbackQuality == quality,
                                onClick = { onPlaybackQualityChanged(quality) },
                            )
                        }
                        Text("화질을 바꾸면 현재 위치부터 새 스트림으로 이어 재생합니다.", color = Color.LightGray)
                    }

                    VlcSettingsPage.OPTIMIZATION -> {
                        VlcOptimizationMode.entries.forEach { mode ->
                            VlcSelectionRow(
                                label = "${mode.label} · ${mode.description}",
                                selected = optimizationMode == mode,
                                onClick = { onOptimizationModeChanged(mode) },
                            )
                        }
                        Text("선택하면 현재 위치를 유지하고 범용 엔진에 즉시 적용됩니다.", color = Color.LightGray)
                    }

                    VlcSettingsPage.STORAGE -> {
                        Text(
                            "재생 파일 경로: ${source.filePath ?: "파일 경로 정보 없음"}",
                            color = Color.LightGray,
                        )
                        Text("임시 재생 데이터와 네트워크 캐시를 삭제합니다.", color = Color.LightGray)
                        VlcActionButton(
                            label = "캐시 삭제",
                            onClick = onClearCache,
                            primary = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            VlcActionButton(
                label = if (page == VlcSettingsPage.MAIN) "닫기" else "뒤로",
                onClick = onNavigateBack,
            )
        },
    )
}

@Composable
private fun VlcPlayerControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Int = 34,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (focused) Color(0xFFFFD400) else Color.Transparent,
            contentColor = if (focused) Color.Black else Color.White,
            disabledContentColor = Color.White.copy(alpha = .35f),
        ),
    ) {
        Icon(
            icon,
            contentDescription,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

@Composable
private fun VlcValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var sliderModifier = modifier.fillMaxWidth()
    if (focusRequester != null) {
        sliderModifier = sliderModifier.focusRequester(focusRequester)
    }
    Surface(
        color = if (focused) Color(0xFF2D2500) else Color(0xFF111111),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) Color(0xFFFFF176) else Color(0xFF454545),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                "$label ${value.roundToInt()}",
                color = if (focused) Color(0xFFFFF176) else Color.White,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                steps = ((range.endInclusive - range.start).roundToInt() - 1).coerceAtLeast(0),
                modifier = sliderModifier
                    .focusProperties {
                        if (up != null) this.up = up
                        if (down != null) this.down = down
                    }
                    .onPreviewKeyEvent { event ->
                        val nativeEvent = event.nativeKeyEvent
                        val direction = when (nativeEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                            else -> return@onPreviewKeyEvent false
                        }
                        if (
                            nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                            nativeEvent.repeatCount == 0
                        ) {
                            val target = if (direction == FocusDirection.Up) up else down
                            if (target != null) {
                                runCatching { target.requestFocus() }
                            } else {
                                focusManager.moveFocus(direction)
                            }
                        }
                        true
                    }
                    .onFocusChanged { focused = it.isFocused || it.hasFocus },
                colors = SliderDefaults.colors(
                    thumbColor = if (focused) Color(0xFFFFD400) else Color.White.copy(alpha = .82f),
                    activeTrackColor = if (focused) Color(0xFFFFD400) else Color.White.copy(alpha = .52f),
                    inactiveTrackColor = Color.White.copy(alpha = .34f),
                ),
            )
        }
    }
}

@Composable
private fun VlcActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) Color.White else Color(0xFF5A5A5A),
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                focused -> Color(0xFFFFD400)
                primary -> Color(0xFFE5A00D)
                else -> Color.DarkGray
            },
            contentColor = if (focused || primary) Color.Black else Color.White,
        ),
    ) {
        Text(
            label,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun VlcChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        modifier = Modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xFF171717),
            labelColor = Color.White,
            selectedContainerColor = Color(0xFF55420B),
            selectedLabelColor = Color.White,
        ),
        border = BorderStroke(
            width = if (focused) 4.dp else if (selected) 2.dp else 1.dp,
            color = when {
                focused -> Color(0xFFFFD400)
                selected -> Color(0xFFE5A00D)
                else -> Color(0xFF454545)
            },
        ),
    )
}

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text,
        color = Color(0xFFE5A00D),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun VlcSelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = when {
            focused -> Color(0xFFE5A00D)
            selected -> Color(0xFF55420B)
            else -> Color(0xFF171717)
        },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = when {
                focused -> 4.dp
                selected -> 2.dp
                else -> 1.dp
            },
            color = when {
                focused -> Color.White
                selected -> Color(0xFFE5A00D)
                else -> Color(0xFF454545)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick),
    ) {
        Text(
            text = if (selected) "✓  $label" else "    $label",
            color = if (focused) Color.Black else Color.White,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

private fun android.content.SharedPreferences.loadVlcSubtitleStyle(): VlcSubtitleStyle =
    VlcSubtitleStyle(
        font = VlcSubtitleFont.fromStorage(getString("subtitle_font", null)),
        sizePercent = getInt("subtitle_size", 100),
        color = getInt("subtitle_color", AndroidColor.WHITE),
        background = getInt("subtitle_background", AndroidColor.TRANSPARENT),
        edgeColor = getInt("subtitle_edge_color", AndroidColor.BLACK),
        edge = VlcSubtitleEdge.fromStoredValue(
            getInt("subtitle_edge_type", CaptionStyleCompat.EDGE_TYPE_OUTLINE),
        ),
        horizontalOffsetPercent = getInt("subtitle_horizontal_offset", 0).coerceIn(-100, 100),
        verticalOffsetPercent = getInt("subtitle_vertical_offset", 0),
        verticalWriting = getBoolean("subtitle_vertical_writing", false),
    )

private fun android.content.SharedPreferences.saveVlcSubtitleStyle(style: VlcSubtitleStyle) {
    edit()
        .putString("subtitle_font", style.font.storage)
        .putInt("subtitle_size", style.sizePercent)
        .putInt("subtitle_color", style.color)
        .putInt("subtitle_background", style.background)
        .putInt("subtitle_edge_color", style.edgeColor)
        .putInt("subtitle_edge_type", style.edge.storedValue)
        .putInt("subtitle_horizontal_offset", style.horizontalOffsetPercent)
        .putInt("subtitle_vertical_offset", style.verticalOffsetPercent)
        .putBoolean("subtitle_vertical_writing", style.verticalWriting)
        .apply()
}

private fun android.content.SharedPreferences.loadVlcVideoSettings(): VlcVideoSettings =
    VlcVideoSettings(
        screenBrightness = getFloat("video_brightness_percent", 100f).coerceIn(1f, 100f),
        pictureMode = VlcPictureMode.fromStorage(getString("video_picture_mode", null)),
        pictureBrightness = getFloat("video_picture_brightness", 0f).coerceIn(-50f, 50f),
        pictureContrast = getFloat("video_picture_contrast", 0f).coerceIn(-50f, 50f),
        pictureBlackLevel = getFloat("video_picture_black_level", 0f).coerceIn(-50f, 50f),
        pictureColorDepth = getFloat("video_picture_color_depth", 0f).coerceIn(-50f, 50f),
        pictureColorTemperature = getFloat("video_picture_color_temperature", 0f)
            .coerceIn(-50f, 50f),
    )

private fun android.content.SharedPreferences.saveVlcVideoSettings(value: VlcVideoSettings) {
    edit()
        .putBoolean("video_picture_controls_v1", true)
        .putFloat("video_brightness_percent", value.screenBrightness)
        .putString("video_picture_mode", value.pictureMode.storage)
        .putFloat("video_picture_brightness", value.pictureBrightness)
        .putFloat("video_picture_contrast", value.pictureContrast)
        .putFloat("video_picture_black_level", value.pictureBlackLevel)
        .putFloat("video_picture_color_depth", value.pictureColorDepth)
        .putFloat("video_picture_color_temperature", value.pictureColorTemperature)
        .apply()
}

private fun android.content.SharedPreferences.isVlcVideoPresetAvailable(slot: Int): Boolean =
    getBoolean("video_screen_preset_${slot}_saved", false)

private fun android.content.SharedPreferences.saveVlcVideoPreset(
    slot: Int,
    settings: VlcVideoSettings,
    scale: VlcVideoScale,
): Boolean {
    val prefix = "video_screen_preset_${slot}_"
    return edit()
        .putBoolean("${prefix}saved", true)
        .putFloat("${prefix}brightness", settings.screenBrightness)
        .putString("${prefix}scale_mode", scale.storage)
        .putString("${prefix}picture_mode", settings.pictureMode.storage)
        .putFloat("${prefix}picture_brightness", settings.pictureBrightness)
        .putFloat("${prefix}picture_contrast", settings.pictureContrast)
        .putFloat("${prefix}picture_black_level", settings.pictureBlackLevel)
        .putFloat("${prefix}picture_color_depth", settings.pictureColorDepth)
        .putFloat("${prefix}picture_color_temperature", settings.pictureColorTemperature)
        .commit()
}

private fun android.content.SharedPreferences.loadVlcVideoPreset(
    slot: Int,
): Pair<VlcVideoSettings, VlcVideoScale>? {
    val prefix = "video_screen_preset_${slot}_"
    if (!getBoolean("${prefix}saved", false)) return null
    return VlcVideoSettings(
        screenBrightness = getFloat("${prefix}brightness", 100f).coerceIn(1f, 100f),
        pictureMode = VlcPictureMode.fromStorage(getString("${prefix}picture_mode", null)),
        pictureBrightness = getFloat("${prefix}picture_brightness", 0f).coerceIn(-50f, 50f),
        pictureContrast = getFloat("${prefix}picture_contrast", 0f).coerceIn(-50f, 50f),
        pictureBlackLevel = getFloat("${prefix}picture_black_level", 0f).coerceIn(-50f, 50f),
        pictureColorDepth = getFloat("${prefix}picture_color_depth", 0f).coerceIn(-50f, 50f),
        pictureColorTemperature = getFloat("${prefix}picture_color_temperature", 0f)
            .coerceIn(-50f, 50f),
    ) to VlcVideoScale.fromStorage(getString("${prefix}scale_mode", null))
}

private fun VlcSubtitleStyle.toSharedSubtitleAppearance(): SubtitleAppearance =
    SubtitleAppearance(
        sizePercent = sizePercent,
        horizontalOffsetPercent = horizontalOffsetPercent,
        verticalOffsetPercent = verticalOffsetPercent,
        verticalWriting = verticalWriting,
        foregroundColor = color,
        backgroundColor = background,
        edgeType = when (edge) {
            VlcSubtitleEdge.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
            VlcSubtitleEdge.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            VlcSubtitleEdge.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        },
        edgeColor = edgeColor,
    )

private fun resolveVlcSubtitleTypeface(
    context: Context,
    font: VlcSubtitleFont,
    customFontFile: File,
): Typeface = when (font) {
    VlcSubtitleFont.SYSTEM -> Typeface.DEFAULT
    VlcSubtitleFont.ASIA_B ->
        ResourcesCompat.getFont(context, R.font.asia_cinema_b) ?: Typeface.DEFAULT
    VlcSubtitleFont.ASIA_M ->
        ResourcesCompat.getFont(context, R.font.asia_cinema_m) ?: Typeface.DEFAULT
    VlcSubtitleFont.ASIA_L ->
        ResourcesCompat.getFont(context, R.font.asia_cinema_l) ?: Typeface.DEFAULT
    VlcSubtitleFont.CUSTOM -> runCatching {
        require(customFontFile.isFile && customFontFile.length() > 0L)
        Typeface.createFromFile(customFontFile)
    }.getOrElse {
        ResourcesCompat.getFont(context, R.font.cinema) ?: Typeface.SERIF
    }
    VlcSubtitleFont.GOWUN ->
        ResourcesCompat.getFont(context, R.font.cinema) ?: Typeface.SERIF
    VlcSubtitleFont.GOTHIC -> Typeface.create("sans-serif", Typeface.NORMAL)
    VlcSubtitleFont.SERIF -> Typeface.create("serif", Typeface.NORMAL)
    VlcSubtitleFont.ROUNDED -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
}

private fun buildVlcArguments(
    context: Context,
    style: VlcSubtitleStyle,
    customFontFile: File,
    optimizationMode: VlcOptimizationMode,
): ArrayList<String> = arrayListOf<String>().apply {
    add("--audio-time-stretch")
    add("--network-caching=${optimizationMode.cachingMs()}")
    add("--file-caching=${optimizationMode.cachingMs()}")
    if (optimizationMode == VlcOptimizationMode.STABILITY) {
        add("--drop-late-frames")
        add("--skip-frames")
    }
    // VLC's text scale also affects ASS/SSA, while the freetype size only
    // affects plain text subtitles. Keep one neutral freetype baseline and
    // apply the user's percentage once through sub-text-scale.
    add("--freetype-rel-fontsize=16")
    add("--sub-text-scale=${style.sizePercent.coerceIn(50, 200)}")
    add("--freetype-color=${style.color and 0x00FFFFFF}")
    add("--freetype-opacity=${AndroidColor.alpha(style.color)}")
    add("--freetype-background-color=${style.background and 0x00FFFFFF}")
    add("--freetype-background-opacity=${AndroidColor.alpha(style.background)}")
    add("--freetype-outline-color=${style.edgeColor and 0x00FFFFFF}")
    add("--freetype-outline-thickness=4")
    add(
        "--freetype-outline-opacity=" +
            if (style.edge == VlcSubtitleEdge.OUTLINE) AndroidColor.alpha(style.edgeColor) else 0,
    )
    add("--freetype-shadow-color=${style.edgeColor and 0x00FFFFFF}")
    add(
        "--freetype-shadow-opacity=" +
            if (style.edge == VlcSubtitleEdge.SHADOW) AndroidColor.alpha(style.edgeColor) else 0,
    )
    add("--sub-margin=${(80 - style.verticalOffsetPercent * 2).coerceIn(0, 200)}")
    // freetype-font expects a font family, not an absolute file path. Keep
    // copied app fonts discoverable for libass and pass their real family.
    resolveVlcFontFile(context, style.font, customFontFile)?.let {
        add("--ssa-fontsdir=${it.parentFile?.absolutePath ?: context.filesDir.absolutePath}")
    }
    add("--freetype-font=${style.font.vlcFontFamily()}")
}

private fun VlcSubtitleFont.vlcFontFamily(): String = when (this) {
    VlcSubtitleFont.ASIA_B -> "KoreanCNMB"
    VlcSubtitleFont.ASIA_M -> "KoreanCNMM"
    VlcSubtitleFont.ASIA_L -> "KoreanCNML"
    VlcSubtitleFont.GOWUN -> "Gowun Batang"
    VlcSubtitleFont.SERIF -> "serif"
    VlcSubtitleFont.ROUNDED -> "sans-serif-rounded"
    VlcSubtitleFont.SYSTEM,
    VlcSubtitleFont.CUSTOM,
    VlcSubtitleFont.GOTHIC,
    -> "sans-serif"
}

private fun VlcOptimizationMode.cachingMs(): Int = when (this) {
    VlcOptimizationMode.STABILITY -> 2_500
    VlcOptimizationMode.PERFORMANCE -> 800
    VlcOptimizationMode.AUTO,
    VlcOptimizationMode.BALANCED,
    -> 1_500
}

private fun applyVlcWindowBrightness(activity: Activity?, brightnessPercent: Float) {
    val brightness = (brightnessPercent / 100f).coerceIn(.01f, 1f)
    activity?.window?.let { window ->
        window.attributes = window.attributes.apply {
            screenBrightness = brightness
        }
    }
}

private fun applyVlcVideoScale(layout: VLCVideoLayout, mode: VlcVideoScale) {
    layout.scaleX = 1f
    layout.scaleY = 1f
    val videoView = layout.findFirstTextureView()
    if (videoView != null) {
        videoView.scaleX = mode.scaleX
        videoView.scaleY = mode.scaleY
        videoView.requestLayout()
        videoView.invalidate()
    } else {
        layout.scaleX = mode.scaleX
        layout.scaleY = mode.scaleY
    }
}

private fun applyVlcVideoAppearance(layout: VLCVideoLayout, settings: VlcVideoSettings) {
    layout.setLayerType(View.LAYER_TYPE_NONE, null)
    val target = layout.findFirstTextureView() ?: return
    val paint = buildVlcVideoLayerPaint(settings)
    if (paint == null) {
        target.setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        target.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
    target.invalidate()
}

private fun View.findFirstTextureView(): TextureView? {
    if (this is TextureView) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findFirstTextureView()?.let { return it }
    }
    return null
}

private fun buildVlcVideoLayerPaint(settings: VlcVideoSettings): Paint? {
    val pictureBrightness = settings.pictureBrightness.coerceIn(-50f, 50f)
    val pictureContrast = settings.pictureContrast.coerceIn(-50f, 50f)
    val pictureBlackLevel = settings.pictureBlackLevel.coerceIn(-50f, 50f)
    val pictureColorDepth = settings.pictureColorDepth.coerceIn(-50f, 50f)
    val pictureColorTemperature = settings.pictureColorTemperature.coerceIn(-50f, 50f)
    if (
        pictureBrightness == 0f &&
        pictureContrast == 0f &&
        pictureBlackLevel == 0f &&
        pictureColorDepth == 0f &&
        pictureColorTemperature == 0f
    ) {
        return null
    }
    val brightnessScale = 1f + pictureBrightness / 100f
    val contrastScale = 1f + pictureContrast / 100f
    val saturationScale = 1f + pictureColorDepth / 100f
    val temperature = pictureColorTemperature / 50f
    val redScale = (1f - temperature * .12f).coerceIn(.72f, 1.28f)
    val greenScale = (1f - abs(temperature) * .025f).coerceIn(.9f, 1.1f)
    val blueScale = (1f + temperature * .16f).coerceIn(.7f, 1.34f)
    val translate = pictureBlackLevel * .4f
    val colorMatrix = ColorMatrix().apply { setSaturation(saturationScale) }
    colorMatrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                contrastScale * brightnessScale * redScale, 0f, 0f, 0f, translate,
                0f, contrastScale * brightnessScale * greenScale, 0f, 0f, translate,
                0f, 0f, contrastScale * brightnessScale * blueScale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    return Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
}

private fun resolveVlcFontFile(
    context: Context,
    font: VlcSubtitleFont,
    customFontFile: File,
): File? {
    if (font == VlcSubtitleFont.SYSTEM) return null
    if (font == VlcSubtitleFont.CUSTOM) return customFontFile.takeIf(File::isFile)
    if (font == VlcSubtitleFont.GOTHIC || font == VlcSubtitleFont.ROUNDED) {
        return listOf(
            File("/system/fonts/NotoSansCJK-Regular.ttc"),
            File("/system/fonts/NotoSansKR-Regular.otf"),
            File("/system/fonts/Roboto-Regular.ttf"),
        ).firstOrNull(File::isFile)
    }
    if (font == VlcSubtitleFont.SERIF) {
        return listOf(
            File("/system/fonts/NotoSerifCJK-Regular.ttc"),
            File("/system/fonts/NotoSerifKR-Regular.otf"),
            File("/system/fonts/NotoSerif-Regular.ttf"),
        ).firstOrNull(File::isFile)
    }
    val resource = when (font) {
        VlcSubtitleFont.ASIA_B -> R.font.asia_cinema_b
        VlcSubtitleFont.ASIA_M -> R.font.asia_cinema_m
        VlcSubtitleFont.ASIA_L -> R.font.asia_cinema_l
        VlcSubtitleFont.GOWUN -> R.font.cinema
        else -> return null
    }
    val output = File(context.filesDir, "vlc-font-${font.storage}.ttf")
    if (!output.isFile || output.length() == 0L) {
        context.resources.openRawResource(resource).use { input ->
            output.outputStream().use(input::copyTo)
        }
    }
    return output
}

private fun PlaybackSubtitle.isPreferredKoreanTextSubtitle(): Boolean =
    isManualTextSubtitle() &&
        (language.orEmpty().isKoreanSubtitleName() || label.isKoreanSubtitleName())

private fun String.isKoreanSubtitleName(): Boolean =
    contains("한국", ignoreCase = true) ||
        contains("korean", ignoreCase = true) ||
        contains("kor", ignoreCase = true)

private fun List<VlcTrack>.bestNativeSubtitleMatch(
    subtitle: PlaybackSubtitle?,
): VlcTrack? {
    subtitle ?: return null
    val requestedName = subtitle.label.normalizedSubtitleTrackName()
    if (requestedName.isNotBlank()) {
        firstOrNull { it.name.normalizedSubtitleTrackName() == requestedName }?.let { return it }
        firstOrNull { track ->
            val candidate = track.name.normalizedSubtitleTrackName()
            candidate.isNotBlank() &&
                (candidate.contains(requestedName) || requestedName.contains(candidate))
        }?.let { return it }
    }
    return if (
        subtitle.language.orEmpty().isKoreanSubtitleName() ||
        subtitle.label.isKoreanSubtitleName()
    ) {
        firstOrNull { it.name.isKoreanSubtitleName() }
    } else {
        null
    }
}

private fun List<PlaybackSubtitle>.bestPlexTextSubtitleMatch(
    nativeTrackName: String?,
): PlaybackSubtitle? {
    val textChoices = filter { it.isManualTextSubtitle() }
    if (textChoices.isEmpty()) return null
    val normalizedNativeName = nativeTrackName.orEmpty().normalizedSubtitleTrackName()
    if (normalizedNativeName.isNotBlank()) {
        textChoices.firstOrNull {
            it.label.normalizedSubtitleTrackName() == normalizedNativeName
        }?.let { return it }
        textChoices.firstOrNull { subtitle ->
            val candidate = subtitle.label.normalizedSubtitleTrackName()
            candidate.isNotBlank() &&
                (candidate.contains(normalizedNativeName) || normalizedNativeName.contains(candidate))
        }?.let { return it }
    }
    val koreanChoices = textChoices.filter {
        it.language.orEmpty().isKoreanSubtitleName() || it.label.isKoreanSubtitleName()
    }
    return koreanChoices.singleOrNull()
        ?.takeIf { nativeTrackName.orEmpty().isKoreanSubtitleName() }
}

private fun String.normalizedSubtitleTrackName(): String =
    lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun String.withPlexToken(token: String): String {
    if (token.isBlank() || contains("X-Plex-Token=")) return this
    return Uri.parse(this).buildUpon()
        .appendQueryParameter("X-Plex-Token", token)
        .build()
        .toString()
}

private fun formatVlcTime(valueMs: Long): String {
    val totalSeconds = valueMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
