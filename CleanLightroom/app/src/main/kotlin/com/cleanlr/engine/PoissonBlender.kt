package com.cleanlr.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Gradient-Domain Seamless Poisson Image Blending Engine (Pérez, Gangnet & Blake).
 *
 * Composites a high-resolution generated patch (from FLUX.1 Fill / BrushNet) back into
 * the original full-resolution photo while guaranteeing:
 *  - 100% bit-exact preservation of all untouched pixels outside the mask
 *  - Zero visible seam/halo along the mask boundary (Dirichlet boundary condition)
 *  - Automatic matching of illumination, color temperature, and shadows across the boundary
 *
 * Uses Red-Black Successive Over-Relaxation (SOR, omega = 1.78) for fast mobile convergence.
 */
object PoissonBlender {

    /**
     * Blends [sourcePatch] into a copy of [originalBitmap] inside the white pixels of [maskBitmap]
     * placed at ([offsetX], [offsetY]). Pixels outside the mask remain 100% identical to [originalBitmap].
     */
    fun blendPatchIntoOriginal(
        originalBitmap: Bitmap,
        sourcePatch: Bitmap,
        maskBitmap: Bitmap,
        offsetX: Int,
        offsetY: Int,
        iterations: Int = 120,
        mixedGradients: Boolean = false
    ): Bitmap {
        val patchW = sourcePatch.width
        val patchH = sourcePatch.height
        val scaledMask = if (maskBitmap.width != patchW || maskBitmap.height != patchH) {
            Bitmap.createScaledBitmap(maskBitmap, patchW, patchH, false)
        } else maskBitmap

        val roiW = patchW.coerceAtMost(originalBitmap.width - offsetX).coerceAtLeast(1)
        val roiH = patchH.coerceAtMost(originalBitmap.height - offsetY).coerceAtLeast(1)

        val dstPixels = IntArray(roiW * roiH)
        originalBitmap.getPixels(dstPixels, 0, roiW, offsetX, offsetY, roiW, roiH)

        val srcPixels = IntArray(roiW * roiH)
        sourcePatch.getPixels(srcPixels, 0, roiW, 0, 0, roiW, roiH)

        val maskPixels = IntArray(roiW * roiH)
        scaledMask.getPixels(maskPixels, 0, roiW, 0, 0, roiW, roiH)
        if (scaledMask !== maskBitmap) scaledMask.recycle()

        val n = roiW * roiH
        val dstR = FloatArray(n); val dstG = FloatArray(n); val dstB = FloatArray(n)
        val srcR = FloatArray(n); val srcG = FloatArray(n); val srcB = FloatArray(n)
        val mask = BooleanArray(n)

        var maskedCount = 0
        for (i in 0 until n) {
            val dp = dstPixels[i]
            dstR[i] = Color.red(dp).toFloat()
            dstG[i] = Color.green(dp).toFloat()
            dstB[i] = Color.blue(dp).toFloat()

            val sp = srcPixels[i]
            srcR[i] = Color.red(sp).toFloat()
            srcG[i] = Color.green(sp).toFloat()
            srcB[i] = Color.blue(sp).toFloat()

            // Mask pixel is active if luminance > 127
            val mp = maskPixels[i]
            val mLuma = (Color.red(mp) + Color.green(mp) + Color.blue(mp)) / 3
            val inside = mLuma > 127
            mask[i] = inside
            if (inside) maskedCount++
        }

        val outBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (maskedCount == 0) return outBitmap

        val outR = solveChannel(dstR, srcR, mask, roiW, roiH, iterations, mixedGradients)
        val outG = solveChannel(dstG, srcG, mask, roiW, roiH, iterations, mixedGradients)
        val outB = solveChannel(dstB, srcB, mask, roiW, roiH, iterations, mixedGradients)

        val blendedPixels = IntArray(n)
        for (i in 0 until n) {
            if (!mask[i]) {
                // Bit-exact original pixel preservation outside mask!
                blendedPixels[i] = dstPixels[i]
            } else {
                val r = outR[i].roundToInt().coerceIn(0, 255)
                val g = outG[i].roundToInt().coerceIn(0, 255)
                val b = outB[i].roundToInt().coerceIn(0, 255)
                val a = Color.alpha(dstPixels[i])
                blendedPixels[i] = Color.argb(a, r, g, b)
            }
        }

        outBitmap.setPixels(blendedPixels, 0, roiW, offsetX, offsetY, roiW, roiH)
        return outBitmap
    }

    /**
     * Pure JVM-testable single-channel discrete Poisson solver using Red-Black SOR.
     * Enforces exact Dirichlet boundary conditions (u = dst outside mask) while matching
     * the gradient field (Laplacian divergence) of [src] inside [mask].
     */
    fun solveChannel(
        dst: FloatArray,
        src: FloatArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        iterations: Int = 120,
        mixedGradients: Boolean = false
    ): FloatArray {
        val n = width * height
        val u = FloatArray(n)
        val divG = FloatArray(n)

        // Initialize u: exact dst outside mask, src inside mask (with mean offset alignment for fast convergence)
        var boundaryDiffSum = 0.0
        var boundaryCount = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val idx = row + x
                if (!mask[idx]) continue
                if (!mask[idx - 1] || !mask[idx + 1] || !mask[idx - width] || !mask[idx + width]) {
                    boundaryDiffSum += (dst[idx] - src[idx])
                    boundaryCount++
                }
            }
        }
        val initialShift = if (boundaryCount > 0) (boundaryDiffSum / boundaryCount).toFloat() else 0f

        // Precompute discrete divergence of guidance vector field inside mask
        val activeIndicesRed = IntArray(n)
        val activeIndicesBlack = IntArray(n)
        var redCount = 0
        var blackCount = 0

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (!mask[idx]) {
                    u[idx] = dst[idx]
                    continue
                }
                // Boundary pixels of the ROI image itself stay anchored to src shifted
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    u[idx] = (src[idx] + initialShift).coerceIn(0f, 255f)
                    continue
                }

                u[idx] = (src[idx] + initialShift).coerceIn(0f, 255f)

                val sC = src[idx]
                val dC = dst[idx]
                var div = 0f

                // 4 neighbors: left, right, up, down
                val neighbors = intArrayOf(idx - 1, idx + 1, idx - width, idx + width)
                for (nb in neighbors) {
                    val gradSrc = sC - src[nb]
                    val gradUse = if (mixedGradients) {
                        val gradDst = dC - dst[nb]
                        if (abs(gradDst) > abs(gradSrc)) gradDst else gradSrc
                    } else {
                        gradSrc
                    }
                    div += gradUse
                }
                divG[idx] = div

                if (((x + y) and 1) == 0) {
                    activeIndicesRed[redCount++] = idx
                } else {
                    activeIndicesBlack[blackCount++] = idx
                }
            }
        }

        // Red-Black SOR iteration (omega = 1.78 gives ~5x faster convergence than Jacobi)
        val omega = 1.78f
        val oneMinusOmega = 1.0f - omega

        for (iter in 0 until iterations) {
            for (i in 0 until redCount) {
                val idx = activeIndicesRed[i]
                val gs = 0.25f * (u[idx - 1] + u[idx + 1] + u[idx - width] + u[idx + width] + divG[idx])
                u[idx] = oneMinusOmega * u[idx] + omega * gs
            }
            for (i in 0 until blackCount) {
                val idx = activeIndicesBlack[i]
                val gs = 0.25f * (u[idx - 1] + u[idx + 1] + u[idx - width] + u[idx + width] + divG[idx])
                u[idx] = oneMinusOmega * u[idx] + omega * gs
            }
        }

        // Clamp final values strictly inside [0, 255] for masked pixels, keep untouched pixels bit-exact
        for (i in 0 until n) {
            if (mask[i]) {
                u[i] = u[i].coerceIn(0f, 255f)
            } else {
                u[i] = dst[i]
            }
        }
        return u
    }

    /**
     * Fast on-device harmonic/content-aware inpaint fallback (when Generative AI switch is OFF
     * or user wants instant offline local object removal with Poisson smoothing).
     */
    fun localContentAwareRemove(
        originalBitmap: Bitmap,
        maskBitmap: Bitmap,
        offsetX: Int,
        offsetY: Int,
        iterations: Int = 180
    ): Bitmap {
        val roiW = maskBitmap.width.coerceAtMost(originalBitmap.width - offsetX).coerceAtLeast(1)
        val roiH = maskBitmap.height.coerceAtMost(originalBitmap.height - offsetY).coerceAtLeast(1)

        val dstPixels = IntArray(roiW * roiH)
        originalBitmap.getPixels(dstPixels, 0, roiW, offsetX, offsetY, roiW, roiH)
        val maskPixels = IntArray(roiW * roiH)
        maskBitmap.getPixels(maskPixels, 0, roiW, 0, 0, roiW, roiH)

        val n = roiW * roiH
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        val zeros = FloatArray(n) // Zero guidance Laplacian => smooth membrane interpolation
        val mask = BooleanArray(n)
        var anyMasked = false
        for (i in 0 until n) {
            val dp = dstPixels[i]
            r[i] = Color.red(dp).toFloat()
            g[i] = Color.green(dp).toFloat()
            b[i] = Color.blue(dp).toFloat()
            val mp = maskPixels[i]
            val inside = ((Color.red(mp) + Color.green(mp) + Color.blue(mp)) / 3) > 127
            mask[i] = inside
            if (inside) anyMasked = true
        }
        val out = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (!anyMasked) return out

        val outR = solveChannel(r, zeros, mask, roiW, roiH, iterations, mixedGradients = false)
        val outG = solveChannel(g, zeros, mask, roiW, roiH, iterations, mixedGradients = false)
        val outB = solveChannel(b, zeros, mask, roiW, roiH, iterations, mixedGradients = false)

        val blended = IntArray(n)
        for (i in 0 until n) {
            blended[i] = if (!mask[i]) {
                dstPixels[i]
            } else {
                Color.argb(
                    Color.alpha(dstPixels[i]),
                    outR[i].roundToInt().coerceIn(0, 255),
                    outG[i].roundToInt().coerceIn(0, 255),
                    outB[i].roundToInt().coerceIn(0, 255)
                )
            }
        }
        out.setPixels(blended, 0, roiW, offsetX, offsetY, roiW, roiH)
        return out
    }
}
