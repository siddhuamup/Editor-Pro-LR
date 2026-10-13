package com.cleanlr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.model.ColorGrading
import com.cleanlr.model.ColorGradingWheel
import com.cleanlr.ui.theme.SpectrumBlue
import com.cleanlr.ui.theme.SpectrumBorder
import com.cleanlr.ui.theme.SpectrumElevated
import com.cleanlr.ui.theme.SpectrumTextPrimary
import kotlin.math.*

enum class GradingTarget(val label: String) {
    SHADOWS("Shadows"),
    MIDTONES("Midtones"),
    HIGHLIGHTS("Highlights")
}

@Composable
fun ColorGradingWheelWidget(
    colorGrading: ColorGrading,
    onColorGradingChanged: (ColorGrading) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTarget by remember { mutableStateOf(GradingTarget.SHADOWS) }

    val activeWheel = when (activeTarget) {
        GradingTarget.SHADOWS -> colorGrading.shadows
        GradingTarget.MIDTONES -> colorGrading.midtones
        GradingTarget.HIGHLIGHTS -> colorGrading.highlights
    }

    fun updateActiveWheel(newWheel: ColorGradingWheel) {
        val updated = when (activeTarget) {
            GradingTarget.SHADOWS -> colorGrading.copy(shadows = newWheel)
            GradingTarget.MIDTONES -> colorGrading.copy(midtones = newWheel)
            GradingTarget.HIGHLIGHTS -> colorGrading.copy(highlights = newWheel)
        }
        onColorGradingChanged(updated)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Target tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GradingTarget.values().forEach { target ->
                val isSelected = target == activeTarget
                Button(
                    onClick = { activeTarget = target },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) SpectrumBlue else SpectrumElevated,
                        contentColor = SpectrumTextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = target.label, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Color Disc Canvas
        Box(
            modifier = Modifier
                .size(160.dp)
                .pointerInput(activeWheel) {
                    detectTapGestures(
                        onDoubleTap = {
                            updateActiveWheel(activeWheel.copy(hue = 0f, saturation = 0f))
                        }
                    )
                }
                .pointerInput(activeWheel) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        val cy = h / 2f
                        val radius = min(cx, cy) - 8.dp.toPx()

                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                        val sat = (dist / radius * 100f).coerceIn(0f, 100f)
                        var angleRad = atan2(dy.toDouble(), dx.toDouble())
                        if (angleRad < 0) angleRad += 2 * Math.PI
                        val hueDeg = Math.toDegrees(angleRad).toFloat()

                        updateActiveWheel(activeWheel.copy(hue = hueDeg, saturation = sat))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(cx, cy) - 8.dp.toPx()

                // Circular gradient ring representing spectrum
                val sweepColors = listOf(
                    Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
                drawCircle(
                    brush = Brush.sweepGradient(sweepColors, Offset(cx, cy)),
                    radius = radius,
                    center = Offset(cx, cy)
                )

                // Radial white overlay from center to simulate saturation falloff
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.95f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(cx, cy)
                )

                // Outer border
                drawCircle(
                    color = SpectrumBorder,
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draggable Puck Indicator
                val angleRad = Math.toRadians(activeWheel.hue.toDouble())
                val puckDist = (activeWheel.saturation / 100f) * radius
                val puckX = cx + puckDist * cos(angleRad).toFloat()
                val puckY = cy + puckDist * sin(angleRad).toFloat()

                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(puckX, puckY)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 3.dp.toPx(),
                    center = Offset(puckX, puckY)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Luminance slider
        RampedSlider(
            label = "Luminance",
            value = activeWheel.luminance,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { updateActiveWheel(activeWheel.copy(luminance = it)) }
        )

        // Blending & Balance
        RampedSlider(
            label = "Blending",
            value = colorGrading.blending,
            minVal = 0f,
            maxVal = 100f,
            defaultValue = 50f,
            isBipolar = false,
            onValueChange = { onColorGradingChanged(colorGrading.copy(blending = it)) }
        )
        RampedSlider(
            label = "Balance",
            value = colorGrading.balance,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { onColorGradingChanged(colorGrading.copy(balance = it)) }
        )
    }
}
