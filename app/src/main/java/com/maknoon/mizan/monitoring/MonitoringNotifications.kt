package com.maknoon.mizan.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.maknoon.mizan.R
import com.maknoon.mizan.app.AppLanguage
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.core.domain.format.isolateLtr

/** Builds the ongoing notification that a foreground monitoring service is required to show. */
object MonitoringNotifications {

    const val MONITORING_NOTIFICATION_ID = 1001

    private const val MONITORING_CHANNEL_ID = "monitoring"

    /**
     * Low importance on purpose: this notification refreshes every second, so it must stay
     * silent and never buzz. High importance with sound is reserved for budget alerts
     * (docs/04), which arrive in phase 4.
     */
    fun createChannel(base: Context, manager: NotificationManager) {
        val context = AppLanguage.localized(base)
        val channel = NotificationChannel(
            MONITORING_CHANNEL_ID,
            context.getString(R.string.notification_channel_monitoring),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_monitoring_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * The live figure with a pause action, as drawn in docs/04.
     *
     * Repeat content updates reuse the same notification id and only alert once, so a
     * once-a-second refresh can never buzz the user.
     */
    fun monitoringNotification(
        base: Context,
        state: MonitoringState,
        openIntent: PendingIntent,
        stopIntent: PendingIntent,
        statusIcon: IconCompat? = null,
    ): Notification {
        val context = AppLanguage.localized(base)
        val usage = state.usage

        val speedLine = if (usage == null) {
            context.getString(R.string.notification_speed_unknown)
        } else {
            context.getString(
                R.string.notification_speed_line,
                ByteFormatter.rate(usage.downBytesPerSecond),
                ByteFormatter.rate(usage.upBytesPerSecond),
            )
        }
        val todayLine = when {
            usage == null -> context.getString(R.string.notification_today_unknown)
            usage.partial -> context.getString(
                R.string.notification_today_partial,
                isolateLtr(ByteFormatter.data(usage.totalBytes)),
            )

            else -> context.getString(
                R.string.notification_today_line,
                isolateLtr(ByteFormatter.data(usage.totalBytes)),
            )
        }

        return NotificationCompat.Builder(context, MONITORING_CHANNEL_ID)
            .apply {
                // The generated bitmap is tinted by the system like any status icon; without
                // one (mode off, or no reading yet) the static monitor glyph stands in.
                if (statusIcon != null) setSmallIcon(statusIcon) else setSmallIcon(R.drawable.ic_notification_monitor)
            }
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(speedLine)
            .setSubText(todayLine)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // Action icons are not drawn from Android 7 onwards, so the icon slot stays empty
            // rather than shipping an asset nobody will ever see.
            .addAction(0, context.getString(R.string.notification_action_pause), stopIntent)
            .build()
    }
}
