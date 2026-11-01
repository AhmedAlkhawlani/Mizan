package com.maknoon.mizan.core.platform.traffic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrafficSnapshotsTest {

    @Test
    fun `a supported reading becomes a snapshot with the counters preserved`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 1_024,
            txBytes = 2_048,
            atMillis = 1_000_000L,
            elapsedRealtimeMillis = 400_000L,
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.rxBytes).isEqualTo(1_024)
        assertThat(snapshot.txBytes).isEqualTo(2_048)
        assertThat(snapshot.atMillis).isEqualTo(1_000_000L)
    }

    @Test
    fun `the boot instant is derived from wall clock minus uptime`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 0,
            txBytes = 0,
            atMillis = 1_000_000L,
            elapsedRealtimeMillis = 400_000L,
        )

        assertThat(snapshot!!.bootEpochMillis).isEqualTo(600_000L)
    }

    @Test
    fun `zero counters are a valid measurement, not an unsupported one`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 0,
            txBytes = 0,
            atMillis = 5_000L,
            elapsedRealtimeMillis = 1_000L,
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.txBytes).isEqualTo(0)
    }

    @Test
    fun `an unsupported receive counter yields no snapshot`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = -1L,
            txBytes = 500L,
            atMillis = 5_000L,
            elapsedRealtimeMillis = 1_000L,
        )

        assertThat(snapshot).isNull()
    }

    @Test
    fun `an unsupported transmit counter yields no snapshot`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 500L,
            txBytes = -1L,
            atMillis = 5_000L,
            elapsedRealtimeMillis = 1_000L,
        )

        assertThat(snapshot).isNull()
    }

    @Test
    fun `a boot instant before the epoch is still passed through unchanged`() {
        // A device whose wall clock was just set can report uptime longer than the epoch time;
        // the accountant, not this converter, decides what that means.
        val snapshot = TrafficSnapshots.of(
            rxBytes = 10L,
            txBytes = 20L,
            atMillis = 1_000L,
            elapsedRealtimeMillis = 50_000L,
        )

        assertThat(snapshot!!.bootEpochMillis).isEqualTo(-49_000L)
    }
}
