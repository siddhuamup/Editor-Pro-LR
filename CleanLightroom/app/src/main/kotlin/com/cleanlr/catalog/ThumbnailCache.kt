package com.cleanlr.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Task T2: Two-Tier High Performance Thumbnail Cache
 *
 * Tier 1: In-memory LruCache (up to 32MB) for instantaneous 60 FPS grid scrolling.
 * Tier 2: Persistent Disk Cache on internal storage for zero-redecode cold starts.
 */
class ThumbnailCache(
    private val context: Context,
    maxMemoryBytes: Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(32 * 1024 * 1024).toInt()
) {
    private val diskCacheDir = File(context.cacheDir, "catalog_thumbs").apply { mkdirs() }

    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    /**
     * Gets a cached thumbnail bitmap, checking memory first, then disk cache.
     */
    fun getThumbnail(id: String): Bitmap? {
        val memoryHit = memoryCache.get(id)
        if (memoryHit != null && !memoryHit.isRecycled) {
            return memoryHit
        }

        // Check disk cache
        val diskFile = File(diskCacheDir, "$id.thumb")
        if (diskFile.exists() && diskFile.length() > 0) {
            val diskBitmap = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
            if (diskBitmap != null) {
                memoryCache.put(id, diskBitmap)
                return diskBitmap
            }
        }

        return null
    }

    /**
     * Puts a generated thumbnail into memory and disk cache.
     */
    fun putThumbnail(id: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        memoryCache.put(id, bitmap)

        // Write to disk cache asynchronously or synchronously
        try {
            val diskFile = File(diskCacheDir, "$id.thumb")
            FileOutputStream(diskFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (_: Exception) {}
    }

    /**
     * Decodes and downsamples a source image Uri to a compact grid thumbnail (e.g. 256x256).
     */
    suspend fun loadOrGenerateThumbnail(
        asset: MediaAsset,
        targetSize: Int = 256
    ): Bitmap? = withContext(Dispatchers.IO) {
        val existing = getThumbnail(asset.id)
        if (existing != null) return@withContext existing

        try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(asset.uri) ?: return@withContext null

            // 1. Decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // 2. Compute sample size
            val srcMax = max(options.outWidth, options.outHeight)
            var sampleSize = 1
            if (srcMax > targetSize) {
                sampleSize = srcMax / targetSize
            }

            // 3. Decode sub-sampled bitmap
            val decodeStream = resolver.openInputStream(asset.uri) ?: return@withContext null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 16-bit to halve memory in grid
            }
            val thumbnail = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()

            if (thumbnail != null) {
                putThumbnail(asset.id, thumbnail)
            }
            return@withContext thumbnail
        } catch (_: Exception) {
            return@withContext null
        }
    }

    fun clearMemory() {
        memoryCache.evictAll()
    }

    fun clearAll() {
        memoryCache.evictAll()
        diskCacheDir.deleteRecursively()
        diskCacheDir.mkdirs()
    }
}
