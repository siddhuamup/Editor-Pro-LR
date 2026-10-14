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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Processor contract for individual export items, facilitating unit testing without
 * live EGL hardware contexts.
 */
fun interface BatchItemProcessor {
    suspend fun processItem(
        item: BatchExportItem,
        onItemProgress: (percent: Int) -> Unit,
        isCancelled: () -> Boolean
    ): String
}

/**
 * Default production implementation of [BatchItemProcessor] using [HeadlessGlRenderer],
 * [TileProcessor], and MediaStore output with Exif preservation.
 */
class DefaultBatchItemProcessor(private val context: Context) : BatchItemProcessor {

    override suspend fun processItem(
        item: BatchExportItem,
        onItemProgress: (percent: Int) -> Unit,
        isCancelled: () -> Boolean
    ): String = withContext(Dispatchers.IO) {
        if (isCancelled()) throw CancellationException("Item export cancelled")

        onItemProgress(10)

        // 1. Load source bitmap
        val inputStream = context.contentResolver.openInputStream(item.sourceUri)
            ?: throw IllegalStateException("Could not open source URI: ${item.sourceUriString}")
        var sourceBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (sourceBitmap == null) {
            throw IllegalStateException("Failed to decode source bitmap")
        }

        if (isCancelled()) {
            sourceBitmap.recycle()
            throw CancellationException("Item export cancelled before rendering")
        }

        onItemProgress(25)

        // 2. Downscale if maxDimension constraint is configured
        val maxDim = item.config.maxDimension
        if (maxDim != null && max(sourceBitmap.width, sourceBitmap.height) > maxDim) {
            val scale = maxDim.toFloat() / max(sourceBitmap.width, sourceBitmap.height)
            val targetW = (sourceBitmap.width * scale).roundToInt()
            val targetH = (sourceBitmap.height * scale).roundToInt()
            val scaled = Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
            if (scaled != sourceBitmap) {
                sourceBitmap.recycle()
                sourceBitmap = scaled
            }
        }

        onItemProgress(35)

        // 3. Render via HeadlessGlRenderer & TileProcessor
        val renderer = HeadlessGlRenderer(context)
        val renderedBitmap = try {
            renderer.renderFullResolution(sourceBitmap, item.recipe)
        } finally {
            sourceBitmap.recycle()
        }

        if (isCancelled()) {
            renderedBitmap.recycle()
            throw CancellationException("Item export cancelled after rendering")
        }

        onItemProgress(75)

        // 4. Save to MediaStore
        val outputUri = try {
            saveToMediaStore(context, renderedBitmap, item)
        } finally {
            renderedBitmap.recycle()
        }

        onItemProgress(100)
        return@withContext outputUri.toString()
    }

    private fun saveToMediaStore(
        context: Context,
        bitmap: Bitmap,
        item: BatchExportItem
    ): Uri {
        val extension = when (item.config.format) {
            ExportFormat.JPEG -> "jpg"
            ExportFormat.PNG -> "png"
            ExportFormat.WEBP -> "webp"
        }
        val mimeType = when (item.config.format) {
            ExportFormat.JPEG -> "image/jpeg"
            ExportFormat.PNG -> "image/png"
            ExportFormat.WEBP -> "image/webp"
        }
        val compressFormat = when (item.config.format) {
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

        val filename = "CleanLR_${System.currentTimeMillis()}_${item.id.take(6)}.$extension"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CleanLightroom")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("MediaStore insert returned null")

        resolver.openOutputStream(imageUri)?.use { outStream: OutputStream ->
            bitmap.compress(compressFormat, item.config.quality, outStream)
        }

        // 5. Exif and non-destructive recipe preservation
        if (item.config.includeExif && item.config.format == ExportFormat.JPEG) {
            try {
                resolver.openFileDescriptor(imageUri, "rw")?.use { pfd ->
                    val targetExif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                    val sourceExif = resolver.openInputStream(item.sourceUri)?.use { inStream ->
                        runCatching { androidx.exifinterface.media.ExifInterface(inStream) }.getOrNull()
                    }
                    ExportMetadataWriter.copyExifTags(sourceExif, targetExif)
                    ExportMetadataWriter.embedRecipe(targetExif, item.recipe)
                    targetExif.saveAttributes()
                }
            } catch (e: Exception) {
                Log.w("DefaultBatchProcessor", "Exif embedding skipped: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }

        return imageUri
    }
}

/**
 * Manages batch export operations for 10-50+ images.
 * Provides queue management, atomic progress tracking (e.g. "Exporting 3 of 10..."),
 * and graceful mid-batch cancellation.
 */
class BatchExportManager {

    companion object {
        const val TAG = "BatchExportManager"
        val instance: BatchExportManager by lazy { BatchExportManager() }
    }

    private val progressFlows = ConcurrentHashMap<String, MutableStateFlow<BatchExportProgress>>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val cancelledBatchIds = ConcurrentHashMap.newKeySet<String>()
    private val mutex = Mutex()

    /**
     * Obtains the observable [StateFlow] of progress for a given [batchId].
     */
    fun observeProgress(batchId: String): StateFlow<BatchExportProgress?> {
        val flow = progressFlows.computeIfAbsent(batchId) {
            MutableStateFlow(
                BatchExportProgress(
                    batchId = batchId,
                    totalCount = 0
                )
            )
        }
        return flow.asStateFlow()
    }

    /**
     * Gets current progress snapshot for [batchId].
     */
    fun getProgress(batchId: String): BatchExportProgress? {
        return progressFlows[batchId]?.value
    }

    /**
     * Cancels an ongoing batch export. Stops active item execution and marks
     * all queued items as [BatchItemStatus.CANCELLED].
     */
    fun cancelBatch(batchId: String) {
        cancelledBatchIds.add(batchId)
        activeJobs[batchId]?.cancel()

        val currentProgress = progressFlows[batchId]?.value ?: return
        val updatedStates = currentProgress.itemStates.map { itemProgress ->
            if (itemProgress.status == BatchItemStatus.QUEUED || itemProgress.status == BatchItemStatus.PROCESSING) {
                itemProgress.copy(status = BatchItemStatus.CANCELLED)
            } else {
                itemProgress
            }
        }
        val completed = updatedStates.count { it.status == BatchItemStatus.COMPLETED }
        val failed = updatedStates.count { it.status == BatchItemStatus.FAILED }
        val cancelled = updatedStates.count { it.status == BatchItemStatus.CANCELLED }

        val finalProgress = currentProgress.copy(
            completedCount = completed,
            failedCount = failed,
            cancelledCount = cancelled,
            isCancelled = true,
            isFinished = true,
            itemStates = updatedStates
        )
        progressFlows[batchId]?.value = finalProgress
    }

    /**
     * Initiates asynchronous batch processing in the provided [scope].
     * Returns the unique [batchId].
     */
    fun startBatch(
        items: List<BatchExportItem>,
        processor: BatchItemProcessor,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
        batchId: String = UUID.randomUUID().toString()
    ): String {
        val initialStates = items.map { item ->
            BatchItemProgress(
                id = item.id,
                sourceUriString = item.sourceUriString,
                status = BatchItemStatus.QUEUED
            )
        }

        val initialProgress = BatchExportProgress(
            batchId = batchId,
            totalCount = items.size,
            itemStates = initialStates
        )

        val flow = progressFlows.computeIfAbsent(batchId) { MutableStateFlow(initialProgress) }
        flow.value = initialProgress

        val job = scope.launch {
            executeBatch(batchId, items, processor)
        }
        activeJobs[batchId] = job
        job.invokeOnCompletion {
            activeJobs.remove(batchId)
        }

        return batchId
    }

    /**
     * Executes the batch sequentially with cooperative cancellation and atomic progress updates.
     */
    suspend fun executeBatch(
        batchId: String,
        items: List<BatchExportItem>,
        processor: BatchItemProcessor,
        onProgressUpdate: ((BatchExportProgress) -> Unit)? = null
    ): BatchExportProgress {
        val total = items.size
        val itemStates = items.map {
            BatchItemProgress(id = it.id, sourceUriString = it.sourceUriString, status = BatchItemStatus.QUEUED)
        }.toMutableList()

        val flow = progressFlows.computeIfAbsent(batchId) {
            MutableStateFlow(
                BatchExportProgress(
                    batchId = batchId,
                    totalCount = total,
                    itemStates = itemStates.toList()
                )
            )
        }

        fun emitProgress(
            currentItemIndex: Int,
            itemProgress: Int,
            isCancelled: Boolean = false,
            isFinished: Boolean = false
        ) {
            val completed = itemStates.count { it.status == BatchItemStatus.COMPLETED }
            val failed = itemStates.count { it.status == BatchItemStatus.FAILED }
            val cancelled = itemStates.count { it.status == BatchItemStatus.CANCELLED }

            val overall = if (total > 0) {
                if (isFinished && !isCancelled) 100
                else {
                    val weighted = (completed * 100 + itemProgress) / total
                    weighted.coerceIn(0, 100)
                }
            } else 100

            val progress = BatchExportProgress(
                batchId = batchId,
                totalCount = total,
                completedCount = completed,
                failedCount = failed,
                cancelledCount = cancelled,
                currentItemIndex = currentItemIndex,
                currentItemProgress = itemProgress,
                overallProgressPercent = overall,
                isCancelled = isCancelled,
                isFinished = isFinished,
                itemStates = itemStates.toList()
            )

            flow.value = progress
            onProgressUpdate?.invoke(progress)
        }

        // Check if already cancelled before starting
        if (cancelledBatchIds.contains(batchId)) {
            itemStates.indices.forEach { idx ->
                itemStates[idx] = itemStates[idx].copy(status = BatchItemStatus.CANCELLED)
            }
            emitProgress(0, 0, isCancelled = true, isFinished = true)
            return flow.value
        }

        for (index in items.indices) {
            val item = items[index]
            val itemIndex1Based = index + 1

            // 1. Check cancellation before item
            if (cancelledBatchIds.contains(batchId)) {
                for (rem in index until items.size) {
                    itemStates[rem] = itemStates[rem].copy(status = BatchItemStatus.CANCELLED)
                }
                emitProgress(itemIndex1Based, 0, isCancelled = true, isFinished = true)
                break
            }

            // 2. Mark item PROCESSING
            itemStates[index] = itemStates[index].copy(
                status = BatchItemStatus.PROCESSING,
                progressPercent = 0
            )
            emitProgress(itemIndex1Based, 0)

            try {
                val outputUriString = processor.processItem(
                    item = item,
                    onItemProgress = { percent ->
                        itemStates[index] = itemStates[index].copy(progressPercent = percent)
                        emitProgress(itemIndex1Based, percent)
                    },
                    isCancelled = { cancelledBatchIds.contains(batchId) }
                )

                // Item Completed
                itemStates[index] = itemStates[index].copy(
                    status = BatchItemStatus.COMPLETED,
                    progressPercent = 100,
                    outputUriString = outputUriString
                )
                emitProgress(itemIndex1Based, 100)

            } catch (e: CancellationException) {
                // Cancelled during processing
                cancelledBatchIds.add(batchId)
                itemStates[index] = itemStates[index].copy(
                    status = BatchItemStatus.CANCELLED,
                    error = "Cancelled by user"
                )
                for (rem in (index + 1) until items.size) {
                    itemStates[rem] = itemStates[rem].copy(status = BatchItemStatus.CANCELLED)
                }
                emitProgress(itemIndex1Based, 0, isCancelled = true, isFinished = true)
                break
            } catch (e: Exception) {
                // Item failed - log and continue to next item in batch!
                Log.e(TAG, "Item ${item.id} failed: ${e.message}", e)
                itemStates[index] = itemStates[index].copy(
                    status = BatchItemStatus.FAILED,
                    error = e.message ?: "Unknown error"
                )
                emitProgress(itemIndex1Based, 0)
            }
        }

        // Finalize if not cancelled
        if (!cancelledBatchIds.contains(batchId)) {
            emitProgress(total, 100, isCancelled = false, isFinished = true)
        }

        return flow.value
    }
}
