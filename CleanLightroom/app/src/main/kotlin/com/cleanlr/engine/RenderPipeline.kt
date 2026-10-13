package com.cleanlr.engine

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.cleanlr.model.EditRecipe
import com.cleanlr.model.HslChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class RenderPipeline(private val context: Context) {

    companion object {
        private const val TAG = "RenderPipeline"

        private val QUAD_VERTICES = floatArrayOf(
            // x, y, u, v
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 0.0f
        )
    }

    private var programId = 0
    private var vaoId = 0
    private var vboId = 0

    private var imageTextureId = 0
    private var lutTextureId = 0

    // Uniform locations
    private var locImageTexture = -1
    private var locToneCurveLut = -1
    private var locExposure = -1
    private var locContrast = -1
    private var locHighlights = -1
    private var locShadows = -1
    private var locWhites = -1
    private var locBlacks = -1
    private var locWhiteBalanceGain = -1
    private var locClarity = -1
    private var locTexture = -1
    private var locDehaze = -1
    private var locVibrance = -1
    private var locSaturation = -1
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
    private var locAltClippingMode = -1
    private var locEnableCurve = -1

    var imageWidth = 0
        private set
    var imageHeight = 0
        private set

    private var isInitialized = false

    fun initGL() {
        if (isInitialized) return

        val vertShaderSource = readAsset("shaders/blit.vert")
        val fragShaderSource = readAsset("shaders/pipeline.frag")

        programId = createProgram(vertShaderSource, fragShaderSource)
        if (programId == 0) {
            Log.e(TAG, "Failed to compile/link master GLSL pipeline")
            return
        }

        // Cache uniform locations
        locImageTexture = GLES30.glGetUniformLocation(programId, "uImageTexture")
        locToneCurveLut = GLES30.glGetUniformLocation(programId, "uToneCurveLut")
        locExposure = GLES30.glGetUniformLocation(programId, "uExposure")
        locContrast = GLES30.glGetUniformLocation(programId, "uContrast")
        locHighlights = GLES30.glGetUniformLocation(programId, "uHighlights")
        locShadows = GLES30.glGetUniformLocation(programId, "uShadows")
        locWhites = GLES30.glGetUniformLocation(programId, "uWhites")
        locBlacks = GLES30.glGetUniformLocation(programId, "uBlacks")
        locWhiteBalanceGain = GLES30.glGetUniformLocation(programId, "uWhiteBalanceGain")
        locClarity = GLES30.glGetUniformLocation(programId, "uClarity")
        locTexture = GLES30.glGetUniformLocation(programId, "uTexture")
        locDehaze = GLES30.glGetUniformLocation(programId, "uDehaze")
        locVibrance = GLES30.glGetUniformLocation(programId, "uVibrance")
        locSaturation = GLES30.glGetUniformLocation(programId, "uSaturation")
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
        locAltClippingMode = GLES30.glGetUniformLocation(programId, "uAltClippingMode")
        locEnableCurve = GLES30.glGetUniformLocation(programId, "uEnableCurve")

        setupQuadGeometry()
        setupLutTexture()

        isInitialized = true
        Log.i(TAG, "RenderPipeline initialized successfully")
    }

    private fun setupQuadGeometry() {
        val vao = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        vaoId = vao[0]
        GLES30.glBindVertexArray(vaoId)

        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        vboId = vbo[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)

        val buffer: FloatBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_VERTICES)
                position(0)
            }

        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            QUAD_VERTICES.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )

        // Attribute 0: Position (vec2)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, 0)

        // Attribute 1: TexCoord (vec2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, 2 * 4)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun setupLutTexture() {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        lutTextureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // Initial identity LUT
        val identity = ByteArray(256) { it.toByte() }
        val lutBuffer = ByteBuffer.allocateDirect(256).apply {
            put(identity)
            position(0)
        }
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 256, 1, 0,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, lutBuffer
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun loadBitmap(bitmap: Bitmap) {
        if (imageTextureId != 0) {
            val textures = intArrayOf(imageTextureId)
            GLES30.glDeleteTextures(1, textures, 0)
            imageTextureId = 0
        }

        imageWidth = bitmap.width
        imageHeight = bitmap.height

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        imageTextureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        Log.i(TAG, "Uploaded bitmap: ${bitmap.width}x${bitmap.height} into texture $imageTextureId")
    }

    fun updateToneCurve(lutData: ByteArray) {
        if (lutTextureId == 0) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTextureId)
        val lutBuffer = ByteBuffer.allocateDirect(256).apply {
            put(lutData)
            position(0)
        }
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0, 256, 1,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, lutBuffer
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * Renders the current frame using the provided recipe.
     * @param recipe Active edit parameters
     * @param altClippingMode 0 = normal, 1 = alt+whites, 2 = alt+blacks
     * @param showOriginal when true, overrides adjustments to show clean raw rendition
     */
    fun render(
        recipe: EditRecipe,
        altClippingMode: Int = 0,
        showOriginal: Boolean = false
    ) {
        if (!isInitialized || imageTextureId == 0) return

        GLES30.glUseProgram(programId)

        // Bind source image to texture unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTextureId)
        GLES30.glUniform1i(locImageTexture, 0)

        // Bind LUT to texture unit 1
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTextureId)
        GLES30.glUniform1i(locToneCurveLut, 1)

        val activeRecipe = if (showOriginal) EditRecipe() else recipe

        // 1. Basic Exposure & Tone
        GLES30.glUniform1f(locExposure, activeRecipe.basic.exposure)
        GLES30.glUniform1f(locContrast, activeRecipe.basic.contrast)
        GLES30.glUniform1f(locHighlights, activeRecipe.basic.highlights)
        GLES30.glUniform1f(locShadows, activeRecipe.basic.shadows)
        GLES30.glUniform1f(locWhites, activeRecipe.basic.whites)
        GLES30.glUniform1f(locBlacks, activeRecipe.basic.blacks)

        // 2. White Balance
        val wbGains = ColorScience.kelvinTintToRgbGain(
            activeRecipe.basic.temperature,
            activeRecipe.basic.tint
        )
        GLES30.glUniform3f(locWhiteBalanceGain, wbGains[0], wbGains[1], wbGains[2])

        // 3. Presence
        GLES30.glUniform1f(locClarity, activeRecipe.presence.clarity)
        GLES30.glUniform1f(locTexture, activeRecipe.presence.texture)
        GLES30.glUniform1f(locDehaze, activeRecipe.presence.dehaze)
        GLES30.glUniform1f(locVibrance, activeRecipe.presence.vibrance)
        GLES30.glUniform1f(locSaturation, activeRecipe.presence.saturation)

        // 4. Color Grading
        val gShadow = activeRecipe.colorGrading.shadows
        val gMid = activeRecipe.colorGrading.midtones
        val gHigh = activeRecipe.colorGrading.highlights
        GLES30.glUniform3f(locGradeShadows, gShadow.hue, gShadow.saturation, gShadow.luminance)
        GLES30.glUniform3f(locGradeMidtones, gMid.hue, gMid.saturation, gMid.luminance)
        GLES30.glUniform3f(locGradeHighlights, gHigh.hue, gHigh.saturation, gHigh.luminance)
        GLES30.glUniform1f(locGradeBlending, activeRecipe.colorGrading.blending / 100.0f)
        GLES30.glUniform1f(locGradeBalance, activeRecipe.colorGrading.balance / 100.0f)

        // 5. 8-Channel HSL Offsets
        setHslUniform(locHslRed, activeRecipe.hsl.red)
        setHslUniform(locHslOrange, activeRecipe.hsl.orange)
        setHslUniform(locHslYellow, activeRecipe.hsl.yellow)
        setHslUniform(locHslGreen, activeRecipe.hsl.green)
        setHslUniform(locHslAqua, activeRecipe.hsl.aqua)
        setHslUniform(locHslBlue, activeRecipe.hsl.blue)
        setHslUniform(locHslPurple, activeRecipe.hsl.purple)
        setHslUniform(locHslMagenta, activeRecipe.hsl.magenta)

        // 6. Tone Curve & Clipping
        val hasCurveAdjustments = activeRecipe.curves.parametricHighlights != 0f ||
                activeRecipe.curves.parametricLights != 0f ||
                activeRecipe.curves.parametricDarks != 0f ||
                activeRecipe.curves.parametricShadows != 0f ||
                activeRecipe.curves.pointCurve.size > 2
        GLES30.glUniform1i(locEnableCurve, if (hasCurveAdjustments && !showOriginal) 1 else 0)
        GLES30.glUniform1i(locAltClippingMode, if (showOriginal) 0 else altClippingMode)

        // Render Quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    private fun setHslUniform(location: Int, channel: HslChannel) {
        if (location != -1) {
            GLES30.glUniform3f(location, channel.hue, channel.saturation, channel.luminance)
        }
    }

    fun release() {
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (imageTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(imageTextureId), 0)
            imageTextureId = 0
        }
        if (lutTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        isInitialized = false
    }

    private fun readAsset(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        if (vertShader == 0 || fragShader == 0) return 0

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertShader)
        GLES30.glAttachShader(program, fragShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Link error: " + GLES30.glGetProgramInfoLog(program))
            GLES30.glDeleteProgram(program)
            return 0
        }
        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compilation error: " + GLES30.glGetShaderInfoLog(shader))
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
