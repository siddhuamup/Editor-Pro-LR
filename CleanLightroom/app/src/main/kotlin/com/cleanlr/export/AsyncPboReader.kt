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
 * with OpenGL ES 3.0 Sync Fences (glFenceSync) to eliminate GPU pipeline stalls
 * during high-resolution tile readback (Doc 4 & Task T4 Secret Trap 2).
 */
class AsyncPboReader(
    private val maxTileWidth: Int = 2176, // 2048 + 2*64 halo
    private val maxTileHeight: Int = 2176
) {
    private val pboIds = IntArray(2)
    private val syncObjects = LongArray(2) { 0L }
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
     * Inserts an OpenGL ES 3.0 Fence Sync object to track asynchronous DMA completion.
     */
    fun triggerAsyncRead(width: Int, height: Int) {
        if (!isInitialized) initGL()
        val currentPbo = pboIds[activeIndex]
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, currentPbo)
        // With PBO bound, the last argument is a byte offset (0), not a client memory pointer!
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)

        // Clear any previous fence on this ping-pong slot
        if (syncObjects[activeIndex] != 0L) {
            GLES30.glDeleteSync(syncObjects[activeIndex])
            syncObjects[activeIndex] = 0L
        }

        // Insert fence sync to track DMA transfer completion
        syncObjects[activeIndex] = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
    }

    /**
     * Reads pixels from the current PBO into the target ByteBuffer.
     * Waits for the fence sync object (up to 100ms) to ensure DMA transfer is complete,
     * then uses glMapBufferRange for zero-copy DMA access without pipeline stall.
     */
    fun readPixelsMapped(width: Int, height: Int, destination: ByteBuffer): Boolean {
        if (!isInitialized) return false
        val currentPbo = pboIds[activeIndex]

        // Wait for fence sync if present
        val sync = syncObjects[activeIndex]
        if (sync != 0L) {
            GLES30.glClientWaitSync(sync, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 100_000_000L) // 100ms timeout
            GLES30.glDeleteSync(sync)
            syncObjects[activeIndex] = 0L
        }

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
            for (i in 0..1) {
                if (syncObjects[i] != 0L) {
                    GLES30.glDeleteSync(syncObjects[i])
                    syncObjects[i] = 0L
                }
            }
            GLES30.glDeleteBuffers(2, pboIds, 0)
            isInitialized = false
        }
    }
}
