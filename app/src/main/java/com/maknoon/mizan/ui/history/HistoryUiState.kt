package com.maknoon.mizan.ui.history

import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceHourlyEntity
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
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
 *
 * The billing cycle can be longer than the chart window, so its total is computed from the
 * cycle range rather than from [days].
 */
data class HistoryUiState(
    val days: List<DayBarUi> = emptyList(),
    val hours: List<HourBarUi> = emptyList(),
    val todayEpochDay: Long = 0L,
    val weekTotalLabel: String = UNKNOWN,
    /** The tallest day of the window: the value the chart's bars are scaled against. */
    val weekPeakLabel: String? = null,
    val todayTotalLabel: String = UNKNOWN,
    val cycleStartDay: Int = BillingCycleCalculator.MIN_START_DAY,
    val cycleStartEpochDay: Long = 0L,
    val cycleEndEpochDay: Long = 0L,
    val cycleTotalLabel: String = UNKNOWN,
    val cyclePartial: Boolean = false,
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
    cycleStartDay: Int = BillingCycleCalculator.MIN_START_DAY,
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

    val cycle = BillingCycleCalculator.cycleFor(todayEpochDay, cycleStartDay)
    val cycleEnd = minOf(cycle.endEpochDay, todayEpochDay)
    val cycleRows = daily.filter { it.epochDay in cycle.startEpochDay..cycleEnd }
    // A day with no row was never observed: the service writes a row every few seconds while it
    // runs, so a missing day is unknown rather than a measured zero. A cycle covering such a day
    // must not claim its total is complete.
    val cyclePartial = cycleRows.size.toLong() < cycleEnd - cycle.startEpochDay + 1 ||
        cycleRows.any { it.partial }

    return HistoryUiState(
        days = dayRows.map { it.copy(fraction = fractionOf(it.totalBytes, dayPeak)) },
        hours = hourRows.map { it.copy(fraction = fractionOf(it.totalBytes, hourPeak)) },
        todayEpochDay = todayEpochDay,
        weekTotalLabel = ByteFormatter.data(dayRows.sumOf { it.totalBytes }),
        weekPeakLabel = if (dayPeak > 0L) ByteFormatter.data(dayPeak) else null,
        todayTotalLabel = ByteFormatter.data(dayRows.last().totalBytes),
        cycleStartDay = BillingCycleCalculator.normalizedStartDay(cycleStartDay),
        cycleStartEpochDay = cycle.startEpochDay,
        cycleEndEpochDay = cycle.endEpochDay,
        cycleTotalLabel = ByteFormatter.data(cycleRows.sumOf { it.rxBytes + it.txBytes }),
        cyclePartial = cyclePartial,
        anyPartial = dayRows.any { it.partial },
        hasData = dayPeak > 0L || hourPeak > 0L || cycleRows.any { it.rxBytes + it.txBytes > 0L },
    )
}

private const val HOURS_PER_DAY = 24

private fun fractionOf(totalBytes: Long, peakBytes: Long): Float =
    if (peakBytes <= 0L) 0f else (totalBytes.toDouble() / peakBytes).toFloat().coerceIn(0f, 1f)
