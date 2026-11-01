package com.maknoon.mizan.core.platform.traffic

import com.maknoon.mizan.core.domain.accounting.TrafficSnapshot

/**
 * Reads the device's cumulative traffic counters.
 *
 * Implementations return `null` when the platform cannot report the counters. That is a
 * deliberate difference from returning zero: docs/02 §9 requires the UI to show "—" when a
 * measurement is unavailable instead of inventing a number.
 */
fun interface TrafficSource {
    fun snapshot(): TrafficSnapshot?
}
