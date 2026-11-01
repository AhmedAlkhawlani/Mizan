package com.maknoon.mizan.core.domain.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkProfileResolverTest {

    private val resolver = NetworkProfileResolver()

    private fun snapshot(
        connected: Boolean = true,
        transport: NetworkTransport = NetworkTransport.WIFI,
        systemMetered: Boolean = false,
        temporarilyUnmetered: Boolean = false,
        roaming: Boolean = false,
    ) = NetworkSnapshot(
        connected = connected,
        transport = transport,
        systemMetered = systemMetered,
        temporarilyUnmetered = temporarilyUnmetered,
        roaming = roaming,
    )

    private fun profile(
        name: String = "البيت",
        transport: NetworkTransport = NetworkTransport.WIFI,
        systemMetered: Boolean = false,
        metered: Boolean = false,
        monthlyBudgetBytes: Long? = null,
    ) = NetworkProfile(
        name = name,
        transport = transport,
        systemMetered = systemMetered,
        metered = metered,
        monthlyBudgetBytes = monthlyBudgetBytes,
        createdAt = 1L,
    )

    @Test
    fun `without a profile the system metered flag is kept`() {
        val metered = resolver.resolve(snapshot(systemMetered = true), emptyList())
        val unmetered = resolver.resolve(snapshot(systemMetered = false), emptyList())

        assertThat(metered.metered).isTrue()
        assertThat(unmetered.metered).isFalse()
        assertThat(metered.profile).isNull()
    }

    @Test
    fun `a profile can declare an unmetered Wi-Fi as metered`() {
        val resolved = resolver.resolve(
            snapshot(transport = NetworkTransport.WIFI, systemMetered = false),
            listOf(profile(metered = true)),
        )

        assertThat(resolved.metered).isTrue()
        assertThat(resolved.profile?.name).isEqualTo("البيت")
    }

    @Test
    fun `a profile can declare a metered hotspot as unmetered`() {
        val resolved = resolver.resolve(
            snapshot(transport = NetworkTransport.WIFI, systemMetered = true),
            listOf(profile(name = "هوتسبوت", systemMetered = true, metered = false)),
        )

        assertThat(resolved.metered).isFalse()
        assertThat(resolved.profile?.name).isEqualTo("هوتسبوت")
    }

    @Test
    fun `a profile for the same transport but the other metered flag does not match`() {
        val resolved = resolver.resolve(
            snapshot(transport = NetworkTransport.WIFI, systemMetered = true),
            listOf(profile(name = "البيت", systemMetered = false, metered = false)),
        )

        assertThat(resolved.profile).isNull()
        assertThat(resolved.metered).isTrue()
    }

    @Test
    fun `a profile for another transport does not match`() {
        val resolved = resolver.resolve(
            snapshot(transport = NetworkTransport.CELLULAR, systemMetered = true),
            listOf(profile(transport = NetworkTransport.WIFI, systemMetered = true, metered = false)),
        )

        assertThat(resolved.profile).isNull()
        assertThat(resolved.metered).isTrue()
    }

    @Test
    fun `temporarily unmetered wins over a metered profile`() {
        val resolved = resolver.resolve(
            snapshot(systemMetered = true, temporarilyUnmetered = true),
            listOf(profile(systemMetered = true, metered = true)),
        )

        assertThat(resolved.metered).isFalse()
        assertThat(resolved.profile?.name).isEqualTo("البيت")
    }

    @Test
    fun `temporarily unmetered wins over the system flag without a profile`() {
        val resolved = resolver.resolve(snapshot(systemMetered = true, temporarilyUnmetered = true), emptyList())

        assertThat(resolved.metered).isFalse()
    }

    @Test
    fun `a disconnected device is never metered`() {
        val resolved = resolver.resolve(
            snapshot(connected = false, transport = NetworkTransport.OTHER, systemMetered = true),
            listOf(profile()),
        )

        assertThat(resolved.connected).isFalse()
        assertThat(resolved.metered).isFalse()
        assertThat(resolved.profile).isNull()
    }

    @Test
    fun `roaming and transport are carried through unchanged`() {
        val resolved = resolver.resolve(
            snapshot(transport = NetworkTransport.CELLULAR, systemMetered = true, roaming = true),
            emptyList(),
        )

        assertThat(resolved.transport).isEqualTo(NetworkTransport.CELLULAR)
        assertThat(resolved.roaming).isTrue()
        assertThat(resolved.connected).isTrue()
    }

    @Test
    fun `the matched profile is returned with its budget for the caller`() {
        val resolved = resolver.resolve(
            snapshot(transport = NetworkTransport.WIFI, systemMetered = false),
            listOf(profile(name = "العمل", metered = true, monthlyBudgetBytes = 20_000_000_000L)),
        )

        assertThat(resolved.profile?.monthlyBudgetBytes).isEqualTo(20_000_000_000L)
        assertThat(resolved.profile?.metered).isTrue()
    }
}
