package com.maknoon.mizan.core.domain.saver

import com.maknoon.mizan.core.domain.network.NetworkTransport
import com.maknoon.mizan.core.domain.network.ResolvedNetwork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverMeteringTest {

    @Test
    fun `blocks on a connected metered network`() {
        assertTrue(shouldBlockTraffic(resolved(connected = true, metered = true)))
    }

    @Test
    fun `allows on a connected unmetered network`() {
        assertFalse(shouldBlockTraffic(resolved(connected = true, metered = false)))
    }

    @Test
    fun `allows when disconnected even if flagged metered`() {
        assertFalse(shouldBlockTraffic(resolved(connected = false, metered = true)))
    }

    private fun resolved(connected: Boolean, metered: Boolean) = ResolvedNetwork(
        connected = connected,
        transport = NetworkTransport.WIFI,
        metered = metered,
        roaming = false,
        profile = null,
    )
}
