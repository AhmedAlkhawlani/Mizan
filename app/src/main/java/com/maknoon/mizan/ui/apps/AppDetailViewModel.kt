package com.maknoon.mizan.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maknoon.mizan.core.data.db.AppDailyNetwork
import com.maknoon.mizan.core.data.db.AppDailyRow
import com.maknoon.mizan.core.data.usage.AppUsageQueries
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal const val APP_DETAIL_DAYS = 7L

/** One column of the per-app chart, with the height normalised to the heaviest day. */
data class AppDayBarUi(
    val epochDay: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val fraction: Float,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/** Bytes by network for the window shown. Networks other than mobile and Wi-Fi are ignored. */
data class AppNetworkSplitUi(
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L,
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
) {
    val mobileBytes: Long get() = mobileRxBytes + mobileTxBytes
    val wifiBytes: Long get() = wifiRxBytes + wifiTxBytes
    val totalBytes: Long get() = mobileBytes + wifiBytes
}

data class AppDetailUiState(
    val packageName: String = "",
    val label: String = "",
    val days: List<AppDayBarUi> = emptyList(),
    val split: AppNetworkSplitUi = AppNetworkSplitUi(),
    val totalBytes: Long = 0L,
    val lastSyncedAtMillis: Long? = null,
) {
    val hasData: Boolean get() = totalBytes > 0L
}

/**
 * One app's stored days, oldest to newest.
 *
 * The window is fixed at seven days ending today, so a day the sync did not cover is an empty
 * column rather than a shifted axis. The split covers the same window as the chart.
 */
class AppDetailViewModel(
    application: Application,
    packageName: String,
) : AndroidViewModel(application) {

    private val queries = AppUsageQueries.create(application)
    private val zone: ZoneId = ZoneId.systemDefault()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AppDetailUiState> = localTodayFlow(zone)
        .flatMapLatest { today ->
            val firstDay = today - APP_DETAIL_DAYS + 1
            queries.observePackageDaily(packageName, firstDay, today)
                .map { rows -> appDetailUiState(packageName, rows, today) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppDetailUiState(packageName = packageName, label = packageName),
        )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(application: Application, packageName: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AppDetailViewModel(application, packageName) }
            }
    }
}

/**
 * Builds the detail state from the stored rows. Kept pure and clock-free so the seven-day
 * window, the zero fill and the network split are unit tested.
 */
internal fun appDetailUiState(
    packageName: String,
    rows: List<AppDailyRow>,
    todayEpochDay: Long,
    daysShown: Long = APP_DETAIL_DAYS,
): AppDetailUiState {
    val window = daysShown.coerceAtLeast(1L)
    val firstDay = todayEpochDay - (window - 1)
    val inWindow = rows.filter { it.epochDay in firstDay..todayEpochDay }

    val byDay = inWindow
        .groupBy { it.epochDay }
        .mapValues { (_, dayRows) ->
            dayRows.sumOf { it.rxBytes } to dayRows.sumOf { it.txBytes }
        }
    val days = (firstDay..todayEpochDay).map { day ->
        val (rxBytes, txBytes) = byDay[day] ?: (0L to 0L)
        AppDayBarUi(epochDay = day, rxBytes = rxBytes, txBytes = txBytes, fraction = 0f)
    }
    val peak = days.maxOfOrNull { it.totalBytes } ?: 0L

    return AppDetailUiState(
        packageName = packageName,
        // A package keeps one label, so the newest row names the app; the package name is the
        // fallback when the label was unavailable during the sync.
        label = inWindow.maxByOrNull { it.epochDay }?.label ?: packageName,
        days = days.map { day ->
            day.copy(
                fraction = if (peak > 0L) (day.totalBytes.toDouble() / peak).toFloat().coerceIn(0f, 1f) else 0f,
            )
        },
        split = networkSplit(inWindow),
        totalBytes = days.sumOf { it.totalBytes },
        lastSyncedAtMillis = inWindow.mapNotNull { it.lastSyncedAt }.maxOrNull(),
    )
}

private fun networkSplit(rows: List<AppDailyRow>): AppNetworkSplitUi {
    var mobileRx = 0L
    var mobileTx = 0L
    var wifiRx = 0L
    var wifiTx = 0L
    rows.forEach { row ->
        when (row.networkType) {
            AppDailyNetwork.MOBILE -> {
                mobileRx += row.rxBytes
                mobileTx += row.txBytes
            }
            AppDailyNetwork.WIFI -> {
                wifiRx += row.rxBytes
                wifiTx += row.txBytes
            }
        }
    }
    return AppNetworkSplitUi(mobileRx, mobileTx, wifiRx, wifiTx)
}
