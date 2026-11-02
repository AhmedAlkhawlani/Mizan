package com.maknoon.mizan.saver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.maknoon.mizan.R

/** The ongoing notification for the blocking-only tunnel, with an instant stop action. */
object SaverNotifications {

    const val NOTIFICATION_ID = 1002

    private const val CHANNEL_ID = "saver"

    fun createChannel(context: Context, manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.saver_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.saver_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun startingNotification(
        context: Context,
        openIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification = build(
        context = context,
        text = context.getString(R.string.saver_notification_starting),
        openIntent = openIntent,
        stopIntent = stopIntent,
    )

    fun activeNotification(
        context: Context,
        blockedCount: Int,
        openIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification = build(
        context = context,
        text = context.resources.getQuantityString(
            R.plurals.saver_notification_active,
            blockedCount,
            blockedCount,
        ),
        openIntent = openIntent,
        stopIntent = stopIntent,
    )

    private fun build(
        context: Context,
        text: String,
        openIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_shield)
        .setContentTitle(context.getString(R.string.saver_title))
        .setContentText(text)
        .setContentIntent(openIntent)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setAutoCancel(false)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .addAction(0, context.getString(R.string.saver_notification_action_stop), stopIntent)
        .build()
}
