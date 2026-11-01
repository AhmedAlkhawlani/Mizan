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
        mobileRxBytes: Long? = null,
        mobileTxBytes: Long? = null,
        loopbackRxBytes: Long? = null,
        loopbackTxBytes: Long? = null,
    ): TrafficSnapshot? {
        if (rxBytes < 0L || txBytes < 0L) return null
        return TrafficSnapshot(
            rxBytes = rxBytes,
            txBytes = txBytes,
            // Counters are scoped to the current boot, so the boot instant is the reference
            // the accountant uses to decide whether today's counters are complete.
            bootEpochMillis = atMillis - elapsedRealtimeMillis,
            atMillis = atMillis,
            // The uptime itself is what detects a boot: the derived instant above moves with
            // wall-clock drift and must not be compared between samples.
            sinceBootMillis = elapsedRealtimeMillis,
            mobileRxBytes = mobileRxBytes?.takeIf { it >= 0L },
            mobileTxBytes = mobileTxBytes?.takeIf { it >= 0L },
            loopbackRxBytes = loopbackRxBytes?.takeIf { it >= 0L },
            loopbackTxBytes = loopbackTxBytes?.takeIf { it >= 0L },
        )
    }
}
