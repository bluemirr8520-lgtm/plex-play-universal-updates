package io.mirr.plexplay.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.mirr.plexplay.data.WatchedCollectionSettings
import io.mirr.plexplay.data.normalizeCollectionName

@Composable
internal fun CollectionSettingsDialog(
    settings: WatchedCollectionSettings,
    isSaving: Boolean,
    onSave: (WatchedCollectionSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var normal by rememberSaveable(settings.defaultName) { mutableStateOf(settings.defaultName) }
    var special by rememberSaveable(settings.specialName) { mutableStateOf(settings.specialName) }
    val normalFocus = remember { FocusRequester() }
    val specialFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val resetFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val valid = WatchedCollectionSettings(normal, special).normalizedOrNull()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .85f).dp
    LaunchedEffect(Unit) { saveFocus.requestFocus() }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(color = Color.Black, shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("시청 완료 컬렉션", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                Text("이 기기에 저장하며 자동·수동 시청 완료와 시리즈 완료에 공통 적용합니다.", color = Color.LightGray)
                val colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = PlexGold, focusedLabelColor = PlexGold,
                    unfocusedBorderColor = Color.Gray, unfocusedLabelColor = Color.LightGray,
                    cursorColor = PlexGold,
                )
                OutlinedTextField(
                    value = normal, onValueChange = { normal = it },
                    label = { Text("일반 컬렉션명 (기본 KILL)") }, singleLine = true,
                    enabled = !isSaving, isError = normalizeCollectionName(normal) == null,
                    colors = colors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { specialFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(normalFocus)
                        .collectionFieldNavigation(closeFocus, specialFocus),
                )
                OutlinedTextField(
                    value = special, onValueChange = { special = it },
                    label = { Text("예외 경로 컬렉션명 (기본 123)") }, singleLine = true,
                    enabled = !isSaving, isError = normalizeCollectionName(special) == null,
                    colors = colors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid != null) saveFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(specialFocus)
                        .collectionFieldNavigation(normalFocus, if (valid != null) saveFocus else resetFocus),
                )
                if (valid == null) Text("컬렉션명은 줄바꿈 없이 1~100자로 입력해 주세요.", color = MaterialTheme.colorScheme.error)
                Text("예외 기준: 앞부분과 무관하게 GDRIVE/VIDEO/AV/자막B/ 아래\n• 모든 깊이의 NO_META 폴더\n• 바로 아래의 기타 폴더\n각 폴더의 하위 폴더도 포함합니다. NO_META2처럼 비슷한 이름은 제외합니다.", color = Color.LightGray)
                Text("다음 시청 완료부터 기존 컬렉션을 지정한 이름 하나로 교체합니다. 저장만으로 Plex의 기존 태그를 일괄 변경하지 않습니다.", color = Color.LightGray)
                CollectionSettingsButton(if (isSaving) "저장 중…" else "저장", !isSaving && valid != null,
                    saveFocus, specialFocus, resetFocus) { valid?.let(onSave) }
                CollectionSettingsButton("기본 이름으로 되돌리기", !isSaving,
                    resetFocus, if (valid != null) saveFocus else specialFocus, closeFocus) {
                    normal = "KILL"
                    special = "123"
                }
                CollectionSettingsButton("취소 / 뒤로", !isSaving, closeFocus, resetFocus, normalFocus, onDismiss)
            }
        }
    }
}

// A single-line text field must not trap the remote's up/down keys in cursor movement.
private fun Modifier.collectionFieldNavigation(up: FocusRequester, down: FocusRequester): Modifier =
    onPreviewKeyEvent { event ->
        val target = when (event.key) { Key.DirectionUp -> up; Key.DirectionDown -> down; else -> null }
        if (target == null) false else {
            if (event.type == KeyEventType.KeyDown) target.requestFocus()
            true
        }
    }

@Composable
private fun CollectionSettingsButton(
    label: String, enabled: Boolean, focus: FocusRequester, up: FocusRequester, down: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
            .focusProperties { this.up = up; this.down = down }
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.DarkGray, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) PlexGold else Color(0xFF252525),
            contentColor = if (focused) Color.Black else Color.White,
        ),
    ) { Text(label) }
}
