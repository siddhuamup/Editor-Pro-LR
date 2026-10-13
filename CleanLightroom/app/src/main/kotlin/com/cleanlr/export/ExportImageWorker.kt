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
        const val KEY_QUALITY = "key_quality"
        const val KEY_OUTPUT_URI = "key_output_uri"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val uriString = inputData.getString(KEY_IMAGE_URI) ?: return@withContext Result.failure()
            val recipeJson = inputData.getString(KEY_RECIPE_JSON) ?: return@withContext Result.failure()
            val quality = inputData.getInt(KEY_QUALITY, 95)

            val recipe = Json.decodeFromString<EditRecipe>(recipeJson)
            val sourceUri = Uri.parse(uriString)

            setProgress(workDataOf("progress" to 10))

            // Load source bitmap
            val inputStream = applicationContext.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure()
            val sourceBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (sourceBitmap == null) {
                Log.e(TAG, "Failed to decode source bitmap")
                return@withContext Result.failure()
            }

            setProgress(workDataOf("progress" to 30))

            // Run Headless GL rendering
            val renderer = HeadlessGlRenderer(applicationContext)
            val renderedBitmap = renderer.renderFullResolution(sourceBitmap, recipe)

            setProgress(workDataOf("progress" to 80))

            // Save to MediaStore
            val outputUri = saveBitmapToMediaStore(renderedBitmap, quality)

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

    private fun saveBitmapToMediaStore(bitmap: Bitmap, quality: Int): Uri? {
        val filename = "CleanLR_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CleanLightroom")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = applicationContext.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            resolver.openOutputStream(imageUri)?.use { outStream: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
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
