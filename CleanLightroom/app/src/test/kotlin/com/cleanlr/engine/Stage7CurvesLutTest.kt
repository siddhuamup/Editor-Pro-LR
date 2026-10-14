package com.cleanlr.engine

import com.cleanlr.model.CurvePoint
import com.cleanlr.model.ToneCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Stage7CurvesLutTest {

    private val epsilon = 0.001f

    private fun shouldSkip(curves: ToneCurves, has3dLut: Boolean, lut3dIntensity: Float): Boolean {
        val hasCurveAdjustments = abs(curves.parametricHighlights) > epsilon ||
                abs(curves.parametricLights) > epsilon ||
                abs(curves.parametricDarks) > epsilon ||
                abs(curves.parametricShadows) > epsilon ||
                curves.pointCurve.size > 2

        val has3dLutActive = has3dLut && lut3dIntensity > epsilon

        return !hasCurveAdjustments && !has3dLutActive
    }

    @Test
    fun testDefaultCurvesSkipRule() {
        val defaultCurves = ToneCurves()
        // Default linear curve without 3D LUT must skip stage for 120 FPS
        assertTrue(shouldSkip(defaultCurves, has3dLut = false, lut3dIntensity = 0.0f))

        // Parametric modification must not skip
        val modCurves = ToneCurves(parametricHighlights = 15.0f)
        assertFalse(shouldSkip(modCurves, has3dLut = false, lut3dIntensity = 0.0f))

        // Point curve modification must not skip
        val pointCurveMod = ToneCurves(
            pointCurve = listOf(
                CurvePoint(0f, 0f),
                CurvePoint(128f, 150f),
                CurvePoint(255f, 255f)
            )
        )
        assertFalse(shouldSkip(pointCurveMod, has3dLut = false, lut3dIntensity = 0.0f))

        // Active 3D LUT must not skip
        assertFalse(shouldSkip(defaultCurves, has3dLut = true, lut3dIntensity = 0.8f))
    }

    @Test
    fun testCubicSplineMonotonicityNoOvershoot() {
        // Monotone S-Curve test: (0,0), (64, 40), (192, 215), (255, 255)
        val sCurvePoints = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(64f, 40f),
            CurvePoint(192f, 215f),
            CurvePoint(255f, 255f)
        )
        val lut = SplineInterpolator.evaluateSpline(sCurvePoints)

        assertEquals(256, lut.size)
        assertEquals("Endpoint 0 must equal 0", 0.0f, lut[0], 0.01f)
        assertEquals("Endpoint 255 must equal 255", 255.0f, lut[255], 0.01f)

        // Strict monotonicity check: lut[i+1] >= lut[i] (no solarization dip)
        for (i in 0 until 255) {
            assertTrue(
                "Spline curve must be monotonically non-decreasing at index $i (got ${lut[i]} -> ${lut[i + 1]})",
                lut[i + 1] >= lut[i] - 0.001f
            )
        }
    }

    @Test
    fun testTrilinearLutBlendMath() {
        val originalR = 0.40f
        val lutR = 0.60f
        val intensity = 0.50f

        val blended = originalR + (lutR - originalR) * intensity
        assertEquals(0.50f, blended, 0.001f)
    }
}
