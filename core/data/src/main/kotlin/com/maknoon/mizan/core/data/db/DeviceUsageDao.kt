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

    @Query("SELECT * FROM device_state WHERE id = ${DeviceStateEntity.SINGLETON_ID}")
    suspend fun state(): DeviceStateEntity?

    @Upsert
    suspend fun upsertState(entity: DeviceStateEntity)
}
