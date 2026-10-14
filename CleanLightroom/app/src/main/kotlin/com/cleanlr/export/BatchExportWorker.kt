package com.cleanlr.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * WorkManager CoroutineWorker for processing batch image exports in the background
 * with foreground notification updates and cancellation support.
 */
class BatchExportWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "BatchExportWorker"
        const val KEY_BATCH_ID = "key_batch_id"
        const val KEY_ITEMS_JSON = "key_items_json"
        const val CHANNEL_ID = "clean_lr_export_channel"
        const val NOTIFICATION_ID = 2002
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return@withContext Result.failure()
        val itemsJson = inputData.getString(KEY_ITEMS_JSON) ?: return@withContext Result.failure()

        val items = try {
            Json.decodeFromString<List<BatchExportItem>>(itemsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode batch items JSON", e)
            return@withContext Result.failure()
        }

        if (items.isEmpty()) {
            return@withContext Result.success()
        }

        // Show foreground notification
        try {
            setForeground(createForegroundInfo("Preparing batch export...", 0))
        } catch (e: Exception) {
            Log.w(TAG, "setForeground failed: ${e.message}")
        }

        val processor = DefaultBatchItemProcessor(context)
        val manager = BatchExportManager.instance

        val resultProgress = manager.executeBatch(
            batchId = batchId,
            items = items,
            processor = processor,
            onProgressUpdate = { progress ->
                // Check WorkManager cooperative stop
                if (isStopped) {
                    manager.cancelBatch(batchId)
                }

                try {
                    // Update notification
                    val notification = createForegroundInfo(progress.statusText, progress.overallProgressPercent)
                    // Note: setForeground can only be called from worker
                    kotlinx.coroutines.runBlocking {
                        runCatching { setForeground(notification) }
                    }
                } catch (ignored: Exception) {
                }

                // Update WorkManager progress data
                kotlinx.coroutines.runBlocking {
                    runCatching {
                        setProgress(
                            workDataOf(
                                "batchId" to progress.batchId,
                                "statusText" to progress.statusText,
                                "overallProgress" to progress.overallProgressPercent,
                                "currentItemIndex" to progress.currentItemIndex,
                                "totalCount" to progress.totalCount,
                                "isCancelled" to progress.isCancelled
                            )
                        )
                    }
                }
            }
        )

        if (resultProgress.isCancelled) {
            Result.failure(workDataOf("reason" to "cancelled"))
        } else if (resultProgress.completedCount > 0) {
            Result.success(
                workDataOf(
                    "completedCount" to resultProgress.completedCount,
                    "failedCount" to resultProgress.failedCount,
                    "totalCount" to resultProgress.totalCount
                )
            )
        } else {
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ExportNotificationHelper.createForegroundInfo(
            context = context,
            notificationId = NOTIFICATION_ID,
            title = "Clean Lightroom Batch Export",
            contentText = "Clean Lightroom Exporting...",
            progressPercent = 0,
            indeterminate = true
        )
    }

    private fun createForegroundInfo(statusText: String, progressPercent: Int): ForegroundInfo {
        return ExportNotificationHelper.createForegroundInfo(
            context = context,
            notificationId = NOTIFICATION_ID,
            title = "Clean Lightroom Batch Export",
            contentText = statusText,
            progressPercent = progressPercent,
            indeterminate = false
        )
    }
}
