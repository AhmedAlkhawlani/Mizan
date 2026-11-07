package com.maknoon.mizan.core.domain.saver

import com.maknoon.mizan.core.domain.network.NetworkTransport
import com.maknoon.mizan.core.domain.network.ResolvedNetwork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverMeteringTest {

    @Test
    fun `blocks on a connected metered network`() {
        assertTrue(
            shouldBlockTraffic(
                resolved(connected = true, metered = true),
                protectEveryNetwork = true,
            ),
        )
    }

    /**
     * The regression this rule exists for: a blocked app opened normally on home Wi-Fi because
     * the tunnel stood down on unmetered connections, and the firewall looked broken.
     */
    @Test
    fun `blocks on an unmetered network when the operator asked for every network`() {
        assertTrue(
            shouldBlockTraffic(
                resolved(connected = true, metered = false),
                protectEveryNetwork = true,
            ),
        )
    }

    @Test
    fun `allows on an unmetered network when the operator chose metered only`() {
        assertFalse(
            shouldBlockTraffic(
                resolved(connected = true, metered = false),
                protectEveryNetwork = false,
            ),
        )
    }

    @Test
    fun `still blocks a metered network when the setting is off`() {
        assertTrue(
            shouldBlockTraffic(
                resolved(connected = true, metered = true),
                protectEveryNetwork = false,
            ),
        )
    }

    @Test
    fun `allows when disconnected even if flagged metered`() {
        assertFalse(
            shouldBlockTraffic(
                resolved(connected = false, metered = true),
                protectEveryNetwork = true,
            ),
        )
    }

    private fun resolved(connected: Boolean, metered: Boolean) = ResolvedNetwork(
        connected = connected,
        transport = NetworkTransport.WIFI,
        metered = metered,
        roaming = false,
        profile = null,
    )
}
