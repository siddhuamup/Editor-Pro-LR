package com.cleanlr.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

object DualRingBrushMath {
    fun calculateInnerRadius(brushRadius: Float): Float = brushRadius

    fun calculateOuterRadius(brushRadius: Float, featherPercent: Float): Float {
        val clampedFeather = featherPercent.coerceIn(0f, 100f)
        return brushRadius * (1.0f + (clampedFeather / 100.0f))
    }
}

/**
 * Task T8: Dual-Ring Brush Cursor (PDF Page 7)
 * 1. Inner Solid Ring: Main brush radius (Radius).
 * 2. Outer Dashed/Faded Ring: Feather limit (Feather = Radius + FeatherAmount).
 * 3. Center Crosshair (+): Subtle crosshair indicating stroke anchor point.
 */
@Composable
fun DualRingBrushCursor(
    center: Offset? = null,
    brushRadiusDp: Float,
    featherPercent: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val actualCenter = center ?: Offset(size.width / 2f, size.height / 2f)
        val rInner = DualRingBrushMath.calculateInnerRadius(brushRadiusDp).dp.toPx()
        val rOuter = DualRingBrushMath.calculateOuterRadius(brushRadiusDp, featherPercent).dp.toPx()

        val strokeWidth = 1.5.dp.toPx()
        val shadowWidth = strokeWidth + 1.dp.toPx()

        // ---------------------------------------------------------------------
        // 1. Center Crosshair (+)
        // ---------------------------------------------------------------------
        val arm = 6.dp.toPx()
        // Dark contrast underlay
        drawLine(
            color = Color.Black.copy(alpha = 0.5f),
            start = Offset(actualCenter.x - arm - 1f, actualCenter.y),
            end = Offset(actualCenter.x + arm + 1f, actualCenter.y),
            strokeWidth = shadowWidth
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.5f),
            start = Offset(actualCenter.x, actualCenter.y - arm - 1f),
            end = Offset(actualCenter.x, actualCenter.y + arm + 1f),
            strokeWidth = shadowWidth
        )
        // White crisp crosshair
        drawLine(
            color = Color.White,
            start = Offset(actualCenter.x - arm, actualCenter.y),
            end = Offset(actualCenter.x + arm, actualCenter.y),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = Color.White,
            start = Offset(actualCenter.x, actualCenter.y - arm),
            end = Offset(actualCenter.x, actualCenter.y + arm),
            strokeWidth = strokeWidth
        )

        // ---------------------------------------------------------------------
        // 2. Inner Solid Ring (Main Brush Density)
        // ---------------------------------------------------------------------
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = rInner,
            center = actualCenter,
            style = Stroke(width = shadowWidth)
        )
        drawCircle(
            color = Color.White,
            radius = rInner,
            center = actualCenter,
            style = Stroke(width = strokeWidth)
        )

        // ---------------------------------------------------------------------
        // 3. Outer Dashed Ring (Feather Limit)
        // ---------------------------------------------------------------------
        if (featherPercent > 0.01f) {
            val dashPattern = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = rOuter,
                center = actualCenter,
                style = Stroke(width = shadowWidth, pathEffect = dashPattern)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = rOuter,
                center = actualCenter,
                style = Stroke(width = strokeWidth, pathEffect = dashPattern)
            )
        }
    }
}
