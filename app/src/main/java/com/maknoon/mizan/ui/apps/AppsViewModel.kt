package com.maknoon.mizan.ui.apps

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maknoon.mizan.core.data.db.AppUsageTotal
import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.data.usage.AppUsageQueries
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import com.maknoon.mizan.ui.budget.BudgetUnit
import com.maknoon.mizan.ui.budget.convertLimitText
import com.maknoon.mizan.ui.budget.parseLimitBytes
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Enabled caps, shown on the row so a budget is visible without opening the editor. */
    val dailyLimitBytes: Long? = null,
    val monthlyLimitBytes: Long? = null,
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
    private val budgetDao = MizanDatabaseProvider.budgetDao(application)
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
            combine(
                queries.observeTotals(fromEpochDay(today, selectedPeriod, cycleStartDay), today),
                budgetDao.observeAppBudgets(),
            ) { totals, budgets -> appsUiState(totals, selectedPeriod, budgets) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppsUiState(),
        )

    private data class EditorTarget(val packageName: String, val label: String)

    private val editorTarget = MutableStateFlow<EditorTarget?>(null)
    private val editorDailyText = MutableStateFlow<String?>(null)
    private val editorMonthlyText = MutableStateFlow<String?>(null)
    private val editorDailyUnit = MutableStateFlow<BudgetUnit?>(null)
    private val editorMonthlyUnit = MutableStateFlow<BudgetUnit?>(null)

    // Read-then-upsert is not atomic, so two quick keystrokes could both see "no row" and insert
    // duplicates; every write to the app caps goes through one lock.
    private val persistMutex = Mutex()

    /** The open per-app budget dialog, or null when it is closed. */
    val editorState: StateFlow<AppBudgetUiState?> = combine(
        editorTarget,
        budgetDao.observeAppBudgets(),
        editorDailyText,
        editorMonthlyText,
        combine(editorDailyUnit, editorMonthlyUnit) { daily, monthly -> daily to monthly },
    ) { target, budgets, dailyText, monthlyText, units ->
        target?.let {
            appBudgetUiState(
                packageName = it.packageName,
                label = it.label,
                budgets = budgets,
                dailyText = dailyText,
                monthlyText = monthlyText,
                dailyUnit = units.first,
                monthlyUnit = units.second,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
    )

    fun setPeriod(selected: AppsPeriod) {
        period.value = selected
    }

    fun openBudgetEditor(packageName: String, label: String) {
        editorDailyText.value = null
        editorMonthlyText.value = null
        editorDailyUnit.value = null
        editorMonthlyUnit.value = null
        editorTarget.value = EditorTarget(packageName, label)
    }

    fun closeBudgetEditor() {
        editorTarget.value = null
        editorDailyText.value = null
        editorMonthlyText.value = null
        editorDailyUnit.value = null
        editorMonthlyUnit.value = null
    }

    fun onEditorDailyLimitChange(text: String, unit: BudgetUnit) {
        editorDailyText.value = text
        val target = editorTarget.value ?: return
        viewModelScope.launch { persistLimit(target.packageName, BudgetPeriod.DAILY, text, unit) }
    }

    fun onEditorMonthlyLimitChange(text: String, unit: BudgetUnit) {
        editorMonthlyText.value = text
        val target = editorTarget.value ?: return
        viewModelScope.launch { persistLimit(target.packageName, BudgetPeriod.MONTHLY, text, unit) }
    }

    fun onEditorDailyUnitChange(unit: BudgetUnit) {
        changeUnit(BudgetPeriod.DAILY, unit)
    }

    fun onEditorMonthlyUnitChange(unit: BudgetUnit) {
        changeUnit(BudgetPeriod.MONTHLY, unit)
    }

    /** Same contract as the device editor: the number is rewritten, not re-read, on a unit swap. */
    private fun changeUnit(period: BudgetPeriod, unit: BudgetUnit) {
        val open = editorState.value ?: return
        val field = if (period == BudgetPeriod.DAILY) open.daily else open.monthly
        if (field.unit == unit) return
        if (period == BudgetPeriod.DAILY) editorDailyUnit.value = unit else editorMonthlyUnit.value = unit
        val converted = convertLimitText(field.text, field.unit, unit)
        if (period == BudgetPeriod.DAILY) {
            onEditorDailyLimitChange(converted, unit)
        } else {
            onEditorMonthlyLimitChange(converted, unit)
        }
    }

    fun onEditorDailyEnabledChange(enabled: Boolean) {
        val target = editorTarget.value ?: return
        viewModelScope.launch { setEnabled(target.packageName, BudgetPeriod.DAILY, enabled) }
    }

    fun onEditorMonthlyEnabledChange(enabled: Boolean) {
        val target = editorTarget.value ?: return
        viewModelScope.launch { setEnabled(target.packageName, BudgetPeriod.MONTHLY, enabled) }
    }

    private fun fromEpochDay(today: Long, selected: AppsPeriod, cycleStartDay: Int): Long =
        when (selected) {
            AppsPeriod.TODAY -> today
            AppsPeriod.WEEK -> today - WEEK_DAYS + 1
            AppsPeriod.CYCLE -> BillingCycleCalculator.cycleFor(today, cycleStartDay).startEpochDay
        }

    /** An unparsable keystroke is ignored: the stored limit keeps its last valid value. */
    private suspend fun persistLimit(
        packageName: String,
        period: BudgetPeriod,
        text: String,
        unit: BudgetUnit,
    ) {
        val bytes = parseLimitBytes(text, unit) ?: return
        persistMutex.withLock {
            val existing = budgetDao.appBudget(packageName, period)
            budgetDao.upsert(
                existing?.copy(limitBytes = bytes)
                    ?: newBudget(packageName, period, limitBytes = bytes, enabled = true),
            )
        }
    }

    private suspend fun setEnabled(packageName: String, period: BudgetPeriod, enabled: Boolean) {
        persistMutex.withLock {
            val existing = budgetDao.appBudget(packageName, period)
            budgetDao.upsert(
                existing?.copy(enabled = enabled)
                    ?: newBudget(packageName, period, limitBytes = 0L, enabled = enabled),
            )
        }
    }

    private fun newBudget(
        packageName: String,
        period: BudgetPeriod,
        limitBytes: Long,
        enabled: Boolean,
    ) = BudgetEntity(
        scope = BudgetScope.APP,
        targetId = packageName,
        period = period,
        limitBytes = limitBytes,
        enabled = enabled,
        createdAt = System.currentTimeMillis(),
    )

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
 * Turns stored totals into rows with each app's share of the listed total and its enabled caps.
 *
 * Rows are ordered heaviest first here as well as in the query, so the order is part of the
 * tested contract rather than a SQL detail. A zero or negative total cannot produce a division.
 * A disabled or zero cap is not shown, because it does not constrain the app yet.
 */
fun appsUiState(
    totals: List<AppUsageTotal>,
    period: AppsPeriod,
    budgets: List<BudgetEntity> = emptyList(),
): AppsUiState {
    val caps = budgets
        .filter { it.scope == BudgetScope.APP && it.enabled && it.limitBytes > 0L }
        .groupBy { it.targetId }

    val measured = totals
        .mapNotNull { total ->
            val bytes = total.rxBytes + total.txBytes
            if (bytes <= 0L) return@mapNotNull null
            val appCaps = caps[total.packageName].orEmpty()
            AppUsageRowUi(
                packageName = total.packageName,
                label = total.label ?: total.packageName,
                rxBytes = total.rxBytes,
                txBytes = total.txBytes,
                bytes = bytes,
                share = 0f,
                dailyLimitBytes = appCaps.limitFor(BudgetPeriod.DAILY),
                monthlyLimitBytes = appCaps.limitFor(BudgetPeriod.MONTHLY),
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

private fun List<BudgetEntity>.limitFor(period: BudgetPeriod): Long? =
    firstOrNull { it.period == period }?.limitBytes
