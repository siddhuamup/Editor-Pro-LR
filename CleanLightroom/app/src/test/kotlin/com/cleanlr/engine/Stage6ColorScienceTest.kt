package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class Stage6ColorScienceTest {

    private fun circularHueDistance(h1: Float, h2: Float): Float {
        val d = abs(h1 - h2)
        return min(d, 1.0f - d)
    }

    /**
     * Exact Normative Smart Vibrance Math (Document Section 1.3):
     * BaseBoost = (vibrance / 100.0) * (1.0 - S_orig)
     * SkinDist = |(H - 30°) / 25°|, SkinWeight = clamp(1.0 - SkinDist^2, 0.0, 1.0)
     * Boost_final = BaseBoost * (1.0 - 0.75 * SkinWeight)
     * S_new = clamp(S_orig + Boost_final, 0.0, 1.0)
     */
    private fun computeSmartVibrance(h: Float, s: Float, vibrancePercent: Float): Float {
        val baseBoost = (vibrancePercent / 100.0f) * (1.0f - s)

        val skinDegDist = circularHueDistance(h, 30.0f / 360.0f) * 360.0f
        val skinDist = skinDegDist / 25.0f
        val skinWeight = (1.0f - skinDist * skinDist).coerceIn(0.0f, 1.0f)

        val boostFinal = baseBoost * (1.0f - 0.75f * skinWeight)
        return (s + boostFinal).coerceIn(0.0f, 1.0f)
    }

    @Test
    fun testVibranceSkinProtectionVsSkyBoost() {
        // Critical Verification Check: Exact math from document Section 1.3
        val vibranceSetting = 50.0f // +50 Vibrance

        // 1. Indian/Asian/Caucasian Skin Tone: Hue = 30° (Orange/Warm Peach), Saturation = 0.40
        val skinHue = 30.0f / 360.0f
        val skinInitialSat = 0.40f
        val skinNewSat = computeSmartVibrance(skinHue, skinInitialSat, vibranceSetting)
        val skinDeltaPercent = (skinNewSat - skinInitialSat) * 100.0f

        // Skin saturation boost is suppressed by 75% (boost is only 7.5% at +50 vibrance)
        assertEquals("Skin tone saturation delta must be exactly +7.5% at +50 vibrance", 7.5f, skinDeltaPercent, 0.01f)

        // 2. Dull Pale Sky: Hue = 210° (Sky Blue), Saturation = 0.15
        val skyHue = 210.0f / 360.0f
        val skyInitialSat = 0.15f
        val skyNewSat = computeSmartVibrance(skyHue, skyInitialSat, vibranceSetting)
        val skyDeltaPercent = (skyNewSat - skyInitialSat) * 100.0f

        // Sky receives full unsaturated boost of +42.5%!
        assertEquals("Pale sky saturation delta must receive full +42.5% boost", 42.5f, skyDeltaPercent, 0.01f)
    }

    @Test
    fun testSkinToneWeightFalloff() {
        // At center 30°, skin weight must be exactly 1.0
        val centerDist = circularHueDistance(30.0f / 360.0f, 30.0f / 360.0f) * 360.0f / 25.0f
        val centerWeight = (1.0f - centerDist * centerDist).coerceIn(0.0f, 1.0f)
        assertEquals(1.0f, centerWeight, 0.001f)

        // At edge of range 30° + 25° = 55° (or 5°), skin weight drops to 0.0
        val edgeDist = circularHueDistance(55.0f / 360.0f, 30.0f / 360.0f) * 360.0f / 25.0f
        val edgeWeight = (1.0f - edgeDist * edgeDist).coerceIn(0.0f, 1.0f)
        assertEquals(0.0f, edgeWeight, 0.001f)

        // Far outside (e.g. Green at 120° or Blue at 210°), skin weight is 0.0
        val blueDist = circularHueDistance(210.0f / 360.0f, 30.0f / 360.0f) * 360.0f / 25.0f
        val blueWeight = (1.0f - blueDist * blueDist).coerceIn(0.0f, 1.0f)
        assertEquals(0.0f, blueWeight, 0.001f)
    }

    @Test
    fun testAlreadySaturatedColorProtection() {
        // If color is already 90% saturated, Vibrance should barely touch it (inverse saturation damping)
        val saturatedSkyHue = 210.0f / 360.0f
        val saturatedSat = 0.90f
        val newSat = computeSmartVibrance(saturatedSkyHue, saturatedSat, vibrancePercent = 50.0f)
        val deltaPercent = (newSat - saturatedSat) * 100.0f

        // Boost is (50 / 100) * (1.0 - 0.90) = 0.05 (+5%)
        assertEquals(5.0f, deltaPercent, 0.01f)
    }

    /**
     * Section 3.3 3-Way Color Grading Weight Functions:
     * - Shadows Wheel: Y < 0.33
     * - Midtones Wheel: 0.15 < Y < 0.85
     * - Highlights Wheel: Y > 0.66
     * - Blending Slider (0-100, default 50): controls overlap width between zones
     * - Balance Slider (-100 to +100, default 0): shifts priority between Shadows and Highlights
     */
    private fun computeGradingWeights(
        lum: Float,
        balancePercent: Float = 0.0f,
        blendingPercent: Float = 50.0f
    ): FloatArray {
        val balShift = (balancePercent / 100.0f).coerceIn(-1.0f, 1.0f) * 0.20f
        val sThreshold = (0.33f - balShift).coerceIn(0.10f, 0.55f)
        val hThreshold = (0.66f - balShift).coerceIn(0.45f, 0.90f)

        val blendNorm = (blendingPercent / 100.0f).coerceIn(0.0f, 1.0f)
        val blendWidth = 0.05f + blendNorm * 0.30f

        fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
            return t * t * (3.0f - 2.0f * t)
        }

        val wShadow = 1.0f - smoothstep(sThreshold - blendWidth, sThreshold + blendWidth, lum)
        val wHigh = smoothstep(hThreshold - blendWidth, hThreshold + blendWidth, lum)

        val midStart = (0.15f - blendWidth * 0.5f).coerceIn(0.0f, sThreshold)
        val midEnd = (0.85f + blendWidth * 0.5f).coerceIn(hThreshold, 1.0f)
        val wMid = (smoothstep(midStart, sThreshold, lum) * (1.0f - smoothstep(hThreshold, midEnd, lum))).coerceIn(0.0f, 1.0f)

        return floatArrayOf(wShadow, wMid, wHigh)
    }

    @Test
    fun testColorGradingZonalRanges() {
        // 1. Shadows zone (Y < 0.33)
        val shadowSample = computeGradingWeights(0.10f)
        assertTrue("Shadow weight should dominate in low luminance", shadowSample[0] > 0.85f)
        assertEquals("Highlights weight must be 0 in deep shadows", 0.0f, shadowSample[2], 0.001f)

        // 2. Midtones zone (0.15 < Y < 0.85)
        val midSample = computeGradingWeights(0.50f)
        assertTrue("Midtones weight should dominate in middle luminance", midSample[1] > 0.80f)
        assertTrue("Shadows weight must be near zero at Y=0.50", midSample[0] < 0.15f)
        assertTrue("Highlights weight must be near zero at Y=0.50", midSample[2] < 0.15f)

        // 3. Highlights zone (Y > 0.66)
        val highSample = computeGradingWeights(0.90f)
        assertTrue("Highlights weight should dominate in high luminance", highSample[2] > 0.85f)
        assertEquals("Shadows weight must be 0 in bright highlights", 0.0f, highSample[0], 0.001f)
    }

    @Test
    fun testColorGradingBalancePriorityShift() {
        // At Y = 0.55 (just below standard highlight threshold 0.66):
        // Standard Balance = 0: Highlights weight is very low (~0.05)
        val defaultBalance = computeGradingWeights(0.55f, balancePercent = 0.0f)

        // Positive Balance (+80): Shifts priority toward Highlights, pulling threshold down
        val highPriorityBalance = computeGradingWeights(0.55f, balancePercent = 80.0f)
        assertTrue(
            "Positive balance must significantly increase highlights weight at Y=0.55",
            highPriorityBalance[2] > defaultBalance[2] * 2.0f
        )

        // Negative Balance (-80): Shifts priority toward Shadows, pulling shadow reach up at Y=0.40
        val defaultShadow = computeGradingWeights(0.40f, balancePercent = 0.0f)
        val shadowPriorityBalance = computeGradingWeights(0.40f, balancePercent = -80.0f)
        assertTrue(
            "Negative balance must significantly increase shadow weight at Y=0.40",
            shadowPriorityBalance[0] > defaultShadow[0] * 1.5f
        )
    }

    @Test
    fun testColorGradingBlendingWidthControl() {
        // At boundary Y = 0.38 (between shadows and midtones):
        // 1. Tight Blending (0%): Sharp zone boundary, minimal shadow bleed
        val tightBlend = computeGradingWeights(0.38f, blendingPercent = 0.0f)

        // 2. Wide Blending (100%): Wide overlap, broad color tint bleed
        val wideBlend = computeGradingWeights(0.38f, blendingPercent = 100.0f)

        assertTrue(
            "Wide blending must allow higher cross-zone overlap than tight blending",
            wideBlend[0] > tightBlend[0]
        )
    }
}
