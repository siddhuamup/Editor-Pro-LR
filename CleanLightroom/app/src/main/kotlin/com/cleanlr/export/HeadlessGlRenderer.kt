package com.cleanlr.export

import android.content.Context
import android.graphics.Bitmap
import android.opengl.*
import android.util.Log
import com.cleanlr.engine.RenderPipeline
import com.cleanlr.engine.SplineInterpolator
import com.cleanlr.model.EditRecipe
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HeadlessGlRenderer(private val context: Context) {

    companion object {
        private const val TAG = "HeadlessGlRenderer"
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    /**
     * Renders high-resolution image offscreen using the exact GLES 3.0 shader pipeline.
     */
    fun renderFullResolution(sourceBitmap: Bitmap, recipe: EditRecipe): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        initEgl()

        val pipeline = RenderPipeline(context)
        pipeline.initGL()
        pipeline.loadBitmap(sourceBitmap)

        // Upload tone curve LUT
        val lutData = SplineInterpolator.generateLut256(recipe.curves)
        pipeline.updateToneCurve(lutData)

        // Setup FBO & Color Attachment
        val fbo = IntArray(1)
        val renderTex = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glGenTextures(1, renderTex, 0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderTex[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, renderTex[0], 0
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Framebuffer incomplete, status: $status")
        }

        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // Execute shader pass
        pipeline.render(recipe, altClippingMode = 0, showOriginal = false)
        GLES30.glFinish()

        // Read pixels
        val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer)

        // Cleanup GL resources
        pipeline.release()
        GLES30.glDeleteTextures(1, renderTex, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
        destroyEgl()

        // Convert byte buffer to Bitmap & flip vertical axis (GL bottom-up to Bitmap top-down)
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val flippedBuffer = ByteBuffer.allocateDirect(width * height * 4)
        val rowSize = width * 4
        val row = ByteArray(rowSize)
        for (y in 0 until height) {
            pixelBuffer.position((height - 1 - y) * rowSize)
            pixelBuffer.get(row, 0, rowSize)
            flippedBuffer.put(row, 0, rowSize)
        }
        flippedBuffer.position(0)
        outBitmap.copyPixelsFromBuffer(flippedBuffer)

        return outBitmap
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE
        )
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttribs, 0)

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun destroyEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
