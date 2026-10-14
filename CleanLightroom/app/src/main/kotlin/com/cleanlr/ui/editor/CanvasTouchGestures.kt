package com.cleanlr.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Mobile Touch Gestures State Machine (PDF Part A Section 3.D):
 * - Canvas Long-Press: While held, shows original photo (Before); on release, returns to edited (After).
 * - Double-Tap on Canvas: Toggles 1:1 Pixel (100%) zoom at tap point; second double-tap fits to screen.
 * - Two-Finger Tap: Instant Undo.
 * - Three-Finger Tap: Instant Redo.
 */
class CanvasGestureStateMachine(
    private val onLongPressChange: (Boolean) -> Unit,
    private val onDoubleTap: (Offset) -> Unit,
    private val onTwoFingerTap: () -> Unit,
    private val onThreeFingerTap: () -> Unit,
    private val onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    var isLongPressActive = false
        private set

    private var lastTapTime = 0L
    private var lastTapPosition = Offset.Zero
    private val doubleTapTimeoutMs = 300L
    private val doubleTapSlop = 48.0f // max distance between two taps

    fun handleSingleFingerDown(timeMs: Long, pos: Offset) {
        // Will be managed with long press delay
    }

    fun handleSingleFingerTap(timeMs: Long, pos: Offset) {
        if (timeMs - lastTapTime <= doubleTapTimeoutMs &&
            (pos - lastTapPosition).getDistance() <= doubleTapSlop
        ) {
            onDoubleTap(pos)
            lastTapTime = 0L
        } else {
            lastTapTime = timeMs
            lastTapPosition = pos
        }
    }

    fun setLongPress(active: Boolean) {
        if (isLongPressActive != active) {
            isLongPressActive = active
            onLongPressChange(active)
        }
    }

    fun triggerTwoFingerTap() {
        if (isLongPressActive) setLongPress(false)
        onTwoFingerTap()
    }

    fun triggerThreeFingerTap() {
        if (isLongPressActive) setLongPress(false)
        onThreeFingerTap()
    }

    fun triggerTransform(centroid: Offset, pan: Offset, zoom: Float) {
        if (isLongPressActive) setLongPress(false)
        onTransform(centroid, pan, zoom)
    }
}

/**
 * Compose PointerInput modifier function to detect all 4 normative gestures.
 */
suspend fun PointerInputScope.detectCanvasTouchGestures(
    onLongPressChange: (Boolean) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTwoFingerTap: () -> Unit,
    onThreeFingerTap: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) = coroutineScope {
    val stateMachine = CanvasGestureStateMachine(
        onLongPressChange = onLongPressChange,
        onDoubleTap = onDoubleTap,
        onTwoFingerTap = onTwoFingerTap,
        onThreeFingerTap = onThreeFingerTap,
        onTransform = onTransform
    )

    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        val downTime = System.currentTimeMillis()
        val downPos = firstDown.position
        var maxPointersSeen = 1
        var hasMovedSignificantly = false
        var isLongPressTriggered = false

        // Launch long press timer job
        val longPressJob = launch {
            delay(350L) // 350ms threshold for Before/After compare
            if (!hasMovedSignificantly && maxPointersSeen == 1) {
                isLongPressTriggered = true
                stateMachine.setLongPress(true)
            }
        }

        var previousCentroid = downPos
        var previousDistance = 0f

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Main)
            val activePointers = event.changes.filter { it.pressed }
            val count = activePointers.size

            if (count > maxPointersSeen) {
                maxPointersSeen = count
                longPressJob.cancel()
                if (isLongPressTriggered) {
                    stateMachine.setLongPress(false)
                    isLongPressTriggered = false
                }
            }

            if (count == 0) {
                // All fingers lifted
                longPressJob.cancel()
                val upTime = System.currentTimeMillis()
                val duration = upTime - downTime

                if (isLongPressTriggered) {
                    stateMachine.setLongPress(false)
                } else if (!hasMovedSignificantly && duration <= 300L) {
                    when (maxPointersSeen) {
                        1 -> stateMachine.handleSingleFingerTap(upTime, downPos)
                        2 -> stateMachine.triggerTwoFingerTap()
                        3 -> stateMachine.triggerThreeFingerTap()
                    }
                }
                break
            } else if (count == 1) {
                val currentChange = activePointers.first()
                val currentPos = currentChange.position
                if ((currentPos - downPos).getDistance() > 18f) {
                    hasMovedSignificantly = true
                    longPressJob.cancel()
                    if (isLongPressTriggered) {
                        stateMachine.setLongPress(false)
                        isLongPressTriggered = false
                    }
                }
            } else if (count >= 2) {
                hasMovedSignificantly = true
                longPressJob.cancel()

                // Multi-finger pinch & pan transform
                val p1 = activePointers[0].position
                val p2 = activePointers[1].position
                val centroid = (p1 + p2) / 2f
                val distance = (p1 - p2).getDistance()

                if (previousDistance > 0f) {
                    val zoomRatio = (distance / previousDistance).coerceIn(0.5f, 2.0f)
                    val pan = centroid - previousCentroid
                    stateMachine.triggerTransform(centroid, pan, zoomRatio)
                }

                previousCentroid = centroid
                previousDistance = distance
            }

            event.changes.forEach { it.consume() }
        }
    }
}
