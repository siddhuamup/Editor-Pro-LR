package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import kotlin.math.abs

/**
 * Stage 4: Presence & Multi-Scale Filtering
 * - Texture: High-frequency detail boost for fine skin pores and micro-contrast.
 * - Clarity: Mid-frequency local Laplacian contrast using authentic triangle function blending (locallaplacian.fsh).
 * - Dehaze: Atmospheric scattering inversion using Dark Channel Prior (DCP).
 * - Implements Skip-disabled-stages rule for 120 FPS when all three sliders are at zero.
 */
class Stage4Presence(private val context: Context) {

    companion object {
        private const val TAG = "Stage4Presence"
        private const val EPSILON = 0.01f
    }

    private var programId = 0
    private var locImageTexture = -1
    private var locTexelSize = -1
    private var locClarity = -1
    private var locTexture = -1
    private var locDehaze = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage4_presence.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 4 Presence shader")
            return
        }

        locImageTexture = GLES30.glGetUniformLocation(programId, "uImageTexture")
        locTexelSize = GLES30.glGetUniformLocation(programId, "uTexelSize")
        locClarity = GLES30.glGetUniformLocation(programId, "uClarity")
        locTexture = GLES30.glGetUniformLocation(programId, "uTexture")
        locDehaze = GLES30.glGetUniformLocation(programId, "uDehaze")

        isInitialized = true
        Log.i(TAG, "Stage 4 Presence initialized successfully")
    }

    /**
     * Determines whether Stage 4 can be safely bypassed (Skip-disabled-stages rule).
     */
    fun shouldSkip(clarity: Float, texture: Float, dehaze: Float): Boolean {
        return abs(clarity) < EPSILON &&
               abs(texture) < EPSILON &&
               abs(dehaze) < EPSILON
    }

    /**
     * Executes Stage 4 processing into the Ping-Pong FBO.
     * Returns true if rendered, or false if skipped.
     */
    fun process(
        clarity: Float,
        texture: Float,
        dehaze: Float,
        pingPong: PingPongFbo,
        vaoId: Int
    ): Boolean {
        if (!isInitialized) return false

        if (shouldSkip(clarity, texture, dehaze)) {
            // Skipped to maintain 120 FPS!
            return false
        }

        val inputTarget = pingPong.currentRead ?: return false
        val width = inputTarget.width
        val height = inputTarget.height
        if (width <= 0 || height <= 0) return false

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Texture Unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTarget.textureId)
        GLES30.glUniform1i(locImageTexture, 0)

        // Set texel size for multi-scale sampling
        GLES30.glUniform2f(locTexelSize, 1.0f / width, 1.0f / height)

        GLES30.glUniform1f(locClarity, clarity)
        GLES30.glUniform1f(locTexture, texture)
        GLES30.glUniform1f(locDehaze, dehaze)

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
