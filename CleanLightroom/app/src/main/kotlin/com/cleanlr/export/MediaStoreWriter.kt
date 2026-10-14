package com.cleanlr.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.cleanlr.model.EditRecipe
import java.io.OutputStream

/**
 * Task T9: MediaStore Scoped Storage Writer & Native Share Sheet Helper
 *
 * Enforces atomic IS_PENDING write transactions into Pictures/CleanLightroom,
 * zero partial-file leaks on error, Exif preservation, and compliant ACTION_SEND sharing.
 */
object MediaStoreWriter {

    const val TAG = "MediaStoreWriter"
    const val DIRECTORY_PICTURES = "Pictures"
    const val ALBUM_DIRECTORY = "CleanLightroom"
    const val RELATIVE_PATH = "$DIRECTORY_PICTURES/$ALBUM_DIRECTORY"

    /**
     * Specification for native share sheet dispatch.
     */
    data class ShareRequest(
        val uriString: String,
        val mimeType: String,
        val chooserTitle: String = "Share Photo",
        val grantReadPermission: Boolean = true
    )

    /**
     * Builds a [ShareRequest] configuration for sharing.
     */
    fun buildShareRequest(
        imageUriString: String,
        format: ExportFormat,
        chooserTitle: String = "Share Photo"
    ): ShareRequest {
        return ShareRequest(
            uriString = imageUriString,
            mimeType = getMimeType(format),
            chooserTitle = chooserTitle,
            grantReadPermission = true
        )
    }

    /**
     * Resolves appropriate MIME type string based on [ExportFormat].
     */
    fun getMimeType(format: ExportFormat): String = when (format) {
        ExportFormat.JPEG -> "image/jpeg"
        ExportFormat.PNG -> "image/png"
        ExportFormat.WEBP -> "image/webp"
    }

    /**
     * Resolves standard file extension string based on [ExportFormat].
     */
    fun getExtension(format: ExportFormat): String = when (format) {
        ExportFormat.JPEG -> "jpg"
        ExportFormat.PNG -> "png"
        ExportFormat.WEBP -> "webp"
    }

    /**
     * Resolves [Bitmap.CompressFormat] compatible across Android API levels.
     */
    fun getCompressFormat(format: ExportFormat): Bitmap.CompressFormat = when (format) {
        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
    }

    /**
     * Saves a rendered [bitmap] to Android MediaStore in Pictures/CleanLightroom
     * using atomic [MediaStore.Images.Media.IS_PENDING] protection.
     *
     * @param context Application or UI context
     * @param bitmap Master rendered bitmap to encode and write
     * @param config Export configuration (format, quality, Exif toggle)
     * @param recipe Non-destructive EditRecipe for XMP embedding
     * @param sourceUri Original source photo Uri for Exif tag copying
     * @param customFilename Optional custom name without extension
     * @return Content Uri of the published photo in MediaStore
     */
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        config: ExportConfig,
        recipe: EditRecipe? = null,
        sourceUri: Uri? = null,
        customFilename: String? = null
    ): Uri {
        val resolver = context.contentResolver
        val ext = getExtension(config.format)
        val mimeType = getMimeType(config.format)
        val compressFormat = getCompressFormat(config.format)
        val filename = customFilename?.let { "$it.$ext" } ?: "CleanLR_${System.currentTimeMillis()}.$ext"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val targetUri = resolver.insert(collectionUri, contentValues)
            ?: throw IllegalStateException("MediaStore insert returned null for $filename")

        try {
            // 1. Stream bitmap compression
            resolver.openOutputStream(targetUri)?.use { outStream: OutputStream ->
                val compressed = bitmap.compress(compressFormat, config.quality, outStream)
                if (!compressed) {
                    throw IllegalStateException("Failed to compress bitmap into $mimeType")
                }
            } ?: throw IllegalStateException("Failed to open output stream for $targetUri")

            // 2. Exif preservation & non-destructive recipe embedding (JPEG only)
            if (config.includeExif && config.format == ExportFormat.JPEG && recipe != null) {
                try {
                    resolver.openFileDescriptor(targetUri, "rw")?.use { pfd ->
                        val targetExif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                        val sourceExif = sourceUri?.let { sUri ->
                            resolver.openInputStream(sUri)?.use { inStream ->
                                runCatching { androidx.exifinterface.media.ExifInterface(inStream) }.getOrNull()
                            }
                        }
                        ExportMetadataWriter.copyExifTags(sourceExif, targetExif)
                        ExportMetadataWriter.embedRecipe(targetExif, recipe)
                        targetExif.saveAttributes()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exif preservation skipped on target storage: ${e.message}")
                }
            }

            // 3. Atomically publish image by clearing IS_PENDING
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(targetUri, contentValues, null, null)
            }

            return targetUri
        } catch (e: Exception) {
            // Rollback and delete broken / 0-byte pending record
            runCatching { resolver.delete(targetUri, null, null) }
            throw e
        }
    }

    /**
     * Builds an [Intent.ACTION_SEND] intent to share an exported image with other apps.
     */
    fun createShareIntent(
        imageUri: Uri,
        mimeType: String = "image/jpeg",
        chooserTitle: String = "Share Image"
    ): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Directly launches the native Android share sheet for [imageUri].
     */
    fun launchShare(
        context: Context,
        imageUri: Uri,
        mimeType: String = "image/jpeg",
        chooserTitle: String = "Share Image"
    ) {
        val intent = createShareIntent(imageUri, mimeType, chooserTitle)
        context.startActivity(intent)
    }
}
