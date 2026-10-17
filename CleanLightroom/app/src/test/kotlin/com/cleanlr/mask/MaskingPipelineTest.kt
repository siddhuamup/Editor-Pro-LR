package com.cleanlr.mask

import android.graphics.Bitmap
import android.graphics.Color
import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.BrushStrokePoint
import com.cleanlr.model.MaskCombineMode
import com.cleanlr.model.MaskLayer
import com.cleanlr.model.SubMask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Tasks T4-T8: AI Masking, Brush Engine, Gradients & Local Adjustments Unit Test Suite
 */
class MaskingPipelineTest {

    @Test
    fun `test T5 Brush rasterizer generates continuous stroke and smooth feather falloff`() {
        val width = 200
        val height = 200

        // Single dab at (100, 100) with radius 20, feather 50%
        val dab = BrushStrokePoint(x = 100f, y = 100f, radius = 20f, feather = 50f, flow = 1.0f)
        val alphaBuffer = BrushRasterizer.rasterizeBrushPoints(listOf(dab), width, height)

        // Center pixel (100, 100) must be 1.0
        val centerAlpha = alphaBuffer[100 * width + 100]
        assertEquals("Center of dab must have 1.0 alpha", 1.0f, centerAlpha, 1e-4f)

        // Inner radius (dist = 10 <= 20 * (1 - 0.5)) must still be 1.0
        val innerAlpha = alphaBuffer[100 * width + 110]
        assertEquals("Inside inner radius must be 1.0 alpha", 1.0f, innerAlpha, 1e-3f)

        // Outside outer radius (dist = 25 > 20) must be 0.0
        val outsideAlpha = alphaBuffer[100 * width + 125]
        assertEquals("Outside radius must be 0.0 alpha", 0.0f, outsideAlpha, 1e-6f)

        // Feathered edge (dist = 15) must be between 0.0 and 1.0
        val midAlpha = alphaBuffer[100 * width + 115]
        assertTrue("Feathered edge must be between 0 and 1", midAlpha > 0.0f && midAlpha < 1.0f)
    }

    @Test
    fun `test T6 Linear and Radial gradients evaluate smooth mathematical transitions`() {
        val width = 100
        val height = 100

        // 1. Horizontal Linear Gradient from (0, 50) to (100, 50)
        val linMask = SubMask.LinearGradient(
            id = "lin_1",
            startX = 0.0f,
            startY = 0.5f,
            endX = 1.0f,
            endY = 0.5f
        )
        val linBuffer = GradientEvaluator.evaluateLinearGradient(linMask, width, height)

        assertEquals("Start of linear gradient is 0.0", 0.0f, linBuffer[50 * width + 0], 1e-4f)
        assertEquals("Midpoint is 0.5", 0.5f, linBuffer[50 * width + 50], 1e-4f)
        assertEquals("End is 1.0", 1.0f, linBuffer[50 * width + 99], 1e-2f)

        // 2. Radial Gradient at center (0.5, 0.5) with radiusX = 0.3, radiusY = 0.3
        val radMask = SubMask.RadialGradient(
            id = "rad_1",
            centerX = 0.5f,
            centerY = 0.5f,
            radiusX = 0.3f,
            radiusY = 0.3f,
            feather = 50f
        )
        val radBuffer = GradientEvaluator.evaluateRadialGradient(radMask, width, height)

        assertEquals("Center of radial is 1.0", 1.0f, radBuffer[50 * width + 50], 1e-4f)
        assertEquals("Outside radial is 0.0", 0.0f, radBuffer[50 * width + 90], 1e-4f)
    }

    @Test
    fun `test T7 Semantic Layer Order and Subtract Combine Mode confirms Trap 3`() = runBlocking {
        val width = 100
        val height = 100

        // SubMask A: Full area (1.0)
        val fullArea = SubMask.LinearGradient(
            id = "base_sky",
            startX = 0f, startY = 0f, endX = 0f, endY = 0f,
            isInverted = true // Inverted zero gradient gives constant 1.0
        )

        // SubMask B: Brush stroke subtract at center (erases circle of radius 20 at center)
        val brushSubtract = SubMask.Brush(
            id = "brush_cutout",
            combineMode = MaskCombineMode.SUBTRACT,
            points = listOf(BrushStrokePoint(50f, 50f, radius = 20f, feather = 0f, flow = 1.0f))
        )

        // Order 1: Base (1.0) -> Subtract Brush (0.0 at center, 1.0 outside)
        val layerOrder1 = MaskLayer(
            id = "layer_sub_order",
            subMasks = listOf(fullArea, brushSubtract)
        )
        val evaluated1 = MaskEvaluator.evaluateLayer(layerOrder1, width, height)

        // Center must be erased (0.0)
        assertEquals("Center must be subtracted to 0.0", 0.0f, evaluated1[50 * width + 50], 1e-4f)
        // Corner must remain 1.0
        assertEquals("Corner outside brush must remain 1.0", 1.0f, evaluated1[0], 1e-4f)
    }

    @Test
    fun `test T8 Mask-scoped local adjustments strictly modulate pixels within mask boundary`() {
        val width = 10
        val height = 10
        val totalBytes = width * height * 4

        // Create neutral mid-gray image (128, 128, 128, 255)
        val inputImage = ByteArray(totalBytes) { idx ->
            if (idx % 4 == 3) 255.toByte() else 128.toByte()
        }

        // Half mask: left half is 1.0 (masked), right half is 0.0 (unmasked)
        val alphaMask = FloatArray(width * height) { idx ->
            val x = idx % width
            if (x < 5) 1.0f else 0.0f
        }

        // Boost exposure by +1.0 EV
        val localAdj = BasicAdjustments(exposure = 1.0f)
        val result = LocalAdjustmentCompositor.compositeLocalAdjustments(
            rgbaBytes = inputImage,
            alphaMask = alphaMask,
            localAdjustments = localAdj,
            width = width,
            height = height
        )

        // 1. Right half (unmasked, alpha = 0.0) must be 100% UNCHANGED (128)
        for (y in 0 until height) {
            for (x in 5 until width) {
                val byteIdx = (y * width + x) * 4
                val r = result[byteIdx].toInt() and 0xFF
                assertEquals("Unmasked pixel must remain exactly 128", 128, r)
            }
        }

        // 2. Left half (masked, alpha = 1.0) must be brightened by +1 EV (>= 175)
        for (y in 0 until height) {
            for (x in 0 until 5) {
                val byteIdx = (y * width + x) * 4
                val r = result[byteIdx].toInt() and 0xFF
                assertTrue("Masked pixel must be brightened (was $r)", r >= 175)
            }
        }
    }
}
