package com.maknoon.mizan.core.data.db

import androidx.room.Entity

/**
 * Traffic observed during one local hour (docs/03), feeding the 24-hour chart.
 *
 * A bucket counts only what monitoring saw while it was running: the app cannot recover the
 * hours before it started, so a bucket is evidence of observed traffic rather than a share of
 * the day's counter total.
 */
@Entity(tableName = "device_hourly", primaryKeys = ["epochDay", "hour"])
data class DeviceHourlyEntity(
    val epochDay: Long,
    val hour: Int,
    val rxBytes: Long,
    val txBytes: Long,
)
