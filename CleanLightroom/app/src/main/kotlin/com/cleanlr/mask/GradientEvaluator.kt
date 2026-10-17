package com.cleanlr.mask

import com.cleanlr.model.SubMask
import kotlin.math.*

/**
 * Task T6: Parametric Linear and Radial Gradient Evaluator
 */
object GradientEvaluator {

    /**
     * Evaluates a Linear Gradient submask onto a 1-channel alpha buffer.
     */
    fun evaluateLinearGradient(
        mask: SubMask.LinearGradient,
        width: Int,
        height: Int,
        outBuffer: FloatArray = FloatArray(width * height)
    ): FloatArray {
        val x0 = mask.startX * width
        val y0 = mask.startY * height
        val x1 = mask.endX * width
        val y1 = mask.endY * height

        val dx = x1 - x0
        val dy = y1 - y0
        val lenSq = (dx * dx + dy * dy).coerceAtLeast(1e-4f)

        for (y in 0 until height) {
            val py = y.toFloat()
            val rowOffset = y * width
            for (x in 0 until width) {
                val px = x.toFloat()

                // Project point onto vector (x1 - x0, y1 - y0)
                val dot = (px - x0) * dx + (py - y0) * dy
                val t = (dot / lenSq).coerceIn(0.0f, 1.0f)

                // Smoothstep transition
                val alpha = t * t * (3.0f - 2.0f * t)
                outBuffer[rowOffset + x] = if (mask.isInverted) 1.0f - alpha else alpha
            }
        }
        return outBuffer
    }

    /**
     * Evaluates an Elliptical Rotated Radial Gradient submask onto a 1-channel alpha buffer.
     */
    fun evaluateRadialGradient(
        mask: SubMask.RadialGradient,
        width: Int,
        height: Int,
        outBuffer: FloatArray = FloatArray(width * height)
    ): FloatArray {
        val cx = mask.centerX * width
        val cy = mask.centerY * height
        val rx = (mask.radiusX * width).coerceAtLeast(1.0f)
        val ry = (mask.radiusY * height).coerceAtLeast(1.0f)

        val radAngle = Math.toRadians(-mask.rotationDegrees.toDouble())
        val cosTheta = cos(radAngle).toFloat()
        val sinTheta = sin(radAngle).toFloat()

        val feather = (mask.feather / 100.0f).coerceIn(0.0f, 1.0f)
        val innerT = 1.0f - feather

        for (y in 0 until height) {
            val py = y.toFloat()
            val rowOffset = y * width
            for (x in 0 until width) {
                val px = x.toFloat()

                // Translate to center
                val tx = px - cx
                val ty = py - cy

                // Rotate
                val rotX = tx * cosTheta - ty * sinTheta
                val rotY = tx * sinTheta + ty * cosTheta

                // Elliptical distance ratio
                val dist = sqrt((rotX / rx) * (rotX / rx) + (rotY / ry) * (rotY / ry))

                val rawAlpha = when {
                    dist <= innerT -> 1.0f
                    dist >= 1.0f -> 0.0f
                    else -> {
                        val edge = (1.0f - dist) / (1.0f - innerT).coerceAtLeast(1e-4f)
                        edge * edge * (3.0f - 2.0f * edge)
                    }
                }

                outBuffer[rowOffset + x] = if (mask.isInverted) 1.0f - rawAlpha else rawAlpha
            }
        }
        return outBuffer
    }
}
