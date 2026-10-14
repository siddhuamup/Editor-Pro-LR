package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.math.max

class GpuHistogramPassTest {

    private fun computeProPhotoLuma(r: Float, g: Float, b: Float): Float {
        return 0.25f * r + 0.50f * g + 0.25f * b
    }

    private fun mapValueToBin(v: Float): Int {
        val clamped = v.coerceIn(0.0f, 1.0f)
        return floor(clamped * 255.0f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun normalizeReductionSum(rawCount: Float, maxCount: Float): Float {
        val norm = max(maxCount, 1.0f)
        return (rawCount / norm).coerceIn(0.0f, 1.0f)
    }

    @Test
    fun testHistogram256x4TextureDimensions() {
        assertEquals("Histogram must have exactly 256 bins", 256, GpuHistogramPass.HISTOGRAM_WIDTH)
        assertEquals("Histogram must have 4 rows for R, G, B, Luma", 4, GpuHistogramPass.HISTOGRAM_HEIGHT)
    }

    @Test
    fun testProPhotoLumaBinMapping() {
        // Pure Red (1.0, 0.0, 0.0) in ProPhoto RGB
        val redLuma = computeProPhotoLuma(1.0f, 0.0f, 0.0f) // 0.25
        val redBin = mapValueToBin(1.0f)
        val redLumaBin = mapValueToBin(redLuma)
        assertEquals(255, redBin)
        assertEquals(64, redLumaBin) // 0.25 * 255 ~ 64

        // Pure Green (0.0, 1.0, 0.0)
        val greenLuma = computeProPhotoLuma(0.0f, 1.0f, 0.0f) // 0.50
        val greenBin = mapValueToBin(1.0f)
        val greenLumaBin = mapValueToBin(greenLuma)
        assertEquals(255, greenBin)
        assertEquals(128, greenLumaBin) // 0.50 * 255 ~ 128

        // Pure White (1.0, 1.0, 1.0)
        val whiteLuma = computeProPhotoLuma(1.0f, 1.0f, 1.0f)
        val whiteBin = mapValueToBin(whiteLuma)
        assertEquals(255, whiteBin)

        // Middle Gray 0.18
        val grayLuma = computeProPhotoLuma(0.18f, 0.18f, 0.18f)
        val grayBin = mapValueToBin(grayLuma)
        assertEquals(46, grayBin) // 0.18 * 255 = 45.9 -> 46
    }

    @Test
    fun testBinIndexClampingAndQuantization() {
        // Underflow clamp
        assertEquals(0, mapValueToBin(-0.5f))
        assertEquals(0, mapValueToBin(0.0f))

        // Overflow clamp
        assertEquals(255, mapValueToBin(1.5f))
        assertEquals(255, mapValueToBin(1.0f))

        // Monotonic progression
        for (i in 0..255) {
            val v = i / 255.0f
            val bin = mapValueToBin(v)
            assertEquals("Normalized value should map 1:1 back to bin index", i, bin)
        }
    }

    @Test
    fun testReductionSumNormalization() {
        // Normalization maps counts to [0.0, 1.0]
        val normPeak = normalizeReductionSum(500.0f, 500.0f)
        assertEquals(1.0f, normPeak, 0.0001f)

        val normHalf = normalizeReductionSum(250.0f, 500.0f)
        assertEquals(0.5f, normHalf, 0.0001f)

        val normZero = normalizeReductionSum(0.0f, 500.0f)
        assertEquals(0.0f, normZero, 0.0001f)

        // Division by zero safeguard
        val safeZeroMax = normalizeReductionSum(10.0f, 0.0f)
        assertTrue("Zero maxCount must not produce NaN or Infinity", !safeZeroMax.isNaN() && !safeZeroMax.isInfinite())
    }
}
