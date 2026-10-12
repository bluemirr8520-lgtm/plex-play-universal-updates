package io.mirr.plexplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** Registered before player/overlay handlers, which keep their normal Back priority. */
@Composable
internal fun AppBackHandler(onBack: () -> Boolean, onExit: () -> Unit) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        if (!onBack()) confirmExit = true
    }
    if (confirmExit) {
        val stayFocus = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("앱을 종료할까요?") },
            text = { Text("계속 사용하려면 취소하거나 뒤로가기를 눌러 주세요.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onExit() }) { Text("종료") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmExit = false },
                    modifier = Modifier.focusRequester(stayFocus),
                ) { Text("계속 사용") }
                // Request focus only after this dialog's button has been composed.
                LaunchedEffect(stayFocus) { stayFocus.requestFocus() }
            },
        )
    }
}
