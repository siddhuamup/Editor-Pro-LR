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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.ColorScience
import com.cleanlr.ui.theme.SpectrumBlue
import com.cleanlr.ui.theme.SpectrumBorder
import com.cleanlr.ui.theme.SpectrumTextPrimary
import com.cleanlr.ui.theme.SpectrumTextSecondary
import java.util.Locale
import kotlin.math.abs

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
            .padding(vertical = 4.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = SpectrumTextSecondary,
                fontSize = 12.sp,
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
            val displayValue = if (formatPattern.contains("f")) {
                val prefix = if (isBipolar && value > 0.001f) "+" else ""
                prefix + String.format(Locale.US, formatPattern, value)
            } else {
                value.toInt().toString()
            }
            Text(
                text = displayValue,
                color = if (abs(value - defaultValue) > 0.001f) SpectrumBlue else SpectrumTextPrimary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
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
                                    // Zero snap magnetic threshold (2% deadzone)
                                    if (abs(sPos) < 0.04f) {
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
                val trackHeight = 3.dp.toPx()
                val thumbRadius = 6.dp.toPx()

                // Background track
                drawRect(
                    color = SpectrumBorder,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight)
                )

                if (isBipolar) {
                    val midX = size.width / 2f

                    // Center notch
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(midX, centerY - 5.dp.toPx()),
                        end = Offset(midX, centerY + 5.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Active fill from center to thumb
                    val thumbX = midX + (normalizedPos * (size.width / 2f))
                    val left = minOf(midX, thumbX)
                    val right = maxOf(midX, thumbX)

                    if (right > left) {
                        drawRect(
                            color = SpectrumBlue,
                            topLeft = Offset(left, centerY - trackHeight / 2f),
                            size = Size(right - left, trackHeight)
                        )
                    }

                    // Thumb circle
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY)
                    )
                } else {
                    val thumbX = normalizedPos * size.width

                    // Active fill from left to thumb
                    drawRect(
                        color = SpectrumBlue,
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(thumbX, trackHeight)
                    )

                    // Thumb circle
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
