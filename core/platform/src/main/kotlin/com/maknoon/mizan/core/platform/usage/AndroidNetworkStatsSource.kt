package com.maknoon.mizan.core.platform.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException

/**
 * Reads the system's per-UID byte counters (docs/02 §2).
 *
 * Mobile and Wi-Fi are queried separately because the platform keeps them apart, and each is
 * nullable: one network failing must not erase the other. `subscriberId` is null so the query
 * covers the default subscription without the READ_PHONE_STATE permission the SIM identifiers
 * would require.
 */
class AndroidNetworkStatsSource(
    private val context: Context,
) : PerAppUsageSource {

    override fun usageForUid(uid: Int, fromMillis: Long, toMillis: Long): AppUsageBytes? {
        // Asking without the grant throws SecurityException; answer honestly instead.
        if (!UsageAccess.isGranted(context)) return null
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return null
        return mergeChannels(
            mobile = query(manager, ConnectivityManager.TYPE_MOBILE, uid, fromMillis, toMillis),
            wifi = query(manager, ConnectivityManager.TYPE_WIFI, uid, fromMillis, toMillis),
        )
    }

    private fun query(
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
