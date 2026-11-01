package com.maknoon.mizan.core.data.alert

import android.content.Context
import com.maknoon.mizan.core.data.db.AlertEventDao
import com.maknoon.mizan.core.data.db.AppUsageDao
import com.maknoon.mizan.core.data.db.BudgetDao
import com.maknoon.mizan.core.data.db.DeviceUsageDao
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.domain.budget.BudgetAlert
import com.maknoon.mizan.core.domain.budget.BudgetAlertEngine
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetRule
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.budget.BudgetUsage
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Feeds live usage to [BudgetAlertEngine] and stores the thresholds it reports.
 *
 * Evaluation is separate from delivery: [pendingAlerts] only reads, and the caller records an
 * alert after the notification actually reached the user. A denied notification permission
 * therefore delays the alert instead of losing it forever.
 *
 * Today and the cycle start are computed from local dates, the same unit the usage rows use,
 * so a daily cap resets at midnight and a monthly cap at the operator's billing day without
 * any scheduled reset job.
 */
class BudgetAlertCoordinator(
    private val deviceDao: DeviceUsageDao,
    private val appDao: AppUsageDao,
    private val budgetDao: BudgetDao,
    private val alertEventDao: AlertEventDao,
    private val settings: MizanSettings,
    private val engine: BudgetAlertEngine = BudgetAlertEngine(),
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun pendingAlerts(): List<BudgetAlert> {
        val budgets = budgetDao.enabledBudgets()
        if (budgets.isEmpty()) return emptyList()

        val rules = budgets.map { stored ->
            BudgetRule(
                scope = stored.scope,
                targetId = stored.targetId,
                period = stored.period,
                limitBytes = stored.limitBytes,
            )
        }
        val today = Instant.ofEpochMilli(clockMillis()).atZone(zone).toLocalDate().toEpochDay()
        val cycleStart = BillingCycleCalculator
            .cycleFor(today, settings.cycleStartDay.first())
            .startEpochDay

        val usage = BudgetUsage(
            deviceDailyBytes = deviceDao.totalBytes(today, today),
            deviceCycleBytes = deviceDao.totalBytes(cycleStart, today),
            appDailyBytes = appDao.totalsByPackage(today, today)
                .associate { it.packageName to it.bytes },
            appCycleBytes = appDao.totalsByPackage(cycleStart, today)
                .associate { it.packageName to it.bytes },
        )

        return engine.evaluate(
            rules = rules,
            usage = usage,
            todayEpochDay = today,
            cycleStartEpochDay = cycleStart,
            notified = BudgetAlertEvents.ledger(alertEventDao.budgetLedger()),
        )
    }

    /** Persists every threshold in the alert; duplicates from a racing evaluator are ignored. */
    suspend fun record(alert: BudgetAlert) {
        alertEventDao.insertAll(BudgetAlertEvents.events(alert, clockMillis()))
    }

    suspend fun acknowledge(
        scope: BudgetScope,
        targetId: String,
        period: BudgetPeriod,
        periodStartEpochDay: Long,
    ) {
        alertEventDao.acknowledge(scope, targetId, period, periodStartEpochDay)
    }

    companion object {
        fun create(context: Context): BudgetAlertCoordinator {
            val database = MizanDatabaseProvider.get(context)
            return BudgetAlertCoordinator(
                deviceDao = database.deviceUsageDao(),
                appDao = database.appUsageDao(),
                budgetDao = database.budgetDao(),
                alertEventDao = database.alertEventDao(),
                settings = MizanSettings(context),
            )
        }
    }
}
