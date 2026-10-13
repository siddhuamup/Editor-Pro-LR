package com.cleanlr.engine

import android.opengl.GLES30
import android.util.Log

/**
 * Encapsulates an OpenGL ES 3.0 Framebuffer Object (FBO) backed by a 2D Texture.
 * Configured with GL_RGBA16F (Half-Float) for wide-gamut, banding-free precision.
 */
data class FboTarget(
    val framebufferId: Int,
    val textureId: Int,
    val width: Int,
    val height: Int,
    val isHalfFloat: Boolean
)

/**
 * Ping-Pong FBO pair for multi-stage image processing pipelines.
 * Allows ping-ponging textures between stages (Stage N output -> Stage N+1 input)
 * without memory allocations or data copies.
 */
class PingPongFbo {

    companion object {
        private const val TAG = "PingPongFbo"
    }

    private var targetA: FboTarget? = null
    private var targetB: FboTarget? = null

    var currentRead: FboTarget? = null
        private set
    var currentWrite: FboTarget? = null
        private set

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    /**
     * Prepares or resizes the ping-pong FBO pair.
     * Reallocation only happens if dimensions change or if targets are uninitialized.
     */
    fun ensureCapacity(newWidth: Int, newHeight: Int, preferHalfFloat: Boolean = true) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (width == newWidth && height == newHeight && targetA != null && targetB != null) {
            return
        }

        release()

        width = newWidth
        height = newHeight

        val fboA = createFbo(width, height, preferHalfFloat)
        val fboB = createFbo(width, height, preferHalfFloat)

        targetA = fboA
        targetB = fboB

        currentRead = targetA
        currentWrite = targetB

        Log.i(TAG, "Allocated Ping-Pong FBO: ${width}x${height}, format=${if (fboA.isHalfFloat) "RGBA16F" else "RGBA8"}")
    }

    /**
     * Swaps the read and write targets so the output of the current stage
     * becomes the input texture for the next stage.
     */
    fun swap() {
        val temp = currentRead
        currentRead = currentWrite
        currentWrite = temp
    }

    /**
     * Binds the current write target FBO and sets the viewport.
     */
    fun bindWrite() {
        val writeTarget = currentWrite ?: return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, writeTarget.framebufferId)
        GLES30.glViewport(0, 0, writeTarget.width, writeTarget.height)
    }

    /**
     * Unbinds the FBO, restoring the default system framebuffer (0).
     */
    fun unbind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Releases all GL texture and framebuffer resources.
     */
    fun release() {
        targetA?.let { deleteFbo(it) }
        targetB?.let { deleteFbo(it) }
        targetA = null
        targetB = null
        currentRead = null
        currentWrite = null
        width = 0
        height = 0
    }

    private fun createFbo(w: Int, h: Int, preferHalfFloat: Boolean): FboTarget {
        val fboIds = IntArray(1)
        val texIds = IntArray(1)

        GLES30.glGenFramebuffers(1, fboIds, 0)
        GLES30.glGenTextures(1, texIds, 0)

        val fboId = fboIds[0]
        val texId = texIds[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        var isHalfFloat = false

        if (preferHalfFloat) {
            // Allocate GL_RGBA16F texture buffer
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
            )

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, texId, 0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                isHalfFloat = true
            } else {
                Log.w(TAG, "GL_RGBA16F framebuffer incomplete (status=$status), falling back to GL_RGBA8")
            }
        }

        if (!isHalfFloat) {
            // Fallback to standard GL_RGBA8
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, texId, 0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "GL_RGBA8 Framebuffer incomplete! status=$status")
            }
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return FboTarget(
            framebufferId = fboId,
            textureId = texId,
            width = w,
            height = h,
            isHalfFloat = isHalfFloat
        )
    }

    private fun deleteFbo(target: FboTarget) {
        if (target.framebufferId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(target.framebufferId), 0)
        }
        if (target.textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(target.textureId), 0)
        }
    }
}

/**
 * Manages reusable FBO resources and ping-pong buffers across stages.
 */
class FboPool {

    private val primaryPingPong = PingPongFbo()
    private val auxiliaryTargets = mutableListOf<FboTarget>()

    /**
     * Gets or prepares the primary ping-pong FBO pair for the given image resolution.
     */
    fun getPrimaryPingPong(width: Int, height: Int, preferHalfFloat: Boolean = true): PingPongFbo {
        primaryPingPong.ensureCapacity(width, height, preferHalfFloat)
        return primaryPingPong
    }

    /**
     * Releases all managed framebuffers and textures.
     */
    fun release() {
        primaryPingPong.release()
        for (target in auxiliaryTargets) {
            if (target.framebufferId != 0) {
                GLES30.glDeleteFramebuffers(1, intArrayOf(target.framebufferId), 0)
            }
            if (target.textureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(target.textureId), 0)
            }
        }
        auxiliaryTargets.clear()
    }
}
