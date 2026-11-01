package com.maknoon.mizan.core.domain.accounting

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class TrafficAccountantTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val day1 = LocalDate.of(2026, 3, 9)
    private val day2 = LocalDate.of(2026, 3, 10)

    private val store = FakeUsageStateStore()

    private fun accountant() = TrafficAccountant(store, zone)

    private fun millisAt(
        date: LocalDate = day1,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
    ): Long = date.atStartOfDay(zone)
        .plusHours(hour.toLong())
        .plusMinutes(minute.toLong())
        .plusSeconds(second.toLong())
        .toInstant()
        .toEpochMilli()

    private fun snapshot(
        rx: Long,
        tx: Long = 0,
        bootEpochMillis: Long = millisAt(hour = 0, minute = 1),
        atMillis: Long,
    ) = TrafficSnapshot(rx, tx, bootEpochMillis, atMillis)

    @Test
    fun `first sample after a boot today counts the counters as today usage`() {
        val usage = accountant().record(
            snapshot(rx = 4_096, tx = 1_024, atMillis = millisAt(hour = 8)),
        )

        assertThat(usage.rxBytes).isEqualTo(4_096)
        assertThat(usage.txBytes).isEqualTo(1_024)
        assertThat(usage.totalBytes).isEqualTo(5_120)
        assertThat(usage.partial).isFalse()
        assertThat(usage.downBytesPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `first sample from a boot before midnight starts at zero and marks the day partial`() {
        val boot = millisAt(date = day1.minusDays(1), hour = 21)

        val usage = accountant().record(snapshot(rx = 5_000, bootEpochMillis = boot, atMillis = millisAt(hour = 9)))

        assertThat(usage.rxBytes).isEqualTo(0)
        assertThat(usage.txBytes).isEqualTo(0)
        assertThat(usage.partial).isTrue()
    }

    @Test
    fun `consecutive samples accumulate deltas and report the measured rate`() {
        val accountant = accountant()
        val boot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 1_000, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 0)))

        val usage = accountant.record(
            snapshot(rx = 1_001_000, tx = 500, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 1)),
        )

        assertThat(usage.rxBytes).isEqualTo(1_001_000)
        assertThat(usage.downBytesPerSecond).isWithin(0.001).of(1_000_000.0)
        assertThat(usage.upBytesPerSecond).isWithin(0.001).of(500.0)
    }

    @Test
    fun `a gap longer than the rate window still counts bytes but reports no rate`() {
        val accountant = accountant()
        val boot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 0, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 0)))

        val usage = accountant.record(
            snapshot(rx = 7_000, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 30)),
        )

        assertThat(usage.rxBytes).isEqualTo(7_000)
        assertThat(usage.downBytesPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `a sample closer than the minimum interval reports no rate`() {
        val accountant = accountant()
        val boot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 0, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 0)))

        val usage = accountant.record(
            snapshot(rx = 900, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 0) + 100),
        )

        assertThat(usage.rxBytes).isEqualTo(900)
        assertThat(usage.downBytesPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `counters going backwards never subtract traffic already counted`() {
        val accountant = accountant()
        val boot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 50_000, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 0)))

        val usage = accountant.record(
            snapshot(rx = 1_000, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 1)),
        )

        assertThat(usage.rxBytes).isEqualTo(50_000)
        assertThat(usage.downBytesPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `a new day started before the current boot resets usage and marks it partial`() {
        val accountant = accountant()
        val boot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 10_000, bootEpochMillis = boot, atMillis = millisAt(hour = 23)))

        val usage = accountant.record(
            snapshot(rx = 12_000, bootEpochMillis = boot, atMillis = millisAt(date = day2, hour = 0, minute = 5)),
        )

        assertThat(usage.epochDay).isEqualTo(day2.toEpochDay())
        assertThat(usage.rxBytes).isEqualTo(0)
        assertThat(usage.partial).isTrue()
    }

    @Test
    fun `a new day started by a boot after midnight counts the counters as full usage`() {
        val accountant = accountant()
        accountant.record(snapshot(rx = 10_000, atMillis = millisAt(hour = 23)))

        val usage = accountant.record(
            snapshot(
                rx = 700,
                bootEpochMillis = millisAt(date = day2, hour = 0, minute = 2),
                atMillis = millisAt(date = day2, hour = 6),
            ),
        )

        assertThat(usage.epochDay).isEqualTo(day2.toEpochDay())
        assertThat(usage.rxBytes).isEqualTo(700)
        assertThat(usage.partial).isFalse()
    }

    @Test
    fun `a reboot in the middle of the day adds the new counters to today`() {
        val accountant = accountant()
        val firstBoot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 2_000, bootEpochMillis = firstBoot, atMillis = millisAt(hour = 8, second = 0)))
        accountant.record(snapshot(rx = 3_000, bootEpochMillis = firstBoot, atMillis = millisAt(hour = 8, second = 1)))

        val usage = accountant.record(
            snapshot(
                rx = 400,
                bootEpochMillis = millisAt(hour = 9),
                atMillis = millisAt(hour = 9, minute = 5),
            ),
        )

        assertThat(usage.rxBytes).isEqualTo(3_400)
        assertThat(usage.partial).isFalse()
        assertThat(usage.downBytesPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `a reboot into a boot that predates today keeps totals and marks the day partial`() {
        val accountant = accountant()
        val firstBoot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 1_500, bootEpochMillis = firstBoot, atMillis = millisAt(hour = 10)))

        val usage = accountant.record(
            snapshot(
                rx = 9_000,
                bootEpochMillis = millisAt(date = day1.minusDays(1), hour = 22),
                atMillis = millisAt(hour = 11),
            ),
        )

        assertThat(usage.rxBytes).isEqualTo(1_500)
        assertThat(usage.partial).isTrue()
    }

    @Test
    fun `a partial day stays partial for the rest of that day`() {
        val accountant = accountant()
        val boot = millisAt(date = day1.minusDays(1), hour = 22)
        accountant.record(snapshot(rx = 5_000, bootEpochMillis = boot, atMillis = millisAt(hour = 9, second = 0)))

        val usage = accountant.record(
            snapshot(rx = 6_000, bootEpochMillis = boot, atMillis = millisAt(hour = 9, second = 1)),
        )

        assertThat(usage.rxBytes).isEqualTo(1_000)
        assertThat(usage.partial).isTrue()
    }

    @Test
    fun `a clock that moves backwards never yields a negative rate`() {
        val accountant = accountant()
        val boot = millisAt(hour = 0, minute = 1)
        accountant.record(snapshot(rx = 1_000, bootEpochMillis = boot, atMillis = millisAt(hour = 10, second = 0)))

        val usage = accountant.record(
            snapshot(rx = 3_000, bootEpochMillis = boot, atMillis = millisAt(hour = 9, second = 0)),
        )

        assertThat(usage.rxBytes).isEqualTo(3_000)
        assertThat(usage.downBytesPerSecond).isEqualTo(0.0)
    }

    @Test
    fun `state survives into a new accountant instance`() {
        val boot = millisAt(hour = 0, minute = 1)
        accountant().record(snapshot(rx = 1_000, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 0)))

        val usage = accountant().record(
            snapshot(rx = 2_000, bootEpochMillis = boot, atMillis = millisAt(hour = 8, second = 1)),
        )

        assertThat(usage.rxBytes).isEqualTo(2_000)
        assertThat(usage.downBytesPerSecond).isWithin(0.001).of(1_000.0)
    }

    private class FakeUsageStateStore(var state: UsageState? = null) : UsageStateStore {
        var saveCount = 0
            private set

        override fun load(): UsageState? = state

        override fun save(state: UsageState) {
            this.state = state
            saveCount++
        }
    }
}
