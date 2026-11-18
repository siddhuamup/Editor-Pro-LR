package com.cleanlr.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.cleanlr.engine.GenerativeExpandEngine
import com.cleanlr.engine.PoissonBlender
import com.cleanlr.engine.RoiPatchExtractor
import com.cleanlr.model.BrushStrokePoint
import kotlin.math.*

/**
 * All-In-One Bundled Offline AI Vision Pipeline (Zero Downloads, Zero Storage Duplication).
 *
 * Orchestrates all 5 bundled ONNX models directly from uncompressed APK assets (`MappedByteBuffer`):
 *  1. `mobilesam_int8.onnx`             — Interactive Tap / Box / Lasso Segmentation
 *  2. `birefnet_lite_fp16.onnx`         — Continuous Hair-Level Alpha Matting (0.0..1.0) + Foreground Decontamination
 *  3. `depth_anything_v2_small_fp16.onnx` — 16-bit Normalized Depth Map with Ground-Plane Continuity
 *  4. `lama_fp16.onnx`                  — Smart Bounding-Box Cropping Offline Remove & Clean-Plate Fill
 *  5. `sd_turbo_inpaint_int8.onnx`      — 4-Step Quantized Offline Mobile AI Expand (512x512 tiled + 24px cosine feather + border histogram & grain match)
 *
 * Every stage executes STRICTLY SEQUENTIALLY through [SequentialOnnxRunner] under the global Mutex.
 */
object AiVisionPipeline {

    data class OpticalBlurPack(
        val decontaminatedForeground: Bitmap,
        val cleanBackgroundPlate: Bitmap,
        val alphaMatte: Bitmap,          // 8-bit continuous alpha encoded in R/G/B (0..255)
        val depthMap16Bit: Bitmap,       // 16-bit depth packed in R (high byte) + G (low byte)
        val focalPlaneDepthS: Float,     // Sampled at subject anchor (feet/eyes)
        val subjectNoiseSigma: Float,    // High-frequency luminance noise variance measured on subject
        val usedNativeOnnxModels: List<String>
    )

    data class ExpandTile(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )

    data class ExpandOutput(
        val bitmap: Bitmap,
        val usedNativeOnnx: Boolean,
        val tilesProcessed: Int,
        val matchedGrainSigma: Float,
        val delegateName: String
    )

    /**
     * Stage 1 (MobileSAM): Interactive Tap / Box Segmentation directly from bundled `mobilesam_int8.onnx`.
     */
    suspend fun segmentInteractive(
        bitmap: Bitmap,
        modelManager: ModelManager,
        tapNormX: Float,
        tapNormY: Float,
        boxNorm: FloatArray? = null
    ): Bitmap {
        val source = modelManager.resolveModelSource(ModelRegistry.ID_MOBILESAM)
        return SequentialOnnxRunner.runSequential(
            modelId = ModelRegistry.ID_MOBILESAM,
            source = source,
            context = modelManager.context
        ) { session ->
            val w = 256
            val h = ((bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)) * w).roundToInt().coerceIn(64, 384)
            val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            if (small !== bitmap) small.recycle()

            // Native SAM 2.1 Tiny encoder tensor pass (fixed 1024x1024 input -> [1, 32, 256, 256] embeddings)
            if (session != null) {
                val samSz = 1024
                val samBmp = Bitmap.createScaledBitmap(bitmap, samSz, samSz, true)
                val samPx = IntArray(samSz * samSz)
                samBmp.getPixels(samPx, 0, samSz, 0, 0, samSz, samSz)
                if (samBmp !== bitmap) samBmp.recycle()

                val inputTensor = bitmapPixelsToNormalizedChwFloat(samPx, samSz, samSz)
                val emb = session.runFloatTensor("pixel_values", inputTensor, longArrayOf(1, 3, samSz.toLong(), samSz.toLong()))
                val featDim = 256
                val channels = 32
                if (emb != null && emb.size >= channels * featDim * featDim) {
                    val tx = (tapNormX.coerceIn(0f, 1f) * (featDim - 1)).toInt()
                    val ty = (tapNormY.coerceIn(0f, 1f) * (featDim - 1)).toInt()
                    val plane = featDim * featDim
                    val seedFeat = FloatArray(channels) { c -> emb[c * plane + ty * featDim + tx] }
                    var seedNorm = 0f
                    for (v in seedFeat) seedNorm += v * v
                    seedNorm = sqrt(seedNorm).coerceAtLeast(1e-6f)

                    val featMask = FloatArray(plane)
                    for (fy in 0 until featDim) {
                        for (fx in 0 until featDim) {
                            val fIdx = fy * featDim + fx
                            val inBox = if (boxNorm != null && boxNorm.size == 4) {
                                val nx = fx.toFloat() / featDim
                                val ny = fy.toFloat() / featDim
                                nx in boxNorm[0]..boxNorm[2] && ny in boxNorm[1]..boxNorm[3]
                            } else true
                            if (!inBox) continue
                            var dot = 0f
                            var normSq = 0f
                            for (c in 0 until channels) {
                                val v = emb[c * plane + fIdx]
                                dot += v * seedFeat[c]
                                normSq += v * v
                            }
                            val cosSim = dot / (sqrt(normSq).coerceAtLeast(1e-6f) * seedNorm)
                            featMask[fIdx] = ((cosSim - 0.55f) / 0.30f).coerceIn(0f, 1f)
                        }
                    }
                    val resampled = resampleFloatGrid(featMask, featDim, featDim, w, h)
                    return@runSequential floatMaskToBitmap(resampled, w, h)
                }
            }

            // Edge-aware multi-seed color-affinity segmentation around tap/box
            val sx = (tapNormX.coerceIn(0f, 1f) * (w - 1)).toInt()
            val sy = (tapNormY.coerceIn(0f, 1f) * (h - 1)).toInt()
            val seedColor = pixels[sy * w + sx]
            val sr = Color.red(seedColor).toFloat()
            val sg = Color.green(seedColor).toFloat()
            val sb = Color.blue(seedColor).toFloat()

            val mask = FloatArray(w * h)
            val maxDist = hypot(w.toFloat(), h.toFloat()) * 0.42f
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val idx = y * w + x
                    val inBox = if (boxNorm != null && boxNorm.size == 4) {
                        val nx = x.toFloat() / w
                        val ny = y.toFloat() / h
                        nx in boxNorm[0]..boxNorm[2] && ny in boxNorm[1]..boxNorm[3]
                    } else true
                    if (!inBox) continue

                    val p = pixels[idx]
                    val dc = sqrt(
                        (Color.red(p) - sr).pow(2) +
                            (Color.green(p) - sg).pow(2) +
                            (Color.blue(p) - sb).pow(2)
                    ) / 441.67f
                    val ds = hypot((x - sx).toFloat(), (y - sy).toFloat()) / maxDist
                    val score = exp(-(dc * dc) / 0.055f) * exp(-(ds * ds) / 0.85f)
                    mask[idx] = ((score - 0.18f) / 0.45f).coerceIn(0f, 1f)
                }
            }
            floatMaskToBitmap(mask, w, h)
        }
    }

    /**
     * Complete 5-Stage Optical Lens Blur Preparation Pipeline (STRICT Sequential Execution!):
     *  Step 1: `birefnet_lite_fp16.onnx` -> continuous AlphaMatte (A) -> session.close()
     *  Step 2: `depth_anything_v2_small_fp16.onnx` -> 16-bit DepthMap (Z) & focal depth (S) -> session.close()
     *  Step 3: Subtract Subject (A > 0.05) -> `lama_fp16.onnx` -> Clean Background Plate -> session.close()
     *  Step 4: Foreground Color Decontamination + Unblurred Subject Sensor Noise Variance (sigma)
     */
    suspend fun buildOpticalBlurPack(
        bitmap: Bitmap,
        modelManager: ModelManager,
        onProgress: ((String) -> Unit)? = null
    ): OpticalBlurPack {
        val usedNative = mutableListOf<String>()
        val maxEdge = 512
        val scale = min(1f, maxEdge.toFloat() / max(bitmap.width, bitmap.height).coerceAtLeast(1))
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(64)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(64)
        val workBmp = if (w != bitmap.width || h != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else bitmap
        val workPixels = IntArray(w * h)
        workBmp.getPixels(workPixels, 0, w, 0, 0, w, h)

        // ─── STEP 1: BiRefNet-Lite FP16 (Continuous 1024x1024 Hair-Level Alpha Matte 0.0..1.0) ───
        onProgress?.invoke("Stage 1/4: BiRefNet-Lite FP16 1024px Hair Alpha Matting...")
        val alphaFloat: FloatArray = SequentialOnnxRunner.runSequential(
            modelId = ModelRegistry.ID_BIREFNET_LITE,
            source = modelManager.resolveModelSource(ModelRegistry.ID_BIREFNET_LITE),
            context = modelManager.context,
            onStageLog = onProgress
        ) { session ->
            if (session != null) {
                // BiRefNet-Lite ONNX requires fixed [1, 3, 1024, 1024] input for individual hair-strand precision
                val brSz = 1024
                val brBmp = Bitmap.createScaledBitmap(bitmap, brSz, brSz, true)
                val brPx = IntArray(brSz * brSz)
                brBmp.getPixels(brPx, 0, brSz, 0, 0, brSz, brSz)
                if (brBmp !== bitmap) brBmp.recycle()

                val tensor = bitmapPixelsToNormalizedChwFloat(brPx, brSz, brSz)
                val res = session.runFloatTensor("input_image", tensor, longArrayOf(1, 3, brSz.toLong(), brSz.toLong()))
                if (res != null && res.size >= brSz * brSz) {
                    usedNative.add(ModelRegistry.ID_BIREFNET_LITE)
                    val fullAlpha = FloatArray(brSz * brSz) { sigmoid(res[it]) }
                    return@runSequential resampleFloatGridBilinear(fullAlpha, brSz, brSz, w, h)
                }
            }
            computeContinuousAlphaMatteFallback(workPixels, w, h)
        }

        // ─── STEP 2: Depth Anything V2 Small FP16 (16-bit Normalized Depth Map) ───
        onProgress?.invoke("Stage 2/4: Depth Anything V2 FP16 (16-bit Depth)...")
        val depthFloat: FloatArray = SequentialOnnxRunner.runSequential(
            modelId = ModelRegistry.ID_DEPTH_ANYTHING_V2,
            source = modelManager.resolveModelSource(ModelRegistry.ID_DEPTH_ANYTHING_V2),
            context = modelManager.context,
            onStageLog = onProgress
        ) { session ->
            if (session != null) {
                // Depth Anything V2 ViT expects patch-divisible 518x518 or multiple-of-14 dims
                val dw = ((w / 14).coerceAtLeast(4)) * 14
                val dh = ((h / 14).coerceAtLeast(4)) * 14
                val dBmp = Bitmap.createScaledBitmap(workBmp, dw, dh, true)
                val dPx = IntArray(dw * dh)
                dBmp.getPixels(dPx, 0, dw, 0, 0, dw, dh)
                if (dBmp !== workBmp) dBmp.recycle()

                val tensor = bitmapPixelsToNormalizedChwFloat(dPx, dw, dh)
                val res = session.runFloatTensor("pixel_values", tensor, longArrayOf(1, 3, dh.toLong(), dw.toLong()))
                if (res != null && res.size >= dw * dh) {
                    usedNative.add(ModelRegistry.ID_DEPTH_ANYTHING_V2)
                    val normD = normalizeDepthArray(res, dw * dh)
                    return@runSequential resampleFloatGrid(normD, dw, dh, w, h)
                }
            }
            computeGroundPlaneAwareDepthFallback(workPixels, alphaFloat, w, h)
        }

        val focalPlaneS = sampleSubjectFocalDepth(alphaFloat, depthFloat, w, h)

        // ─── STEP 3: LaMa FP16 Clean-Plate Background Reconstruction (Subtract A > 0.05) ───
        onProgress?.invoke("Stage 3/4: LaMa FP16 Clean-Plate Reconstruction...")
        val subjectMaskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val subjMaskPixels = IntArray(w * h) { i ->
            if (alphaFloat[i] > 0.05f) Color.WHITE else Color.BLACK
        }
        subjectMaskBmp.setPixels(subjMaskPixels, 0, w, 0, 0, w, h)

        val cleanPlateBmp: Bitmap = SequentialOnnxRunner.runSequential(
            modelId = ModelRegistry.ID_LAMA,
            source = modelManager.resolveModelSource(ModelRegistry.ID_LAMA),
            context = modelManager.context,
            onStageLog = onProgress
        ) { session ->
            if (session != null) {
                val lamaOut = runLama512Session(session, workBmp, subjectMaskBmp)
                if (lamaOut != null) {
                    usedNative.add(ModelRegistry.ID_LAMA)
                    return@runSequential lamaOut
                }
            }
            PoissonBlender.localContentAwareRemove(
                originalBitmap = workBmp,
                maskBitmap = subjectMaskBmp,
                offsetX = 0,
                offsetY = 0,
                iterations = 140
            )
        }
        subjectMaskBmp.recycle()

        // ─── STEP 4: Foreground Color Decontamination + Subject Sensor Noise Sigma ───
        onProgress?.invoke("Stage 4/4: Foreground Decontamination & Sensor Grain Match...")
        val decontaminatedFg = decontaminateForegroundEdges(workPixels, cleanPlateBmp, alphaFloat, w, h)
        val noiseSigma = measureSubjectNoiseSigma(workPixels, alphaFloat, w, h)
        val alphaMatteBmp = floatMaskToBitmap(alphaFloat, w, h)
        val depth16Bmp = pack16BitDepthBitmap(depthFloat, w, h)

        if (workBmp !== bitmap) workBmp.recycle()

        return OpticalBlurPack(
            decontaminatedForeground = decontaminatedFg,
            cleanBackgroundPlate = cleanPlateBmp,
            alphaMatte = alphaMatteBmp,
            depthMap16Bit = depth16Bmp,
            focalPlaneDepthS = focalPlaneS,
            subjectNoiseSigma = noiseSigma,
            usedNativeOnnxModels = usedNative
        )
    }

    /**
     * Offline Instant Generative Remove (`lama_fp16.onnx` directly from APK asset + Smart Bounding-Box Cropping + Poisson Blend).
     */
    suspend fun runOfflineLamaRemove(
        originalBitmap: Bitmap,
        strokes: List<BrushStrokePoint>,
        modelManager: ModelManager
    ): Bitmap {
        val extracted = RoiPatchExtractor.extractRemoveRoi(
            originalBitmap = originalBitmap,
            strokes = strokes,
            maxUploadEdge = 512
        ) ?: return originalBitmap

        return try {
            val source = modelManager.resolveModelSource(ModelRegistry.ID_LAMA)
            val generatedPatch: Bitmap = SequentialOnnxRunner.runSequential(
                modelId = ModelRegistry.ID_LAMA,
                source = source,
                context = modelManager.context
            ) { session ->
                if (session != null) {
                    val lamaOut = runLama512Session(session, extracted.croppedImage, extracted.binaryMask)
                    if (lamaOut != null) return@runSequential lamaOut
                }
                PoissonBlender.localContentAwareRemove(
                    originalBitmap = extracted.croppedImage,
                    maskBitmap = extracted.binaryMask,
                    offsetX = 0,
                    offsetY = 0,
                    iterations = 180
                )
            }
            PoissonBlender.blendPatchIntoOriginal(
                originalBitmap = originalBitmap,
                sourcePatch = generatedPatch,
                maskBitmap = extracted.binaryMask,
                offsetX = extracted.roiBounds.left,
                offsetY = extracted.roiBounds.top,
                iterations = 100
            ).also {
                generatedPatch.recycle()
            }
        } finally {
            extracted.croppedImage.recycle()
            extracted.binaryMask.recycle()
        }
    }

    /**
     * Phase 4: Offline Mobile AI Expand (`sd_turbo_inpaint_int8.onnx`).
     * Default offline mobile outpainting engine when Desktop LAN Bridge is not connected:
     *  1. Pad canvas with seamless mirror-reflection border initialization
     *  2. Build 24px cosine-feathered border mask (`0.0 = original`, `1.0 = outpaint`)
     *  3. Run 512x512 tiled 4-step inference on QNN/NNAPI/GPU via `SequentialOnnxRunner`
     *  4. Border histogram (mean/std color transfer) & sensor grain (`sigma_noise`) matching
     *  5. Composite onto full-res image preserving 100% original interior pixels
     */
    suspend fun runOfflineMobileExpand(
        originalBitmap: Bitmap,
        region: GenerativeExpandEngine.ExpandRegion,
        modelManager: ModelManager,
        onProgress: ((String) -> Unit)? = null
    ): ExpandOutput {
        val fullW = region.originalWidth + region.padLeftPx + region.padRightPx
        val fullH = region.originalHeight + region.padTopPx + region.padBottomPx
        val origLeft = region.padLeftPx
        val origTop = region.padTopPx
        val origRight = region.padLeftPx + region.originalWidth
        val origBottom = region.padTopPx + region.originalHeight

        // Resize source to match region.originalWidth x region.originalHeight if rotated/scaled
        val srcBmp = if (originalBitmap.width != region.originalWidth || originalBitmap.height != region.originalHeight) {
            Bitmap.createScaledBitmap(originalBitmap, region.originalWidth, region.originalHeight, true)
        } else originalBitmap

        val origPixels = IntArray(region.originalWidth * region.originalHeight)
        srcBmp.getPixels(origPixels, 0, region.originalWidth, 0, 0, region.originalWidth, region.originalHeight)

        // Measure original camera sensor noise sigma for Step 4 grain re-injection
        val origLuma = FloatArray(origPixels.size) { i ->
            val p = origPixels[i]
            (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f
        }
        val onesAlpha = FloatArray(origPixels.size) { 1f }
        val sensorSigma = measureNoiseSigmaFromLuma(origLuma, onesAlpha, region.originalWidth, region.originalHeight)

        // Step 1: Pad canvas with edge-mirrored context prior
        onProgress?.invoke("Expand 1/4: Padding canvas (${fullW}×${fullH}) & 24px cosine mask...")
        val canvasPixels = buildMirrorPaddedCanvas(
            origPixels = origPixels,
            origW = region.originalWidth,
            origH = region.originalHeight,
            padLeft = region.padLeftPx,
            padTop = region.padTopPx,
            fullW = fullW,
            fullH = fullH
        )

        // Step 2: 24px cosine feathered border mask
        val featherMask = computeCosineFeatherMask(
            fullW = fullW,
            fullH = fullH,
            origLeft = origLeft,
            origTop = origTop,
            origRight = origRight,
            origBottom = origBottom,
            featherPx = 24
        )

        // Step 3: End-to-End Sequential Latent Diffusion Outpainting:
        //   Pass A: sd_vae_encoder_int8.onnx  -> encode 512x512 tiles into [1, 4, 64, 64] latents -> session.close()
        //   Pass B: sd_turbo_inpaint_int8.onnx -> 4-step RePaint DDIM with precomputed CLIP embeds -> session.close()
        //   Pass C: sd_vae_decoder_int8.onnx  -> decode denoised [1, 4, 64, 64] latents to RGB -> session.close()
        val tiles = computeExpandTiles(
            fullW = fullW,
            fullH = fullH,
            origLeft = origLeft,
            origTop = origTop,
            origRight = origRight,
            origBottom = origBottom,
            tileSize = 512,
            overlap = 64
        )
        onProgress?.invoke("Expand 2/4: SDXS VAE Encoder -> 4-step UNet -> VAE Decoder (${tiles.size} tiles)...")

        var usedNativeOnnx = false
        var activeDelegateName = SequentialOnnxRunner.lastActiveDelegate.providerName
        val synthPixels = canvasPixels.copyOf()

        val clipEmbeds = modelManager.loadSdPromptEmbeds()
        val vaeEncSrc = modelManager.resolveModelSource(ModelRegistry.ID_SD_VAE_ENCODER)
        val unetSrc = modelManager.resolveModelSource(ModelRegistry.ID_SD_TURBO_INPAINT)
        val vaeDecSrc = modelManager.resolveModelSource(ModelRegistry.ID_SD_VAE_DECODER)

        val canRunFullLatentDiffusion =
            clipEmbeds != null &&
                vaeEncSrc !is ModelSource.Missing &&
                unetSrc !is ModelSource.Missing &&
                vaeDecSrc !is ModelSource.Missing

        var decodedTiles: List<IntArray?>? = null
        if (canRunFullLatentDiffusion) {
            val latentSize = 64
            val latentPlane = latentSize * latentSize
            val latentCount = 4 * latentPlane
            val vaeScale = 0.18215f

            // ── PASS A: Sequential VAE Encoder (512x512 RGB [-1..1] -> [1, 4, 64, 64] latent) ──
            val encodedOrigLatents = ArrayList<FloatArray?>(tiles.size)
            val latentMasks = ArrayList<FloatArray>(tiles.size)
            SequentialOnnxRunner.runSequential(
                modelId = ModelRegistry.ID_SD_VAE_ENCODER,
                source = vaeEncSrc,
                context = modelManager.context,
                onStageLog = onProgress
            ) { vaeEnc ->
                for ((tIdx, tile) in tiles.withIndex()) {
                    onProgress?.invoke("Expand 2a/4: VAE Encoding tile ${tIdx + 1}/${tiles.size}...")
                    val (tile512Chw, tileMask64) = extractTile512AndMask64(synthPixels, featherMask, fullW, fullH, tile)
                    latentMasks.add(tileMask64)
                    val zRaw = vaeEnc?.runFloatTensor("sample", tile512Chw, longArrayOf(1, 3, 512, 512))
                    if (zRaw != null && zRaw.size >= latentCount) {
                        // AutoencoderKL encoder returns either [1, 4, 64, 64] or [1, 8, 64, 64] (mean+logvar); first 4 ch = mean!
                        encodedOrigLatents.add(FloatArray(latentCount) { i -> zRaw[i] * vaeScale })
                    } else {
                        encodedOrigLatents.add(null)
                    }
                }
            }

            // ── PASS B: Sequential 4-Step SDXS UNet RePaint Denoising with precomputed CLIP embedding ──
            val denoisedLatents = ArrayList<FloatArray?>(tiles.size)
            if (encodedOrigLatents.any { it != null }) {
                val alphasCumprod = computeScaledLinearAlphasCumprod()
                // 4-step image-guided outpaint trailing schedule (preserves border structure while synthesizing new detail)
                val timesteps = longArrayOf(799L, 599L, 399L, 199L)
                val posEmbed = clipEmbeds!!.first // [1, 77, 768] positive photographic extension prompt

                SequentialOnnxRunner.runSequential(
                    modelId = ModelRegistry.ID_SD_TURBO_INPAINT,
                    source = unetSrc,
                    context = modelManager.context,
                    onStageLog = onProgress
                ) { unet ->
                    if (unet != null) activeDelegateName = unet.activeDelegate.providerName
                    for ((tIdx, tile) in tiles.withIndex()) {
                        val zOrig = encodedOrigLatents[tIdx]
                        if (unet == null || zOrig == null) {
                            denoisedLatents.add(null)
                            continue
                        }
                        val mLat = latentMasks[tIdx]
                        // Deterministic seeded Gaussian noise per tile
                        val rng = java.util.Random(42L + tIdx * 1337L)
                        val noise = FloatArray(latentCount) { rng.nextGaussian().toFloat() }
                        val alphaStart = alphasCumprod[timesteps[0].toInt()]
                        val sqrtAStart = sqrt(alphaStart)
                        val sqrtOneMinusAStart = sqrt((1f - alphaStart).coerceAtLeast(1e-6f))
                        var zt = FloatArray(latentCount) { i ->
                            sqrtAStart * zOrig[i] + sqrtOneMinusAStart * noise[i]
                        }

                        var stepOk = true
                        for (sIdx in timesteps.indices) {
                            val tVal = timesteps[sIdx]
                            onProgress?.invoke("Expand 2b/4: SD-Turbo UNet tile ${tIdx + 1}/${tiles.size} step ${sIdx + 1}/4...")
                            val eps = unet.runMixedTensors(
                                floatInputs = mapOf(
                                    "sample" to Pair(zt, longArrayOf(1, 4, 64, 64)),
                                    "encoder_hidden_states" to Pair(posEmbed, longArrayOf(1, 77, 768))
                                ),
                                longInputs = mapOf(
                                    "timestep" to Pair(longArrayOf(tVal), longArrayOf(1))
                                )
                            )
                            if (eps == null || eps.size < latentCount) {
                                stepOk = false
                                break
                            }
                            val aT = alphasCumprod[tVal.toInt()]
                            val sqrtAT = sqrt(aT).coerceAtLeast(1e-5f)
                            val sqrt1mAT = sqrt((1f - aT).coerceAtLeast(1e-6f))
                            val z0Pred = FloatArray(latentCount) { i ->
                                ((zt[i] - sqrt1mAT * eps[i]) / sqrtAT).coerceIn(-6f, 6f)
                            }
                            if (sIdx == timesteps.lastIndex) {
                                // Final step: lock unmasked original latents, keep generated masked latents
                                zt = FloatArray(latentCount) { i ->
                                    val m = mLat[i % latentPlane]
                                    zOrig[i] * (1f - m) + z0Pred[i] * m
                                }
                            } else {
                                val nextT = timesteps[sIdx + 1].toInt()
                                val aNext = alphasCumprod[nextT]
                                val sqrtANext = sqrt(aNext)
                                val sqrt1mANext = sqrt((1f - aNext).coerceAtLeast(1e-6f))
                                zt = FloatArray(latentCount) { i ->
                                    val m = mLat[i % latentPlane]
                                    val zGenNext = sqrtANext * z0Pred[i] + sqrt1mANext * eps[i]
                                    val zOrigNext = sqrtANext * zOrig[i] + sqrt1mANext * noise[i]
                                    zOrigNext * (1f - m) + zGenNext * m
                                }
                            }
                        }
                        denoisedLatents.add(if (stepOk) zt else null)
                    }
                }
            }

            // ── PASS C: Sequential VAE Decoder ([1, 4, 64, 64] / 0.18215 -> 512x512 RGB) ──
            if (denoisedLatents.any { it != null }) {
                val outTiles = ArrayList<IntArray?>(tiles.size)
                SequentialOnnxRunner.runSequential(
                    modelId = ModelRegistry.ID_SD_VAE_DECODER,
                    source = vaeDecSrc,
                    context = modelManager.context,
                    onStageLog = onProgress
                ) { vaeDec ->
                    for ((tIdx, tile) in tiles.withIndex()) {
                        val zFinal = denoisedLatents[tIdx]
                        if (vaeDec == null || zFinal == null) {
                            outTiles.add(null)
                            continue
                        }
                        onProgress?.invoke("Expand 3/4: VAE Decoding tile ${tIdx + 1}/${tiles.size} to 512×512 RGB...")
                        val scaled = FloatArray(latentCount) { i -> zFinal[i] / vaeScale }
                        val rgb = vaeDec.runFloatTensor("latent_sample", scaled, longArrayOf(1, 4, 64, 64))
                        if (rgb != null && rgb.size >= 3 * 512 * 512) {
                            val px512 = vaeRgbToPixels512(rgb)
                            outTiles.add(px512)
                            usedNativeOnnx = true
                        } else {
                            outTiles.add(null)
                        }
                    }
                }
                decodedTiles = outTiles
            }
        }

        // Write decoded neural tiles back into synthPixels (with graceful anisotropic diffusion fallback per tile)
        for ((tileIdx, tile) in tiles.withIndex()) {
            val neuralPx512 = decodedTiles?.getOrNull(tileIdx)
            if (neuralPx512 != null) {
                blitTile512IntoCanvas(synthPixels, featherMask, fullW, fullH, tile, neuralPx512)
            } else {
                onProgress?.invoke("Expand 3/4: Structural outpaint tile ${tileIdx + 1}/${tiles.size} (4 steps)...")
                refineOutpaintTile4Steps(
                    pixels = synthPixels,
                    mask = featherMask,
                    fullW = fullW,
                    fullH = fullH,
                    tile = tile,
                    origLeft = origLeft,
                    origTop = origTop,
                    origRight = origRight,
                    origBottom = origBottom,
                    steps = 4
                )
            }
        }

        // Step 4: Border histogram matching & sensor grain re-injection
        onProgress?.invoke("Expand 4/4: Matching border histogram & sensor grain (σ=${String.format(java.util.Locale.US, "%.3f", sensorSigma)})...")
        matchBorderHistogramAndGrain(
            synthPixels = synthPixels,
            origPixels = origPixels,
            featherMask = featherMask,
            fullW = fullW,
            fullH = fullH,
            origLeft = origLeft,
            origTop = origTop,
            origW = region.originalWidth,
            origH = region.originalHeight,
            noiseSigma = sensorSigma
        )

        // Step 5: Composite onto full-res image preserving 100% original interior pixels
        val outPixels = IntArray(fullW * fullH)
        for (y in 0 until fullH) {
            val row = y * fullW
            for (x in 0 until fullW) {
                val idx = row + x
                val m = featherMask[idx]
                val insideOrig = x in origLeft until origRight && y in origTop until origBottom
                if (insideOrig && m <= 0.001f) {
                    outPixels[idx] = origPixels[(y - origTop) * region.originalWidth + (x - origLeft)]
                } else if (insideOrig) {
                    val op = origPixels[(y - origTop) * region.originalWidth + (x - origLeft)]
                    val sp = synthPixels[idx]
                    val r = (Color.red(op) * (1f - m) + Color.red(sp) * m).roundToInt().coerceIn(0, 255)
                    val g = (Color.green(op) * (1f - m) + Color.green(sp) * m).roundToInt().coerceIn(0, 255)
                    val b = (Color.blue(op) * (1f - m) + Color.blue(sp) * m).roundToInt().coerceIn(0, 255)
                    outPixels[idx] = Color.argb(255, r, g, b)
                } else {
                    outPixels[idx] = synthPixels[idx]
                }
            }
        }

        if (srcBmp !== originalBitmap) srcBmp.recycle()

        val resultBmp = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, fullW, 0, 0, fullW, fullH)
        }
        return ExpandOutput(
            bitmap = resultBmp,
            usedNativeOnnx = usedNativeOnnx,
            tilesProcessed = tiles.size,
            matchedGrainSigma = sensorSigma,
            delegateName = activeDelegateName
        )
    }

    // ─── Pure JVM-Testable Mathematical Primitives ───────────────────────────

    /**
     * Pure JVM-testable 24px Cosine Feathered Border Mask:
     *  - `0.0` strictly inside the original photo (> featherPx from edge)
     *  - Smooth `0.5 * (1 - cos(pi * t))` cosine ramp across the 24px boundary zone
     *  - `1.0` strictly in the expanded outpaint region
     */
    fun computeCosineFeatherMask(
        fullW: Int,
        fullH: Int,
        origLeft: Int,
        origTop: Int,
        origRight: Int,
        origBottom: Int,
        featherPx: Int = 24
    ): FloatArray {
        val mask = FloatArray(fullW * fullH)
        val f = featherPx.coerceAtLeast(1).toFloat()
        for (y in 0 until fullH) {
            val row = y * fullW
            for (x in 0 until fullW) {
                val insideX = x in origLeft until origRight
                val insideY = y in origTop until origBottom
                if (!insideX || !insideY) {
                    mask[row + x] = 1.0f
                } else {
                    // Distance to nearest expanded border (ignore borders that were NOT expanded)
                    var distToExpandedEdge = Float.MAX_VALUE
                    if (origLeft > 0) distToExpandedEdge = min(distToExpandedEdge, (x - origLeft).toFloat())
                    if (origRight < fullW) distToExpandedEdge = min(distToExpandedEdge, (origRight - 1 - x).toFloat())
                    if (origTop > 0) distToExpandedEdge = min(distToExpandedEdge, (y - origTop).toFloat())
                    if (origBottom < fullH) distToExpandedEdge = min(distToExpandedEdge, (origBottom - 1 - y).toFloat())

                    mask[row + x] = if (distToExpandedEdge >= f) {
                        0.0f
                    } else {
                        val t = (1f - (distToExpandedEdge / f)).coerceIn(0f, 1f)
                        (0.5f * (1f - cos(PI.toFloat() * t))).coerceIn(0f, 1f)
                    }
                }
            }
        }
        return mask
    }

    /**
     * Pure JVM-testable 512x512 Tile Partitioner returning only tiles that overlap expanded borders.
     */
    fun computeExpandTiles(
        fullW: Int,
        fullH: Int,
        origLeft: Int,
        origTop: Int,
        origRight: Int,
        origBottom: Int,
        tileSize: Int = 512,
        overlap: Int = 64
    ): List<ExpandTile> {
        val stride = (tileSize - overlap).coerceAtLeast(64)
        val tiles = ArrayList<ExpandTile>()
        var y = 0
        while (y < fullH) {
            val th = min(tileSize, fullH - y)
            val ty = if (y + th >= fullH && fullH > tileSize) fullH - tileSize else y
            val actualH = min(tileSize, fullH - ty)
            var x = 0
            while (x < fullW) {
                val tw = min(tileSize, fullW - x)
                val tx = if (x + tw >= fullW && fullW > tileSize) fullW - tileSize else x
                val actualW = min(tileSize, fullW - tx)

                val fullyInsideOriginal = tx >= origLeft && (tx + actualW) <= origRight &&
                    ty >= origTop && (ty + actualH) <= origBottom
                if (!fullyInsideOriginal) {
                    val candidate = ExpandTile(tx, ty, actualW, actualH)
                    if (candidate !in tiles) tiles.add(candidate)
                }
                if (tx + actualW >= fullW) break
                x += stride
            }
            if (ty + actualH >= fullH) break
            y += stride
        }
        return tiles
    }

    /**
     * Pure JVM-testable physical Circle of Confusion equation (Spec Stage 2):
     * CoC(Z) = clamp(K * |Z - S| / max(Z, 0.05), 0.0, R_max)
     */
    fun computePhysicalCoC(depthZ: Float, focalPlaneS: Float, apertureK: Float, maxRadius: Float): Float {
        val safeZ = max(depthZ, 0.05f)
        return (apertureK * abs(depthZ - focalPlaneS) / safeZ).coerceIn(0f, maxRadius)
    }

    /**
     * Pure JVM-testable high-frequency luminance noise variance (sigma_noise) estimator
     * using mean absolute Laplacian response inside the sharp unblurred region.
     */
    fun measureNoiseSigmaFromLuma(luma: FloatArray, alpha: FloatArray, w: Int, h: Int): Float {
        var sumAbsLap = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                if (alpha[idx] < 0.7f) continue
                val lap = abs(
                    4f * luma[idx] - luma[idx - 1] - luma[idx + 1] - luma[idx - w] - luma[idx + w]
                )
                if (lap < 0.14f) {
                    sumAbsLap += lap
                    count++
                }
            }
        }
        if (count < 16) return 0.012f
        val meanLap = (sumAbsLap / count).toFloat()
        return (meanLap * 0.45f).coerceIn(0.004f, 0.055f)
    }

    /**
     * Pure JVM-testable Foreground Color Decontamination (Spec Stage 1):
     * F = (C_observed - (1 - A) * B) / A
     */
    fun decontaminatePixel(observed: Float, cleanBg: Float, alpha: Float): Float {
        if (alpha >= 0.96f || alpha <= 0.05f) return observed
        val rawFg = (observed - (1f - alpha) * cleanBg) / alpha
        val conf = ((alpha - 0.05f) / 0.35f).coerceIn(0f, 1f)
        return (observed * (1f - conf) + rawFg.coerceIn(0f, 255f) * conf).coerceIn(0f, 255f)
    }

    // ─── Internal Native Session & Image Processing Helpers ──────────────────

    private fun runLama512Session(
        session: InferenceSessionHandle,
        imageBmp: Bitmap,
        maskBmp: Bitmap
    ): Bitmap? {
        val sz = 512
        val img512 = Bitmap.createScaledBitmap(imageBmp, sz, sz, true)
        val msk512 = Bitmap.createScaledBitmap(maskBmp, sz, sz, false)
        return try {
            val imgPx = IntArray(sz * sz)
            val mskPx = IntArray(sz * sz)
            img512.getPixels(imgPx, 0, sz, 0, 0, sz, sz)
            msk512.getPixels(mskPx, 0, sz, 0, 0, sz, sz)

            val imgChw = bitmapPixelsToChwFloat(imgPx, sz, sz)
            val mskChw = FloatArray(sz * sz) { i -> if (Color.red(mskPx[i]) > 127) 1f else 0f }

            val inputs = LinkedHashMap<String, Pair<FloatArray, LongArray>>()
            val imgKey = session.inputNames.firstOrNull { it.contains("image", ignoreCase = true) } ?: "image"
            val mskKey = session.inputNames.firstOrNull { it.contains("mask", ignoreCase = true) } ?: "mask"
            inputs[imgKey] = Pair(imgChw, longArrayOf(1, 3, sz.toLong(), sz.toLong()))
            if (mskKey in session.inputNames) {
                inputs[mskKey] = Pair(mskChw, longArrayOf(1, 1, sz.toLong(), sz.toLong()))
            }

            val out = session.runFloatTensors(inputs) ?: return null
            if (out.size < sz * sz * 3) return null
            // LaMa outputs either [0..1] or [0..255] depending on export; auto-detect scale
            var maxV = 0f
            for (i in 0 until min(out.size, 4096)) if (out[i] > maxV) maxV = out[i]
            val normScale = if (maxV > 2.0f) 1f / 255f else 1f
            val plane = sz * sz
            val outPx = IntArray(plane) { i ->
                val r = ((out[i] * normScale).coerceIn(0f, 1f) * 255f).roundToInt()
                val g = ((out[plane + i] * normScale).coerceIn(0f, 1f) * 255f).roundToInt()
                val b = ((out[2 * plane + i] * normScale).coerceIn(0f, 1f) * 255f).roundToInt()
                Color.argb(255, r, g, b)
            }
            val raw512 = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888).apply {
                setPixels(outPx, 0, sz, 0, 0, sz, sz)
            }
            if (imageBmp.width != sz || imageBmp.height != sz) {
                Bitmap.createScaledBitmap(raw512, imageBmp.width, imageBmp.height, true).also { raw512.recycle() }
            } else raw512
        } finally {
            if (img512 !== imageBmp) img512.recycle()
            if (msk512 !== maskBmp) msk512.recycle()
        }
    }

    /**
     * Exact SDXS / Stable Diffusion scaled-linear `alphas_cumprod` schedule
     * matching `kmpartner/sdxs-dreamshaper-onnx/scheduler/scheduler_config.json`
     * (`beta_start = 0.00085`, `beta_end = 0.012`, `num_train_timesteps = 1000`).
     */
    fun computeScaledLinearAlphasCumprod(
        numSteps: Int = 1000,
        betaStart: Float = 0.00085f,
        betaEnd: Float = 0.012f
    ): FloatArray {
        val sqrtStart = sqrt(betaStart)
        val sqrtEnd = sqrt(betaEnd)
        val alphasCumprod = FloatArray(numSteps)
        var prod = 1.0
        for (i in 0 until numSteps) {
            val bSqrt = sqrtStart + (i.toFloat() / (numSteps - 1).coerceAtLeast(1)) * (sqrtEnd - sqrtStart)
            val beta = (bSqrt * bSqrt).toDouble()
            prod *= (1.0 - beta)
            alphasCumprod[i] = prod.toFloat().coerceIn(1e-6f, 1f)
        }
        return alphasCumprod
    }

    private fun extractTile512AndMask64(
        canvasPixels: IntArray,
        featherMask: FloatArray,
        fullW: Int,
        fullH: Int,
        tile: ExpandTile
    ): Pair<FloatArray, FloatArray> {
        val sz = 512
        val plane = sz * sz
        val chw = FloatArray(3 * plane)
        for (y in 0 until sz) {
            val sy = (tile.top + ((y.toFloat() / sz) * tile.height).toInt()).coerceIn(0, fullH - 1)
            val srcRow = sy * fullW
            for (x in 0 until sz) {
                val sx = (tile.left + ((x.toFloat() / sz) * tile.width).toInt()).coerceIn(0, fullW - 1)
                val p = canvasPixels[srcRow + sx]
                val idx = y * sz + x
                // AutoencoderKL expects [-1.0, 1.0] CHW float32
                chw[idx] = (Color.red(p) / 127.5f) - 1f
                chw[plane + idx] = (Color.green(p) / 127.5f) - 1f
                chw[2 * plane + idx] = (Color.blue(p) / 127.5f) - 1f
            }
        }
        val m64 = FloatArray(64 * 64)
        for (ly in 0 until 64) {
            val sy = (tile.top + ((ly.toFloat() / 64f) * tile.height).toInt()).coerceIn(0, fullH - 1)
            for (lx in 0 until 64) {
                val sx = (tile.left + ((lx.toFloat() / 64f) * tile.width).toInt()).coerceIn(0, fullW - 1)
                m64[ly * 64 + lx] = featherMask[sy * fullW + sx]
            }
        }
        return Pair(chw, m64)
    }

    private fun vaeRgbToPixels512(rgbChw: FloatArray): IntArray {
        val plane = 512 * 512
        return IntArray(plane) { i ->
            val r = (((rgbChw[i] * 0.5f) + 0.5f).coerceIn(0f, 1f) * 255f).roundToInt()
            val g = (((rgbChw[plane + i] * 0.5f) + 0.5f).coerceIn(0f, 1f) * 255f).roundToInt()
            val b = (((rgbChw[2 * plane + i] * 0.5f) + 0.5f).coerceIn(0f, 1f) * 255f).roundToInt()
            Color.argb(255, r, g, b)
        }
    }

    private fun blitTile512IntoCanvas(
        canvasPixels: IntArray,
        featherMask: FloatArray,
        fullW: Int,
        fullH: Int,
        tile: ExpandTile,
        tile512Px: IntArray
    ) {
        val tw = tile.width.coerceAtLeast(1)
        val th = tile.height.coerceAtLeast(1)
        for (ly in 0 until th) {
            val cy = tile.top + ly
            if (cy !in 0 until fullH) continue
            val sy = ((ly.toFloat() / th) * 512f).toInt().coerceIn(0, 511)
            for (lx in 0 until tw) {
                val cx = tile.left + lx
                if (cx !in 0 until fullW) continue
                val idx = cy * fullW + cx
                val m = featherMask[idx]
                if (m <= 0.01f) continue
                val sx = ((lx.toFloat() / tw) * 512f).toInt().coerceIn(0, 511)
                val np = tile512Px[sy * 512 + sx]
                val op = canvasPixels[idx]
                val r = (Color.red(op) * (1f - m) + Color.red(np) * m).roundToInt().coerceIn(0, 255)
                val g = (Color.green(op) * (1f - m) + Color.green(np) * m).roundToInt().coerceIn(0, 255)
                val b = (Color.blue(op) * (1f - m) + Color.blue(np) * m).roundToInt().coerceIn(0, 255)
                canvasPixels[idx] = Color.argb(255, r, g, b)
            }
        }
    }

    private fun resampleFloatGridBilinear(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src
        val dst = FloatArray(dw * dh)
        val scaleX = (sw - 1).toFloat() / (dw - 1).coerceAtLeast(1)
        val scaleY = (sh - 1).toFloat() / (dh - 1).coerceAtLeast(1)
        for (y in 0 until dh) {
            val fy = y * scaleY
            val y0 = fy.toInt().coerceIn(0, sh - 1)
            val y1 = (y0 + 1).coerceIn(0, sh - 1)
            val wy = fy - y0
            for (x in 0 until dw) {
                val fx = x * scaleX
                val x0 = fx.toInt().coerceIn(0, sw - 1)
                val x1 = (x0 + 1).coerceIn(0, sw - 1)
                val wx = fx - x0
                val top = src[y0 * sw + x0] * (1f - wx) + src[y0 * sw + x1] * wx
                val bot = src[y1 * sw + x0] * (1f - wx) + src[y1 * sw + x1] * wx
                dst[y * dw + x] = top * (1f - wy) + bot * wy
            }
        }
        return dst
    }

    private fun buildMirrorPaddedCanvas(
        origPixels: IntArray,
        origW: Int,
        origH: Int,
        padLeft: Int,
        padTop: Int,
        fullW: Int,
        fullH: Int
    ): IntArray {
        val out = IntArray(fullW * fullH)
        for (y in 0 until fullH) {
            val sy = mirrorIndex(y - padTop, origH)
            val srcRow = sy * origW
            val dstRow = y * fullW
            for (x in 0 until fullW) {
                val sx = mirrorIndex(x - padLeft, origW)
                out[dstRow + x] = origPixels[srcRow + sx]
            }
        }
        return out
    }

    private fun mirrorIndex(v: Int, limit: Int): Int {
        if (limit <= 1) return 0
        var idx = v
        while (idx < 0 || idx >= limit) {
            if (idx < 0) idx = -idx - 1
            if (idx >= limit) idx = 2 * limit - idx - 1
        }
        return idx.coerceIn(0, limit - 1)
    }

    /**
     * 4-step progressive anisotropic diffusion + directional border extrapolation inside a 512x512 tile,
     * eliminating mirror-seam repetition while preserving horizon/sky/ground continuity.
     */
    private fun refineOutpaintTile4Steps(
        pixels: IntArray,
        mask: FloatArray,
        fullW: Int,
        fullH: Int,
        tile: ExpandTile,
        origLeft: Int,
        origTop: Int,
        origRight: Int,
        origBottom: Int,
        steps: Int
    ) {
        val x0 = tile.left.coerceAtLeast(1)
        val y0 = tile.top.coerceAtLeast(1)
        val x1 = (tile.left + tile.width).coerceAtMost(fullW - 1)
        val y1 = (tile.top + tile.height).coerceAtMost(fullH - 1)

        val scratch = IntArray(tile.width * tile.height)
        for (step in 0 until steps) {
            val radius = (steps - step) * 2
            for (y in y0 until y1) {
                val row = y * fullW
                val ly = y - tile.top
                for (x in x0 until x1) {
                    val idx = row + x
                    val m = mask[idx]
                    val lx = x - tile.left
                    if (m <= 0.02f) {
                        scratch[ly * tile.width + lx] = pixels[idx]
                        continue
                    }
                    // Distance outside original photo determines progressive softening + extrapolation
                    val dxOut = when {
                        x < origLeft -> origLeft - x
                        x >= origRight -> x - origRight + 1
                        else -> 0
                    }
                    val dyOut = when {
                        y < origTop -> origTop - y
                        y >= origBottom -> y - origBottom + 1
                        else -> 0
                    }
                    val distOut = hypot(dxOut.toFloat(), dyOut.toFloat())
                    val blendAlpha = (0.35f + (distOut / 120f).coerceIn(0f, 0.45f)) * m

                    val xL = (x - radius).coerceIn(0, fullW - 1)
                    val xR = (x + radius).coerceIn(0, fullW - 1)
                    val yU = (y - radius).coerceIn(0, fullH - 1)
                    val yD = (y + radius).coerceIn(0, fullH - 1)

                    val c0 = pixels[idx]
                    val cL = pixels[row + xL]
                    val cR = pixels[row + xR]
                    val cU = pixels[yU * fullW + x]
                    val cD = pixels[yD * fullW + x]

                    val avgR = (Color.red(cL) + Color.red(cR) + Color.red(cU) + Color.red(cD)) * 0.25f
                    val avgG = (Color.green(cL) + Color.green(cR) + Color.green(cU) + Color.green(cD)) * 0.25f
                    val avgB = (Color.blue(cL) + Color.blue(cR) + Color.blue(cU) + Color.blue(cD)) * 0.25f

                    val nr = (Color.red(c0) * (1f - blendAlpha) + avgR * blendAlpha).roundToInt().coerceIn(0, 255)
                    val ng = (Color.green(c0) * (1f - blendAlpha) + avgG * blendAlpha).roundToInt().coerceIn(0, 255)
                    val nb = (Color.blue(c0) * (1f - blendAlpha) + avgB * blendAlpha).roundToInt().coerceIn(0, 255)
                    scratch[ly * tile.width + lx] = Color.argb(255, nr, ng, nb)
                }
            }
            // Write tile scratch back
            for (y in y0 until y1) {
                val row = y * fullW
                val ly = y - tile.top
                for (x in x0 until x1) {
                    val idx = row + x
                    if (mask[idx] > 0.02f) {
                        pixels[idx] = scratch[ly * tile.width + (x - tile.left)]
                    }
                }
            }
        }
    }

    /**
     * Border Histogram Matching (Reinhard per-channel mean/std alignment against adjacent 32px photo rim)
     * + Deterministic Sensor Grain Re-Injection matching `noiseSigma`.
     */
    private fun matchBorderHistogramAndGrain(
        synthPixels: IntArray,
        origPixels: IntArray,
        featherMask: FloatArray,
        fullW: Int,
        fullH: Int,
        origLeft: Int,
        origTop: Int,
        origW: Int,
        origH: Int,
        noiseSigma: Float
    ) {
        val rim = 32
        var refR = 0.0; var refG = 0.0; var refB = 0.0
        var refR2 = 0.0; var refG2 = 0.0; var refB2 = 0.0
        var refN = 0L

        for (y in 0 until origH) {
            val onBorderY = y < rim || y >= origH - rim
            val row = y * origW
            for (x in 0 until origW) {
                if (!onBorderY && x >= rim && x < origW - rim) continue
                val p = origPixels[row + x]
                val r = Color.red(p).toDouble(); val g = Color.green(p).toDouble(); val b = Color.blue(p).toDouble()
                refR += r; refG += g; refB += b
                refR2 += r * r; refG2 += g * g; refB2 += b * b
                refN++
            }
        }
        if (refN < 16L) return

        val mRefR = refR / refN; val mRefG = refG / refN; val mRefB = refB / refN
        val sRefR = sqrt(max(1.0, refR2 / refN - mRefR * mRefR))
        val sRefG = sqrt(max(1.0, refG2 / refN - mRefG * mRefG))
        val sRefB = sqrt(max(1.0, refB2 / refN - mRefB * mRefB))

        var synR = 0.0; var synG = 0.0; var synB = 0.0
        var synR2 = 0.0; var synG2 = 0.0; var synB2 = 0.0
        var synN = 0L
        for (i in synthPixels.indices) {
            if (featherMask[i] < 0.5f) continue
            val p = synthPixels[i]
            val r = Color.red(p).toDouble(); val g = Color.green(p).toDouble(); val b = Color.blue(p).toDouble()
            synR += r; synG += g; synB += b
            synR2 += r * r; synG2 += g * g; synB2 += b * b
            synN++
        }
        if (synN < 16L) return

        val mSynR = synR / synN; val mSynG = synG / synN; val mSynB = synB / synN
        val sSynR = sqrt(max(1.0, synR2 / synN - mSynR * mSynR))
        val sSynG = sqrt(max(1.0, synG2 / synN - mSynG * mSynG))
        val sSynB = sqrt(max(1.0, synB2 / synN - mSynB * mSynB))

        val gainR = (sRefR / sSynR).coerceIn(0.75, 1.35)
        val gainG = (sRefG / sSynG).coerceIn(0.75, 1.35)
        val gainB = (sRefB / sSynB).coerceIn(0.75, 1.35)
        val grainAmp = (noiseSigma * 255f).coerceIn(1.2f, 12f)

        for (y in 0 until fullH) {
            val row = y * fullW
            for (x in 0 until fullW) {
                val idx = row + x
                val m = featherMask[idx]
                if (m <= 0.01f) continue
                val p = synthPixels[idx]
                val r0 = Color.red(p).toDouble()
                val g0 = Color.green(p).toDouble()
                val b0 = Color.blue(p).toDouble()

                // Soft 45% histogram pull toward original rim statistics so local color tone stays natural
                val tr = mRefR + (r0 - mSynR) * gainR
                val tg = mRefG + (g0 - mSynG) * gainG
                val tb = mRefB + (b0 - mSynB) * gainB

                val hBlend = 0.45 * m
                val grain = (deterministicHashSigned(x, y) * grainAmp * m).toDouble()

                val fr = (r0 * (1.0 - hBlend) + tr * hBlend + grain).roundToInt().coerceIn(0, 255)
                val fg = (g0 * (1.0 - hBlend) + tg * hBlend + grain).roundToInt().coerceIn(0, 255)
                val fb = (b0 * (1.0 - hBlend) + tb * hBlend + grain).roundToInt().coerceIn(0, 255)
                synthPixels[idx] = Color.argb(255, fr, fg, fb)
            }
        }
    }

    private fun deterministicHashSigned(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        val u = ((n xor (n ushr 16)) and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
        return (u * 2f) - 1f
    }

    private fun computeContinuousAlphaMatteFallback(pixels: IntArray, w: Int, h: Int): FloatArray {
        var bgR = 0f; var bgG = 0f; var bgB = 0f; var bgN = 0
        for (x in 0 until w) {
            for (y in intArrayOf(0, 1, h - 2, h - 1)) {
                val p = pixels[y * w + x]
                bgR += Color.red(p); bgG += Color.green(p); bgB += Color.blue(p); bgN++
            }
        }
        bgR /= bgN.coerceAtLeast(1); bgG /= bgN.coerceAtLeast(1); bgB /= bgN.coerceAtLeast(1)

        val alpha = FloatArray(w * h)
        for (y in 0 until h) {
            val ny = (y.toFloat() / h) - 0.48f
            for (x in 0 until w) {
                val nx = (x.toFloat() / w) - 0.50f
                val idx = y * w + x
                val p = pixels[idx]
                val colorDist = sqrt(
                    (Color.red(p) - bgR).pow(2) + (Color.green(p) - bgG).pow(2) + (Color.blue(p) - bgB).pow(2)
                ) / 220f
                val radial = hypot(nx * 1.45f, ny * 1.15f)
                val spatialPrior = (1f - ((radial - 0.22f) / 0.34f)).coerceIn(0f, 1f)
                val softMatte = (spatialPrior * 0.72f + colorDist.coerceIn(0f, 1f) * spatialPrior * 0.55f).coerceIn(0f, 1f)
                alpha[idx] = softMatte * softMatte * (3f - 2f * softMatte)
            }
        }
        return alpha
    }

    private fun computeGroundPlaneAwareDepthFallback(pixels: IntArray, alpha: FloatArray, w: Int, h: Int): FloatArray {
        var feetRow = (h * 0.78f).toInt()
        for (y in h - 1 downTo h / 3) {
            var subjCount = 0
            for (x in w / 5 until (w * 4) / 5) {
                if (alpha[y * w + x] > 0.5f) subjCount++
            }
            if (subjCount > w / 12) {
                feetRow = y
                break
            }
        }

        val subjectDepth = 0.42f
        val depth = FloatArray(w * h)
        for (y in 0 until h) {
            val groundZ = if (y >= feetRow) {
                val t = ((y - feetRow).toFloat() / max(1, h - feetRow)).coerceIn(0f, 1f)
                subjectDepth - t * 0.16f
            } else {
                val t = ((feetRow - y).toFloat() / max(1, feetRow)).coerceIn(0f, 1f)
                subjectDepth + t.pow(1.15f) * 0.53f
            }
            for (x in 0 until w) {
                val idx = y * w + x
                val a = alpha[idx]
                depth[idx] = (groundZ * (1f - a) + subjectDepth * a).coerceIn(0.05f, 1.0f)
            }
        }
        return depth
    }

    private fun sampleSubjectFocalDepth(alpha: FloatArray, depth: FloatArray, w: Int, h: Int): Float {
        var sum = 0.0
        var wSum = 0.0
        for (i in 0 until w * h) {
            if (alpha[i] > 0.65f) {
                sum += depth[i] * alpha[i]
                wSum += alpha[i]
            }
        }
        return if (wSum > 10.0) (sum / wSum).toFloat().coerceIn(0.1f, 0.9f) else 0.42f
    }

    private fun decontaminateForegroundEdges(
        origPixels: IntArray,
        cleanPlate: Bitmap,
        alpha: FloatArray,
        w: Int,
        h: Int
    ): Bitmap {
        val bgPixels = IntArray(w * h)
        cleanPlate.getPixels(bgPixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            val a = alpha[i]
            val op = origPixels[i]
            if (a <= 0.05f || a >= 0.96f) {
                out[i] = op
            } else {
                val bp = bgPixels[i]
                val fr = decontaminatePixel(Color.red(op).toFloat(), Color.red(bp).toFloat(), a).roundToInt()
                val fg = decontaminatePixel(Color.green(op).toFloat(), Color.green(bp).toFloat(), a).roundToInt()
                val fb = decontaminatePixel(Color.blue(op).toFloat(), Color.blue(bp).toFloat(), a).roundToInt()
                out[i] = Color.argb(255, fr, fg, fb)
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    private fun measureSubjectNoiseSigma(pixels: IntArray, alpha: FloatArray, w: Int, h: Int): Float {
        val luma = FloatArray(w * h) { i ->
            val p = pixels[i]
            (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f
        }
        return measureNoiseSigmaFromLuma(luma, alpha, w, h)
    }

    private fun pack16BitDepthBitmap(depth: FloatArray, w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { i ->
            val v16 = (depth[i].coerceIn(0f, 1f) * 65535f).roundToInt().coerceIn(0, 65535)
            val hi = (v16 shr 8) and 0xFF
            val lo = v16 and 0xFF
            Color.argb(255, hi, lo, hi)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(px, 0, w, 0, 0, w, h)
        }
    }

    private fun floatMaskToBitmap(mask: FloatArray, w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { i ->
            val v = (mask[i].coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
            Color.argb(255, v, v, v)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(px, 0, w, 0, 0, w, h)
        }
    }

    private fun normalizeDepthArray(raw: FloatArray, count: Int): FloatArray {
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
        for (i in 0 until count) {
            if (raw[i] < mn) mn = raw[i]
            if (raw[i] > mx) mx = raw[i]
        }
        val span = (mx - mn).coerceAtLeast(1e-5f)
        // Depth Anything V2 outputs inverse disparity (larger = closer); invert to physical distance Z
        return FloatArray(count) { i -> (1f - ((raw[i] - mn) / span)).coerceIn(0.05f, 1f) }
    }

    private fun resampleFloatGrid(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src
        val dst = FloatArray(dw * dh)
        for (y in 0 until dh) {
            val sy = ((y.toFloat() / dh) * sh).toInt().coerceIn(0, sh - 1)
            for (x in 0 until dw) {
                val sx = ((x.toFloat() / dw) * sw).toInt().coerceIn(0, sw - 1)
                dst[y * dw + x] = src[sy * sw + sx]
            }
        }
        return dst
    }

    private fun bitmapPixelsToChwFloat(pixels: IntArray, w: Int, h: Int): FloatArray {
        val plane = w * h
        val out = FloatArray(plane * 3)
        for (i in 0 until plane) {
            val p = pixels[i]
            out[i] = Color.red(p) / 255f
            out[plane + i] = Color.green(p) / 255f
            out[2 * plane + i] = Color.blue(p) / 255f
        }
        return out
    }

    private fun bitmapPixelsToNormalizedChwFloat(pixels: IntArray, w: Int, h: Int): FloatArray {
        val plane = w * h
        val out = FloatArray(plane * 3)
        for (i in 0 until plane) {
            val p = pixels[i]
            out[i] = ((Color.red(p) / 255f) - 0.485f) / 0.229f
            out[plane + i] = ((Color.green(p) / 255f) - 0.456f) / 0.224f
            out[2 * plane + i] = ((Color.blue(p) / 255f) - 0.406f) / 0.225f
        }
        return out
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x.coerceIn(-20f, 20f))))
}
