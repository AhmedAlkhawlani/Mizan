package com.maknoon.mizan.core.platform.network

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.domain.network.NetworkTransport
import org.junit.Test

class NetworkStatesTest {

    @Test
    fun `wifi wins when both wifi and cellular are present`() {
        val snapshot = NetworkStates.of(
            hasWifi = true,
            hasCellular = true,
            hasEthernet = false,
            hasVpn = false,
            systemMetered = false,
            temporarilyUnmetered = false,
            roaming = false,
        )

        assertThat(snapshot.transport).isEqualTo(NetworkTransport.WIFI)
    }

    @Test
    fun `cellular is chosen without wifi`() {
        val snapshot = NetworkStates.of(
            hasWifi = false,
            hasCellular = true,
            hasEthernet = false,
            hasVpn = false,
            systemMetered = true,
            temporarilyUnmetered = false,
            roaming = false,
        )

        assertThat(snapshot.transport).isEqualTo(NetworkTransport.CELLULAR)
    }

    @Test
    fun `ethernet is recognised`() {
        val snapshot = NetworkStates.of(
            hasWifi = false,
            hasCellular = false,
            hasEthernet = true,
            hasVpn = false,
            systemMetered = false,
            temporarilyUnmetered = false,
            roaming = false,
        )

        assertThat(snapshot.transport).isEqualTo(NetworkTransport.ETHERNET)
    }

    @Test
    fun `a vpn-only network falls back to the vpn transport`() {
        val snapshot = NetworkStates.of(
            hasWifi = false,
            hasCellular = false,
            hasEthernet = false,
            hasVpn = true,
            systemMetered = true,
            temporarilyUnmetered = false,
            roaming = false,
        )

        assertThat(snapshot.transport).isEqualTo(NetworkTransport.VPN)
    }

    @Test
    fun `an unrecognised transport stays other`() {
        val snapshot = NetworkStates.of(
            hasWifi = false,
            hasCellular = false,
            hasEthernet = false,
            hasVpn = false,
            systemMetered = true,
            temporarilyUnmetered = false,
            roaming = false,
        )

        assertThat(snapshot.transport).isEqualTo(NetworkTransport.OTHER)
    }

    @Test
    fun `metered, temporary and roaming flags are preserved`() {
        val snapshot = NetworkStates.of(
            hasWifi = true,
            hasCellular = false,
            hasEthernet = false,
            hasVpn = false,
            systemMetered = true,
            temporarilyUnmetered = true,
            roaming = true,
        )

        assertThat(snapshot.connected).isTrue()
        assertThat(snapshot.systemMetered).isTrue()
        assertThat(snapshot.temporarilyUnmetered).isTrue()
        assertThat(snapshot.roaming).isTrue()
    }

    @Test
    fun `a missing network is disconnected and not metered`() {
        val snapshot = NetworkStates.disconnected()

        assertThat(snapshot.connected).isFalse()
        assertThat(snapshot.transport).isEqualTo(NetworkTransport.OTHER)
        assertThat(snapshot.systemMetered).isFalse()
    }
}
