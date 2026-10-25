package com.cleanlr.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.HistogramData
import com.cleanlr.engine.SplineInterpolator
import com.cleanlr.engine.stage.Film3dLut
import com.cleanlr.model.CurvePoint
import com.cleanlr.model.ToneCurves
import com.cleanlr.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Tip 161: 4-Channel Tone Curve Selector
 */
enum class CurveChannel(val label: String, val color: Color) {
    RGB("RGB", Color.White),
    RED("Red", Color(0xFFE53935)),
    GREEN("Green", Color(0xFF43A047)),
    BLUE("Blue", Color(0xFF1E88E5))
}

/**
 * Tip 167: Sub-Mode Switcher (Point Curve vs Parametric Curve)
 */
enum class CurveSubMode(val label: String) {
    POINT("Point Curve"),
    PARAMETRIC("Parametric")
}

/**
 * ToneCurveWidget — Complete Lightroom Pro Tone Curve Widget
 * Implements Tips 161–205
 */
@Composable
fun ToneCurveWidget(
    curves: ToneCurves,
    onCurveChanged: (ToneCurves) -> Unit,
    histogramData: HistogramData = HistogramData(),
    activeFilm3dLut: Film3dLut = Film3dLut.NONE,
    onFilm3dLutChanged: (Film3dLut) -> Unit = {},
    film3dLutIntensity: Float = 1.0f,
    onFilm3dLutIntensityChanged: (Float) -> Unit = {},
    onTouchStart: () -> Unit = {},
    onTouchEnd: (actionName: String) -> Unit = {},
    isTatActive: Boolean = false,
    onToggleTat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current

    var selectedChannel by remember { mutableStateOf(CurveChannel.RGB) }
    var selectedSubMode by remember { mutableStateOf(CurveSubMode.POINT) }
    var selectedPointIndex by remember { mutableStateOf(-1) }
    var isDraggingPoint by remember { mutableStateOf(false) }

    // Tip 183: Curve bypass on hold
    var isBypassed by remember { mutableStateOf(false) }

    // Tip 171: HUD readout
    var hudText by remember { mutableStateOf<String?>(null) }
    var deltaPercentage by remember { mutableStateOf<String?>(null) }

    // Active channel points helper
    val currentPoints = when (selectedChannel) {
        CurveChannel.RGB -> curves.composite
        CurveChannel.RED -> curves.red
        CurveChannel.GREEN -> curves.green
        CurveChannel.BLUE -> curves.blue
    }

    fun updateCurrentPoints(updatedPoints: List<CurvePoint>, actionName: String? = null) {
        val updatedCurves = when (selectedChannel) {
            CurveChannel.RGB -> curves.copy(composite = updatedPoints, pointCurve = updatedPoints)
            CurveChannel.RED -> curves.copy(red = updatedPoints)
            CurveChannel.GREEN -> curves.copy(green = updatedPoints)
            CurveChannel.BLUE -> curves.copy(blue = updatedPoints)
        }
        onCurveChanged(updatedCurves)
        if (actionName != null) {
            onTouchEnd(actionName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tip 167: Sub-Mode Switcher Tab Row (Point Curve vs Parametric Curve)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222222))
                    .padding(2.dp)
            ) {
                CurveSubMode.entries.forEach { mode ->
                    val isSelected = selectedSubMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) SpectrumElevated else Color.Transparent)
                            .clickable {
                                selectedSubMode = mode
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.White else SpectrumTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Toolbar: TAT Icon & Bypass Eye Button (Tips 170 & 183)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tip 170: Curve TAT button
                IconButton(
                    onClick = {
                        onToggleTat()
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Adjust,
                        contentDescription = "Curve Targeted Adjustment Tool",
                        tint = if (isTatActive) SpectrumBlue else SpectrumTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Tip 183: Bypass Eye Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isBypassed = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    tryAwaitRelease()
                                    isBypassed = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBypassed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Bypass Curve Preview (Hold)",
                        tint = if (isBypassed) SpectrumBlue else SpectrumTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Tip 161: 4-Channel Curve Selector Buttons (RGB / Red / Green / Blue)
        if (selectedSubMode == CurveSubMode.POINT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurveChannel.entries.forEach { ch ->
                    val isSelected = selectedChannel == ch
                    val isChannelModified = when (ch) {
                        CurveChannel.RGB -> curves.composite.size > 2 || (curves.composite.size == 2 && (curves.composite[0].y != 0f || curves.composite[1].y != 255f))
                        CurveChannel.RED -> curves.red.size > 2 || (curves.red.size == 2 && (curves.red[0].y != 0f || curves.red[1].y != 255f))
                        CurveChannel.GREEN -> curves.green.size > 2 || (curves.green.size == 2 && (curves.green[0].y != 0f || curves.green[1].y != 255f))
                        CurveChannel.BLUE -> curves.blue.size > 2 || (curves.blue.size == 2 && (curves.blue[0].y != 0f || curves.blue[1].y != 255f))
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ch.color.copy(alpha = 0.25f) else Color(0xFF1E1E1E))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ch.color else SpectrumBorder,
                                shape = CircleShape
                            )
                            .clickable {
                                selectedChannel = ch
                                selectedPointIndex = -1
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(ch.color)
                        )
                        // Tip 186: Modified channel indicator dot
                        if (isChannelModified && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .clip(CircleShape)
                                    .background(SpectrumBlue)
                            )
                        }
                    }
                }
            }
        }

        // Tip 172: Exact Square 1:1 Aspect Ratio Curve Box (220.dp x 220.dp)
        // Tip 189: Prevents parent vertical scroll interception
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpectrumElevated)
                .border(1.dp, SpectrumInnerBorder, RoundedCornerShape(8.dp))
                .pointerInput(currentPoints, selectedSubMode) {
                    if (selectedSubMode != CurveSubMode.POINT) return@pointerInput

                    detectTapGestures(
                        onTap = { offset ->
                            val insetPx = 14.dp.toPx() // Tip 181: 14dp inner padding
                            val effectiveW = size.width - insetPx * 2f
                            val effectiveH = size.height - insetPx * 2f

                            val normX = ((offset.x - insetPx) / effectiveW * 255f).coerceIn(0f, 255f)
                            val normY = ((1f - (offset.y - insetPx) / effectiveH) * 255f).coerceIn(0f, 255f)

                            // Check proximity to existing points
                            val existingIdx = currentPoints.indexOfFirst { pt ->
                                val ptPxX = insetPx + (pt.x / 255f) * effectiveW
                                val ptPxY = insetPx + (1f - pt.y / 255f) * effectiveH
                                hypot((offset.x - ptPxX).toDouble(), (offset.y - ptPxY).toDouble()) < 36.0
                            }

                            if (existingIdx != -1) {
                                selectedPointIndex = existingIdx
                                hudText = "In: ${currentPoints[existingIdx].x.toInt()} → Out: ${currentPoints[existingIdx].y.toInt()}"
                            } else {
                                // Tip 195: Max 16 points guard
                                if (currentPoints.size < 16) {
                                    // Tip 196: Minimum X-spacing guard (min 4 units)
                                    val tooClose = currentPoints.any { abs(it.x - normX) < 4.0f }
                                    if (!tooClose) {
                                        onTouchStart()
                                        val newPoints = (currentPoints + CurvePoint(normX, normY)).sortedBy { it.x }
                                        updateCurrentPoints(newPoints, "Add Curve Point")
                                        selectedPointIndex = newPoints.indexOfFirst { it.x == normX }
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        hudText = "In: ${normX.toInt()} → Out: ${normY.toInt()}"
                                    }
                                }
                            }
                        },
                        onDoubleTap = { offset ->
                            val insetPx = 14.dp.toPx()
                            val effectiveW = size.width - insetPx * 2f
                            val effectiveH = size.height - insetPx * 2f

                            val deleteIdx = currentPoints.indexOfFirst { pt ->
                                val ptPxX = insetPx + (pt.x / 255f) * effectiveW
                                val ptPxY = insetPx + (1f - pt.y / 255f) * effectiveH
                                hypot((offset.x - ptPxX).toDouble(), (offset.y - ptPxY).toDouble()) < 36.0
                            }
                            // Do not delete endpoints 0 and lastIndex
                            if (deleteIdx > 0 && deleteIdx < currentPoints.lastIndex) {
                                onTouchStart()
                                val updated = currentPoints.toMutableList().apply { removeAt(deleteIdx) }
                                updateCurrentPoints(updated, "Delete Curve Point")
                                selectedPointIndex = -1
                                hudText = null
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    )
                }
                .pointerInput(currentPoints, selectedSubMode) {
                    if (selectedSubMode != CurveSubMode.POINT) return@pointerInput

                    detectDragGestures(
                        onDragStart = { offset ->
                            val insetPx = 14.dp.toPx()
                            val effectiveW = size.width - insetPx * 2f
                            val effectiveH = size.height - insetPx * 2f

                            selectedPointIndex = currentPoints.indexOfFirst { pt ->
                                val ptPxX = insetPx + (pt.x / 255f) * effectiveW
                                val ptPxY = insetPx + (1f - pt.y / 255f) * effectiveH
                                hypot((offset.x - ptPxX).toDouble(), (offset.y - ptPxY).toDouble()) < 40.0
                            }
                            if (selectedPointIndex != -1) {
                                isDraggingPoint = true
                                onTouchStart()
                            }
                        },
                        onDragEnd = {
                            isDraggingPoint = false
                            hudText = null
                            deltaPercentage = null
                            onTouchEnd("Tone Curve Drag")
                        },
                        onDragCancel = {
                            isDraggingPoint = false
                            hudText = null
                            deltaPercentage = null
                            onTouchEnd("Cancel")
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (selectedPointIndex != -1 && selectedPointIndex in currentPoints.indices) {
                                val insetPx = 14.dp.toPx()
                                val effectiveW = size.width - insetPx * 2f
                                val effectiveH = size.height - insetPx * 2f

                                // Tip 175: Drag off-canvas to delete point
                                val isOffCanvas = change.position.y < -24.dp.toPx() || change.position.y > size.height + 24.dp.toPx()
                                if (isOffCanvas && selectedPointIndex > 0 && selectedPointIndex < currentPoints.lastIndex) {
                                    val updated = currentPoints.toMutableList().apply { removeAt(selectedPointIndex) }
                                    updateCurrentPoints(updated, "Delete Point")
                                    selectedPointIndex = -1
                                    isDraggingPoint = false
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    return@detectDragGestures
                                }

                                var newX = ((change.position.x - insetPx) / effectiveW * 255f).coerceIn(0f, 255f)
                                var newY = ((1f - (change.position.y - insetPx) / effectiveH) * 255f).coerceIn(0f, 255f)

                                // Tip 179: Snap to 45° diagonal line
                                if (abs(newY - newX) < 3.5f) {
                                    newY = newX
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }

                                // Tip 164 & 176: Stable dragging across neighbor points & endpoint clipping
                                val isFirst = selectedPointIndex == 0
                                val isLast = selectedPointIndex == currentPoints.lastIndex

                                if (isFirst) {
                                    val maxBoundX = maxOf(0f, if (currentPoints.size > 1) currentPoints[1].x - 1.0f else 128f)
                                    newX = newX.coerceIn(0f, maxBoundX)
                                    newY = newY.coerceIn(0f, 255f)
                                } else if (isLast) {
                                    val minBoundX = minOf(255f, if (currentPoints.size > 1) currentPoints[currentPoints.size - 2].x + 1.0f else 128f)
                                    newX = newX.coerceIn(minBoundX, 255f)
                                    newY = newY.coerceIn(0f, 255f)
                                } else {
                                    val minBoundX = currentPoints[selectedPointIndex - 1].x + 1.0f
                                    val maxBoundX = maxOf(minBoundX, currentPoints[selectedPointIndex + 1].x - 1.0f)
                                    newX = newX.coerceIn(minBoundX, maxBoundX)
                                    newY = newY.coerceIn(0f, 255f)
                                }

                                val updated = currentPoints.toMutableList()
                                updated[selectedPointIndex] = CurvePoint(newX, newY)
                                updateCurrentPoints(updated)

                                // Tip 171 & 199: Update HUD coordinate readouts
                                val delta = ((newY - newX) / 255f * 100f).toInt()
                                deltaPercentage = if (delta >= 0) "+$delta%" else "$delta%"
                                hudText = "In: ${newX.toInt()} → Out: ${newY.toInt()} ($deltaPercentage)"
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val inset = 14.dp.toPx()
                val innerW = w - inset * 2f
                val innerH = h - inset * 2f

                // Tip 165: Background Histogram inside Curve Box (alpha = 0.18f)
                val histBins = when (selectedChannel) {
                    CurveChannel.RGB -> histogramData.lumaBins
                    CurveChannel.RED -> histogramData.redBins
                    CurveChannel.GREEN -> histogramData.greenBins
                    CurveChannel.BLUE -> histogramData.blueBins
                }
                if (histBins.isNotEmpty()) {
                    val maxHist = max(1, histBins.maxOrNull() ?: 1).toFloat()
                    val histPath = Path()
                    histPath.moveTo(inset, inset + innerH)
                    for (i in 0..255 step 2) {
                        val px = inset + (i / 255f) * innerW
                        val norm = (histBins[i] / maxHist).coerceIn(0f, 1f)
                        val py = inset + innerH - (norm * innerH * 0.70f)
                        histPath.lineTo(px, py)
                    }
                    histPath.lineTo(inset + innerW, inset + innerH)
                    histPath.close()
                    drawPath(histPath, color = selectedChannel.color.copy(alpha = 0.14f))
                }

                // Tip 173: Diagonal Baseline Ticks & Quarter Grid
                for (i in 1..3) {
                    val gx = inset + (i / 4f) * innerW
                    val gy = inset + (i / 4f) * innerH
                    val isMid = i == 2
                    val strokeW = if (isMid) 1.dp.toPx() else 0.8.dp.toPx()
                    val strokeEffect = if (isMid) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)

                    drawLine(
                        color = SpectrumBorder.copy(alpha = if (isMid) 0.6f else 0.35f),
                        start = Offset(gx, inset),
                        end = Offset(gx, inset + innerH),
                        strokeWidth = strokeW,
                        pathEffect = strokeEffect
                    )
                    drawLine(
                        color = SpectrumBorder.copy(alpha = if (isMid) 0.6f else 0.35f),
                        start = Offset(inset, gy),
                        end = Offset(inset + innerW, gy),
                        strokeWidth = strokeW,
                        pathEffect = strokeEffect
                    )
                }

                // 45° Neutral Reference Line
                drawLine(
                    color = SpectrumBorder.copy(alpha = 0.5f),
                    start = Offset(inset, inset + innerH),
                    end = Offset(inset + innerW, inset),
                    strokeWidth = 1.2.dp.toPx()
                )

                // Tip 166: Ghost Reference Curves for Inactive Channels
                if (selectedSubMode == CurveSubMode.POINT) {
                    fun drawGhost(pts: List<CurvePoint>, col: Color) {
                        val lut = SplineInterpolator.evaluateSpline(pts)
                        val p = Path().apply {
                            moveTo(inset, inset + innerH - (lut[0] / 255f) * innerH)
                            for (i in 1..255 step 2) {
                                val px = inset + (i / 255f) * innerW
                                val py = inset + innerH - (lut[i] / 255f) * innerH
                                lineTo(px, py)
                            }
                        }
                        drawPath(p, color = col.copy(alpha = 0.28f), style = Stroke(width = 1.dp.toPx()))
                    }

                    if (selectedChannel != CurveChannel.RGB) drawGhost(curves.composite, Color.White)
                    if (selectedChannel != CurveChannel.RED) drawGhost(curves.red, Color(0xFFE53935))
                    if (selectedChannel != CurveChannel.GREEN) drawGhost(curves.green, Color(0xFF43A047))
                    if (selectedChannel != CurveChannel.BLUE) drawGhost(curves.blue, Color(0xFF1E88E5))
                }

                // Tip 191 & 192: Parametric Curve Live Spline Preview in Graph
                val liveParametricLut = SplineInterpolator.generateParametricLut256(curves)
                val paramPath = Path().apply {
                    val p0 = (liveParametricLut[0].toInt() and 0xFF) / 255f
                    moveTo(inset, inset + innerH - p0 * innerH)
                    for (i in 1..255 step 2) {
                        val px = inset + (i / 255f) * innerW
                        val py = inset + innerH - ((liveParametricLut[i].toInt() and 0xFF) / 255f) * innerH
                        lineTo(px, py)
                    }
                }
                // Tip 193: Dashed line for Parametric Curve
                drawPath(
                    path = paramPath,
                    color = Color(0xFF888888),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                    )
                )

                // Render Active Channel Point Curve Spline
                val activeLut = SplineInterpolator.evaluateSpline(currentPoints)
                val activePath = Path().apply {
                    moveTo(inset, inset + innerH - (activeLut[0] / 255f) * innerH)
                    for (i in 1..255) {
                        val px = inset + (i / 255f) * innerW
                        val py = inset + innerH - (activeLut[i] / 255f) * innerH
                        lineTo(px, py)
                    }
                }
                drawPath(
                    path = activePath,
                    color = selectedChannel.color,
                    style = Stroke(width = 2.2.dp.toPx())
                )

                // Tip 198: Midpoint Crosshair Guide through active point
                if (selectedPointIndex in currentPoints.indices) {
                    val activePt = currentPoints[selectedPointIndex]
                    val crossPx = inset + (activePt.x / 255f) * innerW
                    val crossPy = inset + (1f - activePt.y / 255f) * innerH
                    drawLine(
                        color = Color(0x661473E6),
                        start = Offset(crossPx, inset),
                        end = Offset(crossPx, inset + innerH),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    drawLine(
                        color = Color(0x661473E6),
                        start = Offset(inset, crossPy),
                        end = Offset(inset + innerW, crossPy),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                // Render Control Points (Tips 180, 181, 182)
                if (selectedSubMode == CurveSubMode.POINT) {
                    currentPoints.forEachIndexed { index, pt ->
                        val px = inset + (pt.x / 255f) * innerW
                        val py = inset + (1f - pt.y / 255f) * innerH
                        val isSelected = index == selectedPointIndex

                        // Tip 180: Halo Ring Styling
                        if (isSelected) {
                            drawCircle(
                                color = selectedChannel.color.copy(alpha = 0.35f),
                                radius = 14.dp.toPx(),
                                center = Offset(px, py)
                            )
                            drawCircle(
                                color = selectedChannel.color,
                                radius = 7.dp.toPx(),
                                center = Offset(px, py)
                            )
                        } else {
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                        drawCircle(
                            color = Color(0xFF141414),
                            radius = (if (isSelected) 4.dp else 2.5.dp).toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // Tip 174: Left & Bottom Tonal Ramp Bars (4.dp gradient bars)
                // Bottom ramp (X-axis Input: Black to White)
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.White), startX = inset, endX = inset + innerW),
                    topLeft = Offset(inset, h - 4.dp.toPx()),
                    size = Size(innerW, 3.dp.toPx())
                )

                // Left ramp (Y-axis Output: Gradient tailored to active channel)
                val yRampColors = when (selectedChannel) {
                    CurveChannel.RGB -> listOf(Color.White, Color.Black)
                    CurveChannel.RED -> listOf(Color(0xFFE53935), Color(0xFF00E5FF)) // Red to Cyan
                    CurveChannel.GREEN -> listOf(Color(0xFF43A047), Color(0xFFE040FB)) // Green to Magenta
                    CurveChannel.BLUE -> listOf(Color(0xFF1E88E5), Color(0xFFFFD600)) // Blue to Yellow
                }
                drawRect(
                    brush = Brush.verticalGradient(yRampColors, startY = inset, endY = inset + innerH),
                    topLeft = Offset(1.dp.toPx(), inset),
                    size = Size(3.dp.toPx(), innerH)
                )

                // Tip 168 & 203: Parametric Split Handles at bottom
                if (selectedSubMode == CurveSubMode.PARAMETRIC) {
                    val splits = listOf(
                        Pair(curves.splitShadow, "Shadows"),
                        Pair(curves.splitMid, "Mid"),
                        Pair(curves.splitHighlight, "Highlights")
                    )
                    splits.forEach { (splitVal, _) ->
                        val sx = inset + (splitVal / 255f) * innerW
                        val triPath = Path().apply {
                            moveTo(sx, inset + innerH)
                            lineTo(sx - 5.dp.toPx(), inset + innerH + 8.dp.toPx())
                            lineTo(sx + 5.dp.toPx(), inset + innerH + 8.dp.toPx())
                            close()
                        }
                        drawPath(triPath, color = Color(0xFF1473E6))
                    }
                }
            }

            // Tip 171 & 205: Floating Input/Output Coordinate HUD
            hudText?.let { text ->
                Surface(
                    color = Color(0xE61E1E1E),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tip 185: Fine-Tune Nudge Buttons & Reset Channel Actions
        if (selectedSubMode == CurveSubMode.POINT && selectedPointIndex in currentPoints.indices) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Point #${selectedPointIndex + 1} (${currentPoints[selectedPointIndex].x.toInt()}, ${currentPoints[selectedPointIndex].y.toInt()})",
                    color = SpectrumTextSecondary,
                    fontSize = 11.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Nudge Left
                    IconButton(
                        onClick = {
                            val pt = currentPoints[selectedPointIndex]
                            val newX = (pt.x - 1f).coerceIn(0f, 255f)
                            val updated = currentPoints.toMutableList()
                            updated[selectedPointIndex] = pt.copy(x = newX)
                            updateCurrentPoints(updated)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nudge Left", tint = SpectrumTextPrimary, modifier = Modifier.size(16.dp))
                    }
                    // Nudge Right
                    IconButton(
                        onClick = {
                            val pt = currentPoints[selectedPointIndex]
                            val newX = (pt.x + 1f).coerceIn(0f, 255f)
                            val updated = currentPoints.toMutableList()
                            updated[selectedPointIndex] = pt.copy(x = newX)
                            updateCurrentPoints(updated)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Nudge Right", tint = SpectrumTextPrimary, modifier = Modifier.size(16.dp))
                    }
                    // Nudge Down
                    IconButton(
                        onClick = {
                            val pt = currentPoints[selectedPointIndex]
                            val newY = (pt.y - 1f).coerceIn(0f, 255f)
                            val updated = currentPoints.toMutableList()
                            updated[selectedPointIndex] = pt.copy(y = newY)
                            updateCurrentPoints(updated)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Nudge Down", tint = SpectrumTextPrimary, modifier = Modifier.size(16.dp))
                    }
                    // Nudge Up
                    IconButton(
                        onClick = {
                            val pt = currentPoints[selectedPointIndex]
                            val newY = (pt.y + 1f).coerceIn(0f, 255f)
                            val updated = currentPoints.toMutableList()
                            updated[selectedPointIndex] = pt.copy(y = newY)
                            updateCurrentPoints(updated)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Nudge Up", tint = SpectrumTextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Tip 177: Quick Curve Preset Chips (Linear, Medium Contrast, Strong Contrast, Matte / Faded, Film Roll-off)
        if (selectedSubMode == CurveSubMode.POINT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tip 187: Invert Curve
                ActionChip(label = "Invert") {
                    val inverted = currentPoints.map { CurvePoint(it.x, 255f - it.y) }
                    updateCurrentPoints(inverted, "Invert Curve")
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }

                // Tip 178: Reset Channel
                ActionChip(label = "Linear (Reset)") {
                    val reset = listOf(CurvePoint(0f, 0f), CurvePoint(255f, 255f))
                    updateCurrentPoints(reset, "Reset Curve")
                    selectedPointIndex = -1
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }

                ActionChip(label = "Medium Contrast") {
                    val sCurve = listOf(
                        CurvePoint(0f, 0f),
                        CurvePoint(64f, 52f),
                        CurvePoint(192f, 204f),
                        CurvePoint(255f, 255f)
                    )
                    updateCurrentPoints(sCurve, "Medium Contrast Curve")
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }

                ActionChip(label = "Strong Contrast") {
                    val strongSCurve = listOf(
                        CurvePoint(0f, 0f),
                        CurvePoint(64f, 42f),
                        CurvePoint(192f, 214f),
                        CurvePoint(255f, 255f)
                    )
                    updateCurrentPoints(strongSCurve, "Strong Contrast Curve")
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }

                ActionChip(label = "Matte Blacks") {
                    val matte = listOf(
                        CurvePoint(0f, 24f),
                        CurvePoint(64f, 60f),
                        CurvePoint(192f, 195f),
                        CurvePoint(255f, 245f)
                    )
                    updateCurrentPoints(matte, "Matte Curve")
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }

                ActionChip(label = "Film Roll-off") {
                    val rollOff = listOf(
                        CurvePoint(0f, 0f),
                        CurvePoint(128f, 128f),
                        CurvePoint(200f, 192f),
                        CurvePoint(255f, 235f)
                    )
                    updateCurrentPoints(rollOff, "Film Roll-off Curve")
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }

        // Parametric Mode Sliders & Split Handles (Tips 168, 169, 203)
        if (selectedSubMode == CurveSubMode.PARAMETRIC) {
            Spacer(modifier = Modifier.height(4.dp))

            RampedSlider(
                label = "Highlights",
                value = curves.parametricHighlights,
                minVal = -100f,
                maxVal = 100f,
                onValueChange = { onCurveChanged(curves.copy(parametricHighlights = it)) },
                onValueChangeStart = onTouchStart,
                onValueChangeEnd = { onTouchEnd("Parametric Highlights") }
            )
            RampedSlider(
                label = "Lights",
                value = curves.parametricLights,
                minVal = -100f,
                maxVal = 100f,
                onValueChange = { onCurveChanged(curves.copy(parametricLights = it)) },
                onValueChangeStart = onTouchStart,
                onValueChangeEnd = { onTouchEnd("Parametric Lights") }
            )
            RampedSlider(
                label = "Darks",
                value = curves.parametricDarks,
                minVal = -100f,
                maxVal = 100f,
                onValueChange = { onCurveChanged(curves.copy(parametricDarks = it)) },
                onValueChangeStart = onTouchStart,
                onValueChangeEnd = { onTouchEnd("Parametric Darks") }
            )
            RampedSlider(
                label = "Shadows",
                value = curves.parametricShadows,
                minVal = -100f,
                maxVal = 100f,
                onValueChange = { onCurveChanged(curves.copy(parametricShadows = it)) },
                onValueChangeStart = onTouchStart,
                onValueChangeEnd = { onTouchEnd("Parametric Shadows") }
            )

            // Tip 203: Double-tap split handle to reset splits
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Splits: ${curves.splitShadow.toInt()} | ${curves.splitMid.toInt()} | ${curves.splitHighlight.toInt()}",
                    color = SpectrumTextSecondary,
                    fontSize = 11.sp
                )
                TextButton(
                    onClick = {
                        onCurveChanged(curves.copy(splitShadow = 64f, splitMid = 128f, splitHighlight = 192f))
                        onTouchEnd("Reset Splits")
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                ) {
                    Text("Reset Splits", color = SpectrumBlue, fontSize = 11.sp)
                }
            }
        }

        // Tips 201 & 202: 3D Film Emulation LUTs Integration
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = SpectrumInnerBorder, modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Film 3D LUT (Creative)",
                color = SpectrumTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (activeFilm3dLut != Film3dLut.NONE) {
                Text(
                    text = "${(film3dLutIntensity * 100).toInt()}%",
                    color = SpectrumBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Film3dLut.entries.forEach { lut ->
                val isSelected = activeFilm3dLut == lut
                Surface(
                    color = if (isSelected) SpectrumBlue else Color(0xFF222222),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SpectrumBlue else SpectrumBorder
                    ),
                    modifier = Modifier.clickable {
                        onFilm3dLutChanged(lut)
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                ) {
                    Text(
                        text = lut.label,
                        color = if (isSelected) Color.White else SpectrumTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (activeFilm3dLut != Film3dLut.NONE) {
            RampedSlider(
                label = "LUT Intensity",
                value = film3dLutIntensity * 100f,
                minVal = 0f,
                maxVal = 100f,
                onValueChange = { onFilm3dLutIntensityChanged(it / 100f) },
                onValueChangeStart = onTouchStart,
                onValueChangeEnd = { onTouchEnd("LUT Intensity") }
            )
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF242424),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383838)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = SpectrumTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
