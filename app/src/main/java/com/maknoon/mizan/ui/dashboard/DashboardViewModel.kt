package com.maknoon.mizan.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = MonitoringStateHolder.state
        .map { it.toDashboardUiState() }
        .stateIn(
            scope = viewModelScope,
            // Keep the last value briefly so a rotation does not flash an empty dashboard.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MonitoringStateHolder.state.value.toDashboardUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
