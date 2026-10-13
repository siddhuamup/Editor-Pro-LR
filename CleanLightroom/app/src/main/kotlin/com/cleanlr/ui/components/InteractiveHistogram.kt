package com.cleanlr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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

enum class HistogramZone(val label: String) {
    BLACKS("Blacks"),
    SHADOWS("Shadows"),
    EXPOSURE("Exposure"),
    HIGHLIGHTS("Highlights"),
    WHITES("Whites"),
    NONE("")
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
                .height(64.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val ratio = (offset.x / width).coerceIn(0f, 1f)
                            activeZone = when {
                                ratio < 0.15f -> HistogramZone.BLACKS
                                ratio < 0.40f -> HistogramZone.SHADOWS
                                ratio < 0.60f -> HistogramZone.EXPOSURE
                                ratio < 0.85f -> HistogramZone.HIGHLIGHTS
                                else -> HistogramZone.WHITES
                            }
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
                                HistogramZone.BLACKS -> onAdjustBlacks(dx * 0.4f)
                                HistogramZone.SHADOWS -> onAdjustShadows(dx * 0.4f)
                                HistogramZone.EXPOSURE -> onAdjustExposure(dx * 0.015f)
                                HistogramZone.HIGHLIGHTS -> onAdjustHighlights(dx * 0.4f)
                                HistogramZone.WHITES -> onAdjustWhites(dx * 0.4f)
                                HistogramZone.NONE -> {}
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw zone dividing guides
                val x1 = w * 0.15f
                val x2 = w * 0.40f
                val x3 = w * 0.60f
                val x4 = w * 0.85f

                drawLine(SpectrumBorder, Offset(x1, 0f), Offset(x1, h), strokeWidth = 1f)
                drawLine(SpectrumBorder, Offset(x2, 0f), Offset(x2, h), strokeWidth = 1f)
                drawLine(SpectrumBorder, Offset(x3, 0f), Offset(x3, h), strokeWidth = 1f)
                drawLine(SpectrumBorder, Offset(x4, 0f), Offset(x4, h), strokeWidth = 1f)

                // Draw active zone highlight
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
                        color = SpectrumBlue.copy(alpha = 0.15f),
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
                        val py = h - ((bins[i] / maxC) * (h * 0.95f))
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    return path
                }

                val pathR = buildCurvePath(histogramData.redBins)
                val pathG = buildCurvePath(histogramData.greenBins)
                val pathB = buildCurvePath(histogramData.blueBins)
                val pathL = buildCurvePath(histogramData.lumaBins)

                // Draw stroke paths
                drawPath(pathR, HistogramRed.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
                drawPath(pathG, HistogramGreen.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
                drawPath(pathB, HistogramBlue.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
                drawPath(pathL, HistogramLuma.copy(alpha = 0.5f), style = Stroke(width = 1.0f))

                // Left Shadow clipping triangle
                val shadowCrushed = histogramData.lumaBins[0] > 0 || isShadowClippingActive
                drawCircle(
                    color = if (shadowCrushed) ClipBlackIndicator else SpectrumBorder,
                    radius = 4.dp.toPx(),
                    center = Offset(10.dp.toPx(), 10.dp.toPx())
                )

                // Right Highlight clipping triangle
                val highlightBlown = histogramData.lumaBins[255] > 0 || isHighlightClippingActive
                drawCircle(
                    color = if (highlightBlown) ClipWhiteIndicator else SpectrumBorder,
                    radius = 4.dp.toPx(),
                    center = Offset(w - 10.dp.toPx(), 10.dp.toPx())
                )
            }

            // Overlay label when user drags a zone
            if (activeZone != HistogramZone.NONE) {
                Text(
                    text = activeZone.label,
                    color = SpectrumTextPrimary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
