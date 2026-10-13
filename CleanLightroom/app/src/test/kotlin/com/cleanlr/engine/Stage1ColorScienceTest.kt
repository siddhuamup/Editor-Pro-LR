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
}
