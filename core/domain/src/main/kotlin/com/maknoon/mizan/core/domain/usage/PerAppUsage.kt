package com.maknoon.mizan.core.domain.usage

/**
 * What the platform attributes to one UID over a queried range.
 *
 * A channel is null when it could not be read, never zero: the same rule the device counters
 * follow, so an unreadable network is shown as unknown instead of "no traffic".
 */
data class AppUsageBytes(
    val mobileRxBytes: Long?,
    val mobileTxBytes: Long?,
    val wifiRxBytes: Long?,
    val wifiTxBytes: Long?,
)

/** One installed package, as far as the Apps screen and the sync need to know it. */
data class InstalledApp(
    val packageName: String,
    val uid: Int,
    val label: String,
)

/**
 * The port through which the platform's per-app counters are read (docs/02 §2).
 *
 * Both methods answer null when Usage access is not granted and the platform therefore refuses
 * to answer. Results are a correction of what the system already recorded, not a live counter:
 * `NetworkStatsManager` buckets can trail real traffic by minutes, so callers label the figure
 * with the time it was last synchronised.
 */
interface PerAppUsageSource {

    /** Bytes attributed to [uid] in [fromMillis, toMillis). */
    fun usageForUid(uid: Int, fromMillis: Long, toMillis: Long): AppUsageBytes?

    /**
     * Bytes for every UID the platform recorded in [fromMillis, toMillis), read in two bulk
     * queries instead of one per app. A UID missing from the result had no traffic on the
     * networks that answered; a null network means that network's query failed.
     */
    fun usageByUid(fromMillis: Long, toMillis: Long): Map<Int, AppUsageBytes>?
}
