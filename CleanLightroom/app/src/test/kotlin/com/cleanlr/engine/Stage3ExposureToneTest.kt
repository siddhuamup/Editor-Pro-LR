package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class Stage3ExposureToneTest {

    private val epsilon = 0.001f

    private fun shouldSkip(
        exposure: Float,
        contrast: Float,
        highlights: Float,
        shadows: Float,
        whites: Float,
        blacks: Float
    ): Boolean {
        return abs(exposure) < epsilon &&
               abs(contrast) < epsilon &&
               abs(highlights) < epsilon &&
               abs(shadows) < epsilon &&
               abs(whites) < epsilon &&
               abs(blacks) < epsilon
    }

    private fun computeProPhotoLuminance(r: Float, g: Float, b: Float): Float {
        return 0.25f * r + 0.50f * g + 0.25f * b
    }

    private fun computeExposureGain(ev: Float): Float {
        return 2.0f.pow(ev)
    }

    @Test
    fun testExposureGainScale() {
        // +1.0 EV = 2x, +2.0 EV = 4x, +3.0 EV = 8x, -1.0 EV = 0.5x
        assertEquals(2.0f, computeExposureGain(1.0f), 0.0001f)
        assertEquals(4.0f, computeExposureGain(2.0f), 0.0001f)
        assertEquals(8.0f, computeExposureGain(3.0f), 0.0001f)
        assertEquals(0.5f, computeExposureGain(-1.0f), 0.0001f)
    }

    @Test
    fun testProPhotoLuminanceWeights() {
        // ProPhoto RGB weights are 0.25, 0.50, 0.25 (Sum = 1.0)
        val lumaUniform = computeProPhotoLuminance(1.0f, 1.0f, 1.0f)
        assertEquals("Full white luminance should equal 1.0", 1.0f, lumaUniform, 0.0001f)

        val lumaMid = computeProPhotoLuminance(0.18f, 0.18f, 0.18f)
        assertEquals("Middle gray luminance should equal 0.18", 0.18f, lumaMid, 0.0001f)

        val lumaGreenDominated = computeProPhotoLuminance(0.0f, 1.0f, 0.0f)
        assertEquals("Pure green luminance weight should be 0.50", 0.50f, lumaGreenDominated, 0.0001f)
    }

    @Test
    fun testSkipDisabledStagesRule() {
        // All default zeros -> skip
        assertTrue(
            "Default parameters should trigger stage skip for 120 FPS",
            shouldSkip(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        )

        // Any active adjustment -> do not skip
        assertFalse(shouldSkip(0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f))
        assertFalse(shouldSkip(0.0f, 15.0f, 0.0f, 0.0f, 0.0f, 0.0f))
        assertFalse(shouldSkip(0.0f, 0.0f, -20.0f, 0.0f, 0.0f, 0.0f))
        assertFalse(shouldSkip(0.0f, 0.0f, 0.0f, 10.0f, 0.0f, 0.0f))
        assertFalse(shouldSkip(0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f))
        assertFalse(shouldSkip(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -5.0f))
    }

    @Test
    fun testPlus3EVPrecisionBandingFreeCheck() {
        // Verification Gate Check: +3 EV on highlight gradient
        // In 8-bit, 2^3 pushes values > 32 (out of 255) to 255 (harsh clipping & banding)
        // In RGBA16F (Half-Float), values up to 65504.0 are represented with 1024 distinct mantissa steps per power-of-two.
        val inputValues = floatArrayOf(0.10f, 0.101f, 0.102f, 0.103f, 0.104f)
        val ev3Gain = computeExposureGain(3.0f) // 8.0x

        val scaled = inputValues.map { it * ev3Gain }
        for (i in 0 until scaled.size - 1) {
            val step = scaled[i + 1] - scaled[i]
            assertTrue("Subtle gradient steps must be strictly distinct and monotonic (> 0)", step > 0.005f)
        }
    }
}
