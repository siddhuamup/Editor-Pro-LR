package com.cleanlr.export

import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import com.cleanlr.model.PresenceAdjustments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Task T6: Batch Export (10 Photos, Progress Tracking, Graceful Cancellation & Resiliency)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchExportTest {

    private fun createSampleRecipe(index: Int): EditRecipe {
        return EditRecipe(
            basic = BasicAdjustments(
                exposure = 0.1f * index,
                contrast = 10f + index,
                highlights = -5f * index
            ),
            presence = PresenceAdjustments(
                clarity = 5f * index,
                vibrance = 10f
            )
        )
    }

    private fun createBatch(count: Int, format: ExportFormat = ExportFormat.JPEG): List<BatchExportItem> {
        return (1..count).map { i ->
            BatchExportItem(
                id = "item_$i",
                sourceUriString = "content://media/external/images/media/$i",
                recipe = createSampleRecipe(i),
                config = ExportConfig(
                    format = format,
                    quality = 90,
                    maxDimension = if (i % 2 == 0) 2048 else null,
                    includeExif = true
                )
            )
        }
    }

    @Test
    fun `test 10-photo batch exports sequentially to 100 percent completion`() = runBlocking {
        val manager = BatchExportManager()
        val items = createBatch(10)
        val progressHistory = mutableListOf<BatchExportProgress>()

        val processor = BatchItemProcessor { item, onItemProgress, _ ->
            onItemProgress(25)
            onItemProgress(50)
            onItemProgress(75)
            "content://media/external/images/output_${item.id}"
        }

        val result = manager.executeBatch(
            batchId = "batch_full_10",
            items = items,
            processor = processor,
            onProgressUpdate = { progressHistory.add(it) }
        )

        // Verify terminal state
        assertTrue("Batch should be finished", result.isFinished)
        assertFalse("Batch should not be cancelled", result.isCancelled)
        assertEquals("Total count must be 10", 10, result.totalCount)
        assertEquals("Completed count must be 10", 10, result.completedCount)
        assertEquals("Failed count must be 0", 0, result.failedCount)
        assertEquals("Cancelled count must be 0", 0, result.cancelledCount)
        assertEquals("Overall progress must reach 100%", 100, result.overallProgressPercent)
        assertEquals("Export complete (10/10)", result.statusText)

        // Verify each individual item status
        assertEquals(10, result.itemStates.size)
        result.itemStates.forEachIndexed { idx, itemProgress ->
            assertEquals(BatchItemStatus.COMPLETED, itemProgress.status)
            assertEquals(100, itemProgress.progressPercent)
            assertEquals("content://media/external/images/output_item_${idx + 1}", itemProgress.outputUriString)
            assertNull(itemProgress.error)
        }

        // Verify progress events progressed through all 10 items
        val indicesEncountered = progressHistory.map { it.currentItemIndex }.distinct()
        assertTrue("Should progress through items 1..10", indicesEncountered.containsAll((1..10).toList()))
    }

    @Test
    fun `test mid-batch cancellation stops execution and cancels remaining items gracefully`() = runBlocking {
        val manager = BatchExportManager()
        val items = createBatch(10)
        val batchId = "batch_cancel_mid"

        val processedCount = AtomicInteger(0)
        val processor = BatchItemProcessor { item, onItemProgress, isCancelled ->
            val count = processedCount.incrementAndGet()
            if (count == 4) {
                // Cancel batch dynamically while item 4 is executing
                manager.cancelBatch(batchId)
            }
            if (isCancelled()) {
                throw kotlinx.coroutines.CancellationException("Cancelled mid-item")
            }
            onItemProgress(50)
            "content://media/external/images/output_${item.id}"
        }

        val result = manager.executeBatch(
            batchId = batchId,
            items = items,
            processor = processor
        )

        // Verify cancellation state
        assertTrue("Batch must be marked cancelled", result.isCancelled)
        assertTrue("Batch must be finished", result.isFinished)
        assertEquals("Total items must be 10", 10, result.totalCount)
        assertEquals("First 3 items must have completed successfully", 3, result.completedCount)
        assertEquals("7 items must be cancelled (item 4 + items 5..10)", 7, result.cancelledCount)
        assertEquals("Failed count must be 0", 0, result.failedCount)

        // Verify item states
        for (i in 0 until 3) {
            assertEquals("Item $i must be COMPLETED", BatchItemStatus.COMPLETED, result.itemStates[i].status)
            assertNotNull(result.itemStates[i].outputUriString)
        }
        for (i in 3 until 10) {
            assertEquals("Item $i must be CANCELLED", BatchItemStatus.CANCELLED, result.itemStates[i].status)
        }

        // Verify cancellation status text
        assertEquals("Batch export cancelled (3 of 10 completed)", result.statusText)
    }

    @Test
    fun `test batch failure resiliency allows remaining items to continue and complete`() = runBlocking {
        val manager = BatchExportManager()
        val items = createBatch(10)
        val batchId = "batch_resiliency_test"

        val processor = BatchItemProcessor { item, onItemProgress, _ ->
            if (item.id == "item_5") {
                throw IllegalStateException("Simulated decode failure on photo 5")
            }
            onItemProgress(100)
            "content://media/external/images/output_${item.id}"
        }

        val result = manager.executeBatch(
            batchId = batchId,
            items = items,
            processor = processor
        )

        assertTrue(result.isFinished)
        assertFalse(result.isCancelled)
        assertEquals(10, result.totalCount)
        assertEquals(9, result.completedCount)
        assertEquals(1, result.failedCount)
        assertEquals(0, result.cancelledCount)

        // Item 5 must be FAILED with error message preserved
        val item5 = result.itemStates.first { it.id == "item_5" }
        assertEquals(BatchItemStatus.FAILED, item5.status)
        assertTrue(item5.error?.contains("Simulated decode failure") == true)

        // All other items must be COMPLETED
        val otherItems = result.itemStates.filter { it.id != "item_5" }
        assertEquals(9, otherItems.size)
        assertTrue(otherItems.all { it.status == BatchItemStatus.COMPLETED })

        assertEquals("Export complete with 1 errors (9/10)", result.statusText)
    }

    @Test
    fun `test progress text accurately tracks incremental 10-item advancement`() = runBlocking {
        val manager = BatchExportManager()
        val items = createBatch(10)
        val statusTexts = mutableListOf<String>()

        val processor = BatchItemProcessor { item, onItemProgress, _ ->
            onItemProgress(50)
            "output_${item.id}"
        }

        manager.executeBatch(
            batchId = "batch_status_test",
            items = items,
            processor = processor,
            onProgressUpdate = { statusTexts.add(it.statusText) }
        )

        // Verify status texts contain progress steps
        assertTrue(statusTexts.any { it.contains("Exporting 1 of 10") })
        assertTrue(statusTexts.any { it.contains("Exporting 5 of 10") })
        assertTrue(statusTexts.any { it.contains("Exporting 10 of 10") })
        assertEquals("Export complete (10/10)", statusTexts.last())
    }

    @Test
    fun `test export configurations retain format, quality, and maxDimension`() {
        val jpegItem = BatchExportItem(
            sourceUriString = "content://media/1",
            recipe = EditRecipe(),
            config = ExportConfig(format = ExportFormat.JPEG, quality = 95, maxDimension = 2048)
        )
        val pngItem = BatchExportItem(
            sourceUriString = "content://media/2",
            recipe = EditRecipe(),
            config = ExportConfig(format = ExportFormat.PNG, quality = 100, maxDimension = null)
        )
        val webpItem = BatchExportItem(
            sourceUriString = "content://media/3",
            recipe = EditRecipe(),
            config = ExportConfig(format = ExportFormat.WEBP, quality = 85, maxDimension = 1080)
        )

        assertEquals(ExportFormat.JPEG, jpegItem.config.format)
        assertEquals(95, jpegItem.config.quality)
        assertEquals(2048, jpegItem.config.maxDimension)

        assertEquals(ExportFormat.PNG, pngItem.config.format)
        assertEquals(100, pngItem.config.quality)
        assertNull(pngItem.config.maxDimension)

        assertEquals(ExportFormat.WEBP, webpItem.config.format)
        assertEquals(85, webpItem.config.quality)
        assertEquals(1080, webpItem.config.maxDimension)
    }
}
