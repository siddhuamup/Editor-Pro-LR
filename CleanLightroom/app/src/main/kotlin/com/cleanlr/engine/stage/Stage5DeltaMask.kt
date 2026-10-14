package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper

/**
 * Stage 5: Masking & Local Adjustments
 * - Composites local adjustments (AI Sky, Subject, Linear/Radial Gradient, Brush) onto the base image.
 * - Uses Lightroom's deltamask.fsh logic:
 *     diff = min(Adjusted - Original, 4.0)
 *     FinalPixel = Original + M(x, y) * diff
 * - Implements Skip-disabled-stages rule: When no mask is active, returns false immediately
 *   without any GPU render passes, maintaining 120 FPS.
 */
class Stage5DeltaMask(private val context: Context) {

    companion object {
        private const val TAG = "Stage5DeltaMask"
    }

    private var programId = 0
    private var locBaseTexture = -1
    private var locAdjustedTexture = -1
    private var locMaskTexture = -1
    private var locHasActiveMask = -1
    private var locMaskIntensity = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage5_deltamask.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 5 Delta Mask shader")
            return
        }

        locBaseTexture = GLES30.glGetUniformLocation(programId, "uBaseTexture")
        locAdjustedTexture = GLES30.glGetUniformLocation(programId, "uAdjustedTexture")
        locMaskTexture = GLES30.glGetUniformLocation(programId, "uMaskTexture")
        locHasActiveMask = GLES30.glGetUniformLocation(programId, "uHasActiveMask")
        locMaskIntensity = GLES30.glGetUniformLocation(programId, "uMaskIntensity")

        isInitialized = true
        Log.i(TAG, "Stage 5 Delta Mask initialized successfully")
    }

    /**
     * Determines whether Stage 5 can be bypassed (Skip-disabled-stages rule).
     */
    fun shouldSkip(hasActiveMask: Boolean, maskIntensity: Float): Boolean {
        return !hasActiveMask || maskIntensity <= 0.001f
    }

    /**
     * Executes Delta Masking composite pass.
     * @param adjustedTextureId Texture ID containing the locally adjusted layer
     * @param maskTextureId Texture ID containing single-channel mask M(x, y) in [0.0 .. 1.0]
     * @param maskIntensity Weight/opacity of the mask
     * @return true if rendered, false if skipped
     */
    fun process(
        hasActiveMask: Boolean,
        adjustedTextureId: Int,
        maskTextureId: Int,
        maskIntensity: Float,
        pingPong: PingPongFbo,
        vaoId: Int
    ): Boolean {
        if (!isInitialized) return false

        if (shouldSkip(hasActiveMask, maskIntensity)) {
            // Skipped to maintain 120 FPS!
            return false
        }

        val baseTextureId = pingPong.currentRead?.textureId ?: return false
        if (adjustedTextureId == 0 || maskTextureId == 0) return false

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Texture Unit 0: Base image
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, baseTextureId)
        GLES30.glUniform1i(locBaseTexture, 0)

        // Texture Unit 1: Adjusted layer
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, adjustedTextureId)
        GLES30.glUniform1i(locAdjustedTexture, 1)

        // Texture Unit 2: Mask M(x, y)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
        GLES30.glUniform1i(locMaskTexture, 2)

        GLES30.glUniform1i(locHasActiveMask, 1)
        GLES30.glUniform1f(locMaskIntensity, maskIntensity.coerceIn(0.0f, 1.0f))

        // Draw Quad
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
