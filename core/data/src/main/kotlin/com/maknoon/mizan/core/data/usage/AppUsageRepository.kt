package com.maknoon.mizan.core.data.usage

import com.maknoon.mizan.core.data.db.AppDailyEntity
import com.maknoon.mizan.core.data.db.AppDailyNetwork
import com.maknoon.mizan.core.data.db.AppUsageDao
import com.maknoon.mizan.core.domain.usage.AppUsageBytes
import com.maknoon.mizan.core.domain.usage.InstalledApp
import com.maknoon.mizan.core.domain.usage.PerAppUsageSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What one sync did, for the worker's log and for tests. */
data class AppSyncResult(
    val rowsWritten: Int,
    val accessGranted: Boolean,
)

/**
 * Copies the platform's per-app counters into `app_daily`.
 *
 * Two local days are queried on every sync: today, and yesterday again. `NetworkStatsManager`
 * buckets land minutes late, so the final minutes of yesterday arrive after midnight; without
 * the second query that tail would never be recorded. The platform reports day-cumulative
 * totals, so each sync replaces the day's rows rather than adding to them.
 *
 * Apps sharing one UID are collapsed to a single entry: the platform attributes bytes per UID,
 * so listing every package of a shared UID would count the same traffic twice. The first
 * package in the sorted list represents the UID.
 */
class AppUsageRepository(
    private val dao: AppUsageDao,
    private val source: PerAppUsageSource,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun sync(nowMillis: Long, apps: List<InstalledApp>): AppSyncResult {
        val localDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val windows = listOf(localDate.minusDays(1), localDate)
            .map { day -> windowFor(day, nowMillis) }
            .filter { it.fromMillis < it.toMillis }

        // Read every window before writing anything, so a revoked grant cannot leave the
        // database half refreshed from a sync that was no longer complete.
        val summaries = windows.map { window ->
            val usageByUid = source.usageByUid(window.fromMillis, window.toMillis)
                ?: return AppSyncResult(rowsWritten = 0, accessGranted = false)
            window to usageByUid
        }

        val distinctApps = apps.distinctBy { it.uid }
        val rows = summaries.flatMap { (window, usageByUid) ->
            rowsFor(usageByUid, distinctApps, window.epochDay, nowMillis)
        }
        if (rows.isNotEmpty()) {
            dao.upsertAll(rows)
        }
        return AppSyncResult(rowsWritten = rows.size, accessGranted = true)
    }

    private fun rowsFor(
        usageByUid: Map<Int, AppUsageBytes>,
        apps: List<InstalledApp>,
        epochDay: Long,
        syncedAtMillis: Long,
    ): List<AppDailyEntity> = buildList {
        apps.forEach { app ->
            val usage = usageByUid[app.uid] ?: return@forEach
            usage.mobileBytes()?.let { (rx, tx) ->
                add(
                    app.row(
                        epochDay = epochDay,
                        networkType = AppDailyNetwork.MOBILE,
                        rxBytes = rx,
                        txBytes = tx,
                        syncedAtMillis = syncedAtMillis,
                    ),
                )
            }
            usage.wifiBytes()?.let { (rx, tx) ->
                add(
                    app.row(
                        epochDay = epochDay,
                        networkType = AppDailyNetwork.WIFI,
                        rxBytes = rx,
                        txBytes = tx,
                        syncedAtMillis = syncedAtMillis,
                    ),
                )
            }
        }
    }

    private fun windowFor(day: LocalDate, nowMillis: Long): DayWindow {
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return DayWindow(
            epochDay = day.toEpochDay(),
            fromMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            // Today's window stops at the sample; querying into the future would only add an
            // empty range.
            toMillis = minOf(nextMidnight, nowMillis),
        )
    }

    private class DayWindow(
        val epochDay: Long,
        val fromMillis: Long,
        val toMillis: Long,
    )
}

/**
 * A network's measured bytes, or null when either direction is unknown or nothing moved.
 * Zero traffic gets no row: it would rewrite a row per app per sync for no information.
 */
private fun AppUsageBytes.mobileBytes(): Pair<Long, Long>? =
    bytes(mobileRxBytes, mobileTxBytes)

private fun AppUsageBytes.wifiBytes(): Pair<Long, Long>? =
    bytes(wifiRxBytes, wifiTxBytes)

private fun bytes(rxBytes: Long?, txBytes: Long?): Pair<Long, Long>? {
    if (rxBytes == null || txBytes == null) return null
    if (rxBytes <= 0L && txBytes <= 0L) return null
    return rxBytes to txBytes
}

private fun InstalledApp.row(
    epochDay: Long,
    networkType: Int,
    rxBytes: Long,
    txBytes: Long,
    syncedAtMillis: Long,
) = AppDailyEntity(
    epochDay = epochDay,
    uid = uid,
    packageName = packageName,
    label = label,
    networkType = networkType,
    rxBytes = rxBytes,
    txBytes = txBytes,
    lastSyncedAt = syncedAtMillis,
)
