package com.cleanlr.engine

import android.graphics.Bitmap
import kotlin.math.max

data class HistogramData(
    val redBins: IntArray = IntArray(256),
    val greenBins: IntArray = IntArray(256),
    val blueBins: IntArray = IntArray(256),
    val lumaBins: IntArray = IntArray(256),
    val maxCount: Int = 1
)

object HistogramExtractor {

    /**
     * Extracts 256-bin RGB and Luminance histogram from a Bitmap.
     * Takes stride/downsampling into account for fast 60fps execution.
     */
    fun extract(bitmap: Bitmap, maxSamplePoints: Int = 25000): HistogramData {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        val red = IntArray(256)
        val green = IntArray(256)
        val blue = IntArray(256)
        val luma = IntArray(256)

        val step = max(1, totalPixels / maxSamplePoints)
        val pixels = IntArray(width)

        var maxVal = 1

        for (y in 0 until height step kotlin.math.sqrt(step.toDouble()).toInt().coerceAtLeast(1)) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (x in 0 until width step kotlin.math.sqrt(step.toDouble()).toInt().coerceAtLeast(1)) {
                val color = pixels[x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val yVal = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)

                red[r]++
                green[g]++
                blue[b]++
                luma[yVal]++

                maxVal = max(maxVal, max(red[r], max(green[g], blue[b])))
            }
        }

        return HistogramData(red, green, blue, luma, maxVal)
    }
}
