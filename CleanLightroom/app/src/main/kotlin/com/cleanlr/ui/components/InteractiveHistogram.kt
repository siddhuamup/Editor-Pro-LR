package com.cleanlr.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.HistogramData
import com.cleanlr.model.BasicAdjustments
import com.cleanlr.ui.editor.PhotoExifInfo
import com.cleanlr.ui.theme.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Section 3.1 & Tips 126–160: Interactive 5-Zone Histogram
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

/**
 * Tip 138: Histogram Display Modes
 */
enum class HistogramDisplayMode(val label: String) {
    RGB_OVERLAP("RGB"),
    LUMA_ONLY("LUMA"),
    RGB_PARADE("PARADE")
}

object ClippingIndicatorMath {
    /**
     * Tip 137: Computes per-channel clipping color (Lightroom Classic behavior)
     */
    fun getShadowClippingColor(data: HistogramData, isForcedActive: Boolean = false): Color? {
        if (isForcedActive) return Color(0xFF0055FF)
        val r = data.redBins.isNotEmpty() && data.redBins[0] > 0
        val g = data.greenBins.isNotEmpty() && data.greenBins[0] > 0
        val b = data.blueBins.isNotEmpty() && data.blueBins[0] > 0
        val l = data.lumaBins.isNotEmpty() && data.lumaBins[0] > 0

        if (!r && !g && !b && !l) return null
        if (r && g && b) return Color.White
        if (r && b) return Color(0xFFE040FB) // Magenta
        if (r && g) return Color(0xFFFFD600) // Yellow
        if (g && b) return Color(0xFF00E5FF) // Cyan
        if (b) return Color(0xFF0055FF)
        if (r) return Color(0xFFFF2222)
        if (g) return Color(0xFF22CC22)
        return Color(0xFF0055FF)
    }

    fun getHighlightClippingColor(data: HistogramData, isForcedActive: Boolean = false): Color? {
        if (isForcedActive) return Color(0xFFFF2222)
        val lastIdx = 255
        val r = data.redBins.size > lastIdx && data.redBins[lastIdx] > 0
        val g = data.greenBins.size > lastIdx && data.greenBins[lastIdx] > 0
        val b = data.blueBins.size > lastIdx && data.blueBins[lastIdx] > 0
        val l = data.lumaBins.size > lastIdx && data.lumaBins[lastIdx] > 0

        if (!r && !g && !b && !l) return null
        if (r && g && b) return Color.White
        if (r && b) return Color(0xFFE040FB)
        if (r && g) return Color(0xFFFFD600)
        if (g && b) return Color(0xFF00E5FF)
        if (r) return Color(0xFFFF2222)
        if (g) return Color(0xFF22CC22)
        if (b) return Color(0xFF0055FF)
        return Color(0xFFFF2222)
    }

    fun getShadowClippedPercent(data: HistogramData): Float {
        val total = 25000f // sample point baseline
        val count = if (data.lumaBins.isNotEmpty()) data.lumaBins[0] else 0
        return (count / total * 100f).coerceIn(0f, 100f)
    }

    fun getHighlightClippedPercent(data: HistogramData): Float {
        val total = 25000f
        val count = if (data.lumaBins.size > 255) data.lumaBins[255] else 0
        return (count / total * 100f).coerceIn(0f, 100f)
    }

    val SHADOW_CLIPPING_COLOR = Color(0xFF0055FF)
    val HIGHLIGHT_CLIPPING_COLOR = Color(0xFFFF2222)
    val INACTIVE_COLOR = Color(0xFF2A2A2A)
    val INACTIVE_BORDER_COLOR = Color(0xFF656565)

    fun isShadowClipped(data: HistogramData, isForcedActive: Boolean = false): Boolean {
        if (isForcedActive) return true
        val r = data.redBins.isNotEmpty() && data.redBins[0] > 0
        val g = data.greenBins.isNotEmpty() && data.greenBins[0] > 0
        val b = data.blueBins.isNotEmpty() && data.blueBins[0] > 0
        val l = data.lumaBins.isNotEmpty() && data.lumaBins[0] > 0
        return r || g || b || l
    }

    fun isHighlightClipped(data: HistogramData, isForcedActive: Boolean = false): Boolean {
        if (isForcedActive) return true
        val lastIdx = 255
        val r = data.redBins.size > lastIdx && data.redBins[lastIdx] > 0
        val g = data.greenBins.size > lastIdx && data.greenBins[lastIdx] > 0
        val b = data.blueBins.size > lastIdx && data.blueBins[lastIdx] > 0
        val l = data.lumaBins.size > lastIdx && data.lumaBins[lastIdx] > 0
        return r || g || b || l
    }
}

/**
 * Interactive 5-Zone Histogram Component
 * Tips 126–160 Complete Implementation
 */
@Composable
fun InteractiveHistogram(
    histogramData: HistogramData,
    ghostHistogramData: HistogramData? = null,
    basicAdjustments: BasicAdjustments = BasicAdjustments(),
    exifInfo: PhotoExifInfo? = null,
    activeSliderZone: HistogramZone? = null,
    onAdjustBlacks: (delta: Float) -> Unit,
    onAdjustShadows: (delta: Float) -> Unit,
    onAdjustExposure: (delta: Float) -> Unit,
    onAdjustHighlights: (delta: Float) -> Unit,
    onAdjustWhites: (delta: Float) -> Unit,
    onResetZone: (HistogramZone) -> Unit = {},
    onJumpToZone: (HistogramZone) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: (zoneName: String) -> Unit = {},
    onToggleShadowClipping: () -> Unit = {},
    onToggleHighlightClipping: () -> Unit = {},
    isShadowClippingActive: Boolean = false,
    isHighlightClippingActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var activeZone by remember { mutableStateOf(HistogramZone.NONE) }
    var touchX by remember { mutableStateOf(0f) }

    // Tip 131: Collapsible Histogram Bar (24.dp compact ribbon vs 72.dp full height)
    var isCompact by remember { mutableStateOf(false) }
    val animatedHeight by animateDpAsState(
        targetValue = if (isCompact) 28.dp else 72.dp,
        animationSpec = tween(180),
        label = "histogramHeight"
    )

    // Tip 138: Display Modes (RGB Overlap -> Luma Only -> RGB Parade)
    var displayMode by remember { mutableStateOf(HistogramDisplayMode.RGB_OVERLAP) }

    // Tip 147: Logarithmic vs Linear Y-scale
    var useLogScale by remember { mutableStateOf(false) }

    // Tip 134: Soft Peak Normalization (EMA Smoothing)
    var smoothedMax by remember { mutableStateOf(1f) }

    // Tip 135: Exclude Bin 0 and Bin 255 from max count
    val computedMax = remember(histogramData) {
        var peak = 1
        for (i in 2..253) {
            if (i < histogramData.redBins.size) peak = max(peak, histogramData.redBins[i])
            if (i < histogramData.greenBins.size) peak = max(peak, histogramData.greenBins[i])
            if (i < histogramData.blueBins.size) peak = max(peak, histogramData.blueBins[i])
            if (i < histogramData.lumaBins.size) peak = max(peak, histogramData.lumaBins[i])
        }
        peak.toFloat()
    }

    LaunchedEffect(computedMax) {
        smoothedMax = smoothedMax * 0.8f + computedMax * 0.2f
    }

    // Tip 143: Haptic bump on clipping threshold
    val shadowColor = ClippingIndicatorMath.getShadowClippingColor(histogramData, isShadowClippingActive)
    val highlightColor = ClippingIndicatorMath.getHighlightClippingColor(histogramData, isHighlightClippingActive)

    LaunchedEffect(shadowColor != null, highlightColor != null) {
        if (shadowColor != null || highlightColor != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpectrumElevated)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // Tip 144: Integrated EXIF Strip & Mode Selector Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // EXIF summary strip
            val exifText = remember(exifInfo) {
                if (exifInfo != null && (exifInfo.iso.isNotEmpty() || exifInfo.shutterSpeed.isNotEmpty())) {
                    listOfNotNull(
                        exifInfo.iso.ifEmpty { null },
                        exifInfo.focalLength.ifEmpty { null },
                        exifInfo.aperture.ifEmpty { null },
                        exifInfo.shutterSpeed.ifEmpty { null }
                    ).joinToString("  •  ")
                } else {
                    "HISTOGRAM"
                }
            }
            Text(
                text = exifText,
                color = SpectrumTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tip 154: Color Cast Indicator Dot
                val castColor = remember(histogramData) {
                    val rAvg = histogramData.redBins.take(256).average().toFloat()
                    val gAvg = histogramData.greenBins.take(256).average().toFloat()
                    val bAvg = histogramData.blueBins.take(256).average().toFloat()
                    val maxCh = max(rAvg, max(gAvg, bAvg))
                    if (maxCh > 0.1f) {
                        Color(
                            red = (rAvg / maxCh).coerceIn(0f, 1f),
                            green = (gAvg / maxCh).coerceIn(0f, 1f),
                            blue = (bAvg / maxCh).coerceIn(0f, 1f)
                        )
                    } else Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(castColor)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Tip 147: Log / Linear Toggle Chip
                Surface(
                    color = if (useLogScale) SpectrumBlue else Color(0xFF333333),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .clickable { useLogScale = !useLogScale }
                        .padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = if (useLogScale) "LOG" else "LIN",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Tip 138: Mode Cycle Tag (RGB -> LUMA -> PARADE)
                Surface(
                    color = Color(0xFF333333),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .clickable {
                            displayMode = when (displayMode) {
                                HistogramDisplayMode.RGB_OVERLAP -> HistogramDisplayMode.LUMA_ONLY
                                HistogramDisplayMode.LUMA_ONLY -> HistogramDisplayMode.RGB_PARADE
                                HistogramDisplayMode.RGB_PARADE -> HistogramDisplayMode.RGB_OVERLAP
                            }
                        }
                        .padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = displayMode.label,
                        color = SpectrumTextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Tip 131: Expand/Collapse button
                Icon(
                    imageVector = if (isCompact) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = "Toggle Histogram Size",
                    tint = SpectrumIconNormal,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { isCompact = !isCompact }
                )
            }
        }

        // Main Waveform Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .pointerInput(isCompact) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = size.width.toFloat()
                            val ratio = (offset.x / width).coerceIn(0f, 1f)
                            val zone = HistogramZone.fromRatio(ratio)
                            // Tip 140: Double-tap zone to reset that tone parameter
                            onResetZone(zone)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        onTap = { offset ->
                            val width = size.width.toFloat()
                            val ratio = (offset.x / width).coerceIn(0f, 1f)
                            val zone = HistogramZone.fromRatio(ratio)
                            // Tip 145: Auto-scroll to slider on zone tap
                            onJumpToZone(zone)
                        }
                    )
                }
                .pointerInput(isCompact) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val ratio = (offset.x / width).coerceIn(0f, 1f)
                            activeZone = HistogramZone.fromRatio(ratio)
                            touchX = offset.x
                            // Tip 127: Fix Histogram Undo History on touch down
                            onDragStart()
                        },
                        onDragEnd = {
                            val finishedZone = activeZone
                            activeZone = HistogramZone.NONE
                            // Tip 127: Commit state on touch up
                            onDragEnd(finishedZone.label)
                        },
                        onDragCancel = {
                            activeZone = HistogramZone.NONE
                            onDragEnd("Cancel")
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            touchX = change.position.x
                            val width = size.width.toFloat()
                            val dx = dragAmount.x
                            // Tips 149 & 150: Dynamic sensitivity normalized by viewport width
                            when (activeZone) {
                                HistogramZone.BLACKS -> onAdjustBlacks((dx / width) * 160.0f)
                                HistogramZone.SHADOWS -> onAdjustShadows((dx / width) * 160.0f)
                                HistogramZone.EXPOSURE -> onAdjustExposure((dx / width) * 4.0f)
                                HistogramZone.HIGHLIGHTS -> onAdjustHighlights((dx / width) * 160.0f)
                                HistogramZone.WHITES -> onAdjustWhites((dx / width) * 160.0f)
                                HistogramZone.NONE -> {}
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Normative 5-zone boundaries
                val x1 = w * (26f / 256f)   // Blacks / Shadows
                val x2 = w * (76f / 256f)   // Shadows / Exposure
                val x3 = w * (181f / 256f)  // Exposure / Highlights
                val x4 = w * (231f / 256f)  // Highlights / Whites

                // Tip 157: High-Contrast Grid Opacity (alpha 0.70f while dragging, 0.25f idle)
                val gridAlpha = if (activeZone != HistogramZone.NONE) 0.70f else 0.25f
                val guideColor = SpectrumBorder.copy(alpha = gridAlpha)
                drawLine(guideColor, Offset(x1, 0f), Offset(x1, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(x2, 0f), Offset(x2, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(x3, 0f), Offset(x3, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(x4, 0f), Offset(x4, h), strokeWidth = 1f)

                // Tip 148: Skin Tone IRE Guide Line (55% to 70% range = bins 140..178)
                val skinStart = w * (140f / 256f)
                val skinWidth = w * (38f / 256f)
                drawRect(
                    color = Color(0xFFFF9800).copy(alpha = 0.05f),
                    topLeft = Offset(skinStart, 0f),
                    size = Size(skinWidth, h)
                )

                // Tip 141: Mid-Gray 18% (Zone V) reference tick at sRGB 118
                val midGrayX = w * (118f / 256f)
                val tickPath = Path().apply {
                    moveTo(midGrayX, h)
                    lineTo(midGrayX - 3.dp.toPx(), h - 5.dp.toPx())
                    lineTo(midGrayX + 3.dp.toPx(), h - 5.dp.toPx())
                    close()
                }
                drawPath(tickPath, color = Color(0xFF888888).copy(alpha = 0.6f))

                // Tip 133: Bi-Directional Highlight from slider touch or active dragging
                val highlightedZone = if (activeZone != HistogramZone.NONE) activeZone else activeSliderZone
                val (hlLeft, hlWidth) = when (highlightedZone) {
                    HistogramZone.BLACKS -> Pair(0f, x1)
                    HistogramZone.SHADOWS -> Pair(x1, x2 - x1)
                    HistogramZone.EXPOSURE -> Pair(x2, x3 - x2)
                    HistogramZone.HIGHLIGHTS -> Pair(x3, x4 - x3)
                    HistogramZone.WHITES -> Pair(x4, w - x4)
                    else -> Pair(0f, 0f)
                }
                if (hlWidth > 0f) {
                    drawRect(
                        color = SpectrumBlue.copy(alpha = 0.12f),
                        topLeft = Offset(hlLeft, 0f),
                        size = Size(hlWidth, h)
                    )
                    // Tip 139: Zone boundary active indicator lines
                    drawLine(SpectrumBlue, Offset(hlLeft, 0f), Offset(hlLeft, h), strokeWidth = 2.dp.toPx())
                    drawLine(SpectrumBlue, Offset(hlLeft + hlWidth, 0f), Offset(hlLeft + hlWidth, h), strokeWidth = 2.dp.toPx())
                }

                // Waveform Rendering Logic
                val maxC = max(1f, smoothedMax)

                fun normalizeVal(raw: Int): Float {
                    val ratio = (raw.toFloat() / maxC).coerceIn(0f, 1f)
                    return if (useLogScale) sqrt(ratio) else ratio
                }

                // Tip 130: Smooth Bezier Waveform Path
                fun buildSmoothPath(bins: IntArray, fill: Boolean = false): Path {
                    val path = Path()
                    if (bins.isEmpty()) return path
                    val sampleStep = 2 // downsample slightly for smooth bezier control points
                    val points = mutableListOf<Offset>()

                    for (i in 0..255 step sampleStep) {
                        val px = (i / 255f) * w
                        val norm = normalizeVal(bins[i])
                        val py = h - (norm * (h * 0.90f))
                        points.add(Offset(px, py))
                    }

                    if (points.isNotEmpty()) {
                        path.moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            val cy = (p0.y + p1.y) / 2f
                            path.quadraticTo(p0.x, p0.y, cx, cy)
                        }
                        path.lineTo(points.last().x, points.last().y)
                    }

                    if (fill) {
                        path.lineTo(w, h)
                        path.lineTo(0f, h)
                        path.close()
                    }
                    return path
                }

                // Tip 142: Before vs After Ghost Histogram
                if (ghostHistogramData != null) {
                    val ghostPath = buildSmoothPath(ghostHistogramData.lumaBins, fill = false)
                    drawPath(
                        path = ghostPath,
                        color = Color.White.copy(alpha = 0.25f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                        )
                    )
                }

                // Render based on display mode
                when (displayMode) {
                    HistogramDisplayMode.RGB_OVERLAP -> {
                        // Tips 128 & 129: Filled gradient + Additive Screen Blending
                        fun drawChannel(bins: IntArray, col: Color) {
                            val strokePath = buildSmoothPath(bins, fill = false)
                            val fillPath = buildSmoothPath(bins, fill = true)

                            // Gradient Fill under curve
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(col.copy(alpha = 0.35f), col.copy(alpha = 0.02f)),
                                    startY = 0f,
                                    endY = h
                                ),
                                blendMode = BlendMode.Screen
                            )
                            // Waveform Stroke
                            drawPath(
                                path = strokePath,
                                color = col.copy(alpha = 0.85f),
                                style = Stroke(width = 1.5.dp.toPx()),
                                blendMode = BlendMode.Screen
                            )
                        }

                        drawChannel(histogramData.redBins, HistogramRed)
                        drawChannel(histogramData.greenBins, HistogramGreen)
                        drawChannel(histogramData.blueBins, HistogramBlue)

                        // Luma subtle outline
                        val lumaStroke = buildSmoothPath(histogramData.lumaBins, fill = false)
                        drawPath(lumaStroke, color = Color.White.copy(alpha = 0.40f), style = Stroke(width = 1.dp.toPx()))
                    }
                    HistogramDisplayMode.LUMA_ONLY -> {
                        val fillPath = buildSmoothPath(histogramData.lumaBins, fill = true)
                        val strokePath = buildSmoothPath(histogramData.lumaBins, fill = false)
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.02f)),
                                startY = 0f,
                                endY = h
                            )
                        )
                        drawPath(strokePath, color = Color.White, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    HistogramDisplayMode.RGB_PARADE -> {
                        val subH = h / 3f
                        fun drawParadeChannel(bins: IntArray, col: Color, offsetIndex: Int) {
                            val startY = offsetIndex * subH
                            val path = Path()
                            path.moveTo(0f, startY + subH)
                            for (i in 0..255) {
                                val px = (i / 255f) * w
                                val norm = normalizeVal(bins[i])
                                val py = startY + subH - (norm * (subH * 0.90f))
                                path.lineTo(px, py)
                            }
                            drawPath(path, color = col, style = Stroke(width = 1.dp.toPx()))
                        }
                        drawParadeChannel(histogramData.redBins, HistogramRed, 0)
                        drawParadeChannel(histogramData.greenBins, HistogramGreen, 1)
                        drawParadeChannel(histogramData.blueBins, HistogramBlue, 2)
                    }
                }

                // -------------------------------------------------------------
                // Left Shadow Clipping Triangle (Tips 136, 137)
                // -------------------------------------------------------------
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
                    color = shadowColor ?: ClippingIndicatorMath.INACTIVE_COLOR
                )
                drawPath(
                    path = leftPath,
                    color = if (shadowColor != null) Color.White.copy(alpha = 0.9f) else ClippingIndicatorMath.INACTIVE_BORDER_COLOR,
                    style = Stroke(width = 1.dp.toPx())
                )

                // -------------------------------------------------------------
                // Right Highlight Clipping Triangle (Tips 136, 137)
                // -------------------------------------------------------------
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
                    color = highlightColor ?: ClippingIndicatorMath.INACTIVE_COLOR
                )
                drawPath(
                    path = rightPath,
                    color = if (highlightColor != null) Color.White.copy(alpha = 0.9f) else ClippingIndicatorMath.INACTIVE_BORDER_COLOR,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Tip 146 & 156: Borderless Ripple + Touch Absorption for Clipping Triangles
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.TopStart)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 18.dp)
                    ) {
                        onToggleShadowClipping()
                        onJumpToZone(HistogramZone.BLACKS)
                    }
            ) {
                // Tip 136: Shadow Clipping Percentage Readout
                if (shadowColor != null) {
                    val pct = ClippingIndicatorMath.getShadowClippedPercent(histogramData)
                    Text(
                        text = String.format(Locale.US, "%.1f%%", pct),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.TopEnd)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 18.dp)
                    ) {
                        onToggleHighlightClipping()
                        onJumpToZone(HistogramZone.WHITES)
                    }
            ) {
                // Tip 136: Highlight Clipping Percentage Readout
                if (highlightColor != null) {
                    val pct = ClippingIndicatorMath.getHighlightClippedPercent(histogramData)
                    Text(
                        text = String.format(Locale.US, "%.1f%%", pct),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 2.dp)
                    )
                }
            }

            // Tip 132 & 158: Live Zone Value Readout Pill positioned over touch coordinate
            if (activeZone != HistogramZone.NONE) {
                val valueReadout = when (activeZone) {
                    HistogramZone.BLACKS -> "Blacks: ${basicAdjustments.blacks.toInt()}"
                    HistogramZone.SHADOWS -> "Shadows: ${basicAdjustments.shadows.toInt()}"
                    HistogramZone.EXPOSURE -> String.format(Locale.US, "Exposure: %+.2f EV", basicAdjustments.exposure)
                    HistogramZone.HIGHLIGHTS -> "Highlights: ${basicAdjustments.highlights.toInt()}"
                    HistogramZone.WHITES -> "Whites: ${basicAdjustments.whites.toInt()}"
                    HistogramZone.NONE -> ""
                }
                Surface(
                    color = Color(0xE6141414),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumBlue),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = valueReadout,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
