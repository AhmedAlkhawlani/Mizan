package com.maknoon.mizan.ui.apps

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.AppDailyNetwork
import com.maknoon.mizan.core.data.db.AppDailyRow
import org.junit.Test

class AppDetailUiStateTest {

    @Test
    fun `the window is seven days ending today with empty days at zero`() {
        val today = 20_758L
        val state = appDetailUiState(
            packageName = "com.example.app",
            rows = listOf(
                row(today - 6, AppDailyNetwork.WIFI, rx = 1_000),
                row(today, AppDailyNetwork.MOBILE, rx = 2_000),
            ),
            todayEpochDay = today,
        )

        assertThat(state.days.map { it.epochDay }).containsExactly(
            today - 6, today - 5, today - 4, today - 3, today - 2, today - 1, today,
        ).inOrder()
        assertThat(state.days.map { it.totalBytes })
            .containsExactly(1_000L, 0L, 0L, 0L, 0L, 0L, 2_000L).inOrder()
        assertThat(state.totalBytes).isEqualTo(3_000)
        assertThat(state.hasData).isTrue()
    }

    @Test
    fun `the tallest day fills the chart and the rest scale against it`() {
        val today = 20_758L
        val state = appDetailUiState(
            packageName = "com.example.app",
            rows = listOf(
                row(today - 1, AppDailyNetwork.WIFI, rx = 5_000),
                row(today, AppDailyNetwork.WIFI, rx = 1_000, tx = 1_000),
            ),
            todayEpochDay = today,
        )

        assertThat(state.days[5].fraction).isWithin(0.0001f).of(1f)
        assertThat(state.days[6].fraction).isWithin(0.0001f).of(0.4f)
    }

    @Test
    fun `mobile and wifi are split while other networks only count in the total`() {
        val today = 20_758L
        val state = appDetailUiState(
            packageName = "com.example.app",
            rows = listOf(
                row(today, AppDailyNetwork.MOBILE, rx = 100, tx = 50),
                row(today, AppDailyNetwork.WIFI, rx = 900, tx = 150),
                row(today, networkType = 2, rx = 70, tx = 30),
            ),
            todayEpochDay = today,
        )

        assertThat(state.split.mobileRxBytes).isEqualTo(100)
        assertThat(state.split.mobileTxBytes).isEqualTo(50)
        assertThat(state.split.wifiRxBytes).isEqualTo(900)
        assertThat(state.split.wifiTxBytes).isEqualTo(150)
        assertThat(state.split.totalBytes).isEqualTo(1_200)
        assertThat(state.totalBytes).isEqualTo(1_300)
    }

    @Test
    fun `rows outside the window are ignored`() {
        val today = 20_758L
        val state = appDetailUiState(
            packageName = "com.example.app",
            rows = listOf(
                row(today - 8, AppDailyNetwork.WIFI, rx = 9_000),
                row(today, AppDailyNetwork.WIFI, rx = 10),
            ),
            todayEpochDay = today,
        )

        assertThat(state.totalBytes).isEqualTo(10)
        assertThat(state.split.wifiBytes).isEqualTo(10)
    }

    @Test
    fun `an empty history falls back to the package name with no data`() {
        val state = appDetailUiState(
            packageName = "com.example.app",
            rows = emptyList(),
            todayEpochDay = 20_758L,
        )

        assertThat(state.label).isEqualTo("com.example.app")
        assertThat(state.hasData).isFalse()
        assertThat(state.lastSyncedAtMillis).isNull()
        assertThat(state.days).hasSize(7)
    }

    @Test
    fun `the newest sync time represents the window`() {
        val today = 20_758L
        val state = appDetailUiState(
            packageName = "com.example.app",
            rows = listOf(
                row(today - 1, AppDailyNetwork.WIFI, rx = 1, lastSyncedAt = 1_000),
                row(today, AppDailyNetwork.WIFI, rx = 1, lastSyncedAt = 9_000),
            ),
            todayEpochDay = today,
        )

        assertThat(state.lastSyncedAtMillis).isEqualTo(9_000)
    }

    private fun row(
        epochDay: Long,
        networkType: Int,
        rx: Long = 0,
        tx: Long = 0,
        label: String? = "Example",
        lastSyncedAt: Long? = 500,
    ) = AppDailyRow(
        epochDay = epochDay,
        networkType = networkType,
        rxBytes = rx,
        txBytes = tx,
        label = label,
        lastSyncedAt = lastSyncedAt,
    )
}
