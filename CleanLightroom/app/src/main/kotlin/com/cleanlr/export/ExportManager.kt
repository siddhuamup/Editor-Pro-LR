package com.cleanlr.export

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.cleanlr.model.EditRecipe
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * High-level Export Facade for enqueuing single photo and batch export tasks
 * via WorkManager and tracking progress.
 */
object ExportManager {

    /**
     * Builds an expedited [OneTimeWorkRequest] for a single photo export, adhering to
     * Android 12+ foreground service guidelines via [OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST].
     */
    fun createSingleWorkRequest(
        sourceImageUriString: String,
        recipe: EditRecipe,
        config: ExportConfig = ExportConfig()
    ): OneTimeWorkRequest {
        val recipeJson = Json.encodeToString(recipe)
        return OneTimeWorkRequestBuilder<ExportImageWorker>()
            .setInputData(
                workDataOf(
                    ExportImageWorker.KEY_IMAGE_URI to sourceImageUriString,
                    ExportImageWorker.KEY_RECIPE_JSON to recipeJson,
                    ExportImageWorker.KEY_QUALITY to config.quality
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("single_export_${sourceImageUriString.hashCode()}")
            .build()
    }

    fun createSingleWorkRequest(
        sourceImageUri: Uri,
        recipe: EditRecipe,
        config: ExportConfig = ExportConfig()
    ): OneTimeWorkRequest {
        return createSingleWorkRequest(sourceImageUri?.toString() ?: "", recipe, config)
    }

    /**
     * Builds an expedited [OneTimeWorkRequest] for a batch export operation.
     */
    fun createBatchWorkRequest(
        batchId: String,
        items: List<BatchExportItem>
    ): OneTimeWorkRequest {
        val itemsJson = Json.encodeToString(items)
        return OneTimeWorkRequestBuilder<BatchExportWorker>()
            .setInputData(
                workDataOf(
                    BatchExportWorker.KEY_BATCH_ID to batchId,
                    BatchExportWorker.KEY_ITEMS_JSON to itemsJson
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("batch_export_$batchId")
            .build()
    }

    /**
     * Enqueues a single photo export through WorkManager with [ExportConfig].
     */
    fun enqueueSingle(
        context: Context,
        sourceImageUri: Uri,
        recipe: EditRecipe,
        config: ExportConfig = ExportConfig()
    ): UUID {
        val workRequest = createSingleWorkRequest(sourceImageUri, recipe, config)
        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }

    /**
     * Enqueues a batch of 10 to 50+ photos for sequential background export.
     * Returns the unique [batchId] used to track progress or cancel the operation.
     */
    fun enqueueBatch(
        context: Context,
        items: List<BatchExportItem>,
        batchId: String = UUID.randomUUID().toString()
    ): String {
        // Initialize progress tracker in BatchExportManager
        val initialProgress = BatchExportProgress(
            batchId = batchId,
            totalCount = items.size,
            itemStates = items.map {
                BatchItemProgress(
                    id = it.id,
                    sourceUriString = it.sourceUriString,
                    status = BatchItemStatus.QUEUED
                )
            }
        )

        val workRequest = createBatchWorkRequest(batchId, items)

        WorkManager.getInstance(context).enqueueUniqueWork(
            "batch_work_$batchId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        return batchId
    }

    /**
     * Cancels an ongoing batch export by [batchId] via WorkManager and the [BatchExportManager].
     */
    fun cancelBatch(context: Context, batchId: String) {
        BatchExportManager.instance.cancelBatch(batchId)
        WorkManager.getInstance(context).cancelUniqueWork("batch_work_$batchId")
    }

    /**
     * Observes real-time batch progress for [batchId].
     */
    fun observeBatchProgress(batchId: String): StateFlow<BatchExportProgress?> {
        return BatchExportManager.instance.observeProgress(batchId)
    }
}
