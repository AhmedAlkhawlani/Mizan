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
    val partial: Boolean = false,
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
        partial = usage.partial,
    )
}
