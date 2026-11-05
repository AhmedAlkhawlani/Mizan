package com.maknoon.mizan.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceUsageDao {

    @Query("SELECT * FROM device_daily WHERE epochDay = :epochDay")
    suspend fun daily(epochDay: Long): DeviceDailyEntity?

    @Query(
        """
        SELECT * FROM device_daily
        WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY epochDay
        """,
    )
    fun observeDailyRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DeviceDailyEntity>>

    /** Every stored day, for the export; the rows are the user's data, so none are dropped. */
    @Query("SELECT * FROM device_daily ORDER BY epochDay")
    suspend fun allDaily(): List<DeviceDailyEntity>

    /** Both directions over an inclusive range, for the budget alerts. */
    @Query(
        """
        SELECT COALESCE(SUM(rxBytes + txBytes), 0) FROM device_daily
        WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    suspend fun totalBytes(fromEpochDay: Long, toEpochDay: Long): Long

    @Upsert
    suspend fun upsertDaily(entity: DeviceDailyEntity)

    /** Adds observed traffic to one hour's bucket, creating it when it is the first sample. */
    @Query("INSERT OR IGNORE INTO device_hourly (epochDay, hour, rxBytes, txBytes) VALUES (:epochDay, :hour, 0, 0)")
    suspend fun openHourBucket(epochDay: Long, hour: Int)

    @Query(
        """
        UPDATE device_hourly
        SET rxBytes = rxBytes + :rxBytes, txBytes = txBytes + :txBytes
        WHERE epochDay = :epochDay AND hour = :hour
        """,
    )
    suspend fun accumulateHour(epochDay: Long, hour: Int, rxBytes: Long, txBytes: Long)

    /**
     * The pair above, kept atomic.
     *
     * `ON CONFLICT(epochDay, hour) DO UPDATE` — the upsert syntax this used to run in one
     * statement — needs SQLite 3.24. Android 9 and OEM builds of Android 10, including the
     * Galaxy S9, still ship 3.22, and there the parser aborted the whole sample write.
     */
    @Transaction
    suspend fun addHourly(epochDay: Long, hour: Int, rxBytes: Long, txBytes: Long) {
        openHourBucket(epochDay, hour)
        accumulateHour(epochDay, hour, rxBytes, txBytes)
    }

    @Query("SELECT * FROM device_hourly WHERE epochDay = :epochDay ORDER BY hour")
    fun observeHourly(epochDay: Long): Flow<List<DeviceHourlyEntity>>

    @Query("SELECT * FROM device_state WHERE id = ${DeviceStateEntity.SINGLETON_ID}")
    suspend fun state(): DeviceStateEntity?

    @Upsert
    suspend fun upsertState(entity: DeviceStateEntity)
}
