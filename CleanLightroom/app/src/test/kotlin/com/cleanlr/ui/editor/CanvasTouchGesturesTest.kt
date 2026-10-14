package com.cleanlr.ui.editor

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Task T7 Verification: Mobile Touch Gestures State Machine (PDF Page 6 Section D)
 * - Canvas Long-Press (Before / After toggle)
 * - Double-Tap on Canvas (1:1 100% Zoom Toggle at tap point)
 * - Two-Finger Tap (Instant Undo)
 * - Three-Finger Tap (Instant Redo)
 */
class CanvasTouchGesturesTest {

    private var longPressState: Boolean? = null
    private var doubleTapOffset: Offset? = null
    private var twoFingerTapTriggered = false
    private var threeFingerTapTriggered = false
    private var transformZoom: Float? = null

    private lateinit var stateMachine: CanvasGestureStateMachine

    @Before
    fun setUp() {
        longPressState = null
        doubleTapOffset = null
        twoFingerTapTriggered = false
        threeFingerTapTriggered = false
        transformZoom = null

        stateMachine = CanvasGestureStateMachine(
            onLongPressChange = { longPressState = it },
            onDoubleTap = { doubleTapOffset = it },
            onTwoFingerTap = { twoFingerTapTriggered = true },
            onThreeFingerTap = { threeFingerTapTriggered = true },
            onTransform = { _, _, zoom -> transformZoom = zoom }
        )
    }

    @Test
    fun testLongPressBeforeAfterStateTransition() {
        // Finger held down: Long press activates
        stateMachine.setLongPress(true)
        assertEquals("Long press must activate Before original preview", true, longPressState)
        assertTrue(stateMachine.isLongPressActive)

        // Finger lifted: Long press deactivates
        stateMachine.setLongPress(false)
        assertEquals("Finger release must return to After edited preview", false, longPressState)
        assertFalse(stateMachine.isLongPressActive)
    }

    @Test
    fun testDoubleTapAtPointDetection() {
        val tapPos1 = Offset(240f, 360f)
        val time1 = 1000L

        // First tap: records position, does not trigger double tap
        stateMachine.handleSingleFingerTap(time1, tapPos1)
        assertNull("First tap must not trigger double tap", doubleTapOffset)

        // Second tap at close coordinate within 200ms (< 300ms window)
        val tapPos2 = Offset(245f, 362f)
        val time2 = 1200L
        stateMachine.handleSingleFingerTap(time2, tapPos2)
        assertNotNull("Second tap within 300ms must trigger 100% zoom", doubleTapOffset)
        assertEquals(tapPos2, doubleTapOffset)

        // Subsequent single tap after 500ms (> 300ms) should reset
        doubleTapOffset = null
        stateMachine.handleSingleFingerTap(2000L, Offset(100f, 100f))
        assertNull("Tap after timeout must not trigger double tap", doubleTapOffset)
    }

    @Test
    fun testTwoFingerTapInstantUndo() {
        assertFalse(twoFingerTapTriggered)
        stateMachine.triggerTwoFingerTap()
        assertTrue("Two-finger tap must trigger instant Undo", twoFingerTapTriggered)
    }

    @Test
    fun testThreeFingerTapInstantRedo() {
        assertFalse(threeFingerTapTriggered)
        stateMachine.triggerThreeFingerTap()
        assertTrue("Three-finger tap must trigger instant Redo", threeFingerTapTriggered)
    }

    @Test
    fun testPinchZoomTransformCancelsLongPress() {
        stateMachine.setLongPress(true)
        assertTrue(stateMachine.isLongPressActive)

        // User starts pinching or panning
        stateMachine.triggerTransform(Offset(200f, 200f), Offset(10f, 5f), 1.25f)
        assertFalse("Pinch transform must cancel Before/After long press", stateMachine.isLongPressActive)
        assertEquals(1.25f, transformZoom ?: 0f, 0.001f)
    }
}
