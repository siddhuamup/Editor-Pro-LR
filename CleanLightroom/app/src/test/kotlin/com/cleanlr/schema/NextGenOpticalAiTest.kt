package com.cleanlr.schema

import com.cleanlr.ai.AiVisionPipeline
import com.cleanlr.ai.HardwareDelegate
import com.cleanlr.ai.ModelManager
import com.cleanlr.ai.ModelRegistry
import com.cleanlr.ai.ModelStatus
import com.cleanlr.ai.SequentialOnnxRunner
import com.cleanlr.engine.GenerativeAiBackend
import com.cleanlr.engine.GenerativeAiConfig
import com.cleanlr.engine.stage.StageOpticalLensBlur
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Verifies CleanLR Next-Gen AI & 5-Stage Optical Lens Blur Engine:
 * - Phase 1: 6-Model BYOM Registry, SHA-256 verification, Strict Sequential ONNX Mutex (peak == 1),
 *   Hardware Delegate Priority Chain (QNN -> NNAPI -> GPU -> XNNPACK).
 * - Phase 2 & 3: Physical CoC ground-plane continuity (CoC(S) == 0), Foreground Color Decontamination,
 *   Subject Sensor Noise Sigma measurement, and all 7 Aperture Blade shapes.
 * - Phase 4: LAN Wi-Fi Bridge (FLUX.2 Klein 4B GGUF) & Zero-API-Cost readiness.
 */
class NextGenOpticalAiTest {

    @Test
    fun testByomModelRegistryCompletenessAndSha256() {
        val ids = ModelRegistry.ALL_MODELS.map { it.id }.toSet()
        assertEquals(8, ModelRegistry.ALL_MODELS.size)
        assertEquals(7, ModelRegistry.BUNDLED_MOBILE_MODELS.size)
        assertTrue(ModelRegistry.ID_MOBILESAM in ids)
        assertTrue(ModelRegistry.ID_BIREFNET_LITE in ids)
        assertTrue(ModelRegistry.ID_DEPTH_ANYTHING_V2 in ids)
        assertTrue(ModelRegistry.ID_LAMA in ids)
        assertTrue(ModelRegistry.ID_SD_TURBO_INPAINT in ids)
        assertTrue(ModelRegistry.ID_SD_VAE_ENCODER in ids)
        assertTrue(ModelRegistry.ID_SD_VAE_DECODER in ids)
        assertTrue(ModelRegistry.ID_FLUX2_KLEIN in ids)
        assertEquals(
            setOf(
                "mobilesam_int8.onnx",
                "birefnet_lite_fp16.onnx",
                "depth_anything_v2_small_fp16.onnx",
                "lama_fp16.onnx",
                "sd_turbo_inpaint_int8.onnx",
                "sd_vae_encoder_int8.onnx",
                "sd_vae_decoder_int8.onnx"
            ),
            ModelRegistry.BUNDLED_MOBILE_MODELS.map { it.fileName }.toSet()
        )
        assertEquals("BUNDLED_READY", ModelStatus.BUNDLED_READY.name)

        // Verify SDXS scaled-linear alphas_cumprod schedule monotonicity & bounds
        val alphas = AiVisionPipeline.computeScaledLinearAlphasCumprod()
        assertEquals(1000, alphas.size)
        assertTrue("alpha_cumprod[0] must be near 1.0", alphas[0] > 0.99f)
        assertTrue("alpha_cumprod[999] must be near 0.0", alphas[999] < 0.01f)
        assertTrue("alpha_cumprod must be strictly decreasing", alphas[199] > alphas[399] && alphas[399] > alphas[799])

        // Verify deterministic SHA-256 digest helper
        val sha = ModelManager.computeSha256Bytes("CleanLR-BYOM-Test".toByteArray())
        assertEquals(64, sha.length)
        assertEquals(sha, ModelManager.computeSha256Bytes("CleanLR-BYOM-Test".toByteArray()))
    }

    @Test
    fun testOfflineMobileExpandCosineFeatherMaskAndTiling() {
        // 200x200 original photo padded by 40px on all sides -> 280x280
        val mask = AiVisionPipeline.computeCosineFeatherMask(
            fullW = 280,
            fullH = 280,
            origLeft = 40,
            origTop = 40,
            origRight = 240,
            origBottom = 240,
            featherPx = 24
        )
        // Center of original photo must stay 100% preserved (mask == 0.0)
        assertEquals(0.0f, mask[140 * 280 + 140], 0.0001f)
        // Outside original bounds must be 100% outpaint (mask == 1.0)
        assertEquals(1.0f, mask[10 * 280 + 10], 0.0001f)
        // Midpoint of 24px cosine transition (12px inside original edge -> x = 52, y = 140) must be ~0.5
        assertEquals(0.5f, mask[140 * 280 + 52], 0.05f)

        // 512x512 tiled partitioning on a 1200x900 expanded canvas skips pure-interior tiles
        val tiles = AiVisionPipeline.computeExpandTiles(
            fullW = 1200,
            fullH = 900,
            origLeft = 100,
            origTop = 100,
            origRight = 1100,
            origBottom = 800,
            tileSize = 512,
            overlap = 64
        )
        assertTrue("Border-touching 512x512 tiles must be generated", tiles.isNotEmpty())
        assertTrue("All tiles must be <= 512x512", tiles.all { it.width <= 512 && it.height <= 512 })
    }

    @Test
    fun testSequentialOnnxRunnerNeverLoadsTwoModelsConcurrently() = runTest {
        // Verify hardware delegate priority strictly starts with QNN -> NNAPI -> GPU -> XNNPACK
        assertEquals(
            listOf(
                HardwareDelegate.QNN_NPU,
                HardwareDelegate.NNAPI,
                HardwareDelegate.GPU,
                HardwareDelegate.XNNPACK_CPU
            ),
            SequentialOnnxRunner.delegatePriorityOrder
        )

        // Launch two concurrent model calls and confirm Mutex enforces strict sequential execution
        val job1 = async {
            SequentialOnnxRunner.runSequential(ModelRegistry.ID_BIREFNET_LITE, null) {
                Thread.sleep(30)
                "birefnet_done"
            }
        }
        val job2 = async {
            SequentialOnnxRunner.runSequential(ModelRegistry.ID_DEPTH_ANYTHING_V2, null) {
                Thread.sleep(30)
                "depth_done"
            }
        }
        assertEquals("birefnet_done", job1.await())
        assertEquals("depth_done", job2.await())

        assertEquals("Peak concurrent loaded models must never exceed 1", 1, SequentialOnnxRunner.peakConcurrentModels)
        assertNull("Session must be closed and cleared after inference", SequentialOnnxRunner.activeLoadedModelId)
    }

    @Test
    fun testPhysicalCircleOfConfusionGroundPlaneContinuity() {
        val focalS = 0.42f // Subject feet & body anchor depth
        val apertureK = 0.032f
        val maxR = 0.036f

        // Floor directly beneath subject's feet (Z == S) -> 100% razor sharp (CoC == 0)
        val cocAtFeet = AiVisionPipeline.computePhysicalCoC(0.42f, focalS, apertureK, maxR)
        assertEquals(0.0f, cocAtFeet, 0.0001f)

        // Midground slightly behind subject -> smooth gradual blur
        val cocMid = AiVisionPipeline.computePhysicalCoC(0.55f, focalS, apertureK, maxR)
        // Far background -> stronger creamy bokeh, clamped to R_max
        val cocFar = AiVisionPipeline.computePhysicalCoC(0.95f, focalS, apertureK, maxR)

        assertTrue("Blur must increase smoothly with depth behind focal plane", cocMid > 0f && cocFar > cocMid)
        assertTrue("CoC must respect R_max optical clamp", cocFar <= maxR + 0.0001f)
    }

    @Test
    fun testForegroundColorDecontaminationRemovesBackgroundSpill() {
        // Suppose pure subject hair is white (240), background is blue-dark (20), and edge pixel has A = 0.5
        // Observed mixed pixel = 0.5 * 240 + 0.5 * 20 = 130
        val decontaminated = AiVisionPipeline.decontaminatePixel(
            observed = 130f,
            cleanBg = 20f,
            alpha = 0.5f
        )
        assertEquals("Decontamination must recover true foreground color (~240)", 240f, decontaminated, 1.5f)
    }

    @Test
    fun testSubjectNoiseSigmaDistinguishesCleanVsGrainySensor() {
        val w = 32
        val h = 32
        val alpha = FloatArray(w * h) { 1.0f }
        val cleanLuma = FloatArray(w * h) { 0.5f }
        val noisyLuma = FloatArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            0.5f + if (((x + y) and 1) == 0) 0.025f else -0.025f
        }

        val sigmaClean = AiVisionPipeline.measureNoiseSigmaFromLuma(cleanLuma, alpha, w, h)
        val sigmaNoisy = AiVisionPipeline.measureNoiseSigmaFromLuma(noisyLuma, alpha, w, h)

        assertTrue("Noisy subject ($sigmaNoisy) must measure higher sensor grain sigma than clean ($sigmaClean)", sigmaNoisy > sigmaClean * 2f)
    }

    @Test
    fun testAllSevenApertureBladesAndLanBridgeConfig() {
        assertEquals(0, StageOpticalLensBlur.bokehShapeToInt("Circle"))
        assertEquals(1, StageOpticalLensBlur.bokehShapeToInt("Bubble"))
        assertEquals(2, StageOpticalLensBlur.bokehShapeToInt("5-blade"))
        assertEquals(3, StageOpticalLensBlur.bokehShapeToInt("7-blade"))
        assertEquals(4, StageOpticalLensBlur.bokehShapeToInt("9-blade"))
        assertEquals(5, StageOpticalLensBlur.bokehShapeToInt("Oval"))
        assertEquals(6, StageOpticalLensBlur.bokehShapeToInt("Cat eye"))

        val lanCfg = GenerativeAiConfig(
            backend = GenerativeAiBackend.LOCAL_LAN_BRIDGE,
            apiKey = "",
            endpointUrl = "http://192.168.1.42:8000/v1/flux-fill"
        )
        assertTrue("Zero-cost LAN Bridge must be ready without any cloud API key", lanCfg.isReady)
    }

    @Test
    fun testHealCloneRemoveModesFeatherMaskAndCloneStamp() {
        assertEquals(
            listOf("Remove", "Heal", "Clone"),
            com.cleanlr.engine.RemoveToolMode.entries.map { it.label }
        )

        val w = 100
        val h = 100
        val stroke = listOf(com.cleanlr.model.BrushStrokePoint(0.25f, 0.50f, radius = 40f))
        val bounds = com.cleanlr.engine.HealCloneEngine.computeStrokesPixelBounds(stroke, w, h, padPx = 4)
        val roiW = bounds[2] - bounds[0]
        val roiH = bounds[3] - bounds[1]
        val mask = com.cleanlr.engine.HealCloneEngine.computeFeatheredStrokeMask(
            strokes = stroke, imageW = w, imageH = h,
            roiLeft = bounds[0], roiTop = bounds[1], roiW = roiW, roiH = roiH,
            featherNorm = 0.5f
        )
        // Center pixel of stroke must have full weight 1.0
        val centerIdx = (50 - bounds[1]) * roiW + (25 - bounds[0])
        assertEquals(1.0f, mask[centerIdx], 0.01f)

        // Clone stamp from right half (pure green 0x00FF00) onto left half (pure red 0xFF0000) at 100% opacity
        val origPixels = IntArray(w * h) { idx ->
            val x = idx % w
            if (x < 50) (0xFF shl 24) or (255 shl 16) else (0xFF shl 24) or (255 shl 8)
        }
        val cloned = com.cleanlr.engine.HealCloneEngine.applyClonePixelBuffer(
            origPixels = origPixels, imageW = w, imageH = h,
            roiLeft = bounds[0], roiTop = bounds[1], roiW = roiW, roiH = roiH,
            featherMask = mask, dx = 50, dy = 0, opacityNorm = 1.0f
        )
        val stampedPixel = cloned[50 * w + 25]
        assertEquals("Clone must transfer green channel cleanly", 255, (stampedPixel shr 8) and 0xFF)
        assertEquals("Clone must overwrite red channel at 100% opacity", 0, (stampedPixel shr 16) and 0xFF)
    }
}
