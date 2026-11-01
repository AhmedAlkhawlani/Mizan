package com.maknoon.mizan.monitoring

import com.maknoon.mizan.core.domain.accounting.DailyUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the monitor currently knows.
 *
 * [usage] is `null` when nothing has been measured yet, either because monitoring is stopped or
 * because the device does not report its counters. The UI shows "—" for that case instead of a
 * zero, following docs/02 §9.
 */
data class MonitoringState(
    val running: Boolean = false,
    val usage: DailyUsage? = null,
)

/**
 * The single in-process source of truth for live monitoring figures, observed by the dashboard,
 * the tile and the notification so they can never disagree (docs/10: one source per number).
 *
 * A plain object stands in for dependency injection here; when repositories arrive with Hilt it
 * becomes a bound singleton without changing its observers.
 */
object MonitoringStateHolder {

    private val currentState = MutableStateFlow(MonitoringState())

    val state: StateFlow<MonitoringState> = currentState.asStateFlow()

    fun publish(state: MonitoringState) {
        currentState.value = state
    }
}
