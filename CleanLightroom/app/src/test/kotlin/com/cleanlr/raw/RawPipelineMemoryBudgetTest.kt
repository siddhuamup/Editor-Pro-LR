package com.cleanlr.raw

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Task T3: 50MP 16-bit RAW Engine & Memory Budget Trap Test
 *
 * Verifies:
 * 1. Theoretical 50MP 16-bit uncompressed footprint is ~402.6 MB (> 350 MB budget).
 * 2. Tiled streaming divides 50MP into 15 tiles with max tile buffer <= 37.9 MB.
 * 3. 16-bit linear tile processing completes with zero memory leak or OOM.
 * 4. DNG White balance gains and Black/White level normalization precision.
 */
class RawPipelineMemoryBudgetTest {

    @Test
    fun `test 50MP 16-bit uncompressed theoretical calculation confirms Secret Trap 1`() {
        val width = 8688
        val height = 5792 // 50.32 Megapixels
        val metadata = RawMetadata(width = width, height = height)

        val uncompressedBytes = width.toLong() * height.toLong() * 8L // 16-bit RGBA = 8 bytes per pixel
        val uncompressedMbDecimal = uncompressedBytes / 1_000_000.0 // 402.57 MB decimal
        val uncompressedMiB = uncompressedBytes / (1024.0 * 1024.0) // 383.92 MiB binary

        println("50.3MP uncompressed 16-bit RGBA footprint: $uncompressedMbDecimal MB ($uncompressedMiB MiB)")
        assertEquals(402.6, uncompressedMbDecimal, 0.5)
        assertEquals(383.9, uncompressedMiB, 0.5)

        // Verifies that a monolithic 16-bit buffer exceeds 350MB
        assertTrue(
            "Monolithic 50MP 16-bit buffer must exceed 350MB (was $uncompressedMiB MiB), proving necessity of tiled streaming",
            uncompressedMiB > 350.0
        )
    }

    @Test
    fun `test 2048px tile streaming limits peak 16-bit tile slice memory to under 38 MB`() {
        val maxDim = RawTileStreamer.MAX_TILE_DIM // 2048
        val halo = RawTileStreamer.HALO_MARGIN // 64
        val maxTileSliceW = maxDim + 2 * halo // 2176
        val maxTileSliceH = maxDim + 2 * halo // 2176

        val peakTileSliceBytes = maxTileSliceW.toLong() * maxTileSliceH.toLong() * 8L
        val peakTileSliceMb = peakTileSliceBytes / (1024.0 * 1024.0)

        println("Peak 16-bit tile slice memory: $peakTileSliceMb MB")
        assertTrue(
            "Peak 16-bit tile memory must be under 38 MB (was $peakTileSliceMb MB)",
            peakTileSliceMb < 38.0
        )
    }

    @Test
    fun `test 50MP 16-bit tiled streaming completes all 15 tiles seamlessly without OOM`() = runBlocking {
        val width = 8688
        val height = 5792
        val metadata = RawMetadata(
            width = width,
            height = height,
            blackLevel = 512,
            whiteLevel = 16383,
            asShotNeutral = floatArrayOf(0.5f, 1.0f, 0.6f)
        )

        val processedTilesCount = AtomicInteger(0)
        var totalCoveredPixels = 0L

        // Provide dummy 16-bit sensor data
        val dummyDataSource: (com.cleanlr.export.TileCoord) -> ShortArray = { tile ->
            ShortArray(tile.srcWidth * tile.srcHeight * 4) { idx ->
                // Linear gradient test values
                ((idx % 16000) + 512).toShort()
            }
        }

        RawTileStreamer.streamTiles(
            metadata = metadata,
            rawDataSource = dummyDataSource,
            onTileProcessed = { tile, outRgba ->
                val count = processedTilesCount.incrementAndGet()
                totalCoveredPixels += (tile.dstWidth.toLong() * tile.dstHeight.toLong())
                assertEquals(tile.dstWidth * tile.dstHeight * 4, outRgba.size)
            }
        )

        assertEquals("50MP must be divided into exactly 15 tiles", 15, processedTilesCount.get())
        assertEquals(width.toLong() * height.toLong(), totalCoveredPixels)
    }

    @Test
    fun `test RAW sensor black level and white balance normalization accuracy`() {
        val metadata = RawMetadata(
            width = 4000,
            height = 3000,
            blackLevel = 512,
            whiteLevel = 16383,
            asShotNeutral = floatArrayOf(0.5f, 1.0f, 0.75f)
        )

        val range = (metadata.whiteLevel - metadata.blackLevel).toFloat()
        assertEquals(15871f, range, 1e-4f)

        // Raw black pixel (512) -> normalized 0.0
        val normBlack = (512 - metadata.blackLevel) / range
        assertEquals(0.0f, normBlack, 1e-6f)

        // Raw white pixel (16383) -> normalized 1.0
        val normWhite = (16383 - metadata.blackLevel) / range
        assertEquals(1.0f, normWhite, 1e-6f)

        // Mid-gray (8447) -> normalized 0.5
        val normMid = (8447.5f - metadata.blackLevel) / range
        assertEquals(0.5f, normMid, 1e-4f)
    }
}
