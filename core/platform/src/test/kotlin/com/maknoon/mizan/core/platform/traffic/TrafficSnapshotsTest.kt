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
    fun `the uptime reading is carried separately from the derived boot instant`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 0,
            txBytes = 0,
            atMillis = 1_000_000L,
            elapsedRealtimeMillis = 400_000L,
        )

        assertThat(snapshot!!.sinceBootMillis).isEqualTo(400_000L)
        assertThat(snapshot.atMillis - snapshot.bootEpochMillis).isEqualTo(snapshot.sinceBootMillis)
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

    @Test
    fun `supported interface counters are carried through`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 1_000L,
            txBytes = 2_000L,
            atMillis = 5_000L,
            elapsedRealtimeMillis = 1_000L,
            mobileRxBytes = 300L,
            mobileTxBytes = 400L,
            loopbackRxBytes = 50L,
            loopbackTxBytes = 60L,
        )

        assertThat(snapshot!!.mobileRxBytes).isEqualTo(300L)
        assertThat(snapshot.mobileTxBytes).isEqualTo(400L)
        assertThat(snapshot.loopbackRxBytes).isEqualTo(50L)
        assertThat(snapshot.loopbackTxBytes).isEqualTo(60L)
    }

    @Test
    fun `an unsupported mobile counter becomes unknown`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 1_000L,
            txBytes = 2_000L,
            atMillis = 5_000L,
            elapsedRealtimeMillis = 1_000L,
            mobileRxBytes = -1L,
            mobileTxBytes = -1L,
        )

        assertThat(snapshot!!.mobileRxBytes).isNull()
        assertThat(snapshot.mobileTxBytes).isNull()
    }

    @Test
    fun `an unsupported loopback counter becomes unknown`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 1_000L,
            txBytes = 2_000L,
            atMillis = 5_000L,
            elapsedRealtimeMillis = 1_000L,
            loopbackRxBytes = -1L,
            loopbackTxBytes = -1L,
        )

        assertThat(snapshot!!.loopbackRxBytes).isNull()
        assertThat(snapshot.loopbackTxBytes).isNull()
    }

    @Test
    fun `a zero interface counter is a real measurement`() {
        val snapshot = TrafficSnapshots.of(
            rxBytes = 1_000L,
            txBytes = 2_000L,
            atMillis = 5_000L,
            elapsedRealtimeMillis = 1_000L,
            mobileRxBytes = 0L,
            mobileTxBytes = 0L,
            loopbackRxBytes = 0L,
            loopbackTxBytes = 0L,
        )

        assertThat(snapshot!!.mobileRxBytes).isEqualTo(0L)
        assertThat(snapshot.loopbackRxBytes).isEqualTo(0L)
    }
}
