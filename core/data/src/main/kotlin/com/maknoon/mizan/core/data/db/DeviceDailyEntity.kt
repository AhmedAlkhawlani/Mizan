package com.maknoon.mizan.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per local day (docs/03).
 *
 * The mobile and Wi-Fi columns are null when the split could not be measured: for example a
 * device that does not report loopback counters, or a day that began under an older schema.
 * Storing zeroes there would be a lie the charts, the export and the app screens would repeat.
 *
 * [partial] marks a day the app did not observe from its first second, for example one that
 * started before an overnight reboot.
 */
@Entity(tableName = "device_daily")
data class DeviceDailyEntity(
    @PrimaryKey val epochDay: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val partial: Boolean,
    val updatedAtMillis: Long,
    val mobileRxBytes: Long? = null,
    val mobileTxBytes: Long? = null,
    val wifiRxBytes: Long? = null,
    val wifiTxBytes: Long? = null,
)
