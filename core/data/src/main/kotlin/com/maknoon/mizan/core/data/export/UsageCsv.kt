package com.maknoon.mizan.core.data.export

import com.maknoon.mizan.core.data.db.DeviceDailyEntity

/**
 * Renders the stored daily rows as the CSV described in docs/03.
 *
 * The channel columns are left empty when the split was not measured. Writing zeroes there
 * would tell a spreadsheet that the day used no mobile data, which is a different claim from
 * "this device did not report the split" and would contradict the charts, which show "—".
 *
 * Cells hold plain integers with an ASCII comma and `\n` line endings: a byte count is not a
 * localised number, and the file has to parse the same way in any spreadsheet or script.
 */
object UsageCsv {

    const val HEADER = "epochDay,rxBytes,txBytes,mobileBytes,wifiBytes"

    fun daily(rows: List<DeviceDailyEntity>): String = buildString {
        append(HEADER)
        append('\n')
        rows.sortedBy { it.epochDay }.forEach { row ->
            append(row.epochDay)
            append(',')
            append(row.rxBytes)
            append(',')
            append(row.txBytes)
            append(',')
            appendChannel(row.mobileRxBytes, row.mobileTxBytes)
            append(',')
            appendChannel(row.wifiRxBytes, row.wifiTxBytes)
            append('\n')
        }
    }

    private fun StringBuilder.appendChannel(rxBytes: Long?, txBytes: Long?) {
        // Half a split is still not a split; both directions must be measured.
        if (rxBytes != null && txBytes != null) append(rxBytes + txBytes)
    }
}
