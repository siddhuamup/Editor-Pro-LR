package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import com.cleanlr.model.ToneCurves
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Stage 7: Curves & Creative LUTs
 * - Evaluates Cubic Spline Monotone Tone Curves (via 256-sample LUT).
 * - Evaluates Creative 3D LUTs (33x33x33 or 64x64x64) via native hardware trilinear interpolation.
 * - Skip-disabled-stages rule: When curve is linear/identity and no 3D LUT is active,
 *   the stage is completely bypassed to maintain 120 FPS.
 */
class Stage7CurvesLut(private val context: Context) {

    companion object {
        private const val TAG = "Stage7CurvesLut"
        private const val EPSILON = 0.001f
    }

    private var programId = 0
    private var locImageTexture = -1
    private var locToneCurveLut = -1
    private var locEnableCurve = -1
    private var locLut3D = -1
    private var locHasLut3D = -1
    private var locLut3DIntensity = -1

    private var defaultCurveLutTexId = 0
    private var default3dLutTexId = 0

    var isInitialized = false
        private set

    fun initGL() {
        if (isInitialized) return

        programId = ShaderHelper.createProgram(
            context,
            "shaders/blit.vert",
            "shaders/stage7_curves_lut.frag"
        )

        if (programId == 0) {
            Log.e(TAG, "Failed to compile Stage 7 Curves & LUT shader")
            return
        }

        locImageTexture = GLES30.glGetUniformLocation(programId, "uImageTexture")
        locToneCurveLut = GLES30.glGetUniformLocation(programId, "uToneCurveLut")
        locEnableCurve = GLES30.glGetUniformLocation(programId, "uEnableCurve")
        locLut3D = GLES30.glGetUniformLocation(programId, "uLut3D")
        locHasLut3D = GLES30.glGetUniformLocation(programId, "uHasLut3D")
        locLut3DIntensity = GLES30.glGetUniformLocation(programId, "uLut3DIntensity")

        setupDefaultTextures()

        isInitialized = true
        Log.i(TAG, "Stage 7 Curves & LUT initialized successfully")
    }

    private fun setupDefaultTextures() {
        // 1. 256x1 1D Identity Curve LUT
        val curveTex = IntArray(1)
        GLES30.glGenTextures(1, curveTex, 0)
        defaultCurveLutTexId = curveTex[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, defaultCurveLutTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val identityCurve = ByteArray(256) { it.toByte() }
        val curveBuf = ByteBuffer.allocateDirect(256).apply {
            put(identityCurve)
            position(0)
        }
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 256, 1, 0,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, curveBuf
        )

        // 2. 2x2x2 Identity 3D LUT Texture (RGB Cube corners)
        val lut3dTex = IntArray(1)
        GLES30.glGenTextures(1, lut3dTex, 0)
        default3dLutTexId = lut3dTex[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, default3dLutTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        // 2x2x2 = 8 voxels of RGBA8
        val cubeData = ByteArray(2 * 2 * 2 * 4)
        var idx = 0
        for (b in 0..1) {
            for (g in 0..1) {
                for (r in 0..1) {
                    cubeData[idx++] = (r * 255).toByte()
                    cubeData[idx++] = (g * 255).toByte()
                    cubeData[idx++] = (b * 255).toByte()
                    cubeData[idx++] = 255.toByte()
                }
            }
        }
        val cubeBuf = ByteBuffer.allocateDirect(cubeData.size).order(ByteOrder.nativeOrder()).apply {
            put(cubeData)
            position(0)
        }
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA8, 2, 2, 2, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, cubeBuf
        )

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    /**
     * Updates the 256-sample curve LUT texture.
     */
    fun updateToneCurveData(lutData: ByteArray) {
        if (defaultCurveLutTexId == 0 || lutData.size < 256) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, defaultCurveLutTexId)
        val buf = ByteBuffer.allocateDirect(256).apply {
            put(lutData, 0, 256)
            position(0)
        }
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0, 256, 1,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * Determines whether Stage 7 can be safely bypassed (Skip-disabled-stages rule).
     */
    fun shouldSkip(
        curves: ToneCurves,
        hasCustom3dLut: Boolean,
        lut3dIntensity: Float
    ): Boolean {
        val hasCurveAdjustments = abs(curves.parametricHighlights) > EPSILON ||
                abs(curves.parametricLights) > EPSILON ||
                abs(curves.parametricDarks) > EPSILON ||
                abs(curves.parametricShadows) > EPSILON ||
                curves.pointCurve.size > 2

        val has3dLutActive = hasCustom3dLut && lut3dIntensity > EPSILON

        return !hasCurveAdjustments && !has3dLutActive
    }

    /**
     * Executes Stage 7 processing into the Ping-Pong FBO.
     * Returns true if rendered, or false if skipped.
     */
    fun process(
        curves: ToneCurves,
        curveTexId: Int = defaultCurveLutTexId,
        custom3dLutTexId: Int = 0,
        lut3dIntensity: Float = 0.0f,
        pingPong: PingPongFbo,
        vaoId: Int
    ): Boolean {
        if (!isInitialized) return false

        val has3dLut = custom3dLutTexId != 0
        if (shouldSkip(curves, has3dLut, lut3dIntensity)) {
            // Skipped to maintain 120 FPS!
            return false
        }

        val inputTexture = pingPong.currentRead?.textureId ?: return false

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Texture Unit 0: Input image
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(locImageTexture, 0)

        // Texture Unit 1: 1D Tone Curve LUT
        val activeCurveTex = if (curveTexId != 0) curveTexId else defaultCurveLutTexId
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, activeCurveTex)
        GLES30.glUniform1i(locToneCurveLut, 1)

        val hasCurveAdjustments = abs(curves.parametricHighlights) > EPSILON ||
                abs(curves.parametricLights) > EPSILON ||
                abs(curves.parametricDarks) > EPSILON ||
                abs(curves.parametricShadows) > EPSILON ||
                curves.pointCurve.size > 2
        GLES30.glUniform1i(locEnableCurve, if (hasCurveAdjustments) 1 else 0)

        // Texture Unit 2: 3D LUT
        val active3dLutTex = if (custom3dLutTexId != 0) custom3dLutTexId else default3dLutTexId
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, active3dLutTex)
        GLES30.glUniform1i(locLut3D, 2)
        GLES30.glUniform1i(locHasLut3D, if (has3dLut) 1 else 0)
        GLES30.glUniform1f(locLut3DIntensity, lut3dIntensity.coerceIn(0.0f, 1.0f))

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
        if (defaultCurveLutTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(defaultCurveLutTexId), 0)
            defaultCurveLutTexId = 0
        }
        if (default3dLutTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(default3dLutTexId), 0)
            default3dLutTexId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        isInitialized = false
    }
}
