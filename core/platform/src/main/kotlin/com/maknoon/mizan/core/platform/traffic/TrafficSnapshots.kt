package com.maknoon.mizan.core.platform.traffic

import com.maknoon.mizan.core.domain.accounting.TrafficSnapshot

/**
 * Turns raw counter readings into a [TrafficSnapshot].
 *
 * Kept separate from [AndroidTrafficSource] so the conversion — including how an unsupported
 * counter is reported and how the boot instant is derived — is testable on the JVM without a
 * device.
 */
internal object TrafficSnapshots {

    /**
     * `TrafficStats` reports `UNSUPPORTED` (-1) for a counter the device does not implement,
     * so any negative reading means "cannot measure" and yields `null`.
     */
    fun of(
        rxBytes: Long,
        txBytes: Long,
        atMillis: Long,
        elapsedRealtimeMillis: Long,
    ): TrafficSnapshot? {
        if (rxBytes < 0L || txBytes < 0L) return null
        return TrafficSnapshot(
            rxBytes = rxBytes,
            txBytes = txBytes,
            // Counters are scoped to the current boot, so the boot instant is the reference
            // the accountant uses to decide whether today's counters are complete.
            bootEpochMillis = atMillis - elapsedRealtimeMillis,
            atMillis = atMillis,
        )
    }
}
