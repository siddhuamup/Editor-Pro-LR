package com.cleanlr.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualRingBrushCursorTest {

    @Test
    fun `test inner radius equals brush radius`() {
        val radius = 45.0f
        val inner = DualRingBrushMath.calculateInnerRadius(radius)
        assertEquals(45.0f, inner, 1e-5f)
    }

    @Test
    fun `test outer radius equals inner radius when feather is zero`() {
        val radius = 50.0f
        val outer = DualRingBrushMath.calculateOuterRadius(radius, featherPercent = 0.0f)
        assertEquals(50.0f, outer, 1e-5f)
    }

    @Test
    fun `test outer radius scales according to feather percent formula`() {
        val radius = 40.0f
        // 50% feather -> 40 * 1.5 = 60.0
        val outer50 = DualRingBrushMath.calculateOuterRadius(radius, featherPercent = 50.0f)
        assertEquals(60.0f, outer50, 1e-4f)

        // 100% feather -> 40 * 2.0 = 80.0
        val outer100 = DualRingBrushMath.calculateOuterRadius(radius, featherPercent = 100.0f)
        assertEquals(80.0f, outer100, 1e-4f)

        // 25% feather -> 40 * 1.25 = 50.0
        val outer25 = DualRingBrushMath.calculateOuterRadius(radius, featherPercent = 25.0f)
        assertEquals(50.0f, outer25, 1e-4f)
    }

    @Test
    fun `test feather percent clamping protects against negative or over-range values`() {
        val radius = 60.0f
        val outerNegative = DualRingBrushMath.calculateOuterRadius(radius, featherPercent = -20.0f)
        assertEquals(60.0f, outerNegative, 1e-5f)

        val outerOver = DualRingBrushMath.calculateOuterRadius(radius, featherPercent = 150.0f)
        assertEquals(120.0f, outerOver, 1e-4f) // clamped to 100% -> 60 * 2.0
    }

    @Test
    fun `test outer radius is strictly greater than or equal to inner radius`() {
        val testRadii = listOf(10f, 25f, 50f, 100f, 250f)
        val testFeathers = listOf(0f, 10f, 33.3f, 50f, 75f, 100f)

        for (r in testRadii) {
            val inner = DualRingBrushMath.calculateInnerRadius(r)
            for (f in testFeathers) {
                val outer = DualRingBrushMath.calculateOuterRadius(r, f)
                assertTrue("Outer radius ($outer) must be >= inner radius ($inner)", outer >= inner)
            }
        }
    }
}
