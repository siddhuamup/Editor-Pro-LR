package com.cleanlr.export

import android.net.Uri
import androidx.work.OutOfQuotaPolicy
import com.cleanlr.model.BasicAdjustments
import com.cleanlr.model.EditRecipe
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Task T7: Android 12+ Foreground Service & Expedited WorkManager Compliance Test
 *
 * Verifies immunity to ForegroundServiceStartNotAllowedException, enforcement of
 * OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, dataSync foreground service type,
 * and Android 13+ POST_NOTIFICATIONS permission declaration.
 */
class FgsComplianceTest {

    @Test
    fun `test single export work request is configured as expedited with fallback quota policy`() {
        val dummyUriString = "content://media/external/images/media/42"
        val recipe = EditRecipe(basic = BasicAdjustments(exposure = 0.5f))
        val config = ExportConfig(format = ExportFormat.JPEG, quality = 88)

        val workRequest = ExportManager.createSingleWorkRequest(dummyUriString, recipe, config)

        // Verify WorkSpec has expedited enabled
        assertTrue("Single export WorkRequest MUST have isExpedited == true", workRequest.workSpec.expedited)
        assertEquals(
            "OutOfQuotaPolicy must be RUN_AS_NON_EXPEDITED_WORK_REQUEST to prevent background crash",
            OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST,
            workRequest.workSpec.outOfQuotaPolicy
        )

        // Verify payload integrity
        val inputData = workRequest.workSpec.input
        assertEquals("content://media/external/images/media/42", inputData.getString(ExportImageWorker.KEY_IMAGE_URI))
        assertEquals(88, inputData.getInt(ExportImageWorker.KEY_QUALITY, -1))
        assertNotNull(inputData.getString(ExportImageWorker.KEY_RECIPE_JSON))
    }

    @Test
    fun `test batch export work request is configured as expedited with fallback quota policy`() {
        val batchId = "batch_expedited_fgs_test"
        val items = listOf(
            BatchExportItem(
                id = "item_1",
                sourceUriString = "content://media/1",
                recipe = EditRecipe()
            ),
            BatchExportItem(
                id = "item_2",
                sourceUriString = "content://media/2",
                recipe = EditRecipe()
            )
        )

        val workRequest = ExportManager.createBatchWorkRequest(batchId, items)

        assertTrue("Batch export WorkRequest MUST have isExpedited == true", workRequest.workSpec.expedited)
        assertEquals(
            "OutOfQuotaPolicy must be RUN_AS_NON_EXPEDITED_WORK_REQUEST",
            OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST,
            workRequest.workSpec.outOfQuotaPolicy
        )

        val inputData = workRequest.workSpec.input
        assertEquals(batchId, inputData.getString(BatchExportWorker.KEY_BATCH_ID))
        assertNotNull(inputData.getString(BatchExportWorker.KEY_ITEMS_JSON))
    }

    @Test
    fun `test notification channel and ids meet low-importance non-intrusive criteria`() {
        assertEquals("clean_lr_export_channel", ExportNotificationHelper.CHANNEL_ID)
        assertEquals("Clean Lightroom Export", ExportNotificationHelper.CHANNEL_NAME)
        assertEquals(1001, ExportNotificationHelper.NOTIFICATION_ID_SINGLE)
        assertEquals(2002, ExportNotificationHelper.NOTIFICATION_ID_BATCH)
    }

    @Test
    fun `test AndroidManifest declares all mandatory Android 12-14 FGS permissions and services`() {
        // Find AndroidManifest.xml
        val possiblePaths = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("CleanLightroom/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = possiblePaths.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist in the workspace", manifestFile)

        val manifestContent = manifestFile!!.readText()

        // 1. FOREGROUND_SERVICE permission (Android 9+)
        assertTrue(
            "Manifest must declare FOREGROUND_SERVICE permission",
            manifestContent.contains("android.permission.FOREGROUND_SERVICE\"")
        )

        // 2. FOREGROUND_SERVICE_DATA_SYNC permission (Mandatory for Android 14+ FGS)
        assertTrue(
            "Manifest must declare FOREGROUND_SERVICE_DATA_SYNC permission",
            manifestContent.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC\"")
        )

        // 3. POST_NOTIFICATIONS permission (Mandatory for Android 13+ runtime notifications)
        assertTrue(
            "Manifest must declare POST_NOTIFICATIONS permission",
            manifestContent.contains("android.permission.POST_NOTIFICATIONS\"")
        )

        // 4. SystemForegroundService with dataSync type
        assertTrue(
            "Manifest must register SystemForegroundService",
            manifestContent.contains("androidx.work.impl.foreground.SystemForegroundService")
        )
        assertTrue(
            "SystemForegroundService must declare foregroundServiceType=\"dataSync\"",
            manifestContent.contains("android:foregroundServiceType=\"dataSync\"")
        )
    }
}
