package com.maknoon.mizan.core.data.db

import androidx.room.Dao
import androidx.room.Upsert

@Dao
interface AppUsageDao {

    /**
     * Rewrites the queried days. The platform's totals are cumulative for the day, so an
     * upsert replaces a stale figure instead of adding to it.
     */
    @Upsert
    suspend fun upsertAll(rows: List<AppDailyEntity>)
}
