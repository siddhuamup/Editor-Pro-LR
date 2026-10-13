package com.cleanlr.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

object ColorScience {

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
}
