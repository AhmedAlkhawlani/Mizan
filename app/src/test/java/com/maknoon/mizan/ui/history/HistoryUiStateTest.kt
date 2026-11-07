package com.maknoon.mizan.ui.history

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceHourlyEntity
import java.time.LocalDate
import org.junit.Test

class HistoryUiStateTest {

    private val today = LocalDate.of(2026, 3, 20).toEpochDay()

    @Test
    fun `an empty database has no content and unknown totals`() {
        val state = historyUiState(emptyList(), emptyList(), today)

        assertThat(state.hasData).isFalse()
        assertThat(state.days).hasSize(HistoryUiState.HISTORY_DAYS)
        assertThat(state.hours).hasSize(24)
        assertThat(HistoryUiState().weekTotalLabel).isEqualTo(HistoryUiState.UNKNOWN)
        assertThat(HistoryUiState().todayTotalLabel).isEqualTo(HistoryUiState.UNKNOWN)
    }

    @Test
    fun `the seven columns end today and missing days are zero`() {
        val state = historyUiState(
            daily = listOf(
                daily(today - 6, rx = 1_000),
                daily(today - 2, rx = 500),
            ),
            hourly = emptyList(),
            todayEpochDay = today,
        )

        assertThat(state.days.map { it.epochDay })
            .containsExactlyElementsIn((today - 6..today).toList())
            .inOrder()
        assertThat(state.days[1].totalBytes).isEqualTo(0)
        assertThat(state.days.last().totalBytes).isEqualTo(0)
        assertThat(state.todayEpochDay).isEqualTo(today)
    }

    @Test
    fun `bar heights are normalised to the tallest day`() {
        val state = historyUiState(
            daily = listOf(
                daily(today, rx = 3_000),
                daily(today - 1, rx = 1_000, tx = 1_000),
            ),
            hourly = emptyList(),
            todayEpochDay = today,
        )

        assertThat(state.days.last().fraction).isEqualTo(1f)
        assertThat(state.days[state.days.lastIndex - 1].fraction).isWithin(0.001f).of(2f / 3f)
        assertThat(state.days[0].fraction).isEqualTo(0f)
    }

    @Test
    fun `the peak label is the tallest day, and absent when nothing was measured`() {
        val state = historyUiState(
            daily = listOf(
                daily(today, rx = 3_000),
                daily(today - 1, rx = 1_000, tx = 1_000),
            ),
            hourly = emptyList(),
            todayEpochDay = today,
        )
        val empty = historyUiState(emptyList(), emptyList(), today)

        assertThat(state.weekPeakLabel).isEqualTo("3 KB")
        // Null, not "0 B": a chart with no bars has no scale to state.
        assertThat(empty.weekPeakLabel).isNull()
    }

    @Test
    fun `the week total covers only the days on screen`() {
        val state = historyUiState(
            daily = listOf(
                daily(today, rx = 1_000, tx = 500),
                daily(today - 6, rx = 2_000),
                daily(today - 10, rx = 5_000_000_000),
            ),
            hourly = emptyList(),
            todayEpochDay = today,
        )

        assertThat(state.weekTotalLabel).isEqualTo("3.5 KB")
        assertThat(state.todayTotalLabel).isEqualTo("1.5 KB")
    }

    @Test
    fun `partial days are flagged`() {
        val state = historyUiState(
            daily = listOf(
                daily(today, rx = 1_000, partial = true),
                daily(today - 1, rx = 1_000),
            ),
            hourly = emptyList(),
            todayEpochDay = today,
        )

        assertThat(state.anyPartial).isTrue()
        assertThat(state.days.last().partial).isTrue()
        assertThat(state.days[state.days.lastIndex - 1].partial).isFalse()
    }

    @Test
    fun `the hour chart fills all twenty-four hours`() {
        val state = historyUiState(
            daily = listOf(daily(today, rx = 1_000)),
            hourly = listOf(
                hourly(today, hour = 9, rx = 1_000),
                hourly(today, hour = 0, rx = 0, tx = 500),
            ),
            todayEpochDay = today,
        )

        assertThat(state.hours.map { it.hour }).isEqualTo((0 until 24).toList())
        assertThat(state.hours[9].fraction).isEqualTo(1f)
        assertThat(state.hours[0].fraction).isWithin(0.001f).of(0.5f)
        assertThat(state.hours[1].totalBytes).isEqualTo(0)
    }

    @Test
    fun `hours from another day are ignored`() {
        val state = historyUiState(
            daily = listOf(daily(today, rx = 1_000)),
            hourly = listOf(hourly(today - 1, hour = 9, rx = 9_000)),
            todayEpochDay = today,
        )

        assertThat(state.hours[9].totalBytes).isEqualTo(0)
        assertThat(state.hasData).isTrue()
    }

    @Test
    fun `observed hours alone count as data`() {
        val state = historyUiState(
            daily = emptyList(),
            hourly = listOf(hourly(today, hour = 12, rx = 2_048)),
            todayEpochDay = today,
        )

        assertThat(state.hasData).isTrue()
        assertThat(state.hours[12].fraction).isEqualTo(1f)
        assertThat(state.weekTotalLabel).isEqualTo("0 B")
    }

    @Test
    fun `the cycle starts on the configured day and ends the day before the next one`() {
        val state = historyUiState(
            daily = emptyList(),
            hourly = emptyList(),
            todayEpochDay = today,
            cycleStartDay = 15,
        )

        assertThat(state.cycleStartDay).isEqualTo(15)
        assertThat(state.cycleStartEpochDay).isEqualTo(LocalDate.of(2026, 3, 15).toEpochDay())
        assertThat(state.cycleEndEpochDay).isEqualTo(LocalDate.of(2026, 4, 14).toEpochDay())
    }

    @Test
    fun `the cycle total reaches back before the seven-day window`() {
        val state = historyUiState(
            daily = listOf(
                daily(LocalDate.of(2026, 3, 2).toEpochDay(), rx = 4_000),
                daily(LocalDate.of(2026, 3, 19).toEpochDay(), rx = 1_000),
            ),
            hourly = emptyList(),
            todayEpochDay = today,
        )

        assertThat(state.weekTotalLabel).isEqualTo("1 KB")
        assertThat(state.cycleTotalLabel).isEqualTo("5 KB")
    }

    @Test
    fun `a day from the previous cycle is not part of the total`() {
        val state = historyUiState(
            daily = listOf(
                daily(LocalDate.of(2026, 3, 14).toEpochDay(), rx = 9_000),
                daily(LocalDate.of(2026, 3, 16).toEpochDay(), rx = 1_000),
            ),
            hourly = emptyList(),
            todayEpochDay = today,
            cycleStartDay = 15,
        )

        assertThat(state.cycleTotalLabel).isEqualTo("1 KB")
    }

    @Test
    fun `a cycle with an unobserved elapsed day is partial`() {
        val state = historyUiState(
            daily = listOf(daily(today, rx = 1_000)),
            hourly = emptyList(),
            todayEpochDay = today,
            cycleStartDay = 18,
        )

        assertThat(state.cyclePartial).isTrue()
    }

    @Test
    fun `a cycle is complete once every elapsed day has a row`() {
        val state = historyUiState(
            daily = (today - 2..today).map { daily(it, rx = 0) },
            hourly = emptyList(),
            todayEpochDay = today,
            cycleStartDay = 18,
        )

        // Days after today belong to the cycle but are not overdue yet.
        assertThat(state.cyclePartial).isFalse()
    }

    @Test
    fun `a partial day inside the cycle makes the cycle partial`() {
        val state = historyUiState(
            daily = (today - 2..today).map { daily(it, rx = 0, partial = it == today) },
            hourly = emptyList(),
            todayEpochDay = today,
            cycleStartDay = 18,
        )

        assertThat(state.cyclePartial).isTrue()
    }

    @Test
    fun `a start day outside the allowed range is clamped`() {
        val state = historyUiState(
            daily = emptyList(),
            hourly = emptyList(),
            todayEpochDay = today,
            cycleStartDay = 40,
        )

        assertThat(state.cycleStartDay).isEqualTo(28)
        assertThat(state.cycleStartEpochDay).isEqualTo(LocalDate.of(2026, 2, 28).toEpochDay())
    }

    @Test
    fun `traffic outside the chart window still counts as data`() {
        val state = historyUiState(
            daily = listOf(daily(LocalDate.of(2026, 3, 2).toEpochDay(), rx = 4_000)),
            hourly = emptyList(),
            todayEpochDay = today,
        )

        assertThat(state.hasData).isTrue()
        assertThat(state.days.all { it.totalBytes == 0L }).isTrue()
        assertThat(state.cycleTotalLabel).isEqualTo("4 KB")
    }

    private fun daily(
        day: Long,
        rx: Long,
        tx: Long = 0,
        partial: Boolean = false,
    ) = DeviceDailyEntity(
        epochDay = day,
        rxBytes = rx,
        txBytes = tx,
        partial = partial,
        updatedAtMillis = 0,
    )

    private fun hourly(day: Long, hour: Int, rx: Long, tx: Long = 0) = DeviceHourlyEntity(
        epochDay = day,
        hour = hour,
        rxBytes = rx,
        txBytes = tx,
    )
}
