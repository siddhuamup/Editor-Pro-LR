package com.cleanlr

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.cleanlr.ui.editor.EditorScreen
import com.cleanlr.ui.editor.EditorViewModel
import com.cleanlr.ui.shortcuts.KeyboardShortcutsHandler
import com.cleanlr.ui.theme.CleanLightroomTheme

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()
    private lateinit var shortcutsHandler: KeyboardShortcutsHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        shortcutsHandler = KeyboardShortcutsHandler(viewModel)

        setContent {
            CleanLightroomTheme {
                EditorScreen(viewModel = viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && shortcutsHandler.handleKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && shortcutsHandler.handleKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
