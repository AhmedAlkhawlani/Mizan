package com.maknoon.mizan.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maknoon.mizan.alerts.BudgetAlerts
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.data.usage.AppUsageRepository
import com.maknoon.mizan.core.platform.usage.AndroidInstalledAppsSource
import com.maknoon.mizan.core.platform.usage.AndroidNetworkStatsSource
import com.maknoon.mizan.core.platform.usage.UsageAccess
import kotlinx.coroutines.CancellationException

/**
 * Copies the platform's per-app counters into Room (docs/02 §2).
 *
 * Without Usage access the worker succeeds silently instead of retrying: no retry can grant a
 * permission the operator has to switch on in Settings, and the Apps screen asks for it.
 */
class AppUsageSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!UsageAccess.isGranted(context)) return Result.success()

        return try {
            val repository = AppUsageRepository(
                dao = MizanDatabaseProvider.appUsageDao(context),
                source = AndroidNetworkStatsSource(context),
            )
            val apps = AndroidInstalledAppsSource(context).appsWithInternetAccess()
            val result = repository.sync(System.currentTimeMillis(), apps)
            Log.i(TAG, "synced ${result.rowsWritten} rows for ${apps.size} apps")
            // Per-app caps only move when this sync writes new totals, so evaluate here too.
            BudgetAlerts.check(context)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The database or the platform call can fail transiently; back off and try again.
            Log.w(TAG, "sync failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "MizanSync"
    }
}
