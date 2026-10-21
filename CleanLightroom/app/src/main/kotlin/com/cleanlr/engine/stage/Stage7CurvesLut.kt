package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import com.cleanlr.engine.SplineInterpolator
import com.cleanlr.model.CurvePoint
import com.cleanlr.model.ToneCurves
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Stage 7: Curves & Creative LUTs
 * - Evaluates Chained Tone Curves:
 *   1. 4-Zone Parametric Tone Curve (Highlights/Lights/Darks/Shadows -> 5 nodes -> Fritsch-Carlson)
 *   2. Point Tone Curve (User control points spline)
 *   Special rule: Parametric curve and point curve are kept as separate LUTs and chained in shader!
 * - Evaluates Creative 3D LUTs (33x33x33 or 64x64x64) via hardware trilinear interpolation.
 * - Skip-disabled-stages rule: When curves are neutral/linear and no 3D LUT is active,
 *   the stage is completely bypassed to maintain 120 FPS.
 */
class Stage7CurvesLut(private val context: Context) {

    companion object {
        private const val TAG = "Stage7CurvesLut"
        private const val EPSILON = 0.001f
    }

    private var programId = 0
    private var locImageTexture = -1
    private var locParametricCurveLut = -1
    private var locEnableParametricCurve = -1
    private var locPointCurveLut = -1
    private var locEnablePointCurve = -1
    private var locLut3D = -1
    private var locHasLut3D = -1
    private var locLut3DIntensity = -1

    private var defaultParametricLutTexId = 0
    private var defaultPointLutTexId = 0
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
        locParametricCurveLut = GLES30.glGetUniformLocation(programId, "uParametricCurveLut")
        locEnableParametricCurve = GLES30.glGetUniformLocation(programId, "uEnableParametricCurve")
        locPointCurveLut = GLES30.glGetUniformLocation(programId, "uPointCurveLut")
        locEnablePointCurve = GLES30.glGetUniformLocation(programId, "uEnablePointCurve")
        locLut3D = GLES30.glGetUniformLocation(programId, "uLut3D")
        locHasLut3D = GLES30.glGetUniformLocation(programId, "uHasLut3D")
        locLut3DIntensity = GLES30.glGetUniformLocation(programId, "uLut3DIntensity")

        setupDefaultTextures()

        isInitialized = true
        Log.i(TAG, "Stage 7 Curves & LUT initialized successfully")
    }

    private fun setupDefaultTextures() {
        // 1. 256x1 1D Identity Parametric Curve LUT
        val pCurveTex = IntArray(1)
        GLES30.glGenTextures(1, pCurveTex, 0)
        defaultParametricLutTexId = pCurveTex[0]
        init1dLutTexture(defaultParametricLutTexId)

        // 2. Tip 162: 256x1 RGBA 4-Channel Point Curve LUT (R=Red, G=Green, B=Blue)
        val ptCurveTex = IntArray(1)
        GLES30.glGenTextures(1, ptCurveTex, 0)
        defaultPointLutTexId = ptCurveTex[0]
        initRgba1dLutTexture(defaultPointLutTexId)

        // 3. 2x2x2 Identity 3D LUT Texture (RGB Cube corners)
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

    private fun init1dLutTexture(textureId: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val identity = ByteArray(256) { it.toByte() }
        val buf = ByteBuffer.allocateDirect(256).apply {
            put(identity)
            position(0)
        }
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 256, 1, 0,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * Tip 162: 256x1 RGBA 1D LUT Texture initialization for 4-channel Point Curves
     */
    private fun initRgba1dLutTexture(textureId: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val identity = ByteArray(256 * 4) { idx ->
            val channel = idx % 4
            if (channel == 3) 255.toByte() else (idx / 4).toByte()
        }
        val buf = ByteBuffer.allocateDirect(identity.size).apply {
            put(identity)
            position(0)
        }
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 256, 1, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun update1dLutTexture(textureId: Int, lutData: ByteArray) {
        if (textureId == 0 || lutData.size < 256) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
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
     * Tip 162: Updates the Point Curve RGBA 4-channel LUT texture (256 * 4 bytes).
     */
    fun updatePointCurveData(lutData: ByteArray) {
        if (defaultPointLutTexId == 0 || lutData.size < 256 * 4) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, defaultPointLutTexId)
        val buf = ByteBuffer.allocateDirect(256 * 4).apply {
            put(lutData, 0, 256 * 4)
            position(0)
        }
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0, 256, 1,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * Updates the Parametric Curve LUT texture.
     */
    fun updateParametricCurveData(lutData: ByteArray) {
        update1dLutTexture(defaultParametricLutTexId, lutData)
    }

    fun isParametricActive(curves: ToneCurves): Boolean {
        return abs(curves.parametricHighlights) > EPSILON ||
               abs(curves.parametricLights) > EPSILON ||
               abs(curves.parametricDarks) > EPSILON ||
               abs(curves.parametricShadows) > EPSILON
    }

    private fun isCurveListActive(points: List<CurvePoint>): Boolean {
        if (points.size > 2) return true
        if (points.size == 2) {
            val p0 = points[0]
            val p1 = points[1]
            return abs(p0.x - 0f) > EPSILON || abs(p0.y - 0f) > EPSILON ||
                   abs(p1.x - 255f) > EPSILON || abs(p1.y - 255f) > EPSILON
        }
        return false
    }

    fun isPointCurveActive(curves: ToneCurves): Boolean {
        return isCurveListActive(curves.composite) ||
               isCurveListActive(curves.red) ||
               isCurveListActive(curves.green) ||
               isCurveListActive(curves.blue)
    }

    fun shouldSkip(
        curves: ToneCurves,
        hasCustom3dLut: Boolean,
        lut3dIntensity: Float
    ): Boolean {
        val has3dLutActive = hasCustom3dLut && lut3dIntensity > EPSILON
        return !isParametricActive(curves) && !isPointCurveActive(curves) && !has3dLutActive
    }

    /**
     * Executes Stage 7 processing into the Ping-Pong FBO.
     * Evaluates chained curves (Parametric -> Point Curve -> 3D LUT)
     * Returns true if rendered, or false if skipped.
     */
    fun process(
        curves: ToneCurves,
        customPointCurveTexId: Int = 0,
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

        val hasParametric = isParametricActive(curves)
        val hasPoint = isPointCurveActive(curves)

        // Upload LUT data when curves are active
        if (hasParametric) {
            val pLut = SplineInterpolator.generateParametricLut256(curves)
            updateParametricCurveData(pLut)
        }

        val activePointTex = if (customPointCurveTexId != 0) {
            customPointCurveTexId
        } else {
            if (hasPoint) {
                val rgbaLut = SplineInterpolator.generateRgbaLut256(curves)
                updatePointCurveData(rgbaLut)
            }
            defaultPointLutTexId
        }

        pingPong.bindWrite()

        GLES30.glUseProgram(programId)

        // Texture Unit 0: Input image
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(locImageTexture, 0)

        // Texture Unit 1: Parametric Curve 1D LUT
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, defaultParametricLutTexId)
        GLES30.glUniform1i(locParametricCurveLut, 1)
        GLES30.glUniform1i(locEnableParametricCurve, if (hasParametric) 1 else 0)

        // Texture Unit 2: Point Curve 1D LUT (CHAINED)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, activePointTex)
        GLES30.glUniform1i(locPointCurveLut, 2)
        GLES30.glUniform1i(locEnablePointCurve, if (hasPoint) 1 else 0)

        // Texture Unit 3: 3D LUT
        val active3dTex = if (has3dLut) custom3dLutTexId else default3dLutTexId
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, active3dTex)
        GLES30.glUniform1i(locLut3D, 3)
        GLES30.glUniform1i(locHasLut3D, if (has3dLut) 1 else 0)
        GLES30.glUniform1f(locLut3DIntensity, lut3dIntensity)

        // Draw quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        // Swap ping-pong targets
        pingPong.swap()
        pingPong.unbind()

        return true
    }

    fun release() {
        if (defaultParametricLutTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(defaultParametricLutTexId), 0)
            defaultParametricLutTexId = 0
        }
        if (defaultPointLutTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(defaultPointLutTexId), 0)
            defaultPointLutTexId = 0
        }
        if (default3dLutTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(default3dLutTexId), 0)
            default3dLutTexId = 0
        }
        film3dLutCache.values.forEach { id ->
            if (id != 0) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
        }
        film3dLutCache.clear()

        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        isInitialized = false
    }

    private val film3dLutCache = mutableMapOf<Film3dLut, Int>()

    /**
     * Tip 202: Retrieves or procedurally generates film emulation 3D LUT textures.
     */
    fun getFilm3dLutTexture(lut: Film3dLut): Int {
        if (lut == Film3dLut.NONE) return 0
        return film3dLutCache.getOrPut(lut) {
            generateProceduralFilm3dLut(lut)
        }
    }

    private fun generateProceduralFilm3dLut(lut: Film3dLut): Int {
        val size = 16
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        val texId = tex[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        val totalBytes = size * size * size * 4
        val data = ByteArray(totalBytes)
        var ptr = 0

        for (b in 0 until size) {
            val normB = b.toFloat() / (size - 1)
            for (g in 0 until size) {
                val normG = g.toFloat() / (size - 1)
                for (r in 0 until size) {
                    val normR = r.toFloat() / (size - 1)

                    var outR = normR
                    var outG = normG
                    var outB = normB

                    when (lut) {
                        Film3dLut.PORTRA_400 -> {
                            // Kodak Portra 400: Warm skin tones, pastel highlights
                            outR = (normR * 1.08f + 0.02f).coerceIn(0f, 1f)
                            outG = (normG * 1.02f + 0.01f).coerceIn(0f, 1f)
                            outB = (normB * 0.92f).coerceIn(0f, 1f)
                        }
                        Film3dLut.FUJI_VELVIA -> {
                            // Fuji Velvia: High saturation, vivid greens and deep blues
                            val luma = normR * 0.299f + normG * 0.587f + normB * 0.114f
                            outR = (luma + (normR - luma) * 1.35f).coerceIn(0f, 1f)
                            outG = (luma + (normG - luma) * 1.45f * 1.05f).coerceIn(0f, 1f)
                            outB = (luma + (normB - luma) * 1.40f * 1.08f).coerceIn(0f, 1f)
                        }
                        Film3dLut.CINEON_LOG -> {
                            // Cineon Log: Lifted blacks, compressed dynamic range
                            outR = (0.12f + normR * 0.78f).coerceIn(0f, 1f)
                            outG = (0.12f + normG * 0.78f).coerceIn(0f, 1f)
                            outB = (0.12f + normB * 0.78f).coerceIn(0f, 1f)
                        }
                        Film3dLut.BLEACH_BYPASS -> {
                            // Bleach Bypass: High contrast, silver desaturation
                            val luma = normR * 0.299f + normG * 0.587f + normB * 0.114f
                            val contrastedLuma = if (luma < 0.5f) 2f * luma * luma else 1f - 2f * (1f - luma) * (1f - luma)
                            outR = (contrastedLuma * 0.65f + normR * 0.35f).coerceIn(0f, 1f)
                            outG = (contrastedLuma * 0.65f + normG * 0.35f).coerceIn(0f, 1f)
                            outB = (contrastedLuma * 0.65f + normB * 0.35f).coerceIn(0f, 1f)
                        }
                        Film3dLut.NONE -> {}
                    }

                    data[ptr++] = (outR * 255f).toInt().toByte()
                    data[ptr++] = (outG * 255f).toInt().toByte()
                    data[ptr++] = (outB * 255f).toInt().toByte()
                    data[ptr++] = 255.toByte()
                }
            }
        }

        val buf = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder()).apply {
            put(data)
            position(0)
        }
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA8, size, size, size, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        return texId
    }
}

/**
 * Tip 202: Built-in Film Emulation 3D LUT Presets
 */
enum class Film3dLut(val label: String) {
    NONE("None"),
    PORTRA_400("Kodak Portra 400"),
    FUJI_VELVIA("Fuji Velvia 50"),
    CINEON_LOG("Cineon Log"),
    BLEACH_BYPASS("Bleach Bypass")
}
