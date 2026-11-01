package com.maknoon.mizan.core.platform.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidNetworkStatsSourceTest {

    @Test
    fun `no network answered means the usage is unknown`() {
        assertThat(mergeChannels(mobile = null, wifi = null)).isNull()
    }

    @Test
    fun `a measured network keeps its zero`() {
        val usage = mergeChannels(mobile = BucketTotals(0, 0), wifi = null)!!

        assertThat(usage.mobileRxBytes).isEqualTo(0)
        assertThat(usage.mobileTxBytes).isEqualTo(0)
        assertThat(usage.wifiRxBytes).isNull()
        assertThat(usage.wifiTxBytes).isNull()
    }

    @Test
    fun `one failing network does not erase the other`() {
        val usage = mergeChannels(mobile = null, wifi = BucketTotals(4_096, 512))!!

        assertThat(usage.mobileRxBytes).isNull()
        assertThat(usage.wifiRxBytes).isEqualTo(4_096)
        assertThat(usage.wifiTxBytes).isEqualTo(512)
    }

    @Test
    fun `both networks are reported separately`() {
        val usage = mergeChannels(
            mobile = BucketTotals(100, 20),
            wifi = BucketTotals(300, 40),
        )!!

        assertThat(usage).isEqualTo(AppUsageBytes(100, 20, 300, 40))
    }
}
