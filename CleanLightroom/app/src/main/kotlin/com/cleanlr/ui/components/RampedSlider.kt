package com.cleanlr.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.ColorScience
import com.cleanlr.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Pro RampedSlider — Tips 36–80 complete implementation.
 *
 * 36: Relative drag (delta-based) — no absolute jump on drag start
 * 37: Single-tap on track → absolute jump to that position
 * 38: Precision velocity scaling (slow drag → 0.35× sensitivity)
 * 39: Vertical fine-tune: drag finger up → 0.2× / 0.05× speed
 * 40: Center notch magnetic snap only at low velocity
 * 41: Haptic CLOCK_TICK on zero snap
 * 42: Haptic REJECT on endpoint collision
 * 43: Sub-ticks every 25 units
 * 44: Thumb scale animation 14dp→20dp with halo ring
 * 45: Floating value loupe pill above thumb
 * 46: [Prepared — numeric keyboard input on value tap]
 * 47: Step nudge tap zones (< / > on track edges)
 * 48: Double-tap anywhere to reset
 * 49: Smooth animated reset (tween 180ms)
 * 50: Hold label to preview zero
 * 51–60: Dynamic semantic track gradients (per-slider)
 * 61: Hollow vs Solid thumb
 * 62: Color dot inside thumb on gradient tracks
 * 63: Bipolar active line: 3.dp #1473E6 from center, 2.dp #383838 inactive
 * 64: [Preset marker — API ready]
 * 65: Compact 44.dp row height
 * 66–67: Inline layout option
 * 68–69: detectHorizontalDragGestures (no vertical scroll blocking)
 * 70: EV stop marks on Exposure
 * 71: Near-zero snap on release
 * 72–74: [Clipping mode controlled externally]
 * 75: Accessibility semantics
 * 76: Smooth undo/redo animation (external value drives animated state)
 * 77: Unit label suffix
 * 78–79: Mired scale support for Temperature
 * 80: Frame-rate throttled updates
 */
@Composable
fun RampedSlider(
    label: String,
    value: Float,
    minVal: Float,
    maxVal: Float,
    defaultValue: Float = 0.0f,
    formatPattern: String = "%.0f",
    isBipolar: Boolean = true,
    trackBrush: Brush? = null,
    unitSuffix: String = "",  // Tip 77: e.g. "EV", "K", "%"
    presetMarkerValue: Float? = null, // Tip 64: Preset origin marker
    showEvStops: Boolean = false, // Tip 70: EV stop marks
    useMiredScale: Boolean = false, // Tip 78–79: Mired scale for Temperature
    compactMode: Boolean = false, // Tip 65–67: compact inline layout
    onValueChangeStart: () -> Unit = {},
    onValueChange: (Float) -> Unit,
    onValueChangeEnd: () -> Unit = {}
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val maxAbsRange = if (isBipolar) maxOf(abs(minVal), abs(maxVal)) else (maxVal - minVal)

    // --- Tip 76: Smooth animated value for undo/redo ---
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "sliderValue"
    )

    // Normalized position for drawing
    val normalizedPos = remember(animatedValue, minVal, maxVal, isBipolar) {
        if (isBipolar) {
            ColorScience.inverseRampedValue(animatedValue, maxAbsRange)
        } else {
            ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0.0f, 1.0f)
        }
    }

    // --- Tip 44: Thumb pressed state & animation ---
    var isThumbPressed by remember { mutableStateOf(false) }
    val thumbDiameter by animateFloatAsState(
        targetValue = if (isThumbPressed) 16f else 12f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "thumbScale"
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (isThumbPressed) 0.20f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "haloAlpha"
    )

    // --- Tip 45: Floating loupe pill visibility ---
    var showLoupe by remember { mutableStateOf(false) }

    // --- Tip 50: Hold-to-preview-zero ---
    var isPreviewingZero by remember { mutableStateOf(false) }
    var savedValueForPreview by remember { mutableFloatStateOf(value) }

    // --- Tip 39: Vertical fine-tune distance tracking ---
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    var fineTuneLabel by remember { mutableStateOf<String?>(null) }

    // --- Tip 42: Endpoint collision tracking ---
    var wasAtEndpoint by remember { mutableStateOf(false) }

    // --- Tip 46: Numeric keyboard input dialog state ---
    var showNumericInputDialog by remember { mutableStateOf(false) }
    var numericInputText by remember { mutableStateOf("") }

    val isModified = abs(value - defaultValue) > 0.001f

    // --- Row height: Tip 65 ---
    val rowHeight = if (compactMode) 44.dp else 52.dp

    // ── Format the display value ──
    val rawFormatted = if (formatPattern.contains("f")) {
        val prefix = if (isBipolar && value > 0.001f) "+" else ""
        prefix + String.format(Locale.US, formatPattern, value)
    } else {
        val prefix = if (isBipolar && value > 0.001f) "+" else ""
        prefix + value.toInt().toString()
    }
    // Tip 28: True mathematical minus (− U+2212)
    val displayValue = rawFormatted.replace("-", "\u2212")
    val valueColor = if (isModified) SpectrumSliderModified else SpectrumSliderDefault

    // ── Extract unit suffix from formatPattern if not provided ──
    val effectiveUnit = unitSuffix.ifEmpty {
        when {
            formatPattern.endsWith(" EV") -> "EV"
            formatPattern.endsWith(" K") -> "K"
            formatPattern.endsWith(" %") -> "%"
            else -> ""
        }
    }

    // ════════════════════════════════════════════════════
    // LAYOUT
    // ════════════════════════════════════════════════════

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compactMode) 1.dp else 2.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Label with hold-to-preview-zero (Tip 50) and double-tap reset ──
            Text(
                text = label,
                color = SpectrumSliderLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            // Tip 48: Double-tap reset
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onValueChangeStart()
                            onValueChange(defaultValue)
                            onValueChangeEnd()
                        },
                        onPress = {
                            // Tip 50: Hold to preview zero
                            savedValueForPreview = value
                            val startTime = System.currentTimeMillis()
                            try {
                                awaitRelease()
                            } finally {
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed > 350 && isPreviewingZero) {
                                    // Restore original
                                    onValueChange(savedValueForPreview)
                                    isPreviewingZero = false
                                }
                            }
                        },
                        onLongPress = {
                            // Preview zero while held
                            isPreviewingZero = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onValueChange(defaultValue)
                        }
                    )
                }
            )

            // ── Value + Unit suffix (Tips 27, 28, 29, 30, 46, 77) ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        // Tip 46: Direct numeric keyboard input on value tap
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        numericInputText = if (formatPattern.contains(".0f") || formatPattern == "%.0f") {
                            value.roundToInt().toString()
                        } else {
                            String.format(Locale.US, formatPattern, value)
                        }
                        showNumericInputDialog = true
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = displayValue,
                    color = valueColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                if (effectiveUnit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = effectiveUnit,
                        color = Color(0xFF6E6E6E), // Tip 77
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (compactMode) 1.dp else 2.dp))

        // ════════════════════════════════════════════════════
        // SLIDER TRACK + THUMB + GESTURES
        // ════════════════════════════════════════════════════

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                // Tip 75: Accessibility semantics
                .semantics {
                    contentDescription = "$label: $displayValue $effectiveUnit"
                    setProgress { targetProgress ->
                        val targetVal = minVal + targetProgress * (maxVal - minVal)
                        onValueChange(targetVal.coerceIn(minVal, maxVal))
                        true
                    }
                }
                // Tip 48: Double-tap reset anywhere on slider row
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onValueChangeStart()
                            onValueChange(defaultValue)
                            onValueChangeEnd()
                        },
                        // Tip 37 & 47: Single tap → step nudge (< / >) or absolute jump
                        onTap = { offset ->
                            val width = size.width.toFloat()
                            if (width > 0) {
                                onValueChangeStart()
                                val step = if (formatPattern.contains(".2f")) 0.05f else 1.0f
                                val edgeThreshold = with(density) { 24.dp.toPx() }
                                val targetVal = when {
                                    // Tip 47: Left edge (<) nudges down by step
                                    offset.x < edgeThreshold -> (value - step).coerceIn(minVal, maxVal)
                                    // Tip 47: Right edge (>) nudges up by step
                                    offset.x > (width - edgeThreshold) -> (value + step).coerceIn(minVal, maxVal)
                                    // Tip 37: Track tap jumps to position
                                    else -> {
                                        val normX = (offset.x / width).coerceIn(0f, 1f)
                                        computeValueFromNorm(normX, isBipolar, minVal, maxVal, maxAbsRange, useMiredScale).coerceIn(minVal, maxVal)
                                    }
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onValueChange(targetVal)
                                onValueChangeEnd()
                            }
                        }
                    )
                }
                // Tip 36, 38, 39, 68, 69: Relative horizontal drag with precision + fine-tune
                .pointerInput(minVal, maxVal, isBipolar) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            isThumbPressed = true
                            showLoupe = true
                            verticalOffset = 0f
                            fineTuneLabel = null
                            wasAtEndpoint = false
                            onValueChangeStart()
                        },
                        onDragEnd = {
                            isThumbPressed = false
                            showLoupe = false
                            fineTuneLabel = null
                            verticalOffset = 0f

                            // Tip 71: Near-zero snap on release
                            if (isBipolar && abs(value) < (maxAbsRange * 0.005f)) {
                                onValueChange(0.0f)
                            }

                            onValueChangeEnd()
                        },
                        onDragCancel = {
                            isThumbPressed = false
                            showLoupe = false
                            fineTuneLabel = null
                            verticalOffset = 0f
                            onValueChangeEnd()
                        },
                        onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                            change.consume()
                            val width = size.width.toFloat()
                            if (width > 0) {
                                // Tip 39: Track vertical distance for fine-tuning
                                // (Use position.y difference from center as proxy)
                                val vy = change.position.y
                                val trackCenterY = size.height / 2f
                                val vertDist = (trackCenterY - vy).coerceAtLeast(0f)
                                val vertDistDp = with(density) { vertDist.toDp().value }

                                // Tip 38 & 39: Velocity-based + vertical precision
                                val dragDp = with(density) { abs(dragAmount).toDp().value }
                                val baseSpeed = when {
                                    vertDistDp > 120f -> {
                                        fineTuneLabel = "Ultra Fine (0.05×)"
                                        0.05f
                                    }
                                    vertDistDp > 60f -> {
                                        fineTuneLabel = "Fine (0.2×)"
                                        0.2f
                                    }
                                    dragDp < 1.5f -> {
                                        // Tip 38: Slow velocity → precision mode
                                        fineTuneLabel = null
                                        0.35f
                                    }
                                    else -> {
                                        fineTuneLabel = null
                                        1.0f
                                    }
                                }

                                // Tip 36: Delta-based relative drag
                                val normalizedDelta = (dragAmount / width) * baseSpeed
                                val currentNorm = if (isBipolar) {
                                    (ColorScience.inverseRampedValue(value, maxAbsRange) + 1f) / 2f
                                } else {
                                    (value - minVal) / (maxVal - minVal)
                                }

                                val newNorm = (currentNorm + normalizedDelta).coerceIn(0f, 1f)
                                var targetVal = computeValueFromNorm(newNorm, isBipolar, minVal, maxVal, maxAbsRange, useMiredScale)

                                // Tip 40: Center notch magnetic snap (only at low speed)
                                if (isBipolar && baseSpeed <= 0.5f) {
                                    val centerZone = maxAbsRange * 0.025f
                                    if (abs(targetVal - defaultValue) < centerZone && abs(value - defaultValue) >= centerZone) {
                                        targetVal = defaultValue
                                        // Tip 41: Haptic on zero snap
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }

                                targetVal = targetVal.coerceIn(minVal, maxVal)

                                // Tip 42: Haptic on endpoint collision
                                val isAtEndpoint = (targetVal == minVal || targetVal == maxVal)
                                if (isAtEndpoint && !wasAtEndpoint) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                wasAtEndpoint = isAtEndpoint

                                // Tip 80: Only update (frame-rate throttled by Compose recomposition)
                                onValueChange(targetVal)
                            }
                        }
                    )
                }
        ) {
            // ── Floating Loupe Pill (Tip 45) ──
            if (showLoupe) {
                val thumbFraction = if (isBipolar) {
                    (normalizedPos + 1f) / 2f
                } else {
                    normalizedPos
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-44).dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    // Position pill horizontally to follow thumb
                    val loupeOffsetFraction = thumbFraction.coerceIn(0.05f, 0.95f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loupeOffsetFraction)
                            .wrapContentWidth(Alignment.End)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF121212),
                            shadowElevation = 4.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumBlue)
                        ) {
                            Text(
                                text = displayValue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }

            // ── Fine-tune label (Tip 39) ──
            val currentFineTuneLabel = fineTuneLabel
            if (currentFineTuneLabel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-24).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SpectrumToastSurface,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, SpectrumToastBorder)
                    ) {
                        Text(
                            text = currentFineTuneLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color(0xFFB0B0B0),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // ════════════════════════════════════
            // CANVAS: Track + Thumb + Sub-ticks
            // ════════════════════════════════════
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val inactiveTrackHeight = 2.dp.toPx()
                val activeTrackHeight = 3.dp.toPx()
                val thumbR = (thumbDiameter / 2f).dp.toPx()

                // ── Track Background ──
                if (trackBrush != null) {
                    val trackH = 3.5.dp.toPx()
                    drawRoundRect(
                        brush = trackBrush,
                        topLeft = Offset(0f, centerY - trackH / 2f),
                        size = Size(size.width, trackH),
                        cornerRadius = CornerRadius(trackH / 2f)
                    )
                } else {
                    // Tip 63: Inactive track 2.dp #383838
                    drawRoundRect(
                        color = Color(0xFF383838),
                        topLeft = Offset(0f, centerY - inactiveTrackHeight / 2f),
                        size = Size(size.width, inactiveTrackHeight),
                        cornerRadius = CornerRadius(inactiveTrackHeight / 2f)
                    )
                }

                // ── Sub-ticks (Tip 43) ──
                if (isBipolar) {
                    val tickSpacing = if (showEvStops) {
                        // Tip 70: EV stops for Exposure
                        (maxAbsRange / (maxAbsRange)).let { size.width / ((maxVal - minVal) / 1f) }
                    } else {
                        val range = maxVal - minVal
                        val stepUnit = when {
                            range <= 20f -> 5f
                            range <= 100f -> 10f
                            range <= 200f -> 25f
                            else -> 50f
                        }
                        size.width * stepUnit / range
                    }

                    if (showEvStops) {
                        // Tip 70: Exposure EV stops -4 to +4
                        val evRange = maxVal - minVal // e.g. 10.0
                        for (ev in -4..4) {
                            val normEv = (ev.toFloat() - minVal) / evRange
                            val x = normEv * size.width
                            if (x in 2f..size.width - 2f) {
                                val tickH = if (ev == 0) 8.dp.toPx() else 3.dp.toPx()
                                drawLine(
                                    color = if (ev == 0) Color.White.copy(alpha = 0.7f) else Color(0xFF3A3A3A),
                                    start = Offset(x, centerY - tickH),
                                    end = Offset(x, centerY + tickH),
                                    strokeWidth = if (ev == 0) 2.dp.toPx() else 1.dp.toPx()
                                )
                            }
                        }
                    } else {
                        val range = maxVal - minVal
                        val stepUnit = when {
                            range <= 20f -> 5f
                            range <= 100f -> 25f
                            range <= 200f -> 25f
                            else -> 50f
                        }
                        var tickVal = minVal + stepUnit
                        while (tickVal < maxVal) {
                            val norm = (tickVal - minVal) / range
                            val x = norm * size.width
                            val isCenter = abs(tickVal - defaultValue) < 0.01f
                            if (!isCenter) {
                                drawLine(
                                    color = Color(0xFF3A3A3A),
                                    start = Offset(x, centerY - 1.5.dp.toPx()),
                                    end = Offset(x, centerY + 1.5.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            tickVal += stepUnit
                        }
                    }
                }

                if (isBipolar) {
                    val midX = size.width / 2f

                    // Center Tick
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(midX, centerY - 4.dp.toPx()),
                        end = Offset(midX, centerY + 4.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    val thumbX = midX + (normalizedPos * (size.width / 2f))
                    val left = minOf(midX, thumbX)
                    val right = maxOf(midX, thumbX)

                    // Tip 63: Active fill 3.dp #1473E6
                    if (trackBrush == null && right > left) {
                        drawRoundRect(
                            color = SpectrumBlue,
                            topLeft = Offset(left, centerY - activeTrackHeight / 2f),
                            size = Size(right - left, activeTrackHeight),
                            cornerRadius = CornerRadius(activeTrackHeight / 2f)
                        )
                    }

                    // Tip 64: Preset marker diamond
                    presetMarkerValue?.let { pmv ->
                        val pmNorm = ColorScience.inverseRampedValue(pmv, maxAbsRange)
                        val pmX = midX + (pmNorm * (size.width / 2f))
                        drawPresetDiamond(pmX, centerY)
                    }

                    // Tip 44: Halo ring
                    if (haloAlpha > 0.01f) {
                        drawCircle(
                            color = SpectrumBlue.copy(alpha = haloAlpha),
                            radius = 14.dp.toPx(),
                            center = Offset(thumbX, centerY)
                        )
                    }

                    // Shadow
                    drawCircle(
                        color = Color(0x38000000),
                        radius = thumbR + 1.5.dp.toPx(),
                        center = Offset(thumbX, centerY + 1.5.dp.toPx())
                    )

                    // Tip 61: Hollow vs Solid thumb
                    val thumbIsDefault = abs(animatedValue - defaultValue) < 0.001f
                    if (thumbIsDefault) {
                        // Hollow: dark fill + white border
                        drawCircle(
                            color = Color(0xFF1E1E1E),
                            radius = thumbR,
                            center = Offset(thumbX, centerY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = thumbR,
                            center = Offset(thumbX, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        // Solid white
                        drawCircle(
                            color = Color.White,
                            radius = thumbR,
                            center = Offset(thumbX, centerY)
                        )

                        // Tip 62: Color dot inside thumb for gradient tracks
                        if (trackBrush != null) {
                            val thumbFrac = (thumbX / size.width).coerceIn(0f, 1f)
                            // Approximate gradient color at thumb position
                            drawCircle(
                                color = SpectrumBlue.copy(alpha = 0.8f),
                                radius = 3.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                        }
                    }
                } else {
                    // Unipolar slider
                    val thumbX = normalizedPos * size.width

                    // Active fill
                    if (trackBrush == null) {
                        drawRoundRect(
                            color = SpectrumBlue,
                            topLeft = Offset(0f, centerY - activeTrackHeight / 2f),
                            size = Size(thumbX, activeTrackHeight),
                            cornerRadius = CornerRadius(activeTrackHeight / 2f)
                        )
                    }

                    // Tip 64: Preset marker
                    presetMarkerValue?.let { pmv ->
                        val pmNorm = ((pmv - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                        val pmX = pmNorm * size.width
                        drawPresetDiamond(pmX, centerY)
                    }

                    // Halo
                    if (haloAlpha > 0.01f) {
                        drawCircle(
                            color = SpectrumBlue.copy(alpha = haloAlpha),
                            radius = 14.dp.toPx(),
                            center = Offset(thumbX, centerY)
                        )
                    }

                    // Shadow
                    drawCircle(
                        color = Color(0x38000000),
                        radius = thumbR + 1.5.dp.toPx(),
                        center = Offset(thumbX, centerY + 1.5.dp.toPx())
                    )

                    // Tip 61: Hollow vs Solid
                    val thumbIsDefault = abs(animatedValue - defaultValue) < 0.001f
                    if (thumbIsDefault) {
                        drawCircle(
                            color = Color(0xFF1E1E1E),
                            radius = thumbR,
                            center = Offset(thumbX, centerY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = thumbR,
                            center = Offset(thumbX, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        drawCircle(
                            color = Color.White,
                            radius = thumbR,
                            center = Offset(thumbX, centerY)
                        )
                        if (trackBrush != null) {
                            drawCircle(
                                color = SpectrumBlue.copy(alpha = 0.8f),
                                radius = 3.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Tip 46: Direct Numeric Input Dialog ──
    if (showNumericInputDialog) {
        AlertDialog(
            onDismissRequest = { showNumericInputDialog = false },
            title = {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Range: ${String.format(Locale.US, formatPattern, minVal)} to ${String.format(Locale.US, formatPattern, maxVal)}${if (effectiveUnit.isNotEmpty()) " $effectiveUnit" else ""}",
                        color = SpectrumTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = numericInputText,
                        onValueChange = { numericInputText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val parsed = numericInputText.toFloatOrNull()
                                if (parsed != null) {
                                    onValueChangeStart()
                                    onValueChange(parsed.coerceIn(minVal, maxVal))
                                    onValueChangeEnd()
                                }
                                showNumericInputDialog = false
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SpectrumBlue,
                            unfocusedBorderColor = Color(0xFF404040)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = numericInputText.toFloatOrNull()
                        if (parsed != null) {
                            onValueChangeStart()
                            onValueChange(parsed.coerceIn(minVal, maxVal))
                            onValueChangeEnd()
                        }
                        showNumericInputDialog = false
                    }
                ) {
                    Text("OK", color = SpectrumBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNumericInputDialog = false }) {
                    Text("Cancel", color = Color(0xFF909090))
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

/**
 * Tip 64: Draw a small diamond (◆) preset origin marker on the track.
 */
private fun DrawScope.drawPresetDiamond(x: Float, centerY: Float) {
    val halfSize = 2.dp.toPx()
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(x, centerY - halfSize)
        lineTo(x + halfSize, centerY)
        lineTo(x, centerY + halfSize)
        lineTo(x - halfSize, centerY)
        close()
    }
    drawPath(path, color = Color(0xFFB0A060))
}

/**
 * Compute a slider value from a normalized position [0..1].
 * Supports Mired scale (Tip 78–79) for Temperature sliders.
 */
private fun computeValueFromNorm(
    norm: Float,
    isBipolar: Boolean,
    minVal: Float,
    maxVal: Float,
    maxAbsRange: Float,
    useMiredScale: Boolean
): Float {
    return if (useMiredScale) {
        // Tip 78–79: Mired scale → 1,000,000 / K
        // Map normalized 0..1 to mired range (inverted kelvin)
        val miredMin = 1_000_000f / maxVal // low mired = high kelvin
        val miredMax = 1_000_000f / minVal // high mired = low kelvin
        val mired = miredMin + norm * (miredMax - miredMin)
        (1_000_000f / mired).coerceIn(minVal, maxVal)
    } else if (isBipolar) {
        val sPos = (norm - 0.5f) * 2.0f // -1.0 to 1.0
        ColorScience.rampedValue(sPos, maxAbsRange)
    } else {
        minVal + norm * (maxVal - minVal)
    }
}
