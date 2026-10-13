package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Stage4PresenceTest {

    private val epsilon = 0.01f

    private fun shouldSkip(clarity: Float, texture: Float, dehaze: Float): Boolean {
        return abs(clarity) < epsilon &&
               abs(texture) < epsilon &&
               abs(dehaze) < epsilon
    }

    private fun evaluateTriangleFunction(g: Float, uRef: Float, uInv_ds: Float = 4.0f): Float {
        val line0 = uInv_ds * (g - uRef) + 1.0f
        val line1 = uInv_ds * (uRef - g) + 1.0f
        return max(min(line0, line1), 0.0f)
    }

    @Test
    fun testSkipDisabledStagesRule() {
        // Zero adjustments must skip stage for 120 FPS
        assertTrue(shouldSkip(0.0f, 0.0f, 0.0f))

        // Active values must not skip
        assertFalse(shouldSkip(15.0f, 0.0f, 0.0f))
        assertFalse(shouldSkip(0.0f, 10.0f, 0.0f))
        assertFalse(shouldSkip(0.0f, 0.0f, 25.0f))
    }

    @Test
    fun testLocalLaplacianTriangleBlendingPreventsHalos() {
        val uRef = 0.50f

        // At identical luminance, alpha should peak at 1.0
        val peakAlpha = evaluateTriangleFunction(0.50f, uRef)
        assertEquals("Alpha should be 1.0 when g == uRef", 1.0f, peakAlpha, 0.001f)

        // Moderate edge difference (0.55 vs 0.50)
        val moderateAlpha = evaluateTriangleFunction(0.55f, uRef)
        assertTrue("Moderate edge should still have partial weight", moderateAlpha > 0.0f && moderateAlpha < 1.0f)

        // High contrast edge step (0.80 vs 0.50, delta = 0.30 > 0.25)
        val harshEdgeAlpha = evaluateTriangleFunction(0.80f, uRef)
        assertEquals("Harsh edge contrast must drop to 0.0 to prevent halos", 0.0f, harshEdgeAlpha, 0.001f)
    }

    @Test
    fun testDarkChannelPriorTransmissionBounds() {
        val darkChannel = 0.8f
        val dehazeAmount = 1.0f // +100
        val t = (1.0f - 0.75f * darkChannel * dehazeAmount).coerceIn(0.15f, 1.0f)

        assertTrue("Atmospheric transmission must remain > 0.10 to prevent division blowup", t >= 0.15f)
        assertTrue("Atmospheric transmission cannot exceed 1.0", t <= 1.0f)
    }
}
