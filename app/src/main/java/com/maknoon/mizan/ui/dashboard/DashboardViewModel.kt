package com.maknoon.mizan.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import com.maknoon.mizan.network.ResolvedNetworkHolder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the dashboard: the live monitor state, the resolved connection for the metered chip,
 * and the device's daily cap so the glowing budget bar compares today against a stored limit
 * only — never against an invented one.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val budgetDao = MizanDatabaseProvider.budgetDao(application)

    val uiState: StateFlow<DashboardUiState> = combine(
        MonitoringStateHolder.state,
        ResolvedNetworkHolder.state,
        budgetDao.observeDevice(),
    ) { monitoring, network, budgets ->
        val totalBytes = monitoring.usage?.totalBytes ?: 0L
        val daily = budgets.firstOrNull {
            it.period == BudgetPeriod.DAILY && it.enabled && it.limitBytes > 0L
        }
        monitoring.toDashboardUiState().copy(
            networkMetered = network?.metered,
            dailyBudgetLimit = daily?.let { ByteFormatter.data(it.limitBytes) },
            dailyBudgetFraction = daily?.let { budgetFraction(totalBytes, it) } ?: 0f,
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
