package com.cleanlr.engine

import com.cleanlr.model.HslAdjustments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TatEngineTest {

    @Test
    fun testRgbToHslConversion() {
        // 1. Pure Red
        val redHsl = TatEngine.rgbToHsl(1.0f, 0.0f, 0.0f)
        assertEquals(0.0f, redHsl[0], 0.001f)
        assertEquals(1.0f, redHsl[1], 0.001f)
        assertEquals(0.5f, redHsl[2], 0.001f)

        // 2. Pure Green
        val greenHsl = TatEngine.rgbToHsl(0.0f, 1.0f, 0.0f)
        assertEquals(1.0f / 3.0f, greenHsl[0], 0.001f)
        assertEquals(1.0f, greenHsl[1], 0.001f)
        assertEquals(0.5f, greenHsl[2], 0.001f)

        // 3. Pure Blue
        val blueHsl = TatEngine.rgbToHsl(0.0f, 0.0f, 1.0f)
        assertEquals(2.0f / 3.0f, blueHsl[0], 0.001f)
        assertEquals(1.0f, blueHsl[1], 0.001f)
        assertEquals(0.5f, blueHsl[2], 0.001f)

        // 4. Middle Gray
        val grayHsl = TatEngine.rgbToHsl(0.5f, 0.5f, 0.5f)
        assertEquals(0.0f, grayHsl[1], 0.001f) // Saturation is 0
        assertEquals(0.5f, grayHsl[2], 0.001f) // Luminance is 0.5
    }

    @Test
    fun testSkyBlueChannelDetectionSection3_2() {
        // Section 3.2: "उदा. आकाशावर क्लिक केल्यास: Blue 85%, Aqua 15%"
        // Sky blue hue around 225-230 degrees (between Aqua 180 and Blue 240)
        val skyHue = 227.0f / 360.0f
        val weights = TatEngine.computeChannelWeights(skyHue)

        // Blue and Aqua must dominate the weights
        assertTrue("Blue weight should be high (~80% - 90%)", weights.blue in 0.80f..0.90f)
        assertTrue("Aqua weight should be secondary (~10% - 20%)", weights.aqua in 0.10f..0.20f)
        assertEquals("Dominant channel must be Blue", "Blue", weights.dominantChannelName())

        // Sum of Blue + Aqua should be > 98% of total weight
        assertTrue("Blue and Aqua should account for virtually all weight", (weights.blue + weights.aqua) > 0.98f)

        // Unrelated channels should be zero
        assertEquals(0.0f, weights.red, 0.01f)
        assertEquals(0.0f, weights.green, 0.01f)
        assertEquals(0.0f, weights.yellow, 0.01f)
    }

    @Test
    fun testTatDragProportionalAdjustment() {
        val initialHsl = HslAdjustments()
        val skyHue = 227.0f / 360.0f
        val weights = TatEngine.computeChannelWeights(skyHue)

        // 1. Drag UP (e.g. +20 delta)
        val draggedUp = TatEngine.applyTatDrag(
            currentHsl = initialHsl,
            weights = weights,
            delta = 20.0f,
            mode = TatTargetMode.SATURATION
        )

        // Blue increases by 20 * weights.blue, Aqua by 20 * weights.aqua
        val expectedBlueSat = 20.0f * weights.blue
        val expectedAquaSat = 20.0f * weights.aqua
        assertEquals(expectedBlueSat, draggedUp.blue.saturation, 0.01f)
        assertEquals(expectedAquaSat, draggedUp.aqua.saturation, 0.01f)
        assertEquals(0.0f, draggedUp.red.saturation, 0.01f)
        assertEquals(0.0f, draggedUp.green.saturation, 0.01f)

        // 2. Drag DOWN (e.g. -15 delta from draggedUp)
        val draggedDown = TatEngine.applyTatDrag(
            currentHsl = draggedUp,
            weights = weights,
            delta = -15.0f,
            mode = TatTargetMode.SATURATION
        )

        assertEquals(expectedBlueSat - 15.0f * weights.blue, draggedDown.blue.saturation, 0.01f)
        assertEquals(expectedAquaSat - 15.0f * weights.aqua, draggedDown.aqua.saturation, 0.01f)
    }

    @Test
    fun testTatHueAndLuminanceModes() {
        val initialHsl = HslAdjustments()
        val greenHue = 120.0f / 360.0f // Pure Green
        val weights = TatEngine.computeChannelWeights(greenHue)

        // Hue Mode
        val hueAdjusted = TatEngine.applyTatDrag(
            currentHsl = initialHsl,
            weights = weights,
            delta = 10.0f,
            mode = TatTargetMode.HUE
        )
        assertTrue(hueAdjusted.green.hue > 9.0f)
        assertEquals(0.0f, hueAdjusted.green.saturation, 0.001f)

        // Luminance Mode
        val lumAdjusted = TatEngine.applyTatDrag(
            currentHsl = initialHsl,
            weights = weights,
            delta = -10.0f,
            mode = TatTargetMode.LUMINANCE
        )
        assertTrue(lumAdjusted.green.luminance < -9.0f)
        assertEquals(0.0f, lumAdjusted.green.saturation, 0.001f)
    }

    // -------------------------------------------------------------------------
    // Sprint A Task T3: HSL Gaussian Weights Circular Distance Audit (H=355°)
    // -------------------------------------------------------------------------
    @Test
    fun testCircularHueDistanceH355RedDominanceAudit() {
        // H = 355 degrees:
        // Circular distance to Red (0 deg) is 5 degrees (0.013889 normalized).
        // Distance to Magenta (320 deg) is 35 degrees (0.097222 normalized).
        // Distance to Orange (30 deg) is 35 degrees (0.097222 normalized).
        val h355 = 355.0f / 360.0f

        val distToRed = TatEngine.circularHueDistance(h355, 0.0f)
        val distToMagenta = TatEngine.circularHueDistance(h355, 320.0f / 360.0f)

        assertEquals("Distance to Red (0 deg) must be exactly 5/360", 5.0f / 360.0f, distToRed, 0.0001f)
        assertEquals("Distance to Magenta (320 deg) must be exactly 35/360", 35.0f / 360.0f, distToMagenta, 0.0001f)

        // Raw Gaussian weights directly from PDF Page 9 formula: Weight_i(H) = exp(-dist^2 / (2*sigma^2))
        val rawWeightRed = TatEngine.gaussianWeight(h355, 0.0f)
        val rawWeightMagenta = TatEngine.gaussianWeight(h355, 320.0f / 360.0f)
        assertTrue("Raw Red Gaussian weight must exceed 0.95 at 355 deg (got $rawWeightRed)", rawWeightRed > 0.95f)
        assertTrue("Raw Magenta Gaussian weight must be low (< 0.35) at 355 deg (got $rawWeightMagenta)", rawWeightMagenta < 0.35f)

        val weights = TatEngine.computeChannelWeights(h355)

        // Red must be the overwhelming dominant channel
        assertEquals("Dominant channel for 355 deg MUST be Red (prevents magenta-skin disaster)", "Red", weights.dominantChannelName())
        assertTrue("Normalized Red weight must exceed 0.60 at 355 deg (got ${weights.red})", weights.red > 0.60f)
        assertTrue("Normalized Magenta weight must be significantly lower than Red (< 0.30)", weights.magenta < 0.30f)
    }

    @Test
    fun testPeakWeightsUniformityAndOrangeSkinProtection() {
        // 1. Peak raw weight at exact center of each channel must be 1.0 (never 2.0x for Red!)
        val centers = floatArrayOf(
            0.0f,               // Red
            30.0f / 360.0f,     // Orange
            60.0f / 360.0f,     // Yellow
            120.0f / 360.0f,    // Green
            180.0f / 360.0f,    // Aqua
            240.0f / 360.0f,    // Blue
            280.0f / 360.0f,    // Purple
            320.0f / 360.0f     // Magenta
        )

        for (center in centers) {
            val rawPeak = TatEngine.gaussianWeight(center, center)
            assertEquals("Raw peak weight at center must be 1.0", 1.0f, rawPeak, 0.0001f)

            val weights = TatEngine.computeChannelWeights(center)
            val maxWeight = maxOf(
                weights.red, weights.orange, weights.yellow, weights.green,
                weights.aqua, weights.blue, weights.purple, weights.magenta
            )
            assertTrue("Normalized peak weight at center $center must be in [0.50..1.0] (got $maxWeight)", maxWeight in 0.50f..1.0f)
        }

        // 2. Orange Skin Protection: narrow sigma (18 deg = 0.050)
        // At 50 degrees (transition to yellow), Orange weight drops sharply to preserve skin tones
        val h50 = 50.0f / 360.0f
        val rawOrange50 = TatEngine.gaussianWeight(h50, 30.0f / 360.0f, 0.050f)
        val rawOrange50Standard = TatEngine.gaussianWeight(h50, 30.0f / 360.0f, 0.065f)
        assertTrue("Narrow sigma (0.050) must suppress Orange bleed significantly compared to standard sigma (0.065)", rawOrange50 < rawOrange50Standard - 0.10f)
        assertTrue("At 50 deg, raw Orange weight must drop < 0.58 (got $rawOrange50)", rawOrange50 < 0.58f)
        val weights50 = TatEngine.computeChannelWeights(h50)
        assertTrue("At 50 deg, normalized Orange weight must drop < 0.40 (got ${weights50.orange})", weights50.orange < 0.40f)
    }
}
