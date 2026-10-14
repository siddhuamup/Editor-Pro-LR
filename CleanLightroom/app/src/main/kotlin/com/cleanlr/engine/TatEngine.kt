package com.cleanlr.engine

import com.cleanlr.model.HslAdjustments
import com.cleanlr.model.HslChannel
import kotlin.math.*

enum class TatTargetMode(val label: String) {
    SATURATION("Saturation"),
    HUE("Hue"),
    LUMINANCE("Luminance")
}

data class TatChannelWeights(
    val red: Float = 0f,
    val orange: Float = 0f,
    val yellow: Float = 0f,
    val green: Float = 0f,
    val aqua: Float = 0f,
    val blue: Float = 0f,
    val purple: Float = 0f,
    val magenta: Float = 0f
) {
    fun dominantChannelName(): String {
        val pairs = listOf(
            "Red" to red,
            "Orange" to orange,
            "Yellow" to yellow,
            "Green" to green,
            "Aqua" to aqua,
            "Blue" to blue,
            "Purple" to purple,
            "Magenta" to magenta
        )
        return pairs.maxByOrNull { it.second }?.first ?: "None"
    }
}

/**
 * Section 3.2: Targeted Adjustment Tool (TAT)
 * 1. Reads RGB from tapped photo pixel and converts to HSL (H in [0..360]).
 * 2. Calculates Gaussian overlap weights across the 8 Lightroom color channels.
 * 3. Dragging UP increases the targeted channel sliders; dragging DOWN decreases them.
 */
object TatEngine {

    private const val SIGMA = 0.065f // ~23 degrees standard deviation from stage6_color_science.frag

    fun circularHueDistance(h1: Float, h2: Float): Float {
        val d = abs(h1 - h2)
        return min(d, 1.0f - d)
    }

    private fun gaussianWeight(h: Float, center: Float, sigma: Float = SIGMA): Float {
        val dist = circularHueDistance(h, center)
        return exp(-(dist * dist) / (2.0f * sigma * sigma))
    }

    /**
     * Converts RGB [0.0..1.0] to HSL (h: [0.0..1.0], s: [0.0..1.0], l: [0.0..1.0])
     */
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal
        val l = (maxVal + minVal) / 2.0f

        if (delta < 0.00001f) {
            return floatArrayOf(0f, 0f, l)
        }

        val s = if (l > 0.5f) delta / (2.0f - maxVal - minVal) else delta / (maxVal + minVal)

        var h = when (maxVal) {
            r -> ((g - b) / delta + (if (g < b) 6.0f else 0.0f)) / 6.0f
            g -> ((b - r) / delta + 2.0f) / 6.0f
            else -> ((r - g) / delta + 4.0f) / 6.0f
        }
        h = (h % 1.0f + 1.0f) % 1.0f

        return floatArrayOf(h, s, l)
    }

    /**
     * Computes the 8-channel normalized weights for a given hue (h in [0.0..1.0])
     */
    fun computeChannelWeights(h: Float): TatChannelWeights {
        val rawRed = gaussianWeight(h, 0.0f) + gaussianWeight(h, 1.0f)
        val rawOrange = gaussianWeight(h, 0.0833f)  // 30 deg
        val rawYellow = gaussianWeight(h, 0.1667f)  // 60 deg
        val rawGreen = gaussianWeight(h, 0.3333f)   // 120 deg
        val rawAqua = gaussianWeight(h, 0.5000f)    // 180 deg
        val rawBlue = gaussianWeight(h, 0.6667f)    // 240 deg
        val rawPurple = gaussianWeight(h, 0.7778f)  // 280 deg
        val rawMagenta = gaussianWeight(h, 0.8889f) // 320 deg

        // In Lightroom TAT, only the targeted and adjacent color channels are adjusted;
        // negligible distant weights (< 0.08) are cut off to isolate the target color.
        val threshold = 0.08f
        val wRed = if (rawRed >= threshold) rawRed else 0.0f
        val wOrange = if (rawOrange >= threshold) rawOrange else 0.0f
        val wYellow = if (rawYellow >= threshold) rawYellow else 0.0f
        val wGreen = if (rawGreen >= threshold) rawGreen else 0.0f
        val wAqua = if (rawAqua >= threshold) rawAqua else 0.0f
        val wBlue = if (rawBlue >= threshold) rawBlue else 0.0f
        val wPurple = if (rawPurple >= threshold) rawPurple else 0.0f
        val wMagenta = if (rawMagenta >= threshold) rawMagenta else 0.0f

        val sum = wRed + wOrange + wYellow + wGreen + wAqua + wBlue + wPurple + wMagenta
        if (sum <= 0.0001f) {
            return TatChannelWeights()
        }

        return TatChannelWeights(
            red = wRed / sum,
            orange = wOrange / sum,
            yellow = wYellow / sum,
            green = wGreen / sum,
            aqua = wAqua / sum,
            blue = wBlue / sum,
            purple = wPurple / sum,
            magenta = wMagenta / sum
        )
    }

    /**
     * Applies drag delta (-dy) across the 8 channels proportionally to their weights.
     */
    fun applyTatDrag(
        currentHsl: HslAdjustments,
        weights: TatChannelWeights,
        delta: Float,
        mode: TatTargetMode
    ): HslAdjustments {
        fun adjustChannel(ch: HslChannel, weight: Float): HslChannel {
            if (weight < 0.001f) return ch
            val change = delta * weight
            return when (mode) {
                TatTargetMode.SATURATION -> ch.copy(saturation = (ch.saturation + change).coerceIn(-100f, 100f))
                TatTargetMode.HUE -> ch.copy(hue = (ch.hue + change).coerceIn(-100f, 100f))
                TatTargetMode.LUMINANCE -> ch.copy(luminance = (ch.luminance + change).coerceIn(-100f, 100f))
            }
        }

        return currentHsl.copy(
            red = adjustChannel(currentHsl.red, weights.red),
            orange = adjustChannel(currentHsl.orange, weights.orange),
            yellow = adjustChannel(currentHsl.yellow, weights.yellow),
            green = adjustChannel(currentHsl.green, weights.green),
            aqua = adjustChannel(currentHsl.aqua, weights.aqua),
            blue = adjustChannel(currentHsl.blue, weights.blue),
            purple = adjustChannel(currentHsl.purple, weights.purple),
            magenta = adjustChannel(currentHsl.magenta, weights.magenta)
        )
    }
}
