package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class Stage9ColorScienceTest {

    // Stage 1 Matrix: sRGB to ProPhoto RGB (column-major in GLSL)
    private val mSrgbToProPhoto = arrayOf(
        floatArrayOf(0.52932379f, 0.33005506f, 0.14064019f),
        floatArrayOf(0.09842654f, 0.87350873f, 0.02811156f),
        floatArrayOf(0.01684577f, 0.11769549f, 0.86547399f)
    )

    // Stage 9 Matrix: ProPhoto RGB to sRGB (row-major representation of GLSL columns)
    // Col 0: (2.03420482, -0.22895313, -0.00846069)
    // Col 1: (-0.72726743, 1.23175691, -0.15334315)
    // Col 2: (-0.30693739, -0.00280378, 1.16180384)
    private val mProPhotoToSrgb = arrayOf(
        floatArrayOf( 2.03420482f, -0.72726743f, -0.30693739f),
        floatArrayOf(-0.22895313f,  1.23175691f, -0.00280378f),
        floatArrayOf(-0.00846069f, -0.15334315f,  1.16180384f)
    )

    // Stage 9 Matrix: Melissa RGB to Display P3 (M_melissa2P3 from PDF Page 2)
    private val mProPhotoToP3 = arrayOf(
        floatArrayOf( 1.345943f, -0.255608f, -0.051112f),
        floatArrayOf(-0.544587f,  1.508167f,  0.020536f),
        floatArrayOf( 0.000000f, -0.020584f,  1.211812f)
    )

    private fun multiplyMatrix(m: Array<FloatArray>, v: FloatArray): FloatArray {
        return floatArrayOf(
            m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]
        )
    }

    private fun srgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    private fun linearToSrgb(c: Float): Float {
        val cl = c.coerceIn(0.0f, 1.0f)
        return if (cl <= 0.0031308f) {
            12.92f * cl
        } else {
            1.055f * cl.pow(1.0f / 2.4f) - 0.055f
        }
    }

    @Test
    fun testProPhotoToSrgbRowSumPreservesNeutralGray() {
        // Critical Check: Neutral gray [g, g, g] must remain strictly neutral [g, g, g]
        val grays = floatArrayOf(0.18f, 0.50f, 0.75f)

        for (g in grays) {
            val input = floatArrayOf(g, g, g)
            val output = multiplyMatrix(mProPhotoToSrgb, input)

            assertEquals("R' must equal g", g, output[0], 0.0001f)
            assertEquals("G' must equal g", g, output[1], 0.0001f)
            assertEquals("B' must equal g", g, output[2], 0.0001f)
        }
    }

    @Test
    fun testProPhotoToDisplayP3NormativeMatrix() {
        // Test with reference 50% gray
        val g = 0.5f
        val input = floatArrayOf(g, g, g)
        val output = multiplyMatrix(mProPhotoToP3, input)

        // Expected values according to normative M_melissa2P3 matrix from PDF Page 2
        // R' = 0.5 * (1.345943 - 0.255608 - 0.051112) = 0.5196115
        // G' = 0.5 * (-0.544587 + 1.508167 + 0.020536) = 0.492058
        // B' = 0.5 * (0.000000 - 0.020584 + 1.211812) = 0.595614
        assertEquals("P3 R' matches normative M_melissa2P3", 0.51961f, output[0], 0.0001f)
        assertEquals("P3 G' matches normative M_melissa2P3", 0.49206f, output[1], 0.0001f)
        assertEquals("P3 B' matches normative M_melissa2P3", 0.59561f, output[2], 0.0001f)
    }

    @Test
    fun testEotfLin2SrgbCurveAccuracy() {
        // Black
        assertEquals("Black must map to 0.0", 0.0f, linearToSrgb(0.0f), 0.0001f)

        // White
        assertEquals("White must map to 1.0", 1.0f, linearToSrgb(1.0f), 0.0001f)

        // Continuity at boundary 0.0031308
        val threshold = 0.0031308f
        val linearBranch = 12.92f * threshold
        val powerBranch = 1.055f * threshold.pow(1.0f / 2.4f) - 0.055f
        assertEquals("Curve must be continuous at threshold", linearBranch, powerBranch, 0.0005f)

        // 18% gray linear should produce standard ~46% display sRGB
        val middleGrayDisplay = linearToSrgb(0.18f)
        assertTrue("18% linear gray must map to ~46% sRGB", middleGrayDisplay in 0.45f..0.47f)
    }

    @Test
    fun testFullPipelineIngestionToDisplayRoundTrip() {
        // Take standard color values (e.g. Skin tones, primary colors)
        val testColors = listOf(
            floatArrayOf(0.85f, 0.65f, 0.55f), // Warm skin tone
            floatArrayOf(0.20f, 0.50f, 0.80f), // Sky blue
            floatArrayOf(0.40f, 0.70f, 0.30f), // Foliage green
            floatArrayOf(0.50f, 0.50f, 0.50f)  // Neutral gray
        )

        for (srgbInput in testColors) {
            // 1. Stage 1: sRGB to Linear
            val linR = srgbToLinear(srgbInput[0])
            val linG = srgbToLinear(srgbInput[1])
            val linB = srgbToLinear(srgbInput[2])

            // 2. Stage 1: Linear to ProPhoto
            val proPhoto = multiplyMatrix(mSrgbToProPhoto, floatArrayOf(linR, linG, linB))

            // 3. Stage 9: ProPhoto to sRGB Linear
            val srgbLinRecovered = multiplyMatrix(mProPhotoToSrgb, proPhoto)

            // 4. Stage 9: EOTF to Display sRGB
            val outR = linearToSrgb(srgbLinRecovered[0])
            val outG = linearToSrgb(srgbLinRecovered[1])
            val outB = linearToSrgb(srgbLinRecovered[2])

            // Verify round-trip accuracy within 0.1% tolerance
            assertEquals("Roundtrip R", srgbInput[0], outR, 0.002f)
            assertEquals("Roundtrip G", srgbInput[1], outG, 0.002f)
            assertEquals("Roundtrip B", srgbInput[2], outB, 0.002f)
        }
    }
}
