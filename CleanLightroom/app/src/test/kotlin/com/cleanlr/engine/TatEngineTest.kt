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
}
