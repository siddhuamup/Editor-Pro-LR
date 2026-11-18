package com.cleanlr.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.cleanlr.model.BrushStrokePoint
import kotlin.math.*

/**
 * Authentic Adobe Lightroom Mobile Remove Tool Modes (`Remove`, `Heal`, `Clone`).
 */
enum class RemoveToolMode(val label: String) {
    REMOVE("Remove"),
    HEAL("Heal"),
    CLONE("Clone")
}

/**
 * Full on-device Heal (Texture-Matched Poisson Spot Heal) & Clone (Exact Pixel Clone Stamp) Engine.
 *
 * - **Heal**: Locates best-matching nearby donor patch (or uses user-positioned source pin), copies
 *   high-frequency texture, aligns tone & lighting seamlessly via [PoissonBlender], and composites
 *   with smooth radial `feather` (0..100) and `opacity` (0..100).
 * - **Clone**: Directly stamps pixels from the offset source region (`sourceNormX`, `sourceNormY`)
 *   into the painted target strokes with smooth `feather` falloff and exact `opacity` (0..100).
 */
object HealCloneEngine {

    /**
     * Applies Lightroom-style **Heal** (Donor Patch Search + Poisson Gradient Tone Match + Feather + Opacity).
     */
    fun applyHeal(
        originalBitmap: Bitmap,
        strokes: List<BrushStrokePoint>,
        feather: Float,
        opacity: Float,
        sourceNorm: Pair<Float, Float>? = null
    ): Bitmap {
        if (strokes.isEmpty()) return originalBitmap
        val w = originalBitmap.width
        val h = originalBitmap.height
        val alphaNorm = (opacity / 100f).coerceIn(0.01f, 1f)
        val featherNorm = (feather / 100f).coerceIn(0f, 1f)

        val (roiLeft, roiTop, roiRight, roiBottom) = computeStrokesPixelBounds(strokes, w, h, padPx = 28)
        val roiW = (roiRight - roiLeft).coerceAtLeast(8)
        val roiH = (roiBottom - roiTop).coerceAtLeast(8)
        val targetCenterX = (roiLeft + roiRight) / 2
        val targetCenterY = (roiTop + roiBottom) / 2

        val origPixels = IntArray(w * h)
        originalBitmap.getPixels(origPixels, 0, w, 0, 0, w, h)

        val featherMask = computeFeatheredStrokeMask(
            strokes = strokes,
            imageW = w,
            imageH = h,
            roiLeft = roiLeft,
            roiTop = roiTop,
            roiW = roiW,
            roiH = roiH,
            featherNorm = featherNorm
        )

        // Determine donor offset (dx, dy) from target -> source
        val (dx, dy) = if (sourceNorm != null) {
            val sx = (sourceNorm.first.coerceIn(0f, 1f) * (w - 1)).roundToInt()
            val sy = (sourceNorm.second.coerceIn(0f, 1f) * (h - 1)).roundToInt()
            Pair(sx - targetCenterX, sy - targetCenterY)
        } else {
            findBestDonorOffset(
                pixels = origPixels,
                imageW = w,
                imageH = h,
                roiLeft = roiLeft,
                roiTop = roiTop,
                roiW = roiW,
                roiH = roiH,
                mask = featherMask
            )
        }

        // Extract donor patch pixels and binary mask for Poisson gradient-domain tone/lighting blending
        val donorPatchPixels = IntArray(roiW * roiH)
        val binaryMaskPixels = IntArray(roiW * roiH)
        for (ry in 0 until roiH) {
            val ty = roiTop + ry
            val sy = (ty + dy).coerceIn(0, h - 1)
            for (rx in 0 until roiW) {
                val tx = roiLeft + rx
                val sx = (tx + dx).coerceIn(0, w - 1)
                val idx = ry * roiW + rx
                donorPatchPixels[idx] = origPixels[sy * w + sx]
                binaryMaskPixels[idx] = if (featherMask[idx] > 0.05f) Color.WHITE else Color.BLACK
            }
        }

        val donorBmp = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888).apply {
            setPixels(donorPatchPixels, 0, roiW, 0, 0, roiW, roiH)
        }
        val maskBmp = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888).apply {
            setPixels(binaryMaskPixels, 0, roiW, 0, 0, roiW, roiH)
        }

        val poissonHealed = try {
            PoissonBlender.blendPatchIntoOriginal(
                originalBitmap = originalBitmap,
                sourcePatch = donorBmp,
                maskBitmap = maskBmp,
                offsetX = roiLeft,
                offsetY = roiTop,
                iterations = 110,
                mixedGradients = true
            )
        } finally {
            donorBmp.recycle()
            maskBmp.recycle()
        }

        // Composite Poisson-healed ROI back onto original with exact Feather × Opacity
        val healedPixels = IntArray(w * h)
        poissonHealed.getPixels(healedPixels, 0, w, 0, 0, w, h)
        val outPixels = origPixels.copyOf()
        for (ry in 0 until roiH) {
            val ty = roiTop + ry
            val rowOff = ty * w
            val maskRow = ry * roiW
            for (rx in 0 until roiW) {
                val m = featherMask[maskRow + rx] * alphaNorm
                if (m <= 0.002f) continue
                val tx = roiLeft + rx
                val idx = rowOff + tx
                val op = origPixels[idx]
                val hp = healedPixels[idx]
                val r = (Color.red(op) * (1f - m) + Color.red(hp) * m).roundToInt().coerceIn(0, 255)
                val g = (Color.green(op) * (1f - m) + Color.green(hp) * m).roundToInt().coerceIn(0, 255)
                val b = (Color.blue(op) * (1f - m) + Color.blue(hp) * m).roundToInt().coerceIn(0, 255)
                outPixels[idx] = Color.argb(255, r, g, b)
            }
        }
        poissonHealed.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, w, 0, 0, w, h)
        }
    }

    /**
     * Applies Lightroom-style **Clone Stamp** (Exact Pixel Copy from Source Pin + Feather + Opacity).
     */
    fun applyClone(
        originalBitmap: Bitmap,
        strokes: List<BrushStrokePoint>,
        feather: Float,
        opacity: Float,
        sourceNorm: Pair<Float, Float>? = null
    ): Bitmap {
        if (strokes.isEmpty()) return originalBitmap
        val w = originalBitmap.width
        val h = originalBitmap.height
        val alphaNorm = (opacity / 100f).coerceIn(0.01f, 1f)
        val featherNorm = (feather / 100f).coerceIn(0f, 1f)

        val (roiLeft, roiTop, roiRight, roiBottom) = computeStrokesPixelBounds(strokes, w, h, padPx = 20)
        val roiW = (roiRight - roiLeft).coerceAtLeast(8)
        val roiH = (roiBottom - roiTop).coerceAtLeast(8)
        val targetCenterX = (roiLeft + roiRight) / 2
        val targetCenterY = (roiTop + roiBottom) / 2

        val origPixels = IntArray(w * h)
        originalBitmap.getPixels(origPixels, 0, w, 0, 0, w, h)

        val featherMask = computeFeatheredStrokeMask(
            strokes = strokes,
            imageW = w,
            imageH = h,
            roiLeft = roiLeft,
            roiTop = roiTop,
            roiW = roiW,
            roiH = roiH,
            featherNorm = featherNorm
        )

        val (dx, dy) = if (sourceNorm != null) {
            val sx = (sourceNorm.first.coerceIn(0f, 1f) * (w - 1)).roundToInt()
            val sy = (sourceNorm.second.coerceIn(0f, 1f) * (h - 1)).roundToInt()
            Pair(sx - targetCenterX, sy - targetCenterY)
        } else {
            // Default clean adjacent offset when user hasn't dragged source pin yet
            val stepX = (roiW + 16).coerceAtLeast(w / 10)
            val offsetX = if (targetCenterX + stepX < w - 8) stepX else -stepX
            Pair(offsetX, 0)
        }

        val cloned = applyClonePixelBuffer(
            origPixels = origPixels,
            imageW = w,
            imageH = h,
            roiLeft = roiLeft,
            roiTop = roiTop,
            roiW = roiW,
            roiH = roiH,
            featherMask = featherMask,
            dx = dx,
            dy = dy,
            opacityNorm = alphaNorm
        )
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(cloned, 0, w, 0, 0, w, h)
        }
    }

    // ─── Pure JVM-Testable Helpers ───────────────────────────────────────────

    fun computeStrokesPixelBounds(
        strokes: List<BrushStrokePoint>,
        imageW: Int,
        imageH: Int,
        padPx: Int = 24
    ): IntArray {
        val minDim = min(imageW, imageH).coerceAtLeast(1).toFloat()
        var left = imageW
        var top = imageH
        var right = 0
        var bottom = 0
        for (pt in strokes) {
            val cx = (pt.x.coerceIn(0f, 1f) * imageW).roundToInt()
            val cy = (pt.y.coerceIn(0f, 1f) * imageH).roundToInt()
            val r = ((pt.radius.coerceIn(2f, 120f) / 400f) * minDim).roundToInt().coerceAtLeast(6)
            left = min(left, cx - r - padPx)
            top = min(top, cy - r - padPx)
            right = max(right, cx + r + padPx)
            bottom = max(bottom, cy + r + padPx)
        }
        return intArrayOf(
            left.coerceIn(0, (imageW - 1).coerceAtLeast(0)),
            top.coerceIn(0, (imageH - 1).coerceAtLeast(0)),
            right.coerceIn(1, imageW),
            bottom.coerceIn(1, imageH)
        )
    }

    /**
     * Generates a smooth radial feathered mask (`0.0..1.0`) for all brush stroke points.
     * `featherNorm = 0f` -> crisp hard edge; `featherNorm = 1f` -> smooth cosine roll-off from center to rim.
     */
    fun computeFeatheredStrokeMask(
        strokes: List<BrushStrokePoint>,
        imageW: Int,
        imageH: Int,
        roiLeft: Int,
        roiTop: Int,
        roiW: Int,
        roiH: Int,
        featherNorm: Float
    ): FloatArray {
        val mask = FloatArray(roiW * roiH)
        val minDim = min(imageW, imageH).coerceAtLeast(1).toFloat()
        val innerRatio = (1f - featherNorm * 0.85f).coerceIn(0.10f, 0.98f)

        for (pt in strokes) {
            val cx = pt.x.coerceIn(0f, 1f) * imageW - roiLeft
            val cy = pt.y.coerceIn(0f, 1f) * imageH - roiTop
            val outerR = ((pt.radius.coerceIn(2f, 120f) / 400f) * minDim).coerceAtLeast(6f)
            val innerR = outerR * innerRatio
            val featherSpan = (outerR - innerR).coerceAtLeast(1f)

            val x0 = floor(cx - outerR).toInt().coerceIn(0, roiW - 1)
            val x1 = ceil(cx + outerR).toInt().coerceIn(0, roiW - 1)
            val y0 = floor(cy - outerR).toInt().coerceIn(0, roiH - 1)
            val y1 = ceil(cy + outerR).toInt().coerceIn(0, roiH - 1)

            for (y in y0..y1) {
                val dy = y - cy
                val row = y * roiW
                for (x in x0..x1) {
                    val dist = hypot(x - cx, dy)
                    if (dist >= outerR) continue
                    val v = if (dist <= innerR) {
                        1.0f
                    } else {
                        val t = ((dist - innerR) / featherSpan).coerceIn(0f, 1f)
                        (0.5f * (1f + cos(PI.toFloat() * t))).coerceIn(0f, 1f)
                    }
                    if (v > mask[row + x]) mask[row + x] = v
                }
            }
        }
        return mask
    }

    /**
     * Scans 16 surrounding candidate offsets outside the painted mask and returns the `(dx, dy)`
     * donor patch with the lowest boundary RGB SSD error (authentic Spot Heal auto-source search).
     */
    fun findBestDonorOffset(
        pixels: IntArray,
        imageW: Int,
        imageH: Int,
        roiLeft: Int,
        roiTop: Int,
        roiW: Int,
        roiH: Int,
        mask: FloatArray
    ): Pair<Int, Int> {
        // Collect boundary rim sample pixels (outside mask, within 10px of mask edge)
        val rimPts = ArrayList<Int>(128)
        val step = max(1, min(roiW, roiH) / 14)
        for (ry in 0 until roiH step step) {
            for (rx in 0 until roiW step step) {
                if (mask[ry * roiW + rx] < 0.08f) {
                    rimPts.add(ry * roiW + rx)
                }
            }
        }

        val baseDist = (max(roiW, roiH) * 0.85f).roundToInt().coerceAtLeast(24)
        var bestDx = baseDist
        var bestDy = 0
        var bestErr = Double.MAX_VALUE

        for (ring in intArrayOf(1, 2)) {
            val rad = baseDist * ring
            for (k in 0 until 8) {
                val angle = (k * PI / 4.0)
                val candDx = (cos(angle) * rad).roundToInt()
                val candDy = (sin(angle) * rad).roundToInt()

                // Candidate patch center must stay well inside image bounds
                val sLeft = roiLeft + candDx
                val sTop = roiTop + candDy
                if (sLeft < 0 || sTop < 0 || sLeft + roiW > imageW || sTop + roiH > imageH) continue

                var err = 0.0
                var count = 0
                for (rIdx in rimPts) {
                    val ry = rIdx / roiW
                    val rx = rIdx % roiW
                    val tp = pixels[(roiTop + ry) * imageW + (roiLeft + rx)]
                    val sp = pixels[(sTop + ry) * imageW + (sLeft + rx)]
                    val dr = ((tp shr 16) and 0xFF) - ((sp shr 16) and 0xFF)
                    val dg = ((tp shr 8) and 0xFF) - ((sp shr 8) and 0xFF)
                    val db = (tp and 0xFF) - (sp and 0xFF)
                    err += (dr * dr + dg * dg + db * db).toDouble()
                    count++
                }
                if (count > 0) {
                    val meanErr = err / count
                    if (meanErr < bestErr) {
                        bestErr = meanErr
                        bestDx = candDx
                        bestDy = candDy
                    }
                }
            }
        }
        return Pair(bestDx, bestDy)
    }

    /**
     * Pure JVM-testable pixel-level Clone Stamp compositing.
     */
    fun applyClonePixelBuffer(
        origPixels: IntArray,
        imageW: Int,
        imageH: Int,
        roiLeft: Int,
        roiTop: Int,
        roiW: Int,
        roiH: Int,
        featherMask: FloatArray,
        dx: Int,
        dy: Int,
        opacityNorm: Float
    ): IntArray {
        val out = origPixels.copyOf()
        for (ry in 0 until roiH) {
            val ty = roiTop + ry
            val sy = (ty + dy).coerceIn(0, imageH - 1)
            val dstRow = ty * imageW
            val srcRow = sy * imageW
            val maskRow = ry * roiW
            for (rx in 0 until roiW) {
                val m = (featherMask[maskRow + rx] * opacityNorm).coerceIn(0f, 1f)
                if (m <= 0.002f) continue
                val tx = roiLeft + rx
                val sx = (tx + dx).coerceIn(0, imageW - 1)
                val op = origPixels[dstRow + tx]
                val sp = origPixels[srcRow + sx]
                val r = (((op shr 16) and 0xFF) * (1f - m) + ((sp shr 16) and 0xFF) * m).roundToInt().coerceIn(0, 255)
                val g = (((op shr 8) and 0xFF) * (1f - m) + ((sp shr 8) and 0xFF) * m).roundToInt().coerceIn(0, 255)
                val b = ((op and 0xFF) * (1f - m) + (sp and 0xFF) * m).roundToInt().coerceIn(0, 255)
                out[dstRow + tx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }
}
