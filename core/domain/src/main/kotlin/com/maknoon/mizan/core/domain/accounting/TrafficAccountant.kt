package com.maknoon.mizan.core.domain.accounting

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One reading of the device's cumulative traffic counters.
 *
 * [rxBytes] and [txBytes] are monotonic since boot, exactly as `TrafficStats` reports them,
 * so they must never be treated as "usage today" on their own.
 * [bootEpochMillis] identifies the boot the counters belong to; [atMillis] is wall-clock
 * time, passed in so this class stays free of ambient clocks.
 */
data class TrafficSnapshot(
    val rxBytes: Long,
    val txBytes: Long,
    val bootEpochMillis: Long,
    val atMillis: Long,
)

/** Usage accumulated for a single local day, plus the rate measured between two samples. */
data class DailyUsage(
    val epochDay: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val downBytesPerSecond: Double,
    val upBytesPerSecond: Double,
    val partial: Boolean,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/**
 * Persisted accumulator. [partial] means the figures cover only part of [epochDay] — for
 * example a boot after midnight — and must be shown as such rather than as a full day.
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
 * either too short or too long to express a meaningful rate. Only real observed traffic is
 * ever added; when the device cannot tell us, the accountant reports `partial` instead of
 * inventing numbers.
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
        if (current.bootEpochMillis != snapshot.bootEpochMillis) {
            return handleReboot(current, snapshot, bootedToday)
        }
        return accumulate(current, snapshot)
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

        val next = current.copy(
            lastRxBytes = snapshot.rxBytes,
            lastTxBytes = snapshot.txBytes,
            todayRxBytes = current.todayRxBytes + deltaRx,
            todayTxBytes = current.todayTxBytes + deltaTx,
            updatedAtMillis = snapshot.atMillis,
        )
        val down = if (measurable) deltaRx / elapsedSeconds else 0.0
        val up = if (measurable) deltaTx / elapsedSeconds else 0.0
        return publish(next, down, up)
    }

    private fun publish(next: UsageState, down: Double, up: Double): DailyUsage {
        state = next
        store.save(next)
        return DailyUsage(
            epochDay = next.epochDay,
            rxBytes = next.todayRxBytes,
            txBytes = next.todayTxBytes,
            downBytesPerSecond = down,
            upBytesPerSecond = up,
            partial = next.partial,
        )
    }

    private fun dayOf(atMillis: Long): Long =
        LocalDate.ofInstant(Instant.ofEpochMilli(atMillis), zone).toEpochDay()

    private fun startOfDay(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    internal companion object {
        const val MIN_RATE_INTERVAL_SECONDS = 0.2
        const val MAX_RATE_INTERVAL_SECONDS = 10.0
    }
}
