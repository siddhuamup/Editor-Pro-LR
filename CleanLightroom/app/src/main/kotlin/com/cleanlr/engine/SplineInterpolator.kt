package com.cleanlr.engine

import com.cleanlr.model.CurvePoint
import com.cleanlr.model.ToneCurves
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Fritsch-Carlson Monotone Cubic Hermite Spline Interpolator
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
            if (kotlin.math.abs(delta[i]) < 1e-7) {
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
     * Generates a 256-byte 1D LUT texture buffer from parametric curve & point curve.
     */
    fun generateLut256(curves: ToneCurves): ByteArray {
        val baseSpline = evaluateSpline(curves.pointCurve)
        val result = ByteArray(256)

        // Split thresholds
        val s0 = curves.splitShadow
        val s1 = curves.splitMid
        val s2 = curves.splitHighlight

        val pHighlights = curves.parametricHighlights / 100.0f
        val pLights = curves.parametricLights / 100.0f
        val pDarks = curves.parametricDarks / 100.0f
        val pShadows = curves.parametricShadows / 100.0f

        for (i in 0..255) {
            val inVal = i.toFloat()
            var paramOffset = 0.0f

            // 4-zone parametric curve offset
            if (inVal < s0) {
                val factor = (s0 - inVal) / max(1.0f, s0)
                paramOffset += pShadows * factor * 35.0f
            } else if (inVal < s1) {
                val factor = (s1 - inVal) / max(1.0f, s1 - s0)
                paramOffset += pDarks * factor * 25.0f
            } else if (inVal < s2) {
                val factor = (inVal - s1) / max(1.0f, s2 - s1)
                paramOffset += pLights * factor * 25.0f
            } else {
                val factor = (inVal - s2) / max(1.0f, 255.0f - s2)
                paramOffset += pHighlights * factor * 35.0f
            }

            val combined = min(255.0f, max(0.0f, baseSpline[i] + paramOffset))
            result[i] = combined.toInt().toByte()
        }
        return result
    }
}
