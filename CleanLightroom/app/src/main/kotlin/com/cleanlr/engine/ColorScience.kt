package com.cleanlr.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

object ColorScience {

    // Unified Full-Precision Melissa RGB Matrices (dng_space_ProPhoto_Linear / libLrAndroid.so)
    // 1. Linear sRGB -> Melissa RGB (Row-major for Kotlin, Column-major in GLSL)
    val M_SRGB_TO_MELISSA = doubleArrayOf(
        0.52932378745827813, 0.33005506400369466, 0.14064019169871309, // Row 0
        0.098426540365603984, 0.87350873408018318, 0.028111562208033621, // Row 1
        0.016845769297481432, 0.11769549320295874, 0.86547399478709403  // Row 2
    )

    // 2. Melissa RGB -> Linear sRGB (Inverse matrix)
    val M_MELISSA_TO_SRGB = doubleArrayOf(
         2.03420482, -0.72726743, -0.30693739, // Row 0
        -0.22895313,  1.23175691, -0.00280378, // Row 1
        -0.00846069, -0.15334315,  1.16180384  // Row 2
    )

    // 3. Melissa RGB -> Display P3 (D65) (M_melissa2P3, PDF Page 2)
    val M_MELISSA_TO_P3 = doubleArrayOf(
         1.345943, -0.255608, -0.051112, // Row 0
        -0.544587,  1.508167,  0.020536, // Row 1
         0.000000, -0.020584,  1.211812  // Row 2
    )

    /**
     * Calculates White Balance RGB gains for shader based on Kelvin temperature & Tint.
     * Normalized relative to reference daylight ~5500K.
     * Returns FloatArray(3) with (gainR, gainG, gainB).
     */
    fun kelvinTintToRgbGain(kelvin: Float, tint: Float): FloatArray {
        // 1. Calculate blackbody color from Kelvin (Tanner-Helland / Krystek approximation)
        val temp = (kelvin / 100.0f).coerceIn(20.0f, 500.0f)
        var r: Float
        var g: Float
        var b: Float

        if (temp <= 66.0f) {
            r = 255.0f
            g = 99.4708025861f * ln(temp) - 161.1195681661f
            b = if (temp <= 19.0f) 0.0f else 138.5177312231f * ln(temp - 10.0f) - 305.0447927307f
        } else {
            r = 329.698727446f * (temp - 60.0f).pow(-0.1332047592f)
            g = 288.1221695283f * (temp - 60.0f).pow(-0.0755148492f)
            b = 255.0f
        }

        r = r.coerceIn(0.0f, 255.0f) / 255.0f
        g = g.coerceIn(0.0f, 255.0f) / 255.0f
        b = b.coerceIn(0.0f, 255.0f) / 255.0f

        // Reference 5500K daylight values
        val refTemp = 55.0f
        val refR = 255.0f / 255.0f
        val refG = (99.4708025861f * ln(refTemp) - 161.1195681661f).coerceIn(0.0f, 255.0f) / 255.0f
        val refB = (138.5177312231f * ln(refTemp - 10.0f) - 305.0447927307f).coerceIn(0.0f, 255.0f) / 255.0f

        // Inverse multipliers to balance against reference
        var gainR = (refR / max(r, 0.001f))
        var gainG = (refG / max(g, 0.001f))
        var gainB = (refB / max(b, 0.001f))

        // 2. Tint adjustment: Magenta (+) adds red & blue, Green (-) adds green
        val tNorm = (tint / 150.0f).coerceIn(-1.0f, 1.0f)
        if (tNorm > 0.0f) {
            gainR *= (1.0f + tNorm * 0.20f)
            gainB *= (1.0f + tNorm * 0.20f)
            gainG *= (1.0f - tNorm * 0.15f)
        } else {
            val greenBoost = -tNorm
            gainG *= (1.0f + greenBoost * 0.30f)
            gainR *= (1.0f - greenBoost * 0.10f)
            gainB *= (1.0f - greenBoost * 0.10f)
        }

        // Normalize so green gain is ~1.0
        val normG = gainG
        return floatArrayOf(gainR / normG, 1.0f, gainB / normG)
    }

    /**
     * Non-linear Ramped Slider Mapping:
     * Slows down touch adjustment by ~35% around zero for precision,
     * accelerates towards extremities.
     *
     * @param normalizedSliderPos value in range [-1.0 .. +1.0]
     * @param maxRange maximum positive value (e.g. 5.0 for exposure, 100.0 for contrast)
     * @param exponent default 1.4
     */
    fun rampedValue(normalizedSliderPos: Float, maxRange: Float, exponent: Float = 1.4f): Float {
        val s = normalizedSliderPos.coerceIn(-1.0f, 1.0f)
        return sign(s) * maxRange * kotlin.math.abs(s).pow(exponent)
    }

    /**
     * Inverse mapping from actual value to normalized slider position [-1.0 .. 1.0].
     */
    fun inverseRampedValue(value: Float, maxRange: Float, exponent: Float = 1.4f): Float {
        val v = value.coerceIn(-maxRange, maxRange)
        val ratio = kotlin.math.abs(v) / maxRange
        return sign(v) * ratio.pow(1.0f / exponent)
    }

    fun srgbToLinear(c: Double): Double {
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    fun linearToSrgb(c: Double): Double {
        val cl = c.coerceIn(0.0, 1.0)
        return if (cl <= 0.0031308) {
            12.92 * cl
        } else {
            1.055 * cl.pow(1.0 / 2.4) - 0.055
        }
    }

    /**
     * Converts sRGB [0.0 .. 1.0] to CIE L*a*b* under standard D65 illuminant.
     * Returns DoubleArray(3) with [L*, a*, b*].
     */
    fun srgbToLab(r: Double, g: Double, b: Double): DoubleArray {
        val rLin = srgbToLinear(r)
        val gLin = srgbToLinear(g)
        val bLin = srgbToLinear(b)

        // sRGB to CIE XYZ (D65)
        val x = (0.4124564 * rLin + 0.3575761 * gLin + 0.1804375 * bLin) / 0.95047
        val y = (0.2126729 * rLin + 0.7151522 * gLin + 0.0721750 * bLin) / 1.00000
        val z = (0.0193339 * rLin + 0.1191920 * gLin + 0.9503041 * bLin) / 1.08883

        val epsilon = 216.0 / 24389.0 // (6/29)^3
        val kappa = 24389.0 / 27.0    // (29/3)^3

        fun f(t: Double): Double {
            return if (t > epsilon) t.pow(1.0 / 3.0) else (kappa * t + 16.0) / 116.0
        }

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        val lStar = (116.0 * fy) - 16.0
        val aStar = 500.0 * (fx - fy)
        val bStar = 200.0 * (fy - fz)

        return doubleArrayOf(lStar, aStar, bStar)
    }

    /**
     * Computes CIE delta E 1976 (standard Euclidean color distance in L*a*b* space).
     */
    fun deltaE76(lab1: DoubleArray, lab2: DoubleArray): Double {
        val dL = lab1[0] - lab2[0]
        val da = lab1[1] - lab2[1]
        val db = lab1[2] - lab2[2]
        return kotlin.math.sqrt(dL * dL + da * da + db * db)
    }

    /**
     * Computes CIE delta E 1976 directly from two sRGB colors [0.0 .. 1.0].
     */
    fun deltaE76FromSrgb(r1: Double, g1: Double, b1: Double, r2: Double, g2: Double, b2: Double): Double {
        val lab1 = srgbToLab(r1, g1, b1)
        val lab2 = srgbToLab(r2, g2, b2)
        return deltaE76(lab1, lab2)
    }
}

