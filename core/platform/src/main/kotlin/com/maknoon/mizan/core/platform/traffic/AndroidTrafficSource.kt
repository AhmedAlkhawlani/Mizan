package com.maknoon.mizan.core.platform.traffic

import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import com.maknoon.mizan.core.domain.accounting.TrafficSnapshot

/**
 * Reads the device-wide counters through `TrafficStats`.
 *
 * These counters are cumulative since boot and need no permission, which is why they are the
 * source for the live speed figure (docs/02 §1). Per-app numbers are a different source
 * altogether and need Usage Access.
 *
 * The total includes loopback traffic, so the Wi-Fi share can only be derived by subtracting
 * both mobile and loopback. Interface byte counters are public from API 31; before that the
 * loopback reading is reported as unknown and the split is left null rather than presented as
 * a total-minus-mobile guess that still contains loopback.
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
        mobileRxBytes = TrafficStats.getMobileRxBytes(),
        mobileTxBytes = TrafficStats.getMobileTxBytes(),
        loopbackRxBytes = loopbackRxBytes(),
        loopbackTxBytes = loopbackTxBytes(),
    )

    private fun loopbackRxBytes(): Long? = if (supportsInterfaceCounters()) {
        TrafficStats.getRxBytes(LOOPBACK_INTERFACE)
    } else {
        null
    }

    private fun loopbackTxBytes(): Long? = if (supportsInterfaceCounters()) {
        TrafficStats.getTxBytes(LOOPBACK_INTERFACE)
    } else {
        null
    }

    private fun supportsInterfaceCounters(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private companion object {
        const val LOOPBACK_INTERFACE = "lo"
    }
}
