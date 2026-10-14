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

            // Save to MediaStore via MediaStoreWriter
            val outputUri = MediaStoreWriter.saveImage(
                context = applicationContext,
                bitmap = renderedBitmap,
                config = config,
                recipe = recipe,
                sourceUri = sourceUri
            )

            setProgress(workDataOf("progress" to 100))

            // Auto-launch native share sheet if enabled in config
            if (config.autoShare) {
                try {
                    MediaStoreWriter.launchShare(
                        context = applicationContext,
                        imageUri = outputUri,
                        mimeType = MediaStoreWriter.getMimeType(config.format)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-share launch failed: ${e.message}")
                }
            }

            Result.success(workDataOf(KEY_OUTPUT_URI to outputUri.toString()))
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
}
