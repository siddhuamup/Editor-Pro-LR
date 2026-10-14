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
}
