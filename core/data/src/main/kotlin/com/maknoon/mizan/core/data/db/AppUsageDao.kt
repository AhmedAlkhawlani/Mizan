package com.maknoon.mizan.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One app's total over a queried range, summed across days, networks and duplicate rows. */
data class AppUsageTotal(
    val packageName: String,
    val label: String?,
    val rxBytes: Long,
    val txBytes: Long,
    val lastSyncedAt: Long?,
)

/** One package's total over a range, for the budget alerts. */
data class PackageBytes(
    val packageName: String,
    val bytes: Long,
)

/** One package's bytes on one day and one network. */
data class AppDailyRow(
    val epochDay: Long,
    val networkType: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val label: String?,
    val lastSyncedAt: Long?,
)

@Dao
interface AppUsageDao {

    /**
     * Rewrites the queried days. The platform's totals are cumulative for the day, so an
     * upsert replaces a stale figure instead of adding to it.
     */
    @Upsert
    suspend fun upsertAll(rows: List<AppDailyEntity>)

    /**
     * Usage per package in a day range, heaviest first. `label` is not aggregated because a
     * package keeps one label per row; SQLite's bare-column pick is the latest one written.
     *
     * `uid >= 10000` (`Process.FIRST_APPLICATION_UID`) hides framework rows such as
     * `com.android.shell`, which the sync no longer writes; the clause also keeps rows stored
     * by builds before that filter was added out of the list.
     */
    @Query(
        """
        SELECT packageName,
               label,
               SUM(rxBytes) AS rxBytes,
               SUM(txBytes) AS txBytes,
               MAX(lastSyncedAt) AS lastSyncedAt
        FROM app_daily
        WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND uid >= 10000
        GROUP BY packageName
        ORDER BY (SUM(rxBytes) + SUM(txBytes)) DESC, packageName
        """,
    )
    fun observeTotals(fromEpochDay: Long, toEpochDay: Long): Flow<List<AppUsageTotal>>

    /** The same package day by day and network by network, oldest first. */
    @Query(
        """
        SELECT epochDay,
               networkType,
               SUM(rxBytes) AS rxBytes,
               SUM(txBytes) AS txBytes,
               MAX(label) AS label,
               MAX(lastSyncedAt) AS lastSyncedAt
        FROM app_daily
        WHERE packageName = :packageName
          AND epochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND uid >= 10000
        GROUP BY epochDay, networkType
        ORDER BY epochDay, networkType
        """,
    )
    fun observePackageDaily(
        packageName: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<AppDailyRow>>

    /**
     * Both directions per package over an inclusive range. The same `uid >= 10000` filter as
     * the list keeps framework rows out of a budget comparison.
     */
    @Query(
        """
        SELECT packageName,
               SUM(rxBytes + txBytes) AS bytes
        FROM app_daily
        WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND uid >= 10000
        GROUP BY packageName
        """,
    )
    suspend fun totalsByPackage(fromEpochDay: Long, toEpochDay: Long): List<PackageBytes>

    /** The heaviest app on one day, for the home-screen widget. */
    @Query(
        """
        SELECT packageName,
               MAX(label) AS label,
               SUM(rxBytes) AS rxBytes,
               SUM(txBytes) AS txBytes,
               MAX(lastSyncedAt) AS lastSyncedAt
        FROM app_daily
        WHERE epochDay = :epochDay
          AND uid >= 10000
        GROUP BY packageName
        ORDER BY (SUM(rxBytes) + SUM(txBytes)) DESC, packageName
        LIMIT 1
        """,
    )
    suspend fun topForDay(epochDay: Long): AppUsageTotal?
}
