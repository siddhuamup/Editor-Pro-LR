package com.cleanlr.mask

import com.cleanlr.model.BrushStrokePoint
import kotlin.math.*

/**
 * Task T5: High-Precision Image-Resolution Brush Engine
 *
 * Renders brush strokes directly onto the full-resolution target image canvas (NOT screen viewport).
 * Supports radius, feather (soft edge), and flow (opacity per dab) with distance-based interpolation.
 */
object BrushRasterizer {

    /**
     * Rasterizes a list of [BrushStrokePoint]s onto a 1-channel alpha buffer of size [width] x [height].
     *
     * @param points List of stroke points with x, y, radius, feather, flow
     * @param width Full target image width
     * @param height Full target image height
     * @param alphaBuffer Output 1D float array of size width * height (values 0.0f to 1.0f)
     */
    fun rasterizeBrushPoints(
        points: List<BrushStrokePoint>,
        width: Int,
        height: Int,
        alphaBuffer: FloatArray = FloatArray(width * height)
    ): FloatArray {
        if (points.isEmpty()) return alphaBuffer

        for (i in points.indices) {
            val p = points[i]
            // Draw current dab
            drawBrushDab(p, width, height, alphaBuffer)

            // Interpolate between consecutive points if gap > step size
            if (i > 0) {
                val prev = points[i - 1]
                val dx = p.x - prev.x
                val dy = p.y - prev.y
                val dist = sqrt(dx * dx + dy * dy)
                val step = max(1.0f, p.radius * 0.25f) // 25% spacing for smooth stroke

                if (dist > step) {
                    val numSteps = (dist / step).toInt()
                    for (s in 1..numSteps) {
                        val t = s.toFloat() / (numSteps + 1)
                        val interpX = prev.x + dx * t
                        val interpY = prev.y + dy * t
                        val interpRadius = prev.radius + (p.radius - prev.radius) * t
                        val interpFeather = prev.feather + (p.feather - prev.feather) * t
                        val interpFlow = prev.flow + (p.flow - prev.flow) * t

                        val interpPoint = BrushStrokePoint(
                            x = interpX,
                            y = interpY,
                            radius = interpRadius,
                            feather = interpFeather,
                            flow = interpFlow
                        )
                        drawBrushDab(interpPoint, width, height, alphaBuffer)
                    }
                }
            }
        }

        return alphaBuffer
    }

    private fun drawBrushDab(
        point: BrushStrokePoint,
        width: Int,
        height: Int,
        alphaBuffer: FloatArray
    ) {
        val radius = point.radius
        val feather = (point.feather / 100.0f).coerceIn(0.0f, 1.0f)
        val flow = point.flow.coerceIn(0.0f, 1.0f)

        val innerRadius = radius * (1.0f - feather)
        val outerRadius = radius

        val minX = max(0, floor(point.x - outerRadius).toInt())
        val maxX = min(width - 1, ceil(point.x + outerRadius).toInt())
        val minY = max(0, floor(point.y - outerRadius).toInt())
        val maxY = min(height - 1, ceil(point.y + outerRadius).toInt())

        val outerSq = outerRadius * outerRadius
        val innerSq = innerRadius * innerRadius

        for (py in minY..maxY) {
            val dy = py - point.y
            val dySq = dy * dy
            val rowOffset = py * width

            for (px in minX..maxX) {
                val dx = px - point.x
                val distSq = dx * dx + dySq

                if (distSq <= outerSq) {
                    val dist = sqrt(distSq)
                    val dabAlpha = if (dist <= innerRadius) {
                        flow
                    } else {
                        // Smoothstep falloff
                        val edge = (outerRadius - dist) / (outerRadius - innerRadius).coerceAtLeast(1e-4f)
                        val smooth = edge * edge * (3.0f - 2.0f * edge)
                        smooth * flow
                    }

                    val idx = rowOffset + px
                    val currentAlpha = alphaBuffer[idx]
                    // Accumulate alpha: a_out = a_current + a_dab * (1 - a_current)
                    alphaBuffer[idx] = (currentAlpha + dabAlpha * (1.0f - currentAlpha)).coerceIn(0.0f, 1.0f)
                }
            }
        }
    }
}
