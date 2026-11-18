package com.cleanlr.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.cleanlr.model.CropAdjustments
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Generative Expand Feature Contract & Canvas Preparation Engine.
 *
 * Ready-to-integrate seam for future AI Outpainting / Generative Fill backends
 * (e.g., on-device diffusion, Gemini Imagen / cloud outpaint API).
 * Right now it handles:
 *  - Exact expanded canvas & padding geometry calculation in pixels
 *  - Binary outpaint mask generation (white = area to synthesize, black = original photo)
 *  - Expansion ratio & target resolution metadata for live UI pills
 *  - Pluggable GenerativeExpandProvider interface
 */
object GenerativeExpandEngine {

    data class ExpandRegion(
        val originalWidth: Int,
        val originalHeight: Int,
        val padLeftPx: Int,
        val padTopPx: Int,
        val padRightPx: Int,
        val padBottomPx: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val expansionPercent: Int
    ) {
        val hasExpansion: Boolean
            get() = padLeftPx > 0 || padTopPx > 0 || padRightPx > 0 || padBottomPx > 0
    }

    enum class ExpandStatus {
        IDLE,
        READY_TO_GENERATE,
        GENERATING,
        APPLIED,
        BACKEND_NOT_CONFIGURED
    }

    interface GenerativeExpandProvider {
        val providerName: String
        val isConfigured: Boolean
        suspend fun generateExpansion(
            sourceBitmap: Bitmap,
            outpaintMask: Bitmap,
            region: ExpandRegion,
            prompt: String? = null
        ): Result<Bitmap>
    }

    /**
     * Default local stub provider until the AI outpainting backend is attached.
     * Prepares the padded source canvas cleanly and returns a structured result.
     */
    class UnconfiguredExpandProvider : GenerativeExpandProvider {
        override val providerName: String = "Generative Expand (Pending AI Model Setup)"
        override val isConfigured: Boolean = false

        override suspend fun generateExpansion(
            sourceBitmap: Bitmap,
            outpaintMask: Bitmap,
            region: ExpandRegion,
            prompt: String?
        ): Result<Bitmap> {
            return Result.failure(
                IllegalStateException("Generative Expand backend is prepared and awaiting AI model configuration.")
            )
        }
    }

    @Volatile
    var activeProvider: GenerativeExpandProvider = UnconfiguredExpandProvider()

    /**
     * Computes exact pixel padding and output dimensions from normalized CropAdjustments
     * when Expand mode extends left/top < 0f or right/bottom > 1f.
     */
    fun computeExpandRegion(
        crop: CropAdjustments,
        imageWidth: Int,
        imageHeight: Int
    ): ExpandRegion {
        val (effW, effH) = CropGeometryEngine.effectiveImageDimensions(
            imageWidth,
            imageHeight,
            crop.rotationDegrees
        )
        val wInt = effW.roundToInt().coerceAtLeast(1)
        val hInt = effH.roundToInt().coerceAtLeast(1)

        val padLeft = if (crop.left < 0f) (-crop.left * effW).roundToInt().coerceAtLeast(0) else 0
        val padTop = if (crop.top < 0f) (-crop.top * effH).roundToInt().coerceAtLeast(0) else 0
        val padRight = if (crop.right > 1f) ((crop.right - 1f) * effW).roundToInt().coerceAtLeast(0) else 0
        val padBottom = if (crop.bottom > 1f) ((crop.bottom - 1f) * effH).roundToInt().coerceAtLeast(0) else 0

        val outW = ((crop.right - crop.left) * effW).roundToInt().coerceAtLeast(1)
        val outH = ((crop.bottom - crop.top) * effH).roundToInt().coerceAtLeast(1)

        val origArea = (wInt.toLong() * hInt.toLong()).coerceAtLeast(1L)
        val outArea = (outW.toLong() * outH.toLong()).coerceAtLeast(1L)
        val pct = (((outArea - origArea).toFloat() / origArea.toFloat()) * 100f).roundToInt().coerceAtLeast(0)

        return ExpandRegion(
            originalWidth = wInt,
            originalHeight = hInt,
            padLeftPx = padLeft,
            padTopPx = padTop,
            padRightPx = padRight,
            padBottomPx = padBottom,
            outputWidth = outW,
            outputHeight = outH,
            expansionPercent = pct
        )
    }

    /**
     * Builds the padded RGB canvas and matching outpaint binary mask (white = generate, black = preserve)
     * ready to pass straight into any AI diffusion / outpainting API.
     */
    fun buildOutpaintInputs(sourceBitmap: Bitmap, region: ExpandRegion): Pair<Bitmap, Bitmap> {
        val fullW = region.originalWidth + region.padLeftPx + region.padRightPx
        val fullH = region.originalHeight + region.padTopPx + region.padBottomPx

        val paddedCanvas = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888)
        Canvas(paddedCanvas).apply {
            drawColor(Color.TRANSPARENT)
            val dst = Rect(
                region.padLeftPx,
                region.padTopPx,
                region.padLeftPx + region.originalWidth,
                region.padTopPx + region.originalHeight
            )
            drawBitmap(sourceBitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val mask = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888)
        Canvas(mask).apply {
            drawColor(Color.WHITE) // White = regions to generate
            val keepPaint = Paint().apply { color = Color.BLACK }
            drawRect(
                region.padLeftPx.toFloat(),
                region.padTopPx.toFloat(),
                (region.padLeftPx + region.originalWidth).toFloat(),
                (region.padTopPx + region.originalHeight).toFloat(),
                keepPaint
            )
        }

        return Pair(paddedCanvas, mask)
    }
}
