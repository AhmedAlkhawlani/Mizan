package com.maknoon.mizan.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DeviceDailyEntity::class, DeviceStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MizanDatabase : RoomDatabase() {

    abstract fun deviceUsageDao(): DeviceUsageDao

    companion object {
        const val NAME = "mizan.db"
    }
}
