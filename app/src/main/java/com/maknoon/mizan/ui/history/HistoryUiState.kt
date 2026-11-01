package com.maknoon.mizan.ui.history

import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceHourlyEntity
import com.maknoon.mizan.core.domain.format.ByteFormatter

/** One column of the 7-day chart, with the height already normalised to the tallest day. */
data class DayBarUi(
    val epochDay: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val fraction: Float,
    val partial: Boolean,
) {
    val totalBytes: Long get() = rxBytes + txBytes
    val totalLabel: String get() = ByteFormatter.data(totalBytes)
}

/** One column of the 24-hour chart. Hours without an observed bucket are still present, at zero. */
data class HourBarUi(
    val hour: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val fraction: Float,
) {
    val totalBytes: Long get() = rxBytes + txBytes
    val totalLabel: String get() = ByteFormatter.data(totalBytes)
}

/**
 * Everything the History screen draws.
 *
 * Both charts are fixed-length: exactly [HISTORY_DAYS] day columns ending today and 24 hour
 * columns, so a gap in the data reads as an empty bar rather than quietly shifting the axis.
 */
data class HistoryUiState(
    val days: List<DayBarUi> = emptyList(),
    val hours: List<HourBarUi> = emptyList(),
    val todayEpochDay: Long = 0L,
    val weekTotalLabel: String = UNKNOWN,
    val todayTotalLabel: String = UNKNOWN,
    val anyPartial: Boolean = false,
    val hasData: Boolean = false,
) {
    companion object {
        const val UNKNOWN = "—"
        const val HISTORY_DAYS = 7
    }
}

/**
 * Builds the screen state from the stored rows.
 *
 * Kept pure and free of clocks so the date window, the normalisation and the empty state are
 * unit tested; the view model supplies today's local day.
 */
internal fun historyUiState(
    daily: List<DeviceDailyEntity>,
    hourly: List<DeviceHourlyEntity>,
    todayEpochDay: Long,
    daysShown: Int = HistoryUiState.HISTORY_DAYS,
): HistoryUiState {
    val dailyByDay = daily.associateBy { it.epochDay }
    val window = daysShown.coerceAtLeast(1)
    val firstDay = todayEpochDay - (window - 1)
    val dayRows = (firstDay..todayEpochDay).map { day ->
        val row = dailyByDay[day]
        DayBarUi(
            epochDay = day,
            rxBytes = row?.rxBytes ?: 0L,
            txBytes = row?.txBytes ?: 0L,
            fraction = 0f,
            partial = row?.partial ?: false,
        )
    }
    val dayPeak = dayRows.maxOfOrNull { it.totalBytes } ?: 0L

    val hourlyByHour = hourly
        .filter { it.epochDay == todayEpochDay }
        .associateBy { it.hour }
    val hourRows = (0 until HOURS_PER_DAY).map { hour ->
        val row = hourlyByHour[hour]
        HourBarUi(
            hour = hour,
            rxBytes = row?.rxBytes ?: 0L,
            txBytes = row?.txBytes ?: 0L,
            fraction = 0f,
        )
    }
    val hourPeak = hourRows.maxOfOrNull { it.totalBytes } ?: 0L

    return HistoryUiState(
        days = dayRows.map { it.copy(fraction = fractionOf(it.totalBytes, dayPeak)) },
        hours = hourRows.map { it.copy(fraction = fractionOf(it.totalBytes, hourPeak)) },
        todayEpochDay = todayEpochDay,
        weekTotalLabel = ByteFormatter.data(dayRows.sumOf { it.totalBytes }),
        todayTotalLabel = ByteFormatter.data(dayRows.last().totalBytes),
        anyPartial = dayRows.any { it.partial },
        hasData = dayPeak > 0L || hourPeak > 0L,
    )
}

private const val HOURS_PER_DAY = 24

private fun fractionOf(totalBytes: Long, peakBytes: Long): Float =
    if (peakBytes <= 0L) 0f else (totalBytes.toDouble() / peakBytes).toFloat().coerceIn(0f, 1f)
