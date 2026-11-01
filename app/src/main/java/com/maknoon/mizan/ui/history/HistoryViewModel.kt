package com.maknoon.mizan.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maknoon.mizan.core.data.export.UsageCsv
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.data.usage.DeviceUsageRepository
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outcome of a CSV export, so the screen can confirm it in the operator's language. */
enum class ExportResult { SAVED, FAILED }

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

    private val _exportResult = MutableSharedFlow<ExportResult>(extraBufferCapacity = 1)
    val exportResult: SharedFlow<ExportResult> = _exportResult.asSharedFlow()

    /** Persists the operator's cycle start day; the flow above re-reads the cycle on change. */
    fun setCycleStartDay(day: Int) {
        viewModelScope.launch { settings.setCycleStartDay(day) }
    }

    /**
     * Writes every stored day to the document the operator picked.
     *
     * The picker returns a URI, so the app needs no storage permission: the system grants
     * access to that one file for this write. Opening with "wt" truncates, because a document
     * the operator chose to overwrite must not keep the tail of the previous export.
     */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val csv = UsageCsv.daily(repository.allDaily())
                    val stream = getApplication<Application>().contentResolver
                        .openOutputStream(uri, "wt")
                        ?: throw IOException("No stream for $uri")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                }
                ExportResult.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExportResult.FAILED
            }
            _exportResult.emit(result)
        }
    }

    private companion object {
        const val HISTORY_DAYS = HistoryUiState.HISTORY_DAYS
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
