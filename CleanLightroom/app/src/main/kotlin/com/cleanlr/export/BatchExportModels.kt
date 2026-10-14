package com.cleanlr.export

import android.net.Uri
import com.cleanlr.model.EditRecipe
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Lifecycle status of an individual item in a batch export queue.
 */
@Serializable
enum class BatchItemStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Represents a single photo export task in a batch queue.
 *
 * @param id Unique identifier for this export task
 * @param sourceUriString String representation of the image Uri
 * @param recipe Non-destructive EditRecipe with adjustments to render
 * @param config Export configuration (format, quality, maxDimension, Exif)
 */
@Serializable
data class BatchExportItem(
    val id: String = UUID.randomUUID().toString(),
    val sourceUriString: String,
    val recipe: EditRecipe,
    val config: ExportConfig = ExportConfig()
) {
    val sourceUri: Uri get() = Uri.parse(sourceUriString)
}

/**
 * State snapshot of an individual item during batch processing.
 */
@Serializable
data class BatchItemProgress(
    val id: String,
    val sourceUriString: String,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val progressPercent: Int = 0,
    val outputUriString: String? = null,
    val error: String? = null
)

/**
 * Aggregate state of an entire batch export operation.
 *
 * @param batchId Unique identifier for the batch
 * @param totalCount Total number of items in this batch
 * @param completedCount Number of items successfully rendered and saved
 * @param failedCount Number of items that failed
 * @param cancelledCount Number of items cancelled
 * @param currentItemIndex 1-based index of current photo (e.g. 3 for "3 of 10")
 * @param currentItemProgress Progress percent of current photo (0..100)
 * @param overallProgressPercent Weighted aggregate progress percent across the whole batch (0..100)
 * @param isCancelled Whether the batch was cancelled by user
 * @param isFinished Whether all processing has completed or terminated
 * @param itemStates List of individual item states in order
 */
@Serializable
data class BatchExportProgress(
    val batchId: String,
    val totalCount: Int,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val cancelledCount: Int = 0,
    val currentItemIndex: Int = 0,
    val currentItemProgress: Int = 0,
    val overallProgressPercent: Int = 0,
    val isCancelled: Boolean = false,
    val isFinished: Boolean = false,
    val itemStates: List<BatchItemProgress> = emptyList()
) {
    val statusText: String
        get() = when {
            isCancelled -> "Batch export cancelled ($completedCount of $totalCount completed)"
            isFinished && failedCount == 0 -> "Export complete ($completedCount/$totalCount)"
            isFinished -> "Export complete with $failedCount errors ($completedCount/$totalCount)"
            currentItemIndex > 0 -> "Exporting $currentItemIndex of $totalCount... ($overallProgressPercent%)"
            else -> "Preparing batch ($totalCount items)..."
        }
}
