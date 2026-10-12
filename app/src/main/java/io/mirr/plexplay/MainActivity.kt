package io.mirr.plexplay

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.mirr.plexplay.data.ConnectionStore
import io.mirr.plexplay.data.PlexRepository
import io.mirr.plexplay.ui.PlexPlayApp
import io.mirr.plexplay.ui.PlexPlayTheme
import io.mirr.plexplay.ui.PlexViewModel
import io.mirr.plexplay.ui.PlexViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val remoteBackPress = RemoteBackPress()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = PlexRepository(ConnectionStore(applicationContext))
        setContent {
            PlexPlayTheme {
                val viewModel: PlexViewModel = viewModel(
                    factory = PlexViewModelFactory(repository),
                )
                PlexPlayApp(viewModel, onExit = { finish() })
            }
        }
        lifecycleScope.launch {
            AppUpdater.checkForUpdates(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdater.installPendingUpdate(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> remoteBackPress.down(event.downTime, event.repeatCount)
            KeyEvent.ACTION_UP -> if (remoteBackPress.up(event.downTime, event.isCanceled)) {
                onBackPressedDispatcher.onBackPressed()
            }
            else -> remoteBackPress.reset()
        }
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) remoteBackPress.reset()
    }
}
