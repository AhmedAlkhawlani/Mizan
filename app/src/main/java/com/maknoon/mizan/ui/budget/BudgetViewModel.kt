package com.maknoon.mizan.ui.budget

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.budget.DEVICE_TARGET_ID
import com.maknoon.mizan.widget.BudgetWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Feeds the Budget screen from Room and the settings store.
 *
 * The text fields hold the operator's in-flight edits, so Room stays authoritative until a
 * keystroke produces a valid limit; then it is persisted immediately. The two text flows stay
 * null until the first edit, which is how the stored value appears on open.
 */
class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MizanDatabaseProvider.budgetDao(application)
    private val settings = MizanSettings(application)

    private val dailyText = MutableStateFlow<String?>(null)
    private val monthlyText = MutableStateFlow<String?>(null)

    // Read-then-upsert is not atomic, and two quick keystrokes could both see "no row" and
    // insert duplicates, so every write goes through one lock.
    private val persistMutex = Mutex()

    val uiState: StateFlow<BudgetUiState> = combine(
        dao.observeDevice(),
        settings.cycleStartDay,
        dailyText,
        monthlyText,
    ) { budgets, cycleStartDay, daily, monthly ->
        budgetUiState(budgets, cycleStartDay, daily, monthly)
    }.stateIn(
        scope = viewModelScope,
        // Keep the last value briefly so a rotation does not flash an empty screen.
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BudgetUiState(),
    )

    fun onDailyLimitChange(text: String) {
        dailyText.value = text
        viewModelScope.launch { persistLimit(BudgetPeriod.DAILY, text) }
    }

    fun onMonthlyLimitChange(text: String) {
        monthlyText.value = text
        viewModelScope.launch { persistLimit(BudgetPeriod.MONTHLY, text) }
    }

    fun onDailyEnabledChange(enabled: Boolean) {
        viewModelScope.launch { setEnabled(BudgetPeriod.DAILY, enabled) }
    }

    fun onMonthlyEnabledChange(enabled: Boolean) {
        viewModelScope.launch { setEnabled(BudgetPeriod.MONTHLY, enabled) }
    }

    fun setCycleStartDay(day: Int) {
        viewModelScope.launch { settings.setCycleStartDay(day) }
    }

    /** An unparsable keystroke is ignored: the stored limit keeps its last valid value. */
    private suspend fun persistLimit(period: BudgetPeriod, text: String) {
        val bytes = parseLimitBytes(text) ?: return
        persistMutex.withLock {
            val existing = dao.deviceBudget(period)
            dao.upsert(
                existing?.copy(limitBytes = bytes)
                    ?: newBudget(period, limitBytes = bytes, enabled = true),
            )
        }
        refreshBudgetWidget()
    }

    private suspend fun setEnabled(period: BudgetPeriod, enabled: Boolean) {
        persistMutex.withLock {
            val existing = dao.deviceBudget(period)
            dao.upsert(
                existing?.copy(enabled = enabled)
                    ?: newBudget(period, limitBytes = 0L, enabled = enabled),
            )
        }
        refreshBudgetWidget()
    }

    /** The ring shows this cap, so it follows each committed edit instead of waiting a minute. */
    private suspend fun refreshBudgetWidget() {
        try {
            BudgetWidget.refresh(getApplication())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "budget widget refresh failed", e)
        }
    }

    private fun newBudget(period: BudgetPeriod, limitBytes: Long, enabled: Boolean) = BudgetEntity(
        scope = BudgetScope.DEVICE,
        targetId = DEVICE_TARGET_ID,
        period = period,
        limitBytes = limitBytes,
        enabled = enabled,
        createdAt = System.currentTimeMillis(),
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val TAG = "MizanBudget"

        fun factory(application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { BudgetViewModel(application) }
            }
    }
}
