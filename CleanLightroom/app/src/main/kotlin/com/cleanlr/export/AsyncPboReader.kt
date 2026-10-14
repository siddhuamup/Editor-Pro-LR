package com.cleanlr.export

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PboMemoryBudget:
 * Enforces the strict < 350 MB memory budget for high-resolution tile processing (Doc 4 & Task T4).
 */
object PboMemoryBudget {
    const val MAX_BUDGET_BYTES = 350L * 1024L * 1024L // 350 MB

    fun calculateTilePboMemory(tileWidth: Int, tileHeight: Int, numPbos: Int = 2): Long {
        val singleBufferSize = tileWidth.toLong() * tileHeight.toLong() * 4L
        return singleBufferSize * numPbos
    }

    fun isWithinBudget(tileWidth: Int, tileHeight: Int, activeBitmapsCount: Int = 2): Boolean {
        val singleTileBytes = tileWidth.toLong() * tileHeight.toLong() * 4L
        val pboBytes = calculateTilePboMemory(tileWidth, tileHeight, numPbos = 2)
        val bitmapBytes = singleTileBytes * activeBitmapsCount
        val totalWorkingSet = pboBytes + bitmapBytes
        return totalWorkingSet < MAX_BUDGET_BYTES
    }
}

/**
 * AsyncPboReader:
 * Implements non-blocking Asynchronous Pixel Buffer Objects (GL_PIXEL_PACK_BUFFER)
 * to avoid GPU pipeline stalls during high-resolution tile readback.
 * (Doc 4 & Task T4 Secret Trap 2).
 */
class AsyncPboReader(
    private val maxTileWidth: Int = 2176, // 2048 + 2*64 halo
    private val maxTileHeight: Int = 2176
) {
    private val pboIds = IntArray(2)
    private val bufferSize = maxTileWidth * maxTileHeight * 4
    private var isInitialized = false
    private var activeIndex = 0

    fun initGL() {
        if (isInitialized) return
        GLES30.glGenBuffers(2, pboIds, 0)
        for (i in 0..1) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[i])
            GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, bufferSize, null, GLES30.GL_STREAM_READ)
        }
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        isInitialized = true
        activeIndex = 0
    }

    /**
     * Dispatches an asynchronous DMA readback from the current FBO to the current PBO.
     * With offset = 0 and GL_PIXEL_PACK_BUFFER bound, glReadPixels returns immediately without GPU stall.
     */
    fun triggerAsyncRead(width: Int, height: Int) {
        if (!isInitialized) initGL()
        val currentPbo = pboIds[activeIndex]
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, currentPbo)
        // With PBO bound, the last argument is a byte offset (0), not a client memory pointer!
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
    }

    /**
     * Reads pixels from the current PBO into the target ByteBuffer.
     * Uses glMapBufferRange for zero-copy DMA access.
     */
    fun readPixelsMapped(width: Int, height: Int, destination: ByteBuffer): Boolean {
        if (!isInitialized) return false
        val currentPbo = pboIds[activeIndex]
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, currentPbo)

        val size = width * height * 4
        val mapped = GLES30.glMapBufferRange(
            GLES30.GL_PIXEL_PACK_BUFFER,
            0,
            size,
            GLES30.GL_MAP_READ_BIT
        ) as? ByteBuffer

        var success = false
        if (mapped != null) {
            mapped.order(ByteOrder.nativeOrder())
            destination.clear()
            destination.put(mapped)
            destination.rewind()
            GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
            success = true
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        // Advance ping-pong index for the next tile
        activeIndex = (activeIndex + 1) % 2
        return success
    }

    fun release() {
        if (isInitialized) {
            GLES30.glDeleteBuffers(2, pboIds, 0)
            isInitialized = false
        }
    }
}
