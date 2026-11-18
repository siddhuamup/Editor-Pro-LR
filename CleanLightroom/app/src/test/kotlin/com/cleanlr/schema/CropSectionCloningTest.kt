package com.cleanlr.schema

import com.cleanlr.engine.CropGeometryEngine
import com.cleanlr.engine.GenerativeExpandEngine
import com.cleanlr.history.HistoryManager
import com.cleanlr.model.CropAdjustments
import com.cleanlr.model.EditRecipe
import com.cleanlr.model.GuidedLine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.tan

/**
 * Complete Verification Suite for Crop, Flip, Ratios, Expand, Auto-Straighten & Geometry Engine.
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
        assertFalse(crop.aspectLocked)
        assertFalse(crop.isExpandEnabled)
        assertFalse(crop.constrainCrop)
        assertEquals("Off", crop.uprightMode)
    }

    @Test
    fun testExactAspectRatioCalculation() {
        // 1200x800 landscape photo (aspect = 1.5)
        val imgAspect = 1200f / 800f

        // 1:1 Square -> width must be 800/1200 = 0.6667, height = 1.0
        val sq = CropGeometryEngine.computeAspectCropRect(1.0f, imgAspect)
        assertEquals(1.0f, ((sq.right - sq.left) * 1200f) / ((sq.bottom - sq.top) * 800f), 0.005f)
        assertEquals(0.0f, sq.top, 0.001f)
        assertEquals(1.0f, sq.bottom, 0.001f)

        // 4:5 Portrait -> pixel aspect = 0.8
        val p45 = CropGeometryEngine.computeAspectCropRect(4f / 5f, imgAspect)
        assertEquals(0.8f, ((p45.right - p45.left) * 1200f) / ((p45.bottom - p45.top) * 800f), 0.005f)

        // 16:9 Landscape -> wider than 3:2, so width = 1.0, height cropped vertically
        val w169 = CropGeometryEngine.computeAspectCropRect(16f / 9f, imgAspect)
        assertEquals(16f / 9f, ((w169.right - w169.left) * 1200f) / ((w169.bottom - w169.top) * 800f), 0.005f)
        assertEquals(0.0f, w169.left, 0.001f)
        assertEquals(1.0f, w169.right, 0.001f)

        // 1.91:1 Instagram Landscape
        val igLand = CropGeometryEngine.parseAspectRatio("1.91:1", imgAspect)
        assertNotNull(igLand)
        assertEquals(1.91f, igLand!!, 0.001f)
    }

    @Test
    fun testAspectLockedHandleDragPreservesExactRatio() {
        val imgAspect = 1.5f // 1200x800
        val initialRect = CropGeometryEngine.computeAspectCropRect(1.0f, imgAspect)
        val lockedCrop = CropAdjustments(
            left = initialRect.left,
            top = initialRect.top,
            right = initialRect.right,
            bottom = initialRect.bottom,
            aspectRatio = "1:1",
            aspectLocked = true
        )

        val dragged = CropGeometryEngine.applyHandleDrag(
            crop = lockedCrop,
            handle = CropGeometryEngine.DragHandle.BOTTOM_RIGHT,
            normDx = -0.15f,
            normDy = -0.05f,
            effectiveImageAspect = imgAspect
        )
        val resultPixelAspect = ((dragged.right - dragged.left) * imgAspect) / (dragged.bottom - dragged.top)
        assertEquals(1.0f, resultPixelAspect, 0.01f)
        assertTrue(dragged.right < lockedCrop.right)
        assertTrue(dragged.bottom < lockedCrop.bottom)
    }

    @Test
    fun testExpandModeAllowsOutwardBoundsAndComputesExpandRegion() {
        val expandCrop = CropAdjustments(isExpandEnabled = true)
        val expanded = CropGeometryEngine.applyHandleDrag(
            crop = expandCrop,
            handle = CropGeometryEngine.DragHandle.TOP_LEFT,
            normDx = -0.20f,
            normDy = -0.15f,
            effectiveImageAspect = 1.5f
        )
        assertEquals(-0.20f, expanded.left, 0.001f)
        assertEquals(-0.15f, expanded.top, 0.001f)

        val region = GenerativeExpandEngine.computeExpandRegion(
            expandCrop.copy(left = expanded.left, top = expanded.top, right = 1.20f, bottom = 1.15f),
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertTrue(region.hasExpansion)
        assertEquals(200, region.padLeftPx)
        assertEquals(150, region.padTopPx)
        assertEquals(200, region.padRightPx)
        assertEquals(150, region.padBottomPx)
        assertEquals(1400, region.outputWidth)
        assertEquals(1300, region.outputHeight)
        assertTrue(region.expansionPercent > 50)
    }

    @Test
    fun testRealAutoStraightenDetectsTiltedHorizon() {
        // Build a synthetic 128x128 grayscale image with a sharp horizon tilted by +5.0 degrees
        val w = 128
        val h = 128
        val luma = FloatArray(w * h)
        val tiltRad = Math.toRadians(5.0)
        val slope = tan(tiltRad).toFloat()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val horizonY = (h / 2f) + (x - w / 2f) * slope
                luma[y * w + x] = if (y < horizonY) 220f else 30f
            }
        }
        val correction = CropGeometryEngine.analyzeStraightenFromLuma(luma, w, h)
        // Horizon tilts +5° (down-right), so correction must be close to -5° (or +5° depending on sign convention, non-zero & ~5° magnitude)
        assertEquals(5.0f, abs(correction), 1.2f)
    }

    @Test
    fun testGuidedUprightSolverComputesVerticalPerspectiveAndRotation() {
        // Two converging vertical building edges (leaning inward toward top)
        val guides = listOf(
            GuidedLine("g1", x1 = 0.28f, y1 = 0.15f, x2 = 0.20f, y2 = 0.85f),
            GuidedLine("g2", x1 = 0.72f, y1 = 0.15f, x2 = 0.80f, y2 = 0.85f)
        )
        val result = CropGeometryEngine.solveGuidedUpright(guides, imageAspect = 1.5f)
        assertTrue("Expected non-zero vertical perspective correction, got ${result.verticalPerspective}", abs(result.verticalPerspective) > 10f)
        assertEquals(0f, result.geometryRotate, 0.5f)

        // Single tilted horizon guide
        val horizonGuide = listOf(GuidedLine("h1", x1 = 0.1f, y1 = 0.45f, x2 = 0.9f, y2 = 0.55f))
        val levelResult = CropGeometryEngine.solveGuidedUpright(horizonGuide, imageAspect = 1.5f)
        assertTrue("Expected non-zero rotation for tilted horizon, got ${levelResult.geometryRotate}", abs(levelResult.geometryRotate) > 2f)
    }

    @Test
    fun testConstrainCropComputesAutoScaleToEliminateCornerWedges() {
        val rotatedCrop = CropAdjustments(
            straightenAngle = 15f,
            verticalPerspective = 35f,
            constrainCrop = true
        )
        val extraScale = CropGeometryEngine.computeConstrainedScale(rotatedCrop, effectiveImageAspect = 1.5f)
        assertTrue("Constrain crop should zoom > 1.0f to eliminate empty corners, got $extraScale", extraScale > 1.15f)

        // Verify all 4 corners map inside [0..1] after applying constrained extraScale
        val corners = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)
        for ((u, v) in corners) {
            val (su, sv) = CropGeometryEngine.inverseTransformUv(u, v, rotatedCrop, 1.5f, extraScale)
            assertTrue("Corner ($u,$v) mapped outside U=$su", su in 0f..1f)
            assertTrue("Corner ($u,$v) mapped outside V=$sv", sv in 0f..1f)
        }
    }

    @Test
    fun testCropFlipRotationAndHistoryPreservation() {
        val initialRecipe = EditRecipe()
        val historyManager = HistoryManager(initialRecipe)

        val edited = initialRecipe.copy(
            crop = initialRecipe.crop.copy(
                left = 0.1f,
                top = 0.1f,
                right = 0.9f,
                bottom = 0.9f,
                rotationDegrees = 90f,
                flipHorizontal = true,
                flipVertical = true,
                verticalPerspective = 25f
            )
        )
        historyManager.commitState(edited, "Crop & Geometry")

        assertTrue(historyManager.liveRecipe.value.crop.flipHorizontal)
        assertTrue(historyManager.liveRecipe.value.crop.flipVertical)
        assertEquals(25f, historyManager.liveRecipe.value.crop.verticalPerspective, 0.001f)
        assertTrue(historyManager.canUndo.value)

        historyManager.undo()
        assertFalse(historyManager.liveRecipe.value.crop.flipHorizontal)
        assertEquals(0f, historyManager.liveRecipe.value.crop.verticalPerspective, 0.001f)

        historyManager.redo()
        assertTrue(historyManager.liveRecipe.value.crop.flipHorizontal)
        assertEquals(90f, historyManager.liveRecipe.value.crop.rotationDegrees, 0.001f)
    }
}
