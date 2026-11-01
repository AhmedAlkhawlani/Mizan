package com.maknoon.mizan.ui.history

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceHourlyEntity
import org.junit.Test

class HistoryUiStateTest {

    private val today = 20_758L

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
