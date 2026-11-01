package com.maknoon.mizan.core.data.export

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import org.junit.Test

class UsageCsvTest {

    private fun row(
        epochDay: Long,
        rxBytes: Long = 1_000,
        txBytes: Long = 200,
        mobileRxBytes: Long? = null,
        mobileTxBytes: Long? = null,
        wifiRxBytes: Long? = null,
        wifiTxBytes: Long? = null,
    ) = DeviceDailyEntity(
        epochDay = epochDay,
        rxBytes = rxBytes,
        txBytes = txBytes,
        partial = false,
        updatedAtMillis = 0L,
        mobileRxBytes = mobileRxBytes,
        mobileTxBytes = mobileTxBytes,
        wifiRxBytes = wifiRxBytes,
        wifiTxBytes = wifiTxBytes,
    )

    @Test
    fun `an empty database exports the header alone`() {
        assertThat(UsageCsv.daily(emptyList())).isEqualTo(UsageCsv.HEADER + "\n")
    }

    @Test
    fun `rows are written oldest first`() {
        val csv = UsageCsv.daily(listOf(row(20_760), row(20_758), row(20_759)))

        assertThat(csv.lines().drop(1).dropLast(1).map { it.substringBefore(',') })
            .containsExactly("20758", "20759", "20760")
            .inOrder()
    }

    @Test
    fun `a row carries the day total and both channel sums`() {
        val csv = UsageCsv.daily(
            listOf(
                row(
                    epochDay = 20_758,
                    rxBytes = 10_000,
                    txBytes = 2_000,
                    mobileRxBytes = 4_000,
                    mobileTxBytes = 500,
                    wifiRxBytes = 6_000,
                    wifiTxBytes = 1_500,
                ),
            ),
        )

        assertThat(csv.lines()[1]).isEqualTo("20758,10000,2000,4500,7500")
    }

    @Test
    fun `an unmeasured split is an empty field, not a zero`() {
        val csv = UsageCsv.daily(listOf(row(epochDay = 20_758)))

        assertThat(csv.lines()[1]).isEqualTo("20758,1000,200,,")
    }

    @Test
    fun `a half-measured split is empty too`() {
        val csv = UsageCsv.daily(
            listOf(row(epochDay = 20_758, mobileRxBytes = 4_000)),
        )

        assertThat(csv.lines()[1]).isEqualTo("20758,1000,200,,")
    }

    @Test
    fun `only the measured channel is filled`() {
        val csv = UsageCsv.daily(
            listOf(
                row(
                    epochDay = 20_758,
                    wifiRxBytes = 0,
                    wifiTxBytes = 0,
                ),
            ),
        )

        assertThat(csv.lines()[1]).isEqualTo("20758,1000,200,,0")
    }

    @Test
    fun `the file ends with one newline and has no blank rows`() {
        val csv = UsageCsv.daily(listOf(row(20_758), row(20_759)))

        assertThat(csv.endsWith("\n")).isTrue()
        assertThat(csv.endsWith("\n\n")).isFalse()
        // lines() keeps an empty tail after the final newline; dropping it leaves the real rows.
        assertThat(csv.lines().dropLast(1)).hasSize(3)
    }

    @Test
    fun `large counts stay plain integers`() {
        val csv = UsageCsv.daily(
            listOf(row(epochDay = 20_758, rxBytes = 10_000_000_000L, txBytes = 2_500_000_000L)),
        )

        assertThat(csv.lines()[1]).startsWith("20758,10000000000,2500000000,")
    }
}
