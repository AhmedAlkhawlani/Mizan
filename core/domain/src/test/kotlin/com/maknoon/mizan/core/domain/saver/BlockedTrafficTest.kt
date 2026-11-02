package com.maknoon.mizan.core.domain.saver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlockedTrafficTest {

    @Test
    fun `counts accumulate packets and bytes per package`() {
        val snapshot = BlockedTrafficSnapshot()
            .count("com.example", 40)
            .count("com.example", 60)

        assertThat(snapshot.perPackage["com.example"]).isEqualTo(TrafficCount(packets = 2, bytes = 100))
    }

    @Test
    fun `a null package goes to the unknown bucket`() {
        val snapshot = BlockedTrafficSnapshot().count(null, 40)

        assertThat(snapshot.unknown).isEqualTo(TrafficCount(packets = 1, bytes = 40))
        assertThat(snapshot.perPackage).isEmpty()
    }

    @Test
    fun `totals sum the per-package and unknown buckets`() {
        val snapshot = BlockedTrafficSnapshot()
            .count("com.example", 40)
            .count(null, 60)

        assertThat(snapshot.totalPackets).isEqualTo(2)
        assertThat(snapshot.totalBytes).isEqualTo(100)
    }

    @Test
    fun `counting is immutable`() {
        val original = BlockedTrafficSnapshot()

        original.count("com.example", 40)

        assertThat(original.perPackage).isEmpty()
        assertThat(original.totalBytes).isEqualTo(0)
    }

    @Test
    fun `two packages stay separate`() {
        val snapshot = BlockedTrafficSnapshot()
            .count("com.alpha", 10)
            .count("com.beta", 20)

        assertThat(snapshot.perPackage.keys).containsExactly("com.alpha", "com.beta")
        assertThat(snapshot.totalBytes).isEqualTo(30)
    }
}
