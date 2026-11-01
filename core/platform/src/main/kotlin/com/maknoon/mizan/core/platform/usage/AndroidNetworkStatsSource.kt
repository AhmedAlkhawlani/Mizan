package com.maknoon.mizan.core.platform.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException
import com.maknoon.mizan.core.domain.usage.AppUsageBytes
import com.maknoon.mizan.core.domain.usage.PerAppUsageSource

/**
 * Reads the system's per-UID byte counters (docs/02 §2).
 *
 * Mobile and Wi-Fi are queried separately because the platform keeps them apart, and each is
 * nullable: one network failing must not erase the other. `subscriberId` is null so the query
 * covers the default subscription without the READ_PHONE_STATE permission the SIM identifiers
 * would require.
 *
 * The bulk [usageByUid] path uses `querySummary`, which returns one row per UID with the same
 * totals as `queryDetailsForUid` but in two calls for the whole device instead of two per app.
 * That keeps a 15-minute sync to four binder calls rather than one per installed package.
 */
class AndroidNetworkStatsSource(
    private val context: Context,
) : PerAppUsageSource {

    override fun usageForUid(uid: Int, fromMillis: Long, toMillis: Long): AppUsageBytes? {
        val manager = manager() ?: return null
        return mergeChannels(
            mobile = details(manager, ConnectivityManager.TYPE_MOBILE, uid, fromMillis, toMillis),
            wifi = details(manager, ConnectivityManager.TYPE_WIFI, uid, fromMillis, toMillis),
        )
    }

    override fun usageByUid(fromMillis: Long, toMillis: Long): Map<Int, AppUsageBytes>? {
        val manager = manager() ?: return null
        return mergeSummaries(
            mobile = summary(manager, ConnectivityManager.TYPE_MOBILE, fromMillis, toMillis),
            wifi = summary(manager, ConnectivityManager.TYPE_WIFI, fromMillis, toMillis),
        )
    }

    private fun manager(): NetworkStatsManager? {
        // Asking without the grant throws SecurityException; answer honestly instead.
        if (!UsageAccess.isGranted(context)) return null
        return context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    }

    private fun details(
        manager: NetworkStatsManager,
        networkType: Int,
        uid: Int,
        fromMillis: Long,
        toMillis: Long,
    ): BucketTotals? = try {
        val stats = manager.queryDetailsForUid(networkType, null, fromMillis, toMillis, uid)
        val bucket = NetworkStats.Bucket()
        var rxBytes = 0L
        var txBytes = 0L
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            rxBytes += bucket.rxBytes
            txBytes += bucket.txBytes
        }
        BucketTotals(rxBytes, txBytes)
    } catch (_: SecurityException) {
        null
    } catch (_: RemoteException) {
        null
    }

    private fun summary(
        manager: NetworkStatsManager,
        networkType: Int,
        fromMillis: Long,
        toMillis: Long,
    ): Map<Int, BucketTotals>? = try {
        val stats = manager.querySummary(networkType, null, fromMillis, toMillis)
        val bucket = NetworkStats.Bucket()
        val byUid = HashMap<Int, BucketTotals>()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            val previous = byUid[bucket.uid]
            byUid[bucket.uid] = BucketTotals(
                rxBytes = (previous?.rxBytes ?: 0L) + bucket.rxBytes,
                txBytes = (previous?.txBytes ?: 0L) + bucket.txBytes,
            )
        }
        byUid
    } catch (_: SecurityException) {
        null
    } catch (_: RemoteException) {
        null
    }
}

internal data class BucketTotals(val rxBytes: Long, val txBytes: Long)

/** Null only when neither network answered; a single answer leaves the other side unknown. */
internal fun mergeChannels(mobile: BucketTotals?, wifi: BucketTotals?): AppUsageBytes? {
    if (mobile == null && wifi == null) return null
    return AppUsageBytes(
        mobileRxBytes = mobile?.rxBytes,
        mobileTxBytes = mobile?.txBytes,
        wifiRxBytes = wifi?.rxBytes,
        wifiTxBytes = wifi?.txBytes,
    )
}

/**
 * Combines the two bulk summaries.
 *
 * Once a network's summary answered, a UID it does not mention is a measured zero on that
 * network, not an unknown: the platform lists every UID it recorded traffic for. Only a
 * summary that failed turns the whole network into null.
 */
internal fun mergeSummaries(
    mobile: Map<Int, BucketTotals>?,
    wifi: Map<Int, BucketTotals>?,
): Map<Int, AppUsageBytes>? {
    if (mobile == null && wifi == null) return null
    val uids = buildSet {
        mobile?.keys?.let(::addAll)
        wifi?.keys?.let(::addAll)
    }
    return uids.associateWith { uid ->
        val mobileTotals = mobile?.get(uid)
        val wifiTotals = wifi?.get(uid)
        AppUsageBytes(
            mobileRxBytes = if (mobile == null) null else mobileTotals?.rxBytes ?: 0L,
            mobileTxBytes = if (mobile == null) null else mobileTotals?.txBytes ?: 0L,
            wifiRxBytes = if (wifi == null) null else wifiTotals?.rxBytes ?: 0L,
            wifiTxBytes = if (wifi == null) null else wifiTotals?.txBytes ?: 0L,
        )
    }
}
