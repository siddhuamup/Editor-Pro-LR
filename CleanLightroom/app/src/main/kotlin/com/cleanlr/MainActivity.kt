package com.cleanlr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.cleanlr.ui.LightroomLibraryScreen
import com.cleanlr.ui.editor.EditorScreen
import com.cleanlr.ui.editor.EditorViewModel
import com.cleanlr.ui.shortcuts.KeyboardShortcutsHandler
import com.cleanlr.ui.theme.CleanLightroomTheme

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()
    private var isEditorVisible by mutableStateOf(true)
    private lateinit var shortcutsHandler: KeyboardShortcutsHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        shortcutsHandler = KeyboardShortcutsHandler(viewModel)

        consumeIncomingIntent(intent)

        setContent {
            CleanLightroomTheme {
                BackHandler(enabled = isEditorVisible) {
                    isEditorVisible = false
                }

                if (isEditorVisible) {
                    EditorScreen(
                        viewModel = viewModel,
                        onBack = { isEditorVisible = false }
                    )
                } else {
                    LightroomLibraryScreen(
                        onPhotoSelected = { uri ->
                            viewModel.setCleanScreenMode(false)
                            viewModel.loadFromUri(uri)
                            isEditorVisible = true
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIncomingIntent(intent)
    }

    private fun consumeIncomingIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        uri?.let {
            viewModel.loadFromUri(it)
            isEditorVisible = true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!::shortcutsHandler.isInitialized) return super.onKeyDown(keyCode, event)
        if (currentFocus is android.widget.EditText) return super.onKeyDown(keyCode, event)
        if (event != null && shortcutsHandler.handleKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!::shortcutsHandler.isInitialized) return super.onKeyUp(keyCode, event)
        if (currentFocus is android.widget.EditText) return super.onKeyUp(keyCode, event)
        if (event != null && shortcutsHandler.handleKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
