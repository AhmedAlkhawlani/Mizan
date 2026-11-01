package com.maknoon.mizan.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.maknoon.mizan.core.data.alert.BudgetAlertCoordinator
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Removes a budget notification when «حسناً» is pressed and marks its rows acknowledged.
 *
 * A receiver is used for the same reason as the monitoring action: it works while the app is in
 * the background, where starting a service would be refused. The notification is cancelled
 * immediately; the database write finishes on a background scope through [goAsync].
 */
class BudgetAlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return

        val scope = intent.getStringExtra(EXTRA_SCOPE)
            ?.let { runCatching { BudgetScope.valueOf(it) }.getOrNull() }
            ?: return
        val targetId = intent.getStringExtra(EXTRA_TARGET_ID) ?: return
        val period = intent.getStringExtra(EXTRA_PERIOD)
            ?.let { runCatching { BudgetPeriod.valueOf(it) }.getOrNull() }
            ?: return
        val periodStart = intent.getLongExtra(EXTRA_PERIOD_START, Long.MIN_VALUE)
        if (periodStart == Long.MIN_VALUE) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BudgetAlertCoordinator.create(context).acknowledge(
                    scope = scope,
                    targetId = targetId,
                    period = period,
                    periodStartEpochDay = periodStart,
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.maknoon.mizan.action.ACKNOWLEDGE_BUDGET_ALERT"
        const val EXTRA_SCOPE = "com.maknoon.mizan.extra.SCOPE"
        const val EXTRA_TARGET_ID = "com.maknoon.mizan.extra.TARGET_ID"
        const val EXTRA_PERIOD = "com.maknoon.mizan.extra.PERIOD"
        const val EXTRA_PERIOD_START = "com.maknoon.mizan.extra.PERIOD_START"
        const val EXTRA_NOTIFICATION_ID = "com.maknoon.mizan.extra.NOTIFICATION_ID"
    }
}
