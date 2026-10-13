package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import kotlin.math.abs

/**
 * Stage 3: Exposure & Dynamic Range Mapping
 * - Applies linear exposure gain: col * 2^EV.
 * - Computes ProPhoto RGB luminance using authentic Lightroom weights (0.25 R + 0.50 G + 0.25 B).
 * - Transforms luminance to Log2 dynamic range space using greylog2.fsh flare offset (2^-12).
 * - Recovers highlights using knee-curve compression.
 * - Lifts shadows without amplifying deep sensor noise floor.
 * - Adjusts whites, blacks, and mid-gray log2 contrast.
 * - Preserves chromaticity by scaling RGB by the adjusted luminance ratio.
 * - Implements Skip-disabled-stages rule for 120 FPS when all tone parameters are at default zero.
 */
class Stage3ExposureTone(private val context: Context) {

    companion object {
        private const val TAG = "Stage3ExposureTone"
        private const val EPSILON = 0.001f
    }

    private var programId = 0
    private var locImageTexture = -1
    private var locExposure = -1
    private var locContrast = -1
    private var locHighlights = -1
    private var locShadows = -1
    private var locWhites = -1
    private var locBlacks = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage3_exposure_tone.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 3 Exposure Tone shader")
            return
        }

        locImageTexture = GLES30.glGetUniformLocation(programId, "uImageTexture")
        locExposure = GLES30.glGetUniformLocation(programId, "uExposure")
        locContrast = GLES30.glGetUniformLocation(programId, "uContrast")
        locHighlights = GLES30.glGetUniformLocation(programId, "uHighlights")
        locShadows = GLES30.glGetUniformLocation(programId, "uShadows")
        locWhites = GLES30.glGetUniformLocation(programId, "uWhites")
        locBlacks = GLES30.glGetUniformLocation(programId, "uBlacks")

        isInitialized = true
        Log.i(TAG, "Stage 3 Exposure Tone initialized successfully")
    }

    /**
     * Determines whether Stage 3 can be safely bypassed (Skip-disabled-stages rule).
     */
    fun shouldSkip(
        exposure: Float,
        contrast: Float,
        highlights: Float,
        shadows: Float,
        whites: Float,
        blacks: Float
    ): Boolean {
        return abs(exposure) < EPSILON &&
               abs(contrast) < EPSILON &&
               abs(highlights) < EPSILON &&
               abs(shadows) < EPSILON &&
               abs(whites) < EPSILON &&
               abs(blacks) < EPSILON
    }

    /**
     * Executes Stage 3 processing into the Ping-Pong FBO.
     * Returns true if rendered, or false if skipped.
     */
    fun process(
        exposure: Float,
        contrast: Float,
        highlights: Float,
        shadows: Float,
        whites: Float,
        blacks: Float,
        pingPong: PingPongFbo,
        vaoId: Int
    ): Boolean {
        if (!isInitialized) return false

        if (shouldSkip(exposure, contrast, highlights, shadows, whites, blacks)) {
            // Skipped to maintain 120 FPS!
            return false
        }

        val inputTexture = pingPong.currentRead?.textureId ?: return false

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Texture Unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(locImageTexture, 0)

        GLES30.glUniform1f(locExposure, exposure)
        GLES30.glUniform1f(locContrast, contrast)
        GLES30.glUniform1f(locHighlights, highlights)
        GLES30.glUniform1f(locShadows, shadows)
        GLES30.glUniform1f(locWhites, whites)
        GLES30.glUniform1f(locBlacks, blacks)

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
