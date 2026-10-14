package com.cleanlr.ui.shortcuts

import android.view.KeyEvent
import com.cleanlr.ui.editor.ActiveTool
import com.cleanlr.ui.editor.ViewMode

/**
 * Task T6: Full Keyboard Shortcuts Mapping (PDF Part A Section 3, Pages 5 & 6)
 * Strictly dispatches through EditorViewModel intents only.
 */
class KeyboardShortcutsHandler(private val viewModel: EditorShortcutsDispatcher) {

    /**
     * Core shortcut resolution decoupled from Android framework for 100% unit testability.
     */
    fun handleKeyDown(keyCode: Int, isCtrl: Boolean, isShift: Boolean, isAlt: Boolean = false): Boolean {
        // ---------------------------------------------------------------------
        // 1. Ctrl / Cmd Modified Shortcuts
        // ---------------------------------------------------------------------
        if (isCtrl) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    if (isShift) {
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
                KeyEvent.KEYCODE_C -> {
                    viewModel.copyEditSettings()
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    viewModel.pasteEditSettings()
                    return true
                }
                KeyEvent.KEYCODE_R -> {
                    if (isShift) {
                        viewModel.resetAll()
                        return true
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 2. View & Navigation Shortcuts (Part A Section 3.A)
        // ---------------------------------------------------------------------
        when (keyCode) {
            KeyEvent.KEYCODE_BACKSLASH -> {
                // Before / After Toggle (Hold or Press)
                viewModel.setComparingOriginal(true)
                return true
            }
            KeyEvent.KEYCODE_Y -> {
                // Split View (Before left, After right)
                viewModel.toggleSplitView()
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                // Clean Screen Mode (Hide all UI panels)
                viewModel.toggleCleanScreenMode()
                return true
            }
            KeyEvent.KEYCODE_F -> {
                // Full Screen Toggle
                viewModel.toggleFullScreen()
                return true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_Z -> {
                // Toggle 100% Zoom / Fit to Screen
                viewModel.toggleZoom100Percent()
                return true
            }
            KeyEvent.KEYCODE_I -> {
                // Info Overlay Toggle (EXIF info + Histogram)
                viewModel.toggleInfoOverlay()
                return true
            }

            // -----------------------------------------------------------------
            // 3. Rating & Flags Shortcuts (Part A Section 3.B)
            // -----------------------------------------------------------------
            KeyEvent.KEYCODE_0 -> {
                viewModel.setStarRating(0)
                return true
            }
            KeyEvent.KEYCODE_1 -> {
                viewModel.setStarRating(1)
                return true
            }
            KeyEvent.KEYCODE_2 -> {
                viewModel.setStarRating(2)
                return true
            }
            KeyEvent.KEYCODE_3 -> {
                viewModel.setStarRating(3)
                return true
            }
            KeyEvent.KEYCODE_4 -> {
                viewModel.setStarRating(4)
                return true
            }
            KeyEvent.KEYCODE_5 -> {
                viewModel.setStarRating(5)
                return true
            }
            KeyEvent.KEYCODE_P -> {
                viewModel.pickPhoto()
                return true
            }
            KeyEvent.KEYCODE_U -> {
                viewModel.unflagPhoto()
                return true
            }
            KeyEvent.KEYCODE_X -> {
                viewModel.rejectPhoto()
                return true
            }

            // -----------------------------------------------------------------
            // 4. Tools & Masking Shortcuts (Part A Section 3.C)
            // -----------------------------------------------------------------
            KeyEvent.KEYCODE_E -> {
                viewModel.setViewMode(ViewMode.LOUPE)
                return true
            }
            KeyEvent.KEYCODE_G -> {
                viewModel.setViewMode(ViewMode.GRID)
                return true
            }
            KeyEvent.KEYCODE_R -> {
                viewModel.toggleCropActive()
                return true
            }
            KeyEvent.KEYCODE_K -> {
                viewModel.selectBrushTool()
                return true
            }
            KeyEvent.KEYCODE_M -> {
                if (isShift) {
                    viewModel.selectRadialGradientMask()
                } else {
                    viewModel.selectLinearGradientMask()
                }
                return true
            }
            KeyEvent.KEYCODE_O -> {
                if (isShift) {
                    viewModel.cycleMaskOverlayColor()
                } else {
                    viewModel.toggleMaskOverlay()
                }
                return true
            }
            KeyEvent.KEYCODE_LEFT_BRACKET -> {
                if (isShift) {
                    viewModel.adjustBrushFeather(-5.0f)
                } else {
                    viewModel.adjustBrushSize(-5.0f)
                }
                return true
            }
            KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                if (isShift) {
                    viewModel.adjustBrushFeather(5.0f)
                } else {
                    viewModel.adjustBrushSize(5.0f)
                }
                return true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                viewModel.setAltClippingMode(1)
                return true
            }
        }

        return false
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val meta = try { event.metaState } catch (_: Throwable) { 0 }
        val isCtrl = (meta and (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON)) != 0 ||
                try { event.isCtrlPressed } catch (_: Throwable) { false }
        val isShift = (meta and (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON)) != 0 ||
                try { event.isShiftPressed } catch (_: Throwable) { false }
        val isAlt = (meta and (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON)) != 0 ||
                try { event.isAltPressed } catch (_: Throwable) { false }

        return handleKeyDown(keyCode, isCtrl, isShift, isAlt)
    }

    fun handleKeyUp(keyCode: Int, isAlt: Boolean = false): Boolean {
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

    fun handleKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val meta = try { event.metaState } catch (_: Throwable) { 0 }
        val isAlt = (meta and (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON)) != 0 ||
                try { event.isAltPressed } catch (_: Throwable) { false }
        return handleKeyUp(keyCode, isAlt)
    }
}
