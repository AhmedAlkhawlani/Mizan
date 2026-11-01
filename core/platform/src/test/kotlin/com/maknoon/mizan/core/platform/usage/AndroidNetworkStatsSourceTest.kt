package com.maknoon.mizan.core.platform.usage

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.domain.usage.AppUsageBytes
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

    @Test
    fun `no summary at all means the usage is unknown`() {
        assertThat(mergeSummaries(mobile = null, wifi = null)).isNull()
    }

    @Test
    fun `a uid missing from a successful summary is a measured zero`() {
        val merged = mergeSummaries(
            mobile = mapOf(10 to BucketTotals(1_000, 200)),
            wifi = mapOf(20 to BucketTotals(3_000, 400)),
        )!!

        assertThat(merged[10]).isEqualTo(AppUsageBytes(1_000, 200, 0, 0))
        assertThat(merged[20]).isEqualTo(AppUsageBytes(0, 0, 3_000, 400))
    }

    @Test
    fun `a failed summary leaves that network unknown for every uid`() {
        val merged = mergeSummaries(
            mobile = mapOf(10 to BucketTotals(1_000, 200)),
            wifi = null,
        )!!

        assertThat(merged.getValue(10))
            .isEqualTo(AppUsageBytes(1_000, 200, null, null))
    }

    @Test
    fun `repeated buckets of one uid are summed`() {
        // querySummary can emit more than one bucket per UID (for example foreground and
        // background tags); the source folds them into one total.
        val merged = mergeSummaries(
            mobile = mapOf(10 to BucketTotals(700, 300)),
            wifi = mapOf(10 to BucketTotals(5, 5)),
        )!!

        assertThat(merged.getValue(10)).isEqualTo(AppUsageBytes(700, 300, 5, 5))
    }
}
