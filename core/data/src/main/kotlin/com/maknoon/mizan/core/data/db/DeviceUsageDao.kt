package com.maknoon.mizan.core.data.db

import androidx.room.Dao
import androidx.room.Query
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

    @Upsert
    suspend fun upsertDaily(entity: DeviceDailyEntity)

    /** Adds observed traffic to one hour's bucket, creating it when it is the first sample. */
    @Query(
        """
        INSERT INTO device_hourly (epochDay, hour, rxBytes, txBytes)
        VALUES (:epochDay, :hour, :rxBytes, :txBytes)
        ON CONFLICT(epochDay, hour) DO UPDATE SET
            rxBytes = rxBytes + :rxBytes,
            txBytes = txBytes + :txBytes
        """,
    )
    suspend fun addHourly(epochDay: Long, hour: Int, rxBytes: Long, txBytes: Long)

    @Query("SELECT * FROM device_hourly WHERE epochDay = :epochDay ORDER BY hour")
    fun observeHourly(epochDay: Long): Flow<List<DeviceHourlyEntity>>

    @Query("SELECT * FROM device_state WHERE id = ${DeviceStateEntity.SINGLETON_ID}")
    suspend fun state(): DeviceStateEntity?

    @Upsert
    suspend fun upsertState(entity: DeviceStateEntity)
}
