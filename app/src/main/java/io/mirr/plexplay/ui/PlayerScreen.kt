package io.mirr.plexplay.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import io.mirr.plexplay.BuildConfig
import io.mirr.plexplay.R
import io.mirr.plexplay.data.PlaybackSource
import io.mirr.plexplay.data.PlaybackQuality
import io.mirr.plexplay.data.PlaybackSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class AudioTrackOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

private data class SubtitleTrackOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
    val isExternal: Boolean,
    val isBitmap: Boolean,
    val externalSubtitleIndex: Int?,
)

private const val ExternalSubtitleLabelPrefix = "외부 · "
private const val ExternalSubtitleIndexedLabelPrefix = "외부 · #"
private val ExternalSubtitleIndexRegex = Regex("^외부 · #(\\d+) · ")
internal data class ManualSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

private enum class PlayerGestureMode {
    VOLUME,
    BRIGHTNESS,
    SEEK,
    SCALE,
    SUBTITLE_POSITION,
}

private enum class PlayerSettingsPage(
    val title: String,
) {
    MAIN("재생 설정"),
    SUBTITLE("자막 설정"),
    SPEED("재생 속도"),
    DISPLAY("화면 설정"),
    DISPLAY_ADVANCED("고급 화면 설정"),
    AUDIO("오디오"),
    QUALITY("재생 품질 변환"),
    OPTIMIZATION("기기 성능 최적화"),
    STORAGE("저장공간"),
}

private enum class DevicePerformanceTier(
    val label: String,
) {
    STABILITY("안정성"),
    BALANCED("균형"),
    PERFORMANCE("고성능"),
}

private enum class DeviceFormFactor(
    val label: String,
) {
    PHONE("휴대폰"),
    TABLET("태블릿·폴더블"),
    TELEVISION("Android TV·OTT"),
}

private enum class DeviceOptimizationMode(
    val storageValue: String,
    val label: String,
    val description: String,
) {
    AUTO(
        "auto",
        "자동 최적화 · 권장",
        "화면 크기, 기기 종류, 메모리와 CPU를 감지해 자동으로 조절합니다.",
    ),
    STABILITY(
        "stability",
        "재생 안정성 우선",
        "메모리 사용량을 줄이고 적응형 영상은 최대 Full HD로 선택합니다.",
    ),
    BALANCED(
        "balanced",
        "균형",
        "화질과 재생 안정성을 균형 있게 조절합니다.",
    ),
    PERFORMANCE(
        "performance",
        "고성능·고해상도 기기",
        "메모리와 실제 화면 해상도 안에서 최대 4K 영상을 사용합니다.",
    ),
    ;

    companion object {
        fun fromStorage(value: String?): DeviceOptimizationMode =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
}

private data class DeviceCapabilities(
    val tier: DevicePerformanceTier,
    val formFactor: DeviceFormFactor,
    val totalMemoryGb: Float,
    val availableMemoryGb: Float,
    val appMemoryLimitMb: Int,
    val cpuCores: Int,
    val lowRamDevice: Boolean,
    val screenWidthPixels: Int,
    val screenHeightPixels: Int,
)

private data class PlaybackOptimizationProfile(
    val tier: DevicePerformanceTier,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferAfterRebufferMs: Int,
    val backBufferMs: Int,
    val targetBufferBytes: Int,
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
)

private fun detectDeviceCapabilities(context: Context): DeviceCapabilities {
    val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val totalMemoryGb = memoryInfo.totalMem / 1_073_741_824f
    val availableMemoryGb = memoryInfo.availMem / 1_073_741_824f
    val appMemoryLimitMb = activityManager.memoryClass
    val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val displayMetrics = context.resources.displayMetrics
    val screenWidthPixels = maxOf(
        displayMetrics.widthPixels,
        displayMetrics.heightPixels,
    ).coerceAtLeast(1)
    val screenHeightPixels = minOf(
        displayMetrics.widthPixels,
        displayMetrics.heightPixels,
    ).coerceAtLeast(1)
    val formFactor = when {
        context.isTelevisionDevice() -> DeviceFormFactor.TELEVISION
        context.resources.configuration.smallestScreenWidthDp >= 600 ->
            DeviceFormFactor.TABLET
        else -> DeviceFormFactor.PHONE
    }
    val lowRamDevice = activityManager.isLowRamDevice || memoryInfo.lowMemory
    val tier = when {
        lowRamDevice ||
            totalMemoryGb < 2f ||
            availableMemoryGb < 0.5f ||
            appMemoryLimitMb < 192 ||
            cpuCores <= 2 ->
            DevicePerformanceTier.STABILITY
        totalMemoryGb >= 4f && appMemoryLimitMb >= 256 && cpuCores >= 4 ->
            DevicePerformanceTier.PERFORMANCE
        else -> DevicePerformanceTier.BALANCED
    }
    return DeviceCapabilities(
        tier = tier,
        formFactor = formFactor,
        totalMemoryGb = totalMemoryGb,
        availableMemoryGb = availableMemoryGb,
        appMemoryLimitMb = appMemoryLimitMb,
        cpuCores = cpuCores,
        lowRamDevice = lowRamDevice,
        screenWidthPixels = screenWidthPixels,
        screenHeightPixels = screenHeightPixels,
    )
}

private fun DeviceOptimizationMode.resolveProfile(
    capabilities: DeviceCapabilities,
): PlaybackOptimizationProfile {
    val resolvedTier = when (this) {
        DeviceOptimizationMode.AUTO -> capabilities.tier
        DeviceOptimizationMode.STABILITY -> DevicePerformanceTier.STABILITY
        DeviceOptimizationMode.BALANCED -> DevicePerformanceTier.BALANCED
        DeviceOptimizationMode.PERFORMANCE -> DevicePerformanceTier.PERFORMANCE
    }
    val resolutionLimit = when (resolvedTier) {
        DevicePerformanceTier.STABILITY -> 1_920 to 1_080
        DevicePerformanceTier.BALANCED -> 2_560 to 1_440
        DevicePerformanceTier.PERFORMANCE -> 3_840 to 2_160
    }
    val maxVideoWidth = minOf(
        resolutionLimit.first,
        capabilities.screenWidthPixels,
    ).coerceAtLeast(640)
    val maxVideoHeight = minOf(
        resolutionLimit.second,
        capabilities.screenHeightPixels,
    ).coerceAtLeast(360)
    return when (resolvedTier) {
        DevicePerformanceTier.STABILITY -> PlaybackOptimizationProfile(
            tier = resolvedTier,
            minBufferMs = 6_000,
            maxBufferMs = 20_000,
            bufferForPlaybackMs = 1_500,
            bufferAfterRebufferMs = 2_500,
            backBufferMs = 0,
            targetBufferBytes = 16 * 1024 * 1024,
            maxVideoWidth = maxVideoWidth,
            maxVideoHeight = maxVideoHeight,
        )
        DevicePerformanceTier.BALANCED -> PlaybackOptimizationProfile(
            tier = resolvedTier,
            minBufferMs = 12_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 1_500,
            bufferAfterRebufferMs = 3_000,
            backBufferMs = 0,
            targetBufferBytes = 32 * 1024 * 1024,
            maxVideoWidth = maxVideoWidth,
            maxVideoHeight = maxVideoHeight,
        )
        DevicePerformanceTier.PERFORMANCE -> PlaybackOptimizationProfile(
            tier = resolvedTier,
            minBufferMs = 18_000,
            maxBufferMs = 40_000,
            bufferForPlaybackMs = 2_000,
            bufferAfterRebufferMs = 4_000,
            backBufferMs = 0,
            targetBufferBytes = 64 * 1024 * 1024,
            maxVideoWidth = maxVideoWidth,
            maxVideoHeight = maxVideoHeight,
        )
    }
}

private enum class VideoScaleMode(
    val storageValue: String,
    val label: String,
    val resizeMode: Int,
    val scaleX: Float,
    val scaleY: Float,
) {
    FIT(
        "fit",
        "화면 맞춤 · 100%",
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        1f,
        1f,
    ),
    ZOOM(
        "zoom",
        "화면 확대 · 115%",
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        1.15f,
        1.15f,
    ),
    ZOOM_LARGE(
        "zoom_large",
        "화면 확대 · 130%",
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        1.3f,
        1.3f,
    ),
    STRETCH(
        "stretch",
        "가로 늘이기 · 112%",
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        1.12f,
        1f,
    ),
    ;

    companion object {
        fun fromStorage(value: String?): VideoScaleMode =
            entries.firstOrNull { it.storageValue == value } ?: FIT
    }
}

private enum class PictureMode(
    val storageValue: String,
    val label: String,
    val description: String,
    val brightness: Float,
    val contrast: Float,
    val blackLevel: Float,
    val colorDepth: Float,
    val colorTemperature: Float,
) {
    STANDARD(
        "standard",
        "표준 화면",
        "원본에 가까운 균형 잡힌 화면",
        0f,
        0f,
        0f,
        0f,
        0f,
    ),
    VIVID(
        "vivid",
        "선명한 화면",
        "밝고 색감이 풍부한 화면",
        10f,
        12f,
        0f,
        12f,
        3f,
    ),
    CINEMA(
        "cinema",
        "영화 화면",
        "어두운 공간에서 편안한 색감",
        3f,
        -4f,
        0f,
        -3f,
        -8f,
    ),
    BRIGHT_ROOM(
        "bright_room",
        "밝은 공간",
        "낮이나 조명이 밝은 공간용",
        14f,
        6f,
        2f,
        5f,
        0f,
    ),
    EYE_COMFORT(
        "eye_comfort",
        "눈이 편안한 화면",
        "밝기와 푸른빛을 낮춘 화면",
        -8f,
        -6f,
        2f,
        -8f,
        -18f,
    ),
    CUSTOM(
        "custom",
        "사용자 설정",
        "고급 화면 설정에서 직접 조정",
        0f,
        0f,
        0f,
        0f,
        0f,
    ),
    ;

    companion object {
        fun fromStorage(value: String?): PictureMode =
            entries.firstOrNull { it.storageValue == value } ?: CUSTOM
    }
}

private enum class SubtitleFontOption(
    val storageValue: String,
    val label: String,
) {
    SYSTEM("system", "시스템 기본"),
    ASIA_CINEMA_B("asia_cinema_b", "a시네마B"),
    ASIA_CINEMA_M("asia_cinema_m", "a시네마M"),
    ASIA_CINEMA_L("asia_cinema_l", "a시네마L"),
    CUSTOM("custom", "사용자 폰트"),
    GOWUN("gowun", "고운바탕"),
    GOTHIC("gothic", "고딕체"),
    SERIF("serif", "명조체"),
    ROUNDED("rounded", "둥근 고딕"),
    ;

    companion object {
        fun fromStorage(value: String?): SubtitleFontOption =
            when (value) {
                "cinema" -> ASIA_CINEMA_B
                else -> entries.firstOrNull { it.storageValue == value } ?: SYSTEM
            }
    }
}

internal data class SubtitleAppearance(
    val sizePercent: Int = 100,
    val horizontalOffsetPercent: Int = 0,
    val verticalOffsetPercent: Int = 0,
    val verticalWriting: Boolean = false,
    val foregroundColor: Int = android.graphics.Color.WHITE,
    val backgroundColor: Int = android.graphics.Color.TRANSPARENT,
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    val edgeColor: Int = android.graphics.Color.BLACK,
)

internal data class VerticalSubtitleGlyph(
    val text: String,
    val rotate: Boolean = false,
    val spacer: Boolean = false,
    val advanceScale: Float = 1f,
    val centerInCell: Boolean = false,
    val measureRotatedTextAdvance: Boolean = false,
)

internal const val VerticalSubtitleRightRotationDegrees = 90f

private data class VideoScreenSettings(
    val brightness: Float = 100f,
    val pictureMode: PictureMode = PictureMode.STANDARD,
    val pictureBrightness: Float = 0f,
    val pictureContrast: Float = 0f,
    val pictureBlackLevel: Float = 0f,
    val pictureColorDepth: Float = 0f,
    val pictureColorTemperature: Float = 0f,
)

private fun VideoScreenSettings.applyPictureMode(
    mode: PictureMode,
): VideoScreenSettings =
    copy(
        pictureMode = mode,
        pictureBrightness = mode.brightness,
        pictureContrast = mode.contrast,
        pictureBlackLevel = mode.blackLevel,
        pictureColorDepth = mode.colorDepth,
        pictureColorTemperature = mode.colorTemperature,
    )

private data class SubtitleIntOption(
    val label: String,
    val value: Int,
)

private val SUBTITLE_SIZE_OPTIONS = listOf(50, 75, 100, 125, 150, 200)
private val SUBTITLE_COLOR_OPTIONS = listOf(
    SubtitleIntOption("흰색", android.graphics.Color.WHITE),
    SubtitleIntOption("노란색", android.graphics.Color.YELLOW),
    SubtitleIntOption("하늘색", android.graphics.Color.CYAN),
    SubtitleIntOption("연두색", android.graphics.Color.GREEN),
)
private val SUBTITLE_EDGE_OPTIONS = listOf(
    SubtitleIntOption("없음", CaptionStyleCompat.EDGE_TYPE_NONE),
    SubtitleIntOption("외곽선", CaptionStyleCompat.EDGE_TYPE_OUTLINE),
    SubtitleIntOption("그림자", CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW),
)
private val SUBTITLE_EDGE_COLOR_OPTIONS = listOf(
    SubtitleIntOption("검정", android.graphics.Color.BLACK),
    SubtitleIntOption("흰색", android.graphics.Color.WHITE),
    SubtitleIntOption("파랑", android.graphics.Color.BLUE),
)
private val SUBTITLE_BACKGROUND_OPTIONS = listOf(
    SubtitleIntOption("투명", android.graphics.Color.TRANSPARENT),
    SubtitleIntOption("반투명", 0x99000000.toInt()),
    SubtitleIntOption("검정", android.graphics.Color.BLACK),
)
private val PlayerMenuFocusColor = Color(0xFFFFD400)
private val PlayerMenuFocusBackground = Color(0xFF053A46)
private val PlayerMenuSelectedBackground = Color(0xFF12404A)
private val PlayerMenuIdleBorder = Color(0x66FFFFFF)
private const val SubtitleLineSpacingMultiplier = 1.24f
private const val VerticalSubtitleWordSpacing = .22f

private fun normalizeStoredScreenBrightness(
    storedBrightness: Float,
    hasStoredBrightness: Boolean,
): Float =
    when {
        !hasStoredBrightness -> 50f
        storedBrightness >= -20f && storedBrightness <= 20f ->
            (50f + storedBrightness * 2f).coerceIn(1f, 100f)
        else ->
            storedBrightness.coerceIn(1f, 100f)
    }

private fun applyPlaybackWindowBrightness(
    activity: Activity?,
    brightnessPercent: Float,
) {
    val brightness = (brightnessPercent / 100f).coerceIn(.01f, 1f)
    activity?.window?.let { window ->
        window.attributes = window.attributes.apply {
            screenBrightness = brightness
        }
    }
}

@Composable
private fun playerSettingsSliderColors(focused: Boolean) =
    SliderDefaults.colors(
        thumbColor = if (focused) {
            PlayerMenuFocusColor
        } else {
            Color.White.copy(alpha = .82f)
        },
        activeTrackColor = if (focused) {
            PlayerMenuFocusColor
        } else {
            Color.White.copy(alpha = .52f)
        },
        inactiveTrackColor = Color.White.copy(alpha = .34f),
        activeTickColor = if (focused) {
            Color.Black.copy(alpha = .72f)
        } else {
            Color.White.copy(alpha = .72f)
        },
        inactiveTickColor = Color.White.copy(alpha = .72f),
    )

@Composable
private fun WebOsPictureSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    focusRequester: FocusRequester,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Text("$label ${value.roundToInt()}")
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .onPreviewKeyEvent {
                handleDpadFocusMove(
                    it.nativeKeyEvent,
                    up = up,
                    down = down,
                )
            },
        colors = playerSettingsSliderColors(focused),
        valueRange = 0f..100f,
        steps = 99,
    )
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
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
    onPlaybackUnavailable: (Long) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
    val isTelevision = remember(context, configuration) {
        context.isTelevisionDevice()
    }
    var controllerVisible by remember { mutableStateOf(false) }
    var playerSettingsVisible by remember { mutableStateOf(false) }
    var playerViewHandle by remember { mutableStateOf<PlayerView?>(null) }
    val playerFocusRequester = remember { FocusRequester() }
    val playerSettingsFocusRequester = remember { FocusRequester() }
    var playerSettingsPage by remember {
        mutableStateOf(PlayerSettingsPage.MAIN)
    }
    val initialPlaybackUrl = remember(source.url) { source.url }
    val playbackFallbackUrls = remember(source, initialPlaybackUrl) {
        ArrayDeque(
            source.fallbackUrls
                .distinct()
                .filterNot { it == initialPlaybackUrl },
        )
    }
    var activePlaybackUrl by remember(source, initialPlaybackUrl) {
        mutableStateOf(initialPlaybackUrl)
    }
    var playbackReconnectAttempts by remember(source.url) { mutableIntStateOf(0) }
    var tracksRevision by remember { mutableIntStateOf(0) }
    var playerSubtitleText by remember { mutableStateOf("") }
    var manualSubtitleText by remember { mutableStateOf("") }
    var useNativeSubtitleRenderer by remember { mutableStateOf(false) }
    val activeSubtitleText =
        if (useNativeSubtitleRenderer) {
            ""
        } else {
            playerSubtitleText.ifBlank { manualSubtitleText }
        }
    var manualSubtitleIndex by remember(source.ratingKey, source.subtitles) {
        mutableStateOf(defaultSubtitleIndex(source.subtitles))
    }
    var manualSubtitleCues by remember(source.ratingKey, source.subtitles) {
        mutableStateOf(emptyList<ManualSubtitleCue>())
    }
    var completionHandled by remember(source.ratingKey) { mutableStateOf(false) }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }
    var gestureFeedbackRevision by remember { mutableIntStateOf(0) }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val playerPreferences = remember {
        context.getSharedPreferences(
            "player_settings",
            android.content.Context.MODE_PRIVATE,
        )
    }
    val deviceCapabilities = remember(context, configuration) {
        detectDeviceCapabilities(context)
    }
    var deviceOptimizationMode by remember {
        mutableStateOf(
            DeviceOptimizationMode.fromStorage(
                playerPreferences.getString("device_optimization_mode", null),
            ),
        )
    }
    val activePlaybackOptimizationProfile = remember(source.url) {
        deviceOptimizationMode.resolveProfile(deviceCapabilities)
    }
    var playbackSpeed by remember {
        mutableFloatStateOf(playerPreferences.getFloat("playback_speed", 1f))
    }
    var videoScaleMode by remember {
        mutableStateOf(
            VideoScaleMode.fromStorage(
                playerPreferences.getString("video_scale_mode", null),
            ),
        )
    }
    val displaySettingsNeedReset = remember {
        !playerPreferences.getBoolean(
            "video_picture_controls_v1",
            true,
        )
    }
    var videoScreenSettings by remember {
        mutableStateOf(
            if (displaySettingsNeedReset) {
                VideoScreenSettings()
            } else {
                VideoScreenSettings(
                    brightness = playerPreferences.getFloat(
                        "video_brightness_percent",
                        VideoScreenSettings().brightness,
                    ).coerceIn(1f, 100f),
                    pictureMode = PictureMode.fromStorage(
                        playerPreferences.getString("video_picture_mode", null),
                    ),
                    pictureBrightness = playerPreferences.getFloat(
                        "video_picture_brightness",
                        VideoScreenSettings().pictureBrightness,
                    ).coerceIn(-50f, 50f),
                    pictureContrast = playerPreferences.getFloat(
                        "video_picture_contrast",
                        VideoScreenSettings().pictureContrast,
                    ).coerceIn(-50f, 50f),
                    pictureBlackLevel = playerPreferences.getFloat(
                        "video_picture_black_level",
                        VideoScreenSettings().pictureBlackLevel,
                    ).coerceIn(-50f, 50f),
                    pictureColorDepth = playerPreferences.getFloat(
                        "video_picture_color_depth",
                        VideoScreenSettings().pictureColorDepth,
                    ).coerceIn(-50f, 50f),
                    pictureColorTemperature = playerPreferences.getFloat(
                        "video_picture_color_temperature",
                        VideoScreenSettings().pictureColorTemperature,
                    ).coerceIn(-50f, 50f),
                )
            },
        )
    }
    var videoScreenPresetRevision by remember { mutableIntStateOf(0) }
    val customSubtitleFontFile = remember {
        File(context.filesDir, "custom_subtitle_font")
    }
    val pendingCustomSubtitleFontFile = remember {
        File(context.cacheDir, "pending_subtitle_font")
    }
    var customFontRevision by remember { mutableIntStateOf(0) }
    var customFontDisplayName by remember {
        mutableStateOf(
            playerPreferences.getString("custom_subtitle_font_name", null),
        )
    }
    var subtitleFont by remember {
        val stored = SubtitleFontOption.fromStorage(
            playerPreferences.getString("subtitle_font", null),
        )
        mutableStateOf(
            if (
                stored == SubtitleFontOption.CUSTOM &&
                !customSubtitleFontFile.exists()
            ) {
                SubtitleFontOption.GOWUN
            } else {
                stored
            },
        )
    }
    var subtitleAppearance by remember {
        mutableStateOf(
            SubtitleAppearance(
                sizePercent = playerPreferences.getInt("subtitle_size", 100),
                horizontalOffsetPercent = playerPreferences.getInt(
                    "subtitle_horizontal_offset",
                    0,
                ),
                verticalOffsetPercent = playerPreferences.getInt(
                    "subtitle_vertical_offset",
                    0,
                ),
                verticalWriting = playerPreferences.getBoolean(
                    "subtitle_vertical_writing",
                    false,
                ),
                foregroundColor = playerPreferences.getInt(
                    "subtitle_color",
                    android.graphics.Color.WHITE,
                ),
                backgroundColor = playerPreferences.getInt(
                    "subtitle_background",
                    android.graphics.Color.TRANSPARENT,
                ),
                edgeType = playerPreferences.getInt(
                    "subtitle_edge_type",
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                ),
                edgeColor = playerPreferences.getInt(
                    "subtitle_edge_color",
                    android.graphics.Color.BLACK,
                ),
            ),
        )
    }
    val customFontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val imported = runCatching {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "글꼴 파일을 열 수 없습니다." }
                    pendingCustomSubtitleFontFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Typeface.createFromFile(pendingCustomSubtitleFontFile)
                runCatching {
                    Files.move(
                        pendingCustomSubtitleFontFile.toPath(),
                        customSubtitleFontFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }.getOrElse {
                    Files.move(
                        pendingCustomSubtitleFontFile.toPath(),
                        customSubtitleFontFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                queryDisplayName(context, uri)
            }
            imported.onSuccess { displayName ->
                customFontRevision++
                customFontDisplayName = displayName
                subtitleFont = SubtitleFontOption.CUSTOM
                playerPreferences.edit()
                    .putString(
                        "subtitle_font",
                        SubtitleFontOption.CUSTOM.storageValue,
                    )
                    .putString("custom_subtitle_font_name", displayName)
                    .apply()
                Toast.makeText(
                    context,
                    "사용자 글꼴을 적용했습니다.",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                pendingCustomSubtitleFontFile.delete()
                Toast.makeText(
                    context,
                    "올바른 TTF 또는 OTF 글꼴 파일이 아닙니다.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val subtitleTypeface = remember(subtitleFont, customFontRevision) {
        when (subtitleFont) {
            SubtitleFontOption.SYSTEM -> Typeface.DEFAULT
            SubtitleFontOption.ASIA_CINEMA_B ->
                ResourcesCompat.getFont(context, R.font.asia_cinema_b)
                    ?: Typeface.DEFAULT
            SubtitleFontOption.ASIA_CINEMA_M ->
                ResourcesCompat.getFont(context, R.font.asia_cinema_m)
                    ?: Typeface.DEFAULT
            SubtitleFontOption.ASIA_CINEMA_L ->
                ResourcesCompat.getFont(context, R.font.asia_cinema_l)
                    ?: Typeface.DEFAULT
            SubtitleFontOption.CUSTOM ->
                runCatching {
                    Typeface.createFromFile(customSubtitleFontFile)
                }.getOrElse {
                    ResourcesCompat.getFont(context, R.font.cinema) ?: Typeface.SERIF
                }
            SubtitleFontOption.GOWUN ->
                ResourcesCompat.getFont(context, R.font.cinema) ?: Typeface.SERIF
            SubtitleFontOption.GOTHIC ->
                Typeface.create("sans-serif", Typeface.NORMAL)
            SubtitleFontOption.SERIF ->
                Typeface.create("serif", Typeface.NORMAL)
            SubtitleFontOption.ROUNDED ->
                Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        }
    }

    fun updateSubtitleAppearance(value: SubtitleAppearance) {
        subtitleAppearance = value
        playerPreferences.edit()
            .putInt("subtitle_size", value.sizePercent)
            .putInt(
                "subtitle_horizontal_offset",
                value.horizontalOffsetPercent,
            )
            .putInt(
                "subtitle_vertical_offset",
                value.verticalOffsetPercent,
            )
            .putBoolean("subtitle_vertical_writing", value.verticalWriting)
            .putInt("subtitle_color", value.foregroundColor)
            .putInt("subtitle_background", value.backgroundColor)
            .putInt("subtitle_edge_type", value.edgeType)
            .putInt("subtitle_edge_color", value.edgeColor)
            .apply()
    }
    fun persistVideoScreenSettings(
        value: VideoScreenSettings,
        commitNow: Boolean,
    ): Boolean {
        val editor = playerPreferences.edit()
            .putFloat("video_brightness_percent", value.brightness)
            .putString("video_scale_mode", videoScaleMode.storageValue)
            .putString("video_picture_mode", value.pictureMode.storageValue)
            .putFloat("video_picture_brightness", value.pictureBrightness)
            .putFloat("video_picture_contrast", value.pictureContrast)
            .putFloat("video_picture_black_level", value.pictureBlackLevel)
            .putFloat("video_picture_color_depth", value.pictureColorDepth)
            .putFloat(
                "video_picture_color_temperature",
                value.pictureColorTemperature,
            )
            .remove("video_brightness")
            .remove("video_tone_brightness")
            .remove("video_contrast")
            .remove("video_saturation")
            .remove("video_color_temperature")
            .remove("video_color_boost_mode")
            .remove("video_sharpness_mode")
            .remove("noise_reduction_mode")
            .putBoolean("video_picture_controls_v1", true)
        return if (commitNow) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
    }
    fun saveVideoScreenSettingsSnapshot(
        value: VideoScreenSettings,
    ): Boolean =
        playerPreferences.edit()
            .putBoolean("saved_video_screen_available", true)
            .putFloat("saved_video_brightness_percent", value.brightness)
            .putString("saved_video_scale_mode", videoScaleMode.storageValue)
            .putString("saved_video_picture_mode", value.pictureMode.storageValue)
            .putFloat(
                "saved_video_picture_brightness",
                value.pictureBrightness,
            )
            .putFloat("saved_video_picture_contrast", value.pictureContrast)
            .putFloat("saved_video_picture_black_level", value.pictureBlackLevel)
            .putFloat("saved_video_picture_color_depth", value.pictureColorDepth)
            .putFloat(
                "saved_video_picture_color_temperature",
                value.pictureColorTemperature,
            )
            .commit()

    fun loadVideoScreenSettingsSnapshot(): Pair<VideoScreenSettings, VideoScaleMode>? {
        if (!playerPreferences.getBoolean("saved_video_screen_available", false)) {
            return null
        }
        val defaults = VideoScreenSettings()
        val settings = VideoScreenSettings(
            brightness = playerPreferences.getFloat(
                "saved_video_brightness_percent",
                defaults.brightness,
            ).coerceIn(1f, 100f),
            pictureMode = PictureMode.fromStorage(
                playerPreferences.getString("saved_video_picture_mode", null),
            ),
            pictureBrightness = playerPreferences.getFloat(
                "saved_video_picture_brightness",
                defaults.pictureBrightness,
            ).coerceIn(-50f, 50f),
            pictureContrast = playerPreferences.getFloat(
                "saved_video_picture_contrast",
                defaults.pictureContrast,
            ).coerceIn(-50f, 50f),
            pictureBlackLevel = playerPreferences.getFloat(
                "saved_video_picture_black_level",
                defaults.pictureBlackLevel,
            ).coerceIn(-50f, 50f),
            pictureColorDepth = playerPreferences.getFloat(
                "saved_video_picture_color_depth",
                defaults.pictureColorDepth,
            ).coerceIn(-50f, 50f),
            pictureColorTemperature = playerPreferences.getFloat(
                "saved_video_picture_color_temperature",
                defaults.pictureColorTemperature,
            ).coerceIn(-50f, 50f),
        )
        val scaleMode = VideoScaleMode.fromStorage(
            playerPreferences.getString("saved_video_scale_mode", null),
        )
        return settings to scaleMode
    }
    fun isVideoScreenPresetAvailable(slot: Int): Boolean {
        val prefix = "video_screen_preset_${slot}_"
        return playerPreferences.getBoolean("${prefix}saved", false) ||
            (
                slot == 1 &&
                    playerPreferences.getBoolean(
                        "saved_video_screen_available",
                        false,
                    )
                )
    }
    fun saveVideoScreenPreset(
        slot: Int,
        value: VideoScreenSettings,
    ): Boolean {
        val prefix = "video_screen_preset_${slot}_"
        return playerPreferences.edit()
            .putBoolean("${prefix}saved", true)
            .putFloat("${prefix}brightness", value.brightness)
            .putString("${prefix}scale_mode", videoScaleMode.storageValue)
            .putString("${prefix}picture_mode", value.pictureMode.storageValue)
            .putFloat("${prefix}picture_brightness", value.pictureBrightness)
            .putFloat("${prefix}picture_contrast", value.pictureContrast)
            .putFloat("${prefix}picture_black_level", value.pictureBlackLevel)
            .putFloat("${prefix}picture_color_depth", value.pictureColorDepth)
            .putFloat(
                "${prefix}picture_color_temperature",
                value.pictureColorTemperature,
            )
            .commit()
    }
    fun loadVideoScreenPreset(
        slot: Int,
    ): Pair<VideoScreenSettings, VideoScaleMode>? {
        val prefix = "video_screen_preset_${slot}_"
        if (!playerPreferences.getBoolean("${prefix}saved", false)) {
            return if (slot == 1) loadVideoScreenSettingsSnapshot() else null
        }
        val defaults = VideoScreenSettings()
        val settings = VideoScreenSettings(
            brightness = playerPreferences.getFloat(
                "${prefix}brightness",
                defaults.brightness,
            ).coerceIn(1f, 100f),
            pictureMode = PictureMode.fromStorage(
                playerPreferences.getString("${prefix}picture_mode", null),
            ),
            pictureBrightness = playerPreferences.getFloat(
                "${prefix}picture_brightness",
                defaults.pictureBrightness,
            ).coerceIn(-50f, 50f),
            pictureContrast = playerPreferences.getFloat(
                "${prefix}picture_contrast",
                defaults.pictureContrast,
            ).coerceIn(-50f, 50f),
            pictureBlackLevel = playerPreferences.getFloat(
                "${prefix}picture_black_level",
                defaults.pictureBlackLevel,
            ).coerceIn(-50f, 50f),
            pictureColorDepth = playerPreferences.getFloat(
                "${prefix}picture_color_depth",
                defaults.pictureColorDepth,
            ).coerceIn(-50f, 50f),
            pictureColorTemperature = playerPreferences.getFloat(
                "${prefix}picture_color_temperature",
                defaults.pictureColorTemperature,
            ).coerceIn(-50f, 50f),
        )
        val scaleMode = VideoScaleMode.fromStorage(
            playerPreferences.getString("${prefix}scale_mode", null),
        )
        return settings to scaleMode
    }
    fun updateVideoScreenSettings(value: VideoScreenSettings) {
        videoScreenSettings = value
        persistVideoScreenSettings(value, commitNow = false)
    }
    LaunchedEffect(displaySettingsNeedReset) {
        if (displaySettingsNeedReset) {
            videoScaleMode = VideoScaleMode.FIT
            val editor = playerPreferences.edit()
                .remove("video_scale_mode")
                .remove("video_bright_vivid_defaults_v2")
            for (slot in 1..3) {
                val prefix = "video_screen_preset_${slot}_"
                listOf(
                    "saved",
                    "brightness",
                    "tone_brightness",
                    "contrast",
                    "saturation",
                    "color_temperature",
                    "sharpness_mode",
                    "noise_reduction_mode",
                    "scale_mode",
                    "picture_mode",
                    "picture_brightness",
                    "picture_contrast",
                    "picture_black_level",
                    "picture_color_depth",
                    "picture_color_temperature",
                ).forEach { suffix ->
                    editor.remove("$prefix$suffix")
                }
            }
            editor.apply()
            updateVideoScreenSettings(VideoScreenSettings())
        }
    }
    val player = remember(source.url, initialPlaybackUrl) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(60_000)
            .setUserAgent("PlexPlay/${BuildConfig.VERSION_NAME} Android")
            .setDefaultRequestProperties(
                mapOf(
                    "X-Plex-Token" to source.token,
                    "Cache-Control" to "no-cache, no-store",
                    "Pragma" to "no-cache",
                ),
            )
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        val safeTargetBufferBytes = minOf(
            activePlaybackOptimizationProfile.targetBufferBytes,
            (
                deviceCapabilities.appMemoryLimitMb.toLong() * 1024L * 1024L / 6L
                ).coerceAtLeast(12L * 1024L * 1024L).toInt(),
        )
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                activePlaybackOptimizationProfile.minBufferMs,
                activePlaybackOptimizationProfile.maxBufferMs,
                activePlaybackOptimizationProfile.bufferForPlaybackMs,
                activePlaybackOptimizationProfile.bufferAfterRebufferMs,
            )
            .setBackBuffer(
                activePlaybackOptimizationProfile.backBufferMs,
                false,
            )
            .setTargetBufferBytes(
                safeTargetBufferBytes,
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
            .setLoadErrorHandlingPolicy(
                DefaultLoadErrorHandlingPolicy(6),
            )
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                trackSelectionParameters =
                    trackSelectionParameters.buildUpon()
                        .setPreferredTextLanguages("ko", "kor")
                        .setSelectUndeterminedTextLanguage(true)
                        .setMaxVideoSize(
                            activePlaybackOptimizationProfile.maxVideoWidth,
                            activePlaybackOptimizationProfile.maxVideoHeight,
                        )
                        .build()
                setMediaItem(
                    buildMediaItem(initialPlaybackUrl, source),
                )
                if (source.resumePositionMs > 0) seekTo(source.resumePositionMs)
                prepare()
                playWhenReady = true
            }
    }

    LaunchedEffect(
        activity,
        videoScreenSettings.brightness,
    ) {
        applyPlaybackWindowBrightness(activity, videoScreenSettings.brightness)
    }

    fun finishPlaybackAsWatched() {
        if (completionHandled) return
        completionHandled = true
        val position = player.currentPosition.coerceAtLeast(0)
        player.pause()
        onPlaybackCompleted(position)
    }

    fun showGestureFeedback(message: String) {
        gestureFeedback = message
        gestureFeedbackRevision++
    }

    var confirmLongPressConsumed by remember { mutableStateOf(false) }
    var leftLongPressSeeking by remember { mutableStateOf(false) }
    var rightLongPressSeeking by remember { mutableStateOf(false) }
    val lastHandledRemoteEventSignature = remember { LongArray(1) }

    fun openPlayerSettings(page: PlayerSettingsPage = PlayerSettingsPage.MAIN) {
        playerSettingsPage = page
        playerSettingsVisible = true
        playerViewHandle?.hideController()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            showGestureFeedback("일시정지")
        } else {
            player.play()
            showGestureFeedback("재생")
        }
    }

    fun seekByRemote(deltaMs: Long) {
        val duration = player.duration
            .takeIf { it > 0 && it != C.TIME_UNSET }
            ?: source.durationMs
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, duration.coerceAtLeast(0L))
        player.seekTo(target)
        showGestureFeedback(
            if (deltaMs >= 0L) "10초 앞으로" else "10초 뒤로",
        )
    }

    fun configureEpisodeNavigation(playerView: PlayerView) {
        configureEpisodeNavigationButtons(
            playerView = playerView,
            hasPrevious = hasPreviousPlayback,
            hasNext = hasNextPlayback,
            onPrevious = {
                onPlayPrevious(player.currentPosition.coerceAtLeast(0))
                showGestureFeedback("이전화")
            },
            onNext = {
                onPlayNext(player.currentPosition.coerceAtLeast(0))
                showGestureFeedback("다음화")
            },
        )
    }

    fun showRemoteControls() {
        playerViewHandle?.apply {
            showController()
            post {
                configureEpisodeNavigation(this)
                findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                    ?.requestFocus()
            }
        }
    }

    fun playNextFromRemote() {
        if (hasNextPlayback) {
            onPlayNext(player.currentPosition.coerceAtLeast(0))
            showGestureFeedback("다음화")
        } else {
            showGestureFeedback("다음 영상이 없습니다")
        }
    }

    fun handlePlayerRemoteKey(nativeEvent: AndroidKeyEvent): Boolean {
        if (playerSettingsVisible) return true

        val isDown = nativeEvent.action == AndroidKeyEvent.ACTION_DOWN
        val isUp = nativeEvent.action == AndroidKeyEvent.ACTION_UP
        if (!isDown && !isUp) return false

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
        if (
            controllerVisible &&
            controllerNavigationKey &&
            nativeEvent.repeatCount == 0
        ) {
            return false
        }

        val eventSignature =
            nativeEvent.eventTime * 1_000L +
                nativeEvent.keyCode * 10L +
                nativeEvent.action
        if (lastHandledRemoteEventSignature[0] == eventSignature) {
            return true
        }

        fun openSettingsFromRemote(page: PlayerSettingsPage = PlayerSettingsPage.MAIN) {
            if (!playerSettingsVisible) {
                openPlayerSettings(page)
            }
        }

        fun handleConfirmKey(): Boolean {
            if (isDown && nativeEvent.repeatCount > 0) {
                confirmLongPressConsumed = true
                return true
            }
            if (isUp) {
                if (confirmLongPressConsumed) {
                    confirmLongPressConsumed = false
                } else {
                    showRemoteControls()
                }
                return true
            }
            return true
        }

        fun handlePlayPauseKey(): Boolean {
            if (isDown && nativeEvent.repeatCount == 0) {
                togglePlayPause()
            }
            return true
        }

        val handled = when (nativeEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A,
            AndroidKeyEvent.KEYCODE_BUTTON_SELECT,
            AndroidKeyEvent.KEYCODE_BUTTON_START,
            AndroidKeyEvent.KEYCODE_SPACE,
            -> handleConfirmKey()

            AndroidKeyEvent.KEYCODE_HEADSETHOOK,
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> handlePlayPauseKey()

            AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (isDown && nativeEvent.repeatCount == 0) {
                    player.play()
                    showGestureFeedback("재생")
                }
                true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (isDown && nativeEvent.repeatCount == 0) {
                    player.pause()
                    showGestureFeedback("일시정지")
                }
                true
            }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isDown && nativeEvent.repeatCount > 0) {
                    leftLongPressSeeking = true
                    seekByRemote(-10_000L)
                } else if (isUp) {
                    if (leftLongPressSeeking) {
                        leftLongPressSeeking = false
                    } else {
                        seekByRemote(-10_000L)
                    }
                }
                true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
            AndroidKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            AndroidKeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            AndroidKeyEvent.KEYCODE_BUTTON_L1,
            -> {
                if (isDown) {
                    seekByRemote(-10_000L)
                }
                true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
            -> {
                if (isDown && nativeEvent.repeatCount == 0 && hasPreviousPlayback) {
                    onPlayPrevious(player.currentPosition.coerceAtLeast(0))
                    showGestureFeedback("이전화")
                } else if (isDown) {
                    seekByRemote(-10_000L)
                }
                true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
            AndroidKeyEvent.KEYCODE_CHANNEL_UP,
            -> {
                if (isDown && nativeEvent.repeatCount == 0) {
                    playNextFromRemote()
                }
                true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isDown && nativeEvent.repeatCount > 0) {
                    rightLongPressSeeking = true
                    seekByRemote(10_000L)
                } else if (isUp) {
                    if (rightLongPressSeeking) {
                        rightLongPressSeeking = false
                    } else {
                        seekByRemote(10_000L)
                    }
                }
                true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            AndroidKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            AndroidKeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            AndroidKeyEvent.KEYCODE_BUTTON_R1,
            -> {
                if (isDown) {
                    seekByRemote(10_000L)
                }
                true
            }
            AndroidKeyEvent.KEYCODE_DPAD_UP,
            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                if (isDown && nativeEvent.repeatCount > 0) {
                    openSettingsFromRemote()
                } else if (isDown) {
                    showRemoteControls()
                }
                true
            }
            AndroidKeyEvent.KEYCODE_MENU,
            AndroidKeyEvent.KEYCODE_SETTINGS,
            AndroidKeyEvent.KEYCODE_INFO,
            AndroidKeyEvent.KEYCODE_GUIDE,
            AndroidKeyEvent.KEYCODE_BUTTON_MODE,
            -> {
                if (isDown || isUp) {
                    openSettingsFromRemote()
                }
                true
            }
            AndroidKeyEvent.KEYCODE_CAPTIONS -> {
                if (isDown || isUp) {
                    openSettingsFromRemote(PlayerSettingsPage.SUBTITLE)
                }
                true
            }
            AndroidKeyEvent.KEYCODE_LANGUAGE_SWITCH -> {
                if (isDown || isUp) {
                    openSettingsFromRemote(PlayerSettingsPage.AUDIO)
                }
                true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_STOP -> {
                if (isDown && nativeEvent.repeatCount == 0) {
                    player.pause()
                    onProgress(
                        source,
                        player.currentPosition.coerceAtLeast(0),
                        "stopped",
                    )
                    onClose()
                }
                true
            }
            else -> false
        }
        if (handled) {
            lastHandledRemoteEventSignature[0] = eventSignature
        }
        return handled
    }

    fun closeOrStepBackPlayerSettings() {
        when (playerSettingsPage) {
            PlayerSettingsPage.MAIN -> playerSettingsVisible = false
            PlayerSettingsPage.DISPLAY_ADVANCED ->
                playerSettingsPage = PlayerSettingsPage.DISPLAY
            else -> playerSettingsPage = PlayerSettingsPage.MAIN
        }
    }

    fun handlePlayerSettingsKeyEvent(nativeEvent: AndroidKeyEvent): Boolean {
        val keyHandledByDialog = when (nativeEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_BACK,
            AndroidKeyEvent.KEYCODE_ESCAPE,
            AndroidKeyEvent.KEYCODE_MENU,
            AndroidKeyEvent.KEYCODE_SETTINGS,
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
            AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> true
            else -> false
        }
        if (!keyHandledByDialog) return false
        if (
            nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
            nativeEvent.repeatCount == 0 &&
            nativeEvent.keyCode !in listOf(
                AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            )
        ) {
            closeOrStepBackPlayerSettings()
        }
        return true
    }

    LaunchedEffect(gestureFeedbackRevision) {
        if (gestureFeedbackRevision == 0) return@LaunchedEffect
        delay(900)
        gestureFeedback = null
    }

    LaunchedEffect(player, playerSettingsVisible) {
        if (!playerSettingsVisible) {
            playerFocusRequester.requestFocus()
        } else {
            playerViewHandle?.hideController()
            delay(80)
            runCatching { playerSettingsFocusRequester.requestFocus() }
        }
    }

    BackHandler {
        if (playerSettingsVisible) {
            closeOrStepBackPlayerSettings()
        } else {
            player.pause()
            onProgress(source, player.currentPosition.coerceAtLeast(0), "paused")
            onClose()
        }
    }

    val latestVideoScreenSettings by rememberUpdatedState(videoScreenSettings)

    DisposableEffect(player) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val previousScreenBrightness = window?.attributes?.screenBrightness
        val insetsController = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        val fallbackListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                playerSubtitleText = ""
                useNativeSubtitleRenderer = false
                selectDefaultSubtitleIfNeeded(player, tracks)
                tracksRevision++
            }

            override fun onCues(cueGroup: CueGroup) {
                useNativeSubtitleRenderer =
                    cueGroup.cues.any { it.bitmap != null }
                playerSubtitleText =
                    if (useNativeSubtitleRenderer) {
                        ""
                    } else {
                        cueGroup.cues.toSubtitleOverlayText()
                    }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> playbackReconnectAttempts = 0
                    Player.STATE_ENDED -> finishPlaybackAsWatched()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val position = player.currentPosition.coerceAtLeast(0)
                if (
                    error.isRetryableConnectionFailure() &&
                    playbackReconnectAttempts < MaxPlaybackReconnectAttempts
                ) {
                    playbackReconnectAttempts++
                    player.stop()
                    player.seekTo(position)
                    player.prepare()
                    player.playWhenReady = true
                    showGestureFeedback(
                        "재생 연결 복구 중 " +
                            "$playbackReconnectAttempts/$MaxPlaybackReconnectAttempts",
                    )
                    return
                }
                val fallbackUrl = playbackFallbackUrls.pollFirst()
                if (fallbackUrl == null) {
                    Toast.makeText(
                        context,
                        "기본 재생이 어려워 범용 코덱으로 전환합니다.",
                        Toast.LENGTH_LONG,
                    ).show()
                    onPlaybackUnavailable(position)
                    return
                }
                playbackReconnectAttempts = 0
                activePlaybackUrl = fallbackUrl
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(
                    buildMediaItem(fallbackUrl, source),
                )
                player.seekTo(position)
                player.prepare()
                player.playWhenReady = true
                showGestureFeedback(
                    "원본 화질로 자동 재연결합니다",
                )
            }
        }
        player.addListener(fallbackListener)
        onDispose {
            player.removeListener(fallbackListener)
            onProgress(source, player.currentPosition.coerceAtLeast(0), "stopped")
            player.release()
            if (window != null && previousScreenBrightness != null) {
                window.attributes = window.attributes.apply {
                    screenBrightness = previousScreenBrightness
                }
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    LaunchedEffect(player, source.url) {
        repeat(24) {
            val tracks = player.currentTracks
            if (
                tracks.groups.any {
                    it.type == C.TRACK_TYPE_TEXT && it.length > 0
                }
            ) {
                selectDefaultSubtitleIfNeeded(player, tracks)
                tracksRevision++
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val cueGroup = player.currentCues
            val hasBitmapCue = cueGroup.cues.any { it.bitmap != null }
            useNativeSubtitleRenderer = hasBitmapCue
            playerSubtitleText =
                if (hasBitmapCue) {
                    ""
                } else {
                    cueGroup.cues.toSubtitleOverlayText()
                }
            delay(200)
        }
    }

    LaunchedEffect(
        source.ratingKey,
        source.subtitles,
        manualSubtitleIndex,
        activePlaybackUrl,
    ) {
        manualSubtitleText = ""
        manualSubtitleCues = emptyList()
        val subtitle = manualSubtitleIndex
            ?.let { source.subtitles.getOrNull(it) }
            ?: return@LaunchedEffect
        if (!subtitle.isManualTextSubtitle()) return@LaunchedEffect
        manualSubtitleCues =
            runCatching {
                loadManualSubtitleCues(subtitle, source.token)
            }.getOrDefault(emptyList())
    }

    LaunchedEffect(player, manualSubtitleCues) {
        while (true) {
            manualSubtitleText =
                manualSubtitleCues.textAt(player.currentPosition.coerceAtLeast(0))
            delay(120)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            onProgress(
                source,
                player.currentPosition.coerceAtLeast(0),
                if (player.isPlaying) "playing" else "paused",
            )
        }
    }

    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    val audioTracks = remember(player, tracksRevision) {
        buildList {
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .forEach { group ->
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        val language = format.language?.let {
                            Locale.forLanguageTag(it)
                                .getDisplayLanguage(Locale.KOREAN)
                                .ifBlank { it.uppercase() }
                        }
                        val label = format.label
                            ?: language
                            ?: "오디오 ${size + 1}"
                        val channels = format.channelCount
                            .takeIf { it > 0 }
                            ?.let { " · ${it}채널" }
                            .orEmpty()
                        add(
                            AudioTrackOption(
                                group = group.mediaTrackGroup,
                                trackIndex = index,
                                label = label + channels,
                                selected = group.isTrackSelected(index),
                            ),
                        )
                    }
                }
        }
    }
    val subtitleTracks = remember(player, tracksRevision) {
        buildList {
            var fallbackExternalSubtitleIndex = 0
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .forEach { group ->
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        val language = format.language?.let {
                            Locale.forLanguageTag(it)
                                .getDisplayLanguage(Locale.KOREAN)
                                .ifBlank { it.uppercase() }
                        }
                        val sourceLabel = format.label.orEmpty()
                        val isExternal =
                            sourceLabel.startsWith(
                                ExternalSubtitleLabelPrefix,
                            )
                        val externalSubtitleIndex =
                            if (isExternal) {
                                externalSubtitleIndexFromLabel(sourceLabel)
                                    ?: fallbackExternalSubtitleIndex++
                            } else {
                                null
                            }
                        val sampleMimeType =
                            format.sampleMimeType.orEmpty().lowercase(Locale.ROOT)
                        val isBitmap =
                            sampleMimeType.contains("pgs") ||
                                sampleMimeType.contains("vobsub") ||
                                sampleMimeType.contains("dvbsubs")
                        val trackLabel =
                            displaySubtitleTrackLabel(sourceLabel)
                                .ifBlank { language ?: "자막 ${size + 1}" }
                        add(
                            SubtitleTrackOption(
                                group = group.mediaTrackGroup,
                                trackIndex = index,
                                label = trackLabel +
                                    if (isBitmap) {
                                        " · 그림 자막"
                                    } else {
                                        ""
                                    },
                                selected = group.isTrackSelected(index),
                                isExternal = isExternal,
                                isBitmap = isBitmap,
                                externalSubtitleIndex = externalSubtitleIndex,
                            ),
                        )
                    }
                }
        }
    }
    val latestSubtitleAppearance by rememberUpdatedState(subtitleAppearance)
    val latestActiveSubtitleText by rememberUpdatedState(activeSubtitleText)
    val latestUpdateSubtitleAppearance by
        rememberUpdatedState<(SubtitleAppearance) -> Unit>(
            ::updateSubtitleAppearance,
        )

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                handlePlayerRemoteKey(event.nativeKeyEvent)
            }
            .pointerInput(videoScaleMode) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var pinchStarted = false
                    var zoomAmount = 1f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedCount =
                            event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            pinchStarted = true
                            val eventZoom = event.calculateZoom()
                            if (eventZoom.isFinite() && eventZoom > 0f) {
                                zoomAmount *= eventZoom
                            }
                        }
                        if (pinchStarted) {
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (pinchStarted) {
                        val zoomModes = listOf(
                            VideoScaleMode.FIT,
                            VideoScaleMode.ZOOM,
                            VideoScaleMode.ZOOM_LARGE,
                        )
                        val baseScale = when (videoScaleMode) {
                            VideoScaleMode.FIT -> 1f
                            VideoScaleMode.ZOOM -> 1.15f
                            VideoScaleMode.ZOOM_LARGE -> 1.3f
                            VideoScaleMode.STRETCH -> 1f
                        }
                        val targetScale =
                            (baseScale * zoomAmount).coerceIn(1f, 1.3f)
                        val selected = zoomModes.minBy { mode ->
                            abs(mode.scaleX - targetScale)
                        }
                        if (selected != videoScaleMode) {
                            videoScaleMode = selected
                            playerPreferences.edit()
                                .putString(
                                    "video_scale_mode",
                                    selected.storageValue,
                                )
                                .apply()
                        }
                        showGestureFeedback(selected.label)
                    }
                }
            }
            .pointerInput(player, activity, audioManager, videoScaleMode) {
                var startX = 0f
                var startY = 0f
                var totalX = 0f
                var totalY = 0f
                var mode: PlayerGestureMode? = null
                var startVolume = 0
                var startBrightness = .5f
                var startPositionMs = 0L
                var seekTargetMs = 0L
                var startSubtitleHorizontalOffset = 0
                var startSubtitleVerticalOffset = 0

                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        startY = offset.y
                        totalX = 0f
                        totalY = 0f
                        mode = null
                        startVolume = audioManager.getStreamVolume(
                            AudioManager.STREAM_MUSIC,
                        )
                        startBrightness = activity?.window
                            ?.attributes
                            ?.screenBrightness
                            ?.takeIf { it >= 0f }
                            ?: (
                                Settings.System.getInt(
                                    context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS,
                                    128,
                                ) / 255f
                        )
                        startPositionMs = player.currentPosition.coerceAtLeast(0)
                        seekTargetMs = startPositionMs
                        val appearance = latestSubtitleAppearance
                        startSubtitleHorizontalOffset =
                            appearance.horizontalOffsetPercent
                        startSubtitleVerticalOffset =
                            appearance.verticalOffsetPercent
                    },
                    onDragCancel = {
                        mode = null
                    },
                    onDragEnd = {
                        if (mode == PlayerGestureMode.SEEK) {
                            player.seekTo(seekTargetMs)
                            onProgress(
                                source,
                                seekTargetMs,
                                if (player.isPlaying) "playing" else "paused",
                            )
                        }
                        if (
                            mode == PlayerGestureMode.SCALE &&
                            abs(totalX) >= size.width * .12f
                        ) {
                            val modes = VideoScaleMode.entries
                            val currentIndex = modes.indexOf(videoScaleMode)
                            val direction = if (totalX > 0f) 1 else -1
                            val selected = modes[
                                (currentIndex + direction)
                                    .coerceIn(0, modes.lastIndex)
                            ]
                            videoScaleMode = selected
                            playerPreferences.edit()
                                .putString(
                                    "video_scale_mode",
                                    selected.storageValue,
                                )
                                .apply()
                            showGestureFeedback(selected.label)
                        }
                        mode = null
                    },
                ) { change, dragAmount ->
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    if (mode == null) {
                        val appearance = latestSubtitleAppearance
                        val subtitleGestureZone =
                            latestActiveSubtitleText.isNotBlank() &&
                                if (appearance.verticalWriting) {
                                    startX >= size.width * .55f &&
                                        startX <= size.width * .82f &&
                                        startY >= size.height * .12f &&
                                        startY <= size.height * .88f
                                } else {
                                    startY >= size.height * .52f &&
                                        startY <= size.height * .70f &&
                                        startX >= size.width * .16f &&
                                        startX <= size.width * .84f
                                }
                        val volumeGestureZone =
                            abs(totalY) > abs(totalX) &&
                                startX >= size.width * .88f
                        val brightnessGestureZone =
                            abs(totalY) > abs(totalX) &&
                                startX <= size.width * .12f
                        mode = when {
                            subtitleGestureZone ->
                                PlayerGestureMode.SUBTITLE_POSITION
                            startY >= size.height * .78f &&
                                abs(totalX) > abs(totalY) ->
                                PlayerGestureMode.SEEK
                            startY <= size.height * .28f &&
                                abs(totalX) > abs(totalY) ->
                                PlayerGestureMode.SCALE
                            volumeGestureZone ->
                                PlayerGestureMode.VOLUME
                            brightnessGestureZone ->
                                PlayerGestureMode.BRIGHTNESS
                            else -> null
                        }
                    }
                    when (mode) {
                        PlayerGestureMode.VOLUME -> {
                            change.consume()
                            val maxVolume = audioManager.getStreamMaxVolume(
                                AudioManager.STREAM_MUSIC,
                            ).coerceAtLeast(1)
                            val volume = (
                                startVolume -
                                    totalY / size.height * maxVolume * 1.5f
                                ).roundToInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                volume,
                                0,
                            )
                            showGestureFeedback(
                                "볼륨 ${volume * 100 / maxVolume}%",
                            )
                        }
                        PlayerGestureMode.BRIGHTNESS -> {
                            change.consume()
                            val brightness = (
                                startBrightness -
                                    totalY / size.height * 1.5f
                                ).coerceIn(.01f, 1f)
                            val brightnessPercent = brightness * 100f
                            updateVideoScreenSettings(
                                latestVideoScreenSettings.copy(
                                    brightness = brightnessPercent,
                                ),
                            )
                            applyPlaybackWindowBrightness(
                                activity,
                                brightnessPercent,
                            )
                            showGestureFeedback(
                                "밝기 ${brightnessPercent.roundToInt()}%",
                            )
                        }
                        PlayerGestureMode.SEEK -> {
                            change.consume()
                            val durationMs = player.duration
                                .takeIf { it > 0 && it != C.TIME_UNSET }
                                ?: source.durationMs
                            val seekDeltaMs = (
                                totalX / size.width * 600_000f
                                ).toLong()
                            seekTargetMs = (startPositionMs + seekDeltaMs)
                                .coerceIn(0L, durationMs.coerceAtLeast(0L))
                            val sign = if (seekDeltaMs >= 0) "+" else "−"
                            showGestureFeedback(
                                "$sign${formatGestureTime(abs(seekDeltaMs))}  " +
                                    formatGestureTime(seekTargetMs),
                            )
                        }
                        PlayerGestureMode.SCALE -> {
                            change.consume()
                            showGestureFeedback(
                                if (totalX >= 0f) {
                                    "화면 크기 확대 방향"
                                } else {
                                    "화면 크기 축소 방향"
                                },
                            )
                        }
                        PlayerGestureMode.SUBTITLE_POSITION -> {
                            change.consume()
                            val horizontalOffset = (
                                startSubtitleHorizontalOffset +
                                    (totalX / size.width * 100f).roundToInt()
                                ).coerceIn(-100, 100)
                            val verticalOffset = (
                                startSubtitleVerticalOffset +
                                    (totalY / size.height * 100f).roundToInt()
                                ).coerceIn(-100, 100)
                            latestUpdateSubtitleAppearance(
                                latestSubtitleAppearance.copy(
                                    horizontalOffsetPercent = horizontalOffset,
                                    verticalOffsetPercent = verticalOffset,
                                ),
                            )
                            showGestureFeedback(
                                "자막 위치 가로 ${formatSubtitleOffset(horizontalOffset)}  " +
                                    "세로 ${formatSubtitleOffset(verticalOffset)}",
                            )
                        }
                        null -> Unit
                    }
                }
            }
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                (
                    LayoutInflater.from(viewContext).inflate(
                        R.layout.player_view_texture,
                        null,
                        false,
                    ) as PlayerView
                    ).apply {
                    playerViewHandle = this
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnKeyListener { _, _, keyEvent ->
                        handlePlayerRemoteKey(keyEvent)
                    }
                    useController = true
                    controllerShowTimeoutMs = 7_000
                    controllerAutoShow = false
                    setEnableComposeSurfaceSyncWorkaround(true)
                    applyVideoScaleMode(this, videoScaleMode)
                    applyVideoScreenSettings(this, videoScreenSettings)
                    keepScreenOn = true
                    this.player = player
                    applySubtitleStyle(
                        this,
                        subtitleTypeface,
                        subtitleAppearance,
                        useNativeSubtitleRenderer,
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                            if (visibility == View.VISIBLE) {
                                post { configureEpisodeNavigation(this) }
                            }
                        },
                    )
                    configureEpisodeNavigation(this)
                    findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                        ?.visibility = View.GONE
                    post {
                        requestFocus()
                        configureEpisodeNavigation(this)
                        hideController()
                    }
                }
            },
            update = {
                playerViewHandle = it
                it.player = player
                it.setOnKeyListener { _, _, keyEvent ->
                    handlePlayerRemoteKey(keyEvent)
                }
                configureEpisodeNavigation(it)
                applyVideoScaleMode(it, videoScaleMode)
                applyVideoScreenSettings(it, videoScreenSettings)
                it.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                    ?.visibility = View.GONE
                applySubtitleStyle(
                    it,
                    subtitleTypeface,
                    subtitleAppearance,
                    useNativeSubtitleRenderer,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        HorizontalSubtitleOverlay(
            text = activeSubtitleText,
            typeface = subtitleTypeface,
            appearance = subtitleAppearance,
            modifier = Modifier.fillMaxSize(),
        )
        VerticalSubtitleOverlay(
            text = activeSubtitleText,
            typeface = subtitleTypeface,
            appearance = subtitleAppearance,
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = controllerVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerControlButton(
                icon = Icons.Rounded.Close,
                contentDescription = "재생 화면 닫기",
                onClick = {
                    player.pause()
                    onProgress(
                        source,
                        player.currentPosition.coerceAtLeast(0),
                        "paused",
                    )
                    onClose()
                },
            )
        }
        AnimatedVisibility(
            visible = controllerVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerControlButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "재생 설정",
                onClick = {
                    playerSettingsPage = PlayerSettingsPage.MAIN
                    playerSettingsVisible = true
                },
            )
        }
        AnimatedVisibility(
            visible = controllerVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 96.dp, vertical = 14.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = .68f),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 18.dp,
                        vertical = 10.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = source.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    source.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = .78f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (hasPreviousPlayback && !previousPlaybackTitle.isNullOrBlank()) {
                        Text(
                            text = "이전: $previousPlaybackTitle",
                            color = Color.White.copy(alpha = .72f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (hasNextPlayback && !nextPlaybackTitle.isNullOrBlank()) {
                        Text(
                            text = "다음화: $nextPlaybackTitle",
                            color = PlayerMenuFocusColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = gestureFeedback != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = .72f),
            ) {
                Text(
                    text = gestureFeedback.orEmpty(),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                )
            }
        }
    }

    if (playerSettingsVisible) {
        val picturePreviewVisible =
            playerSettingsPage == PlayerSettingsPage.DISPLAY ||
                playerSettingsPage == PlayerSettingsPage.DISPLAY_ADVANCED
        AlertDialog(
            modifier = Modifier
                .focusRequester(playerSettingsFocusRequester)
                .onPreviewKeyEvent {
                    handlePlayerSettingsKeyEvent(it.nativeKeyEvent)
                }
                .focusable(),
            onDismissRequest = {
                playerSettingsVisible = false
                playerSettingsPage = PlayerSettingsPage.MAIN
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
                decorFitsSystemWindows = false,
            ),
            containerColor = if (picturePreviewVisible) {
                Color.Black.copy(alpha = .82f)
            } else {
                Color.Black
            },
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(playerSettingsPage.title) },
            text = {
                PictureSettingsPreviewWindowEffect(
                    enabled = picturePreviewVisible,
                )
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (playerSettingsPage) {
                        PlayerSettingsPage.MAIN -> {
                            PlaybackFilePathPanel(
                                filePath = source.filePath
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "파일 경로 정보 없음",
                            )
                            AudioOptionRow(
                                label = if (autoPlayNext) {
                                    "다음화 자동재생: 켬"
                                } else {
                                    "다음화 자동재생: 끔"
                                },
                                selected = autoPlayNext,
                                onClick = {
                                    onAutoPlayNextChanged(!autoPlayNext)
                                },
                            )
                            Text(
                                text = if (hasNextPlayback) {
                                    nextPlaybackTitle?.let {
                                        "현재 영상이 끝나면 다음화 '$it'을 자동 재생할 수 있습니다."
                                    } ?: "현재 영상이 끝나면 다음화를 자동 재생할 수 있습니다."
                                } else {
                                    "같은 시즌의 다음화가 있을 때 자동재생이 적용됩니다."
                                },
                                color = Color.Gray,
                            )
                            PlayerSettingsPage.entries
                                .filter {
                                    it != PlayerSettingsPage.MAIN &&
                                        it != PlayerSettingsPage.DISPLAY_ADVANCED
                                }
                                .forEach { page ->
                                    SettingsMenuButton(
                                        label = page.title,
                                        onClick = { playerSettingsPage = page },
                                    )
                                }
                            Text(
                                text = "리모컨·키보드 공통: 확인 버튼 한 번은 " +
                                    "재생 컨트롤을 열고 선택한 정지·재생·앞·뒤를 실행, " +
                                    "확인 버튼 길게 누르기와 " +
                                    "상하 버튼 길게 누르기는 이 화면 열기, " +
                                    "좌우는 10초 이동하며 길게 누르면 연속 탐색, " +
                                    "다음 트랙 버튼은 다음화, 상하는 재생 컨트롤 표시, " +
                                    "설정·메뉴 버튼도 이 화면을 엽니다.",
                                color = Color.Gray,
                            )
                        }
                        PlayerSettingsPage.SUBTITLE -> {
                            SubtitleSettings(
                                player = player,
                                subtitleTracks = subtitleTracks,
                                sourceSubtitles = source.subtitles,
                                automaticSubtitleIndex =
                                    defaultSubtitleIndex(source.subtitles),
                                manualSubtitleIndex = manualSubtitleIndex,
                                subtitleFont = subtitleFont,
                                customFontAvailable =
                                    customSubtitleFontFile.exists(),
                                customFontDisplayName = customFontDisplayName,
                                onSubtitleFontChanged = { selected ->
                                    if (
                                        selected == SubtitleFontOption.CUSTOM &&
                                        !customSubtitleFontFile.exists()
                                    ) {
                                        customFontPicker.launch(arrayOf("*/*"))
                                    } else {
                                        subtitleFont = selected
                                        playerPreferences.edit()
                                            .putString(
                                                "subtitle_font",
                                                selected.storageValue,
                                            )
                                            .apply()
                                    }
                                },
                                onPickCustomFont = {
                                    customFontPicker.launch(arrayOf("*/*"))
                                },
                                appearance = subtitleAppearance,
                                onAppearanceChanged = ::updateSubtitleAppearance,
                                onManualSubtitleIndexChanged = {
                                    manualSubtitleIndex = it
                                    manualSubtitleText = ""
                                },
                                onTracksChanged = {
                                    playerSubtitleText = ""
                                    useNativeSubtitleRenderer = false
                                    tracksRevision++
                                },
                            )
                        }
                        PlayerSettingsPage.SPEED -> {
                            PLAYBACK_SPEEDS.chunked(4).forEach { rowSpeeds ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(4.dp),
                                ) {
                                    rowSpeeds.forEach { speed ->
                                        FilterChip(
                                            selected = playbackSpeed == speed,
                                            onClick = {
                                                playbackSpeed = speed
                                                player.setPlaybackSpeed(speed)
                                                playerPreferences.edit()
                                                    .putFloat(
                                                        "playback_speed",
                                                        speed,
                                                    )
                                                    .apply()
                                            },
                                            label = { Text(formatSpeed(speed)) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(4 - rowSpeeds.size) {
                                        Box(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        PlayerSettingsPage.DISPLAY -> {
                            Text(
                                text = "실시간 미리보기 · 뒤의 영상을 보면서 조절하세요.",
                                color = PlayerMenuFocusColor,
                            )
                            Text("화면 모드 선택")
                            PictureMode.entries
                                .filter { it != PictureMode.CUSTOM }
                                .forEach { mode ->
                                    AudioOptionRow(
                                        label = "${mode.label} · ${mode.description}",
                                        selected =
                                            mode == videoScreenSettings.pictureMode,
                                        onClick = {
                                            updateVideoScreenSettings(
                                                videoScreenSettings.applyPictureMode(mode),
                                            )
                                        },
                                    )
                                }
                            if (videoScreenSettings.pictureMode == PictureMode.CUSTOM) {
                                Text(
                                    text = "현재 모드 · 사용자 설정",
                                    color = PlayerMenuFocusColor,
                                )
                            }

                            SettingsMenuButton(
                                label = "고급 화면 설정",
                                onClick = {
                                    playerSettingsPage =
                                        PlayerSettingsPage.DISPLAY_ADVANCED
                                },
                            )

                            Text("화면 크기")
                            VideoScaleMode.entries.forEach { mode ->
                                AudioOptionRow(
                                    label = mode.label,
                                    selected = mode == videoScaleMode,
                                    onClick = {
                                        videoScaleMode = mode
                                        playerPreferences.edit()
                                            .putString(
                                                "video_scale_mode",
                                                mode.storageValue,
                                            )
                                            .apply()
                                    },
                                )
                            }

                            Text("개별 화면 설정 저장")
                            val presetRevision = videoScreenPresetRevision
                            repeat(3) { presetIndex ->
                                val slot = presetIndex + 1
                                val presetSaved = remember(slot, presetRevision) {
                                    isVideoScreenPresetAvailable(slot)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PlayerMenuActionButton(
                                        label = "설정 $slot 저장",
                                        onClick = {
                                            val saved = saveVideoScreenPreset(
                                                slot,
                                                videoScreenSettings,
                                            )
                                            if (saved) videoScreenPresetRevision++
                                            Toast.makeText(
                                                context,
                                                if (saved) {
                                                    "화면 설정 $slot 번에 저장했습니다."
                                                } else {
                                                    "화면 설정 $slot 저장에 실패했습니다."
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    PlayerMenuActionButton(
                                        label = if (presetSaved) {
                                            "설정 $slot 불러오기 ✓"
                                        } else {
                                            "설정 $slot 비어 있음"
                                        },
                                        onClick = {
                                            val saved = loadVideoScreenPreset(slot)
                                            if (saved == null) {
                                                Toast.makeText(
                                                    context,
                                                    "화면 설정 $slot 번은 비어 있습니다.",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                videoScaleMode = saved.second
                                                playerPreferences.edit()
                                                    .putString(
                                                        "video_scale_mode",
                                                        saved.second.storageValue,
                                                    )
                                                    .apply()
                                                updateVideoScreenSettings(saved.first)
                                                Toast.makeText(
                                                    context,
                                                    "화면 설정 $slot 번을 불러왔습니다.",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            PlayerMenuActionButton(
                                label = "화면 설정 초기화",
                                onClick = {
                                    videoScaleMode = VideoScaleMode.FIT
                                    playerPreferences.edit()
                                        .putString(
                                            "video_scale_mode",
                                            VideoScaleMode.FIT.storageValue,
                                        )
                                        .apply()
                                    updateVideoScreenSettings(
                                        VideoScreenSettings(),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "LG webOS처럼 화면 모드를 먼저 선택하고, " +
                                    "고급 화면 설정에서 세부 값을 조정할 수 있습니다. " +
                                    "변경 사항은 즉시 적용·자동 저장됩니다. 설정 1·2·3은 " +
                                    "각각 독립적으로 저장하고 불러올 수 있습니다.",
                                color = Color.Gray,
                            )
                        }
                        PlayerSettingsPage.DISPLAY_ADVANCED -> {
                            val advancedFocusRequesters = remember {
                                List(6) { FocusRequester() }
                            }
                            Text(
                                text = "현재 화면 모드 · " +
                                    videoScreenSettings.pictureMode.label,
                                color = PlayerMenuFocusColor,
                            )
                            Text(
                                text = "슬라이더를 움직이면 영상에 바로 반영됩니다.",
                                color = Color.White.copy(alpha = .78f),
                            )
                            WebOsPictureSlider(
                                label = "밝기",
                                value = videoScreenSettings.pictureBrightness + 50f,
                                onValueChange = {
                                    updateVideoScreenSettings(
                                        videoScreenSettings.copy(
                                            pictureMode = PictureMode.CUSTOM,
                                            pictureBrightness = it - 50f,
                                        ),
                                    )
                                },
                                focusRequester = advancedFocusRequesters[0],
                                down = advancedFocusRequesters[1],
                            )
                            WebOsPictureSlider(
                                label = "명암 · 기기 방식",
                                value = videoScreenSettings.pictureContrast + 50f,
                                onValueChange = {
                                    updateVideoScreenSettings(
                                        videoScreenSettings.copy(
                                            pictureMode = PictureMode.CUSTOM,
                                            pictureContrast = it - 50f,
                                        ),
                                    )
                                },
                                focusRequester = advancedFocusRequesters[1],
                                up = advancedFocusRequesters[0],
                                down = advancedFocusRequesters[2],
                            )
                            Text(
                                text = "검은 영역은 유지하고 밝은 영역의 출력 레벨을 " +
                                    "조절합니다.",
                                color = Color.Gray,
                            )
                            WebOsPictureSlider(
                                label = "블랙 레벨",
                                value = videoScreenSettings.pictureBlackLevel + 50f,
                                onValueChange = {
                                    updateVideoScreenSettings(
                                        videoScreenSettings.copy(
                                            pictureMode = PictureMode.CUSTOM,
                                            pictureBlackLevel = it - 50f,
                                        ),
                                    )
                                },
                                focusRequester = advancedFocusRequesters[2],
                                up = advancedFocusRequesters[1],
                                down = advancedFocusRequesters[3],
                            )
                            WebOsPictureSlider(
                                label = "색 농도",
                                value = videoScreenSettings.pictureColorDepth + 50f,
                                onValueChange = {
                                    updateVideoScreenSettings(
                                        videoScreenSettings.copy(
                                            pictureMode = PictureMode.CUSTOM,
                                            pictureColorDepth = it - 50f,
                                        ),
                                    )
                                },
                                focusRequester = advancedFocusRequesters[3],
                                up = advancedFocusRequesters[2],
                                down = advancedFocusRequesters[4],
                            )
                            WebOsPictureSlider(
                                label = "색 온도",
                                value =
                                    videoScreenSettings.pictureColorTemperature + 50f,
                                onValueChange = {
                                    updateVideoScreenSettings(
                                        videoScreenSettings.copy(
                                            pictureMode = PictureMode.CUSTOM,
                                            pictureColorTemperature = it - 50f,
                                        ),
                                    )
                                },
                                focusRequester = advancedFocusRequesters[4],
                                up = advancedFocusRequesters[3],
                                down = advancedFocusRequesters[5],
                            )
                            Text(
                                text = "색 온도는 0에 가까울수록 따뜻하고, " +
                                    "100에 가까울수록 차갑게 표시됩니다.",
                                color = Color.Gray,
                            )
                            PlayerMenuActionButton(
                                label = "고급 설정 초기화",
                                onClick = {
                                    updateVideoScreenSettings(
                                        videoScreenSettings.applyPictureMode(
                                            PictureMode.STANDARD,
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                focusRequester = advancedFocusRequesters[5],
                                up = advancedFocusRequesters[4],
                            )
                        }
                        PlayerSettingsPage.AUDIO -> {
                            val hasAudioOverride =
                                player.trackSelectionParameters.overrides.keys.any {
                                    it.type == C.TRACK_TYPE_AUDIO
                                }
                            AudioOptionRow(
                                label = "자동",
                                selected = !hasAudioOverride,
                                onClick = {
                                    player.trackSelectionParameters =
                                        player.trackSelectionParameters.buildUpon()
                                            .clearOverridesOfType(
                                                C.TRACK_TYPE_AUDIO,
                                            )
                                            .build()
                                    tracksRevision++
                                },
                            )
                            if (audioTracks.isEmpty()) {
                                Text(
                                    text = "선택 가능한 오디오 트랙이 없습니다.",
                                    color = Color.Gray,
                                )
                            } else {
                                audioTracks.forEach { option ->
                                    AudioOptionRow(
                                        label = option.label,
                                        selected =
                                            hasAudioOverride && option.selected,
                                        onClick = {
                                            player.trackSelectionParameters =
                                                player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(
                                                            option.group,
                                                            option.trackIndex,
                                                        ),
                                                    )
                                                    .build()
                                            tracksRevision++
                                        },
                                    )
                                }
                            }
                        }
                        PlayerSettingsPage.QUALITY -> {
                            PlaybackQuality.entries.forEach { quality ->
                                AudioOptionRow(
                                    label = if (
                                        quality == PlaybackQuality.ORIGINAL
                                    ) {
                                        "원본 직접 재생"
                                    } else {
                                        quality.maxBitrateKbps?.let {
                                            "${quality.label} · " +
                                                "최대 ${it / 1000} Mbps"
                                        } ?: quality.label
                                    },
                                    selected = quality == playbackQuality,
                                    onClick = {
                                        onPlaybackQualityChanged(
                                            quality,
                                            player.currentPosition
                                                .coerceAtLeast(0),
                                        )
                                    },
                                )
                            }
                            Text(
                                text = "기본값은 서버 변환 없는 원본 직접 재생입니다. " +
                                    "해상도를 직접 선택한 경우에만 품질 변환하며, " +
                                    "현재 재생 위치에서 바로 적용됩니다.",
                                color = Color.Gray,
                            )
                        }
                        PlayerSettingsPage.OPTIMIZATION -> {
                            Text(
                                text = "감지된 기기: " +
                                    "${deviceCapabilities.formFactor.label} · " +
                                    "${deviceCapabilities.tier.label}",
                                color = PlayerMenuFocusColor,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "화면 " +
                                    "${deviceCapabilities.screenWidthPixels}×" +
                                    "${deviceCapabilities.screenHeightPixels} · " +
                                    "RAM ${String.format(Locale.US, "%.1f", deviceCapabilities.totalMemoryGb)} GB · " +
                                    "여유 ${String.format(Locale.US, "%.1f", deviceCapabilities.availableMemoryGb)} GB · " +
                                    "앱 한도 ${deviceCapabilities.appMemoryLimitMb} MB · " +
                                    "CPU ${deviceCapabilities.cpuCores}코어",
                                color = Color.White,
                            )
                            Text(
                                text = "현재 영상 적용 프로필: " +
                                    "${activePlaybackOptimizationProfile.tier.label} · " +
                                    "최대 ${activePlaybackOptimizationProfile.maxVideoWidth}×" +
                                    "${activePlaybackOptimizationProfile.maxVideoHeight}",
                                color = Color.White,
                            )
                            DeviceOptimizationMode.entries.forEach { mode ->
                                AudioOptionRow(
                                    label = mode.label,
                                    selected = mode == deviceOptimizationMode,
                                    onClick = {
                                        deviceOptimizationMode = mode
                                        playerPreferences.edit()
                                            .putString(
                                                "device_optimization_mode",
                                                mode.storageValue,
                                            )
                                            .apply()
                                        Toast.makeText(
                                            context,
                                            "최적화 설정을 저장했습니다. 다음 영상부터 적용됩니다.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                                if (mode == deviceOptimizationMode) {
                                    Text(
                                        text = mode.description,
                                        color = Color.Gray,
                                    )
                                }
                            }
                            Text(
                                text = "자동 최적화는 제조사나 특정 모델명이 아니라 " +
                                    "실제 화면 크기, 휴대폰·태블릿·TV/OTT 구분, " +
                                    "메모리 상태와 CPU를 사용합니다. 저사양 기기는 " +
                                    "버퍼를 줄이고 고해상도 기기는 실제 화면 범위에서 " +
                                    "화질을 선택합니다. 원본 직접 재생은 서버 변환을 " +
                                    "강제로 실행하지 않습니다.",
                                color = Color.Gray,
                            )
                        }
                        PlayerSettingsPage.STORAGE -> {
                            PlayerMenuActionButton(
                                label = "캐시 삭제",
                                onClick = {
                                    val cleared = runCatching {
                                        context.cacheDir.listFiles()?.forEach {
                                            it.deleteRecursively()
                                        }
                                    }.isSuccess
                                    Toast.makeText(
                                        context,
                                        if (cleared) "캐시를 삭제했습니다."
                                        else "캐시 삭제에 실패했습니다.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            Text(
                                text = "임시 이미지와 재생 캐시를 삭제합니다.",
                                color = Color.Gray,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                PlayerMenuActionButton(
                    label = if (playerSettingsPage == PlayerSettingsPage.MAIN) {
                        "닫기"
                    } else {
                        "뒤로"
                    },
                    onClick = {
                        closeOrStepBackPlayerSettings()
                    },
                )
            },
        )
    }
}

@Composable
private fun PictureSettingsPreviewWindowEffect(
    enabled: Boolean,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view, enabled) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousDimAmount = window?.attributes?.dimAmount ?: 0f
        val hadDimFlag = window?.attributes?.flags?.let { flags ->
            flags.and(WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
        } ?: false

        if (enabled) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0f)
        }

        onDispose {
            if (enabled && window != null) {
                window.setDimAmount(previousDimAmount)
                if (hadDimFlag) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
            }
        }
    }
}

@Composable
private fun PlaybackFilePathPanel(
    filePath: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF111111),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PlayerMenuIdleBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "재생 파일 경로",
                color = Color.Gray,
            )
            Text(
                text = filePath,
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsMenuButton(
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (focused) {
                PlayerMenuFocusBackground
            } else {
                Color.Transparent
            },
            contentColor = Color.White,
        ),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PlayerMenuFocusColor else PlayerMenuIdleBorder,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = "$label  ›",
            color = Color.White,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PlayerMenuActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var focusModifier = modifier.fillMaxWidth()
    if (focusRequester != null) {
        focusModifier = focusModifier.focusRequester(focusRequester)
    }
    OutlinedButton(
        onClick = onClick,
        modifier = focusModifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent {
                handleDpadFocusMove(
                    it.nativeKeyEvent,
                    up = up,
                    down = down,
                )
            },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (focused) {
                PlayerMenuFocusBackground
            } else {
                Color.Transparent
            },
            contentColor = Color.White,
        ),
        border = BorderStroke(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) PlayerMenuFocusColor else PlayerMenuIdleBorder,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(UnstableApi::class)
private fun applyVideoScaleMode(
    playerView: PlayerView,
    mode: VideoScaleMode,
) {
    playerView.setResizeMode(mode.resizeMode)
    val surfaceView = playerView.videoSurfaceView
    surfaceView?.scaleX = mode.scaleX
    surfaceView?.scaleY = mode.scaleY
    surfaceView?.requestLayout()
    surfaceView?.invalidate()
    playerView.requestLayout()
    playerView.invalidate()
    playerView.post {
        playerView.setResizeMode(mode.resizeMode)
        playerView.videoSurfaceView?.requestLayout()
    }
}

private fun applyVideoScreenSettings(
    playerView: PlayerView,
    settings: VideoScreenSettings,
) {
    val videoSurface = playerView.videoSurfaceView ?: return
    val colorPaint = buildVideoLayerPaint(settings)
    if (colorPaint == null) {
        videoSurface.setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        videoSurface.setLayerType(View.LAYER_TYPE_HARDWARE, colorPaint)
    }
    videoSurface.invalidate()
}

private fun buildVideoLayerPaint(
    settings: VideoScreenSettings,
): Paint? {
    val pictureBrightness = settings.pictureBrightness.coerceIn(-50f, 50f)
    val pictureContrast = settings.pictureContrast.coerceIn(-50f, 50f)
    val pictureBlackLevel = settings.pictureBlackLevel.coerceIn(-50f, 50f)
    val pictureColorDepth = settings.pictureColorDepth.coerceIn(-50f, 50f)
    val pictureColorTemperature =
        settings.pictureColorTemperature.coerceIn(-50f, 50f)
    if (
        pictureBrightness == 0f &&
        pictureContrast == 0f &&
        pictureBlackLevel == 0f &&
        pictureColorDepth == 0f &&
        pictureColorTemperature == 0f
    ) {
        return null
    }

    // TV 기기의 명암 조절처럼 블랙 기준을 이동시키지 않고 밝은 영역의
    // 출력 레벨만 바꾼다.
    val brightnessScale = 1f + pictureBrightness / 100f
    val userContrastScale = 1f + pictureContrast / 100f
    val contrastScale = userContrastScale
    val saturationScale = 1f + pictureColorDepth / 100f
    val temperature = pictureColorTemperature / 50f
    val redScale =
        (1f - temperature * .12f).coerceIn(.72f, 1.28f)
    val greenScale =
        (1f - abs(temperature) * .025f).coerceIn(.9f, 1.1f)
    val blueScale =
        (1f + temperature * .16f).coerceIn(.7f, 1.34f)
    val blackLevelOffset = pictureBlackLevel * .4f
    val colorMatrix = ColorMatrix()
    colorMatrix.setSaturation(saturationScale)
    val translate = blackLevelOffset
    val comfortMatrix = ColorMatrix(
        floatArrayOf(
            contrastScale * brightnessScale * redScale, 0f, 0f, 0f,
            translate,
            0f, contrastScale * brightnessScale * greenScale, 0f, 0f,
            translate,
            0f, 0f, contrastScale * brightnessScale * blueScale, 0f,
            translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    colorMatrix.postConcat(comfortMatrix)
    return Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
}

private fun selectDefaultSubtitleIfNeeded(
    player: ExoPlayer,
    tracks: Tracks,
) {
    val parameters = player.trackSelectionParameters
    if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes) return
    if (parameters.overrides.keys.any { it.type == C.TRACK_TYPE_TEXT }) return
    val textGroups = tracks.groups.filter {
        it.type == C.TRACK_TYPE_TEXT && it.length > 0
    }
    val koreanTrack = textGroups.firstNotNullOfOrNull { group ->
        (0 until group.length).firstOrNull { index ->
            val format = group.getTrackFormat(index)
            isKoreanSubtitle(format.language, format.label)
        }?.let { index -> group to index }
    }
    val selectedTrack = koreanTrack
        ?: textGroups.firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull(group::isTrackSelected)
                ?.let { index -> group to index }
        }
        ?: textGroups.firstOrNull()?.let { group -> group to 0 }
        ?: return
    if (selectedTrack.first.isTrackSelected(selectedTrack.second)) {
        return
    }
    player.trackSelectionParameters = parameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(
            TrackSelectionOverride(
                selectedTrack.first.mediaTrackGroup,
                selectedTrack.second,
            ),
        )
        .build()
}

private fun buildMediaItem(
    url: String,
    source: PlaybackSource,
): MediaItem {
    val defaultSubtitleIndex = defaultSubtitleIndex(source.subtitles)
    val subtitleConfigurations =
        source.subtitles.mapIndexedNotNull { index, subtitle ->
            if (!subtitle.isExoSidecarSubtitle()) {
                return@mapIndexedNotNull null
            }
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setLabel(externalSubtitleLabel(index, subtitle.label))
                .setSelectionFlags(
                    if (index == defaultSubtitleIndex) {
                        C.SELECTION_FLAG_DEFAULT
                    } else {
                        0
                    },
                )
                .build()
        }
    return MediaItem.Builder()
        .setUri(url)
        .setSubtitleConfigurations(subtitleConfigurations)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(source.title)
                .setSubtitle(source.subtitle)
                .build(),
        )
        .build()
}

private const val MaxPlaybackReconnectAttempts = 2

private fun PlaybackException.isRetryableConnectionFailure(): Boolean =
    errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

private fun String.isPlexTranscodeStream(): Boolean =
    contains("/video/:/transcode/", ignoreCase = true)

private fun defaultSubtitleIndex(subtitles: List<PlaybackSubtitle>): Int? =
    subtitles.indices
        .filter { subtitles[it].isManualTextSubtitle() }
        .let { textIndices ->
            textIndices.firstOrNull {
                isKoreanSubtitle(
                    subtitles[it].language,
                    subtitles[it].label,
                )
            }
                ?: textIndices.firstOrNull { subtitles[it].selected }
                ?: textIndices.firstOrNull()
        }

private fun externalSubtitleLabel(index: Int, label: String): String =
    "$ExternalSubtitleIndexedLabelPrefix$index · $label"

private fun externalSubtitleIndexFromLabel(label: String): Int? =
    ExternalSubtitleIndexRegex.find(label)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

private fun displaySubtitleTrackLabel(sourceLabel: String): String =
    ExternalSubtitleIndexRegex.replace(sourceLabel, "")
        .removePrefix(ExternalSubtitleLabelPrefix)

private fun PlaybackSubtitle.normalizedCodec(): String =
    codec?.trim()?.lowercase(Locale.ROOT).orEmpty()

internal fun PlaybackSubtitle.isManualTextSubtitle(): Boolean {
    val normalizedCodec = normalizedCodec()
    val normalizedMimeType = mimeType.lowercase(Locale.ROOT)
    return normalizedCodec in setOf(
        "srt",
        "subrip",
        "ass",
        "ssa",
        "vtt",
        "webvtt",
        "ttml",
        "smi",
        "sami",
        "mov_text",
        "tx3g",
        "ttxt",
        "text",
    ) ||
        normalizedMimeType.contains("subrip") ||
        normalizedMimeType.contains("ssa") ||
        normalizedMimeType.contains("vtt") ||
        normalizedMimeType.contains("ttml")
}

private fun PlaybackSubtitle.isExoSidecarSubtitle(): Boolean =
    normalizedCodec() !in setOf("smi", "sami")

private fun PlaybackSubtitle.codecLabelSuffix(): String =
    normalizedCodec()
        .takeIf { it.isNotBlank() }
        ?.uppercase(Locale.ROOT)
        ?.let { " · $it" }
        .orEmpty()

internal suspend fun loadManualSubtitleCues(
    subtitle: PlaybackSubtitle,
    token: String,
): List<ManualSubtitleCue> =
    withContext(Dispatchers.IO) {
        val candidates = (listOf(subtitle.url) + subtitle.fallbackUrls)
            .distinct()
            .flatMap { url ->
                // Keep the token in a request header first. Some Plex reverse
                // proxies drop custom headers while redirecting subtitle
                // resources, so retry the same private resource with the
                // standard Plex query token before falling back to native VLC.
                listOf(url, url.withPlexSubtitleTokenQuery(token))
            }
            .distinct()
        for (candidate in candidates) {
            val cues = try {
                loadManualSubtitleCuesFromUrl(candidate, subtitle, token)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
            if (cues.isNotEmpty()) return@withContext cues
        }
        emptyList()
    }

internal fun String.withPlexSubtitleTokenQuery(token: String): String {
    if (token.isBlank() || contains("X-Plex-Token=", ignoreCase = true)) return this
    return Uri.parse(this).buildUpon()
        .appendQueryParameter("X-Plex-Token", token)
        .build()
        .toString()
}

private fun loadManualSubtitleCuesFromUrl(
    url: String,
    subtitle: PlaybackSubtitle,
    token: String,
): List<ManualSubtitleCue> {
    val http = URL(url.withPlexSubtitleDownloadHint())
        .openConnection() as HttpURLConnection
    http.connectTimeout = 12_000
    http.readTimeout = 12_000
    http.instanceFollowRedirects = true
    http.useCaches = false
    http.setRequestProperty("X-Plex-Token", token)
    http.setRequestProperty(
        "User-Agent",
        "PlexPlay/${BuildConfig.VERSION_NAME} Android",
    )
    http.setRequestProperty("Accept", "text/*, application/ttml+xml, */*")
    // Plex stream responses routed through some reverse proxies can terminate
    // chunked/gzip transfers early. Request an uncompressed, non-persistent
    // response and retain already-received subtitle bytes if the tail is cut.
    http.setRequestProperty("Accept-Encoding", "identity")
    http.setRequestProperty("Connection", "close")
    try {
        val status = http.responseCode
        if (status !in 200..299) return emptyList()
        if (http.contentLengthLong > MaxSubtitleDownloadBytes) return emptyList()
        val bytes = http.inputStream.use(::readSubtitleBytes)
        if (bytes.isEmpty()) return emptyList()
        val body = decodeSubtitleBytes(bytes)
        if (body.trimStart().startsWith("<html", ignoreCase = true)) return emptyList()
        return parseManualSubtitleCues(body, subtitle)
    } finally {
        http.disconnect()
    }
}

private const val MaxSubtitleDownloadBytes = 8L * 1024L * 1024L

internal fun readSubtitleBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (output.size().toLong() <= MaxSubtitleDownloadBytes) {
        val read = try {
            input.read(buffer)
        } catch (timeout: SocketTimeoutException) {
            if (output.size() > 0) break else throw timeout
        } catch (error: IOException) {
            if (output.size() > 0) break else throw error
        }
        if (read < 0) break
        if (output.size().toLong() + read > MaxSubtitleDownloadBytes) return byteArrayOf()
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

internal fun decodeSubtitleBytes(bytes: ByteArray): String {
    if (bytes.size >= 2) {
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
    }
    if (bytes.size >= 4) {
        var evenZeroes = 0
        var oddZeroes = 0
        var index = 0
        while (index < bytes.size) {
            if (bytes[index] == 0.toByte()) evenZeroes++
            if (index + 1 < bytes.size && bytes[index + 1] == 0.toByte()) oddZeroes++
            index += 2
        }
        val pairs = bytes.size / 2
        if (oddZeroes > pairs / 3) return bytes.toString(Charsets.UTF_16LE)
        if (evenZeroes > pairs / 3) return bytes.toString(Charsets.UTF_16BE)
    }
    val utf8 = bytes.toString(Charsets.UTF_8)
    if ('\uFFFD' !in utf8) return utf8
    return runCatching {
        bytes.toString(Charset.forName("MS949"))
    }.getOrDefault(utf8)
}

private fun String.withPlexSubtitleDownloadHint(): String {
    if (!contains("/library/streams/", ignoreCase = true)) return this
    if (contains("?download=") || contains("&download=")) return this
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}download=1"
}

internal fun List<ManualSubtitleCue>.textAt(positionMs: Long): String =
    asSequence()
        .filter { positionMs >= it.startMs && positionMs < it.endMs }
        .map { it.text }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

internal fun parseManualSubtitleCues(
    body: String,
    subtitle: PlaybackSubtitle,
): List<ManualSubtitleCue> {
    val normalized = body
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    // Plex's embedded-subtitle endpoint can convert ASS/SSA/TTML/SMI to SRT
    // while the metadata still reports the original codec. Detect the actual
    // response body first so converted text is not sent to the wrong parser.
    return when {
        normalized.contains("[Events]", ignoreCase = true) ->
            parseAssSubtitleCues(normalized)
        normalized.contains("<SAMI", ignoreCase = true) ||
            normalized.contains("<SYNC", ignoreCase = true) ->
            parseSamiSubtitleCues(normalized)
        normalized.contains("<tt", ignoreCase = true) &&
            normalized.contains("<p", ignoreCase = true) ->
            parseTtmlSubtitleCues(normalized)
        else -> parseTimedTextSubtitleCues(normalized)
    }
}

private val TtmlParagraphRegex = Regex("""(?is)<p\b([^>]*)>(.*?)</p>""")
private val TtmlTimeAttributeRegex = Regex("""(?i)\b(begin|end|dur)\s*=\s*["']([^"']+)["']""")

private fun parseTtmlSubtitleCues(body: String): List<ManualSubtitleCue> =
    TtmlParagraphRegex.findAll(body).mapNotNull { paragraph ->
        val timing = TtmlTimeAttributeRegex.findAll(paragraph.groupValues[1])
            .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
        val startMs = timing["begin"]?.let(::parseTtmlTimeMs) ?: return@mapNotNull null
        val endMs = timing["end"]?.let(::parseTtmlTimeMs)
            ?: timing["dur"]?.let(::parseTtmlTimeMs)?.let(startMs::plus)
            ?: return@mapNotNull null
        val text = cleanManualSubtitleText(paragraph.groupValues[2])
        if (text.isBlank() || endMs <= startMs) return@mapNotNull null
        ManualSubtitleCue(startMs, endMs, text)
    }.toList()

private fun parseTtmlTimeMs(value: String): Long {
    val normalized = value.trim()
    if (normalized.endsWith("ms", ignoreCase = true)) {
        return normalized.dropLast(2).toDoubleOrNull()?.toLong() ?: 0L
    }
    if (normalized.endsWith("s", ignoreCase = true)) {
        return ((normalized.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000.0).toLong()
    }
    val parts = normalized.split(':')
    if (parts.size != 3) return 0L
    val hours = parts[0].toLongOrNull() ?: 0L
    val minutes = parts[1].toLongOrNull() ?: 0L
    val seconds = parts[2].toDoubleOrNull() ?: 0.0
    return (((hours * 60L + minutes) * 60L) * 1_000L + seconds * 1_000.0).toLong()
}

private val SubtitleTimingRegex = Regex(
    """(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""",
)

private fun parseTimedTextSubtitleCues(body: String): List<ManualSubtitleCue> {
    val normalized = body.replace(Regex("(?m)^WEBVTT.*$"), "")
    val timings = SubtitleTimingRegex.findAll(normalized).toList()
    return timings.mapIndexedNotNull { index, match ->
        val lineEnd = normalized.indexOf('\n', match.range.last + 1)
            .let { if (it < 0) normalized.length else it + 1 }
        val nextTimingStart = timings.getOrNull(index + 1)?.range?.first
            ?: normalized.length
        if (lineEnd > nextTimingStart) return@mapIndexedNotNull null

        val cueLines = normalized.substring(lineEnd, nextTimingStart)
            .lines()
            .toMutableList()
        while (cueLines.lastOrNull()?.isBlank() == true) {
            cueLines.removeAt(cueLines.lastIndex)
        }
        // Broken/hand-authored SRT files often omit the blank line between
        // cues. In that case the next numeric cue identifier belongs to the
        // following timing line, not to the current subtitle text.
        if (
            index + 1 < timings.size &&
            cueLines.lastOrNull()?.trim()?.matches(Regex("\\d+")) == true
        ) {
            cueLines.removeAt(cueLines.lastIndex)
        }
        val text = cleanManualSubtitleText(cueLines.joinToString("\n"))
        if (text.isBlank()) return@mapIndexedNotNull null
        ManualSubtitleCue(
            startMs = match.subtitleTimeMs(1),
            endMs = match.subtitleTimeMs(5),
            text = text,
        )
    }.filter { it.endMs > it.startMs }
}

private fun parseAssSubtitleCues(body: String): List<ManualSubtitleCue> {
    var inEvents = false
    var fields = listOf<String>()
    val cues = mutableListOf<ManualSubtitleCue>()
    body.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.startsWith("[", ignoreCase = true)) {
            inEvents = line.equals("[Events]", ignoreCase = true)
            return@forEach
        }
        if (!inEvents) return@forEach
        if (line.startsWith("Format:", ignoreCase = true)) {
            fields = line.substringAfter(':')
                .split(',')
                .map { it.trim().lowercase(Locale.ROOT) }
            return@forEach
        }
        if (!line.startsWith("Dialogue:", ignoreCase = true) || fields.isEmpty()) {
            return@forEach
        }
        val values = line.substringAfter(':')
            .split(",", limit = fields.size)
        val startIndex = fields.indexOf("start")
        val endIndex = fields.indexOf("end")
        val textIndex = fields.indexOf("text")
        if (
            startIndex !in values.indices ||
            endIndex !in values.indices ||
            textIndex !in values.indices
        ) {
            return@forEach
        }
        val text = cleanManualSubtitleText(values[textIndex])
        if (text.isBlank()) return@forEach
        val startMs = parseAssTimeMs(values[startIndex])
        val endMs = parseAssTimeMs(values[endIndex])
        if (endMs > startMs) {
            cues += ManualSubtitleCue(startMs, endMs, text)
        }
    }
    return cues
}

private val SamiSyncRegex = Regex(
    """(?is)<sync\s+start\s*=\s*["']?(\d+)["']?[^>]*>(.*?)(?=<sync\s+start|\z)""",
)
private val SamiParagraphRegex = Regex("""(?is)<p\b([^>]*)>(.*?)(?=<p\b|\z)""")

private fun parseSamiSubtitleCues(body: String): List<ManualSubtitleCue> {
    val entries = SamiSyncRegex.findAll(body)
        .mapNotNull { match ->
            val startMs = match.groupValues[1].toLongOrNull()
                ?: return@mapNotNull null
            val text = cleanSamiSubtitleText(match.groupValues[2])
            startMs to text
        }
        .filter { it.second.isNotBlank() }
        .toList()
    return entries.mapIndexed { index, entry ->
        val endMs = entries.getOrNull(index + 1)?.first
            ?.coerceAtLeast(entry.first + 1)
            ?: (entry.first + 4_000L)
        ManualSubtitleCue(entry.first, endMs, entry.second)
    }
}

private fun cleanSamiSubtitleText(body: String): String {
    val paragraphs = SamiParagraphRegex.findAll(body).toList()
    if (paragraphs.isEmpty()) return cleanManualSubtitleText(body)
    val koreanParagraphs = paragraphs.filter { match ->
        val attributes = match.groupValues[1].lowercase(Locale.ROOT)
        attributes.contains("krcc") ||
            attributes.contains("ko") ||
            attributes.contains("kor") ||
            attributes.contains("korean") ||
            attributes.contains("한국")
    }
    val selected = (koreanParagraphs.ifEmpty { paragraphs })
        .joinToString("\n") { it.groupValues[2] }
    return cleanManualSubtitleText(selected)
}

private fun MatchResult.subtitleTimeMs(offset: Int): Long {
    val hours = groups[offset]?.value?.toLongOrNull() ?: 0L
    val minutes = groups[offset + 1]?.value?.toLongOrNull() ?: 0L
    val seconds = groups[offset + 2]?.value?.toLongOrNull() ?: 0L
    val millis = groups[offset + 3]
        ?.value
        ?.padEnd(3, '0')
        ?.take(3)
        ?.toLongOrNull()
        ?: 0L
    return ((hours * 60L + minutes) * 60L + seconds) * 1_000L + millis
}

private fun parseAssTimeMs(value: String): Long {
    val parts = value.trim().split(':')
    if (parts.size != 3) return 0L
    val secondsParts = parts[2].split('.')
    val hours = parts[0].toLongOrNull() ?: 0L
    val minutes = parts[1].toLongOrNull() ?: 0L
    val seconds = secondsParts.getOrNull(0)?.toLongOrNull() ?: 0L
    val centiseconds = secondsParts.getOrNull(1)
        ?.padEnd(2, '0')
        ?.take(2)
        ?.toLongOrNull()
        ?: 0L
    return ((hours * 60L + minutes) * 60L + seconds) * 1_000L +
        centiseconds * 10L
}

private fun cleanManualSubtitleText(text: String): String =
    text
        .replace("\\N", "\n")
        .replace("\\n", "\n")
        .replace("\\h", " ")
        .replace(Regex("""\{\\[^}]*}"""), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .decodeNumericHtmlEntities()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

private fun String.decodeNumericHtmlEntities(): String =
    replace(Regex("""&#(x[0-9a-fA-F]+|\d+);""")) { match ->
        val value = match.groupValues[1]
        val codePoint =
            if (value.startsWith("x", ignoreCase = true)) {
                value.drop(1).toIntOrNull(16)
            } else {
                value.toIntOrNull()
            }
        codePoint
            ?.takeIf { Character.isValidCodePoint(it) }
            ?.let { String(Character.toChars(it)) }
            ?: match.value
    }

private fun isKoreanSubtitle(language: String?, label: String?): Boolean {
    val normalizedLanguage = language?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (
        normalizedLanguage == "ko" ||
        normalizedLanguage == "kor" ||
        normalizedLanguage == "kr" ||
        normalizedLanguage.startsWith("ko-") ||
        normalizedLanguage.startsWith("ko_")
    ) {
        return true
    }

    val normalizedLabel = label?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return normalizedLabel.contains("korean") ||
        normalizedLabel.contains("한국어") ||
        normalizedLabel.contains("한국") ||
        normalizedLabel.contains("한글") ||
        normalizedLabel.contains("ko-kr") ||
        normalizedLabel.contains("ko_kr") ||
        normalizedLabel.split(' ', '.', '-', '_', '[', ']', '(', ')')
            .any { it == "ko" || it == "kor" }
}

@Composable
private fun SubtitleSettings(
    player: ExoPlayer,
    subtitleTracks: List<SubtitleTrackOption>,
    sourceSubtitles: List<PlaybackSubtitle>,
    automaticSubtitleIndex: Int?,
    manualSubtitleIndex: Int?,
    subtitleFont: SubtitleFontOption,
    customFontAvailable: Boolean,
    customFontDisplayName: String?,
    onSubtitleFontChanged: (SubtitleFontOption) -> Unit,
    onPickCustomFont: () -> Unit,
    appearance: SubtitleAppearance,
    onAppearanceChanged: (SubtitleAppearance) -> Unit,
    onManualSubtitleIndexChanged: (Int?) -> Unit,
    onTracksChanged: () -> Unit,
) {
    val horizontalPositionFocusRequester = remember { FocusRequester() }
    val verticalPositionFocusRequester = remember { FocusRequester() }
    val resetPositionFocusRequester = remember { FocusRequester() }
    val verticalWritingFocusRequester = remember { FocusRequester() }
    var horizontalPositionFocused by remember { mutableStateOf(false) }
    var verticalPositionFocused by remember { mutableStateOf(false) }
    val textTrackDisabled =
        C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes
    val hasTextOverride =
        player.trackSelectionParameters.overrides.keys.any {
            it.type == C.TRACK_TYPE_TEXT
        }
    val automaticManualSubtitle = automaticSubtitleIndex
        ?.let { sourceSubtitles.getOrNull(it) }
        ?.isManualTextSubtitle() == true
    Text(
        text = "자막 트랙 선택",
        color = Color.White,
        fontWeight = FontWeight.Bold,
    )
    AudioOptionRow(
        label = "끔",
        selected = textTrackDisabled,
        onClick = {
            onManualSubtitleIndexChanged(null)
            player.trackSelectionParameters =
                player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            onTracksChanged()
        },
    )
    AudioOptionRow(
        label = "자동 · 한국어 우선",
        selected = if (automaticManualSubtitle) {
            manualSubtitleIndex == automaticSubtitleIndex
        } else {
            !textTrackDisabled && !hasTextOverride
        },
        onClick = {
            onManualSubtitleIndexChanged(automaticSubtitleIndex)
            player.trackSelectionParameters = if (automaticManualSubtitle) {
                player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            } else {
                player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setPreferredTextLanguages("ko", "kor")
                    .setSelectUndeterminedTextLanguage(true)
                    .build()
            }
            onTracksChanged()
        },
    )
    if (sourceSubtitles.isNotEmpty()) {
        Text(
            text = "Plex 자막 직접 표시 (${sourceSubtitles.size})",
            color = PlayerMenuFocusColor,
            fontWeight = FontWeight.Bold,
        )
        sourceSubtitles.forEachIndexed { index, subtitle ->
            val matchingTrack = subtitleTracks.firstOrNull {
                it.externalSubtitleIndex == index
            }
            val manualTextSubtitle = subtitle.isManualTextSubtitle()
            val selectable = manualTextSubtitle || matchingTrack != null
            AudioOptionRow(
                label = subtitle.label +
                    subtitle.codecLabelSuffix() +
                    when {
                        manualTextSubtitle -> " · 앱 표시 · 사용자 설정 적용"
                        matchingTrack != null -> " · 플레이어 표시"
                        else -> " · 지원하지 않는 자막"
                    },
                selected = manualSubtitleIndex == index,
                onClick = {
                    if (selectable) {
                        onManualSubtitleIndexChanged(index)
                        player.trackSelectionParameters =
                            player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .apply {
                                    if (manualTextSubtitle) {
                                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    } else {
                                        val playerTrack = checkNotNull(matchingTrack)
                                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        setOverrideForType(
                                            TrackSelectionOverride(
                                                playerTrack.group,
                                                playerTrack.trackIndex,
                                            ),
                                        )
                                    }
                                }
                                .build()
                        onTracksChanged()
                    }
                },
            )
        }
    }
    if (subtitleTracks.isEmpty() && sourceSubtitles.isEmpty()) {
        Text(
            text = "이 영상에서 사용할 수 있는 자막이 없습니다.",
            color = Color.Gray,
        )
    } else {
        val embeddedSubtitleTracks =
            subtitleTracks.filterNot { it.isExternal }
        val externalSubtitleTracks =
            subtitleTracks.filter { it.isExternal }
        Text(
            text = "내장 자막 (${embeddedSubtitleTracks.size})",
            color = PlayerMenuFocusColor,
            fontWeight = FontWeight.Bold,
        )
        if (embeddedSubtitleTracks.isEmpty()) {
            Text(
                text = "재생기가 인식한 내장 자막이 없습니다.",
                color = Color.Gray,
            )
        } else {
            embeddedSubtitleTracks.forEach { option ->
                SubtitleTrackRow(
                    player = player,
                    option = option,
                    textTrackDisabled = textTrackDisabled,
                    hasTextOverride = hasTextOverride,
                    onManualSubtitleIndexChanged =
                        onManualSubtitleIndexChanged,
                    onTracksChanged = onTracksChanged,
                )
            }
        }
        Text(
            text = "외부 자막 (${externalSubtitleTracks.size})",
            color = PlayerMenuFocusColor,
            fontWeight = FontWeight.Bold,
        )
        if (externalSubtitleTracks.isEmpty()) {
            Text(
                text = "연결된 외부 자막이 없습니다.",
                color = Color.Gray,
            )
        } else {
            externalSubtitleTracks.forEach { option ->
                SubtitleTrackRow(
                    player = player,
                    option = option,
                    textTrackDisabled = textTrackDisabled,
                    hasTextOverride = hasTextOverride,
                    onManualSubtitleIndexChanged =
                        onManualSubtitleIndexChanged,
                    onTracksChanged = onTracksChanged,
                )
            }
        }
    }
    Text(
        text = "자막 글꼴 선택",
        color = Color.White,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "선택한 글꼴은 텍스트형 내장·외부 자막에 즉시 적용됩니다. " +
            "PGS 같은 그림 자막은 원본 모양으로 표시됩니다.",
        color = Color.Gray,
    )
    SubtitleFontOption.entries.forEach { option ->
        AudioOptionRow(
            label = if (
                option == SubtitleFontOption.CUSTOM
            ) {
                when {
                    !customFontAvailable -> "${option.label} · 파일 선택 필요"
                    !customFontDisplayName.isNullOrBlank() ->
                        "${option.label} · $customFontDisplayName"
                    else -> option.label
                }
            } else {
                option.label
            },
            selected = option == subtitleFont,
            onClick = { onSubtitleFontChanged(option) },
        )
    }
    PlayerMenuActionButton(
        label = if (customFontAvailable) {
            "내 TTF/OTF 폰트 파일 교체"
        } else {
            "내 TTF/OTF 폰트 파일 선택"
        },
        onClick = onPickCustomFont,
    )

    Text("자막 크기")
    SubtitleChoiceChips(
        options = SUBTITLE_SIZE_OPTIONS.map { "$it%" to it },
        selected = appearance.sizePercent,
        onSelect = {
            onAppearanceChanged(appearance.copy(sizePercent = it))
        },
    )

    Text("자막 위치")
    Text(
        text = "가로 ${formatSubtitleOffset(appearance.horizontalOffsetPercent)}",
        color = Color.White,
    )
    Slider(
        value = appearance.horizontalOffsetPercent.toFloat(),
        onValueChange = {
            onAppearanceChanged(
                appearance.copy(horizontalOffsetPercent = it.roundToInt()),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(horizontalPositionFocusRequester)
            .onFocusChanged {
                horizontalPositionFocused = it.isFocused || it.hasFocus
            }
            .onPreviewKeyEvent {
                handleDpadFocusMove(
                    it.nativeKeyEvent,
                    down = verticalPositionFocusRequester,
                )
            },
        colors = playerSettingsSliderColors(horizontalPositionFocused),
        valueRange = -100f..100f,
        steps = 39,
    )
    Text(
        text = "세로 ${formatSubtitleOffset(appearance.verticalOffsetPercent)}",
        color = Color.White,
    )
    Slider(
        value = appearance.verticalOffsetPercent.toFloat(),
        onValueChange = {
            onAppearanceChanged(
                appearance.copy(verticalOffsetPercent = it.roundToInt()),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(verticalPositionFocusRequester)
            .onFocusChanged {
                verticalPositionFocused = it.isFocused || it.hasFocus
            }
            .onPreviewKeyEvent {
                handleDpadFocusMove(
                    it.nativeKeyEvent,
                    up = horizontalPositionFocusRequester,
                    down = resetPositionFocusRequester,
                )
            },
        colors = playerSettingsSliderColors(verticalPositionFocused),
        valueRange = -100f..100f,
        steps = 39,
    )
    Text(
        text = "가로는 - 왼쪽 / + 오른쪽, 세로는 - 위 / + 아래로 이동합니다.",
        color = Color.Gray,
    )
    Text(
        text = "가로 자막 위치는 하단 중앙 위쪽, 탐색은 화면 맨 아래에서 조절합니다.",
        color = Color.Gray,
    )
    Text(
        text = "세로 자막 위치는 오른쪽 안쪽, 볼륨은 맨 오른쪽 가장자리, 밝기는 맨 왼쪽 가장자리에서 조절합니다.",
        color = Color.Gray,
    )
    PlayerMenuActionButton(
        label = "자막 위치 가운데로",
        onClick = {
            onAppearanceChanged(
                appearance.copy(
                    horizontalOffsetPercent = 0,
                    verticalOffsetPercent = 0,
                ),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(resetPositionFocusRequester)
            .onPreviewKeyEvent {
                handleDpadFocusMove(
                    it.nativeKeyEvent,
                    up = verticalPositionFocusRequester,
                    down = verticalWritingFocusRequester,
                )
            },
    )

    Text("자막 세로쓰기")
    AudioOptionRow(
        label = if (appearance.verticalWriting) {
            "세로쓰기: 켬"
        } else {
            "세로쓰기: 끔"
        },
        selected = appearance.verticalWriting,
        modifier = Modifier
            .focusRequester(verticalWritingFocusRequester)
            .onPreviewKeyEvent {
                handleDpadFocusMove(
                    it.nativeKeyEvent,
                    up = resetPositionFocusRequester,
                )
            },
        onClick = {
            val enabled = !appearance.verticalWriting
            onAppearanceChanged(
                appearance.copy(
                    verticalWriting = enabled,
                    horizontalOffsetPercent = if (enabled) {
                        0
                    } else {
                        appearance.horizontalOffsetPercent
                    },
                    verticalOffsetPercent = if (enabled) {
                        0
                    } else {
                        appearance.verticalOffsetPercent
                    },
                ),
            )
        },
    )
    Text(
        text = "세로쓰기는 어절 단위로 줄바꿈하고 문장부호를 세로쓰기용으로 표시합니다.",
        color = Color.Gray,
    )

    Text("자막 색상")
    SubtitleChoiceChips(
        options = SUBTITLE_COLOR_OPTIONS.map { it.label to it.value },
        selected = appearance.foregroundColor,
        onSelect = {
            onAppearanceChanged(appearance.copy(foregroundColor = it))
        },
    )

    Text("자막 외곽선")
    SubtitleChoiceChips(
        options = SUBTITLE_EDGE_OPTIONS.map { it.label to it.value },
        selected = appearance.edgeType,
        onSelect = {
            onAppearanceChanged(appearance.copy(edgeType = it))
        },
    )
    if (appearance.edgeType != CaptionStyleCompat.EDGE_TYPE_NONE) {
        Text("외곽선 색상")
        SubtitleChoiceChips(
            options = SUBTITLE_EDGE_COLOR_OPTIONS.map { it.label to it.value },
            selected = appearance.edgeColor,
            onSelect = {
                onAppearanceChanged(appearance.copy(edgeColor = it))
            },
        )
    }

    Text("자막 배경")
    SubtitleChoiceChips(
        options = SUBTITLE_BACKGROUND_OPTIONS.map { it.label to it.value },
        selected = appearance.backgroundColor,
        onSelect = {
            onAppearanceChanged(appearance.copy(backgroundColor = it))
        },
    )
}

@Composable
private fun SubtitleTrackRow(
    player: ExoPlayer,
    option: SubtitleTrackOption,
    textTrackDisabled: Boolean,
    hasTextOverride: Boolean,
    onManualSubtitleIndexChanged: (Int?) -> Unit,
    onTracksChanged: () -> Unit,
) {
    AudioOptionRow(
        label = option.label +
            if (option.isBitmap) {
                " · 글꼴 고정"
            } else {
                ""
            } +
            if (option.selected && !textTrackDisabled) {
                " · 현재 재생"
            } else {
                ""
            },
        selected = !textTrackDisabled &&
            hasTextOverride &&
            option.selected,
        onClick = {
            onManualSubtitleIndexChanged(option.externalSubtitleIndex)
            player.trackSelectionParameters =
                player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setOverrideForType(
                        TrackSelectionOverride(
                            option.group,
                            option.trackIndex,
                        ),
                    )
                    .build()
            onTracksChanged()
        },
    )
}

private fun queryDisplayName(
    context: Context,
    uri: Uri,
): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column >= 0) cursor.getString(column) else null
    }
}.getOrNull()

private fun applySubtitleStyle(
    playerView: PlayerView,
    typeface: Typeface,
    appearance: SubtitleAppearance,
    useNativeSubtitleRenderer: Boolean,
) {
    playerView.subtitleView?.apply {
        visibility =
            if (useNativeSubtitleRenderer) View.VISIBLE else View.GONE
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setFractionalTextSize(
            .0533f * appearance.sizePercent / 100f,
        )
        setStyle(
            CaptionStyleCompat(
                appearance.foregroundColor,
                appearance.backgroundColor,
                android.graphics.Color.TRANSPARENT,
                appearance.edgeType,
                appearance.edgeColor,
                typeface,
            ),
        )
        post {
            pivotX = width / 2f
            pivotY = height / 2f
            rotation = 0f
            translationX =
                if (appearance.verticalWriting) {
                    0f
                } else {
                    width * appearance.horizontalOffsetPercent / 100f
                }
            translationY =
                if (appearance.verticalWriting) {
                    0f
                } else {
                    height * appearance.verticalOffsetPercent / 100f
                }
            invalidate()
        }
    }
}

@Composable
internal fun HorizontalSubtitleOverlay(
    text: String,
    typeface: Typeface,
    appearance: SubtitleAppearance,
    modifier: Modifier = Modifier,
) {
    if (appearance.verticalWriting || text.isBlank()) return
    AndroidView(
        factory = { context ->
            HorizontalSubtitleView(context).apply {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
        },
        update = { view ->
            val density = view.resources.displayMetrics.density
            view.subtitleText = text
            view.subtitleTypeface = typeface
            view.subtitleTextFraction =
                .0533f * appearance.sizePercent.coerceIn(50, 200) / 100f
            view.subtitleFillColor = appearance.foregroundColor
            view.subtitleBackgroundColor = appearance.backgroundColor
            view.subtitleEdgeType = appearance.edgeType
            view.subtitleEdgeColor = appearance.edgeColor
            view.outlineStrokeWidth = 3.5f * density
            view.horizontalOffsetPercent =
                appearance.horizontalOffsetPercent
            view.verticalOffsetPercent =
                appearance.verticalOffsetPercent
            view.lineHeightMultiplier = SubtitleLineSpacingMultiplier
            view.setPadding(
                (24 * density).roundToInt(),
                (24 * density).roundToInt(),
                (24 * density).roundToInt(),
                (72 * density).roundToInt(),
            )
            view.requestLayout()
            view.invalidate()
        },
        modifier = modifier,
    )
}

@Composable
internal fun VerticalSubtitleOverlay(
    text: String,
    typeface: Typeface,
    appearance: SubtitleAppearance,
    modifier: Modifier = Modifier,
) {
    if (!appearance.verticalWriting || text.isBlank()) return
    AndroidView(
        factory = { context ->
            VerticalSubtitleView(context).apply {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
        },
        update = { view ->
            val density = view.resources.displayMetrics.density
            view.subtitleText = text
            view.subtitleTypeface = typeface
            view.subtitleTextSizePx =
                30f * density * appearance.sizePercent.coerceIn(50, 200) / 100f
            view.subtitleFillColor = appearance.foregroundColor
            view.subtitleEdgeType = appearance.edgeType
            view.subtitleEdgeColor = appearance.edgeColor
            view.outlineStrokeWidth = 3.5f * density
            view.columnLineSpacingMultiplier = SubtitleLineSpacingMultiplier
            view.glyphAdvancePx = 28.5f * density *
                appearance.sizePercent.coerceIn(50, 200) / 100f
            view.horizontalOffsetPercent =
                appearance.horizontalOffsetPercent
            view.verticalOffsetPercent =
                appearance.verticalOffsetPercent
            view.setBackgroundColor(appearance.backgroundColor)
            view.setPadding(
                (34 * density).roundToInt(),
                (18 * density).roundToInt(),
                (80 * density).roundToInt(),
                (18 * density).roundToInt(),
            )
            view.requestLayout()
            view.invalidate()
        },
        modifier = modifier,
    )
}

private class HorizontalSubtitleView(context: Context) : View(context) {
    var subtitleText: String = ""
        set(value) {
            if (field == value) return
            field = value
            paragraphs = value.toSubtitleParagraphs()
            requestLayout()
            invalidate()
        }
    var subtitleTypeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            invalidate()
        }
    var subtitleTextFraction: Float = .0533f
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }
    var subtitleFillColor: Int = android.graphics.Color.WHITE
    var subtitleBackgroundColor: Int = android.graphics.Color.TRANSPARENT
    var subtitleEdgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE
    var subtitleEdgeColor: Int = android.graphics.Color.BLACK
    var outlineStrokeWidth: Float = 4f
    var horizontalOffsetPercent: Int = 0
    var verticalOffsetPercent: Int = 0
    var lineHeightMultiplier: Float = SubtitleLineSpacingMultiplier

    private var paragraphs: List<String> = emptyList()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val textSizePx = (height * subtitleTextFraction).coerceAtLeast(18f)
        configurePaints(textSizePx)
        val horizontalPadding = textSizePx * .34f
        val verticalPadding = textSizePx * .18f
        val maxTextWidth =
            (
                width -
                    paddingLeft -
                    paddingRight -
                    horizontalPadding * 2f
                ).coerceAtLeast(1f)
        val lines = paragraphs
            .flatMap { it.wrapHorizontalSubtitleLine(fillPaint, maxTextWidth) }
        if (lines.isEmpty()) return
        val fontMetrics = fillPaint.fontMetrics
        val lineHeight = textSizePx * lineHeightMultiplier
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val contentHeight = textHeight + lineHeight * (lines.size - 1)
        val centerX = width / 2f + width * horizontalOffsetPercent / 100f
        val offsetY = height * verticalOffsetPercent / 100f
        val top = height - paddingBottom - contentHeight + offsetY
        var baseline = top - fontMetrics.ascent
        lines.forEach { line ->
            if (android.graphics.Color.alpha(subtitleBackgroundColor) > 0) {
                val textWidth = fillPaint.measureText(line)
                canvas.drawRoundRect(
                    android.graphics.RectF(
                        centerX - textWidth / 2f - horizontalPadding,
                        baseline + fontMetrics.ascent - verticalPadding,
                        centerX + textWidth / 2f + horizontalPadding,
                        baseline + fontMetrics.descent + verticalPadding,
                    ),
                    textSizePx * .12f,
                    textSizePx * .12f,
                    backgroundPaint,
                )
            }
            if (subtitleEdgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
                canvas.drawText(line, centerX, baseline, edgePaint)
            }
            canvas.drawText(line, centerX, baseline, fillPaint)
            baseline += lineHeight
        }
    }

    private fun configurePaints(textSizePx: Float) {
        fillPaint.apply {
            style = Paint.Style.FILL
            color = subtitleFillColor
            typeface = subtitleTypeface
            textSize = textSizePx
            textAlign = Paint.Align.CENTER
            if (subtitleEdgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
                setShadowLayer(
                    outlineStrokeWidth,
                    outlineStrokeWidth * .55f,
                    outlineStrokeWidth * .55f,
                    subtitleEdgeColor,
                )
            } else {
                clearShadowLayer()
            }
        }
        edgePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = outlineStrokeWidth
            color = subtitleEdgeColor
            typeface = subtitleTypeface
            textSize = textSizePx
            textAlign = Paint.Align.CENTER
            clearShadowLayer()
        }
        backgroundPaint.apply {
            style = Paint.Style.FILL
            color = subtitleBackgroundColor
            clearShadowLayer()
        }
    }
}

private class VerticalSubtitleView(context: Context) : View(context) {
    var subtitleText: String = ""
        set(value) {
            if (field == value) return
            field = value
            sourceColumns = value.toVerticalSubtitleColumns()
            measuredColumns = emptyList()
            requestLayout()
            invalidate()
        }
    var subtitleTypeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            configurePaints()
            requestLayout()
            invalidate()
        }
    var subtitleTextSizePx: Float = 30f
        set(value) {
            field = value
            configurePaints()
            requestLayout()
            invalidate()
        }
    var subtitleFillColor: Int = android.graphics.Color.WHITE
    var subtitleEdgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE
    var subtitleEdgeColor: Int = android.graphics.Color.BLACK
    var outlineStrokeWidth: Float = 4f
    var columnLineSpacingMultiplier: Float = SubtitleLineSpacingMultiplier
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }
    var glyphAdvancePx: Float = 28.5f
        set(value) {
            if (field == value) return
            field = value.coerceAtLeast(1f)
            measuredColumns = emptyList()
            requestLayout()
            invalidate()
        }
    var horizontalOffsetPercent: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var verticalOffsetPercent: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var sourceColumns: List<List<VerticalSubtitleGlyph>> = emptyList()
    private var measuredColumns: List<List<VerticalSubtitleGlyph>> = emptyList()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphBounds = Rect()

    init {
        setWillNotDraw(false)
        configurePaints()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        configurePaints()
        val availableHeight = View.MeasureSpec.getSize(heightMeasureSpec)
            .takeIf {
                View.MeasureSpec.getMode(heightMeasureSpec) !=
                    View.MeasureSpec.UNSPECIFIED
            }
            ?: Int.MAX_VALUE
        val maxColumnAdvance = maxVerticalColumnAdvance(availableHeight)
        val measuredSourceColumns = sourceColumns.map { glyphs ->
            glyphs.map { glyph ->
                if (!glyph.measureRotatedTextAdvance) {
                    glyph
                } else {
                    glyph.copy(
                        advanceScale = (
                            (fillPaint.measureText(glyph.text) + outlineStrokeWidth * 2f) /
                                glyphAdvancePx
                            ).coerceAtLeast(1f),
                    )
                }
            }
        }
        val columns = measuredSourceColumns.wrapVerticalSubtitleColumns(maxColumnAdvance)
        measuredColumns = columns
        val columnCount = columns.size.coerceAtLeast(1)
        val measuredColumnAdvance = columns.maxOfOrNull { it.verticalAdvance() } ?: 1f
        val columnWidth = verticalColumnWidth()
        val columnAdvance = verticalColumnAdvance()
        val desiredWidth = paddingLeft + paddingRight +
            columnWidth +
            ((columnCount - 1).coerceAtLeast(0) * columnAdvance)
        val desiredHeight = paddingTop + paddingBottom + measuredColumnAdvance * glyphAdvancePx
        setMeasuredDimension(
            resolveSize(desiredWidth.roundToInt(), widthMeasureSpec),
            resolveSize(desiredHeight.roundToInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        configurePaints()
        val columns = measuredColumns
        if (columns.isEmpty()) return
        val columnWidth = verticalColumnWidth()
        val columnAdvance = verticalColumnAdvance()
        val availableTop = paddingTop.toFloat()
        val availableRight = (width - paddingRight).toFloat()
        val availableBottom = (height - paddingBottom).toFloat()
        val offsetX = width * horizontalOffsetPercent / 100f
        val rightEdge = availableRight + offsetX
        val firstColumnX = rightEdge - columnWidth / 2f
        columns.forEachIndexed { columnIndex, glyphs ->
            val x = firstColumnX - columnIndex * columnAdvance
            val contentHeight = glyphs.verticalAdvance() * glyphAdvancePx
            val offsetY = height * verticalOffsetPercent / 100f
            val availableHeight = availableBottom - availableTop
            val top = availableTop +
                (availableHeight - contentHeight) / 2f +
                offsetY
            var baseline = top -
                ((fillPaint.ascent() + fillPaint.descent()) / 2f)
            glyphs.forEach { glyph ->
                if (!glyph.spacer) {
                    drawGlyph(canvas, glyph, x, baseline)
                }
                baseline += glyphAdvancePx * glyph.advanceScale
            }
        }
    }

    private fun drawGlyph(
        canvas: android.graphics.Canvas,
        glyph: VerticalSubtitleGlyph,
        x: Float,
        baseline: Float,
    ) {
        val baseCellCenterY = baseline + (fillPaint.ascent() + fillPaint.descent()) / 2f
        val cellCenterY = if (glyph.measureRotatedTextAdvance) {
            baseCellCenterY + glyphAdvancePx * (glyph.advanceScale - 1f) / 2f
        } else {
            baseCellCenterY
        }
        val drawPosition = if (glyph.centerInCell) {
            centeredGlyphPosition(glyph.text, x, cellCenterY)
        } else {
            x to baseline
        }
        val drawX = drawPosition.first
        val drawBaseline = drawPosition.second
        val previousFillAlign = fillPaint.textAlign
        val previousEdgeAlign = edgePaint.textAlign
        if (glyph.centerInCell) {
            fillPaint.textAlign = Paint.Align.LEFT
            edgePaint.textAlign = Paint.Align.LEFT
        }
        if (glyph.rotate) {
            canvas.save()
            canvas.rotate(VerticalSubtitleRightRotationDegrees, x, cellCenterY)
        }
        if (subtitleEdgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
            canvas.drawText(glyph.text, drawX, drawBaseline, edgePaint)
        }
        canvas.drawText(glyph.text, drawX, drawBaseline, fillPaint)
        if (glyph.rotate) {
            canvas.restore()
        }
        if (glyph.centerInCell) {
            fillPaint.textAlign = previousFillAlign
            edgePaint.textAlign = previousEdgeAlign
        }
    }

    private fun configurePaints() {
        fillPaint.apply {
            style = Paint.Style.FILL
            color = subtitleFillColor
            typeface = subtitleTypeface
            textSize = subtitleTextSizePx
            textAlign = Paint.Align.CENTER
            if (subtitleEdgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
                setShadowLayer(
                    outlineStrokeWidth,
                    outlineStrokeWidth * .55f,
                    outlineStrokeWidth * .55f,
                    subtitleEdgeColor,
                )
            } else {
                clearShadowLayer()
            }
        }
        edgePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = outlineStrokeWidth
            color = subtitleEdgeColor
            typeface = subtitleTypeface
            textSize = subtitleTextSizePx
            textAlign = Paint.Align.CENTER
            clearShadowLayer()
        }
    }

    private fun maxVerticalColumnAdvance(availableHeightPx: Int): Float {
        val contentHeight = if (availableHeightPx == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            (availableHeightPx - paddingTop - paddingBottom).coerceAtLeast(1)
        }
        return (contentHeight / glyphAdvancePx)
            .coerceAtLeast(4f)
    }

    private fun verticalColumnWidth(): Float =
        subtitleTextSizePx * 1.9f + outlineStrokeWidth * 2.2f

    private fun verticalColumnAdvance(): Float =
        subtitleTextSizePx * columnLineSpacingMultiplier + outlineStrokeWidth * 1.4f

    private fun centeredGlyphPosition(
        text: String,
        cellCenterX: Float,
        cellCenterY: Float,
    ): Pair<Float, Float> {
        val previousAlign = fillPaint.textAlign
        fillPaint.textAlign = Paint.Align.LEFT
        fillPaint.getTextBounds(text, 0, text.length, glyphBounds)
        fillPaint.textAlign = previousAlign
        if (glyphBounds.isEmpty) {
            return cellCenterX to
                (cellCenterY - (fillPaint.ascent() + fillPaint.descent()) / 2f)
        }
        return (cellCenterX - (glyphBounds.left + glyphBounds.right) / 2f) to
            (cellCenterY - (glyphBounds.top + glyphBounds.bottom) / 2f)
    }
}

private fun List<androidx.media3.common.text.Cue>.toSubtitleOverlayText(): String =
    mapNotNull { cue ->
        cue.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }.joinToString("\n\n")

private fun String.toSubtitleParagraphs(): List<String> =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

private fun String.wrapHorizontalSubtitleLine(
    paint: Paint,
    maxWidthPx: Float,
): List<String> {
    val normalized = trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return emptyList()
    if (paint.measureText(normalized) <= maxWidthPx) return listOf(normalized)

    val lines = mutableListOf<String>()
    var current = ""
    normalized.codePoints().toArray().forEach { codePoint ->
        val token = String(Character.toChars(codePoint))
        val candidate = current + token
        if (current.isEmpty() || paint.measureText(candidate) <= maxWidthPx) {
            current = candidate
            return@forEach
        }

        val breakIndex = current.trimEnd().lastIndexOf(' ')
        if (breakIndex > 0) {
            lines += current.substring(0, breakIndex).trimEnd()
            current = current.substring(breakIndex).trimStart() + token
        } else {
            lines += current.trimEnd()
            current = token.trimStart()
        }
    }
    current.trim().takeIf { it.isNotBlank() }?.let { lines += it }
    return lines
}

private fun String.toVerticalSubtitleColumns(): List<List<VerticalSubtitleGlyph>> {
    val lines = lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .toList()
    return lines
        .map { it.toVerticalSubtitleGlyphs() }
        .filter { it.isNotEmpty() }
}

private fun List<List<VerticalSubtitleGlyph>>.wrapVerticalSubtitleColumns(
    maxColumnAdvance: Float,
): List<List<VerticalSubtitleGlyph>> =
    flatMap { glyphs -> glyphs.wrapVerticalSubtitleColumn(maxColumnAdvance) }

private fun List<VerticalSubtitleGlyph>.wrapVerticalSubtitleColumn(
    maxColumnAdvance: Float,
): List<List<VerticalSubtitleGlyph>> {
    if (isEmpty()) return emptyList()
    val safeMaxAdvance = maxColumnAdvance.coerceAtLeast(4f)
    val columns = mutableListOf<List<VerticalSubtitleGlyph>>()
    val words = toVerticalSubtitleWords()
    var current = mutableListOf<VerticalSubtitleGlyph>()
    var currentAdvance = 0f

    fun flush() {
        while (current.firstOrNull()?.spacer == true) current.removeAt(0)
        while (current.lastOrNull()?.spacer == true) current.removeAt(current.lastIndex)
        if (current.isNotEmpty()) columns += current.toList()
        current = mutableListOf()
        currentAdvance = 0f
    }

    fun addWord(word: List<VerticalSubtitleGlyph>) {
        if (word.isEmpty()) return
        val separatorAdvance =
            if (current.isEmpty()) 0f else VerticalSubtitleWordSpacing
        val wordAdvance = word.verticalWrapAdvance()
        if (
            current.isNotEmpty() &&
            currentAdvance + separatorAdvance + wordAdvance > safeMaxAdvance
        ) {
            flush()
        }
        if (current.isNotEmpty()) {
            current += verticalSubtitleWordSpacer()
            currentAdvance += VerticalSubtitleWordSpacing
        }
        current.addAll(word)
        currentAdvance += wordAdvance
    }

    words.forEach { word ->
        if (word.verticalWrapAdvance() > safeMaxAdvance) {
            if (current.isNotEmpty()) {
                flush()
            }
            word.wrapLongVerticalSubtitleWord(safeMaxAdvance).forEach {
                current.addAll(it)
                currentAdvance = it.verticalWrapAdvance()
                flush()
            }
        } else {
            addWord(word)
        }
    }
    flush()
    return columns
}

private fun List<VerticalSubtitleGlyph>.toVerticalSubtitleWords(): List<List<VerticalSubtitleGlyph>> {
    val words = mutableListOf<List<VerticalSubtitleGlyph>>()
    var current = mutableListOf<VerticalSubtitleGlyph>()

    fun flush() {
        if (current.isNotEmpty()) {
            words += current.toList()
            current = mutableListOf()
        }
    }

    forEach { glyph ->
        if (glyph.spacer) {
            flush()
        } else {
            current += glyph
        }
    }
    flush()
    return words
}

private fun List<VerticalSubtitleGlyph>.wrapLongVerticalSubtitleWord(
    maxColumnAdvance: Float,
): List<List<VerticalSubtitleGlyph>> {
    val safeMaxAdvance = maxColumnAdvance.coerceAtLeast(4f)
    val columns = mutableListOf<List<VerticalSubtitleGlyph>>()
    var current = mutableListOf<VerticalSubtitleGlyph>()
    var currentAdvance = 0f

    fun flush() {
        if (current.isNotEmpty()) {
            columns += current.toList()
            current = mutableListOf()
            currentAdvance = 0f
        }
    }

    forEach { glyph ->
        val glyphAdvance = glyph.verticalWrapAdvance()
        if (
            current.isNotEmpty() &&
            currentAdvance + glyphAdvance > safeMaxAdvance
        ) {
            flush()
        }
        current += glyph
        currentAdvance += glyphAdvance
    }
    flush()
    return columns
}

internal fun String.toVerticalSubtitleGlyphs(): List<VerticalSubtitleGlyph> {
    val codePoints = codePoints().toArray()
    val glyphs = mutableListOf<VerticalSubtitleGlyph>()
    var index = 0
    var doubleQuoteOpen = true
    var singleQuoteOpen = true
    while (index < codePoints.size) {
        val codePoint = codePoints[index]
        if (Character.isWhitespace(codePoint)) {
            glyphs += VerticalSubtitleGlyph(
                text = " ",
                spacer = true,
                advanceScale = .22f,
            )
            index++
            continue
        }
        if (isAsciiEnglishLetter(codePoint)) {
            val start = index
            while (index < codePoints.size && isAsciiEnglishLetter(codePoints[index])) {
                index++
            }
            val englishRun = codePoints.copyOfRange(start, index).toCodePointString()
            glyphs += if (index - start == 1) {
                VerticalSubtitleGlyph(englishRun)
            } else {
                VerticalSubtitleGlyph(
                    text = englishRun,
                    rotate = true,
                    centerInCell = true,
                    measureRotatedTextAdvance = true,
                )
            }
            continue
        }
        if (isEllipsisDot(codePoint)) {
            val start = index
            while (
                index < codePoints.size &&
                isEllipsisDot(codePoints[index])
            ) {
                index++
            }
            val dotRun = codePoints.copyOfRange(start, index)
            if (dotRun.size > 1 || dotRun.firstOrNull() == '…'.code) {
                glyphs += VerticalSubtitleGlyph(
                    "︙",
                    advanceScale = .82f,
                    centerInCell = true,
                )
            } else {
                glyphs += VerticalSubtitleGlyph(
                    verticalPunctuationForm(dotRun.first()) ?: dotRun.toCodePointString(),
                    centerInCell = true,
                )
            }
            continue
        }
        if (codePoint == '"'.code) {
            glyphs += VerticalSubtitleGlyph(
                if (doubleQuoteOpen) "﹁" else "﹂",
                centerInCell = true,
            )
            doubleQuoteOpen = !doubleQuoteOpen
            index++
            continue
        }
        if (codePoint == '\''.code && !isApostropheInsideWord(codePoints, index)) {
            glyphs += VerticalSubtitleGlyph(
                if (singleQuoteOpen) "﹃" else "﹄",
                centerInCell = true,
            )
            singleQuoteOpen = !singleQuoteOpen
            index++
            continue
        }
        verticalPunctuationForm(codePoint)?.let { verticalForm ->
            glyphs += VerticalSubtitleGlyph(
                verticalForm,
                centerInCell = true,
            )
            index++
            continue
        }
        rotatedVerticalSymbolGlyph(codePoint)?.let { glyph ->
            glyphs += glyph
            index++
            continue
        }
        glyphs += VerticalSubtitleGlyph(String(Character.toChars(codePoint)))
        index++
    }
    return glyphs
}

private fun isAsciiEnglishLetter(codePoint: Int): Boolean =
    codePoint in 'A'.code..'Z'.code || codePoint in 'a'.code..'z'.code

private fun List<VerticalSubtitleGlyph>.verticalAdvance(): Float =
    sumOf { it.advanceScale.toDouble() }.toFloat()

private fun List<VerticalSubtitleGlyph>.verticalWrapAdvance(): Float =
    sumOf { it.verticalWrapAdvance().toDouble() }.toFloat()

private fun VerticalSubtitleGlyph.verticalWrapAdvance(): Float =
    advanceScale

private fun verticalSubtitleWordSpacer(): VerticalSubtitleGlyph =
    VerticalSubtitleGlyph(
        text = " ",
        spacer = true,
        advanceScale = VerticalSubtitleWordSpacing,
    )

private fun isEllipsisDot(codePoint: Int): Boolean =
    codePoint == '.'.code ||
        codePoint == '．'.code ||
        codePoint == '·'.code ||
        codePoint == '•'.code ||
        codePoint == '…'.code

private fun verticalPunctuationForm(codePoint: Int): String? =
    when (codePoint) {
        ','.code, '，'.code -> "︐"
        '、'.code, '､'.code -> "︑"
        '.'.code, '．'.code, '。'.code -> "︒"
        ':'.code, '：'.code -> "︓"
        ';'.code, '；'.code -> "︔"
        '!'.code, '！'.code -> "︕"
        '?'.code, '？'.code -> "︖"
        '('.code, '（'.code -> "︵"
        ')'.code, '）'.code -> "︶"
        '{'.code, '｛'.code -> "︷"
        '}'.code, '｝'.code -> "︸"
        '〔'.code -> "︹"
        '〕'.code -> "︺"
        '【'.code -> "︻"
        '】'.code -> "︼"
        '《'.code -> "︽"
        '》'.code -> "︾"
        '〈'.code, '<'.code, '＜'.code -> "︿"
        '〉'.code, '>'.code, '＞'.code -> "﹀"
        '「'.code, '“'.code, '‘'.code, '｢'.code -> "﹁"
        '」'.code, '”'.code, '’'.code, '｣'.code -> "﹂"
        '『'.code, '〝'.code -> "﹃"
        '』'.code, '〟'.code -> "﹄"
        '['.code, '［'.code -> "﹇"
        ']'.code, '］'.code -> "﹈"
        '—'.code, '―'.code -> "︱"
        '-'.code, '－'.code, '–'.code -> "︲"
        '_'.code, '＿'.code -> "︳"
        '〜'.code, '～'.code, '~'.code -> "︴"
        else -> null
    }

private fun rotatedVerticalSymbolGlyph(codePoint: Int): VerticalSubtitleGlyph? =
    if (isRotatedSymbol(codePoint)) {
        VerticalSubtitleGlyph(
            String(Character.toChars(codePoint)),
            rotate = true,
            advanceScale = .84f,
            centerInCell = true,
        )
    } else {
        null
    }

private fun isRotatedSymbol(codePoint: Int): Boolean =
    codePoint in listOf(
        '/'.code,
        '\\'.code,
        '|'.code,
        '@'.code,
        '#'.code,
        '$'.code,
        '%'.code,
        '^'.code,
        '&'.code,
        '*'.code,
        '+'.code,
        '='.code,
    )

private fun isApostropheInsideWord(codePoints: IntArray, index: Int): Boolean =
    index > 0 &&
        index < codePoints.lastIndex &&
        Character.isLetterOrDigit(codePoints[index - 1]) &&
        Character.isLetterOrDigit(codePoints[index + 1])

private fun IntArray.toCodePointString(): String =
    joinToString(separator = "") { codePoint ->
        String(Character.toChars(codePoint))
    }

private fun formatSubtitleOffset(value: Int): String =
    when {
        value > 0 -> "+$value%"
        value < 0 -> "$value%"
        else -> "0%"
    }

private fun handleDpadFocusMove(
    nativeEvent: AndroidKeyEvent,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
): Boolean {
    val target = when (nativeEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> up
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> down
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> left
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> right
        else -> null
    } ?: return false
    if (
        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
        nativeEvent.repeatCount == 0
    ) {
        runCatching { target.requestFocus() }
    }
    return true
}

@Composable
private fun <T> SubtitleChoiceChips(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    options.chunked(3).forEach { rowOptions ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rowOptions.forEach { (label, value) ->
                var focused by remember { mutableStateOf(false) }
                val selectedItem = selected == value
                FilterChip(
                    selected = selectedItem,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            focused = it.isFocused || it.hasFocus
                        },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (focused) {
                            PlayerMenuFocusBackground
                        } else {
                            Color.Transparent
                        },
                        labelColor = if (focused || selectedItem) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = .82f)
                        },
                        selectedContainerColor = if (focused) {
                            PlayerMenuFocusBackground
                        } else {
                            PlayerMenuSelectedBackground.copy(alpha = .95f)
                        },
                        selectedLabelColor = Color.White,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedItem,
                        borderColor = if (focused) {
                            PlayerMenuFocusColor
                        } else {
                            PlayerMenuIdleBorder
                        },
                        selectedBorderColor = if (focused) {
                            PlayerMenuFocusColor
                        } else {
                            PlayerMenuFocusColor.copy(alpha = .72f)
                        },
                        borderWidth = when {
                            focused -> 4.dp
                            selectedItem -> 2.dp
                            else -> 1.dp
                        },
                    ),
                )
            }
            repeat(3 - rowOptions.size) {
                Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AudioOptionRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        focused -> PlayerMenuFocusBackground
        selected -> PlayerMenuSelectedBackground
        else -> Color.Transparent
    }
    val borderColor = when {
        focused -> PlayerMenuFocusColor
        selected -> PlayerMenuFocusColor.copy(alpha = .65f)
        else -> PlayerMenuIdleBorder
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = when {
                focused -> 4.dp
                selected -> 2.dp
                else -> 1.dp
            },
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Text(
                text = label,
                color = if (focused || selected) {
                    Color.White
                } else {
                    Color.White.copy(alpha = .82f)
                },
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private val PLAYBACK_SPEEDS =
    listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}.0×" else "$speed×"

private fun formatGestureTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun configureEpisodeNavigationButtons(
    playerView: PlayerView,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val previous =
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_prev)
    val rewind =
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_rew)
    val playPause =
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
    val fastForward =
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_ffwd)
    val next =
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_next)

    previous?.apply {
        visibility = View.VISIBLE
        isEnabled = hasPrevious
        isFocusable = hasPrevious
        alpha = if (hasPrevious) 1f else .28f
        contentDescription = "이전화"
        setOnClickListener(if (hasPrevious) View.OnClickListener { onPrevious() } else null)
        nextFocusRightId = androidx.media3.ui.R.id.exo_rew
    }
    rewind?.apply {
        isFocusable = true
        contentDescription = "10초 뒤로"
        nextFocusLeftId = if (hasPrevious) {
            androidx.media3.ui.R.id.exo_prev
        } else {
            View.NO_ID
        }
        nextFocusRightId = androidx.media3.ui.R.id.exo_play_pause
    }
    playPause?.apply {
        isFocusable = true
        contentDescription = "재생 또는 일시정지"
        nextFocusLeftId = androidx.media3.ui.R.id.exo_rew
        nextFocusRightId = androidx.media3.ui.R.id.exo_ffwd
    }
    fastForward?.apply {
        isFocusable = true
        contentDescription = "10초 앞으로"
        nextFocusLeftId = androidx.media3.ui.R.id.exo_play_pause
        nextFocusRightId = if (hasNext) {
            androidx.media3.ui.R.id.exo_next
        } else {
            View.NO_ID
        }
    }
    next?.apply {
        visibility = View.VISIBLE
        isEnabled = hasNext
        isFocusable = hasNext
        alpha = if (hasNext) 1f else .28f
        contentDescription = "다음화"
        setOnClickListener(if (hasNext) View.OnClickListener { onNext() } else null)
        nextFocusLeftId = androidx.media3.ui.R.id.exo_ffwd
    }
}

@Composable
private fun PlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .scale(if (focused) 1.12f else 1f)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CircleShape,
        color = if (focused) Color.White else Color.Black.copy(alpha = .68f),
        border = if (focused) BorderStroke(3.dp, PlexGold) else null,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (focused) Color.Black else Color.White,
            )
        }
    }
}
