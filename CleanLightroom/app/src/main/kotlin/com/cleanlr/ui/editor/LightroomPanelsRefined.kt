package com.cleanlr.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.model.EditRecipe
import com.cleanlr.profile.ProfileItem
import com.cleanlr.profile.ProfileManager
import com.cleanlr.ui.components.CurveChannel
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.SpectrumBlue
import com.cleanlr.ui.theme.SpectrumSliderLabel
import com.cleanlr.ui.theme.spectrum_surface_dark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// =============================================================================
// 1. AUTHENTIC ADOBE LIGHTROOM v11.8.15 UNDO / REDO POPUP MENU (res/ixC.xml)
// =============================================================================

@Composable
fun AuthenticUndoRedoPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    resetLabel: String,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onOpenVersions: () -> Unit
) {
    val view = LocalView.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(172.dp)
            .background(Color(0xFF242426), RoundedCornerShape(8.dp))
            .border(0.8.dp, Color(0xFF3E3E42), RoundedCornerShape(8.dp))
    ) {
        // Row 1: Side-by-side [ Undo ] | [ Redo ] (exact layout from undo_redo_menu res/ixC.xml)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp, 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = canUndo) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onUndo()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_undo),
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else Color(0xFF5A5A5E),
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp, 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = canRedo) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onRedo()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_redo),
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else Color(0xFF5A5A5E),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF3A3A3E), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 2.dp))

        // Row 2: Reset (context-aware: Reset / Reset Crop / Reset Masking / Reset Lens Blur)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDismiss()
                    onReset()
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lr_reset_all),
                contentDescription = resetLabel,
                tint = Color(0xFFE3E3E3),
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = resetLabel,
                color = Color(0xFFE3E3E3),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }

        // Row 3: Versions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDismiss()
                    onOpenVersions()
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lr_versions),
                contentDescription = "Versions",
                tint = Color(0xFFE3E3E3),
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = "Versions",
                color = Color(0xFFE3E3E3),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// 2. AUTHENTIC ADOBE LIGHTROOM v11.8.15 LENS BLUR PANEL
//    (Reference Screenshots 083626, 200919, 200924 — ZERO Model Selector / AI Engine buttons!)
// =============================================================================

@Composable
fun AuthenticLensBlurPanel(
    blurAmount: Float,
    onBlurAmountChange: (Float) -> Unit,
    focalRangeStart: Float,
    focalRangeEnd: Float,
    onFocalRangeChange: (Float, Float) -> Unit,
    selectedBokehShape: String,
    onBokehShapeSelected: (String) -> Unit,
    isVisualizeDepthActive: Boolean,
    onToggleVisualizeDepth: () -> Unit,
    isFocusRangeSubMode: Boolean,
    onFocusRangeSubModeChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    var focusMode by remember { mutableStateOf("Subject") } // "Subject" or "Point"
    var tempStart by remember(focalRangeStart, isFocusRangeSubMode) { mutableFloatStateOf(focalRangeStart) }
    var tempEnd by remember(focalRangeEnd, isFocusRangeSubMode) { mutableFloatStateOf(focalRangeEnd) }

    if (isFocusRangeSubMode) {
        // ─── FOCUS RANGE SUB-PANEL (Exact Match to Screenshot 200919!) ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(spectrum_surface_dark)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header: "Focus range" + Visualize Depth icon right-aligned
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Focus range",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleVisualizeDepth()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isVisualizeDepthActive) R.drawable.ic_lr_mask_overlay_on else R.drawable.ic_lr_mask_overlay_off
                            ),
                            contentDescription = "Visualize depth",
                            tint = if (isVisualizeDepthActive) SpectrumBlue else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                // Focus mode row: "Focus" + [Subject] [Point]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Focus",
                        color = SpectrumSliderLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                    listOf(
                        "Subject" to R.drawable.ic_lr_focus_subject,
                        "Point" to R.drawable.ic_lr_focus_point
                    ).forEach { (mode, iconRes) ->
                        val active = focusMode == mode
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222224))
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) SpectrumBlue else Color(0xFF424246),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    focusMode = mode
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = "$mode focus",
                                tint = if (active) Color.White else Color(0xFFA2A2A2),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                // Depth Histogram Range Slider ("Near" ... "Far")
                DepthHistogramRangeBar(
                    rangeStart = tempStart,
                    rangeEnd = tempEnd,
                    onRangeChange = { s, e ->
                        tempStart = s
                        tempEnd = e
                        onFocalRangeChange(s, e)
                    }
                )
            }

            // Footer: [×] left + [Apply] blue pill right (Screenshot 200919)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onFocusRangeSubModeChange(false)
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_close),
                        contentDescription = "Cancel focus range",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    color = Color(0xFF1473E6),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onFocalRangeChange(tempStart, tempEnd)
                        onFocusRangeSubModeChange(false)
                    }
                ) {
                    Text(
                        text = "Apply",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp)
                    )
                }
            }
        }
        return
    }

    // ─── MAIN LENS BLUR PANEL (Exact Match to Screenshots 083626 & 200924) ───
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(spectrum_surface_dark)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Blur amount slider (exact Lightroom "+ 21" / "+ 57" white track + 20dp hollow thumb)
        RampedSlider(
            label = "Blur amount",
            value = blurAmount,
            minVal = 0f,
            maxVal = 100f,
            defaultValue = 0f,
            isBipolar = false,
            formatPattern = "+ %.0f",
            onValueChange = onBlurAmountChange
        )

        // Row 2: 6 Distinct Bokeh Shape Cards | Vertical Divider | Focus Button
        val bokehShapes = listOf("Circle", "Bubble", "5-blade", "Ring", "Oval", "Cat eye")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bokehShapes.forEach { shapeName ->
                    val isSelected = selectedBokehShape.equals(shapeName, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onBokehShapeSelected(shapeName)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF222225) else Color(0xFF161618))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) SpectrumBlue else Color(0xFF2E2E32),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BokehShapeGlyph(
                                shapeName = shapeName,
                                isSelected = isSelected,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = shapeName,
                            color = if (isSelected) Color.White else Color(0xFFA2A2A2),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            // Vertical divider between Bokeh Shapes and Focus button (exact match to Screenshot 083626)
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 16.dp)
                    .width(1.dp)
                    .height(44.dp)
                    .background(Color(0xFF393939))
            )

            // Focus Range button (exact match to Screenshot 083626 & 200924)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onFocusRangeSubModeChange(true)
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF262629))
                        .border(1.dp, Color(0xFF38383C), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_focus_subject),
                        contentDescription = "Focus",
                        tint = Color(0xFFE3E3E3),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Focus",
                    color = Color(0xFFE3E3E3),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Exact Dual-Shape Bokeh Glyphs matching Adobe Lightroom v11.8.15 Screenshot 200924:
 * Circle, Bubble, 5-blade, Ring, Oval, Cat eye.
 */
@Composable
private fun BokehShapeGlyph(
    shapeName: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryFill = if (isSelected) Color(0x88949498) else Color(0x6678787C)
    val secondaryFill = if (isSelected) Color(0xAAAEAEB2) else Color(0x888E8E92)
    val strokeColor = if (isSelected) Color(0xFFD0D0D4) else Color(0xFFA0A0A4)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bigCenter = Offset(w * 0.58f, h * 0.42f)
        val smallCenter = Offset(w * 0.36f, h * 0.66f)
        val bigR = w * 0.34f
        val smallR = w * 0.20f

        when (shapeName.lowercase()) {
            "circle" -> {
                drawCircle(color = primaryFill, radius = bigR, center = bigCenter)
                drawCircle(color = secondaryFill, radius = smallR, center = smallCenter)
            }
            "bubble" -> {
                drawCircle(color = primaryFill.copy(alpha = 0.22f), radius = bigR, center = bigCenter)
                drawCircle(color = strokeColor, radius = bigR, center = bigCenter, style = Stroke(width = 1.2.dp.toPx()))
                drawCircle(color = secondaryFill.copy(alpha = 0.28f), radius = smallR, center = smallCenter)
                drawCircle(color = strokeColor, radius = smallR, center = smallCenter, style = Stroke(width = 1.2.dp.toPx()))
            }
            "5-blade" -> {
                fun pentagon(c: Offset, r: Float): Path = Path().apply {
                    for (i in 0 until 5) {
                        val a = (-PI / 2.0 + i * 2.0 * PI / 5.0).toFloat()
                        val x = c.x + r * cos(a)
                        val y = c.y + r * sin(a)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(pentagon(bigCenter, bigR), color = primaryFill)
                drawPath(pentagon(smallCenter, smallR), color = secondaryFill)
            }
            "ring" -> {
                drawCircle(
                    color = primaryFill,
                    radius = bigR * 0.86f,
                    center = bigCenter,
                    style = Stroke(width = bigR * 0.42f)
                )
                drawCircle(
                    color = secondaryFill,
                    radius = smallR * 0.85f,
                    center = smallCenter,
                    style = Stroke(width = smallR * 0.42f)
                )
            }
            "oval" -> {
                drawOval(
                    color = primaryFill,
                    topLeft = Offset(bigCenter.x - bigR * 0.56f, bigCenter.y - bigR * 1.05f),
                    size = Size(bigR * 1.12f, bigR * 2.10f)
                )
                drawOval(
                    color = secondaryFill,
                    topLeft = Offset(smallCenter.x - smallR * 0.58f, smallCenter.y - smallR * 1.02f),
                    size = Size(smallR * 1.16f, smallR * 2.04f)
                )
            }
            "cat eye" -> {
                fun catEyePath(c: Offset, rx: Float, ry: Float): Path = Path().apply {
                    moveTo(c.x - rx, c.y)
                    quadraticTo(c.x, c.y - ry, c.x + rx, c.y)
                    quadraticTo(c.x, c.y + ry, c.x - rx, c.y)
                    close()
                }
                rotate(degrees = 42f, pivot = bigCenter) {
                    drawPath(catEyePath(bigCenter, bigR * 1.08f, bigR * 0.82f), color = primaryFill)
                }
                rotate(degrees = 42f, pivot = smallCenter) {
                    drawPath(catEyePath(smallCenter, smallR * 1.08f, smallR * 0.82f), color = secondaryFill)
                }
            }
            else -> {
                drawCircle(color = primaryFill, radius = bigR, center = bigCenter)
                drawCircle(color = secondaryFill, radius = smallR, center = smallCenter)
            }
        }
    }
}

/**
 * Interactive Depth Histogram Focus-Range Slider matching Lightroom Screenshot 200919.
 */
@Composable
private fun DepthHistogramRangeBar(
    rangeStart: Float,
    rangeEnd: Float,
    onRangeChange: (Float, Float) -> Unit
) {
    var activeHandle by remember { mutableStateOf(0) } // 1=left, 2=right, 3=center
    val curStart by rememberUpdatedState(rangeStart)
    val curEnd by rememberUpdatedState(rangeEnd)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val nx = (offset.x / w).coerceIn(0f, 1f)
                            val dLeft = abs(nx - curStart)
                            val dRight = abs(nx - curEnd)
                            activeHandle = when {
                                dLeft < 0.10f -> 1
                                dRight < 0.10f -> 2
                                else -> 3
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val dx = dragAmount / w
                            when (activeHandle) {
                                1 -> {
                                    val ns = (curStart + dx).coerceIn(0f, (curEnd - 0.12f).coerceAtLeast(0f))
                                    onRangeChange(ns, curEnd)
                                }
                                2 -> {
                                    val ne = (curEnd + dx).coerceIn((curStart + 0.12f).coerceAtMost(1f), 1f)
                                    onRangeChange(curStart, ne)
                                }
                                else -> {
                                    val span = (curEnd - curStart).coerceAtLeast(0.12f)
                                    val ns = (curStart + dx).coerceIn(0f, 1f - span)
                                    onRangeChange(ns, ns + span)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val barTop = 4.dp.toPx()
                val barH = h - 8.dp.toPx()

                // Checkerboard background
                val cell = 6.dp.toPx()
                val cols = (w / cell).toInt() + 1
                val rows = (barH / cell).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val dark = (r + c) % 2 == 0
                        drawRect(
                            color = if (dark) Color(0xFF2C2C2F) else Color(0xFF38383C),
                            topLeft = Offset(c * cell, barTop + r * cell),
                            size = Size(cell.coerceAtMost(w - c * cell), cell.coerceAtMost(barTop + barH - (barTop + r * cell)))
                        )
                    }
                }

                // Synthesized foreground/background depth histogram silhouette
                val histBins = floatArrayOf(
                    0.05f, 0.08f, 0.22f, 0.55f, 0.85f, 0.70f, 0.45f, 0.30f, 0.12f, 0.06f,
                    0.04f, 0.03f, 0.03f, 0.04f, 0.05f, 0.06f, 0.08f, 0.10f, 0.12f, 0.14f,
                    0.18f, 0.25f, 0.42f, 0.65f, 0.88f, 0.72f, 0.48f, 0.90f
                )
                val binW = w / histBins.size
                histBins.forEachIndexed { idx, v ->
                    val bh = v * (barH * 0.86f)
                    drawRect(
                        color = Color(0x99B8B8BE),
                        topLeft = Offset(idx * binW, barTop + barH - bh),
                        size = Size(binW + 0.5f, bh)
                    )
                }

                // Active white-bordered focal-range window
                val sx = (rangeStart.coerceIn(0f, 0.88f)) * w
                val ex = (rangeEnd.coerceIn(rangeStart + 0.12f, 1f)) * w
                val winW = (ex - sx).coerceAtLeast(24.dp.toPx())

                drawRoundRect(
                    color = Color(0x40141416),
                    topLeft = Offset(sx, barTop),
                    size = Size(winW, barH),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(sx, barTop),
                    size = Size(winW, barH),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Left & right white pill grab handles
                val pillW = 7.dp.toPx()
                val pillH = 22.dp.toPx()
                val pillY = barTop + (barH - pillH) / 2f
                listOf(sx - pillW * 0.5f, sx + winW - pillW * 0.5f).forEach { px ->
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(px, pillY),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }

                // Central hollow circular focal-plane indicator
                val midX = sx + winW * 0.5f
                val midY = barTop + barH * 0.5f
                drawCircle(color = Color(0xFF141414), radius = 6.dp.toPx(), center = Offset(midX, midY))
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(midX, midY), style = Stroke(width = 2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Near", color = Color(0xFFA2A2A2), fontSize = 12.sp)
            Text("Far", color = Color(0xFFA2A2A2), fontSize = 12.sp)
        }
    }
}

// =============================================================================
// 3. AUTHENTIC PROFILES PANEL WITH REAL LIVE PHOTO PREVIEW THUMBNAILS
//    (Reference Screenshot 083723 — shows ACTUAL edited photo with each profile applied!)
// =============================================================================

object ProfilePreviewEngine {

    /**
     * Returns an authentic 4x5 Android ColorMatrix modeling each Lightroom profile look
     * so profile thumbnails (and live photo preview) accurately display the user's photo!
     */
    fun colorMatrixForProfile(profile: ProfileItem): ColorMatrix {
        val name = profile.name
        val cm = ColorMatrix()
        when {
            profile.isMonochrome || name.contains("Monochrome", true) || name.startsWith("B&W", true) -> {
                val (wr, wg, wb, contrast, lift) = when {
                    name.contains("Red Filter", true) -> floatArrayOf(0.75f, 0.20f, 0.05f, 1.25f, -14f)
                    name.contains("Orange Filter", true) -> floatArrayOf(0.62f, 0.32f, 0.06f, 1.18f, -10f)
                    name.contains("Yellow Filter", true) -> floatArrayOf(0.45f, 0.48f, 0.07f, 1.12f, -6f)
                    name.contains("Green Filter", true) -> floatArrayOf(0.15f, 0.72f, 0.13f, 1.15f, -8f)
                    name.contains("Blue Filter", true) -> floatArrayOf(0.12f, 0.25f, 0.63f, 1.20f, -12f)
                    name.endsWith("02") || name.endsWith("06") -> floatArrayOf(0.30f, 0.59f, 0.11f, 1.32f, -20f)
                    name.endsWith("03") || name.endsWith("07") -> floatArrayOf(0.34f, 0.56f, 0.10f, 0.90f, 14f)
                    else -> floatArrayOf(0.299f, 0.587f, 0.114f, 1.10f, -4f)
                }
                cm.set(
                    floatArrayOf(
                        wr * contrast, wg * contrast, wb * contrast, 0f, lift,
                        wr * contrast, wg * contrast, wb * contrast, 0f, lift,
                        wr * contrast, wg * contrast, wb * contrast, 0f, lift,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            name.equals("Adobe Vivid", true) -> {
                cm.setSaturation(1.38f)
                val c = ColorMatrix(floatArrayOf(1.12f,0f,0f,0f,-10f, 0f,1.12f,0f,0f,-10f, 0f,0f,1.12f,0f,-10f, 0f,0f,0f,1f,0f))
                cm.postConcat(c)
            }
            name.equals("Adobe Landscape", true) -> {
                cm.setSaturation(1.24f)
                val c = ColorMatrix(floatArrayOf(1.04f,0f,0f,0f,-4f, 0f,1.10f,0f,0f,-4f, 0f,0f,1.14f,0f,-2f, 0f,0f,0f,1f,0f))
                cm.postConcat(c)
            }
            name.equals("Adobe Portrait", true) -> {
                cm.setSaturation(0.95f)
                val c = ColorMatrix(floatArrayOf(1.05f,0f,0f,0f,6f, 0f,1.01f,0f,0f,2f, 0f,0f,0.96f,0f,-2f, 0f,0f,0f,1f,0f))
                cm.postConcat(c)
            }
            name.equals("Adobe Neutral", true) -> {
                cm.setSaturation(0.86f)
                val c = ColorMatrix(floatArrayOf(0.90f,0f,0f,0f,12f, 0f,0.90f,0f,0f,12f, 0f,0f,0.90f,0f,12f, 0f,0f,0f,1f,0f))
                cm.postConcat(c)
            }
            name.startsWith("Artistic", true) -> {
                val idx = name.filter { it.isDigit() }.toIntOrNull() ?: 1
                val m = when (idx % 8) {
                    1 -> floatArrayOf(1.18f,0.06f,0.04f,0f,8f,  0.02f,0.84f,0.08f,0f,-6f, 0.08f,0.02f,1.18f,0f,14f, 0f,0f,0f,1f,0f) // Magenta-violet (Screenshot 083723)
                    2 -> floatArrayOf(1.12f,0.04f,0f,0f,10f,    0f,0.96f,0f,0f,4f,        0f,0.04f,0.84f,0f,8f,    0f,0f,0f,1f,0f) // Warm muted sunset
                    3 -> floatArrayOf(1.18f,0f,0f,0f,-8f,       0f,1.06f,0.06f,0f,-4f,    0f,0.10f,1.14f,0f,10f,   0f,0f,0f,1f,0f) // Teal-orange cinema
                    4 -> floatArrayOf(0.88f,0f,0.06f,0f,4f,     0f,1.12f,0.06f,0f,6f,     0.04f,0.06f,1.18f,0f,12f,0f,0f,0f,1f,0f) // Cool cyan
                    5 -> floatArrayOf(1.06f,0.08f,0f,0f,14f,    0.02f,1.10f,0f,0f,8f,     0f,0.05f,0.82f,0f,4f,    0f,0f,0f,1f,0f) // Golden emerald
                    6 -> floatArrayOf(1.22f,0.02f,0.02f,0f,10f, 0.04f,0.94f,0.04f,0f,4f,  0.06f,0f,1.06f,0f,8f,    0f,0f,0f,1f,0f) // Rose gold
                    7 -> floatArrayOf(0.92f,0f,0.08f,0f,-6f,    0f,0.98f,0.04f,0f,-4f,    0.04f,0.04f,1.26f,0f,16f,0f,0f,0f,1f,0f) // Cobalt shadows
                    else -> floatArrayOf(1.16f,0.05f,0f,0f,6f,  0.04f,1.04f,0f,0f,0f,     0f,0f,0.78f,0f,-4f,      0f,0f,0f,1f,0f) // Warm amber
                }
                cm.set(m)
            }
            name.startsWith("Modern", true) -> {
                val idx = name.filter { it.isDigit() }.toIntOrNull() ?: 1
                cm.setSaturation(if (idx % 2 == 0) 0.85f else 1.18f)
                val shift = ((idx % 5) - 2) * 8f
                val c = ColorMatrix(floatArrayOf(1.10f,0f,0f,0f,shift, 0f,1.06f,0f,0f,0f, 0f,0f,1.14f,0f,-shift, 0f,0f,0f,1f,0f))
                cm.postConcat(c)
            }
            name.startsWith("Vintage", true) -> {
                val idx = name.filter { it.isDigit() }.toIntOrNull() ?: 1
                cm.setSaturation(0.76f)
                val warm = 10f + (idx % 4) * 4f
                val c = ColorMatrix(floatArrayOf(0.96f,0.04f,0f,0f,warm, 0f,0.94f,0.02f,0f,warm*0.6f, 0f,0.04f,0.82f,0f,12f, 0f,0f,0f,1f,0f))
                cm.postConcat(c)
            }
            name.startsWith("Film", true) -> {
                val idx = name.filter { it.isDigit() }.toIntOrNull() ?: 1
                cm.setSaturation(0.92f)
                val lift = 10f + (idx % 3) * 4f
                val gShift = if (idx % 2 == 0) 6f else -4f
                val c = ColorMatrix(floatArrayOf(1.06f,0f,0f,0f,lift, 0f,1.02f,0f,0f,lift+gShift, 0f,0f,0.92f,0f,lift, 0f,0f,0f,1f,0f))
                cm.postConcat(c)
            }
            else -> { /* Adobe Color: identity */ }
        }
        return cm
    }

    fun renderThumbnail(baseThumb: Bitmap, profile: ProfileItem): Bitmap {
        val out = Bitmap.createBitmap(baseThumb.width, baseThumb.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrixForProfile(profile))
        canvas.drawBitmap(baseThumb, 0f, 0f, paint)
        return out
    }

    /**
     * Also applies a subtle real recipe color transform when user picks a creative profile
     * so selecting a profile immediately updates the main photo canvas!
     */
    fun applyProfileLookToRecipe(profileName: String, recipe: EditRecipe): EditRecipe {
        val prof = ProfileManager.findProfileByName(profileName)
        val isMono = prof?.isMonochrome == true || ProfileManager.isProfileMonochrome(profileName)
        return if (isMono) {
            recipe.copy(
                profile = profileName,
                basic = recipe.basic.copy(saturation = -100f),
                presence = recipe.presence.copy(saturation = -100f)
            ).syncLegacyFields()
        } else {
            recipe.copy(profile = profileName).syncLegacyFields()
        }
    }
}

@Composable
fun AuthenticProfilesPanel(
    sourceBitmap: Bitmap?,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    selectedProfile: String,
    onProfileSelected: (String) -> Unit
) {
    val view = LocalView.current
    var showGroupMenu by remember { mutableStateOf(false) }

    // For "Basic" group, mirror Screenshot 083723: show Adobe Color, Monochrome | Artistic 01..04 inline
    val profiles = remember(selectedGroup) {
        if (selectedGroup == "Basic") {
            ProfileManager.getProfilesByGroup("Basic") + ProfileManager.getProfilesByGroup("Artistic").take(4)
        } else {
            ProfileManager.getProfilesByGroup(selectedGroup)
        }
    }

    // Create compact downscaled thumbnail once per active photo
    val baseThumb = remember(sourceBitmap) {
        sourceBitmap?.let { bmp ->
            val maxDim = 220
            val scale = (maxDim.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1)).coerceAtMost(1f)
            val tw = (bmp.width * scale).toInt().coerceAtLeast(32)
            val th = (bmp.height * scale).toInt().coerceAtLeast(32)
            Bitmap.createScaledBitmap(bmp, tw, th, true)
        }
    }

    // Asynchronously render real profile previews of the user's photo!
    var previewMap by remember(baseThumb, selectedGroup) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(baseThumb, profiles) {
        val thumb = baseThumb ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.Default) {
            profiles.associate { prof ->
                prof.id to ProfilePreviewEngine.renderThumbnail(thumb, prof)
            }
        }
        previewMap = rendered
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(spectrum_surface_dark)
            .padding(top = 6.dp, bottom = 8.dp)
    ) {
        // Header Row: "<selectedGroup> ⌄" + 3-dots overflow (exact match to Screenshot 083723)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showGroupMenu = true }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedGroup,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_chevron_down),
                        contentDescription = "Choose profile group",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = showGroupMenu,
                    onDismissRequest = { showGroupMenu = false },
                    modifier = Modifier.background(Color(0xFF242426))
                ) {
                    ProfileManager.GROUPS.forEach { group ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = group,
                                    color = if (group == selectedGroup) SpectrumBlue else Color.White,
                                    fontSize = 14.sp
                                )
                            },
                            onClick = {
                                onGroupSelected(group)
                                showGroupMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = { showGroupMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_more_vert),
                    contentDescription = "Profile options",
                    tint = Color(0xFFA2A2A2),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // Amount slider (0..200, default 100)
        var profileAmount by remember(selectedProfile) { mutableFloatStateOf(100f) }
        RampedSlider(
            label = "Amount",
            value = profileAmount,
            minVal = 0f,
            maxVal = 200f,
            defaultValue = 100f,
            isBipolar = false,
            unitSuffix = "",
            formatPattern = "%.0f",
            onValueChange = { profileAmount = it }
        )

        Spacer(Modifier.height(6.dp))

        // Horizontal LazyRow of Real Photo Profile Preview Cards (exact match to Screenshot 083723)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(profiles, key = { _, item -> item.id }) { index, profileItem ->
                val isSelected = selectedProfile.equals(profileItem.name, ignoreCase = true)
                val previewBmp = previewMap[profileItem.id]

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 0.8.dp,
                            color = if (isSelected) SpectrumBlue else Color(0xFF2E2E30)
                        ),
                        modifier = Modifier
                            .size(width = 122.dp, height = 108.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onProfileSelected(profileItem.name)
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (previewBmp != null) {
                                Image(
                                    bitmap = previewBmp.asImageBitmap(),
                                    contentDescription = profileItem.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (baseThumb != null) {
                                Image(
                                    bitmap = baseThumb.asImageBitmap(),
                                    contentDescription = profileItem.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF242426)))
                            }

                            // Favorite star top-right (blue rounded badge when active, matching Screenshot 201008)
                            if (profileItem.group == "Basic") {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) SpectrumBlue else Color(0x66000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lr_star_filled),
                                        contentDescription = "Favorite",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Bottom label strip: Spectrum Blue solid bar when selected (Screenshot 201008), dark scrim when unselected (Screenshot 083723)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(SpectrumBlue)
                                        } else {
                                            Modifier.background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color(0xCC0D0D0F))
                                                )
                                            )
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = profileItem.name,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Crisp vertical divider separating Basic profiles from Artistic cards (Screenshot 083723)
                    if (selectedGroup == "Basic" && index == 5) {
                        Box(
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .width(1.dp)
                                .height(86.dp)
                                .background(Color(0xFF393939))
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 4. AUTHENTIC TONE CURVE BOTTOM CONTROLS (Screenshots 083439 & 083452)
// =============================================================================

@Composable
fun AuthenticToneCurveControls(
    selectedChannel: CurveChannel,
    isParametricMode: Boolean,
    onSelectChannel: (CurveChannel) -> Unit,
    onSelectParametric: () -> Unit,
    onDone: () -> Unit,
    parametricHighlights: Float = 0f,
    parametricLights: Float = 0f,
    parametricDarks: Float = 0f,
    parametricShadows: Float = 0f,
    onParametricChange: ((highlights: Float, lights: Float, darks: Float, shadows: Float) -> Unit)? = null
) {
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(spectrum_surface_dark)
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 14.dp)
    ) {
        // Header Row: "Curve" left + dark flat "DONE" button right (exact match to 083439 / 083452)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Curve",
                color = SpectrumSliderLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            Surface(
                color = Color(0xFF141414),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDone()
                }
            ) {
                Text(
                    text = "DONE",
                    color = Color(0xFFA2A2A2),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 5 Evenly Spaced Channel Buttons: RGB, Red, Green, Blue, Parametric (083439 / 083452)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurveChannel.entries.forEach { ch ->
                val isSelected = !isParametricMode && selectedChannel == ch
                val ringColor = when (ch) {
                    CurveChannel.RGB -> Color(0xFFE0E0E0)
                    CurveChannel.RED -> Color(0xFFD92B3A)
                    CurveChannel.GREEN -> Color(0xFF63C132)
                    CurveChannel.BLUE -> Color(0xFF3E4EB8)
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectChannel(ch)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        if (isSelected) {
                            drawCircle(color = if (ch == CurveChannel.RGB) Color(0xFFC8C8C8) else ringColor)
                            drawCircle(
                                color = if (ch == CurveChannel.RGB) Color(0xFF1A1A1A) else Color.White,
                                radius = 3.2.dp.toPx()
                            )
                        } else {
                            drawCircle(
                                color = ringColor,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            // 5th Button: Authentic Extracted Parametric Multi-Wave S-Curve Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectParametric()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_curve_parametric),
                    contentDescription = "Parametric Curve",
                    tint = if (isParametricMode) Color.White else Color(0xFF8E8E92),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Parametric Sliders (Highlights, Lights, Darks, Shadows) matching Screenshot 083452
        if (isParametricMode && onParametricChange != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)
            Spacer(Modifier.height(6.dp))
            RampedSlider(
                label = "Highlights",
                value = parametricHighlights,
                minVal = -100f,
                maxVal = 100f,
                defaultValue = 0f,
                isBipolar = true,
                onValueChange = { onParametricChange(it, parametricLights, parametricDarks, parametricShadows) }
            )
            RampedSlider(
                label = "Lights",
                value = parametricLights,
                minVal = -100f,
                maxVal = 100f,
                defaultValue = 0f,
                isBipolar = true,
                onValueChange = { onParametricChange(parametricHighlights, it, parametricDarks, parametricShadows) }
            )
            RampedSlider(
                label = "Darks",
                value = parametricDarks,
                minVal = -100f,
                maxVal = 100f,
                defaultValue = 0f,
                isBipolar = true,
                onValueChange = { onParametricChange(parametricHighlights, parametricLights, it, parametricShadows) }
            )
            RampedSlider(
                label = "Shadows",
                value = parametricShadows,
                minVal = -100f,
                maxVal = 100f,
                defaultValue = 0f,
                isBipolar = true,
                onValueChange = { onParametricChange(parametricHighlights, parametricLights, parametricDarks, it) }
            )
        }
    }
}
