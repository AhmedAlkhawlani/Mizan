package com.maknoon.mizan.alerts

import android.content.Context
import com.maknoon.mizan.core.data.alert.BudgetAlertCoordinator

/**
 * The one entry point both evaluators call: the monitoring service every minute and the sync
 * worker after refreshing per-app usage.
 *
 * A threshold is recorded only after its notification was posted, so a denied permission or a
 * temporarily disabled channel delays the alert instead of consuming it.
 */
object BudgetAlerts {

    suspend fun check(context: Context) {
        val coordinator = BudgetAlertCoordinator.create(context)
        val alerts = coordinator.pendingAlerts()
        if (alerts.isEmpty()) return

        val notifier = BudgetAlertNotifier(context)
        alerts.forEach { alert ->
            if (notifier.post(alert)) coordinator.record(alert)
        }
    }
}
