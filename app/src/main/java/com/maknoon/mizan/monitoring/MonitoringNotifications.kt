package com.maknoon.mizan.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.maknoon.mizan.R

/** Builds the ongoing notification that a foreground monitoring service is required to show. */
object MonitoringNotifications {

    const val MONITORING_NOTIFICATION_ID = 1001

    private const val MONITORING_CHANNEL_ID = "monitoring"

    /**
     * Low importance on purpose: this notification refreshes every second, so it must stay
     * silent and never buzz. High importance with sound is reserved for budget alerts
     * (docs/04), which arrive in phase 4.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            MONITORING_CHANNEL_ID,
            context.getString(R.string.notification_channel_monitoring),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_monitoring_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun monitoringNotification(context: Context): Notification =
        NotificationCompat.Builder(context, MONITORING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_monitor)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_monitoring_starting))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
}
