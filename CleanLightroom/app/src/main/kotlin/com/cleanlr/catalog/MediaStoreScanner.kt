package com.cleanlr.catalog

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Task T2: MediaStore Synchronization Engine
 *
 * Scans the Android MediaStore for photos and RAW images, extracting EXIF/file dimensions,
 * file sizes, and timestamps, and indexing them into the [CatalogRepository].
 */
object MediaStoreScanner {

    private const val TAG = "MediaStoreScanner"

    private val PROJECTION = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.SIZE
    )

    suspend fun scanMediaStore(
        context: Context,
        repository: CatalogRepository,
        batchSize: Int = 200,
        onProgress: ((scannedCount: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        var totalIndexed = 0
        val buffer = mutableListOf<MediaAsset>()

        try {
            val cursor: Cursor? = resolver.query(
                collectionUri,
                PROJECTION,
                null,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateTakenCol = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateModCol = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val widthCol = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeCol = c.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (c.moveToNext()) {
                    val mediaId = c.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collectionUri, mediaId)
                    val name = c.getString(nameCol) ?: "IMG_$mediaId.jpg"
                    val mime = c.getString(mimeCol) ?: "image/jpeg"

                    val dateTaken = if (dateTakenCol >= 0 && !c.isNull(dateTakenCol)) {
                        c.getLong(dateTakenCol)
                    } else if (dateModCol >= 0 && !c.isNull(dateModCol)) {
                        c.getLong(dateModCol) * 1000L
                    } else {
                        System.currentTimeMillis()
                    }

                    val width = if (widthCol >= 0 && !c.isNull(widthCol)) c.getInt(widthCol) else 0
                    val height = if (heightCol >= 0 && !c.isNull(heightCol)) c.getInt(heightCol) else 0
                    val size = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L

                    val isRaw = MediaAsset.isRawFile(name, mime)

                    val asset = MediaAsset(
                        id = mediaId.toString(),
                        uriString = contentUri.toString(),
                        fileName = name,
                        mimeType = mime,
                        dateTaken = dateTaken,
                        width = width,
                        height = height,
                        isRaw = isRaw,
                        fileSizeBytes = size
                    )

                    buffer.add(asset)
                    totalIndexed++

                    if (buffer.size >= batchSize) {
                        repository.upsertAssets(buffer.toList())
                        buffer.clear()
                        onProgress?.invoke(totalIndexed)
                    }
                }

                if (buffer.isNotEmpty()) {
                    repository.upsertAssets(buffer.toList())
                    buffer.clear()
                    onProgress?.invoke(totalIndexed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
        }

        return@withContext totalIndexed
    }
}
