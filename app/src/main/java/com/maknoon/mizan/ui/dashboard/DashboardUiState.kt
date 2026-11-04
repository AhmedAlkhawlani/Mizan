package com.maknoon.mizan.ui.dashboard

import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.monitoring.MonitoringState

/**
 * Everything the dashboard draws, already formatted.
 *
 * Formatting happens here rather than in the composable so the presentation rules — decimal
 * units, and an em dash instead of a zero when nothing could be measured — are unit tested.
 */
data class DashboardUiState(
    val running: Boolean = false,
    val speedAvailable: Boolean = false,
    val downSpeed: String = UNKNOWN,
    val upSpeed: String = UNKNOWN,
    val todayTotal: String = UNKNOWN,
    val mobileTotal: String = UNKNOWN,
    val wifiTotal: String = UNKNOWN,
    val partial: Boolean = false,
    /** null while the connection is unknown or absent; the chip then says so instead of guessing. */
    val networkMetered: Boolean? = null,
    val dailyBudgetLimit: String? = null,
    val dailyBudgetFraction: Float = 0f,
) {
    companion object {
        /** docs/02 §9: an unavailable figure is shown as a dash, never guessed. */
        const val UNKNOWN = "—"
    }
}

internal fun MonitoringState.toDashboardUiState(): DashboardUiState {
    val usage = usage
        ?: return DashboardUiState(running = running, speedAvailable = false)

    return DashboardUiState(
        running = running,
        speedAvailable = true,
        downSpeed = ByteFormatter.rate(usage.downBytesPerSecond),
        upSpeed = ByteFormatter.rate(usage.upBytesPerSecond),
        todayTotal = ByteFormatter.data(usage.totalBytes),
        mobileTotal = channelTotal(usage.mobileRxBytes, usage.mobileTxBytes),
        wifiTotal = channelTotal(usage.wifiRxBytes, usage.wifiTxBytes),
        partial = usage.partial,
    )
}

/** A split with a missing half is unknown as a whole; summing one side would understate it. */
private fun channelTotal(rxBytes: Long?, txBytes: Long?): String =
    if (rxBytes == null || txBytes == null) {
        DashboardUiState.UNKNOWN
    } else {
        ByteFormatter.data(rxBytes + txBytes)
    }
