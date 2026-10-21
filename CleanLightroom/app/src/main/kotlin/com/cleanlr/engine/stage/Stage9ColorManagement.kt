package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper

/**
 * Stage 9: Color Management & Output Display
 * - Color space conversion: ProPhoto RGB Linear -> Target Display Gamut (sRGB or Display P3)
 * - EOTF / Gamma transfer curve: lin2srgb.fsh
 * - Final presentation to Android SurfaceView / TextureView (FBO 0) or output FBO
 */
class Stage9ColorManagement(private val context: Context) {

    companion object {
        private const val TAG = "Stage9ColorManagement"

        const val GAMUT_SRGB = 0
        const val GAMUT_DISPLAY_P3 = 1
    }

    private var programId = 0
    private var locInputTexture = -1
    private var locTargetGamut = -1
    private var locAltClippingMode = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage9_color_management.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 9 Color Management shader")
            return
        }

        locInputTexture = GLES30.glGetUniformLocation(programId, "uInputTexture")
        locTargetGamut = GLES30.glGetUniformLocation(programId, "uTargetGamut")
        locAltClippingMode = GLES30.glGetUniformLocation(programId, "uAltClippingMode")

        isInitialized = true
        Log.d(TAG, "Stage 9 Color Management initialized successfully")
    }

    /**
     * Renders the final ProPhoto linear image directly to the target framebuffer (e.g. screen FBO 0).
     */
    fun renderToScreen(
        inputTextureId: Int,
        targetFbo: Int,
        targetGamut: Int = GAMUT_SRGB,
        viewportWidth: Int,
        viewportHeight: Int,
        vaoId: Int,
        altClippingMode: Int = 0,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ) {
        if (!isInitialized || inputTextureId <= 0) return

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)

        // Tip 81: Calculate aspect-ratio Fit-Center viewport to prevent photo distortion
        if (imageWidth > 0 && imageHeight > 0 && viewportWidth > 0 && viewportHeight > 0) {
            val scale = minOf(viewportWidth.toFloat() / imageWidth, viewportHeight.toFloat() / imageHeight)
            val fitW = (imageWidth * scale).toInt()
            val fitH = (imageHeight * scale).toInt()
            val offX = (viewportWidth - fitW) / 2
            val offY = (viewportHeight - fitH) / 2
            GLES30.glViewport(offX, offY, fitW, fitH)
        } else {
            GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        }

        GLES30.glUseProgram(programId)

        // Texture Unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTextureId)
        GLES30.glUniform1i(locInputTexture, 0)

        // Target Gamut (0 = sRGB, 1 = Display P3)
        GLES30.glUniform1i(locTargetGamut, targetGamut)
        GLES30.glUniform1i(locAltClippingMode, altClippingMode)

        // Draw quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Renders the color-managed result into the PingPong FBO chain for offscreen export / caching.
     */
    fun process(
        pingPong: PingPongFbo,
        targetGamut: Int = GAMUT_SRGB,
        vaoId: Int,
        altClippingMode: Int = 0
    ): Boolean {
        if (!isInitialized) return false

        val inputTexture = pingPong.currentRead?.textureId ?: return false

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(locInputTexture, 0)

        GLES30.glUniform1i(locTargetGamut, targetGamut)
        GLES30.glUniform1i(locAltClippingMode, altClippingMode)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

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
