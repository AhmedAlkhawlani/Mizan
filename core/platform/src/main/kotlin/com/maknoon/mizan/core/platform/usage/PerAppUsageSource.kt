package com.maknoon.mizan.core.platform.usage

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

fun interface PerAppUsageSource {

    /**
     * Bytes attributed to [uid] in [fromMillis, toMillis), or null when Usage access is not
     * granted and the platform therefore refuses to answer.
     *
     * The result is a correction of what the system already recorded, not a live counter:
     * `NetworkStatsManager` buckets can trail real traffic by minutes, so callers label the
     * figure with the time it was last synchronised.
     */
    fun usageForUid(uid: Int, fromMillis: Long, toMillis: Long): AppUsageBytes?
}
