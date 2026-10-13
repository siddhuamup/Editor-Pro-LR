package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper

/**
 * Stage 1: Ingestion & Linearization
 * - Inverts sRGB non-linear gamma to linear radiance space (srgb2lin)
 * - Multiplies with the 3x3 Adobe ProPhoto RGB (ROMM RGB) Bradford Adaptation Matrix
 * - Preserves exact neutral gray (R=G=B in -> R=G=B out)
 * - Optionally inverts baked camera curves via evalUndoRenderCurve
 * - Writes linear ProPhoto RGB into the RGBA16F Ping-Pong FBO
 */
class Stage1Linearize(private val context: Context) {

    companion object {
        private const val TAG = "Stage1Linearize"
    }

    private var programId = 0
    private var locImageTexture = -1
    private var locIsInputLinear = -1
    private var locEnableUndoCurve = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage1_linearize.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 1 Linearization shader")
            return
        }

        locImageTexture = GLES30.glGetUniformLocation(programId, "uImageTexture")
        locIsInputLinear = GLES30.glGetUniformLocation(programId, "uIsInputLinear")
        locEnableUndoCurve = GLES30.glGetUniformLocation(programId, "uEnableUndoCurve")

        isInitialized = true
        Log.i(TAG, "Stage 1 Linearize initialized successfully")
    }

    /**
     * Executes Stage 1 processing.
     * Takes an input source texture and renders the linearized ProPhoto RGB result into pingPong.
     */
    fun process(
        inputTextureId: Int,
        pingPong: PingPongFbo,
        vaoId: Int,
        isLinearInput: Boolean = false,
        enableUndoCurve: Boolean = false
    ) {
        if (!isInitialized || inputTextureId == 0) return

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Texture Unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTextureId)
        GLES30.glUniform1i(locImageTexture, 0)

        GLES30.glUniform1i(locIsInputLinear, if (isLinearInput) 1 else 0)
        GLES30.glUniform1i(locEnableUndoCurve, if (enableUndoCurve) 1 else 0)

        // Draw Full-screen Quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        // Swap ping-pong so Stage 1 output becomes the next stage's input
        pingPong.swap()
        pingPong.unbind()
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        isInitialized = false
    }
}
