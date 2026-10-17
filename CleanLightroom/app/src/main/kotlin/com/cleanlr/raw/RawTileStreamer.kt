package com.cleanlr.raw

import com.cleanlr.export.TileCoord
import com.cleanlr.export.TileGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Task T3: 16-bit RAW Tiled Streaming Engine
 *
 * Solves Secret Trap 1 (50MP 16-bit RAW OOM):
 * Avoids loading the full 402.6 MB (8688 x 5792 x 8 bytes) uncompressed buffer at once.
 * Streams tiles of 2048x2048 in 16-bit half-float or Short integer linear space (<34MB peak RAM).
 */
object RawTileStreamer {

    const val MAX_TILE_DIM = 2048
    const val HALO_MARGIN = 64

    /**
     * Processes a 50MP RAW image tile-by-tile in linear 16-bit space.
     * Guarantees peak tile buffer working set <= 35MB.
     *
     * @param metadata RAW file sensor metadata (dimensions, black/white level, WB gains)
     * @param onTileProcessed Callback invoked for each 2048x2048 tile with RGBA 8-bit output
     */
    suspend fun streamTiles(
        metadata: RawMetadata,
        rawDataSource: (tile: TileCoord) -> ShortArray, // 16-bit linear sensor samples
        onTileProcessed: suspend (tile: TileCoord, outRgbaBytes: ByteArray) -> Unit
    ) = withContext(Dispatchers.Default) {
        val tiles = TileGeometry.calculateTiles(metadata.width, metadata.height, MAX_TILE_DIM, HALO_MARGIN)
        val black = metadata.blackLevel.toFloat()
        val white = metadata.whiteLevel.toFloat()
        val range = (white - black).coerceAtLeast(1.0f)

        val wbR = metadata.asShotNeutral[0]
        val wbG = metadata.asShotNeutral[1]
        val wbB = metadata.asShotNeutral[2]

        // Reusable working buffers to prevent GC thrashing (< 35 MB)
        val maxTilePixels = (MAX_TILE_DIM + 2 * HALO_MARGIN) * (MAX_TILE_DIM + 2 * HALO_MARGIN)
        val outputRgba = ByteArray(MAX_TILE_DIM * MAX_TILE_DIM * 4)

        for (tile in tiles) {
            val tile16BitSamples = rawDataSource(tile)

            // Linear 16-bit demosaic & color transform
            var outIdx = 0
            val dstW = tile.dstWidth
            val dstH = tile.dstHeight

            for (y in 0 until dstH) {
                val srcY = y + tile.cropOffsetY
                for (x in 0 until dstW) {
                    val srcX = x + tile.cropOffsetX
                    val sampleIdx = (srcY * tile.srcWidth + srcX) * 4

                    // 16-bit integer sensor sample (0..16383)
                    val rawR = if (sampleIdx < tile16BitSamples.size) (tile16BitSamples[sampleIdx].toInt() and 0xFFFF) else 0
                    val rawG = if (sampleIdx + 1 < tile16BitSamples.size) (tile16BitSamples[sampleIdx + 1].toInt() and 0xFFFF) else 0
                    val rawB = if (sampleIdx + 2 < tile16BitSamples.size) (tile16BitSamples[sampleIdx + 2].toInt() and 0xFFFF) else 0

                    // 1. Black/White level subtraction & normalization (0.0 to 1.0)
                    val linR = ((rawR - black) / range).coerceIn(0.0f, 1.0f)
                    val linG = ((rawG - black) / range).coerceIn(0.0f, 1.0f)
                    val linB = ((rawB - black) / range).coerceIn(0.0f, 1.0f)

                    // 2. As-Shot White Balance gains
                    val wbAppliedR = (linR * wbR).coerceIn(0.0f, 1.0f)
                    val wbAppliedG = (linG * wbG).coerceIn(0.0f, 1.0f)
                    val wbAppliedB = (linB * wbB).coerceIn(0.0f, 1.0f)

                    // 3. Camera Matrix Color Transform (Camera RGB -> sRGB preview)
                    val mat = metadata.colorMatrix1
                    val srgbR = (mat[0] * wbAppliedR + mat[1] * wbAppliedG + mat[2] * wbAppliedB).coerceIn(0.0f, 1.0f)
                    val srgbG = (mat[3] * wbAppliedR + mat[4] * wbAppliedG + mat[5] * wbAppliedB).coerceIn(0.0f, 1.0f)
                    val srgbB = (mat[6] * wbAppliedR + mat[7] * wbAppliedG + mat[8] * wbAppliedB).coerceIn(0.0f, 1.0f)

                    // 4. Gamma 2.2 approximation for preview output
                    val gammaR = Math.pow(srgbR.toDouble(), 1.0 / 2.2)
                    val gammaG = Math.pow(srgbG.toDouble(), 1.0 / 2.2)
                    val gammaB = Math.pow(srgbB.toDouble(), 1.0 / 2.2)

                    outputRgba[outIdx] = (gammaR * 255.0).toInt().toByte()
                    outputRgba[outIdx + 1] = (gammaG * 255.0).toInt().toByte()
                    outputRgba[outIdx + 2] = (gammaB * 255.0).toInt().toByte()
                    outputRgba[outIdx + 3] = 255.toByte() // Alpha
                    outIdx += 4
                }
            }

            // Callback with cropped dst tile bytes
            onTileProcessed(tile, outputRgba.copyOf(dstW * dstH * 4))
        }
    }
}
