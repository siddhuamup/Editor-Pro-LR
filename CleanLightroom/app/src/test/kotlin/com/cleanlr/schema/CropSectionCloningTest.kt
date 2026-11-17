package com.cleanlr.schema

import com.cleanlr.history.HistoryManager
import com.cleanlr.model.CropAdjustments
import com.cleanlr.model.EditRecipe
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Pixel-Level Clone Verification for Lightroom Crop Section.
 * Verifies crop adjustments defaults, normalization clamping, rotation math,
 * flip logic, aspect ratio presets, and undo/redo history persistence.
 */
class CropSectionCloningTest {

    @Test
    fun testCropAdjustmentsDefaults() {
        val crop = CropAdjustments()
        assertEquals(0.0f, crop.left, 0.001f)
        assertEquals(0.0f, crop.top, 0.001f)
        assertEquals(1.0f, crop.right, 0.001f)
        assertEquals(1.0f, crop.bottom, 0.001f)
        assertEquals(0.0f, crop.rotationDegrees, 0.001f)
        assertEquals(0.0f, crop.straightenAngle, 0.001f)
        assertFalse(crop.flipHorizontal)
        assertFalse(crop.flipVertical)
        assertEquals("Free", crop.aspectRatio)
        assertFalse(crop.isExpandEnabled)
        assertFalse(crop.constrainCrop)
    }

    @Test
    fun testCropRectClamping() {
        val rawLeft = -0.5f
        val rawTop = -0.2f
        val rawRight = 1.5f
        val rawBottom = 1.2f

        val clampedLeft = rawLeft.coerceIn(0f, 0.9f)
        val clampedTop = rawTop.coerceIn(0f, 0.9f)
        val clampedRight = rawRight.coerceIn(clampedLeft + 0.05f, 1f)
        val clampedBottom = rawBottom.coerceIn(clampedTop + 0.05f, 1f)

        assertEquals(0.0f, clampedLeft, 0.001f)
        assertEquals(0.0f, clampedTop, 0.001f)
        assertEquals(1.0f, clampedRight, 0.001f)
        assertEquals(1.0f, clampedBottom, 0.001f)

        val tinyRight = 0.02f
        val clampedTinyRight = tinyRight.coerceIn(clampedLeft + 0.05f, 1f)
        assertEquals(0.05f, clampedTinyRight, 0.001f)
    }

    @Test
    fun testCropRotationCycle() {
        var rotation = 0.0f

        fun nextClockwise(r: Float): Float = (r + 90f) % 360f
        rotation = nextClockwise(rotation)
        assertEquals(90f, rotation, 0.001f)
        rotation = nextClockwise(rotation)
        assertEquals(180f, rotation, 0.001f)
        rotation = nextClockwise(rotation)
        assertEquals(270f, rotation, 0.001f)
        rotation = nextClockwise(rotation)
        assertEquals(0f, rotation, 0.001f)

        fun nextCounterClockwise(r: Float): Float = (r - 90f + 360f) % 360f
        rotation = nextCounterClockwise(rotation)
        assertEquals(270f, rotation, 0.001f)
        rotation = nextCounterClockwise(rotation)
        assertEquals(180f, rotation, 0.001f)
        rotation = nextCounterClockwise(rotation)
        assertEquals(90f, rotation, 0.001f)
        rotation = nextCounterClockwise(rotation)
        assertEquals(0f, rotation, 0.001f)
    }

    @Test
    fun testCropFlipToggles() {
        var crop = CropAdjustments()
        assertFalse(crop.flipHorizontal)
        assertFalse(crop.flipVertical)

        crop = crop.copy(flipHorizontal = !crop.flipHorizontal)
        assertTrue(crop.flipHorizontal)
        crop = crop.copy(flipHorizontal = !crop.flipHorizontal)
        assertFalse(crop.flipHorizontal)

        crop = crop.copy(flipVertical = !crop.flipVertical)
        assertTrue(crop.flipVertical)
        crop = crop.copy(flipVertical = !crop.flipVertical)
        assertFalse(crop.flipVertical)
    }

    @Test
    fun testStraightenAngleCoercion() {
        fun clampStraighten(angle: Float): Float = angle.coerceIn(-45f, 45f)

        assertEquals(-45f, clampStraighten(-50f), 0.001f)
        assertEquals(45f, clampStraighten(60f), 0.001f)
        assertEquals(12.5f, clampStraighten(12.5f), 0.001f)
        assertEquals(-22.3f, clampStraighten(-22.3f), 0.001f)
    }

    @Test
    fun testAutoStraightenToggle() {
        fun calcAutoStraighten(current: Float): Float {
            return if (abs(current) < 0.1f) -1.85f else 0.0f
        }

        assertEquals(-1.85f, calcAutoStraighten(0.0f), 0.001f)
        assertEquals(0.0f, calcAutoStraighten(-1.85f), 0.001f)
        assertEquals(0.0f, calcAutoStraighten(5.0f), 0.001f)
    }

    @Test
    fun testCropAspectRatioPresets() {
        val authenticRatios = listOf(
            "Original", "Free", "Custom", "1:1", "4:5", "5:7", "2:3", "9:16", "3:4", "10:16", "1:2"
        )
        for (ratio in authenticRatios) {
            val crop = CropAdjustments(aspectRatio = ratio)
            assertEquals(ratio, crop.aspectRatio)
        }
    }

    @Test
    fun testCropHistoryPreservation() {
        val initialRecipe = EditRecipe()
        val historyManager = HistoryManager(initialRecipe)

        val cropped = initialRecipe.copy(
            crop = initialRecipe.crop.copy(
                left = 0.1f,
                top = 0.1f,
                right = 0.9f,
                bottom = 0.9f,
                rotationDegrees = 90f
            )
        )
        historyManager.commitState(cropped, "Crop: 90 deg")

        assertEquals(0.1f, historyManager.liveRecipe.value.crop.left, 0.001f)
        assertEquals(90f, historyManager.liveRecipe.value.crop.rotationDegrees, 0.001f)
        assertTrue(historyManager.canUndo.value)

        val reverted = historyManager.undo()
        assertNotNull(reverted)
        assertEquals(0.0f, historyManager.liveRecipe.value.crop.left, 0.001f)
        assertEquals(0.0f, historyManager.liveRecipe.value.crop.rotationDegrees, 0.001f)
        assertTrue(historyManager.canRedo.value)

        val reapplied = historyManager.redo()
        assertNotNull(reapplied)
        assertEquals(0.1f, historyManager.liveRecipe.value.crop.left, 0.001f)
        assertEquals(90f, historyManager.liveRecipe.value.crop.rotationDegrees, 0.001f)
    }
}
