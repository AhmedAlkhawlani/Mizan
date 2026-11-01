package com.maknoon.mizan.core.data.usage

import android.content.Context
import com.maknoon.mizan.core.data.db.AppUsageDao
import com.maknoon.mizan.core.data.db.AppUsageTotal
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import kotlinx.coroutines.flow.Flow

/**
 * Reads the per-app totals the sync wrote.
 *
 * Kept apart from [AppUsageRepository] because reading needs no platform source and no clock:
 * the screen only observes Room, and the worker owns the writing.
 */
class AppUsageQueries(
    private val dao: AppUsageDao,
) {

    fun observeTotals(fromEpochDay: Long, toEpochDay: Long): Flow<List<AppUsageTotal>> =
        dao.observeTotals(fromEpochDay, toEpochDay)

    companion object {
        fun create(context: Context) = AppUsageQueries(MizanDatabaseProvider.appUsageDao(context))
    }
}
