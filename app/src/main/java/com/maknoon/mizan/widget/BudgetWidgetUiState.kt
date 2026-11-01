package com.maknoon.mizan.widget

import com.maknoon.mizan.core.data.widget.WidgetTopApp

/** The four states the ring can be in; colours and text stay in the Glance layer. */
internal enum class BudgetRingStatus { NONE, NORMAL, WARNING, EXCEEDED }

/**
 * The ring's figures after formatting rules, without Android types, so the thresholds and the
 * unknown-versus-zero distinction are unit tested like every other screen's state.
 */
internal data class BudgetWidgetUiState(
    val usedBytes: Long?,
    val limitBytes: Long?,
    val percent: Int?,
    val ringFraction: Float,
    val status: BudgetRingStatus,
    val topAppPackage: String?,
    val topAppLabel: String?,
    val topAppBytes: Long?,
)

/**
 * The number in the ring's centre. Remaining is only meaningful next to a cap, so without one
 * the widget keeps showing the usage it does know. An overrun shows zero remaining, not a
 * negative number; the percentage and the danger colour already say how far past the cap it is.
 */
internal fun BudgetWidgetUiState.centerBytes(showRemaining: Boolean): Long? =
    if (showRemaining && limitBytes != null) {
        usedBytes?.let { (limitBytes - it).coerceAtLeast(0L) }
    } else {
        usedBytes
    }

internal fun budgetWidgetUiState(
    usedBytes: Long?,
    limitBytes: Long?,
    topApp: WidgetTopApp?,
): BudgetWidgetUiState {
    val used = usedBytes?.coerceAtLeast(0L)
    val limit = limitBytes?.takeIf { it > 0 }
    val percent = if (used != null && limit != null) {
        ((used.toDouble() / limit) * 100).toInt()
    } else {
        null
    }
    val fraction = if (used != null && limit != null) {
        (used.toDouble() / limit).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val status = when {
        used == null || limit == null -> BudgetRingStatus.NONE
        used >= limit -> BudgetRingStatus.EXCEEDED
        percent != null && percent >= WARNING_PERCENT -> BudgetRingStatus.WARNING
        else -> BudgetRingStatus.NORMAL
    }
    return BudgetWidgetUiState(
        usedBytes = used,
        limitBytes = limit,
        percent = percent,
        ringFraction = fraction,
        status = status,
        topAppPackage = topApp?.packageName,
        topAppLabel = topApp?.label?.trim()?.takeIf { it.isNotEmpty() } ?: topApp?.packageName,
        topAppBytes = topApp?.bytes,
    )
}

private const val WARNING_PERCENT = 80
