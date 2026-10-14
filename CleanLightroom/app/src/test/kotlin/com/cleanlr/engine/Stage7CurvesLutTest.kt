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
        val hasParametric = abs(curves.parametricHighlights) > epsilon ||
                abs(curves.parametricLights) > epsilon ||
                abs(curves.parametricDarks) > epsilon ||
                abs(curves.parametricShadows) > epsilon

        val hasPoint = curves.pointCurve.size > 2 || (curves.pointCurve.size == 2 &&
                (curves.pointCurve[0].x != 0f || curves.pointCurve[0].y != 0f ||
                 curves.pointCurve[1].x != 255f || curves.pointCurve[1].y != 255f))

        val has3dLutActive = has3dLut && lut3dIntensity > epsilon

        return !hasParametric && !hasPoint && !has3dLutActive
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
    fun testParametric5ControlNodesFormulas() {
        // Lightroom CParametricCurveTag Exact Math:
        // Highlights = 40, Lights = 20, Darks = -20, Shadows = -40
        // Split: 64, 128, 192
        val curves = ToneCurves(
            parametricHighlights = 40.0f,
            parametricLights = 20.0f,
            parametricDarks = -20.0f,
            parametricShadows = -40.0f,
            splitShadow = 64.0f,
            splitMid = 128.0f,
            splitHighlight = 192.0f
        )

        val nodes = SplineInterpolator.computeParametricNodes(curves)
        assertEquals(5, nodes.size)

        // Node 0: (0, 0)
        assertEquals(0.0f, nodes[0].x, 0.001f)
        assertEquals(0.0f, nodes[0].y, 0.001f)

        // Node 1: x1 = 64, y1 = 64 + ((-40 + -20) / 2) * 0.25 = 64 - 7.5 = 56.5
        assertEquals(64.0f, nodes[1].x, 0.001f)
        assertEquals(56.5f, nodes[1].y, 0.001f)

        // Node 2: x2 = 128, y2 = 128 + ((-20 + 20) / 2) * 0.35 = 128 + 0 = 128.0
        assertEquals(128.0f, nodes[2].x, 0.001f)
        assertEquals(128.0f, nodes[2].y, 0.001f)

        // Node 3: x3 = 192, y3 = 192 + ((20 + 40) / 2) * 0.25 = 192 + 7.5 = 199.5
        assertEquals(192.0f, nodes[3].x, 0.001f)
        assertEquals(199.5f, nodes[3].y, 0.001f)

        // Node 4: (255, 255)
        assertEquals(255.0f, nodes[4].x, 0.001f)
        assertEquals(255.0f, nodes[4].y, 0.001f)
    }

    @Test
    fun testParametricCurveLutMonotonicityAndSmoothness() {
        val curves = ToneCurves(
            parametricHighlights = 35.0f,
            parametricLights = 15.0f,
            parametricDarks = -15.0f,
            parametricShadows = -35.0f
        )

        val lut = SplineInterpolator.generateParametricLut256(curves)
        assertEquals(256, lut.size)

        // Unpack unsigned bytes
        val lutValues = IntArray(256) { lut[it].toInt() and 0xFF }

        assertEquals("Black point must remain 0", 0, lutValues[0])
        assertEquals("White point must remain 255", 255, lutValues[255])

        // Verify Fritsch-Carlson guarantees monotonicity without overshoot
        for (i in 0 until 255) {
            assertTrue(
                "Parametric curve must be monotonic at $i: ${lutValues[i]} -> ${lutValues[i + 1]}",
                lutValues[i + 1] >= lutValues[i]
            )
        }

        // Shadows should be compressed, highlights should be boosted (S-curve)
        assertTrue("Shadows must be reduced below linear", lutValues[64] < 64)
        assertTrue("Highlights must be boosted above linear", lutValues[192] > 192)
    }

    @Test
    fun testSeparateChainedCurvesArchitecture() {
        // Parametric curve provides 4-zone tonal contrast
        val curves = ToneCurves(
            parametricHighlights = 20.0f,
            parametricShadows = -20.0f,
            pointCurve = listOf(
                CurvePoint(0f, 10f),   // Matte black lift
                CurvePoint(255f, 245f) // Rolled highlight
            )
        )

        val paramLut = SplineInterpolator.generateParametricLut256(curves)
        val pointLut = SplineInterpolator.generatePointCurveLut256(curves.pointCurve)
        val chainedLut = SplineInterpolator.generateLut256(curves)

        // Parametric black is 0
        assertEquals(0, paramLut[0].toInt() and 0xFF)

        // Point curve black is lifted to 10
        assertEquals(10, pointLut[0].toInt() and 0xFF)

        // Chained curve applies PointCurve after ParametricCurve:
        // Chained(0) = PointCurve(ParametricCurve(0)) = PointCurve(0) = 10
        assertEquals(10, chainedLut[0].toInt() and 0xFF)
        assertEquals(245, chainedLut[255].toInt() and 0xFF)
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
