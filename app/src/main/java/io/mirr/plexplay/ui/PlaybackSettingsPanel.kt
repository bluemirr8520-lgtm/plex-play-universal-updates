package io.mirr.plexplay.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A modal settings overlay in the player's existing window. Keep this as a sibling of the
 * player surface: opening it must not move, re-create, or take window focus from that surface.
 * The text slot owns scrolling; its height is bounded while the title and actions remain visible.
 */
@Composable
internal fun PlaybackSettingsPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    focusKey: Any = Unit,
    preferRemoteInput: Boolean = LocalContext.current.isTelevisionDevice(),
    previewVideo: Boolean = false,
    containerColor: Color = Color.Black,
    titleContentColor: Color = Color.White,
    textContentColor: Color = Color.White,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val contentFocusRequester = remember { FocusRequester() }
    val footerFocusRequester = remember { FocusRequester() }
    val dismissKeysDown = remember { mutableSetOf<Int>() }
    val dismiss by rememberUpdatedState(onDismissRequest)

    BackHandler { dismiss() }
    LaunchedEffect(preferRemoteInput) {
        if (preferRemoteInput) inputModeManager.requestInputMode(InputMode.Keyboard)
    }
    LaunchedEffect(focusKey, inputModeManager.inputMode) {
        withFrameNanos { }
        // Focus an actual option, not a focusable panel or a temporary invisible spacer.
        // Replacing the page's content group also prevents retaining focus on its footer.
        if (!contentFocusRequester.requestFocus()) footerFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent
                when (key.keyCode) {
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_SETTINGS -> {
                        // Keep the overlay mounted until key-up, so that release cannot reach
                        // the player. Ignore the unmatched release of the key that opened it.
                        when (key.action) {
                            KeyEvent.ACTION_DOWN -> if (key.repeatCount == 0) {
                                dismissKeysDown.add(key.keyCode)
                            }
                            KeyEvent.ACTION_UP -> {
                                val pressedHere = dismissKeysDown.remove(key.keyCode)
                                if (pressedHere && !key.isCanceled) dismiss()
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_STOP,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> true
                    else -> false
                }
            }
            .onKeyEvent { event ->
                // Child sliders/buttons get first refusal. Always consume remaining navigation,
                // including failed moves, so Android/Compose cannot wrap focus to the player.
                val key = event.nativeKeyEvent
                val direction = when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                    KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                    KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
                    KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
                    KeyEvent.KEYCODE_TAB -> if (key.isShiftPressed) {
                        FocusDirection.Previous
                    } else {
                        FocusDirection.Next
                    }
                    else -> null
                }
                if (direction != null) {
                    if (key.action == KeyEvent.ACTION_DOWN) focusManager.moveFocus(direction)
                    true
                } else {
                    false
                }
            }
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // Pointer-only scrim: it must never become a D-pad/Tab destination.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (previewVideo) 0f else .32f))
                .pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) },
        )
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    // Consume blank-area taps without introducing another focusable element.
                    // Child controls/scrolling still receive their events before this detector.
                    .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                    .semantics {
                        dialog()
                        paneTitle = "재생 환경설정"
                        isTraversalGroup = true
                    },
                color = containerColor,
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { title() }
                        }
                    }
                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            key(focusKey) {
                                Box(Modifier.weight(1f, fill = false).fillMaxWidth()
                                    .focusRequester(contentFocusRequester).focusGroup()) { text() }
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(top = 16.dp)
                            .focusRequester(footerFocusRequester).focusGroup(),
                        contentAlignment = Alignment.CenterEnd,
                    ) { confirmButton() }
                }
            }
        }
    }
}
