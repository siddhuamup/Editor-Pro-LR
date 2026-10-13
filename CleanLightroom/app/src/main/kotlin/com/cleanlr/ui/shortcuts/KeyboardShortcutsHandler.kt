package com.cleanlr.ui.shortcuts

import android.view.KeyEvent
import com.cleanlr.ui.editor.EditorViewModel

class KeyboardShortcutsHandler(private val viewModel: EditorViewModel) {

    fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) {
                        viewModel.redo()
                    } else {
                        viewModel.undo()
                    }
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    viewModel.redo()
                    return true
                }
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BACKSLASH -> {
                viewModel.setComparingOriginal(true)
                return true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                viewModel.setAltClippingMode(1)
                return true
            }
            KeyEvent.KEYCODE_Z -> {
                // Zoom toggle can be dispatched
                return true
            }
        }
        return false
    }

    fun handleKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACKSLASH -> {
                viewModel.setComparingOriginal(false)
                return true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                viewModel.setAltClippingMode(0)
                return true
            }
        }
        return false
    }
}
