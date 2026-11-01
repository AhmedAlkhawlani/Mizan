package com.maknoon.mizan.core.domain.accounting

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One reading of the device's cumulative traffic counters.
 *
 * [rxBytes] and [txBytes] are monotonic since boot, exactly as `TrafficStats` reports them,
 * so they must never be treated as "usage today" on their own.
 * [bootEpochMillis] identifies the boot the counters belong to for day attribution;
 * [atMillis] is wall-clock time, passed in so this class stays free of ambient clocks.
 *
 * [sinceBootMillis] is the device's monotonic uptime. It is the only reliable way to tell one
 * boot from another: [bootEpochMillis] is derived as wall clock minus uptime, and the two
 * clocks drift by milliseconds, so the derived instant moves between samples even though no
 * reboot happened. Its default keeps the two readings consistent for callers that only model
 * a boot instant and a sample time.
 *
 * The optional counters split the total by network path. They are `null` when the platform
 * returns `UNSUPPORTED` for that counter: a missing split is reported as unknown rather than
 * inferred from the total, which also contains loopback and tunnel traffic.
 */
data class TrafficSnapshot(
    val rxBytes: Long,
    val txBytes: Long,
    val bootEpochMillis: Long,
    val atMillis: Long,
    val sinceBootMillis: Long = atMillis - bootEpochMillis,
    val mobileRxBytes: Long? = null,
    val mobileTxBytes: Long? = null,
    val loopbackRxBytes: Long? = null,
    val loopbackTxBytes: Long? = null,
)

/**
 * Usage accumulated for a single local day, plus the rate measured between two samples.
 *
 * [mobileRxBytes] and [wifiRxBytes] (and their transmit counterparts) are `null` when the day
 * could not be split, for example when the platform does not report loopback counters. They
 * are never estimated by subtraction from an incomplete total.
 */
data class DailyUsage(
    val epochDay: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val downBytesPerSecond: Double,
    val upBytesPerSecond: Double,
    val partial: Boolean,
    val mobileRxBytes: Long? = null,
    val mobileTxBytes: Long? = null,
    val wifiRxBytes: Long? = null,
    val wifiTxBytes: Long? = null,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/**
 * Persisted accumulator. [partial] means the figures cover only part of [epochDay] — for
 * example a boot after midnight — and must be shown as such rather than as a full day.
 *
 * The mobile and loopback channels mirror the total's boot/day rules. A channel whose today
 * fields are `null` has no trustworthy split for this day: it may have been unreadable when
 * the day began, and traffic counted from then on cannot be presented as the day's figure.
 */
data class UsageState(
    val epochDay: Long,
    val bootEpochMillis: Long,
    val lastRxBytes: Long,
    val lastTxBytes: Long,
    val todayRxBytes: Long,
    val todayTxBytes: Long,
    val updatedAtMillis: Long,
    val startedAtMillis: Long,
    val partial: Boolean,
    val lastMobileRxBytes: Long? = null,
    val lastMobileTxBytes: Long? = null,
    val todayMobileRxBytes: Long? = null,
    val todayMobileTxBytes: Long? = null,
    val lastLoopbackRxBytes: Long? = null,
    val lastLoopbackTxBytes: Long? = null,
    val todayLoopbackRxBytes: Long? = null,
    val todayLoopbackTxBytes: Long? = null,
)

/** Where the accountant keeps its accumulator between app starts. */
interface UsageStateStore {
    fun load(): UsageState?
    fun save(state: UsageState)
}

/**
 * Turns cumulative, boot-scoped counters into per-day usage and instantaneous rates.
 *
 * Four events make naive subtraction wrong, and each is handled explicitly: a new local day,
 * a reboot, a counter that appears to go backwards, and a gap between two samples that is
 * either too short or too long to express a meaningful rate. A reboot is recognized by the
 * monotonic uptime resetting, never by the derived boot instant changing. Only real observed
 * traffic is ever added; when the device cannot tell us, the accountant reports `partial`
 * instead of inventing numbers.
 *
 * This module runs on Android from API 26 while its tests run on a modern desktop JDK, so only
 * `java.time` entry points that exist on API 26 may be used here. A JDK-only method would pass
 * every test and fail on a real device.
 */
class TrafficAccountant(
    private val store: UsageStateStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private var state: UsageState? = store.load()

    /** Records a sample and returns the day's usage so far with the rate since the last sample. */
    fun record(snapshot: TrafficSnapshot): DailyUsage {
        val day = dayOf(snapshot.atMillis)
        val dayStartMillis = startOfDay(day)
        val bootedToday = snapshot.bootEpochMillis >= dayStartMillis
        val current = state

        if (current == null || day > current.epochDay) {
            return startDay(snapshot, day, dayStartMillis, bootedToday)
        }
        if (isNewBoot(current, snapshot)) {
            return handleReboot(current, snapshot, bootedToday)
        }
        return accumulate(current, snapshot)
    }

    /**
     * Detects the counter reset that a boot causes.
     *
     * Uptime is monotonic and only goes backwards at a boot. The boot instant must not be
     * compared between samples: it is derived from the wall clock minus uptime, and on a real
     * device the two clocks drift by a few milliseconds, so equality fails on most samples.
     * Treating that as a reboot discards the traffic of the sample, and when the boot falls
     * after midnight it would instead add the whole counter again. Falling counters are the
     * second signal, and they also catch a boot that happened while the process was not
     * running.
     */
    private fun isNewBoot(current: UsageState, snapshot: TrafficSnapshot): Boolean {
        val lastUptimeMillis = current.updatedAtMillis - current.bootEpochMillis
        return snapshot.sinceBootMillis < lastUptimeMillis ||
            snapshot.rxBytes < current.lastRxBytes ||
            snapshot.txBytes < current.lastTxBytes
    }

    /** Usage from the very first sample, which cannot have a rate attached to it. */
    private fun startDay(
        snapshot: TrafficSnapshot,
        day: Long,
        dayStartMillis: Long,
        bootedToday: Boolean,
    ): DailyUsage {
        val next = UsageState(
            epochDay = day,
            bootEpochMillis = snapshot.bootEpochMillis,
            lastRxBytes = snapshot.rxBytes,
            lastTxBytes = snapshot.txBytes,
            todayRxBytes = if (bootedToday) snapshot.rxBytes else 0L,
            todayTxBytes = if (bootedToday) snapshot.txBytes else 0L,
            updatedAtMillis = snapshot.atMillis,
            startedAtMillis = if (bootedToday) dayStartMillis else snapshot.atMillis,
            partial = !bootedToday,
            lastMobileRxBytes = snapshot.mobileRxBytes,
            lastMobileTxBytes = snapshot.mobileTxBytes,
            todayMobileRxBytes = seedChannel(snapshot.mobileRxBytes, bootedToday),
            todayMobileTxBytes = seedChannel(snapshot.mobileTxBytes, bootedToday),
            lastLoopbackRxBytes = snapshot.loopbackRxBytes,
            lastLoopbackTxBytes = snapshot.loopbackTxBytes,
            todayLoopbackRxBytes = seedChannel(snapshot.loopbackRxBytes, bootedToday),
            todayLoopbackTxBytes = seedChannel(snapshot.loopbackTxBytes, bootedToday),
        )
        return publish(next, down = 0.0, up = 0.0)
    }

    /**
     * A reboot restarts both counters at zero. Everything counted since the new boot belongs
     * to today only if the new boot happened today; otherwise the earlier part of the day is
     * lost and the day is marked partial.
     */
    private fun handleReboot(
        current: UsageState,
        snapshot: TrafficSnapshot,
        bootedToday: Boolean,
    ): DailyUsage {
        val next = current.copy(
            bootEpochMillis = snapshot.bootEpochMillis,
            lastRxBytes = snapshot.rxBytes,
            lastTxBytes = snapshot.txBytes,
            todayRxBytes = current.todayRxBytes + if (bootedToday) snapshot.rxBytes else 0L,
            todayTxBytes = current.todayTxBytes + if (bootedToday) snapshot.txBytes else 0L,
            updatedAtMillis = snapshot.atMillis,
            partial = current.partial || !bootedToday,
            lastMobileRxBytes = snapshot.mobileRxBytes,
            lastMobileTxBytes = snapshot.mobileTxBytes,
            todayMobileRxBytes = addRebootedChannel(
                current.todayMobileRxBytes,
                snapshot.mobileRxBytes,
                bootedToday,
            ),
            todayMobileTxBytes = addRebootedChannel(
                current.todayMobileTxBytes,
                snapshot.mobileTxBytes,
                bootedToday,
            ),
            lastLoopbackRxBytes = snapshot.loopbackRxBytes,
            lastLoopbackTxBytes = snapshot.loopbackTxBytes,
            todayLoopbackRxBytes = addRebootedChannel(
                current.todayLoopbackRxBytes,
                snapshot.loopbackRxBytes,
                bootedToday,
            ),
            todayLoopbackTxBytes = addRebootedChannel(
                current.todayLoopbackTxBytes,
                snapshot.loopbackTxBytes,
                bootedToday,
            ),
        )
        return publish(next, down = 0.0, up = 0.0)
    }

    /** Normal sampling: add the delta since the previous sample and derive the rate from it. */
    private fun accumulate(current: UsageState, snapshot: TrafficSnapshot): DailyUsage {
        // Counters can only grow within one boot; a decrease means an unseen reset, so the
        // delta is treated as zero instead of subtracting traffic that was already counted.
        val deltaRx = (snapshot.rxBytes - current.lastRxBytes).coerceAtLeast(0L)
        val deltaTx = (snapshot.txBytes - current.lastTxBytes).coerceAtLeast(0L)
        val elapsedSeconds = (snapshot.atMillis - current.updatedAtMillis) / 1000.0
        val measurable = elapsedSeconds in MIN_RATE_INTERVAL_SECONDS..MAX_RATE_INTERVAL_SECONDS

        val mobileRx = addObservedChannel(
            current.todayMobileRxBytes,
            current.lastMobileRxBytes,
            snapshot.mobileRxBytes,
        )
        val mobileTx = addObservedChannel(
            current.todayMobileTxBytes,
            current.lastMobileTxBytes,
            snapshot.mobileTxBytes,
        )
        val loopbackRx = addObservedChannel(
            current.todayLoopbackRxBytes,
            current.lastLoopbackRxBytes,
            snapshot.loopbackRxBytes,
        )
        val loopbackTx = addObservedChannel(
            current.todayLoopbackTxBytes,
            current.lastLoopbackTxBytes,
            snapshot.loopbackTxBytes,
        )

        val next = current.copy(
            lastRxBytes = snapshot.rxBytes,
            lastTxBytes = snapshot.txBytes,
            todayRxBytes = current.todayRxBytes + deltaRx,
            todayTxBytes = current.todayTxBytes + deltaTx,
            updatedAtMillis = snapshot.atMillis,
            // Kept aligned with the sample time so the persisted pair still yields this
            // sample's uptime after the process restarts.
            bootEpochMillis = snapshot.bootEpochMillis,
            lastMobileRxBytes = snapshot.mobileRxBytes,
            lastMobileTxBytes = snapshot.mobileTxBytes,
            todayMobileRxBytes = mobileRx,
            todayMobileTxBytes = mobileTx,
            lastLoopbackRxBytes = snapshot.loopbackRxBytes,
            lastLoopbackTxBytes = snapshot.loopbackTxBytes,
            todayLoopbackRxBytes = loopbackRx,
            todayLoopbackTxBytes = loopbackTx,
        )
        val down = if (measurable) deltaRx / elapsedSeconds else 0.0
        val up = if (measurable) deltaTx / elapsedSeconds else 0.0
        return publish(next, down, up)
    }

    private fun publish(next: UsageState, down: Double, up: Double): DailyUsage {
        state = next
        store.save(next)

        // Wi-Fi is what remains of the total after mobile and loopback. It is only derivable
        // when both readings exist; otherwise the split is unknown, not zero. Tunnel traffic
        // is also counted on its underlying interface, so Wi-Fi keeps that double count.
        val wifiRxBytes = subtractChannels(
            next.todayRxBytes,
            next.todayMobileRxBytes,
            next.todayLoopbackRxBytes,
        )
        val wifiTxBytes = subtractChannels(
            next.todayTxBytes,
            next.todayMobileTxBytes,
            next.todayLoopbackTxBytes,
        )

        return DailyUsage(
            epochDay = next.epochDay,
            rxBytes = next.todayRxBytes,
            txBytes = next.todayTxBytes,
            downBytesPerSecond = down,
            upBytesPerSecond = up,
            partial = next.partial,
            mobileRxBytes = next.todayMobileRxBytes,
            mobileTxBytes = next.todayMobileTxBytes,
            wifiRxBytes = wifiRxBytes,
            wifiTxBytes = wifiTxBytes,
        )
    }

    /**
     * The first sample seeds a channel the same way it seeds the total: if the boot predates
     * midnight the counter contains traffic from before the day, so only zero is certain.
     */
    private fun seedChannel(reading: Long?, bootedToday: Boolean): Long? = when {
        reading == null -> null
        bootedToday -> reading
        else -> 0L
    }

    /**
     * Adds an observed delta to a seeded channel. A channel that had no trustworthy value at
     * the start of the day stays unknown for the rest of it; starting to count mid-day would
     * present a partial figure as the day's split.
     */
    private fun addObservedChannel(today: Long?, last: Long?, reading: Long?): Long? = when {
        today == null || reading == null -> null
        last == null -> today
        else -> today + (reading - last).coerceAtLeast(0L)
    }

    /** A reboot adds the whole new counter only when the boot happened after midnight. */
    private fun addRebootedChannel(today: Long?, reading: Long?, bootedToday: Boolean): Long? =
        when {
            today == null || reading == null -> null
            bootedToday -> today + reading
            else -> today
        }

    private fun subtractChannels(total: Long, mobile: Long?, loopback: Long?): Long? {
        if (mobile == null || loopback == null) return null
        return (total - mobile - loopback).coerceAtLeast(0L)
    }

    // Instant.atZone is the API 26-safe route to a local date. LocalDate.ofInstant looks
    // tidier but only exists from API 34, and this module is tested on a desktop JDK, so using
    // it here would pass every JVM test and crash on an Android 8-13 device.
    private fun dayOf(atMillis: Long): Long =
        Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().toEpochDay()

    private fun startOfDay(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    internal companion object {
        const val MIN_RATE_INTERVAL_SECONDS = 0.2
        const val MAX_RATE_INTERVAL_SECONDS = 10.0
    }
}
