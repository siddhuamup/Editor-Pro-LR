package com.cleanlr.engine

import com.cleanlr.model.CurvePoint
import com.cleanlr.model.ToneCurves
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Fritsch-Carlson Monotone Cubic Hermite Spline Interpolator
 * Implements Lightroom CParametricCurveTag 4-Zone Spline Mapping & Point Curves.
 * Guarantees monotonicity to prevent overshoot and solarization artifacts in tone curves.
 */
object SplineInterpolator {

    /**
     * Interpolates points using Fritsch-Carlson algorithm.
     * Evaluates curve values for integer inputs x in 0..255.
     */
    fun evaluateSpline(points: List<CurvePoint>): FloatArray {
        if (points.isEmpty()) {
            return FloatArray(256) { it.toFloat() }
        }
        val sorted = points.sortedBy { it.x }.distinctBy { it.x }
        val n = sorted.size
        if (n == 1) {
            return FloatArray(256) { sorted[0].y }
        }

        val x = DoubleArray(n) { sorted[it].x.toDouble() }
        val y = DoubleArray(n) { sorted[it].y.toDouble() }

        // 1. Secants and step sizes
        val h = DoubleArray(n - 1)
        val delta = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            h[i] = x[i + 1] - x[i]
            delta[i] = if (h[i] > 1e-6) (y[i + 1] - y[i]) / h[i] else 0.0
        }

        // 2. Initial tangents
        val d = DoubleArray(n)
        d[0] = delta[0]
        d[n - 1] = delta[n - 2]
        for (i in 1 until n - 1) {
            if (delta[i - 1] * delta[i] <= 0.0) {
                d[i] = 0.0
            } else {
                d[i] = (delta[i - 1] + delta[i]) / 2.0
            }
        }

        // 3. Fritsch-Carlson monotonicity condition
        for (i in 0 until n - 1) {
            if (abs(delta[i]) < 1e-7) {
                d[i] = 0.0
                d[i + 1] = 0.0
            } else {
                val alpha = d[i] / delta[i]
                val beta = d[i + 1] / delta[i]
                val distSq = alpha * alpha + beta * beta
                if (distSq > 9.0) {
                    val tau = 3.0 / sqrt(distSq)
                    d[i] = tau * alpha * delta[i]
                    d[i + 1] = tau * beta * delta[i]
                }
            }
        }

        // 4. Sample 256 values
        val lut = FloatArray(256)
        var seg = 0
        for (i in 0..255) {
            val xi = i.toDouble()
            while (seg < n - 2 && xi > x[seg + 1]) {
                seg++
            }
            val hSeg = h[seg]
            if (hSeg < 1e-6) {
                lut[i] = y[seg].toFloat()
            } else {
                val t = (xi - x[seg]) / hSeg
                val t2 = t * t
                val t3 = t2 * t

                val h00 = 2.0 * t3 - 3.0 * t2 + 1.0
                val h10 = t3 - 2.0 * t2 + t
                val h01 = -2.0 * t3 + 3.0 * t2
                val h11 = t3 - t2

                val yi = h00 * y[seg] + h10 * hSeg * d[seg] + h01 * y[seg + 1] + h11 * hSeg * d[seg + 1]
                lut[i] = min(255.0, max(0.0, yi)).toFloat()
            }
        }
        return lut
    }

    /**
     * Computes the 5 Control Nodes for the 4-zone Parametric Tone Curve
     * Formulas (Lightroom CParametricCurveTag):
     * Node 0: (0, 0)
     * Node 1: (Split_shadow, Split_shadow + ((S_shadows + S_darks) / 2) * 0.25)
     * Node 2: (Split_mid, Split_mid + ((S_darks + S_lights) / 2) * 0.35)
     * Node 3: (Split_highlight, Split_highlight + ((S_lights + S_highlights) / 2) * 0.25)
     * Node 4: (255, 255)
     */
    fun computeParametricNodes(curves: ToneCurves): List<CurvePoint> {
        val sShadow = curves.parametricShadows
        val sDarks = curves.parametricDarks
        val sLights = curves.parametricLights
        val sHighlights = curves.parametricHighlights

        val x0 = 0.0f
        val y0 = 0.0f

        val x1 = curves.splitShadow.coerceIn(1.0f, curves.splitMid - 1.0f)
        val y1 = (x1 + ((sShadow + sDarks) / 2.0f) * 0.25f).coerceIn(0.0f, 255.0f)

        val x2 = curves.splitMid.coerceIn(x1 + 1.0f, curves.splitHighlight - 1.0f)
        val y2 = (x2 + ((sDarks + sLights) / 2.0f) * 0.35f).coerceIn(0.0f, 255.0f)

        val x3 = curves.splitHighlight.coerceIn(x2 + 1.0f, 254.0f)
        val y3 = (x3 + ((sLights + sHighlights) / 2.0f) * 0.25f).coerceIn(0.0f, 255.0f)

        val x4 = 255.0f
        val y4 = 255.0f

        return listOf(
            CurvePoint(x0, y0),
            CurvePoint(x1, y1),
            CurvePoint(x2, y2),
            CurvePoint(x3, y3),
            CurvePoint(x4, y4)
        )
    }

    /**
     * Generates a 256-byte 1D LUT texture buffer exclusively for the 4-Zone Parametric Curve.
     */
    fun generateParametricLut256(curves: ToneCurves): ByteArray {
        val nodes = computeParametricNodes(curves)
        val spline = evaluateSpline(nodes)
        val result = ByteArray(256)
        for (i in 0..255) {
            result[i] = spline[i].toInt().coerceIn(0, 255).toByte()
        }
        return result
    }

    /**
     * Generates a 256-byte 1D LUT texture buffer exclusively for the Point Curve.
     */
    fun generatePointCurveLut256(points: List<CurvePoint>): ByteArray {
        val spline = evaluateSpline(points)
        val result = ByteArray(256)
        for (i in 0..255) {
            result[i] = spline[i].toInt().coerceIn(0, 255).toByte()
        }
        return result
    }

    /**
     * Chains both curves: PointCurve(ParametricCurve(x)).
     * Used when chaining without two texture passes.
     */
    fun generateLut256(curves: ToneCurves): ByteArray {
        val paramLut = generateParametricLut256(curves)
        val pointSpline = evaluateSpline(curves.pointCurve)

        val result = ByteArray(256)
        for (i in 0..255) {
            val paramOut = paramLut[i].toInt() and 0xFF
            val finalVal = pointSpline[paramOut].toInt().coerceIn(0, 255)
            result[i] = finalVal.toByte()
        }
        return result
    }

    /**
     * Tip 162: Generates a 256x1 RGBA 4-channel LUT texture buffer.
     * Evaluates Composite + individual RGB curves chained:
     * - Red: RedCurve(Composite(x))
     * - Green: GreenCurve(Composite(x))
     * - Blue: BlueCurve(Composite(x))
     * - Alpha: 255
     */
    fun generateRgbaLut256(curves: ToneCurves): ByteArray {
        val compositeSpline = evaluateSpline(curves.composite)
        val redSpline = evaluateSpline(curves.red)
        val greenSpline = evaluateSpline(curves.green)
        val blueSpline = evaluateSpline(curves.blue)

        val result = ByteArray(256 * 4)
        for (i in 0..255) {
            val compVal = compositeSpline[i].toInt().coerceIn(0, 255)
            val rVal = redSpline[compVal].toInt().coerceIn(0, 255)
            val gVal = greenSpline[compVal].toInt().coerceIn(0, 255)
            val bVal = blueSpline[compVal].toInt().coerceIn(0, 255)

            result[i * 4 + 0] = rVal.toByte()
            result[i * 4 + 1] = gVal.toByte()
            result[i * 4 + 2] = bVal.toByte()
            result[i * 4 + 3] = 255.toByte()
        }
        return result
    }
}
