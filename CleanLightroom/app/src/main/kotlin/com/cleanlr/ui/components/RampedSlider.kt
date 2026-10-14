package com.cleanlr.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.ColorScience
import com.cleanlr.ui.theme.SpectrumBlue
import com.cleanlr.ui.theme.SpectrumBorder
import com.cleanlr.ui.theme.SpectrumTextPrimary
import com.cleanlr.ui.theme.SpectrumTextSecondary
import java.util.Locale
import kotlin.math.abs

/**
 * Adobe Spectrum Non-Linear Ramped Slider (PDF Part A Section 1 & 2):
 * - Track Height: 2.dp (Inactive), 3.dp (Active/Filled segment)
 * - Thumb Diameter: 16.dp (Pure White #FFFFFF with 2.dp elevation shadow)
 * - Center Tick (Zero Notch): 2.dp width, 8.dp height
 * - Touch Target Height: 48.dp (recommended 44.dp to 48.dp)
 * - Typography: Parameter Name 12.sp Regular (400), Value Label 13.sp Medium (500)
 * - Non-Linear Ramped Math: f(x) = sign(x) * |x|^1.4 (slows down ~35% in center zone |x| < 0.2)
 * - Haptic Zero Snap: Magnetic deadzone at |x| < 0.03 with haptic vibration
 */
@Composable
fun RampedSlider(
    label: String,
    value: Float,
    minVal: Float,
    maxVal: Float,
    defaultValue: Float = 0.0f,
    formatPattern: String = "%.0f",
    isBipolar: Boolean = true, // Whether 0 is center
    onValueChangeStart: () -> Unit = {},
    onValueChange: (Float) -> Unit,
    onValueChangeEnd: () -> Unit = {}
) {
    val view = LocalView.current
    val maxAbsRange = if (isBipolar) maxOf(abs(minVal), abs(maxVal)) else (maxVal - minVal)

    // Convert current value to normalized position [-1.0 .. 1.0] or [0.0 .. 1.0]
    val normalizedPos = remember(value, minVal, maxVal, isBipolar) {
        if (isBipolar) {
            ColorScience.inverseRampedValue(value, maxAbsRange)
        } else {
            ((value - minVal) / (maxVal - minVal)).coerceIn(0.0f, 1.0f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Parameter Name Font: 12.sp, Weight: Regular (400)
            Text(
                text = label,
                color = SpectrumTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onValueChangeStart()
                            onValueChange(defaultValue)
                            onValueChangeEnd()
                        }
                    )
                }
            )

            // Value Label Font: 13.sp, Weight: Medium (500)
            val displayValue = if (formatPattern.contains("f")) {
                val prefix = if (isBipolar && value > 0.001f) "+" else ""
                prefix + String.format(Locale.US, formatPattern, value)
            } else {
                value.toInt().toString()
            }
            Text(
                text = displayValue,
                color = if (abs(value - defaultValue) > 0.001f) SpectrumBlue else SpectrumTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Touch Target Height: 48.dp (Normative Spec: 44.dp to 48.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onValueChangeStart()
                            onValueChange(defaultValue)
                            onValueChangeEnd()
                        }
                    )
                }
                .pointerInput(minVal, maxVal, isBipolar) {
                    detectDragGestures(
                        onDragStart = {
                            onValueChangeStart()
                        },
                        onDragEnd = {
                            onValueChangeEnd()
                        },
                        onDragCancel = {
                            onValueChangeEnd()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val width = size.width.toFloat()
                            if (width > 0) {
                                val touchX = change.position.x.coerceIn(0f, width)
                                val normX = touchX / width // 0.0 to 1.0

                                val targetVal = if (isBipolar) {
                                    var sPos = (normX - 0.5f) * 2.0f // -1.0 to 1.0
                                    // Haptic Zero Snap: Magnetic deadzone at |x| < 0.03
                                    if (abs(sPos) < 0.03f) {
                                        sPos = 0.0f
                                    }
                                    ColorScience.rampedValue(sPos, maxAbsRange)
                                } else {
                                    minVal + normX * (maxVal - minVal)
                                }

                                if (isBipolar && abs(targetVal) < 0.001f && abs(value) > 0.001f) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }

                                onValueChange(targetVal.coerceIn(minVal, maxVal))
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                // Normative Track Dimensions: Inactive 2.dp, Active 3.dp
                val inactiveTrackHeight = 2.dp.toPx()
                val activeTrackHeight = 3.dp.toPx()
                // Normative Thumb: 16.dp Diameter (8.dp Radius) Pure White #FFFFFF
                val thumbRadius = 8.dp.toPx()

                // Inactive Background track (2.dp)
                drawRect(
                    color = SpectrumBorder,
                    topLeft = Offset(0f, centerY - inactiveTrackHeight / 2f),
                    size = Size(size.width, inactiveTrackHeight)
                )

                if (isBipolar) {
                    val midX = size.width / 2f

                    // Center Tick (Zero Notch): 2.dp width, 8.dp height
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(midX, centerY - 4.dp.toPx()),
                        end = Offset(midX, centerY + 4.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Active fill from center to thumb (3.dp)
                    val thumbX = midX + (normalizedPos * (size.width / 2f))
                    val left = minOf(midX, thumbX)
                    val right = maxOf(midX, thumbX)

                    if (right > left) {
                        drawRect(
                            color = SpectrumBlue,
                            topLeft = Offset(left, centerY - activeTrackHeight / 2f),
                            size = Size(right - left, activeTrackHeight)
                        )
                    }

                    // 2.dp Elevation Shadow below Thumb
                    drawCircle(
                        color = Color(0x38000000),
                        radius = thumbRadius + 1.5.dp.toPx(),
                        center = Offset(thumbX, centerY + 1.5.dp.toPx())
                    )

                    // Pure White #FFFFFF 16.dp Thumb
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY)
                    )
                } else {
                    val thumbX = normalizedPos * size.width

                    // Active fill from left to thumb (3.dp)
                    drawRect(
                        color = SpectrumBlue,
                        topLeft = Offset(0f, centerY - activeTrackHeight / 2f),
                        size = Size(thumbX, activeTrackHeight)
                    )

                    // 2.dp Elevation Shadow below Thumb
                    drawCircle(
                        color = Color(0x38000000),
                        radius = thumbRadius + 1.5.dp.toPx(),
                        center = Offset(thumbX, centerY + 1.5.dp.toPx())
                    )

                    // Pure White #FFFFFF 16.dp Thumb
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY)
                    )
                }
            }
        }
    }
}
