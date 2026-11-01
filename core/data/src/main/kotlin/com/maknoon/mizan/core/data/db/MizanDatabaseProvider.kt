package com.maknoon.mizan.core.data.db

import android.content.Context
import androidx.room.Room

/**
 * One database instance per process.
 *
 * A manual singleton stands in for dependency injection; when Hilt arrives it becomes a bound
 * `@Singleton` and this object disappears.
 */
object MizanDatabaseProvider {

    @Volatile
    private var instance: MizanDatabase? = null

    fun get(context: Context): MizanDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            MizanDatabase::class.java,
            MizanDatabase.NAME,
        )
            .addMigrations(
                MizanDatabase.MIGRATION_1_2,
                MizanDatabase.MIGRATION_2_3,
                MizanDatabase.MIGRATION_3_4,
                MizanDatabase.MIGRATION_4_5,
                MizanDatabase.MIGRATION_5_6,
                MizanDatabase.MIGRATION_6_7,
            )
            .build()
            .also { instance = it }
    }

    /**
     * Keeps Room out of callers' classpaths: a caller that only needs a DAO should not have to
     * depend on the database's supertypes.
     */
    fun appUsageDao(context: Context): AppUsageDao = get(context).appUsageDao()

    fun deviceUsageDao(context: Context): DeviceUsageDao = get(context).deviceUsageDao()

    fun budgetDao(context: Context): BudgetDao = get(context).budgetDao()

    fun networkProfileDao(context: Context): NetworkProfileDao = get(context).networkProfileDao()
}
