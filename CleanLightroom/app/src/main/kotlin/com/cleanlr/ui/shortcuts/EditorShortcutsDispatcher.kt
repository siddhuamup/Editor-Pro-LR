package com.cleanlr.ui.shortcuts

import com.cleanlr.ui.editor.ActiveTool
import com.cleanlr.ui.editor.ViewMode

/**
 * Interface representing all shortcuts and touch gestures dispatch targets.
 * Strictly guarantees that all hardware keys and touch gestures route directly
 * through EditorViewModel intents.
 */
interface EditorShortcutsDispatcher {
    fun setComparingOriginal(comparing: Boolean)
    fun toggleComparingOriginal()
    fun toggleSplitView()
    fun toggleCleanScreenMode()
    fun toggleFullScreen()
    fun toggleZoom100Percent()
    fun toggleInfoOverlay()

    fun setStarRating(rating: Int)
    fun pickPhoto()
    fun unflagPhoto()
    fun rejectPhoto()

    fun setViewMode(mode: ViewMode)
    fun toggleCropActive()
    fun setActiveTool(tool: ActiveTool?)
    fun selectBrushTool()
    fun selectLinearGradientMask()
    fun selectRadialGradientMask()

    fun toggleMaskOverlay()
    fun cycleMaskOverlayColor()

    fun adjustBrushSize(delta: Float)
    fun adjustBrushFeather(delta: Float)

    fun setAltClippingMode(mode: Int)

    fun undo()
    fun redo()
    fun copyEditSettings()
    fun pasteEditSettings()
    fun resetAll()
}
