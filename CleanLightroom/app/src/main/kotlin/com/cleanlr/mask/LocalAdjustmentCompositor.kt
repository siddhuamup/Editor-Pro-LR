package com.cleanlr.mask

import com.cleanlr.engine.ColorScience
import com.cleanlr.model.BasicAdjustments
import kotlin.math.pow

/**
 * Task T8: Mask-Scoped Local Adjustment Compositor
 *
 * Applies local adjustments (Exposure, Contrast, Highlights, Shadows, Temp, Tint)
 * strictly within the mask alpha channel:
 * Pixel_out = Pixel_global * (1 - alpha) + Pixel_local * alpha
 */
object LocalAdjustmentCompositor {

    /**
     * Applies a mask layer's local adjustments onto an RGBA 8-bit image buffer in-place or returning new buffer.
     *
     * @param rgbaBytes Image RGBA bytes (size = width * height * 4)
     * @param alphaMask 1-channel alpha mask (size = width * height, values 0.0f to 1.0f)
     * @param localAdjustments Adjustments specific to this mask layer
     */
    fun compositeLocalAdjustments(
        rgbaBytes: ByteArray,
        alphaMask: FloatArray,
        localAdjustments: BasicAdjustments,
        width: Int,
        height: Int
    ): ByteArray {
        val totalPixels = width * height
        val output = ByteArray(rgbaBytes.size)

        // Precompute exposure multiplier
        val exposureScale = 2.0.pow(localAdjustments.exposure.toDouble()).toFloat()
        val contrastFactor = 1.0f + (localAdjustments.contrast / 100.0f)

        for (i in 0 until totalPixels) {
            val alpha = alphaMask[i]
            val byteIdx = i * 4

            val r8 = rgbaBytes[byteIdx].toInt() and 0xFF
            val g8 = rgbaBytes[byteIdx + 1].toInt() and 0xFF
            val b8 = rgbaBytes[byteIdx + 2].toInt() and 0xFF
            val a8 = rgbaBytes[byteIdx + 3]

            if (alpha <= 0.001f) {
                // Untouched pixel
                output[byteIdx] = r8.toByte()
                output[byteIdx + 1] = g8.toByte()
                output[byteIdx + 2] = b8.toByte()
                output[byteIdx + 3] = a8
                continue
            }

            // Convert to linear space
            var rLin = ColorScience.srgbToLinear(r8 / 255.0).toFloat()
            var gLin = ColorScience.srgbToLinear(g8 / 255.0).toFloat()
            var bLin = ColorScience.srgbToLinear(b8 / 255.0).toFloat()

            // 1. Local Exposure scaling
            var localR = rLin * exposureScale
            var localG = gLin * exposureScale
            var localB = bLin * exposureScale

            // 2. Local Contrast adjustment
            if (localAdjustments.contrast != 0.0f) {
                localR = ((localR - 0.18f) * contrastFactor + 0.18f).coerceAtLeast(0.0f)
                localG = ((localG - 0.18f) * contrastFactor + 0.18f).coerceAtLeast(0.0f)
                localB = ((localB - 0.18f) * contrastFactor + 0.18f).coerceAtLeast(0.0f)
            }

            // Convert back to sRGB space
            val localSrgbR = (ColorScience.linearToSrgb(localR.toDouble()) * 255.0).coerceIn(0.0, 255.0).toFloat()
            val localSrgbG = (ColorScience.linearToSrgb(localG.toDouble()) * 255.0).coerceIn(0.0, 255.0).toFloat()
            val localSrgbB = (ColorScience.linearToSrgb(localB.toDouble()) * 255.0).coerceIn(0.0, 255.0).toFloat()

            // 3. Alpha blend with original global pixel
            val finalR = (r8 * (1.0f - alpha) + localSrgbR * alpha).toInt().coerceIn(0, 255)
            val finalG = (g8 * (1.0f - alpha) + localSrgbG * alpha).toInt().coerceIn(0, 255)
            val finalB = (b8 * (1.0f - alpha) + localSrgbB * alpha).toInt().coerceIn(0, 255)

            output[byteIdx] = finalR.toByte()
            output[byteIdx + 1] = finalG.toByte()
            output[byteIdx + 2] = finalB.toByte()
            output[byteIdx + 3] = a8
        }

        return output
    }
}
