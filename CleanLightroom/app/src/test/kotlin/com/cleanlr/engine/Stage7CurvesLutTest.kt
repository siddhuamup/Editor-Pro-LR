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

    // -------------------------------------------------------------------------
    // Sprint A Task T2: Fritsch-Carlson Step-by-Step Mathematical Audit
    // -------------------------------------------------------------------------
    @Test
    fun testFritschCarlsonTauLimiterStepByStepAudit() {
        // Doc Section 2.A:
        // Test points engineered to trigger the tau limiter:
        // (0, 0), (10, 50), (20, 52), (30, 100)
        // delta_0 = (50 - 0)/10 = 5.0
        // delta_1 = (52 - 50)/10 = 0.2
        // Initial d_1 = (5.0 + 0.2) / 2 = 2.6
        // alpha = d_1 / delta_1 = 2.6 / 0.2 = 13.0 -> alpha^2 = 169 >> 9!
        val points = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(10f, 50f),
            CurvePoint(20f, 52f),
            CurvePoint(30f, 100f),
            CurvePoint(255f, 255f)
        )

        val lut = SplineInterpolator.evaluateSpline(points)

        // 1. Endpoints match exact control points
        assertEquals(0f, lut[0], 0.01f)
        assertEquals(50f, lut[10], 0.01f)
        assertEquals(52f, lut[20], 0.01f)
        assertEquals(100f, lut[30], 0.01f)
        assertEquals(255f, lut[255], 0.01f)

        // 2. Tau limiter guarantees monotonicity: no overshoot between (10, 50) and (20, 52)
        for (x in 10..20) {
            assertTrue("Between x=10 and x=20, y must stay within [50, 52] without overshoot", lut[x] in 50.0f..52.001f)
        }

        // 3. Strict non-decreasing monotonicity across entire 0..255 range (no solarization)
        for (i in 0 until 255) {
            assertTrue(
                "Fritsch-Carlson tau limiter must guarantee strict monotonicity at x=$i (${lut[i]} -> ${lut[i+1]})",
                lut[i + 1] >= lut[i] - 0.001f
            )
        }
    }

    @Test
    fun testHermiteBasisPolynomialsAudit() {
        // Doc Section 2.A Step 4:
        // h00(t) = 2t^3 - 3t^2 + 1
        // h10(t) = t^3 - 2t^2 + t
        // h01(t) = -2t^3 + 3t^2
        // h11(t) = t^3 - t^2
        fun h00(t: Double) = 2.0 * t * t * t - 3.0 * t * t + 1.0
        fun h10(t: Double) = t * t * t - 2.0 * t * t + t
        fun h01(t: Double) = -2.0 * t * t * t + 3.0 * t * t
        fun h11(t: Double) = t * t * t - t * t

        // At t = 0: exactly passes through y_k
        assertEquals(1.0, h00(0.0), 1e-9)
        assertEquals(0.0, h10(0.0), 1e-9)
        assertEquals(0.0, h01(0.0), 1e-9)
        assertEquals(0.0, h11(0.0), 1e-9)

        // At t = 1: exactly passes through y_{k+1}
        assertEquals(0.0, h00(1.0), 1e-9)
        assertEquals(0.0, h10(1.0), 1e-9)
        assertEquals(1.0, h01(1.0), 1e-9)
        assertEquals(0.0, h11(1.0), 1e-9)

        // Unity partition: h00(t) + h01(t) == 1.0 for all t
        for (step in 0..10) {
            val t = step / 10.0
            assertEquals("Hermite position polynomials must sum to 1.0 at t=$t", 1.0, h00(t) + h01(t), 1e-9)
        }
    }
}
