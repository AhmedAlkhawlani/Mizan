package com.maknoon.mizan.core.domain.cycle

import java.time.LocalDate
import java.time.YearMonth

/**
 * The inclusive range of local days covered by one operator billing cycle.
 *
 * Both ends are epoch days of the local calendar, the same unit the database stores, so a
 * cycle can be compared with a daily row without converting time zones again.
 */
data class BillingCycle(
    val startEpochDay: Long,
    val endEpochDay: Long,
) {
    fun contains(epochDay: Long): Boolean = epochDay in startEpochDay..endEpochDay

    val dayCount: Long get() = endEpochDay - startEpochDay + 1
}

/**
 * Works out which billing cycle a local day belongs to.
 *
 * Operators bill from a day-of-month, but not every month has a 31st, so the start day is
 * capped at 28: the 28th exists in every month and the boundary never has to be shifted.
 * The cycle ends the day before the next month's start day.
 */
object BillingCycleCalculator {

    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28

    /** Clamps a user-supplied start day into the range every month can honour. */
    fun normalizedStartDay(startDay: Int): Int = startDay.coerceIn(MIN_START_DAY, MAX_START_DAY)

    fun cycleFor(epochDay: Long, startDay: Int): BillingCycle {
        val day = LocalDate.ofEpochDay(epochDay)
        val start = normalizedStartDay(startDay)
        val month = YearMonth.from(day)
        // Before the start day belongs to the cycle that opened last month.
        val anchor = if (day.isBefore(month.atDay(start))) month.minusMonths(1) else month
        val startDate = anchor.atDay(start)
        val nextStart = anchor.plusMonths(1).atDay(start)
        return BillingCycle(
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = nextStart.minusDays(1).toEpochDay(),
        )
    }
}
