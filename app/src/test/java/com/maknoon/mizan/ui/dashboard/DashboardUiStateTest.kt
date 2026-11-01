package com.maknoon.mizan.ui.dashboard

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.domain.accounting.DailyUsage
import com.maknoon.mizan.monitoring.MonitoringState
import org.junit.Test

class DashboardUiStateTest {

    @Test
    fun `a stopped monitor shows dashes and no speed`() {
        val ui = MonitoringState(running = false, usage = null).toDashboardUiState()

        assertThat(ui.running).isFalse()
        assertThat(ui.speedAvailable).isFalse()
        assertThat(ui.downSpeed).isEqualTo(DashboardUiState.UNKNOWN)
        assertThat(ui.upSpeed).isEqualTo(DashboardUiState.UNKNOWN)
        assertThat(ui.todayTotal).isEqualTo(DashboardUiState.UNKNOWN)
    }

    @Test
    fun `a running monitor without a measurable snapshot shows dashes`() {
        val ui = MonitoringState(running = true, usage = null).toDashboardUiState()

        assertThat(ui.running).isTrue()
        assertThat(ui.speedAvailable).isFalse()
        assertThat(ui.todayTotal).isEqualTo(DashboardUiState.UNKNOWN)
    }

    @Test
    fun `rates and totals use decimal units`() {
        val usage = DailyUsage(
            epochDay = 20_000L,
            rxBytes = 1_240_000_000L,
            txBytes = 60_000_000L,
            downBytesPerSecond = 12_400_000.0,
            upBytesPerSecond = 1_200_000.0,
            partial = false,
        )

        val ui = MonitoringState(running = true, usage = usage).toDashboardUiState()

        assertThat(ui.speedAvailable).isTrue()
        assertThat(ui.downSpeed).isEqualTo("12.4 MB/s")
        assertThat(ui.upSpeed).isEqualTo("1.2 MB/s")
        assertThat(ui.todayTotal).isEqualTo("1.3 GB")
    }

    @Test
    fun `a partial day is carried to the dashboard`() {
        val usage = DailyUsage(
            epochDay = 20_000L,
            rxBytes = 1_000L,
            txBytes = 0L,
            downBytesPerSecond = 0.0,
            upBytesPerSecond = 0.0,
            partial = true,
        )

        assertThat(MonitoringState(running = true, usage = usage).toDashboardUiState().partial).isTrue()
    }

    @Test
    fun `a zero rate is a real measurement, not a dash`() {
        val usage = DailyUsage(
            epochDay = 20_000L,
            rxBytes = 0L,
            txBytes = 0L,
            downBytesPerSecond = 0.0,
            upBytesPerSecond = 0.0,
            partial = false,
        )

        val ui = MonitoringState(running = true, usage = usage).toDashboardUiState()

        assertThat(ui.speedAvailable).isTrue()
        assertThat(ui.downSpeed).isEqualTo("0 B/s")
        assertThat(ui.todayTotal).isEqualTo("0 B")
    }

    @Test
    fun `the split shows formatted mobile and wifi totals`() {
        val usage = DailyUsage(
            epochDay = 20_000L,
            rxBytes = 560_000_000L,
            txBytes = 60_000_000L,
            downBytesPerSecond = 0.0,
            upBytesPerSecond = 0.0,
            partial = false,
            mobileRxBytes = 100_000_000L,
            mobileTxBytes = 20_000_000L,
            wifiRxBytes = 460_000_000L,
            wifiTxBytes = 40_000_000L,
        )

        val ui = MonitoringState(running = true, usage = usage).toDashboardUiState()

        assertThat(ui.mobileTotal).isEqualTo("120 MB")
        assertThat(ui.wifiTotal).isEqualTo("500 MB")
    }

    @Test
    fun `an unknown split shows dashes instead of guessing`() {
        val usage = DailyUsage(
            epochDay = 20_000L,
            rxBytes = 1_000L,
            txBytes = 0L,
            downBytesPerSecond = 0.0,
            upBytesPerSecond = 0.0,
            partial = false,
            mobileRxBytes = 500L,
            mobileTxBytes = null,
            wifiRxBytes = null,
            wifiTxBytes = null,
        )

        val ui = MonitoringState(running = true, usage = usage).toDashboardUiState()

        assertThat(ui.mobileTotal).isEqualTo(DashboardUiState.UNKNOWN)
        assertThat(ui.wifiTotal).isEqualTo(DashboardUiState.UNKNOWN)
    }
}
