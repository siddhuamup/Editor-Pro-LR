package com.cleanlr.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cleanlr.model.EditRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ExportImageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ExportImageWorker"
        const val KEY_IMAGE_URI = "key_image_uri"
        const val KEY_RECIPE_JSON = "key_recipe_json"
        const val KEY_CONFIG_JSON = "key_config_json"
        const val KEY_QUALITY = "key_quality"
        const val KEY_OUTPUT_URI = "key_output_uri"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val uriString = inputData.getString(KEY_IMAGE_URI) ?: return@withContext Result.failure()
            val recipeJson = inputData.getString(KEY_RECIPE_JSON) ?: return@withContext Result.failure()
            val configJson = inputData.getString(KEY_CONFIG_JSON)
            val config = if (configJson != null) {
                Json.decodeFromString<ExportConfig>(configJson)
            } else {
                ExportConfig(quality = inputData.getInt(KEY_QUALITY, 95))
            }

            val recipe = Json.decodeFromString<EditRecipe>(recipeJson)
            val sourceUri = Uri.parse(uriString)

            // 1. Establish Foreground Service notification for Android 12+ FGS compliance
            try {
                setForeground(
                    ExportNotificationHelper.createForegroundInfo(
                        context = applicationContext,
                        notificationId = ExportNotificationHelper.NOTIFICATION_ID_SINGLE,
                        title = "Clean Lightroom Export",
                        contentText = "फोटो लोड होत आहे... (10%)",
                        progressPercent = 10
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "setForeground non-fatal exception: ${e.message}")
            }

            setProgress(workDataOf("progress" to 10))

            // Load source bitmap
            val inputStream = applicationContext.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure()
            var sourceBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (sourceBitmap == null) {
                Log.e(TAG, "Failed to decode source bitmap")
                return@withContext Result.failure()
            }

            // Downscale if maxDimension constraint is configured (e.g. 2048px for web export)
            val maxDim = config.maxDimension
            if (maxDim != null && kotlin.math.max(sourceBitmap.width, sourceBitmap.height) > maxDim) {
                val scale = maxDim.toFloat() / kotlin.math.max(sourceBitmap.width, sourceBitmap.height)
                val targetW = kotlin.math.round((sourceBitmap.width * scale)).toInt()
                val targetH = kotlin.math.round((sourceBitmap.height * scale)).toInt()
                val scaled = Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
                if (scaled != sourceBitmap) {
                    sourceBitmap.recycle()
                    sourceBitmap = scaled
                }
            }

            try {
                setForeground(
                    ExportNotificationHelper.createForegroundInfo(
                        context = applicationContext,
                        notificationId = ExportNotificationHelper.NOTIFICATION_ID_SINGLE,
                        title = "Clean Lightroom Export",
                        contentText = "GPU शेडर आणि टाइल प्रोसेसिंग चालू आहे... (30%)",
                        progressPercent = 30
                    )
                )
            } catch (ignored: Exception) {}
            setProgress(workDataOf("progress" to 30))

            // Run Headless GL rendering
            val renderer = HeadlessGlRenderer(applicationContext)
            val renderedBitmap = try {
                renderer.renderFullResolution(sourceBitmap, recipe)
            } finally {
                sourceBitmap.recycle()
            }

            try {
                setForeground(
                    ExportNotificationHelper.createForegroundInfo(
                        context = applicationContext,
                        notificationId = ExportNotificationHelper.NOTIFICATION_ID_SINGLE,
                        title = "Clean Lightroom Export",
                        contentText = "MediaStore व Exif सेव्ह होत आहे... (80%)",
                        progressPercent = 80
                    )
                )
            } catch (ignored: Exception) {}
            setProgress(workDataOf("progress" to 80))

            // Save to MediaStore with Exif preservation and recipe embedding
            val outputUri = saveBitmapToMediaStore(renderedBitmap, config, recipe, sourceUri)

            setProgress(workDataOf("progress" to 100))

            if (outputUri != null) {
                Result.success(workDataOf(KEY_OUTPUT_URI to outputUri.toString()))
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during export", e)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        return ExportNotificationHelper.createForegroundInfo(
            context = applicationContext,
            notificationId = ExportNotificationHelper.NOTIFICATION_ID_SINGLE,
            title = "Clean Lightroom Export",
            contentText = "एक्सपोर्ट सुरू होत आहे...",
            progressPercent = 0,
            indeterminate = true
        )
    }

    private fun saveBitmapToMediaStore(
        bitmap: Bitmap,
        config: ExportConfig,
        recipe: EditRecipe,
        sourceUri: Uri
    ): Uri? {
        val extension = when (config.format) {
            ExportFormat.JPEG -> "jpg"
            ExportFormat.PNG -> "png"
            ExportFormat.WEBP -> "webp"
        }
        val mimeType = when (config.format) {
            ExportFormat.JPEG -> "image/jpeg"
            ExportFormat.PNG -> "image/png"
            ExportFormat.WEBP -> "image/webp"
        }
        val compressFormat = when (config.format) {
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

        val filename = "CleanLR_${System.currentTimeMillis()}.$extension"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CleanLightroom")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = applicationContext.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            resolver.openOutputStream(imageUri)?.use { outStream: OutputStream ->
                bitmap.compress(compressFormat, config.quality, outStream)
            }

            // Inject preserved Exif metadata & embedded EditRecipe (JPEG only)
            if (config.includeExif && config.format == ExportFormat.JPEG) {
                try {
                    resolver.openFileDescriptor(imageUri, "rw")?.use { pfd ->
                        val targetExif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                        val sourceExif = resolver.openInputStream(sourceUri)?.use { inStream ->
                            runCatching { androidx.exifinterface.media.ExifInterface(inStream) }.getOrNull()
                        }
                        ExportMetadataWriter.copyExifTags(sourceExif, targetExif)
                        ExportMetadataWriter.embedRecipe(targetExif, recipe)
                        targetExif.saveAttributes()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exif injection skipped or unsupported on target storage: ${e.message}")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
        }
        return imageUri
    }
}
