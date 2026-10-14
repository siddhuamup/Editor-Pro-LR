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

    /**
     * Triangle transfer function from Lightroom locallaplacian.fsh
     */
    private fun evaluateTriangleFunction(g: Float, uRef: Float, uInv_ds: Float = 4.0f): Float {
        val line0 = uInv_ds * (g - uRef) + 1.0f
        val line1 = uInv_ds * (uRef - g) + 1.0f
        return max(min(line0, line1), 0.0f)
    }

    /**
     * Texture contour gate function:
     * Suppresses micro-contrast enhancement on large contours (g_mag >= 0.12)
     */
    private fun evaluateTextureContourGate(gMag: Float, contourThreshold: Float = 0.12f): Float {
        val normalized = gMag / contourThreshold
        return (1.0f - normalized * normalized).coerceIn(0.0f, 1.0f)
    }

    /**
     * Dark Channel Prior Dehaze (Document Section 1.4):
     * J_dark(x) = min_{c in {r,g,b}} (min_{y in Omega(x)} I^c(y))
     * t(x) = 1.0 - omega * (J_dark(x) / A)
     * J(x) = (I(x) - A) / max(t(x), 0.1) + A
     */
    private fun computeDarkChannel(patch: List<FloatArray>): Float {
        var minVal = Float.MAX_VALUE
        for (rgb in patch) {
            val cMin = min(min(rgb[0], rgb[1]), rgb[2])
            if (cMin < minVal) minVal = cMin
        }
        return minVal
    }

    private fun recoverDehaze(
        iPixel: FloatArray,
        jDark: Float,
        dehazePercent: Float,
        airlight: FloatArray = floatArrayOf(0.92f, 0.95f, 1.0f),
        airlightScalar: Float = 0.955f
    ): FloatArray {
        val dehazeAmount = dehazePercent / 100.0f
        val out = FloatArray(3)

        if (dehazeAmount > 0.0f) {
            val omega = 0.95f * dehazeAmount
            val t = (1.0f - omega * (jDark / airlightScalar)).coerceIn(0.1f, 1.0f)
            val safeT = max(t, 0.1f)
            for (c in 0..2) {
                out[c] = ((iPixel[c] - airlight[c]) / safeT) + airlight[c]
            }
        } else {
            val fog = -dehazeAmount * 0.45f
            for (c in 0..2) {
                out[c] = iPixel[c] * (1.0f - fog) + airlight[c] * fog
            }
        }
        return out
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

        // Moderate edge difference (0.55 vs 0.50, delta = 0.05)
        val moderateAlpha = evaluateTriangleFunction(0.55f, uRef)
        assertEquals("Moderate delta 0.05 with uInv_ds 4.0 gives alpha 0.80", 0.80f, moderateAlpha, 0.001f)

        // Boundary edge (0.75 vs 0.50, delta = 0.25) -> alpha drops to 0.0
        val boundaryAlpha = evaluateTriangleFunction(0.75f, uRef)
        assertEquals("Alpha reaches 0.0 at delta 0.25", 0.0f, boundaryAlpha, 0.001f)

        // Harsh edge step beyond 0.25 (0.80 vs 0.50, delta = 0.30 > 0.25)
        val harshEdgeAlpha = evaluateTriangleFunction(0.80f, uRef)
        assertEquals("Harsh edge contrast must drop to 0.0 to prevent halos", 0.0f, harshEdgeAlpha, 0.001f)
    }

    @Test
    fun testTextureContourGateProtectsEdges() {
        // 1. Fine skin pore / hair detail (delta = 0.03 < 0.12)
        val microWeight = evaluateTextureContourGate(0.03f)
        assertTrue("Subtle micro-detail should retain high weight (> 0.90)", microWeight > 0.90f)
        assertEquals(1.0f - (0.03f / 0.12f) * (0.03f / 0.12f), microWeight, 0.001f)

        // 2. High contrast object contour (delta = 0.15 > 0.12)
        val contourWeight = evaluateTextureContourGate(0.15f)
        assertEquals("High-contrast object contours must receive 0 weight to prevent edge artifacts", 0.0f, contourWeight, 0.001f)
    }

    @Test
    fun testDarkChannelPriorTransmissionAndRecovery() {
        // Neighborhood patch with a dark channel point (e.g. shadow or dark object)
        val patch = listOf(
            floatArrayOf(0.60f, 0.65f, 0.70f),
            floatArrayOf(0.40f, 0.45f, 0.50f),
            floatArrayOf(0.20f, 0.30f, 0.25f), // darkMin = 0.20
            floatArrayOf(0.70f, 0.75f, 0.80f)
        )
        val jDark = computeDarkChannel(patch)
        assertEquals(0.20f, jDark, 0.001f)

        // Positive Dehaze (+50)
        val iPixel = floatArrayOf(0.50f, 0.55f, 0.60f)
        val recovered = recoverDehaze(iPixel, jDark, dehazePercent = 50.0f)

        // With dehaze, hazy image pixel is darkened/contrasted away from airlight
        assertTrue("Recovered red should be clearer (lower than original hazy value)", recovered[0] < iPixel[0])
        assertTrue("Recovered green should be clearer", recovered[1] < iPixel[1])
        assertTrue("Recovered blue should be clearer", recovered[2] < iPixel[2])

        // Test transmission minimum bound t >= 0.1 to avoid division by zero
        val heavyHazePixel = floatArrayOf(0.90f, 0.92f, 0.95f)
        val maxDehazeRecovered = recoverDehaze(heavyHazePixel, jDark = 0.95f, dehazePercent = 100.0f)
        assertTrue("Max dehaze recovery must not produce NaN or Infinity", !maxDehazeRecovered[0].isNaN() && !maxDehazeRecovered[0].isInfinite())

        // Negative Dehaze (-40) adds atmospheric fog
        val foggyPixel = recoverDehaze(iPixel, jDark, dehazePercent = -40.0f)
        assertTrue("Negative dehaze should blend toward atmospheric airlight", foggyPixel[0] > iPixel[0])
        assertTrue("Negative dehaze should blend toward atmospheric airlight", foggyPixel[1] > iPixel[1])
    }
}
