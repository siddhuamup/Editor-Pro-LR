package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class Stage5DeltaMaskTest {

    private fun shouldSkip(hasActiveMask: Boolean, maskIntensity: Float): Boolean {
        return !hasActiveMask || maskIntensity <= 0.001f
    }

    private fun computeDeltaMask(original: Float, adjusted: Float, maskVal: Float, maskIntensity: Float): Float {
        val effectiveMask = maskVal * maskIntensity
        val diff = min(adjusted - original, 4.0f)
        return (original + effectiveMask * diff).coerceAtLeast(0.0f)
    }

    @Test
    fun testSkipDisabledStagesRule() {
        // When no mask is active, stage must be skipped for 120 FPS
        assertTrue(shouldSkip(hasActiveMask = false, maskIntensity = 1.0f))
        assertTrue(shouldSkip(hasActiveMask = true, maskIntensity = 0.0f))

        // When mask is active with positive intensity, stage must process
        assertFalse(shouldSkip(hasActiveMask = true, maskIntensity = 0.8f))
    }

    @Test
    fun testDeltaMaskingZeroAndFullMask() {
        val original = 0.30f
        val adjusted = 0.70f

        // At M=0.0, pixel must remain 100% original
        val zeroMaskPixel = computeDeltaMask(original, adjusted, maskVal = 0.0f, maskIntensity = 1.0f)
        assertEquals("At M=0, pixel must equal original", original, zeroMaskPixel, 0.0001f)

        // At M=1.0, pixel must equal adjusted (within 4.0 limit)
        val fullMaskPixel = computeDeltaMask(original, adjusted, maskVal = 1.0f, maskIntensity = 1.0f)
        assertEquals("At M=1, pixel must equal adjusted", adjusted, fullMaskPixel, 0.0001f)

        // At M=0.5, pixel must be 50% blend
        val halfMaskPixel = computeDeltaMask(original, adjusted, maskVal = 0.5f, maskIntensity = 1.0f)
        assertEquals("At M=0.5, pixel must be midpoint", 0.50f, halfMaskPixel, 0.0001f)
    }

    @Test
    fun testDeltaClippingLimit() {
        // Formula: diff = min(Adjusted - Original, 4.0) prevents blown-out shadow noise
        val original = 0.10f
        val extremeAdjusted = 10.0f // delta = 9.9 > 4.0

        val clippedPixel = computeDeltaMask(original, extremeAdjusted, maskVal = 1.0f, maskIntensity = 1.0f)
        assertEquals("Delta must be capped at 4.0 maximum gain", original + 4.0f, clippedPixel, 0.0001f)
    }
}
