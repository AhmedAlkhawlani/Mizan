package com.maknoon.mizan.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Keeps the per-app sync scheduled every 15 minutes — the shortest interval WorkManager allows
 * (docs/02 §6) — plus one immediate run whenever the app opens.
 *
 * `KEEP` makes both calls idempotent: opening the app repeatedly does not stack syncs, and the
 * periodic registration survives because WorkManager persists it across reboots.
 */
object AppUsageSyncScheduler {

    private const val PERIODIC_WORK = "app-usage-sync"
    private const val IMMEDIATE_WORK = "app-usage-sync-now"
    private const val PERIOD_MINUTES = 15L

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AppUsageSyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AppUsageSyncWorker>().build(),
        )
    }
}
