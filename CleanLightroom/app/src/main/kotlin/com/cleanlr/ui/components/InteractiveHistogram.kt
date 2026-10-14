package com.cleanlr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.HistogramData
import com.cleanlr.ui.theme.*
import kotlin.math.max

/**
 * Section 3.1: Interactive Histogram 5 Zones
 * - Blacks: Bins 0 - 25
 * - Shadows: Bins 26 - 75
 * - Exposure: Bins 76 - 180
 * - Highlights: Bins 181 - 230
 * - Whites: Bins 231 - 255
 */
enum class HistogramZone(val label: String, val startBin: Int, val endBin: Int) {
    BLACKS("Blacks", 0, 25),
    SHADOWS("Shadows", 26, 75),
    EXPOSURE("Exposure", 76, 180),
    HIGHLIGHTS("Highlights", 181, 230),
    WHITES("Whites", 231, 255),
    NONE("", -1, -1);

    companion object {
        fun fromRatio(ratio: Float): HistogramZone {
            val bin = (ratio.coerceIn(0f, 1f) * 255f).toInt()
            return fromBin(bin)
        }

        fun fromBin(bin: Int): HistogramZone {
            return when (bin.coerceIn(0, 255)) {
                in 0..25 -> BLACKS
                in 26..75 -> SHADOWS
                in 76..180 -> EXPOSURE
                in 181..230 -> HIGHLIGHTS
                else -> WHITES
            }
        }
    }
}

object ClippingIndicatorMath {
    /**
     * Determines whether shadow clipping occurs in the histogram data.
     * In Lightroom, shadows are clipped when pixels hit pure black (bin 0) in luma or any color channel.
     */
    fun isShadowClipped(data: HistogramData, isForcedActive: Boolean = false): Boolean {
        if (isForcedActive) return true
        return (data.lumaBins.isNotEmpty() && data.lumaBins[0] > 0) ||
                (data.redBins.isNotEmpty() && data.redBins[0] > 0) ||
                (data.greenBins.isNotEmpty() && data.greenBins[0] > 0) ||
                (data.blueBins.isNotEmpty() && data.blueBins[0] > 0)
    }

    /**
     * Determines whether highlight clipping occurs in the histogram data.
     * In Lightroom, highlights are clipped when pixels hit pure white (bin 255) in luma or any color channel.
     */
    fun isHighlightClipped(data: HistogramData, isForcedActive: Boolean = false): Boolean {
        if (isForcedActive) return true
        val lastLuma = data.lumaBins.size - 1
        val lastRed = data.redBins.size - 1
        val lastGreen = data.greenBins.size - 1
        val lastBlue = data.blueBins.size - 1
        return (lastLuma >= 0 && data.lumaBins[lastLuma] > 0) ||
                (lastRed >= 0 && data.redBins[lastRed] > 0) ||
                (lastGreen >= 0 && data.greenBins[lastGreen] > 0) ||
                (lastBlue >= 0 && data.blueBins[lastBlue] > 0)
    }

    // Exact normative Adobe Spectrum Dark Clipping Tokens (PDF Part A Section 1 & 2)
    val SHADOW_CLIPPING_COLOR = spectrum_clipping_shadow   // #0055FF
    val HIGHLIGHT_CLIPPING_COLOR = spectrum_clipping_high // #FF2222
    val INACTIVE_COLOR = spectrum_surface_elevated         // #2A2A2A
    val INACTIVE_BORDER_COLOR = spectrum_text_disabled     // #656565
}

@Composable
fun InteractiveHistogram(
    histogramData: HistogramData,
    onAdjustBlacks: (delta: Float) -> Unit,
    onAdjustShadows: (delta: Float) -> Unit,
    onAdjustExposure: (delta: Float) -> Unit,
    onAdjustHighlights: (delta: Float) -> Unit,
    onAdjustWhites: (delta: Float) -> Unit,
    onToggleShadowClipping: () -> Unit = {},
    onToggleHighlightClipping: () -> Unit = {},
    onJumpToBlacks: () -> Unit = {},
    onJumpToWhites: () -> Unit = {},
    isShadowClippingActive: Boolean = false,
    isHighlightClippingActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var activeZone by remember { mutableStateOf(HistogramZone.NONE) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpectrumElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val ratio = (offset.x / width).coerceIn(0f, 1f)
                            activeZone = HistogramZone.fromRatio(ratio)
                        },
                        onDragEnd = {
                            activeZone = HistogramZone.NONE
                        },
                        onDragCancel = {
                            activeZone = HistogramZone.NONE
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x
                            when (activeZone) {
                                HistogramZone.BLACKS -> onAdjustBlacks(dx * 0.35f)
                                HistogramZone.SHADOWS -> onAdjustShadows(dx * 0.35f)
                                HistogramZone.EXPOSURE -> onAdjustExposure(dx * 0.015f)
                                HistogramZone.HIGHLIGHTS -> onAdjustHighlights(dx * 0.35f)
                                HistogramZone.WHITES -> onAdjustWhites(dx * 0.35f)
                                HistogramZone.NONE -> {}
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Exact normative 5-zone boundaries (Bins 0..25, 26..75, 76..180, 181..230, 231..255)
                val x1 = w * (26f / 256f)   // Blacks / Shadows boundary
                val x2 = w * (76f / 256f)   // Shadows / Exposure boundary
                val x3 = w * (181f / 256f)  // Exposure / Highlights boundary
                val x4 = w * (231f / 256f)  // Highlights / Whites boundary

                // Subtle vertical guide dividers
                val guideColor = SpectrumBorder.copy(alpha = 0.6f)
                drawLine(guideColor, Offset(x1, 0f), Offset(x1, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(x2, 0f), Offset(x2, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(x3, 0f), Offset(x3, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(x4, 0f), Offset(x4, h), strokeWidth = 1f)

                // Active zone highlight overlay (light gray/blue translucent band)
                val (highlightLeft, highlightWidth) = when (activeZone) {
                    HistogramZone.BLACKS -> Pair(0f, x1)
                    HistogramZone.SHADOWS -> Pair(x1, x2 - x1)
                    HistogramZone.EXPOSURE -> Pair(x2, x3 - x2)
                    HistogramZone.HIGHLIGHTS -> Pair(x3, x4 - x3)
                    HistogramZone.WHITES -> Pair(x4, w - x4)
                    HistogramZone.NONE -> Pair(0f, 0f)
                }
                if (highlightWidth > 0f) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(highlightLeft, 0f),
                        size = Size(highlightWidth, h)
                    )
                }

                // Draw R, G, B, and Luminance waveforms
                val maxC = max(1, histogramData.maxCount).toFloat()

                fun buildCurvePath(bins: IntArray): Path {
                    val path = Path()
                    path.moveTo(0f, h)
                    for (i in 0..255) {
                        val px = (i / 255f) * w
                        val py = h - ((bins[i] / maxC) * (h * 0.92f))
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    return path
                }

                val pathR = buildCurvePath(histogramData.redBins)
                val pathG = buildCurvePath(histogramData.greenBins)
                val pathB = buildCurvePath(histogramData.blueBins)
                val pathL = buildCurvePath(histogramData.lumaBins)

                // Draw waveform stroke paths
                drawPath(pathR, HistogramRed.copy(alpha = 0.85f), style = Stroke(width = 1.5f))
                drawPath(pathG, HistogramGreen.copy(alpha = 0.85f), style = Stroke(width = 1.5f))
                drawPath(pathB, HistogramBlue.copy(alpha = 0.85f), style = Stroke(width = 1.5f))
                drawPath(pathL, HistogramLuma.copy(alpha = 0.60f), style = Stroke(width = 1.0f))

                // -------------------------------------------------------------
                // Left Shadow clipping triangle (Adobe Lightroom Spec)
                // -------------------------------------------------------------
                val shadowCrushed = ClippingIndicatorMath.isShadowClipped(histogramData, isShadowClippingActive)
                val leftPath = Path().apply {
                    val cx = 14.dp.toPx()
                    val cy = 13.dp.toPx()
                    val halfW = 5.dp.toPx()
                    val halfH = 4.5.dp.toPx()
                    moveTo(cx, cy - halfH)
                    lineTo(cx + halfW, cy + halfH)
                    lineTo(cx - halfW, cy + halfH)
                    close()
                }
                drawPath(
                    path = leftPath,
                    color = if (shadowCrushed) ClippingIndicatorMath.SHADOW_CLIPPING_COLOR else ClippingIndicatorMath.INACTIVE_COLOR
                )
                drawPath(
                    path = leftPath,
                    color = if (shadowCrushed) Color.White.copy(alpha = 0.8f) else ClippingIndicatorMath.INACTIVE_BORDER_COLOR,
                    style = Stroke(width = 1.dp.toPx())
                )

                // -------------------------------------------------------------
                // Right Highlight clipping triangle (Adobe Lightroom Spec)
                // -------------------------------------------------------------
                val highlightBlown = ClippingIndicatorMath.isHighlightClipped(histogramData, isHighlightClippingActive)
                val rightPath = Path().apply {
                    val cx = w - 14.dp.toPx()
                    val cy = 13.dp.toPx()
                    val halfW = 5.dp.toPx()
                    val halfH = 4.5.dp.toPx()
                    moveTo(cx, cy - halfH)
                    lineTo(cx + halfW, cy + halfH)
                    lineTo(cx - halfW, cy + halfH)
                    close()
                }
                drawPath(
                    path = rightPath,
                    color = if (highlightBlown) ClippingIndicatorMath.HIGHLIGHT_CLIPPING_COLOR else ClippingIndicatorMath.INACTIVE_COLOR
                )
                drawPath(
                    path = rightPath,
                    color = if (highlightBlown) Color.White.copy(alpha = 0.8f) else ClippingIndicatorMath.INACTIVE_BORDER_COLOR,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Clickable clipping indicator regions with standard touch targets (44dp x 44dp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.TopStart)
                    .clickable {
                        onToggleShadowClipping()
                        onJumpToBlacks()
                    }
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.TopEnd)
                    .clickable {
                        onToggleHighlightClipping()
                        onJumpToWhites()
                    }
            )

            // Dynamic hover/drag feedback overlay showing the active slider zone
            if (activeZone != HistogramZone.NONE) {
                Text(
                    text = activeZone.label,
                    color = SpectrumTextPrimary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
