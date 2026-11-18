package com.cleanlr.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.cleanlr.model.BrushStrokePoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimal-Bandwidth ROI Crop + Mask Extractor for Zero-Bloat Cloud FLUX.1 Fill.
 *
 * Extracts ONLY the context-padded bounding box around the user's Remove brush strokes
 * (never uploads the entire 50MP photo!), snaps dimensions to FLUX.1 VAE 16px multiples,
 * and tracks exact pixel coordinates so PoissonBlender composites the returned patch
 * back into the full-resolution original photo with 100% untouched pixel fidelity.
 */
object RoiPatchExtractor {

    data class RoiBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    data class ExtractedRoiPatch(
        val roiBounds: RoiBounds,
        val croppedImage: Bitmap,
        val binaryMask: Bitmap,
        val uploadWidth: Int,
        val uploadHeight: Int
    )

    /**
     * Pure JVM-testable bounding box calculator from normalized brush points (0..1).
     * Adds generous context margin (default 1.0x mask span or min 128px) so FLUX.1 Fill
     * accurately infers lighting direction, shadow angle, and surrounding skin/fabric textures.
     */
    fun computeContextRoi(
        strokes: List<BrushStrokePoint>,
        imageWidth: Int,
        imageHeight: Int,
        minContextPx: Int = 128,
        contextMultiplier: Float = 0.85f
    ): RoiBounds? {
        if (strokes.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null

        val minDim = min(imageWidth, imageHeight).toFloat()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (pt in strokes) {
            val px = pt.x.coerceIn(0f, 1f) * imageWidth
            val py = pt.y.coerceIn(0f, 1f) * imageHeight
            // Brush radius is 1..100 mapped to fraction of image minDim
            val radPx = ((pt.radius.coerceIn(2f, 120f) / 400f) * minDim).coerceAtLeast(10f)
            minX = min(minX, px - radPx)
            minY = min(minY, py - radPx)
            maxX = max(maxX, px + radPx)
            maxY = max(maxY, py + radPx)
        }

        val maskW = (maxX - minX).coerceAtLeast(16f)
        val maskH = (maxY - minY).coerceAtLeast(16f)
        val padX = max(minContextPx.toFloat(), maskW * contextMultiplier)
        val padY = max(minContextPx.toFloat(), maskH * contextMultiplier)

        var left = floor(minX - padX).toInt().coerceAtLeast(0)
        var top = floor(minY - padY).toInt().coerceAtLeast(0)
        var right = ceil(maxX + padX).toInt().coerceAtMost(imageWidth)
        var bottom = ceil(maxY + padY).toInt().coerceAtMost(imageHeight)

        // Snap width & height to multiples of 16px (FLUX.1 VAE alignment) when image allows
        var w = (right - left).coerceAtLeast(16)
        var h = (bottom - top).coerceAtLeast(16)
        if (imageWidth >= 32) {
            val alignedW = ((w + 15) / 16) * 16
            if (alignedW <= imageWidth) {
                val extra = alignedW - w
                left = (left - extra / 2).coerceIn(0, imageWidth - alignedW)
                w = alignedW
            } else {
                left = 0
                w = (imageWidth / 16) * 16
            }
        }
        if (imageHeight >= 32) {
            val alignedH = ((h + 15) / 16) * 16
            if (alignedH <= imageHeight) {
                val extra = alignedH - h
                top = (top - extra / 2).coerceIn(0, imageHeight - alignedH)
                h = alignedH
            } else {
                top = 0
                h = (imageHeight / 16) * 16
            }
        }

        return RoiBounds(left = left, top = top, width = w.coerceAtLeast(16), height = h.coerceAtLeast(16))
    }

    /**
     * Extracts the cropped RGB ROI bitmap and crisp white-on-black binary mask bitmap
     * ready for upload to FLUX.1 Fill (capped at [maxUploadEdge] for 1-2s cloud inference).
     */
    fun extractRemoveRoi(
        originalBitmap: Bitmap,
        strokes: List<BrushStrokePoint>,
        maxUploadEdge: Int = 1024
    ): ExtractedRoiPatch? {
        val bounds = computeContextRoi(strokes, originalBitmap.width, originalBitmap.height) ?: return null

        val roiBmp = Bitmap.createBitmap(originalBitmap, bounds.left, bounds.top, bounds.width, bounds.height)
        val maskBmp = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(maskBmp)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val minDim = min(originalBitmap.width, originalBitmap.height).toFloat()
        for (pt in strokes) {
            val localX = pt.x * originalBitmap.width - bounds.left
            val localY = pt.y * originalBitmap.height - bounds.top
            // Slightly dilate (+4px) so boundary transitions cleanly into Poisson solver
            val radPx = ((pt.radius.coerceIn(2f, 120f) / 400f) * minDim).coerceAtLeast(10f) + 4f
            canvas.drawCircle(localX, localY, radPx, paint)
        }

        // Compute upload dimensions (preserve aspect, snap to 16px, cap at maxUploadEdge)
        val maxSide = max(bounds.width, bounds.height)
        val scale = if (maxSide > maxUploadEdge) maxUploadEdge.toFloat() / maxSide else 1f
        val upW = (((bounds.width * scale).roundToInt().coerceAtLeast(64) + 15) / 16) * 16
        val upH = (((bounds.height * scale).roundToInt().coerceAtLeast(64) + 15) / 16) * 16

        return ExtractedRoiPatch(
            roiBounds = bounds,
            croppedImage = roiBmp,
            binaryMask = maskBmp,
            uploadWidth = upW,
            uploadHeight = upH
        )
    }
}
