package com.cleanlr.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.ui.res.painterResource
import com.cleanlr.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.model.ColorGrading
import com.cleanlr.model.ColorGradingWheel
import com.cleanlr.ui.theme.*
import kotlin.math.*

/**
 * Tip 252: 4-Way Color Grading Targets (Shadows, Midtones, Highlights, Global)
 */
enum class GradingTarget(val label: String) {
    SHADOWS("Shadows"),
    MIDTONES("Midtones"),
    HIGHLIGHTS("Highlights"),
    GLOBAL("Global")
}

/**
 * ColorGradingWheelWidget — Complete Pro 3-Way & Global Color Grading Wheels
 * Authentic Adobe Lightroom Architecture (Screenshot 083519 & 083543)
 */
@Composable
fun ColorGradingWheelWidget(
    colorGrading: ColorGrading,
    onColorGradingChanged: (ColorGrading) -> Unit,
    onTouchStart: () -> Unit = {},
    onTouchEnd: (actionName: String) -> Unit = {},
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var activeTarget by remember { mutableStateOf(GradingTarget.SHADOWS) }
    var is3UpMode by remember { mutableStateOf(false) }

    val activeWheel = when (activeTarget) {
        GradingTarget.SHADOWS -> colorGrading.shadows
        GradingTarget.MIDTONES -> colorGrading.midtones
        GradingTarget.HIGHLIGHTS -> colorGrading.highlights
        GradingTarget.GLOBAL -> colorGrading.global
    }

    fun updateWheelForTarget(target: GradingTarget, newWheel: ColorGradingWheel) {
        val updated = when (target) {
            GradingTarget.SHADOWS -> colorGrading.copy(shadows = newWheel)
            GradingTarget.MIDTONES -> colorGrading.copy(midtones = newWheel)
            GradingTarget.HIGHLIGHTS -> colorGrading.copy(highlights = newWheel)
            GradingTarget.GLOBAL -> colorGrading.copy(global = newWheel)
        }
        onColorGradingChanged(updated)
    }

    fun updateActiveWheel(newWheel: ColorGradingWheel) {
        updateWheelForTarget(activeTarget, newWheel)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Header Bar: "Color Grading" + "DONE" button (Screenshot 083519)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Color Grading",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Authentic DONE Button (Screenshot 083519)
            Surface(
                color = Color(0xFF262626),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDone()
                }
            ) {
                Text(
                    text = "DONE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // 2. Authentic 4-Target Switcher Row: Shadows ○ | Midtones ◎ | Highlights ● | Global ◑
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val threeTargets = listOf(
                GradingTarget.SHADOWS,
                GradingTarget.MIDTONES,
                GradingTarget.HIGHLIGHTS
            )

            threeTargets.forEach { target ->
                val isSelected = target == activeTarget && !is3UpMode
                val iconRes = when (target) {
                    GradingTarget.SHADOWS -> R.drawable.ic_lr_cg_shadows_wheel
                    GradingTarget.MIDTONES -> R.drawable.ic_lr_cg_midtones_wheel
                    GradingTarget.HIGHLIGHTS -> R.drawable.ic_lr_cg_highlights_wheel
                    else -> R.drawable.ic_lr_cg_global_wheel
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(26.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            is3UpMode = false
                            activeTarget = target
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = target.label,
                        tint = if (isSelected) Color.White else Color(0xFF727272),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Divider |
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color(0xFF383838))
            )

            // Global Target ◑
            val isGlobalSelected = activeTarget == GradingTarget.GLOBAL && !is3UpMode
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(26.dp)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        is3UpMode = false
                        activeTarget = GradingTarget.GLOBAL
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_cg_global_wheel),
                    contentDescription = "Global",
                    tint = if (isGlobalSelected) Color.White else Color(0xFF727272),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        if (is3UpMode) {
            // 3-Up Simultaneous Mini Wheels View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    GradingTarget.SHADOWS to colorGrading.shadows,
                    GradingTarget.MIDTONES to colorGrading.midtones,
                    GradingTarget.HIGHLIGHTS to colorGrading.highlights
                ).forEach { (target, wheel) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = target.label,
                            color = SpectrumTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SingleColorGradingWheelCanvas(
                            wheel = wheel,
                            diameterDp = 92,
                            onWheelChanged = { updated ->
                                updateWheelForTarget(target, updated)
                            },
                            onTouchStart = onTouchStart,
                            onTouchEnd = { onTouchEnd("${target.label} Wheel") }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (wheel.saturation > 0.01f) "${wheel.hue.toInt()}° • ${wheel.saturation.toInt()}%" else "Neutral",
                            color = SpectrumTextTertiary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        } else {
            // Authentic Centered Single Wheel Layout (Screenshot 083519)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Centered 210dp Wheel Canvas
                SingleColorGradingWheelCanvas(
                    wheel = activeWheel,
                    diameterDp = 210,
                    onWheelChanged = { updated ->
                        updateActiveWheel(updated)
                    },
                    onTouchStart = onTouchStart,
                    onTouchEnd = { onTouchEnd("${activeTarget.label} Wheel") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Target Name Row with More dots and Chevron (Screenshot 083519)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeTarget.label} •••",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_back_chevron),
                        contentDescription = "Previous / More",
                        tint = Color(0xFFA2A2A2),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Live Parameter Readout: "Hue 0  Sat 0"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "Hue  ${activeWheel.hue.toInt()}   Sat  ${activeWheel.saturation.toInt()}",
                        color = Color(0xFFA2A2A2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Luminance Slider
                RampedSlider(
                    label = "Luminance",
                    value = activeWheel.luminance,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = true,
                    onValueChangeStart = onTouchStart,
                    onValueChange = { updateActiveWheel(activeWheel.copy(luminance = it)) },
                    onValueChangeEnd = { onTouchEnd("${activeTarget.label} Luminance") }
                )

                if (activeTarget != GradingTarget.GLOBAL) {
                    RampedSlider(
                        label = "Blending",
                        value = colorGrading.blending,
                        minVal = 0f,
                        maxVal = 100f,
                        defaultValue = 50f,
                        isBipolar = false,
                        onValueChangeStart = onTouchStart,
                        onValueChange = { onColorGradingChanged(colorGrading.copy(blending = it)) },
                        onValueChangeEnd = { onTouchEnd("Grading Blending") }
                    )

                    RampedSlider(
                        label = "Balance",
                        value = colorGrading.balance,
                        minVal = -100f,
                        maxVal = 100f,
                        defaultValue = 0f,
                        isBipolar = true,
                        onValueChangeStart = onTouchStart,
                        onValueChange = { onColorGradingChanged(colorGrading.copy(balance = it)) },
                        onValueChangeEnd = { onTouchEnd("Grading Balance") }
                    )
                } else {
                    // Global Fine Sliders
                    RampedSlider(
                        label = "Global Hue",
                        value = activeWheel.hue,
                        minVal = 0f,
                        maxVal = 360f,
                        defaultValue = 0f,
                        isBipolar = false,
                        formatPattern = "%.0f°",
                        onValueChangeStart = onTouchStart,
                        onValueChange = { updateActiveWheel(activeWheel.copy(hue = it)) },
                        onValueChangeEnd = { onTouchEnd("Global Hue") }
                    )
                    RampedSlider(
                        label = "Global Sat",
                        value = activeWheel.saturation,
                        minVal = 0f,
                        maxVal = 100f,
                        defaultValue = 0f,
                        isBipolar = false,
                        formatPattern = "%.0f",
                        onValueChangeStart = onTouchStart,
                        onValueChange = { updateActiveWheel(activeWheel.copy(saturation = it)) },
                        onValueChangeEnd = { onTouchEnd("Global Saturation") }
                    )
                }
            }
        }

        // Split-Tone Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "Teal & Orange" to ColorGrading(
                    shadows = ColorGradingWheel(hue = 195f, saturation = 35f, luminance = -5f),
                    highlights = ColorGradingWheel(hue = 35f, saturation = 30f, luminance = 5f),
                    blending = 65f, balance = 10f
                ),
                "Warm Gold" to ColorGrading(
                    shadows = ColorGradingWheel(hue = 45f, saturation = 25f, luminance = 0f),
                    highlights = ColorGradingWheel(hue = 55f, saturation = 40f, luminance = 10f),
                    blending = 50f, balance = 0f
                ),
                "Cool Blue" to ColorGrading(
                    shadows = ColorGradingWheel(hue = 220f, saturation = 35f, luminance = -10f),
                    highlights = ColorGradingWheel(hue = 200f, saturation = 20f, luminance = 0f),
                    blending = 55f, balance = -15f
                ),
                "Sepia" to ColorGrading(
                    shadows = ColorGradingWheel(hue = 30f, saturation = 30f, luminance = -10f),
                    midtones = ColorGradingWheel(hue = 38f, saturation = 25f, luminance = 0f),
                    highlights = ColorGradingWheel(hue = 45f, saturation = 20f, luminance = 5f),
                    blending = 70f, balance = 0f
                )
            )

            presets.forEach { (name, presetGrading) ->
                Surface(
                    color = SpectrumElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumBorder),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTouchStart()
                        onColorGradingChanged(presetGrading)
                        onTouchEnd("Preset: $name")
                    }
                ) {
                    Text(
                        text = name,
                        color = SpectrumTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
/**
 * Dedicated Canvas for a Single Color Grading Wheel
 * Implements Tips 254 (Fine-tune), 255 (Radial line), 256 (Hue lock ring), 260 (Puck fill), 264 (Crosshair)
 */
@Composable
fun SingleColorGradingWheelCanvas(
    wheel: ColorGradingWheel,
    diameterDp: Int,
    onWheelChanged: (ColorGradingWheel) -> Unit,
    onTouchStart: () -> Unit = {},
    onTouchEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    // Tip 281 & 282: rememberUpdatedState prevents gesture interruption on state re-emission
    val currentWheel by rememberUpdatedState(wheel)
    var cachedHue by remember { mutableFloatStateOf(wheel.hue) }

    Box(
        modifier = modifier
            .size(diameterDp.dp)
            .pointerInput(diameterDp) {
                detectTapGestures(
                    onDoubleTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTouchStart()
                        // Tip 272 & 273: Double tap resets saturation but retains hue angle
                        onWheelChanged(currentWheel.copy(saturation = 0f))
                        onTouchEnd()
                    }
                )
            }
            .pointerInput(diameterDp) {
                detectDragGestures(
                    onDragStart = { onTouchStart() },
                    onDragEnd = { onTouchEnd() },
                    onDragCancel = { onTouchEnd() },
                    onDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        val cy = h / 2f
                        val radius = min(cx, cy) - 6.dp.toPx()

                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtMost(radius)

                        // Tip 265: Magnetic snap to center within 4dp
                        if (dist < 4.dp.toPx()) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onWheelChanged(currentWheel.copy(saturation = 0f))
                        } else {
                            // Tip 254: Non-linear fine tuning near center: (dist / radius)^1.3 * 100
                            val normalizedDist = (dist / radius).coerceIn(0f, 1f)
                            val sat = normalizedDist.pow(1.3f) * 100f

                            var angleRad = atan2(dy.toDouble(), dx.toDouble())
                            if (angleRad < 0) angleRad += 2 * Math.PI
                            val hueDeg = Math.toDegrees(angleRad).toFloat()
                            cachedHue = hueDeg

                            onWheelChanged(currentWheel.copy(hue = hueDeg, saturation = sat))
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(cx, cy) - 6.dp.toPx()

            // 1. Full Spectrum Sweep Gradient Ring
            val sweepColors = listOf(
                Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
            drawCircle(
                brush = Brush.sweepGradient(sweepColors, Offset(cx, cy)),
                radius = radius,
                center = Offset(cx, cy),
                alpha = 0.88f
            )

            // 2. Radial Gradient: neutral gray at center to transparent at rim (Tip 287)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF282828), Color(0x66282828), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )

            // 3. Outer Border (Tinted if active) (Tip 280)
            val isWheelActive = currentWheel.saturation > 0.01f
            val rimColor = if (isWheelActive) {
                val h = (currentWheel.hue % 360f) / 360f
                val s = (currentWheel.saturation / 100f).coerceIn(0f, 1f)
                Color.hsl(h * 360f, s, 0.5f).copy(alpha = 0.8f)
            } else {
                Color(0xFF484848)
            }
            drawCircle(
                color = rimColor,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = if (isWheelActive) 2.dp.toPx() else 1.5.dp.toPx())
            )

            // 4. Center Crosshair (Tip 264)
            val crossSize = 3.dp.toPx()
            drawLine(
                color = Color(0x88FFFFFF),
                start = Offset(cx - crossSize, cy),
                end = Offset(cx + crossSize, cy),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0x88FFFFFF),
                start = Offset(cx, cy - crossSize),
                end = Offset(cx, cy + crossSize),
                strokeWidth = 1.dp.toPx()
            )

            // 5. Puck indicator & Hue-lock radial line (Tip 255 & 260)
            val angleRad = Math.toRadians(currentWheel.hue.toDouble())
            val normalizedSat = (currentWheel.saturation / 100f).coerceIn(0f, 1f)
            val visualDist = (normalizedSat.pow(1.0f / 1.3f) * radius).coerceAtMost(radius)
            val puckX = (cx + visualDist * cos(angleRad).toFloat()).coerceIn(cx - radius, cx + radius)
            val puckY = (cy + visualDist * sin(angleRad).toFloat()).coerceIn(cy - radius, cy + radius)

            if (isWheelActive) {
                // Radial guide line from center to puck (Tip 255)
                drawLine(
                    color = Color(0x66FFFFFF),
                    start = Offset(cx, cy),
                    end = Offset(puckX, puckY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Puck fill with selected Hue/Saturation (Tip 260)
            val puckFillColor = if (isWheelActive) {
                val h = (currentWheel.hue % 360f) / 360f
                val s = (currentWheel.saturation / 100f).coerceIn(0f, 1f)
                Color.hsl(h * 360f, s, 0.5f)
            } else {
                Color(0xFF808080)
            }

            drawCircle(
                color = puckFillColor,
                radius = 5.dp.toPx(),
                center = Offset(puckX, puckY)
            )
            drawCircle(
                color = Color.White,
                radius = 5.5.dp.toPx(),
                center = Offset(puckX, puckY),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
