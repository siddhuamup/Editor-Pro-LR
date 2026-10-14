package com.cleanlr.export

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.cleanlr.model.EditRecipe
import kotlin.math.max
import kotlin.math.min

/**
 * Tile Coordinate specification for high-resolution image processing.
 *
 * @param indexX horizontal tile index (0 until totalTilesX)
 * @param indexY vertical tile index (0 until totalTilesY)
 * @param totalTilesX total horizontal tile count
 * @param totalTilesY total vertical tile count
 * @param dstX X coordinate in the final destination canvas
 * @param dstY Y coordinate in the final destination canvas
 * @param dstWidth width of the tile in the destination canvas
 * @param dstHeight height of the tile in the destination canvas
 * @param srcX X coordinate of source extraction window (including halo)
 * @param srcY Y coordinate of source extraction window (including halo)
 * @param srcWidth width of source extraction window (including halo)
 * @param srcHeight height of source extraction window (including halo)
 * @param cropOffsetX X offset to crop out the left halo padding
 * @param cropOffsetY Y offset to crop out the top halo padding
 */
data class TileCoord(
    val indexX: Int,
    val indexY: Int,
    val totalTilesX: Int,
    val totalTilesY: Int,
    val dstX: Int,
    val dstY: Int,
    val dstWidth: Int,
    val dstHeight: Int,
    val srcX: Int,
    val srcY: Int,
    val srcWidth: Int,
    val srcHeight: Int,
    val cropOffsetX: Int,
    val cropOffsetY: Int
)

object TileGeometry {
    const val TILE_SIZE = 2048
    const val HALO_MARGIN = 64

    /**
     * Calculates the tile decomposition for an image of dimensions [imageWidth] x [imageHeight].
     * Each tile is padded with [halo] pixels on all sides (clamped to image bounds) to provide
     * boundary support for spatial filters (Laplacian clarity, dehaze, sharpening).
     */
    fun calculateTiles(
        imageWidth: Int,
        imageHeight: Int,
        tileSize: Int = TILE_SIZE,
        halo: Int = HALO_MARGIN
    ): List<TileCoord> {
        val tiles = mutableListOf<TileCoord>()
        val totalTilesX = (imageWidth + tileSize - 1) / tileSize
        val totalTilesY = (imageHeight + tileSize - 1) / tileSize

        for (ty in 0 until totalTilesY) {
            val dstY = ty * tileSize
            val dstHeight = min(tileSize, imageHeight - dstY)

            val srcY = max(0, dstY - halo)
            val srcYEnd = min(imageHeight, dstY + dstHeight + halo)
            val srcHeight = srcYEnd - srcY
            val cropOffsetY = dstY - srcY

            for (tx in 0 until totalTilesX) {
                val dstX = tx * tileSize
                val dstWidth = min(tileSize, imageWidth - dstX)

                val srcX = max(0, dstX - halo)
                val srcXEnd = min(imageWidth, dstX + dstWidth + halo)
                val srcWidth = srcXEnd - srcX
                val cropOffsetX = dstX - srcX

                tiles.add(
                    TileCoord(
                        indexX = tx,
                        indexY = ty,
                        totalTilesX = totalTilesX,
                        totalTilesY = totalTilesY,
                        dstX = dstX,
                        dstY = dstY,
                        dstWidth = dstWidth,
                        dstHeight = dstHeight,
                        srcX = srcX,
                        srcY = srcY,
                        srcWidth = srcWidth,
                        srcHeight = srcHeight,
                        cropOffsetX = cropOffsetX,
                        cropOffsetY = cropOffsetY
                    )
                )
            }
        }
        return tiles
    }
}

/**
 * TileProcessor: Executes 2048x2048 tile-based rendering with 64px halo margins.
 * Eliminates GPU texture memory limits (GL_MAX_TEXTURE_SIZE) and prevents OOM on 50MP-100MP files.
 */
class TileProcessor(private val renderer: HeadlessGlRenderer) {

    /**
     * Renders a large bitmap in 2048x2048 chunks with 64px halo overlap to avoid seam artifacts.
     */
    fun renderTiled(
        sourceBitmap: Bitmap,
        recipe: EditRecipe,
        onProgress: ((completedTiles: Int, totalTiles: Int) -> Unit)? = null
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        if (width <= TileGeometry.TILE_SIZE && height <= TileGeometry.TILE_SIZE) {
            onProgress?.invoke(1, 1)
            return renderer.renderBitmap(sourceBitmap, recipe)
        }

        val tiles = TileGeometry.calculateTiles(width, height)
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        renderer.beginSession()
        try {
            tiles.forEachIndexed { index, tile ->
                // 1. Extract tile slice including halo padding
                val tileSlice = Bitmap.createBitmap(
                    sourceBitmap,
                    tile.srcX,
                    tile.srcY,
                    tile.srcWidth,
                    tile.srcHeight
                )

                // 2. Render tile through 9-stage GLSL pipeline
                val renderedTile = renderer.renderBitmap(tileSlice, recipe)
                tileSlice.recycle()

                // 3. Crop halo margin and blit valid interior into output canvas
                val srcRect = Rect(
                    tile.cropOffsetX,
                    tile.cropOffsetY,
                    tile.cropOffsetX + tile.dstWidth,
                    tile.cropOffsetY + tile.dstHeight
                )
                val dstRect = Rect(
                    tile.dstX,
                    tile.dstY,
                    tile.dstX + tile.dstWidth,
                    tile.dstY + tile.dstHeight
                )

                canvas.drawBitmap(renderedTile, srcRect, dstRect, paint)
                renderedTile.recycle()

                onProgress?.invoke(index + 1, tiles.size)
            }
        } finally {
            renderer.endSession()
        }

        return outputBitmap
    }

    /**
     * Directly decodes and processes regions from a BitmapRegionDecoder for minimal RAM usage.
     */
    fun renderTiledFromDecoder(
        decoder: BitmapRegionDecoder,
        imageWidth: Int,
        imageHeight: Int,
        recipe: EditRecipe,
        onProgress: ((completedTiles: Int, totalTiles: Int) -> Unit)? = null
    ): Bitmap {
        val tiles = TileGeometry.calculateTiles(imageWidth, imageHeight)
        val outputBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        renderer.beginSession()
        try {
            tiles.forEachIndexed { index, tile ->
                val regionRect = Rect(tile.srcX, tile.srcY, tile.srcX + tile.srcWidth, tile.srcY + tile.srcHeight)
                val tileSlice = decoder.decodeRegion(regionRect, null)
                    ?: throw IllegalStateException("Failed to decode region $regionRect")

                val renderedTile = renderer.renderBitmap(tileSlice, recipe)
                tileSlice.recycle()

                val srcRect = Rect(
                    tile.cropOffsetX,
                    tile.cropOffsetY,
                    tile.cropOffsetX + tile.dstWidth,
                    tile.cropOffsetY + tile.dstHeight
                )
                val dstRect = Rect(
                    tile.dstX,
                    tile.dstY,
                    tile.dstX + tile.dstWidth,
                    tile.dstY + tile.dstHeight
                )

                canvas.drawBitmap(renderedTile, srcRect, dstRect, paint)
                renderedTile.recycle()

                onProgress?.invoke(index + 1, tiles.size)
            }
        } finally {
            renderer.endSession()
        }

        return outputBitmap
    }
}
