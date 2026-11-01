package com.maknoon.mizan.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeviceDailyEntity::class, DeviceHourlyEntity::class, DeviceStateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MizanDatabase : RoomDatabase() {

    abstract fun deviceUsageDao(): DeviceUsageDao

    companion object {
        const val NAME = "mizan.db"

        /**
         * Adds the hourly buckets behind the 24-hour chart. Existing daily rows are untouched:
         * their totals were measured and stay valid, while hours before the upgrade simply have
         * no bucket, which the chart shows as an empty bar rather than inventing a split.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `device_hourly` (
                        `epochDay` INTEGER NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `rxBytes` INTEGER NOT NULL,
                        `txBytes` INTEGER NOT NULL,
                        PRIMARY KEY(`epochDay`, `hour`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
