package com.maknoon.mizan.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.data.usage.DeviceUsageRepository
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Feeds the History screen from Room.
 *
 * The day window is recomputed when the local date changes, not only when the screen is first
 * opened: a chart that keeps yesterday at its right edge until the app restarts would be wrong
 * every night. The date flow sleeps until the next local midnight instead of polling.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceUsageRepository.create(application)
    private val settings = MizanSettings(application)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val todayFlow: Flow<Long> = flow {
        while (true) {
            val now = ZonedDateTime.now(zone)
            emit(now.toLocalDate().toEpochDay())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
            val waitMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L)
            delay(waitMillis)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        todayFlow,
        settings.cycleStartDay,
    ) { today, cycleStartDay -> today to cycleStartDay }
        .flatMapLatest { (today, cycleStartDay) ->
            // The cycle can start before the seven-day window, and its total must include those
            // days, so the query begins at whichever is older.
            val cycle = BillingCycleCalculator.cycleFor(today, cycleStartDay)
            val fromEpochDay = minOf(today - HISTORY_DAYS + 1, cycle.startEpochDay)
            combine(
                repository.observeDailyRange(fromEpochDay, today),
                repository.observeHourly(today),
            ) { daily, hourly ->
                historyUiState(daily, hourly, today, cycleStartDay, HISTORY_DAYS)
            }
        }
        .stateIn(
            scope = viewModelScope,
            // Keep the last value briefly so a rotation does not flash an empty screen.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HistoryUiState(),
        )

    /** Persists the operator's cycle start day; the flow above re-reads the cycle on change. */
    fun setCycleStartDay(day: Int) {
        viewModelScope.launch { settings.setCycleStartDay(day) }
    }

    private companion object {
        const val HISTORY_DAYS = HistoryUiState.HISTORY_DAYS
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
