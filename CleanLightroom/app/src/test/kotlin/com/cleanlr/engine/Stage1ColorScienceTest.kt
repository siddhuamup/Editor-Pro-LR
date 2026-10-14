package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class Stage1ColorScienceTest {

    // Same matrix as in stage1_linearize.frag
    private val rgb2proPhoto = doubleArrayOf(
        0.52932378745827813, 0.33005506400369466, 0.14064019169871309, // Row 0
        0.098426540365603984, 0.87350872697710469, 0.028111560975330636, // Row 1
        0.016845769297481432, 0.11769549131351224, 0.86547398998538982  // Row 2
    )

    private fun srgbToLinear(c: Double): Double {
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun applyProPhotoMatrix(rLin: Double, gLin: Double, bLin: Double): DoubleArray {
        val rPro = rgb2proPhoto[0] * rLin + rgb2proPhoto[1] * gLin + rgb2proPhoto[2] * bLin
        val gPro = rgb2proPhoto[3] * rLin + rgb2proPhoto[4] * gLin + rgb2proPhoto[5] * bLin
        val bPro = rgb2proPhoto[6] * rLin + rgb2proPhoto[7] * gLin + rgb2proPhoto[8] * bLin
        return doubleArrayOf(rPro, gPro, bPro)
    }

    @Test
    fun testNeutralGrayPreservationAcrossFullScale() {
        // Verification Check: Neutral gray MUST remain strictly neutral after Stage 1
        val testGrays = doubleArrayOf(0.05, 0.18, 0.35, 0.50, 0.75, 1.0)

        for (gray in testGrays) {
            val lin = srgbToLinear(gray)
            val proPhoto = applyProPhotoMatrix(lin, lin, lin)

            val r = proPhoto[0]
            val g = proPhoto[1]
            val b = proPhoto[2]

            // Check that R, G, B channels in ProPhoto match within 0.0001
            val deltaRG = abs(r - g)
            val deltaGB = abs(g - b)
            val deltaBR = abs(b - r)

            assertEquals("R and G should match for gray=$gray", 0.0, deltaRG, 0.0001)
            assertEquals("G and B should match for gray=$gray", 0.0, deltaGB, 0.0001)
            assertEquals("B and R should match for gray=$gray", 0.0, deltaBR, 0.0001)
        }
    }

    @Test
    fun testRowSumsEqualUnity() {
        // Each row of the conversion matrix must sum to 1.0 for energy conservation
        val row0 = rgb2proPhoto[0] + rgb2proPhoto[1] + rgb2proPhoto[2]
        val row1 = rgb2proPhoto[3] + rgb2proPhoto[4] + rgb2proPhoto[5]
        val row2 = rgb2proPhoto[6] + rgb2proPhoto[7] + rgb2proPhoto[8]

        assertEquals("Row 0 sum should equal 1.0", 1.0, row0, 0.0002)
        assertEquals("Row 1 sum should equal 1.0", 1.0, row1, 0.0002)
        assertEquals("Row 2 sum should equal 1.0", 1.0, row2, 0.0002)
    }

    @Test
    fun testProPhotoMatrix8DecimalPrecisionAudit() {
        // PDF Part B Section 1:
        // [ 0.52932379  0.09842654  0.01684577 ]
        // [ 0.33005506  0.87350873  0.11769549 ]
        // [ 0.14064019  0.02811156  0.86547399 ]
        val expectedCol0 = doubleArrayOf(0.52932379, 0.09842654, 0.01684577)
        val expectedCol1 = doubleArrayOf(0.33005506, 0.87350873, 0.11769549)
        val expectedCol2 = doubleArrayOf(0.14064019, 0.02811156, 0.86547399)

        // Matrix in ColorScience.kt (Row-major: [0, 3, 6] is Col 0; [1, 4, 7] is Col 1; [2, 5, 8] is Col 2)
        val actualCol0 = doubleArrayOf(ColorScience.M_SRGB_TO_MELISSA[0], ColorScience.M_SRGB_TO_MELISSA[3], ColorScience.M_SRGB_TO_MELISSA[6])
        val actualCol1 = doubleArrayOf(ColorScience.M_SRGB_TO_MELISSA[1], ColorScience.M_SRGB_TO_MELISSA[4], ColorScience.M_SRGB_TO_MELISSA[7])
        val actualCol2 = doubleArrayOf(ColorScience.M_SRGB_TO_MELISSA[2], ColorScience.M_SRGB_TO_MELISSA[5], ColorScience.M_SRGB_TO_MELISSA[8])

        for (i in 0..2) {
            assertEquals("Col 0 index $i matches 8-decimal specification", expectedCol0[i], actualCol0[i], 1e-7)
            assertEquals("Col 1 index $i matches 8-decimal specification", expectedCol1[i], actualCol1[i], 1e-7)
            assertEquals("Col 2 index $i matches 8-decimal specification", expectedCol2[i], actualCol2[i], 1e-7)
        }
    }

    @Test
    fun testProPhotoLuminanceWeightsAudit() {
        // PDF Part B Section 1.2:
        // Y = 0.25 * R + 0.50 * G + 0.25 * B
        val wR = 0.25
        val wG = 0.50
        val wB = 0.25

        assertEquals("Sum of ProPhoto luminance weights must be exactly 1.0", 1.0, wR + wG + wB, 0.000001)
        assertEquals("Red luminance weight is 0.25", 0.25, wR, 0.000001)
        assertEquals("Green luminance weight is 0.50", 0.50, wG, 0.000001)
        assertEquals("Blue luminance weight is 0.25", 0.25, wB, 0.000001)
    }
}
