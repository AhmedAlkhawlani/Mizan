package com.maknoon.mizan.core.domain.anomaly

import kotlin.math.ceil

/**
 * An app whose usage today is far above its own normal.
 *
 * [medianBytes] is the robust baseline, [thresholdBytes] is the effective cut-off
 * (`max(minimumBytes, multiplier × median)`), and [ratio] is today's usage divided by the
 * median, so the caller can sort alerts and explain them without recomputing anything.
 */
data class Anomaly(
    val packageName: String,
    val usedBytes: Long,
    val medianBytes: Long,
    val thresholdBytes: Long,
    val ratio: Double,
)

/**
 * Finds apps that consumed several times their habitual daily amount (docs/05 4.5).
 *
 * The baseline is a median, not a mean: one unusual day in the history must not raise the bar
 * for every later day. The detector is stateless; the caller owns both the history window and
 * the cooldown set, exactly as [com.maknoon.mizan.core.domain.budget.BudgetPolicy] leaves the
 * notification ledger to its caller.
 *
 * Inputs are already aggregated by the caller:
 * - [todayBytes] maps a package to what it has used since midnight.
 * - [historyBytes] maps a package to its previous daily totals, one entry per day it was seen.
 *   Days the app never appeared on are omitted, so a freshly installed app cannot inherit a
 *   zero baseline and trigger a false "anomaly" on its first real use.
 * - [coolingDown] holds packages already alerted in the current cooldown window (the caller
 *   persists one alert per app per local day), so repeated evaluation stays silent.
 */
class AnomalyDetector(
    private val multiplier: Double = DEFAULT_MULTIPLIER,
    private val minimumHistoryDays: Int = DEFAULT_MINIMUM_HISTORY_DAYS,
    private val minimumBytes: Long = DEFAULT_MINIMUM_BYTES,
) {

    init {
        require(multiplier > 0.0) { "multiplier must be positive but was $multiplier" }
        require(minimumHistoryDays >= 1) {
            "minimumHistoryDays must be at least 1 but was $minimumHistoryDays"
        }
        require(minimumBytes >= 0L) { "minimumBytes must not be negative but was $minimumBytes" }
    }

    fun detect(
        todayBytes: Map<String, Long>,
        historyBytes: Map<String, List<Long>>,
        coolingDown: Set<String> = emptySet(),
    ): List<Anomaly> = todayBytes.asSequence()
        .filter { (packageName, used) -> used > 0L && packageName !in coolingDown }
        .mapNotNull { (packageName, used) ->
            val history = historyBytes[packageName].orEmpty()
            if (history.size < minimumHistoryDays) return@mapNotNull null

            val median = medianOf(history)
            if (median <= 0L) return@mapNotNull null

            val threshold = maxOf(minimumBytes, scaledThreshold(median))
            if (used < threshold) return@mapNotNull null

            Anomaly(
                packageName = packageName,
                usedBytes = used,
                medianBytes = median,
                thresholdBytes = threshold,
                ratio = used.toDouble() / median,
            )
        }
        .sortedWith(
            compareByDescending<Anomaly> { it.ratio }
                .thenBy { it.packageName },
        )
        .toList()

    /**
     * Median of an even-sized sample is the midpoint of the two middle values, computed without
     * adding them (which could overflow for byte counts near `Long.MAX_VALUE`).
     */
    private fun medianOf(history: List<Long>): Long {
        val sorted = history.map { it.coerceAtLeast(0L) }.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            sorted[middle - 1] + (sorted[middle] - sorted[middle - 1]) / 2
        }
    }

    private fun scaledThreshold(median: Long): Long {
        val scaled = median.toDouble() * multiplier
        return if (scaled >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else ceil(scaled).toLong()
    }

    companion object {
        /** An app is abnormal at four times its median day (docs/10). */
        const val DEFAULT_MULTIPLIER = 4.0

        /** Fewer active days than this cannot establish a habit. */
        const val DEFAULT_MINIMUM_HISTORY_DAYS = 3

        /** Below this absolute usage an alert would be noise whatever the ratio is. */
        const val DEFAULT_MINIMUM_BYTES = 50_000_000L
    }
}
