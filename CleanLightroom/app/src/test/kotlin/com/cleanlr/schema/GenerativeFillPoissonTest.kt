package com.cleanlr.schema

import com.cleanlr.engine.GenerativeAiBackend
import com.cleanlr.engine.GenerativeAiConfig
import com.cleanlr.engine.PoissonBlender
import com.cleanlr.engine.RoiPatchExtractor
import com.cleanlr.model.BrushStrokePoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Verifies Zero-Bloat Generative Remove & Expand Pipeline:
 * 1. Gradient-domain Poisson Blending preserves 100% original pixels outside mask
 *    and seamlessly adapts patch exposure/lighting at the boundary.
 * 2. Minimal-bandwidth ROI Crop+Mask extractor aligns dimensions to FLUX.1 16px VAE multiples.
 * 3. Multi-provider FLUX.1 Fill config validation (Fal.ai, Replicate, RunPod, Self-Hosted GPU).
 */
class GenerativeFillPoissonTest {

    @Test
    fun testPoissonBlendingPreservesOriginalPixelsAndEliminatesSeam() {
        val w = 32
        val h = 32
        val n = w * h

        // Original photo has constant luminance 180
        val dst = FloatArray(n) { 180f }
        // Generated patch has slightly mismatched global exposure (luminance 120) + a centered +20 texture bump
        val src = FloatArray(n) { idx ->
            val x = idx % w
            val y = idx / w
            if (x in 14..17 && y in 14..17) 140f else 120f
        }
        // Circular/square mask in the middle (8..23)
        val mask = BooleanArray(n) { idx ->
            val x = idx % w
            val y = idx / w
            x in 8..23 && y in 8..23
        }

        val blended = PoissonBlender.solveChannel(dst, src, mask, w, h, iterations = 150)

        // 1. Every single untouched pixel outside the mask MUST be 100% bit-exact (180f)
        for (i in 0 until n) {
            if (!mask[i]) {
                assertEquals(180f, blended[i], 0.0001f)
            }
        }

        // 2. Flat region inside mask must automatically shift from 120 -> ~180 to eliminate seam!
        val flatInside = blended[10 * w + 10]
        assertEquals("Expected lighting transfer to match 180 boundary", 180f, flatInside, 2.5f)

        // 3. Internal texture bump (+20 gradient in source patch) must be preserved on top of 180 -> ~200!
        val bumpCenter = blended[15 * w + 15]
        assertEquals("Expected internal texture gradient preserved at ~200", 200f, bumpCenter, 3.0f)
    }

    @Test
    fun testRoiExtractorMinimizesBandwidthAndAlignsToFlux16Px() {
        // High-res 4000x3000 photo with a small brush stroke near center
        val strokes = listOf(
            BrushStrokePoint(x = 0.50f, y = 0.50f, radius = 12f),
            BrushStrokePoint(x = 0.51f, y = 0.51f, radius = 12f)
        )
        val roi = RoiPatchExtractor.computeContextRoi(strokes, imageWidth = 4000, imageHeight = 3000)
        assertNotNull(roi)
        roi!!

        // Must be vastly smaller than full 4000x3000 image (Zero-bloat bandwidth optimization!)
        assertTrue("ROI width should be tightly cropped (< 1000px), got ${roi.width}", roi.width < 1000)
        assertTrue("ROI height should be tightly cropped (< 1000px), got ${roi.height}", roi.height < 1000)

        // Must be snapped to multiples of 16px for FLUX.1 Fill VAE
        assertEquals(0, roi.width % 16)
        assertEquals(0, roi.height % 16)
        assertTrue(roi.left >= 0 && roi.right <= 4000)
        assertTrue(roi.top >= 0 && roi.bottom <= 3000)
    }

    @Test
    fun testGenerativeAiBackendReadinessRules() {
        val unconfiguredFal = GenerativeAiConfig(backend = GenerativeAiBackend.FAL_AI, apiKey = "")
        assertFalse(unconfiguredFal.isReady)

        val configuredFal = unconfiguredFal.copy(apiKey = "fal_live_key_123")
        assertTrue(configuredFal.isReady)
        assertTrue(configuredFal.usePoissonBlending)

        val customSelfHosted = GenerativeAiConfig(
            backend = GenerativeAiBackend.CUSTOM_GPU_SERVER,
            apiKey = "",
            endpointUrl = "http://192.168.1.50:8000/v1/flux-fill"
        )
        assertTrue(customSelfHosted.isReady)
    }
}
