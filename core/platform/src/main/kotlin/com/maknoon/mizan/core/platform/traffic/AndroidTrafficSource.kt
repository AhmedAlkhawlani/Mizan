package com.maknoon.mizan.core.platform.traffic

import android.net.TrafficStats
import android.os.SystemClock
import com.maknoon.mizan.core.domain.accounting.TrafficSnapshot

/**
 * Reads the device-wide counters through `TrafficStats`.
 *
 * These counters are cumulative since boot and need no permission, which is why they are the
 * source for the live speed figure (docs/02 §1). Per-app numbers are a different source
 * altogether and need Usage Access.
 *
 * Both clocks are injectable so the conversion can be exercised without a device.
 */
class AndroidTrafficSource(
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) : TrafficSource {

    override fun snapshot(): TrafficSnapshot? = TrafficSnapshots.of(
        rxBytes = TrafficStats.getTotalRxBytes(),
        txBytes = TrafficStats.getTotalTxBytes(),
        atMillis = wallClockMillis(),
        elapsedRealtimeMillis = elapsedRealtimeMillis(),
    )
}
