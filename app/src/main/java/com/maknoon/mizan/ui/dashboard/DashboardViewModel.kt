package com.maknoon.mizan.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maknoon.mizan.core.data.db.AppUsageTotal
import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import com.maknoon.mizan.network.ResolvedNetworkHolder
import com.maknoon.mizan.saver.SaverStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the dashboard: the live monitor state, the resolved connection for the metered chip,
 * and the device's daily cap so the glowing budget bar compares today against a stored limit
 * only — never against an invented one.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val budgetDao = MizanDatabaseProvider.budgetDao(application)

    private val appUsageDao = MizanDatabaseProvider.appUsageDao(application)

    private val settings = MizanSettings(application)

    /**
     * Today's per-app totals. The day comes from the accountant's own reading rather than the
     * wall clock, so the card flips at the same moment the "today" figure does instead of
     * showing yesterday's apps under today's total.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayApps = MonitoringStateHolder.state
        .map { it.usage?.epochDay }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            if (day == null) flowOf(emptyList()) else appUsageDao.observeTotals(day, day)
        }

    private val budgetsAndApps = combine(
        budgetDao.observeDevice(),
        todayApps,
    ) { budgets, apps -> budgets to apps }

    val uiState: StateFlow<DashboardUiState> = combine(
        MonitoringStateHolder.state,
        ResolvedNetworkHolder.state,
        budgetsAndApps,
        SaverStateHolder.state,
        settings.dataSaverEnabled,
    ) { monitoring, network, budgetAndApps, guard, saverEnabled ->
        val (budgets, apps) = budgetAndApps
        val totalBytes = monitoring.usage?.totalBytes ?: 0L
        val daily = budgets.firstOrNull {
            it.period == BudgetPeriod.DAILY && it.enabled && it.limitBytes > 0L
        }
        monitoring.toDashboardUiState().copy(
            networkMetered = network?.metered,
            // Only a tunnel the service reports as established counts as protection; the stored
            // switch alone would claim a guard that may have failed to start.
            guardProtected = if (!saverEnabled) null else guard.active,
            dailyBudgetLimit = daily?.let { ByteFormatter.data(it.limitBytes) },
            dailyBudgetFraction = daily?.let { budgetFraction(totalBytes, it) } ?: 0f,
            topApps = apps.toTopRows(),
        )
    }.stateIn(
        scope = viewModelScope,
        // Keep the last value briefly so a rotation does not flash an empty dashboard.
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MonitoringStateHolder.state.value.toDashboardUiState(),
    )

    init {
        ResolvedNetworkHolder.start(application)
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DashboardViewModel(application) }
            }
    }
}

/** Over-cap reads as a full bar; the label carries the true figures. */
private fun budgetFraction(totalBytes: Long, budget: BudgetEntity): Float =
    (totalBytes.toFloat() / budget.limitBytes.toFloat()).coerceIn(0f, 1f)

/** How many apps the dashboard shows; three fits without scrolling on the smallest phone. */
private const val TOP_APPS_LIMIT = 3

/**
 * The heaviest apps of the day, with each bar scaled to the heaviest so the rows read as a
 * comparison. Apps with no measured bytes are dropped rather than drawn as empty rows, and the
 * label falls back to the package name when the sync never recorded one.
 */
private fun List<AppUsageTotal>.toTopRows(): List<TopAppRow> {
    val ranked = asSequence()
        .map { it to (it.rxBytes + it.txBytes) }
        .filter { (_, bytes) -> bytes > 0L }
        .sortedByDescending { (_, bytes) -> bytes }
        .take(TOP_APPS_LIMIT)
        .toList()
    val heaviest = ranked.firstOrNull()?.second ?: return emptyList()
    return ranked.map { (row, bytes) ->
        TopAppRow(
            label = row.label?.takeIf { it.isNotBlank() } ?: row.packageName,
            bytes = ByteFormatter.data(bytes),
            fraction = (bytes.toFloat() / heaviest.toFloat()).coerceIn(0f, 1f),
        )
    }
}
