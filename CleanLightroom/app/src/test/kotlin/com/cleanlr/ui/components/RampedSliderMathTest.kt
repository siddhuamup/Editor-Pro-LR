package com.cleanlr.ui.components

import com.cleanlr.engine.ColorScience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * Task T5 Verification: Non-Linear Ramped Slider Mathematics & Curve Audit (PDF Pages 3 & 4)
 * - Formula: f(x) = sign(x) * |x|^1.4
 * - Exposure Mapping: Exposure(x) = sign(x) * 5.0 * |x|^1.4
 * - Center Precision Zone (|x| < 0.2): Slows down by ~35% - 47%
 * - Extreme Zone (|x| > 0.7): Rapid acceleration to +/- 5.0
 * - Haptic Zero Snap Deadzone: |x| < 0.03
 */
class RampedSliderMathTest {

    @Test
    fun testPiecewisePowerCurveFormulaAndPrecisionZones() {
        val maxExposure = 5.0f

        // 1. Center High-Precision Zone (|x| < 0.2)
        // Linear mapping would produce 1.0 EV at x = 0.2.
        // Ramped mapping: 5.0 * 0.2^1.4 = ~0.5255 EV (slowed down touch rate allows micro 0.05 EV adjustments)
        val xCenter = 0.2f
        val expCenter = ColorScience.rampedValue(xCenter, maxExposure)
        val expectedCenter = maxExposure * xCenter.pow(1.4f)
        assertEquals(expectedCenter, expCenter, 0.0001f)
        assertTrue("At x=0.2, exposure must be ~0.53 EV (got $expCenter), slowing down touch by > 35%", expCenter < 0.60f)

        // 2. Extreme Acceleration Zone (|x| > 0.7)
        val xExtreme = 0.7f
        val expExtreme = ColorScience.rampedValue(xExtreme, maxExposure)
        val expectedExtreme = maxExposure * xExtreme.pow(1.4f)
        assertEquals(expectedExtreme, expExtreme, 0.0001f)
        assertTrue("At x=0.7, exposure ramps up past 3.0 EV (got $expExtreme)", expExtreme > 3.0f)

        // 3. Full Reach (+/- 5.0 EV at x = +/- 1.0)
        assertEquals(5.0f, ColorScience.rampedValue(1.0f, maxExposure), 0.0001f)
        assertEquals(-5.0f, ColorScience.rampedValue(-1.0f, maxExposure), 0.0001f)

        // 4. Exact Symmetry: f(-x) = -f(x)
        val testPoints = floatArrayOf(0.05f, 0.15f, 0.35f, 0.50f, 0.85f, 0.95f)
        for (x in testPoints) {
            val positive = ColorScience.rampedValue(x, maxExposure)
            val negative = ColorScience.rampedValue(-x, maxExposure)
            assertEquals("Symmetry violated at x=$x", positive, -negative, 0.0001f)
        }
    }

    @Test
    fun testInverseRampedMappingRoundtrip() {
        val maxExposure = 5.0f
        val testValues = floatArrayOf(-5.0f, -3.2f, -1.0f, -0.25f, -0.05f, 0.0f, 0.05f, 0.25f, 1.0f, 3.2f, 5.0f)

        for (v in testValues) {
            val normPos = ColorScience.inverseRampedValue(v, maxExposure)
            val reconstructed = ColorScience.rampedValue(normPos, maxExposure)
            assertEquals("Roundtrip failed for EV=$v", v, reconstructed, 0.001f)
        }
    }

    @Test
    fun testHapticZeroSnappingThreshold() {
        // Spec Page 4: Haptic Zero Snapping at |x| < 0.03
        val snapThreshold = 0.03f
        val maxExposure = 5.0f

        fun simulateSliderTouch(normX: Float): Float {
            var sPos = (normX - 0.5f) * 2.0f // -1.0 to 1.0
            if (abs(sPos) < snapThreshold) {
                sPos = 0.0f
            }
            return ColorScience.rampedValue(sPos, maxExposure)
        }

        // Inside deadzone (|x| < 0.03, i.e., normX in [0.486 .. 0.514])
        assertEquals(0.0f, simulateSliderTouch(0.500f), 0.0001f) // exact center
        assertEquals(0.0f, simulateSliderTouch(0.510f), 0.0001f) // sPos = 0.02 < 0.03 -> snapped
        assertEquals(0.0f, simulateSliderTouch(0.490f), 0.0001f) // sPos = -0.02 > -0.03 -> snapped

        // Outside deadzone (|x| >= 0.03)
        val outsidePos = simulateSliderTouch(0.520f) // sPos = 0.04 > 0.03
        assertTrue("Outside deadzone must produce non-zero value (got $outsidePos)", outsidePos > 0.05f)
    }
}
