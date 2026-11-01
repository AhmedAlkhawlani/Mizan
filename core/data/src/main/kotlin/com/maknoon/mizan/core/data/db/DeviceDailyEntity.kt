package com.maknoon.mizan.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per local day (docs/03).
 *
 * Only what the device can actually report is stored. The mobile/Wi-Fi split from the schema
 * sketch is deliberately absent until per-interface counters are implemented in phase 2.4 —
 * storing zeroes for traffic we never measured would be a lie the charts would repeat.
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
)
