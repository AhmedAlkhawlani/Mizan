package com.maknoon.mizan.core.data.widget

import android.content.Context
import com.maknoon.mizan.core.data.db.AppUsageDao
import com.maknoon.mizan.core.data.db.BudgetDao
import com.maknoon.mizan.core.data.db.DeviceUsageDao
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.domain.budget.BudgetPeriod

/** The heaviest app of the day, as the widget needs it. */
data class WidgetTopApp(
    val packageName: String,
    val label: String?,
    val bytes: Long,
)

/**
 * Everything the 2×2 widget draws that is not live speed.
 *
 * The daily device budget is the board's ring; a monthly-only cap is deliberately not mixed in,
 * because the ring's total is today's usage and comparing it with a monthly cap would mislead.
 */
data class BudgetWidgetSnapshot(
    val storedTodayBytes: Long?,
    val dailyLimitBytes: Long?,
    val topApp: WidgetTopApp?,
)

/**
 * Reads the stored figures a widget redraw needs.
 *
 * The live total is owned by the monitor and read by the widget itself; this repository supplies
 * the durable fallback for when monitoring is stopped, plus the cap and the top app.
 */
class BudgetWidgetRepository(
    private val budgetDao: BudgetDao,
    private val deviceUsageDao: DeviceUsageDao,
    private val appUsageDao: AppUsageDao,
) {

    suspend fun load(todayEpochDay: Long): BudgetWidgetSnapshot {
        val budget = budgetDao.deviceBudget(BudgetPeriod.DAILY)
            ?.takeIf { it.enabled && it.limitBytes > 0 }
        val storedToday = deviceUsageDao.daily(todayEpochDay)
            ?.let { it.rxBytes + it.txBytes }
        val top = appUsageDao.topForDay(todayEpochDay)?.let {
            WidgetTopApp(
                packageName = it.packageName,
                label = it.label,
                bytes = it.rxBytes + it.txBytes,
            )
        }
        return BudgetWidgetSnapshot(
            storedTodayBytes = storedToday,
            dailyLimitBytes = budget?.limitBytes,
            topApp = top,
        )
    }

    companion object {
        fun create(context: Context) = BudgetWidgetRepository(
            budgetDao = MizanDatabaseProvider.budgetDao(context),
            deviceUsageDao = MizanDatabaseProvider.deviceUsageDao(context),
            appUsageDao = MizanDatabaseProvider.appUsageDao(context),
        )
    }
}
