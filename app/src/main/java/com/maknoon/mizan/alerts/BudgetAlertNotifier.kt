package com.maknoon.mizan.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.maknoon.mizan.MainActivity
import com.maknoon.mizan.R
import com.maknoon.mizan.core.domain.budget.BudgetAlert
import com.maknoon.mizan.core.domain.budget.BudgetLevel
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.core.domain.format.isolateLtr

/**
 * Shows budget alerts on their own high-importance channel (docs/04).
 *
 * The monitoring notification stays low and silent because it refreshes every second; this one
 * must make a sound, so it gets a separate channel. It is ongoing and not auto-cancelled: the
 * only way to remove it is the «حسناً» action, which acknowledges the rows behind it.
 */
class BudgetAlertNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Posts [alert] and returns whether it was actually shown. A denied notification permission
     * yields false so the caller does not record the threshold and can retry later.
     */
    @SuppressLint("MissingPermission")
    fun post(alert: BudgetAlert): Boolean {
        ensureChannel()
        if (!canPost()) return false

        val id = notificationId(alert)
        manager.notify(id, buildNotification(alert, id))
        return true
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.budget_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.budget_channel_description)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun buildNotification(alert: BudgetAlert, id: Int) = NotificationCompat.Builder(
        context,
        CHANNEL_ID,
    )
        .setSmallIcon(R.drawable.ic_notification_monitor)
        .setContentTitle(title(alert))
        .setContentText(
            context.getString(
                R.string.budget_alert_body,
                isolateLtr(ByteFormatter.data(alert.usedBytes)),
                isolateLtr(ByteFormatter.data(alert.limitBytes)),
            ),
        )
        .setSubText(periodLabel(alert.period))
        .setContentIntent(openIntent(id))
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setOngoing(true)
        .setAutoCancel(false)
        .addAction(0, context.getString(R.string.budget_alert_action_done), doneIntent(alert, id))
        .build()

    private fun title(alert: BudgetAlert): String = context.getString(
        when (alert.level) {
            BudgetLevel.WARNING -> R.string.budget_alert_title_warning
            BudgetLevel.EXCEEDED -> R.string.budget_alert_title_exceeded
        },
        targetLabel(alert),
    )

    private fun periodLabel(period: BudgetPeriod): String = context.getString(
        when (period) {
            BudgetPeriod.DAILY -> R.string.budget_alert_period_daily
            BudgetPeriod.MONTHLY -> R.string.budget_alert_period_monthly
        },
    )

    private fun targetLabel(alert: BudgetAlert): String = when (alert.scope) {
        BudgetScope.DEVICE -> context.getString(R.string.budget_alert_target_device)
        BudgetScope.APP -> appLabel(alert.targetId)
    }

    private fun appLabel(packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString().ifBlank { packageName }
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun openIntent(id: Int): PendingIntent = PendingIntent.getActivity(
        context,
        id + OPEN_REQUEST_OFFSET,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun doneIntent(alert: BudgetAlert, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, BudgetAlertActionReceiver::class.java)
            .setAction(BudgetAlertActionReceiver.ACTION_ACKNOWLEDGE)
            .putExtra(BudgetAlertActionReceiver.EXTRA_SCOPE, alert.scope.name)
            .putExtra(BudgetAlertActionReceiver.EXTRA_TARGET_ID, alert.targetId)
            .putExtra(BudgetAlertActionReceiver.EXTRA_PERIOD, alert.period.name)
            .putExtra(BudgetAlertActionReceiver.EXTRA_PERIOD_START, alert.periodStartEpochDay)
            .putExtra(BudgetAlertActionReceiver.EXTRA_NOTIFICATION_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * One id per budget and cycle, so the warning is replaced by the exceed notice instead of
     * stacking, while every other budget keeps its own notification.
     */
    private fun notificationId(alert: BudgetAlert): Int {
        val key = "${alert.scope}|${alert.targetId}|${alert.period}|${alert.periodStartEpochDay}"
        return ID_BASE + (key.hashCode() and 0x7FFFFFFF) % ID_RANGE
    }

    private companion object {
        const val CHANNEL_ID = "budget_alerts"
        const val ID_BASE = 2000
        const val ID_RANGE = 100_000
        const val OPEN_REQUEST_OFFSET = 100_000
    }
}
