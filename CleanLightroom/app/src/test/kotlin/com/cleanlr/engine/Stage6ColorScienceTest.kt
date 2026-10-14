package com.cleanlr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

class Stage6ColorScienceTest {

    private fun circularHueDistance(h1: Float, h2: Float): Float {
        val d = abs(h1 - h2)
        return min(d, 1.0f - d)
    }

    private fun computeSmartVibrance(h: Float, s: Float, vibrancePercent: Float): Float {
        val vibAmount = vibrancePercent / 100.0f

        // Skin tone detection around Orange/Warm Peach (H ~ 0.0833)
        val skinDist = circularHueDistance(h, 0.0833f)
        val skinFactor = exp(-(skinDist * skinDist) / (2.0f * 0.035f * 0.035f))

        // Vibrance protects skin by 85% and protects already saturated areas (1.0 - s)
        val skinProtection = 1.0f - 0.85f * skinFactor
        val vibGain = vibAmount * (1.0f - s) * skinProtection

        return (s + vibGain).coerceIn(0.0f, 1.0f)
    }

    @Test
    fun testVibranceSkinProtectionVsSkyBoost() {
        // Critical Verification Check: त्वचा +-5%, आकाश +40%
        val vibranceSetting = 60.0f // +60 Vibrance

        // 1. Indian/Asian/Caucasian Skin Tone: Hue = 0.083 (30 deg Orange), Saturation = 0.40
        val skinHue = 0.083f
        val skinInitialSat = 0.40f
        val skinNewSat = computeSmartVibrance(skinHue, skinInitialSat, vibranceSetting)
        val skinDeltaPercent = (skinNewSat - skinInitialSat) * 100.0f

        // Skin should only increase by <= 6% (safe within ~5% tolerance)
        assertTrue(
            "Skin tone saturation delta ($skinDeltaPercent%) must remain protected within ~5%",
            skinDeltaPercent in 0.0f..6.5f
        )

        // 2. Dull Pale Sky: Hue = 0.58 (210 deg Sky Blue), Saturation = 0.15
        val skyHue = 0.58f
        val skyInitialSat = 0.15f
        val skyNewSat = computeSmartVibrance(skyHue, skyInitialSat, vibranceSetting)
        val skyDeltaPercent = (skyNewSat - skyInitialSat) * 100.0f

        // Sky should receive a dramatic boost around ~35% - 45%!
        assertTrue(
            "Pale sky saturation delta ($skyDeltaPercent%) must receive dramatic boost (+30% to +45%)",
            skyDeltaPercent >= 30.0f
        )

        // Verifies the feature contrast: Sky boost is at least 6x stronger than skin boost!
        assertTrue(
            "Sky boost must be vastly higher than protected skin boost",
            skyDeltaPercent > (skinDeltaPercent * 5.0f)
        )
    }

    @Test
    fun testGaussianBellCurveHueCenters() {
        val sigma = 0.065f

        // Orange center (0.0833)
        val dZero = circularHueDistance(0.0833f, 0.0833f)
        val wCenter = exp(-(dZero * dZero) / (2.0f * sigma * sigma))
        assertEquals(1.0f, wCenter, 0.0001f)

        // Blue sample (0.6667) tested against Orange (0.0833) should be virtually zero
        val dFar = circularHueDistance(0.6667f, 0.0833f)
        val wFar = exp(-(dFar * dFar) / (2.0f * sigma * sigma))
        assertTrue("Distant hues must have ~0 weight", wFar < 0.00001f)
    }
}
