package com.cleanlr.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Zero-Bloat Cloud / Self-Hosted GPU Client for SOTA FLUX.1 Fill [pro/dev] & BrushNet+SDXL.
 *
 * Uses Android's built-in HttpURLConnection (0 KB extra APK dependencies!).
 * Supports:
 *  - Fal.ai (`fal-ai/flux-pro/v1/fill`, `fal-ai/flux-lora-fill`) — ~1-2s latency
 *  - Replicate (`black-forest-labs/flux-fill-pro`, `black-forest-labs/flux-fill-dev`)
 *  - RunPod Serverless (`https://api.runpod.ai/v2/<endpoint>/runsync`)
 *  - Custom Self-Hosted GPU Server (`server/flux_fill_server.py` running FLUX.1-Fill-dev)
 */
enum class GenerativeAiBackend(
    val displayName: String,
    val defaultModel: String,
    val defaultEndpoint: String
) {
    LOCAL_LAN_BRIDGE(
        displayName = "Desktop LAN Bridge (FLUX.2 Klein 4B GGUF • 0 Cost)",
        defaultModel = "flux2-klein-4b-Q4_K_M.gguf",
        defaultEndpoint = "http://192.168.1.100:8000/v1/flux-fill"
    ),
    CUSTOM_GPU_SERVER(
        displayName = "Self-Hosted GPU / Gradio / HF BYOE (0 API Cost)",
        defaultModel = "black-forest-labs/FLUX.1-Fill-dev",
        defaultEndpoint = "http://10.0.2.2:8000/v1/flux-fill"
    ),
    FAL_AI(
        displayName = "Fal.ai (FLUX.1 Fill • Cloud 1-2s)",
        defaultModel = "fal-ai/flux-pro/v1/fill",
        defaultEndpoint = "https://fal.run/fal-ai/flux-pro/v1/fill"
    ),
    REPLICATE(
        displayName = "Replicate (FLUX.1 Fill Pro/Dev)",
        defaultModel = "black-forest-labs/flux-fill-pro",
        defaultEndpoint = "https://api.replicate.com/v1/models/black-forest-labs/flux-fill-pro/predictions"
    ),
    RUNPOD(
        displayName = "RunPod Serverless GPU",
        defaultModel = "black-forest-labs/FLUX.1-Fill-dev",
        defaultEndpoint = "https://api.runpod.ai/v2/YOUR_ENDPOINT_ID/runsync"
    )
}

data class GenerativeAiConfig(
    val backend: GenerativeAiBackend = GenerativeAiBackend.LOCAL_LAN_BRIDGE,
    val apiKey: String = "",
    val endpointUrl: String = GenerativeAiBackend.LOCAL_LAN_BRIDGE.defaultEndpoint,
    val modelId: String = GenerativeAiBackend.LOCAL_LAN_BRIDGE.defaultModel,
    val usePoissonBlending: Boolean = true,
    val inferenceSteps: Int = 28,
    val guidanceScale: Float = 30.0f
) {
    val isReady: Boolean
        get() = when (backend) {
            GenerativeAiBackend.LOCAL_LAN_BRIDGE,
            GenerativeAiBackend.CUSTOM_GPU_SERVER -> endpointUrl.isNotBlank()
            else -> apiKey.isNotBlank() && endpointUrl.isNotBlank()
        }
}

object GenerativeAiConfigStore {
    private const val PREFS = "cleanlr_genai_config"
    private const val KEY_BACKEND = "backend"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_MODEL = "model"
    private const val KEY_POISSON = "poisson"

    fun load(context: Context): GenerativeAiConfig {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val backendName = sp.getString(KEY_BACKEND, GenerativeAiBackend.FAL_AI.name)
        val backend = runCatching { GenerativeAiBackend.valueOf(backendName ?: "") }
            .getOrDefault(GenerativeAiBackend.FAL_AI)
        return GenerativeAiConfig(
            backend = backend,
            apiKey = sp.getString(KEY_API_KEY, "") ?: "",
            endpointUrl = sp.getString(KEY_ENDPOINT, backend.defaultEndpoint) ?: backend.defaultEndpoint,
            modelId = sp.getString(KEY_MODEL, backend.defaultModel) ?: backend.defaultModel,
            usePoissonBlending = sp.getBoolean(KEY_POISSON, true)
        )
    }

    fun save(context: Context, config: GenerativeAiConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BACKEND, config.backend.name)
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_ENDPOINT, config.endpointUrl.trim())
            .putString(KEY_MODEL, config.modelId.trim())
            .putBoolean(KEY_POISSON, config.usePoissonBlending)
            .apply()
    }
}

class FluxFillClient(private val config: GenerativeAiConfig) {

    companion object {
        private const val TAG = "FluxFillClient"
        const val DEFAULT_REMOVE_PROMPT =
            "Clean natural background seamlessly continuing surrounding lighting, shadows, depth of field, and fine photographic texture with zero artifacts"
        const val DEFAULT_EXPAND_PROMPT =
            "Seamless photorealistic outpainting naturally extending the scene, lighting direction, shadows, perspective, lens bokeh, and grain"
    }

    /**
     * Sends ONLY the cropped ROI image + binary mask to FLUX.1 Fill on the configured GPU server
     * and returns the generated high-resolution patch resized to match the original ROI dimensions.
     */
    suspend fun generatePatch(
        croppedImage: Bitmap,
        binaryMask: Bitmap,
        prompt: String,
        mode: String = "remove",
        uploadWidth: Int = croppedImage.width,
        uploadHeight: Int = croppedImage.height
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (!config.isReady) {
            return@withContext Result.failure(
                IllegalStateException("FLUX.1 Fill server is not configured. Add an API key or GPU server URL.")
            )
        }

        try {
            val sendImg = if (croppedImage.width != uploadWidth || croppedImage.height != uploadHeight) {
                Bitmap.createScaledBitmap(croppedImage, uploadWidth, uploadHeight, true)
            } else croppedImage
            val sendMask = if (binaryMask.width != uploadWidth || binaryMask.height != uploadHeight) {
                Bitmap.createScaledBitmap(binaryMask, uploadWidth, uploadHeight, false)
            } else binaryMask

            val imgB64 = bitmapToPngBase64(sendImg)
            val maskB64 = bitmapToPngBase64(sendMask)

            if (sendImg !== croppedImage) sendImg.recycle()
            if (sendMask !== binaryMask) sendMask.recycle()

            val generated = when (config.backend) {
                GenerativeAiBackend.FAL_AI -> callFalAi(imgB64, maskB64, prompt)
                GenerativeAiBackend.REPLICATE -> callReplicate(imgB64, maskB64, prompt)
                GenerativeAiBackend.RUNPOD -> callRunPod(imgB64, maskB64, prompt, mode)
                GenerativeAiBackend.LOCAL_LAN_BRIDGE,
                GenerativeAiBackend.CUSTOM_GPU_SERVER -> callCustomServer(imgB64, maskB64, prompt, mode)
            }

            // Ensure returned patch matches exact ROI pixel dimensions for bit-exact Poisson Blending
            val finalPatch = if (generated.width != croppedImage.width || generated.height != croppedImage.height) {
                Bitmap.createScaledBitmap(generated, croppedImage.width, croppedImage.height, true).also {
                    generated.recycle()
                }
            } else generated

            Result.success(finalPatch)
        } catch (e: Exception) {
            Log.e(TAG, "FLUX.1 Fill inference failed", e)
            Result.failure(e)
        }
    }

    private fun callFalAi(imgB64: String, maskB64: String, prompt: String): Bitmap {
        val body = JSONObject().apply {
            put("image_url", "data:image/png;base64,$imgB64")
            put("mask_url", "data:image/png;base64,$maskB64")
            put("prompt", prompt)
            put("num_inference_steps", config.inferenceSteps)
            put("guidance_scale", config.guidanceScale.toDouble())
            put("output_format", "png")
            put("safety_tolerance", "5")
        }
        val respText = httpPostJson(
            urlStr = config.endpointUrl,
            jsonBody = body.toString(),
            headers = mapOf("Authorization" to "Key ${config.apiKey}")
        )
        val json = JSONObject(respText)
        val imageUrl = json.optJSONArray("images")?.optJSONObject(0)?.optString("url")
            ?: json.optJSONObject("image")?.optString("url")
            ?: throw IllegalStateException("Fal.ai response missing output image URL")
        return downloadBitmap(imageUrl)
    }

    private suspend fun callReplicate(imgB64: String, maskB64: String, prompt: String): Bitmap {
        val input = JSONObject().apply {
            put("image", "data:image/png;base64,$imgB64")
            put("mask", "data:image/png;base64,$maskB64")
            put("prompt", prompt)
            put("steps", config.inferenceSteps)
            put("guidance", config.guidanceScale.toDouble())
            put("output_format", "png")
        }
        val body = JSONObject().apply { put("input", input) }

        val respText = httpPostJson(
            urlStr = config.endpointUrl,
            jsonBody = body.toString(),
            headers = mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "Prefer" to "wait"
            )
        )
        var json = JSONObject(respText)
        val getUrl = json.optJSONObject("urls")?.optString("get")

        // Poll if Replicate is still processing
        var attempts = 0
        while (json.optString("status") in listOf("starting", "processing") && !getUrl.isNullOrBlank() && attempts < 25) {
            delay(1000L)
            val pollResp = httpGet(getUrl, mapOf("Authorization" to "Bearer ${config.apiKey}"))
            json = JSONObject(pollResp)
            attempts++
        }

        if (json.optString("status") == "failed") {
            throw IllegalStateException("Replicate error: ${json.optString("error")}")
        }

        val outAny = json.opt("output")
        val outputUrl = when (outAny) {
            is String -> outAny
            is org.json.JSONArray -> outAny.optString(0)
            else -> null
        } ?: throw IllegalStateException("Replicate response missing output URL")

        return downloadBitmap(outputUrl)
    }

    private fun callRunPod(imgB64: String, maskB64: String, prompt: String, mode: String): Bitmap {
        val input = JSONObject().apply {
            put("image_base64", imgB64)
            put("mask_base64", maskB64)
            put("prompt", prompt)
            put("mode", mode)
            put("model", config.modelId)
            put("steps", config.inferenceSteps)
            put("guidance_scale", config.guidanceScale.toDouble())
        }
        val body = JSONObject().apply { put("input", input) }
        val respText = httpPostJson(
            urlStr = config.endpointUrl,
            jsonBody = body.toString(),
            headers = mapOf("Authorization" to "Bearer ${config.apiKey}")
        )
        val json = JSONObject(respText)
        val output = json.optJSONObject("output") ?: json
        return decodeImageFromResponse(output)
    }

    private fun callCustomServer(imgB64: String, maskB64: String, prompt: String, mode: String): Bitmap {
        val body = JSONObject().apply {
            put("image_base64", imgB64)
            put("mask_base64", maskB64)
            put("prompt", prompt)
            put("mode", mode)
            put("model", config.modelId)
            put("steps", config.inferenceSteps)
            put("guidance_scale", config.guidanceScale.toDouble())
        }
        val headers = if (config.apiKey.isNotBlank()) {
            mapOf("Authorization" to "Bearer ${config.apiKey}")
        } else emptyMap()
        val respText = httpPostJson(config.endpointUrl, body.toString(), headers)
        return decodeImageFromResponse(JSONObject(respText))
    }

    private fun decodeImageFromResponse(json: JSONObject): Bitmap {
        val b64 = json.optString("image_base64", "")
        if (b64.isNotBlank()) {
            val clean = b64.substringAfter("base64,", b64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Failed to decode base64 PNG from server")
        }
        val url = json.optString("image_url", "")
        if (url.isNotBlank()) {
            return downloadBitmap(url)
        }
        throw IllegalStateException("Server JSON must include 'image_base64' or 'image_url'")
    }

    private fun bitmapToPngBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun httpPostJson(urlStr: String, jsonBody: String, headers: Map<String, String>): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { os -> os.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${response.take(220)}")
        }
        return response
    }

    private fun httpGet(urlStr: String, headers: Map<String, String>): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${response.take(220)}")
        }
        return response
    }

    private fun downloadBitmap(imageUrl: String): Bitmap {
        val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode downloaded image from $imageUrl")
    }
}

/**
 * Plugs FluxFillClient + PoissonBlender directly into GenerativeExpandEngine.GenerativeExpandProvider!
 * Sends padded canvas + outpaint mask to FLUX.1 Fill, then composites the 100% untouched original
 * full-resolution pixels back into the center with seamless Poisson Blending!
 */
class FluxFillExpandProvider(
    private val config: GenerativeAiConfig
) : GenerativeExpandEngine.GenerativeExpandProvider {

    override val providerName: String = config.backend.displayName
    override val isConfigured: Boolean = config.isReady

    override suspend fun generateExpansion(
        sourceBitmap: Bitmap,
        outpaintMask: Bitmap,
        region: GenerativeExpandEngine.ExpandRegion,
        prompt: String?
    ): Result<Bitmap> {
        val client = FluxFillClient(config)
        val maxEdge = 1280
        val maxSide = maxOf(sourceBitmap.width, sourceBitmap.height)
        val scale = if (maxSide > maxEdge) maxEdge.toFloat() / maxSide else 1f
        val upW = (((sourceBitmap.width * scale).toInt().coerceAtLeast(64) + 15) / 16) * 16
        val upH = (((sourceBitmap.height * scale).toInt().coerceAtLeast(64) + 15) / 16) * 16

        val generatedResult = client.generatePatch(
            croppedImage = sourceBitmap,
            binaryMask = outpaintMask,
            prompt = prompt?.takeIf { it.isNotBlank() } ?: FluxFillClient.DEFAULT_EXPAND_PROMPT,
            mode = "expand",
            uploadWidth = upW,
            uploadHeight = upH
        )

        return generatedResult.map { generatedFull ->
            if (config.usePoissonBlending) {
                // Blend ONLY the white outpaint ring into sourceBitmap while preserving 100% original inner pixels!
                PoissonBlender.blendPatchIntoOriginal(
                    originalBitmap = sourceBitmap,
                    sourcePatch = generatedFull,
                    maskBitmap = outpaintMask,
                    offsetX = 0,
                    offsetY = 0,
                    iterations = 100,
                    mixedGradients = false
                )
            } else {
                generatedFull
            }
        }
    }
}
