package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import com.cleanlr.model.ColorGrading
import com.cleanlr.model.HslAdjustments
import com.cleanlr.model.HslChannel
import kotlin.math.abs

/**
 * Stage 6: Color Science & Color Mixer
 * - 8-Channel HSL Mixer (Gaussian Bell Curves on Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta)
 * - 3-Way Color Grading Wheels (Shadows, Midtones, Highlights)
 * - Smart Vibrance with Skin Protection (preserves skin tones within +-5% while boosting unsaturated sky up to +40%)
 * - Skip-disabled-stages rule: Skipped when no color adjustments are active to preserve 120 FPS.
 */
class Stage6ColorScience(private val context: Context) {

    companion object {
        private const val TAG = "Stage6ColorScience"
        private const val EPSILON = 0.01f
    }

    private var programId = 0
    private var locImageTexture = -1
    private var locGradeShadows = -1
    private var locGradeMidtones = -1
    private var locGradeHighlights = -1
    private var locGradeBlending = -1
    private var locGradeBalance = -1
    private var locHslRed = -1
    private var locHslOrange = -1
    private var locHslYellow = -1
    private var locHslGreen = -1
    private var locHslAqua = -1
    private var locHslBlue = -1
    private var locHslPurple = -1
    private var locHslMagenta = -1
    private var locVibrance = -1
    private var locSaturation = -1

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage6_color_science.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 6 Color Science shader")
            return
        }

        locImageTexture = GLES30.glGetUniformLocation(programId, "uImageTexture")
        locGradeShadows = GLES30.glGetUniformLocation(programId, "uGradeShadows")
        locGradeMidtones = GLES30.glGetUniformLocation(programId, "uGradeMidtones")
        locGradeHighlights = GLES30.glGetUniformLocation(programId, "uGradeHighlights")
        locGradeBlending = GLES30.glGetUniformLocation(programId, "uGradeBlending")
        locGradeBalance = GLES30.glGetUniformLocation(programId, "uGradeBalance")

        locHslRed = GLES30.glGetUniformLocation(programId, "uHslRed")
        locHslOrange = GLES30.glGetUniformLocation(programId, "uHslOrange")
        locHslYellow = GLES30.glGetUniformLocation(programId, "uHslYellow")
        locHslGreen = GLES30.glGetUniformLocation(programId, "uHslGreen")
        locHslAqua = GLES30.glGetUniformLocation(programId, "uHslAqua")
        locHslBlue = GLES30.glGetUniformLocation(programId, "uHslBlue")
        locHslPurple = GLES30.glGetUniformLocation(programId, "uHslPurple")
        locHslMagenta = GLES30.glGetUniformLocation(programId, "uHslMagenta")

        locVibrance = GLES30.glGetUniformLocation(programId, "uVibrance")
        locSaturation = GLES30.glGetUniformLocation(programId, "uSaturation")

        isInitialized = true
        Log.i(TAG, "Stage 6 Color Science initialized successfully")
    }

    /**
     * Determines whether Stage 6 can be safely bypassed (Skip-disabled-stages rule).
     */
    fun shouldSkip(
        hsl: HslAdjustments,
        grading: ColorGrading,
        vibrance: Float,
        saturation: Float
    ): Boolean {
        if (abs(vibrance) > EPSILON || abs(saturation) > EPSILON) return false

        if (grading.shadows.saturation > EPSILON ||
            grading.midtones.saturation > EPSILON ||
            grading.highlights.saturation > EPSILON
        ) return false

        return isChannelZero(hsl.red) &&
               isChannelZero(hsl.orange) &&
               isChannelZero(hsl.yellow) &&
               isChannelZero(hsl.green) &&
               isChannelZero(hsl.aqua) &&
               isChannelZero(hsl.blue) &&
               isChannelZero(hsl.purple) &&
               isChannelZero(hsl.magenta)
    }

    private fun isChannelZero(ch: HslChannel): Boolean {
        return abs(ch.hue) < EPSILON && abs(ch.saturation) < EPSILON && abs(ch.luminance) < EPSILON
    }

    /**
     * Executes Stage 6 color processing into the Ping-Pong FBO.
     * Returns true if rendered, or false if skipped.
     */
    fun process(
        hsl: HslAdjustments,
        grading: ColorGrading,
        vibrance: Float,
        saturation: Float,
        pingPong: PingPongFbo,
        vaoId: Int
    ): Boolean {
        if (!isInitialized) return false

        if (shouldSkip(hsl, grading, vibrance, saturation)) {
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

        // Color Grading Wheels (Normalized: hue [0..1], sat [0..1], lum [-1..1], blend [0..1], balance [-1..1])
        GLES30.glUniform3f(
            locGradeShadows,
            (grading.shadows.hue / 360.0f) % 1.0f,
            grading.shadows.saturation / 100.0f,
            grading.shadows.luminance / 100.0f
        )
        GLES30.glUniform3f(
            locGradeMidtones,
            (grading.midtones.hue / 360.0f) % 1.0f,
            grading.midtones.saturation / 100.0f,
            grading.midtones.luminance / 100.0f
        )
        GLES30.glUniform3f(
            locGradeHighlights,
            (grading.highlights.hue / 360.0f) % 1.0f,
            grading.highlights.saturation / 100.0f,
            grading.highlights.luminance / 100.0f
        )
        GLES30.glUniform1f(locGradeBlending, (grading.blending / 100.0f).coerceIn(0f, 1f))
        GLES30.glUniform1f(locGradeBalance, (grading.balance / 100.0f).coerceIn(-1f, 1f))

        // 8 HSL Channels
        setChannelUniform(locHslRed, hsl.red)
        setChannelUniform(locHslOrange, hsl.orange)
        setChannelUniform(locHslYellow, hsl.yellow)
        setChannelUniform(locHslGreen, hsl.green)
        setChannelUniform(locHslAqua, hsl.aqua)
        setChannelUniform(locHslBlue, hsl.blue)
        setChannelUniform(locHslPurple, hsl.purple)
        setChannelUniform(locHslMagenta, hsl.magenta)

        // Vibrance & Saturation
        GLES30.glUniform1f(locVibrance, vibrance)
        GLES30.glUniform1f(locSaturation, saturation)

        // Draw Quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        // Swap ping-pong FBO targets
        pingPong.swap()
        pingPong.unbind()

        return true
    }

    private fun setChannelUniform(location: Int, ch: HslChannel) {
        if (location != -1) {
            GLES30.glUniform3f(location, ch.hue, ch.saturation, ch.luminance)
        }
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        isInitialized = false
    }
}
