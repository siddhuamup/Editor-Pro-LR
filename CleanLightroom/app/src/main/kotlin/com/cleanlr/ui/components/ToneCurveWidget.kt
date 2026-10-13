package com.cleanlr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.SplineInterpolator
import com.cleanlr.model.CurvePoint
import com.cleanlr.model.ToneCurves
import com.cleanlr.ui.theme.SpectrumBlue
import com.cleanlr.ui.theme.SpectrumBorder
import com.cleanlr.ui.theme.SpectrumElevated
import com.cleanlr.ui.theme.SpectrumTextSecondary
import kotlin.math.hypot

@Composable
fun ToneCurveWidget(
    curves: ToneCurves,
    onCurveChanged: (ToneCurves) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPointIndex by remember { mutableStateOf(-1) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Curve Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(SpectrumElevated)
                .pointerInput(curves.pointCurve) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val clickX = (offset.x / w * 255f).coerceIn(0f, 255f)
                            val clickY = ((1f - offset.y / h) * 255f).coerceIn(0f, 255f)

                            // Check if tapped near an existing point (within 24dp)
                            val existingIdx = curves.pointCurve.indexOfFirst { pt ->
                                val ptPxX = (pt.x / 255f) * w
                                val ptPxY = (1f - pt.y / 255f) * h
                                hypot((offset.x - ptPxX).toDouble(), (offset.y - ptPxY).toDouble()) < 48.0
                            }

                            if (existingIdx != -1) {
                                selectedPointIndex = existingIdx
                            } else {
                                // Add new point
                                val updated = (curves.pointCurve + CurvePoint(clickX, clickY)).sortedBy { it.x }
                                onCurveChanged(curves.copy(pointCurve = updated))
                                selectedPointIndex = updated.indexOfFirst { it.x == clickX }
                            }
                        },
                        onDoubleTap = { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            // Delete point if not endpoints (0,0) and (255,255)
                            val deleteIdx = curves.pointCurve.indexOfFirst { pt ->
                                val ptPxX = (pt.x / 255f) * w
                                val ptPxY = (1f - pt.y / 255f) * h
                                hypot((offset.x - ptPxX).toDouble(), (offset.y - ptPxY).toDouble()) < 48.0
                            }
                            if (deleteIdx > 0 && deleteIdx < curves.pointCurve.lastIndex) {
                                val updated = curves.pointCurve.toMutableList().apply { removeAt(deleteIdx) }
                                onCurveChanged(curves.copy(pointCurve = updated))
                                selectedPointIndex = -1
                            }
                        }
                    )
                }
                .pointerInput(curves.pointCurve) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            selectedPointIndex = curves.pointCurve.indexOfFirst { pt ->
                                val ptPxX = (pt.x / 255f) * w
                                val ptPxY = (1f - pt.y / 255f) * h
                                hypot((offset.x - ptPxX).toDouble(), (offset.y - ptPxY).toDouble()) < 48.0
                            }
                        },
                        onDragEnd = { selectedPointIndex = -1 },
                        onDragCancel = { selectedPointIndex = -1 },
                        onDrag = { change, _ ->
                            change.consume()
                            if (selectedPointIndex != -1) {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val newX = (change.position.x / w * 255f).coerceIn(0f, 255f)
                                val newY = ((1f - change.position.y / h) * 255f).coerceIn(0f, 255f)

                                val updated = curves.pointCurve.toMutableList()
                                val isFirst = selectedPointIndex == 0
                                val isLast = selectedPointIndex == updated.lastIndex

                                updated[selectedPointIndex] = CurvePoint(
                                    x = if (isFirst) 0f else if (isLast) 255f else newX,
                                    y = newY
                                )
                                onCurveChanged(curves.copy(pointCurve = updated.sortedBy { it.x }))
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw 4x4 Grid
                for (i in 1..3) {
                    val lineX = (i / 4f) * w
                    val lineY = (i / 4f) * h
                    drawLine(SpectrumBorder, Offset(lineX, 0f), Offset(lineX, h), strokeWidth = 1f)
                    drawLine(SpectrumBorder, Offset(0f, lineY), Offset(w, lineY), strokeWidth = 1f)
                }

                // Reference 45-degree diagonal
                drawLine(
                    color = SpectrumBorder.copy(alpha = 0.6f),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                    strokeWidth = 1.5f
                )

                // Render Spline
                val lut = SplineInterpolator.evaluateSpline(curves.pointCurve)
                val curvePath = Path().apply {
                    moveTo(0f, h - (lut[0] / 255f) * h)
                    for (i in 1..255) {
                        val px = (i / 255f) * w
                        val py = h - (lut[i] / 255f) * h
                        lineTo(px, py)
                    }
                }
                drawPath(curvePath, color = Color.White, style = Stroke(width = 2.dp.toPx()))

                // Draw Control Points
                curves.pointCurve.forEachIndexed { index, pt ->
                    val px = (pt.x / 255f) * w
                    val py = h - (pt.y / 255f) * h
                    val isSelected = index == selectedPointIndex

                    drawCircle(
                        color = if (isSelected) SpectrumBlue else Color.White,
                        radius = (if (isSelected) 7.dp else 5.dp).toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = (if (isSelected) 4.dp else 3.dp).toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            Text(
                text = "Tap to add point, double tap to remove",
                color = SpectrumTextSecondary,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Parametric Sliders
        RampedSlider(
            label = "Highlights",
            value = curves.parametricHighlights,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { onCurveChanged(curves.copy(parametricHighlights = it)) }
        )
        RampedSlider(
            label = "Lights",
            value = curves.parametricLights,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { onCurveChanged(curves.copy(parametricLights = it)) }
        )
        RampedSlider(
            label = "Darks",
            value = curves.parametricDarks,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { onCurveChanged(curves.copy(parametricDarks = it)) }
        )
        RampedSlider(
            label = "Shadows",
            value = curves.parametricShadows,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { onCurveChanged(curves.copy(parametricShadows = it)) }
        )
    }
}
