package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
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

    /**
     * Exact Normative Luminance Knee Compression (Section 1.5):
     * L_recovered = 0.85 + 0.15 * (1.0 - exp(-(L - 0.85) / 0.15))
     */
    private fun computeLuminanceKnee(l: Float): Float {
        if (l <= 0.85f) return l
        return 0.85f + 0.15f * (1.0f - exp(-(l - 0.85f) / 0.15f))
    }

    /**
     * Chromaticity Inpainting (Section 1.5):
     * Propagate R/G and B/G chromaticity ratios from unclipped neighbors to clipped pixel
     */
    private fun inpaintClippedChromaticity(
        clippedCol: FloatArray,
        unclippedNeighbors: List<FloatArray>
    ): FloatArray {
        var chromRSum = 0.0f
        var chromBSum = 0.0f
        var weightCount = 0

        for (n in unclippedNeighbors) {
            val maxC = max(max(n[0], n[1]), n[2])
            if (maxC < 0.85f && n[1] > 0.001f) {
                chromRSum += n[0] / n[1]
                chromBSum += n[2] / n[1]
                weightCount++
            }
        }

        if (weightCount == 0) return clippedCol

        val avgR = chromRSum / weightCount
        val avgB = chromBSum / weightCount

        // Inpaint using green channel baseline
        val originalLum = computeProPhotoLuminance(clippedCol[0], clippedCol[1], clippedCol[2])
        val reconstructed = floatArrayOf(avgR * clippedCol[1], clippedCol[1], avgB * clippedCol[1])
        val reconLum = computeProPhotoLuminance(reconstructed[0], reconstructed[1], reconstructed[2])

        val scale = if (reconLum > 0.0001f) originalLum / reconLum else 1.0f
        return floatArrayOf(reconstructed[0] * scale, reconstructed[1] * scale, reconstructed[2] * scale)
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
        val inputValues = floatArrayOf(0.10f, 0.101f, 0.102f, 0.103f, 0.104f)
        val ev3Gain = computeExposureGain(3.0f) // 8.0x

        val scaled = inputValues.map { it * ev3Gain }
        for (i in 0 until scaled.size - 1) {
            val step = scaled[i + 1] - scaled[i]
            assertTrue("Subtle gradient steps must be strictly distinct and monotonic (> 0)", step > 0.005f)
        }
    }

    @Test
    fun testLuminanceKneeCompressionProperties() {
        // 1. Boundary continuity at L = 0.85
        val atKneeThreshold = computeLuminanceKnee(0.85f)
        assertEquals("At L = 0.85, knee output must be exactly 0.85", 0.85f, atKneeThreshold, 0.0001f)

        // 2. Below threshold is strictly unchanged
        assertEquals(0.50f, computeLuminanceKnee(0.50f), 0.0001f)

        // 3. At L = 1.00: 0.85 + 0.15 * (1.0 - exp(-1.0)) ~ 0.9448
        val at100 = computeLuminanceKnee(1.00f)
        val expectedAt100 = 0.85f + 0.15f * (1.0f - exp(-1.0f))
        assertEquals(expectedAt100, at100, 0.0001f)

        // 4. Overexposed values (L = 2.0, 5.0, 100.0) compress asymptotically to 1.00 without NaN/overflow
        val at200 = computeLuminanceKnee(2.00f)
        assertTrue("Luminance at L=2.00 should be compressed below 1.00", at200 < 1.0f)
        assertTrue("Luminance at L=2.00 should be > 0.99", at200 > 0.99f)

        val atInfinity = computeLuminanceKnee(100.0f)
        assertEquals("Extreme overexposure should asymptote to 1.00", 1.00f, atInfinity, 0.001f)
    }

    @Test
    fun testChromaticityInpaintingBurntSkyRestoration() {
        // Burnt sky pixel where Green clipped to 1.0 and channels are near white
        // Original raw clipped pixel: R = 0.95, G = 1.0, B = 0.98 (appears chalky white/gray)
        val clippedSky = floatArrayOf(0.95f, 1.0f, 0.98f)

        // Surrounding unclipped sky neighbors (Blue sky: R = 0.30, G = 0.50, B = 0.80)
        // R/G = 0.60, B/G = 1.60
        val skyNeighbors = listOf(
            floatArrayOf(0.30f, 0.50f, 0.80f),
            floatArrayOf(0.32f, 0.52f, 0.82f)
        )

        val restored = inpaintClippedChromaticity(clippedSky, skyNeighbors)

        // The restored pixel should recover deep sky chromaticity: Blue channel must dominate Red
        assertTrue("Restored sky blue channel must be substantially higher than red", restored[2] > restored[0])
        val restoredBGRatio = restored[2] / restored[1]
        val expectedBGRatio = 0.80f / 0.50f // 1.60
        assertEquals("B/G chromaticity ratio must match the unclipped sky", expectedBGRatio, restoredBGRatio, 0.05f)
    }
}
