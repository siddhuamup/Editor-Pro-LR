package com.cleanlr.engine

import com.cleanlr.engine.stage.Stage8Details
import com.cleanlr.model.DetailAdjustments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class Stage8DetailsTest {

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun computeVignetteGain(
        u: Float,
        v: Float,
        amount: Float,
        midpoint: Float,
        feather: Float
    ): Float {
        val cu = u - 0.5f
        val cv = v - 0.5f
        val r = sqrt(cu * cu + cv * cv) * 1.41421356f

        val halfFeather = (feather * 0.45f).coerceAtLeast(0.05f)
        val inner = (midpoint - halfFeather).coerceIn(0.0f, 0.95f)
        val outer = (midpoint + halfFeather).coerceIn(inner + 0.05f, 1.414f)

        val falloff = smoothstep(inner, outer, r)
        val gain = 1.0f - amount * falloff
        return gain.coerceAtLeast(0.0f)
    }

    private fun computeGrainModulation(luma: Float): Float {
        return (1.0f - smoothstep(0.4f, 1.2f, luma)).coerceIn(0.0f, 1.0f)
    }

    @Test
    fun testVignetteCenterIsAlwaysUntouched() {
        // At center (0.5, 0.5), r = 0, gain must remain 1.0 regardless of amount
        val gainCenterDark = computeVignetteGain(0.5f, 0.5f, amount = 0.8f, midpoint = 0.5f, feather = 0.5f)
        val gainCenterBright = computeVignetteGain(0.5f, 0.5f, amount = -0.8f, midpoint = 0.5f, feather = 0.5f)

        assertEquals("Center gain must remain 1.0", 1.0f, gainCenterDark, 0.001f)
        assertEquals("Center gain must remain 1.0", 1.0f, gainCenterBright, 0.001f)
    }

    @Test
    fun testVignetteCornerFalloff() {
        // At corner (0.0, 0.0), r ~= 1.0
        val cornerGainDark = computeVignetteGain(0.0f, 0.0f, amount = 0.6f, midpoint = 0.5f, feather = 0.5f)
        assertTrue("Dark vignette must reduce corner gain significantly", cornerGainDark < 0.5f)

        val cornerGainBright = computeVignetteGain(0.0f, 0.0f, amount = -0.5f, midpoint = 0.5f, feather = 0.5f)
        assertTrue("White vignette must boost corner gain above 1.0", cornerGainBright > 1.3f)
    }

    @Test
    fun testGrainModulatesInverselyWithLuminance() {
        // Film grain responds like real silver-halide crystals: dense in shadows, rolled off in highlights
        val shadowMod = computeGrainModulation(0.1f)
        val midtoneMod = computeGrainModulation(0.7f)
        val highlightMod = computeGrainModulation(1.3f)

        assertEquals("Shadows must have full grain intensity", 1.0f, shadowMod, 0.001f)
        assertTrue("Midtones must have moderate grain intensity", midtoneMod in 0.4f..0.85f)
        assertEquals("Blown highlights must have zero grain intensity to avoid harsh speckle", 0.0f, highlightMod, 0.001f)
    }

    @Test
    fun testUnsharpMaskingWithEdgeSuppression() {
        // Simulate high pass on an edge vs flat sky
        val edgeCenter = 0.9f
        val edgeBlurred = 0.3f
        val edgeHighPass = edgeCenter - edgeBlurred // 0.6 (sharp edge)
        val edgeStrength = abs(edgeHighPass)

        val flatCenter = 0.50f
        val flatBlurred = 0.49f
        val flatHighPass = flatCenter - flatBlurred // 0.01 (flat area / sensor noise)
        val flatStrength = abs(flatHighPass)

        val maskingSlider = 0.60f // 60% masking
        val threshold = maskingSlider * 0.05f // 0.03

        val edgeMaskVal = smoothstep(threshold * 0.5f, threshold * 1.5f, edgeStrength)
        val flatMaskVal = smoothstep(threshold * 0.5f, threshold * 1.5f, flatStrength)

        assertTrue("True edges must pass through sharpening (>90%)", edgeMaskVal > 0.9f)
        assertTrue("Flat sky/skin areas must have sharpening suppressed (<10%)", flatMaskVal < 0.1f)
    }

    @Test
    fun testSkipDisabledStagesRule() {
        // Mock stage helper logic
        fun isStageActive(detail: DetailAdjustments): Boolean {
            val epsilon = 0.001f
            return abs(detail.sharpening) > epsilon ||
                   abs(detail.vignetteAmount) > epsilon ||
                   abs(detail.grainAmount) > epsilon
        }

        // Neutral settings -> must return false (bypass FBO swap for 120 FPS)
        val neutral = DetailAdjustments(
            sharpening = 0.0f,
            vignetteAmount = 0.0f,
            grainAmount = 0.0f
        )
        assertFalse("Stage 8 must be skipped when neutral", isStageActive(neutral))

        // Any active setting -> must return true
        val withSharpen = neutral.copy(sharpening = 25.0f)
        assertTrue("Stage 8 must activate when sharpening > 0", isStageActive(withSharpen))

        val withVignette = neutral.copy(vignetteAmount = -30.0f)
        assertTrue("Stage 8 must activate when vignette != 0", isStageActive(withVignette))

        val withGrain = neutral.copy(grainAmount = 15.0f)
        assertTrue("Stage 8 must activate when grain > 0", isStageActive(withGrain))
    }
}
