package com.maknoon.mizan.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeviceDailyEntity::class,
        DeviceHourlyEntity::class,
        DeviceStateEntity::class,
        AppDailyEntity::class,
        BudgetEntity::class,
        AlertEventEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MizanDatabase : RoomDatabase() {

    abstract fun deviceUsageDao(): DeviceUsageDao

    abstract fun appUsageDao(): AppUsageDao

    abstract fun budgetDao(): BudgetDao

    abstract fun alertEventDao(): AlertEventDao

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

        /**
         * Adds the per-interface split. Both tables use nullable columns because a reading that
         * was never taken is unknown, not zero. Rows written before this migration leave the
         * split null, so the day in progress stays unsplit rather than showing only the traffic
         * counted after the upgrade; the next midnight starts a fully seeded day.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `device_daily` ADD COLUMN `mobileRxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_daily` ADD COLUMN `mobileTxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_daily` ADD COLUMN `wifiRxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_daily` ADD COLUMN `wifiTxBytes` INTEGER")

                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `lastMobileRxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `lastMobileTxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `todayMobileRxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `todayMobileTxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `lastLoopbackRxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `lastLoopbackTxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `todayLoopbackRxBytes` INTEGER")
                db.execSQL("ALTER TABLE `device_state` ADD COLUMN `todayLoopbackTxBytes` INTEGER")
            }
        }

        /**
         * Adds the per-app table. No backfill is possible: the platform only answers for ranges
         * the app asks about, and rows start with the first sync after the upgrade.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_daily` (
                        `epochDay` INTEGER NOT NULL,
                        `uid` INTEGER NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `networkType` INTEGER NOT NULL,
                        `rxBytes` INTEGER NOT NULL,
                        `txBytes` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`epochDay`, `uid`, `networkType`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds the budgets table. The scope and period enums are stored as their names, exactly
         * as Room's built-in enum converter writes them.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `period` TEXT NOT NULL,
                        `limitBytes` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds the delivered-alert ledger. The unique index makes the "once per level per
         * cycle" rule survive the two independent evaluators (service and worker): a second
         * insert for the same threshold is ignored instead of notifying again.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alert_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `at` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `period` TEXT NOT NULL,
                        `periodStartEpochDay` INTEGER NOT NULL,
                        `measuredBytes` INTEGER NOT NULL,
                        `limitBytes` INTEGER NOT NULL,
                        `acknowledged` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_alert_events_type_scope_targetId_period_periodStartEpochDay`
                    ON `alert_events` (`type`, `scope`, `targetId`, `period`, `periodStartEpochDay`)
                    """.trimIndent(),
                )
            }
        }
    }
}
