package com.cleanlr.ui.shortcuts

import android.view.KeyEvent
import com.cleanlr.ui.editor.ActiveTool
import com.cleanlr.ui.editor.ViewMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Task T6 Verification: Full Keyboard Shortcuts Mapping (PDF Pages 5 & 6)
 * Tests that every hardware shortcut maps to the exact EditorViewModel intent.
 */
class KeyboardShortcutsHandlerTest {

    private class MockShortcutsDispatcher : EditorShortcutsDispatcher {
        var comparingOriginal: Boolean? = null
        var splitViewToggled = false
        var cleanScreenToggled = false
        var fullScreenToggled = false
        var zoom100Toggled = false
        var infoOverlayToggled = false
        var starRating: Int? = null
        var flagAction: String? = null
        var currentViewMode: ViewMode? = null
        var cropToggled = false
        var currentActiveTool: ActiveTool? = null
        var maskOverlayToggled = false
        var maskColorCycled = false
        var brushSizeDelta: Float? = null
        var brushFeatherDelta: Float? = null
        var altClippingMode: Int? = null
        var undoCalled = false
        var redoCalled = false
        var copySettingsCalled = false
        var pasteSettingsCalled = false
        var resetAllCalled = false

        override fun setComparingOriginal(comparing: Boolean) { comparingOriginal = comparing }
        override fun toggleComparingOriginal() { comparingOriginal = !(comparingOriginal ?: false) }
        override fun toggleSplitView() { splitViewToggled = true }
        override fun toggleCleanScreenMode() { cleanScreenToggled = true }
        override fun toggleFullScreen() { fullScreenToggled = true }
        override fun toggleZoom100Percent() { zoom100Toggled = true }
        override fun toggleInfoOverlay() { infoOverlayToggled = true }
        override fun setStarRating(rating: Int) { starRating = rating }
        override fun pickPhoto() { flagAction = "PICK" }
        override fun unflagPhoto() { flagAction = "UNFLAG" }
        override fun rejectPhoto() { flagAction = "REJECT" }
        override fun setViewMode(mode: ViewMode) { currentViewMode = mode }
        override fun toggleCropActive() { cropToggled = true }
        override fun setActiveTool(tool: ActiveTool?) { currentActiveTool = tool }
        override fun selectBrushTool() { currentActiveTool = ActiveTool.BRUSH }
        override fun selectLinearGradientMask() { currentActiveTool = ActiveTool.LINEAR_GRADIENT }
        override fun selectRadialGradientMask() { currentActiveTool = ActiveTool.RADIAL_GRADIENT }
        override fun toggleMaskOverlay() { maskOverlayToggled = true }
        override fun cycleMaskOverlayColor() { maskColorCycled = true }
        override fun adjustBrushSize(delta: Float) { brushSizeDelta = delta }
        override fun adjustBrushFeather(delta: Float) { brushFeatherDelta = delta }
        override fun setAltClippingMode(mode: Int) { altClippingMode = mode }
        override fun undo() { undoCalled = true }
        override fun redo() { redoCalled = true }
        override fun copyEditSettings() { copySettingsCalled = true }
        override fun pasteEditSettings() { pasteSettingsCalled = true }
        override fun resetAll() { resetAllCalled = true }
    }

    private lateinit var dispatcher: MockShortcutsDispatcher
    private lateinit var handler: KeyboardShortcutsHandler

    @Before
    fun setUp() {
        dispatcher = MockShortcutsDispatcher()
        handler = KeyboardShortcutsHandler(dispatcher)
    }

    @Test
    fun testViewAndNavigationShortcuts() {
        // \ (Backslash): Before / After
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_BACKSLASH, isCtrl = false, isShift = false))
        assertEquals(true, dispatcher.comparingOriginal)

        assertTrue(handler.handleKeyUp(KeyEvent.KEYCODE_BACKSLASH))
        assertEquals(false, dispatcher.comparingOriginal)

        // Y: Split View
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_Y, isCtrl = false, isShift = false))
        assertTrue(dispatcher.splitViewToggled)

        // Tab: Clean Screen Mode
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_TAB, isCtrl = false, isShift = false))
        assertTrue(dispatcher.cleanScreenToggled)

        // F: Full Screen Toggle
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_F, isCtrl = false, isShift = false))
        assertTrue(dispatcher.fullScreenToggled)

        // Space / Z: 100% Zoom Toggle
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_SPACE, isCtrl = false, isShift = false))
        assertTrue(dispatcher.zoom100Toggled)

        dispatcher.zoom100Toggled = false
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_Z, isCtrl = false, isShift = false))
        assertTrue(dispatcher.zoom100Toggled)

        // I: Info Overlay Toggle
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_I, isCtrl = false, isShift = false))
        assertTrue(dispatcher.infoOverlayToggled)
    }

    @Test
    fun testRatingAndFlagsShortcuts() {
        // 1 to 5 Star Ratings
        for (star in 1..5) {
            val key = KeyEvent.KEYCODE_0 + star
            assertTrue(handler.handleKeyDown(key, isCtrl = false, isShift = false))
            assertEquals(star, dispatcher.starRating)
        }

        // 0: Clear Rating
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_0, isCtrl = false, isShift = false))
        assertEquals(0, dispatcher.starRating)

        // P: Pick Flag
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_P, isCtrl = false, isShift = false))
        assertEquals("PICK", dispatcher.flagAction)

        // U: Unflag
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_U, isCtrl = false, isShift = false))
        assertEquals("UNFLAG", dispatcher.flagAction)

        // X: Reject
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_X, isCtrl = false, isShift = false))
        assertEquals("REJECT", dispatcher.flagAction)
    }

    @Test
    fun testToolsAndMaskingShortcuts() {
        // E: Loupe / Edit Mode
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_E, isCtrl = false, isShift = false))
        assertEquals(ViewMode.LOUPE, dispatcher.currentViewMode)

        // G: Grid / Library Mode
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_G, isCtrl = false, isShift = false))
        assertEquals(ViewMode.GRID, dispatcher.currentViewMode)

        // R: Crop & Rotate Tool
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_R, isCtrl = false, isShift = false))
        assertTrue(dispatcher.cropToggled)

        // K: Adjustment Brush Tool
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_K, isCtrl = false, isShift = false))
        assertEquals(ActiveTool.BRUSH, dispatcher.currentActiveTool)

        // M: Linear Gradient Mask
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_M, isCtrl = false, isShift = false))
        assertEquals(ActiveTool.LINEAR_GRADIENT, dispatcher.currentActiveTool)

        // Shift + M: Radial Gradient Mask
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_M, isCtrl = false, isShift = true))
        assertEquals(ActiveTool.RADIAL_GRADIENT, dispatcher.currentActiveTool)

        // O: Toggle Mask Overlay
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_O, isCtrl = false, isShift = false))
        assertTrue(dispatcher.maskOverlayToggled)

        // Shift + O: Cycle Mask Color
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_O, isCtrl = false, isShift = true))
        assertTrue(dispatcher.maskColorCycled)

        // [ / ]: Brush Size
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_LEFT_BRACKET, isCtrl = false, isShift = false))
        assertEquals(-5.0f, dispatcher.brushSizeDelta)

        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_RIGHT_BRACKET, isCtrl = false, isShift = false))
        assertEquals(5.0f, dispatcher.brushSizeDelta)

        // Shift + [ / ]: Brush Feather
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_LEFT_BRACKET, isCtrl = false, isShift = true))
        assertEquals(-5.0f, dispatcher.brushFeatherDelta)

        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_RIGHT_BRACKET, isCtrl = false, isShift = true))
        assertEquals(5.0f, dispatcher.brushFeatherDelta)
    }

    @Test
    fun testCtrlModifierAndAltClippingShortcuts() {
        // Ctrl + Z: Undo
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_Z, isCtrl = true, isShift = false))
        assertTrue(dispatcher.undoCalled)

        // Ctrl + Shift + Z: Redo
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_Z, isCtrl = true, isShift = true))
        assertTrue(dispatcher.redoCalled)

        // Ctrl + Y: Redo
        dispatcher.redoCalled = false
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_Y, isCtrl = true, isShift = false))
        assertTrue(dispatcher.redoCalled)

        // Ctrl + C: Copy Settings
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_C, isCtrl = true, isShift = false))
        assertTrue(dispatcher.copySettingsCalled)

        // Ctrl + V: Paste Settings
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_V, isCtrl = true, isShift = false))
        assertTrue(dispatcher.pasteSettingsCalled)

        // Ctrl + Shift + R: Reset to Original
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_R, isCtrl = true, isShift = true))
        assertTrue(dispatcher.resetAllCalled)

        // Alt: Clipping Mask Preview
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_ALT_LEFT, isCtrl = false, isShift = false, isAlt = true))
        assertEquals(1, dispatcher.altClippingMode)

        assertTrue(handler.handleKeyUp(KeyEvent.KEYCODE_ALT_LEFT, isAlt = true))
        assertEquals(0, dispatcher.altClippingMode)
    }
}
