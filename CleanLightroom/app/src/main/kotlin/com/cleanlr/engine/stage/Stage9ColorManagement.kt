package com.cleanlr.engine.stage

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.cleanlr.engine.CropGeometryEngine
import com.cleanlr.engine.PingPongFbo
import com.cleanlr.engine.ShaderHelper
import com.cleanlr.model.CropAdjustments
import kotlin.math.roundToInt

/**
 * Stage 9: Geometry Transform, Crop, Expand Checkerboard & Color Management
 * - Real-time GPU Flip (H/V), 90° Rotate, Straighten, Perspective Keystone,
 *   Radial Distortion, Aspect, Scale, Offset X/Y, Constrain Crop & Committed Crop!
 * - ProPhoto RGB Linear -> Target Display Gamut (sRGB / Display P3) + EOTF
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
    private var locSharpenMasking = -1

    // Geometry & Crop uniform locations
    private var locViewportSize = -1
    private var locDisplayRect = -1
    private var locCropRect = -1
    private var locIsCropActive = -1
    private var locIsExpandEnabled = -1
    private var locFlip = -1
    private var locRotationSteps = -1
    private var locTotalAngleRad = -1
    private var locPerspective = -1
    private var locDistortion = -1
    private var locGeoAspect = -1
    private var locGeoScale = -1
    private var locGeoOffset = -1
    private var locImageAspect = -1
    private var locBlurAmount = -1
    private var locBlurBoost = -1

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
        locSharpenMasking = GLES30.glGetUniformLocation(programId, "uSharpenMasking")

        locViewportSize = GLES30.glGetUniformLocation(programId, "uViewportSize")
        locDisplayRect = GLES30.glGetUniformLocation(programId, "uDisplayRect")
        locCropRect = GLES30.glGetUniformLocation(programId, "uCropRect")
        locIsCropActive = GLES30.glGetUniformLocation(programId, "uIsCropActive")
        locIsExpandEnabled = GLES30.glGetUniformLocation(programId, "uIsExpandEnabled")
        locFlip = GLES30.glGetUniformLocation(programId, "uFlip")
        locRotationSteps = GLES30.glGetUniformLocation(programId, "uRotationSteps")
        locTotalAngleRad = GLES30.glGetUniformLocation(programId, "uTotalAngleRad")
        locPerspective = GLES30.glGetUniformLocation(programId, "uPerspective")
        locDistortion = GLES30.glGetUniformLocation(programId, "uDistortion")
        locGeoAspect = GLES30.glGetUniformLocation(programId, "uGeoAspect")
        locGeoScale = GLES30.glGetUniformLocation(programId, "uGeoScale")
        locGeoOffset = GLES30.glGetUniformLocation(programId, "uGeoOffset")
        locImageAspect = GLES30.glGetUniformLocation(programId, "uImageAspect")
        locBlurAmount = GLES30.glGetUniformLocation(programId, "uBlurAmount")
        locBlurBoost = GLES30.glGetUniformLocation(programId, "uBlurBoost")

        isInitialized = true
        Log.d(TAG, "Stage 9 Color Management & GPU Geometry initialized successfully")
    }

    /**
     * Renders the final ProPhoto linear image with full GPU Crop, Flip, Rotate & Geometry
     * directly to the target framebuffer (screen FBO 0 or offscreen export FBO).
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
        imageHeight: Int = 0,
        sharpenMasking: Float = 0.0f,
        crop: CropAdjustments = CropAdjustments(),
        isCropActive: Boolean = false,
        blurAmount: Float = 0.0f,
        blurBoost: Float = 0.0f
    ) {
        if (!isInitialized || inputTextureId <= 0) return

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)

        GLES30.glUseProgram(programId)

        // Texture Unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTextureId)
        GLES30.glUniform1i(locInputTexture, 0)

        GLES30.glUniform1i(locTargetGamut, targetGamut)
        GLES30.glUniform1i(locAltClippingMode, altClippingMode)
        GLES30.glUniform1f(locSharpenMasking, sharpenMasking)

        val imgW = if (imageWidth > 0) imageWidth else viewportWidth.coerceAtLeast(1)
        val imgH = if (imageHeight > 0) imageHeight else viewportHeight.coerceAtLeast(1)
        val (effW, effH) = CropGeometryEngine.effectiveImageDimensions(imgW, imgH, crop.rotationDegrees)
        val effectiveAspect = (effW / effH).coerceAtLeast(0.05f)

        val displayRect = CropGeometryEngine.computeImageDisplayRect(
            viewportWidth = viewportWidth.toFloat(),
            viewportHeight = viewportHeight.toFloat(),
            imageWidth = imgW,
            imageHeight = imgH,
            crop = crop,
            isCropActive = isCropActive
        )

        val normRot = (((crop.rotationDegrees % 360f) + 360f) % 360f)
        val rotSteps = ((normRot / 90f).roundToInt()) % 4
        val totalAngleRad = Math.toRadians((crop.straightenAngle + crop.geometryRotate).toDouble()).toFloat()
        val constrainedExtraScale = CropGeometryEngine.computeConstrainedScale(crop, effectiveAspect)
        val totalScale = ((crop.geometryScale / 100f).coerceIn(0.25f, 4.0f)) * constrainedExtraScale

        GLES30.glUniform2f(locViewportSize, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES30.glUniform4f(locDisplayRect, displayRect.left, displayRect.top, displayRect.width, displayRect.height)
        GLES30.glUniform4f(locCropRect, crop.left, crop.top, crop.right, crop.bottom)
        GLES30.glUniform1i(locIsCropActive, if (isCropActive) 1 else 0)
        GLES30.glUniform1i(locIsExpandEnabled, if (isCropActive && crop.isExpandEnabled) 1 else 0)
        GLES30.glUniform2f(
            locFlip,
            if (crop.flipHorizontal) -1.0f else 1.0f,
            if (crop.flipVertical) -1.0f else 1.0f
        )
        GLES30.glUniform1i(locRotationSteps, rotSteps)
        GLES30.glUniform1f(locTotalAngleRad, totalAngleRad)
        GLES30.glUniform2f(locPerspective, crop.verticalPerspective / 100f, crop.horizontalPerspective / 100f)
        GLES30.glUniform1f(locDistortion, crop.distortion / 100f)
        GLES30.glUniform1f(locGeoAspect, crop.geometryAspect / 100f)
        GLES30.glUniform1f(locGeoScale, totalScale)
        GLES30.glUniform2f(locGeoOffset, crop.geometryXOffset / 100f, crop.geometryYOffset / 100f)
        GLES30.glUniform1f(locImageAspect, effectiveAspect)
        GLES30.glUniform1f(locBlurAmount, (blurAmount / 100f).coerceIn(0f, 1f))
        GLES30.glUniform1f(locBlurBoost, (blurBoost / 100f).coerceIn(0f, 1f))

        // Draw full-viewport quad
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
        altClippingMode: Int = 0,
        sharpenMasking: Float = 0.0f
    ): Boolean {
        if (!isInitialized) return false

        val inputTexture = pingPong.currentRead?.textureId ?: return false
        val w = pingPong.width.coerceAtLeast(1)
        val h = pingPong.height.coerceAtLeast(1)

        pingPong.bindWrite()
        renderToScreen(
            inputTextureId = inputTexture,
            targetFbo = pingPong.currentWrite?.framebufferId ?: 0,
            targetGamut = targetGamut,
            viewportWidth = w,
            viewportHeight = h,
            vaoId = vaoId,
            altClippingMode = altClippingMode,
            imageWidth = w,
            imageHeight = h,
            sharpenMasking = sharpenMasking,
            crop = CropAdjustments(),
            isCropActive = false
        )
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
