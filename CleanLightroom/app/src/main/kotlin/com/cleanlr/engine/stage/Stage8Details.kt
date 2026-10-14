package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import com.cleanlr.model.DetailAdjustments
import kotlin.math.abs

/**
 * Stage 8: Details & Effects
 * - Unsharp Mask Sharpening with Edge Masking
 * - Cosmetic Vignette Falloff
 * - Procedural Film Grain (Inverse-Luminance Modulated)
 * - Skip-disabled-stages rule: Skipped when no details or effects are active to maintain 120 FPS.
 */
class Stage8Details(private val context: Context) {

    companion object {
        private const val TAG = "Stage8Details"
        private const val EPSILON = 0.001f
    }

    private var programId = 0
    private var locInputTexture = -1
    private var locTexelSize = -1
    private var locSharpenAmount = -1
    private var locSharpenRadius = -1
    private var locSharpenMasking = -1
    private var locVignetteAmount = -1
    private var locVignetteMidpoint = -1
    private var locVignetteFeather = -1
    private var locGrainAmount = -1
    private var locGrainSize = -1
    private var locTimeSeed = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage8_details.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 8 Details shader")
            return
        }

        locInputTexture = GLES30.glGetUniformLocation(programId, "uInputTexture")
        locTexelSize = GLES30.glGetUniformLocation(programId, "uTexelSize")
        locSharpenAmount = GLES30.glGetUniformLocation(programId, "uSharpenAmount")
        locSharpenRadius = GLES30.glGetUniformLocation(programId, "uSharpenRadius")
        locSharpenMasking = GLES30.glGetUniformLocation(programId, "uSharpenMasking")
        locVignetteAmount = GLES30.glGetUniformLocation(programId, "uVignetteAmount")
        locVignetteMidpoint = GLES30.glGetUniformLocation(programId, "uVignetteMidpoint")
        locVignetteFeather = GLES30.glGetUniformLocation(programId, "uVignetteFeather")
        locGrainAmount = GLES30.glGetUniformLocation(programId, "uGrainAmount")
        locGrainSize = GLES30.glGetUniformLocation(programId, "uGrainSize")
        locTimeSeed = GLES30.glGetUniformLocation(programId, "uTimeSeed")

        isInitialized = true
        Log.d(TAG, "Stage 8 Details & Effects initialized successfully")
    }

    /**
     * Determines whether Stage 8 has active adjustments.
     * Skip-disabled-stages rule: Returns false if sharpening, vignette, and grain are zero.
     */
    fun shouldSkip(detail: DetailAdjustments): Boolean {
        return abs(detail.sharpening) < EPSILON &&
               abs(detail.vignetteAmount) < EPSILON &&
               abs(detail.grainAmount) < EPSILON
    }

    /**
     * Executes Stage 8 on the PingPong FBO chain.
     * Returns true if rendered, or false if skipped.
     */
    fun process(
        detail: DetailAdjustments,
        pingPong: PingPongFbo,
        vaoId: Int,
        seed: Float = 0.0f
    ): Boolean {
        if (!isInitialized) return false

        if (shouldSkip(detail)) {
            // Skipped to maintain 120 FPS!
            return false
        }

        val inputTexture = pingPong.currentRead?.textureId ?: return false
        val width = pingPong.currentRead?.width ?: 1
        val height = pingPong.currentRead?.height ?: 1

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Bind input texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(locInputTexture, 0)

        // Set texel size for convolution kernels
        val texelWidth = 1.0f / width.coerceAtLeast(1)
        val texelHeight = 1.0f / height.coerceAtLeast(1)
        GLES30.glUniform2f(locTexelSize, texelWidth, texelHeight)

        // Sharpening uniforms (normalized)
        GLES30.glUniform1f(locSharpenAmount, detail.sharpening / 100.0f)
        GLES30.glUniform1f(locSharpenRadius, detail.sharpenRadius.coerceIn(0.5f, 3.0f))
        GLES30.glUniform1f(locSharpenMasking, detail.masking / 100.0f)

        // Vignette uniforms
        GLES30.glUniform1f(locVignetteAmount, detail.vignetteAmount / 100.0f)
        GLES30.glUniform1f(locVignetteMidpoint, (detail.vignetteMidpoint / 100.0f).coerceIn(0.0f, 1.0f))
        GLES30.glUniform1f(locVignetteFeather, (detail.vignetteFeather / 100.0f).coerceIn(0.01f, 1.0f))

        // Grain uniforms
        GLES30.glUniform1f(locGrainAmount, detail.grainAmount / 100.0f)
        val mappedGrainSize = 1.0f + (detail.grainSize / 100.0f) * 3.0f
        GLES30.glUniform1f(locGrainSize, mappedGrainSize)
        GLES30.glUniform1f(locTimeSeed, seed)

        // Draw quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        // Swap ping-pong FBO targets
        pingPong.swap()
        pingPong.unbind()

        return true
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        isInitialized = false
    }
}
