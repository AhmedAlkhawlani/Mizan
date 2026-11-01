package com.maknoon.mizan.core.data.db

import androidx.room.Entity

/**
 * One app's measured traffic per local day and network (docs/03).
 *
 * A missing row means no traffic was measured for that app, day and network; it never means
 * an unreadable counter, because the sync simply does not write a row when the platform did
 * not answer. Rows are rewritten in full on every sync, so a late platform bucket raises the
 * stored total rather than being added on top of it.
 */
@Entity(tableName = "app_daily", primaryKeys = ["epochDay", "uid", "networkType"])
data class AppDailyEntity(
    val epochDay: Long,
    val uid: Int,
    val packageName: String,
    val label: String,
    val networkType: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val lastSyncedAt: Long,
)

/** The stored [AppDailyEntity.networkType] values; they match `ConnectivityManager`'s. */
object AppDailyNetwork {
    const val MOBILE = 0
    const val WIFI = 1
}
