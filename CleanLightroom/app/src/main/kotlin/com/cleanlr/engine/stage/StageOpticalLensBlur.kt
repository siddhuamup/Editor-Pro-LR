package com.cleanlr.engine.stage

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.cleanlr.ai.AiVisionPipeline
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import com.cleanlr.model.BlurAdjustments

/**
 * Stage Optical Lens Blur (5-Stage Clean-Plate + Decontaminated Hair Foreground +
 * 16-bit RG Bilinear Depth CoC + Linear HDR Bokeh + Hair-Safe Partial Occlusion + Sensor Grain Matching).
 */
class StageOpticalLensBlur(private val context: Context) {

    companion object {
        private const val TAG = "StageOpticalLensBlur"

        fun bokehShapeToInt(shape: String): Int = when (shape.lowercase()) {
            "bubble" -> 1
            "5-blade" -> 2
            "7-blade" -> 3
            "9-blade" -> 4
            "oval", "anamorphic" -> 5
            "cat eye", "cats-eye" -> 6
            else -> 0 // Circle
        }
    }

    private var programId = 0
    private var locOriginalTex = -1
    private var locCleanPlateTex = -1
    private var locAlphaMatteTex = -1
    private var locDepthMapTex = -1
    private var locDecontaminatedFgTex = -1
    private var locHasOpticalMaps = -1
    private var locBlurAmount = -1
    private var locFocalDepthS = -1
    private var locBokehBoost = -1
    private var locBokehShape = -1
    private var locNoiseSigma = -1
    private var locImageAspect = -1
    private var locDepthTexSize = -1
    private var locVisualizeDepth = -1

    private var cleanPlateTexId = 0
    private var alphaMatteTexId = 0
    private var depthMapTexId = 0
    private var decontaminatedFgTexId = 0
    private var depthTexWidth = 512f
    private var depthTexHeight = 512f
    private var hasOpticalMaps = false
    private var focalDepthS = 0.42f
    private var noiseSigma = 0.014f

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return
        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage_optical_lens_blur.frag"
        )
        if (programId == 0) {
            Log.e(TAG, "Failed to compile StageOpticalLensBlur shader")
            return
        }

        locOriginalTex = GLES30.glGetUniformLocation(programId, "uOriginalTex")
        locCleanPlateTex = GLES30.glGetUniformLocation(programId, "uCleanPlateTex")
        locAlphaMatteTex = GLES30.glGetUniformLocation(programId, "uAlphaMatteTex")
        locDepthMapTex = GLES30.glGetUniformLocation(programId, "uDepthMapTex")
        locDecontaminatedFgTex = GLES30.glGetUniformLocation(programId, "uDecontaminatedFgTex")
        locHasOpticalMaps = GLES30.glGetUniformLocation(programId, "uHasOpticalMaps")
        locBlurAmount = GLES30.glGetUniformLocation(programId, "uBlurAmount")
        locFocalDepthS = GLES30.glGetUniformLocation(programId, "uFocalDepthS")
        locBokehBoost = GLES30.glGetUniformLocation(programId, "uBokehBoost")
        locBokehShape = GLES30.glGetUniformLocation(programId, "uBokehShape")
        locNoiseSigma = GLES30.glGetUniformLocation(programId, "uNoiseSigma")
        locImageAspect = GLES30.glGetUniformLocation(programId, "uImageAspect")
        locDepthTexSize = GLES30.glGetUniformLocation(programId, "uDepthTexSize")
        locVisualizeDepth = GLES30.glGetUniformLocation(programId, "uVisualizeDepth")

        isInitialized = true
        Log.i(TAG, "StageOpticalLensBlur 5-stage GLSL pipeline initialized (Hair Decontam + RG16 Bilinear)")
    }

    /**
     * Uploads Clean-Plate, BiRefNet Hair AlphaMatte, 16-bit DepthAnythingV2 DepthMap (GL_NEAREST),
     * and Foreground-Color-Decontaminated Hair Plate to GPU.
     */
    fun uploadOpticalPack(pack: AiVisionPipeline.OpticalBlurPack) {
        clearOpticalPack()
        cleanPlateTexId = uploadBitmapTex(pack.cleanBackgroundPlate, nearest = false)
        alphaMatteTexId = uploadBitmapTex(pack.alphaMatte, nearest = false)
        // CRITICAL: GL_NEAREST prevents 8-bit carry interpolation bands on RG-packed 16-bit depth!
        depthMapTexId = uploadBitmapTex(pack.depthMap16Bit, nearest = true)
        decontaminatedFgTexId = uploadBitmapTex(pack.decontaminatedForeground, nearest = false)
        depthTexWidth = pack.depthMap16Bit.width.coerceAtLeast(1).toFloat()
        depthTexHeight = pack.depthMap16Bit.height.coerceAtLeast(1).toFloat()
        focalDepthS = pack.focalPlaneDepthS
        noiseSigma = pack.subjectNoiseSigma
        hasOpticalMaps = (
            cleanPlateTexId != 0 &&
                alphaMatteTexId != 0 &&
                depthMapTexId != 0 &&
                decontaminatedFgTexId != 0
            )
    }

    fun clearOpticalPack() {
        val ids = intArrayOf(
            cleanPlateTexId,
            alphaMatteTexId,
            depthMapTexId,
            decontaminatedFgTexId
        ).filter { it != 0 }.toIntArray()
        if (ids.isNotEmpty()) {
            GLES30.glDeleteTextures(ids.size, ids, 0)
        }
        cleanPlateTexId = 0
        alphaMatteTexId = 0
        depthMapTexId = 0
        decontaminatedFgTexId = 0
        hasOpticalMaps = false
    }

    /**
     * Executes the 5-Stage Optical Lens Blur pass inside the Half-Float PingPong FBO chain.
     * Skips cleanly with 0ms overhead when blur.amount <= 0.5f.
     */
    fun process(
        blur: BlurAdjustments,
        pingPong: PingPongFbo,
        vaoId: Int,
        imageWidth: Int,
        imageHeight: Int,
        visualizeDepth: Boolean = false
    ): Boolean {
        if (!isInitialized) return false
        if (!visualizeDepth && blur.amount <= 0.5f) return false
        val inputTex = pingPong.currentRead?.textureId ?: return false

        pingPong.bindWrite()
        GLES30.glUseProgram(programId)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTex)
        GLES30.glUniform1i(locOriginalTex, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (hasOpticalMaps) cleanPlateTexId else inputTex)
        GLES30.glUniform1i(locCleanPlateTex, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (hasOpticalMaps) alphaMatteTexId else inputTex)
        GLES30.glUniform1i(locAlphaMatteTex, 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (hasOpticalMaps) depthMapTexId else inputTex)
        GLES30.glUniform1i(locDepthMapTex, 3)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (hasOpticalMaps) decontaminatedFgTexId else inputTex)
        GLES30.glUniform1i(locDecontaminatedFgTex, 4)

        val userFocal = if (blur.focalRangeStart > 0.01f || blur.focalRangeEnd < 0.99f) {
            ((blur.focalRangeStart + blur.focalRangeEnd) * 0.5f).coerceIn(0.05f, 0.95f)
        } else focalDepthS

        GLES30.glUniform1i(locHasOpticalMaps, if (hasOpticalMaps) 1 else 0)
        GLES30.glUniform1f(locBlurAmount, (blur.amount / 100f).coerceIn(0f, 1f))
        GLES30.glUniform1f(locFocalDepthS, userFocal)
        GLES30.glUniform1f(locBokehBoost, (blur.boost / 100f).coerceIn(0f, 1f))
        GLES30.glUniform1i(locBokehShape, bokehShapeToInt(blur.bokehShape))
        GLES30.glUniform1f(locNoiseSigma, noiseSigma)
        GLES30.glUniform1f(locImageAspect, imageWidth.coerceAtLeast(1).toFloat() / imageHeight.coerceAtLeast(1).toFloat())
        GLES30.glUniform2f(locDepthTexSize, depthTexWidth, depthTexHeight)
        GLES30.glUniform1i(locVisualizeDepth, if (visualizeDepth) 1 else 0)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        pingPong.swap()
        pingPong.unbind()
        return true
    }

    private fun uploadBitmapTex(bmp: Bitmap, nearest: Boolean): Int {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        val id = tex[0]
        if (id == 0) return 0
        val filter = if (nearest) GLES30.GL_NEAREST else GLES30.GL_LINEAR
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return id
    }

    fun release() {
        clearOpticalPack()
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        isInitialized = false
    }
}
