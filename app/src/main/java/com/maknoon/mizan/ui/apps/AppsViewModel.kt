package com.maknoon.mizan.ui.apps

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maknoon.mizan.core.data.db.AppUsageTotal
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.data.usage.AppUsageQueries
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.app.Application

/** The ranges the Apps screen offers (docs/04). */
enum class AppsPeriod { TODAY, WEEK, CYCLE }

data class AppUsageRowUi(
    val packageName: String,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    val bytes: Long,
    /** Share of the listed apps' total, for the bar; the percentage is shown as text too. */
    val share: Float,
)

data class AppsUiState(
    val period: AppsPeriod = AppsPeriod.WEEK,
    val rows: List<AppUsageRowUi> = emptyList(),
    val totalBytes: Long = 0L,
    val lastSyncedAtMillis: Long? = null,
)

/**
 * Feeds the Apps screen from Room.
 *
 * The window is recomputed when the local date changes, like the History screen, so a list
 * that stays open overnight switches to the new day without a restart. The cycle range follows
 * the operator's configured start day.
 */
class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val queries = AppUsageQueries.create(application)
    private val settings = MizanSettings(application)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val period = MutableStateFlow(AppsPeriod.WEEK)

    private val todayFlow: Flow<Long> = localTodayFlow(zone)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AppsUiState> = combine(
        todayFlow,
        period,
        settings.cycleStartDay,
    ) { today, selectedPeriod, cycleStartDay ->
        Triple(today, selectedPeriod, cycleStartDay)
    }
        .flatMapLatest { (today, selectedPeriod, cycleStartDay) ->
            queries.observeTotals(fromEpochDay(today, selectedPeriod, cycleStartDay), today)
                .map { totals -> appsUiState(totals, selectedPeriod) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppsUiState(),
        )

    fun setPeriod(selected: AppsPeriod) {
        period.value = selected
    }

    private fun fromEpochDay(today: Long, selected: AppsPeriod, cycleStartDay: Int): Long =
        when (selected) {
            AppsPeriod.TODAY -> today
            AppsPeriod.WEEK -> today - WEEK_DAYS + 1
            AppsPeriod.CYCLE -> BillingCycleCalculator.cycleFor(today, cycleStartDay).startEpochDay
        }

    private companion object {
        const val WEEK_DAYS = 7L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Emits today's local epoch day, then waits for the next midnight and emits again, so a screen
 * left open overnight moves to the new day instead of showing yesterday's window.
 */
internal fun localTodayFlow(zone: ZoneId): Flow<Long> = flow {
    while (true) {
        val now = ZonedDateTime.now(zone)
        emit(now.toLocalDate().toEpochDay())
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        val waitMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L)
        delay(waitMillis)
    }
}.distinctUntilChanged()

/**
 * Turns stored totals into rows with each app's share of the listed total.
 *
 * Rows are ordered heaviest first here as well as in the query, so the order is part of the
 * tested contract rather than a SQL detail. A zero or negative total cannot produce a division.
 */
fun appsUiState(totals: List<AppUsageTotal>, period: AppsPeriod): AppsUiState {
    val measured = totals
        .mapNotNull { total ->
            val bytes = total.rxBytes + total.txBytes
            if (bytes <= 0L) return@mapNotNull null
            AppUsageRowUi(
                packageName = total.packageName,
                label = total.label ?: total.packageName,
                rxBytes = total.rxBytes,
                txBytes = total.txBytes,
                bytes = bytes,
                share = 0f,
            )
        }
        .sortedWith(compareByDescending<AppUsageRowUi> { it.bytes }.thenBy { it.label })

    val total = measured.sumOf { it.bytes }
    val rows = measured.map { row ->
        row.copy(share = if (total > 0L) row.bytes.toFloat() / total else 0f)
    }
    return AppsUiState(
        period = period,
        rows = rows,
        totalBytes = total,
        lastSyncedAtMillis = totals.mapNotNull { it.lastSyncedAt }.maxOrNull(),
    )
}
