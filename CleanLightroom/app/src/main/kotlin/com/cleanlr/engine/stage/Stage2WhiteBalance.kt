package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.ColorScience
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import kotlin.math.abs

/**
 * Stage 2: Camera Calibration & White Balance
 * - Computes Bradford Chromatic Adaptation Transform (CAT) channel multipliers from Kelvin (2000K-50000K) & Tint (-150..+150).
 * - Multiplies linear ProPhoto RGB channels: R' = R * W_R, G' = G * W_G, B' = B * W_B.
 * - Implements strict 'Skip-disabled-stages rule' when gains are at identity (5500K daylight, 0 tint)
 *   to preserve 120 FPS and avoid unnecessary FBO ping-pong passes.
 */
class Stage2WhiteBalance(private val context: Context) {

    companion object {
        private const val TAG = "Stage2WhiteBalance"
        private const val SKIP_EPSILON = 0.001f
    }

    private var programId = 0
    private var locImageTexture = -1
    private var locWhiteBalanceGains = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage2_white_balance.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 2 White Balance shader")
            return
        }

        locImageTexture = GLES30.glGetUniformLocation(programId, "uImageTexture")
        locWhiteBalanceGains = GLES30.glGetUniformLocation(programId, "uWhiteBalanceGains")

        isInitialized = true
        Log.i(TAG, "Stage 2 White Balance initialized successfully")
    }

    /**
     * Determines whether Stage 2 can be safely bypassed (Skip-disabled-stages rule).
     */
    fun shouldSkip(gains: FloatArray): Boolean {
        if (gains.size < 3) return true
        return abs(gains[0] - 1.0f) < SKIP_EPSILON &&
               abs(gains[1] - 1.0f) < SKIP_EPSILON &&
               abs(gains[2] - 1.0f) < SKIP_EPSILON
    }

    /**
     * Executes White Balance pass.
     * Returns true if the stage was rendered (ping-pong swapped), or false if skipped.
     */
    fun process(
        temperature: Float,
        tint: Float,
        pingPong: PingPongFbo,
        vaoId: Int
    ): Boolean {
        if (!isInitialized) return false

        val gains = ColorScience.kelvinTintToRgbGain(temperature, tint)
        if (shouldSkip(gains)) {
            // Stage skipped to maintain 120 FPS!
            return false
        }

        val inputTexture = pingPong.currentRead?.textureId ?: return false

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Texture Unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(locImageTexture, 0)

        GLES30.glUniform3f(locWhiteBalanceGains, gains[0], gains[1], gains[2])

        // Draw Full-screen Quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        // Swap ping-pong so Stage 2 output is the next stage's input
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
