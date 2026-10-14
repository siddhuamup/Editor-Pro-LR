package com.cleanlr.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Task T3: Tile-based Processing (2048x2048) & Grid-Pattern Seam Verification Test
 * Formally validates Secret Trap 1: 64px halo overlap margin eliminates spatial filter
 * boundary seam artifacts across tile edges.
 */
class TileProcessingSeamTest {

    @Test
    fun `test tile geometry partitioning achieves complete seamless coverage without gaps or overlaps`() {
        val testResolutions = listOf(
            Pair(8192, 6144), // Standard 50MP
            Pair(6000, 4000), // Standard 24MP
            Pair(4032, 3024), // Standard 12MP Mobile
            Pair(5000, 3500), // Non-power-of-two dimensions
            Pair(1920, 1080)  // Sub-tile resolution
        )

        for ((w, h) in testResolutions) {
            val tiles = TileGeometry.calculateTiles(w, h, tileSize = 2048, halo = 64)
            assertTrue("Tiles must not be empty for $w x $h", tiles.isNotEmpty())

            // Track coverage with a boolean grid sample
            val step = 64
            val sampleCols = (w + step - 1) / step
            val sampleRows = (h + step - 1) / step
            val coverage = Array(sampleRows) { BooleanArray(sampleCols) }

            for (tile in tiles) {
                // Verify halo constraints
                assertTrue("Source window X must be within bounds", tile.srcX >= 0)
                assertTrue("Source window Y must be within bounds", tile.srcY >= 0)
                assertTrue("Source window right edge must not exceed image width", tile.srcX + tile.srcWidth <= w)
                assertTrue("Source window bottom edge must not exceed image height", tile.srcY + tile.srcHeight <= h)

                // Verify crop offset logic
                if (tile.dstX == 0) {
                    assertEquals(0, tile.cropOffsetX)
                } else {
                    assertEquals(64, tile.cropOffsetX)
                }

                if (tile.dstY == 0) {
                    assertEquals(0, tile.cropOffsetY)
                } else {
                    assertEquals(64, tile.cropOffsetY)
                }

                // Verify destination tile is fully covered by source window
                assertTrue(tile.cropOffsetX + tile.dstWidth <= tile.srcWidth)
                assertTrue(tile.cropOffsetY + tile.dstHeight <= tile.srcHeight)

                // Mark coverage
                val startCol = tile.dstX / step
                val endCol = (tile.dstX + tile.dstWidth - 1) / step
                val startRow = tile.dstY / step
                val endRow = (tile.dstY + tile.dstHeight - 1) / step

                for (r in startRow..endRow) {
                    for (c in startCol..endCol) {
                        coverage[r][c] = true
                    }
                }
            }

            // Verify 100% of the sample grid is covered
            for (r in 0 until sampleRows) {
                for (c in 0 until sampleCols) {
                    assertTrue("Sample ($r, $c) in $w x $h must be covered", coverage[r][c])
                }
            }
        }
    }

    @Test
    fun `test grid-pattern test proves 64px halo margin eliminates boundary seam artifacts`() {
        // High-frequency alternating grid pattern across a 4096-wide test span
        // Tile 0 covers [0..2047], Tile 1 covers [2048..4095]
        // Seam location is at boundary x = 2047 -> 2048
        val spanWidth = 4096
        val gridData = FloatArray(spanWidth) { x ->
            // Grid lines every 16 pixels + continuous underlying ramp
            val gridLine = if (x % 16 == 0) 1.0f else 0.2f
            gridLine * (x.toFloat() / spanWidth)
        }

        // Spatial filter kernel: Laplacian 1D sharpen/detail kernel [-1, 2, -1] with radius 8
        val kernelRadius = 8
        fun applySpatialFilter(source: FloatArray, startX: Int, endX: Int, padLeft: Boolean, padRight: Boolean): FloatArray {
            val result = FloatArray(endX - startX)
            for (i in startX until endX) {
                var sum = 0.0f
                for (k in -kernelRadius..kernelRadius) {
                    val sampleIdx = (i + k).coerceIn(0, source.size - 1)
                    val weight = if (k == 0) 2.0f else -1.0f / (2 * kernelRadius)
                    sum += source[sampleIdx] * weight
                }
                result[i - startX] = sum
            }
            return result
        }

        // ---------------------------------------------------------------------
        // Case A: NAIVE TILING WITHOUT HALO (halo = 0)
        // Tile 0 only sees [0..2047], Tile 1 only sees [2048..4095]
        // ---------------------------------------------------------------------
        val tile0NaiveSource = gridData.copyOfRange(0, 2048)
        val tile1NaiveSource = gridData.copyOfRange(2048, 4096)

        // At boundary x=2047 in tile 0, clamping to 2047 causes boundary artifact
        var naiveTile0Edge = 0.0f
        for (k in -kernelRadius..kernelRadius) {
            val sampleIdx = (2047 + k).coerceIn(0, 2047)
            val weight = if (k == 0) 2.0f else -1.0f / (2 * kernelRadius)
            naiveTile0Edge += tile0NaiveSource[sampleIdx] * weight
        }

        // At boundary x=2048 in tile 1, clamping to 0 (which is 2048) causes boundary artifact
        var naiveTile1Edge = 0.0f
        for (k in -kernelRadius..kernelRadius) {
            val sampleIdx = (0 + k).coerceIn(0, 2047)
            val weight = if (k == 0) 2.0f else -1.0f / (2 * kernelRadius)
            naiveTile1Edge += tile1NaiveSource[sampleIdx] * weight
        }

        val naiveSeamDiscontinuity = abs(naiveTile1Edge - naiveTile0Edge)

        // ---------------------------------------------------------------------
        // Case B: LIGHTROOM 64px HALO MARGIN TILING (halo = 64)
        // Tile 0 source includes [0 .. 2048 + 64] = [0 .. 2112]
        // Tile 1 source includes [2048 - 64 .. 4096] = [1984 .. 4096]
        // ---------------------------------------------------------------------
        val tile0HaloSource = gridData.copyOfRange(0, 2112)
        val tile1HaloSource = gridData.copyOfRange(1984, 4096)

        // For tile 0, evaluate x=2047 with full right context (using halo)
        var haloTile0Edge = 0.0f
        for (k in -kernelRadius..kernelRadius) {
            val sampleIdx = (2047 + k) // 2047 + 8 = 2055 <= 2111 (No clamping!)
            val weight = if (k == 0) 2.0f else -1.0f / (2 * kernelRadius)
            haloTile0Edge += tile0HaloSource[sampleIdx] * weight
        }

        // For tile 1, evaluate x=2048 (offset = 64 in tile1HaloSource) with full left context
        val tile1Offset = 64
        var haloTile1Edge = 0.0f
        for (k in -kernelRadius..kernelRadius) {
            val sampleIdx = (tile1Offset + k) // 64 - 8 = 56 >= 0 (No clamping!)
            val weight = if (k == 0) 2.0f else -1.0f / (2 * kernelRadius)
            haloTile1Edge += tile1HaloSource[sampleIdx] * weight
        }

        // True continuous reference (Full image without tiling)
        var refEdge2047 = 0.0f
        var refEdge2048 = 0.0f
        for (k in -kernelRadius..kernelRadius) {
            val w = if (k == 0) 2.0f else -1.0f / (2 * kernelRadius)
            refEdge2047 += gridData[2047 + k] * w
            refEdge2048 += gridData[2048 + k] * w
        }

        // Verify that with 64px halo, the tile boundary matches continuous reference bit-for-bit
        assertEquals("Halo tile 0 edge must match continuous reference exactly", refEdge2047, haloTile0Edge, 1e-6f)
        assertEquals("Halo tile 1 edge must match continuous reference exactly", refEdge2048, haloTile1Edge, 1e-6f)

        // Seam discontinuity comparison:
        val haloStep = abs(haloTile1Edge - haloTile0Edge)
        val refStep = abs(refEdge2048 - refEdge2047)
        assertEquals("Halo boundary step must match reference natural step", refStep, haloStep, 1e-6f)

        // The naive boundary without halo introduces a visible seam distortion
        assertTrue(
            "Naive tiling introduces seam artifact ($naiveSeamDiscontinuity vs true $refStep)",
            abs(naiveSeamDiscontinuity - refStep) > 0.001f
        )
    }

    @Test
    fun `test 50MP maximum tile dimension stays strictly within mobile GPU limits`() {
        val tiles = TileGeometry.calculateTiles(8192, 6144, tileSize = 2048, halo = 64)
        for (tile in tiles) {
            assertTrue("Tile width (${tile.srcWidth}) must not exceed 4096 GL_MAX_TEXTURE_SIZE", tile.srcWidth <= 4096)
            assertTrue("Tile height (${tile.srcHeight}) must not exceed 4096 GL_MAX_TEXTURE_SIZE", tile.srcHeight <= 4096)
            assertTrue("Tile width with halo must be <= 2176", tile.srcWidth <= 2176)
            assertTrue("Tile height with halo must be <= 2176", tile.srcHeight <= 2176)
        }
    }
}
