package com.cleanlr.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * Helper to manage low-importance notification channels and compliant ForegroundInfo
 * instances for background WorkManager jobs under Android 12+ (FGS constraints) and Android 14+
 * (mandatory FOREGROUND_SERVICE_TYPE_DATA_SYNC).
 */
object ExportNotificationHelper {

    const val CHANNEL_ID = "clean_lr_export_channel"
    const val CHANNEL_NAME = "Clean Lightroom Export"
    const val NOTIFICATION_ID_SINGLE = 1001
    const val NOTIFICATION_ID_BATCH = 2002

    /**
     * Creates and registers the low-importance notification channel if not already present.
     */
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "High-resolution background export operations"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Builds a [ForegroundInfo] configured with [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC]
     * to satisfy Android 14+ background execution policies and prevent
     * [ForegroundServiceStartNotAllowedException].
     */
    fun createForegroundInfo(
        context: Context,
        notificationId: Int,
        title: String,
        contentText: String,
        progressPercent: Int = 0,
        indeterminate: Boolean = false
    ): ForegroundInfo {
        ensureNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progressPercent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
