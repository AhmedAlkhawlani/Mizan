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
        ).build().also { instance = it }
    }
}
