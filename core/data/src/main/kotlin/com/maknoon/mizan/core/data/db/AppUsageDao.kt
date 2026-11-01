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
}
